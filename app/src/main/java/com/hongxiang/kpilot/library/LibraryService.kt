package com.hongxiang.kpilot.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.data.cacheScope
import com.hongxiang.kpilot.notes.AtomicCache
import com.hongxiang.kpilot.ssh.KindleSshSessionFactory
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant

/** Read-only device library synchronization. Call these blocking functions on an IO dispatcher. */
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
        val books = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val remotePath = item.getString("remotePath")
            require(LibraryRules.isBookPath(remotePath))
            val coverName = item.optionalString("cover")?.takeIf { it.matches(Regex("[a-f0-9]{64}\\.jpg")) }
            KindleBook(
                remotePath = remotePath,
                title = item.getString("title"),
                author = item.optionalString("author"),
                coverPath = coverName?.let { directory.resolve("covers/$it") }?.takeIf(File::isFile)?.absolutePath,
                percentFinished = item.optionalDouble("percentFinished")?.coerceIn(0.0, 100.0),
                contentSize = if (item.has("contentSize")) item.getLong("contentSize") else null
            )
        }
        LibrarySnapshot(books, Instant.parse(root.getString("syncedAt")), scope)
    }.getOrNull()

    fun sync(settings: ConnectionSettings): LibrarySnapshot {
        settings.validate()
        val scope = settings.cacheScope()
        val directory = cacheRoot.resolve(scope)
        check(directory.isDirectory || directory.mkdirs()) { "无法创建书库缓存目录" }
        val oldBooks = loadCached(settings)?.books.orEmpty().associateBy { it.remotePath }
        val session = KindleSshSessionFactory.create(settings)
        try {
            session.connect(CONNECT_TIMEOUT_MS)
            val predicates = LibraryRules.supportedExtensions.sorted().joinToString(" -o ") {
                "-iname ${shellQuote("*.$it")}"
            }
            val command = "test -d /mnt/us/documents && find /mnt/us/documents -type d -iname '*.sdr' -prune -o -type f \\( $predicates \\) -print0"
            val paths = execute(session, command, MAX_LIST_BYTES).toString(Charsets.UTF_8)
                .split('\u0000').filter(LibraryRules::isBookPath).distinct()
            val entries = optionalMetadata {
                val temporary = File.createTempFile("catalog-", ".sqlite", directory)
                try {
                    temporary.writeBytes(execute(session, "cat /var/local/cc.db", MAX_CATALOG_BYTES))
                    readCatalog(temporary)
                } finally { temporary.delete() }
            }.orEmpty()
            val books = paths.map { path ->
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
            val snapshot = LibrarySnapshot(books, Instant.now(), scope)
            saveSnapshot(directory, snapshot)
            return snapshot
        } finally { session.disconnect() }
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

    private fun cacheCover(session: Session, thumbnail: String, directory: File): String? {
        val filename = MessageDigest.getInstance("SHA-256").digest(thumbnail.toByteArray())
            .joinToString("") { "%02x".format(it) } + ".jpg"
        val destination = directory.resolve("covers/$filename")
        if (usableCover(destination)) return destination.absolutePath
        AtomicCache.writeValidated(destination,
            writer = { it.write(execute(session, "cat ${shellQuote(thumbnail)}", MAX_COVER_BYTES)) },
            validate = { require(usableCover(it)) { "Kindle 封面无法读取" } })
        return destination.absolutePath
    }

    private fun usableCover(file: File): Boolean {
        if (!file.isFile) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth >= 32 && options.outHeight >= 32
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

    private fun execute(session: Session, command: String, maximumBytes: Int): ByteArray {
        val channel = session.openChannel("exec") as ChannelExec
        val output = BoundedOutput(maximumBytes)
        val error = BoundedOutput(16 * 1024)
        channel.setCommand(command)
        channel.setOutputStream(output, true)
        channel.setErrStream(error, true)
        try {
            channel.connect(CONNECT_TIMEOUT_MS)
            val deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
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
        const val MAX_LIST_BYTES = 8 * 1024 * 1024
        const val MAX_CATALOG_BYTES = 32 * 1024 * 1024
        const val MAX_COVER_BYTES = 2 * 1024 * 1024
    }
}
