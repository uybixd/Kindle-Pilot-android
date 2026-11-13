package com.hongxiang.kpilot.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.data.cacheScope
import com.hongxiang.kpilot.notes.AtomicCache
import com.hongxiang.kpilot.ssh.KindleSshSessionFactory
import com.hongxiang.kpilot.support.SyncTrace
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Library synchronization and legacy backup archiving. Call these blocking functions on an IO dispatcher. */
class LibraryService(context: Context) {
    private val cacheRoot = context.noBackupFilesDir.resolve("library")

    fun loadCached(settings: ConnectionSettings): LibrarySnapshot? = runCatching {
        val scope = settings.cacheScope()
        val directory = cacheRoot.resolve(scope)
        val file = directory.resolve("snapshot.json")
        if (!file.isFile) return null
        val root = JSONObject(file.readText())
        if (root.getString("sourceKey") != scope) return null
        val array = root.getJSONArray("books")
        val books = (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val remotePath = item.getString("remotePath")
            // Older snapshots included hidden note backups. Drop only those entries,
            // so an offline upgrade still keeps the user's real books available.
            if (!LibraryRules.isBookPath(remotePath)) return@mapNotNull null
            val coverName = item.optionalString("cover")?.takeIf { it.matches(Regex("[a-f0-9]{64}\\.jpg")) }
            KindleBook(
                remotePath = remotePath,
                title = item.getString("title"),
                author = item.optionalString("author"),
                coverPath = coverName?.let { directory.resolve("covers/$it") }?.takeIf(::usableCover)?.absolutePath,
                percentFinished = item.optionalDouble("percentFinished")?.coerceIn(0.0, 100.0),
                contentSize = if (item.has("contentSize")) item.getLong("contentSize") else null
            )
        }
        LibrarySnapshot(books, Instant.parse(root.getString("syncedAt")), scope)
    }.getOrNull()

    fun sync(settings: ConnectionSettings): LibrarySnapshot {
        val span = SyncTrace.begin("library")
        var bookCount = 0
        try {
            settings.validate()
            val scope = settings.cacheScope()
            val directory = cacheRoot.resolve(scope)
            check(directory.isDirectory || directory.mkdirs()) { "无法创建书库缓存目录" }
            val oldBooks = loadCached(settings)?.books.orEmpty().associateBy { it.remotePath }
            val session = KindleSshSessionFactory.create(settings)
            try {
                span.stage("connect") { session.connect(CONNECT_TIMEOUT_MS) }
                span.stage("legacy_backups") { optionalMetadata { archiveLegacyClippingsBackups(session) } }
                val predicates = LibraryRules.supportedExtensions.sorted().joinToString(" -o ") {
                    "-iname ${shellQuote("*.$it")}"
                }
                val command = "test -d /mnt/us/documents && find /mnt/us/documents -mindepth 1 \\( -name '.*' -o -type d -iname '*.sdr' \\) -prune -o -type f \\( $predicates \\) -print0"
                val paths = span.stage("list") {
                    execute(session, command, MAX_LIST_BYTES).toString(Charsets.UTF_8)
                        .split('\u0000').filter(LibraryRules::isBookPath).distinct()
                }
                val entries = span.stage("catalog") {
                    optionalMetadata {
                        val temporary = File.createTempFile("catalog-", ".sqlite", directory)
                        try {
                            temporary.writeBytes(execute(session, "cat /var/local/cc.db", MAX_CATALOG_BYTES))
                            readCatalog(temporary)
                        } finally { temporary.delete() }
                    }.orEmpty()
                }
                val books = span.stage("books") {
                    paths.map { path ->
                        val entry = entries[path]
                        val previousCover = oldBooks[path]?.coverPath?.takeIf { File(it).isFile }
                        val cover = entry?.thumbnail?.let { thumbnail ->
                            optionalMetadata { cacheCover(session, thumbnail, directory) }
                        } ?: previousCover
                        KindleBook(
                            remotePath = path,
                            title = entry?.title?.takeIf(String::isNotBlank) ?: LibraryRules.fileTitle(path),
                            author = entry?.author,
                            coverPath = cover,
                            percentFinished = entry?.percent?.takeIf(Double::isFinite)?.coerceIn(0.0, 100.0),
                            contentSize = entry?.size
                        )
                    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                }
                bookCount = books.size
                val snapshot = LibrarySnapshot(books, Instant.now(), scope)
                span.stage("save") { saveSnapshot(directory, snapshot) }
                return snapshot
            } finally { session.disconnect() }
        } finally {
            span.end("books=$bookCount")
        }
    }

    /**
     * Rewrites only Kindle's shelf thumbnail. The ebook, content catalog and .sdr are never edited.
     * Matches the iOS repair flow: extract the EXTH cover, back up the previous thumbnail,
     * write and verify the replacement, then keep a copy in Kindle's recovery folder.
     * [localBookBytes] is used for a book that was just uploaded, avoiding a second download.
     */
    fun repairCover(
        settings: ConnectionSettings,
        remotePath: String,
        localBookBytes: ByteArray? = null
    ): CoverRepairResult {
        settings.validate()
        val path = LibraryRules.normalizeRemotePath(remotePath)
        require(path == remotePath && LibraryRules.isCoverRepairPath(path)) {
            "拒绝操作不安全的 Kindle 路径：$remotePath"
        }
        val directory = cacheRoot.resolve(settings.cacheScope())
        check(directory.isDirectory || directory.mkdirs()) { "无法创建书库缓存目录" }
        val session = KindleSshSessionFactory.create(settings)
        session.setServerAliveInterval(SERVER_ALIVE_INTERVAL_MS)
        session.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX)
        try {
            session.connect(CONNECT_TIMEOUT_MS)
            val cover = (if (localBookBytes != null) {
                check(
                    localBookBytes.isNotEmpty() &&
                        localBookBytes.size.toLong() <= LibraryRules.MAX_COVER_BOOK_BYTES
                ) { "封面提取仅支持不超过 64 MB 的非空书籍；书籍上传不受影响。" }
                MobiCoverExtractor.cover(localBookBytes)
            } else {
                val quotedPath = shellQuote(path)
                val size = execute(session, "wc -c < $quotedPath", 4 * 1024)
                    .toString(Charsets.UTF_8).trim().toLongOrNull()
                check(size != null && size > 0 && size <= LibraryRules.MAX_COVER_BOOK_BYTES) {
                    "封面提取仅支持不超过 64 MB 的非空书籍；书籍上传不受影响。"
                }
                val bookFile = File.createTempFile("cover-book-", ".bin", directory)
                try {
                    download(
                        session, "cat $quotedPath", bookFile,
                        LibraryRules.MAX_COVER_BOOK_BYTES, BOOK_TRANSFER_TIMEOUT_MS
                    )
                    MobiCoverExtractor.cover(bookFile.readBytes())
                } finally { bookFile.delete() }
            }) ?: throw IllegalStateException("未找到可用的内嵌封面，可能没有封面、格式不兼容或受 DRM 保护。")

            val filename = if (localBookBytes != null) {
                // A freshly uploaded filename can still map to the OLD catalog entry until
                // Kindle reindexes, so only trust the incoming ebook's own identity here.
                cover.thumbnailFilename
                    ?: throw IllegalStateException(
                        "无法确定封面标识。请在 Kindle 打开书籍并返回书架，待索引完成后再长按修复。"
                    )
            } else {
                val catalogThumbnail = optionalMetadata {
                    val catalogFile = File.createTempFile("catalog-", ".sqlite", directory)
                    try {
                        catalogFile.writeBytes(execute(session, "cat /var/local/cc.db", MAX_CATALOG_BYTES))
                        readCatalog(catalogFile)[path]?.thumbnail
                    } finally { catalogFile.delete() }
                }
                resolveThumbnailFilename(catalogThumbnail, cover.thumbnailFilename)
            }
            val jpeg = KindleCoverThumbnail.encode(cover.data)
                ?: throw IllegalStateException("未找到可用的内嵌封面，可能没有封面、格式不兼容或受 DRM 保护。")

            val target = "/mnt/us/system/thumbnails/$filename"
            val recovery = "/mnt/us/amazon-cover-bug/$filename"
            writeRemote(session, jpeg, target)
            val verification = execute(
                session, "cat ${shellQuote(target)}", MAX_THUMBNAIL_BYTES, THUMBNAIL_TRANSFER_TIMEOUT_MS
            )
            check(verification.contentEquals(jpeg)) {
                "封面写入后校验不一致，可能已被 Kindle 覆盖，请稍后重试。"
            }
            try {
                // Calibre's reconnect recovery uses the same folder; KPilot's own refresh stays read-only.
                writeRemote(session, jpeg, recovery)
            } catch (error: Exception) {
                val reason = error.message ?: error.javaClass.simpleName
                throw IllegalStateException("封面已写入并校验，但恢复副本保存失败：$reason。", error)
            }

            val coverPath = optionalMetadata { cacheCover(session, target, directory, force = true) }
            return CoverRepairResult(
                "封面已写入并校验。请返回 Kindle 书架查看；若联网后被覆盖，可再次长按修复。",
                coverPath
            )
        } finally { session.disconnect() }
    }

    /** Deletes a Kindle book, optionally its .sdr reading data, and updates the offline shelf. */
    fun deleteBook(settings: ConnectionSettings, remotePath: String, includingReadingData: Boolean) {
        settings.validate()
        val path = LibraryRules.normalizeRemotePath(remotePath)
        require(path == remotePath && LibraryRules.isBookPath(path)) {
            "拒绝操作不安全的 Kindle 路径：$remotePath"
        }
        val session = KindleSshSessionFactory.create(settings)
        try {
            session.connect(CONNECT_TIMEOUT_MS)
            execute(session, LibraryDeletion.command(path, includingReadingData), 16 * 1024)
        } finally { session.disconnect() }
        // Keep the offline shelf correct even if the next refresh happens while offline.
        // The remote deletion already succeeded, so a local write failure must not report it as failed.
        try {
            val directory = cacheRoot.resolve(settings.cacheScope())
            if (directory.isDirectory || directory.mkdirs()) {
                loadCached(settings)?.let { snapshot ->
                    saveSnapshot(
                        directory,
                        snapshot.copy(books = snapshot.books.filterNot { it.remotePath == path })
                    )
                }
            }
        } catch (interrupted: InterruptedException) {
            throw interrupted
        } catch (_: Exception) {
        }
    }

    private fun resolveThumbnailFilename(mapped: String?, extracted: String?): String {
        if (!mapped.isNullOrEmpty()) {
            val name = mapped.substringAfterLast('/')
            check(mapped == "/mnt/us/system/thumbnails/$name" && MobiCoverExtractor.isSafeThumbnailFilename(name)) {
                "无法确定封面标识。请在 Kindle 打开书籍并返回书架，待索引完成后再长按修复。"
            }
            return name
        }
        return extracted
            ?: throw IllegalStateException("无法确定封面标识。请在 Kindle 打开书籍并返回书架，待索引完成后再长按修复。")
    }

    private fun writeRemote(session: Session, data: ByteArray, target: String) {
        val directory = target.substringBeforeLast('/')
        val temporary = "$directory/.kpilot-cover-${UUID.randomUUID()}.tmp"
        val command = "set -e; mkdir -p ${shellQuote(directory)}; " +
            "cat > ${shellQuote(temporary)}; " +
            "mv -f ${shellQuote(temporary)} ${shellQuote(target)}; sync"
        try {
            upload(session, command, data, THUMBNAIL_TRANSFER_TIMEOUT_MS)
        } catch (error: Exception) {
            // A failed write must not leave a hidden temporary thumbnail behind.
            runCatching { execute(session, "rm -f ${shellQuote(temporary)}", 16 * 1024) }
            throw error
        }
    }

    private fun upload(session: Session, command: String, data: ByteArray, timeoutMs: Long) {
        val channel = session.openChannel("exec") as ChannelExec
        val error = BoundedOutput(16 * 1024)
        channel.setCommand(command)
        channel.setErrStream(error, true)
        channel.setInputStream(ByteArrayInputStream(data))
        try {
            channel.connect(CONNECT_TIMEOUT_MS)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                error.failure?.let { throw it }
                Thread.sleep(20)
            }
            error.failure?.let { throw it }
            check(channel.isClosed) { "写入 Kindle 封面超时" }
            check(channel.exitStatus == 0) {
                error.bytes.toString(Charsets.UTF_8).trim().ifBlank { "写入 Kindle 封面失败 (${channel.exitStatus})" }
            }
        } finally { channel.disconnect() }
    }

    private fun download(
        session: Session,
        command: String,
        destination: File,
        maximumBytes: Long,
        timeoutMs: Long
    ) {
        val channel = session.openChannel("exec") as ChannelExec
        val error = BoundedOutput(16 * 1024)
        channel.setCommand(command)
        channel.setErrStream(error, true)
        destination.outputStream().use { file ->
            val output = BoundedFileOutput(file, maximumBytes)
            // JSch must own the stream before connect() starts delivering stdout packets.
            channel.setOutputStream(output, true)
            try {
                channel.connect(CONNECT_TIMEOUT_MS)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    output.failure?.let { throw it }
                    error.failure?.let { throw it }
                    Thread.sleep(20)
                }
                output.failure?.let { throw it }
                error.failure?.let { throw it }
                check(channel.isClosed) { "下载 Kindle 书籍超时" }
                check(channel.exitStatus == 0) {
                    error.bytes.toString(Charsets.UTF_8).trim().ifBlank { "下载 Kindle 书籍失败 (${channel.exitStatus})" }
                }
            } finally { channel.disconnect() }
        }
    }

    private fun archiveLegacyClippingsBackups(session: Session) {
        val backups = execute(session, LibraryBackupMaintenance.listCommand, MAX_LIST_BYTES)
            .toString(Charsets.UTF_8).split('\u0000')
            .filter(LibraryBackupMaintenance::isLegacyClippingsBackup).distinct()
        // Maintenance is best-effort: failure to move one old backup must not prevent
        // books from syncing or stop the remaining backups from being archived.
        backups.forEach { backup ->
            optionalMetadata {
                execute(session, LibraryBackupMaintenance.archiveCommand(backup), 16 * 1024)
            }
        }
    }

    private fun readCatalog(file: File): Map<String, CatalogEntry> {
        val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return database.use { db ->
            val columns = db.rawQuery("PRAGMA table_info(Entries)", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            require("p_location" in columns)
            val names = listOf("p_location", "p_titles_0_nominal", "p_credits_0_name_collation", "p_thumbnail", "p_percentFinished", "p_contentSize")
            val selected = names.joinToString(",") { if (it in columns) it else "NULL" }
            db.rawQuery("SELECT $selected FROM Entries WHERE p_location IS NOT NULL", null).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        val path = LibraryRules.normalizeRemotePath(cursor.getString(0))
                        if (!LibraryRules.isBookPath(path)) continue
                        fun text(index: Int) = if (cursor.isNull(index)) null else cursor.getString(index)
                        put(path, CatalogEntry(text(1), text(2), LibraryRules.thumbnailPath(text(3)),
                            if (cursor.isNull(4)) null else cursor.getDouble(4),
                            if (cursor.isNull(5)) null else cursor.getLong(5)))
                    }
                }
            }
        }
    }

    private fun cacheCover(session: Session, thumbnail: String, directory: File, force: Boolean = false): String? {
        val filename = MessageDigest.getInstance("SHA-256").digest(thumbnail.toByteArray())
            .joinToString("") { "%02x".format(it) } + ".jpg"
        val destination = directory.resolve("covers/$filename")
        if (!force && usableCover(destination)) return destination.absolutePath
        AtomicCache.writeValidated(destination,
            writer = { it.write(execute(session, "cat ${shellQuote(thumbnail)}", MAX_COVER_BYTES)) },
            validate = { require(usableCover(it)) { "Kindle 封面无法读取" } })
        return destination.absolutePath
    }

    private fun usableCover(file: File): Boolean {
        if (!file.isFile) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return LibraryRules.isUsableCoverSize(options.outWidth, options.outHeight)
    }

    private fun saveSnapshot(directory: File, snapshot: LibrarySnapshot) {
        val array = JSONArray()
        snapshot.books.forEach { book ->
            array.put(JSONObject().apply {
                put("remotePath", book.remotePath)
                put("title", book.title)
                book.author?.let { put("author", it) }
                book.coverPath?.let { put("cover", File(it).name) }
                book.percentFinished?.let { put("percentFinished", it) }
                book.contentSize?.let { put("contentSize", it) }
            })
        }
        val data = JSONObject().put("sourceKey", snapshot.sourceKey)
            .put("syncedAt", snapshot.syncedAt.toString()).put("books", array).toString().toByteArray()
        AtomicCache.write(directory.resolve("snapshot.json")) { it.write(data) }
    }

    private fun execute(
        session: Session,
        command: String,
        maximumBytes: Int,
        timeoutMs: Long = COMMAND_TIMEOUT_MS
    ): ByteArray {
        val channel = session.openChannel("exec") as ChannelExec
        val output = BoundedOutput(maximumBytes)
        val error = BoundedOutput(16 * 1024)
        channel.setCommand(command)
        channel.setOutputStream(output, true)
        channel.setErrStream(error, true)
        try {
            channel.connect(CONNECT_TIMEOUT_MS)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                output.failure?.let { throw it }
                Thread.sleep(20)
            }
            output.failure?.let { throw it }
            check(channel.isClosed) { "读取 Kindle 书库超时" }
            check(channel.exitStatus == 0) {
                error.bytes.toString(Charsets.UTF_8).trim().ifBlank { "读取 Kindle 书库失败 (${channel.exitStatus})" }
            }
            return output.bytes
        } finally { channel.disconnect() }
    }

    private class BoundedOutput(private val maximum: Int) : OutputStream() {
        private val output = ByteArrayOutputStream()
        @Volatile var failure: IOException? = null
            private set
        val bytes: ByteArray get() = output.toByteArray()
        override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
        override fun write(data: ByteArray, offset: Int, length: Int) {
            if (output.size().toLong() + length > maximum) {
                val error = IOException("Kindle 书库文件超过允许的大小")
                failure = error
                throw error
            }
            output.write(data, offset, length)
        }
    }

    /** Streams a potentially large book to disk without holding it in memory twice. */
    private class BoundedFileOutput(
        private val output: OutputStream,
        private val maximum: Long
    ) : OutputStream() {
        private var written = 0L
        @Volatile var failure: IOException? = null
            private set
        override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
        override fun write(data: ByteArray, offset: Int, length: Int) {
            if (written + length > maximum) {
                val error = IOException("Kindle 书库文件超过允许的大小")
                failure = error
                throw error
            }
            output.write(data, offset, length)
            written += length
        }
    }

    private inline fun <T> optionalMetadata(block: () -> T): T? = try { block() }
        catch (interrupted: InterruptedException) { throw interrupted }
        catch (_: Exception) { null }

    private fun shellQuote(value: String) = "'${value.replace("'", "'\\''")}'"
    private fun JSONObject.optionalString(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    private fun JSONObject.optionalDouble(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key).takeIf(Double::isFinite) else null
    private data class CatalogEntry(val title: String?, val author: String?, val thumbnail: String?, val percent: Double?, val size: Long?)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val COMMAND_TIMEOUT_MS = 60_000L
        const val BOOK_TRANSFER_TIMEOUT_MS = 300_000L
        const val THUMBNAIL_TRANSFER_TIMEOUT_MS = 120_000L
        const val SERVER_ALIVE_INTERVAL_MS = 15_000
        const val SERVER_ALIVE_COUNT_MAX = 3
        const val MAX_LIST_BYTES = 8 * 1024 * 1024
        const val MAX_CATALOG_BYTES = 32 * 1024 * 1024
        const val MAX_COVER_BYTES = 2 * 1024 * 1024
        const val MAX_THUMBNAIL_BYTES = 4 * 1024 * 1024
    }
}
