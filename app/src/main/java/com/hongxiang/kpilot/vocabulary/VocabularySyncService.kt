package com.hongxiang.kpilot.vocabulary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.data.cacheScope
import com.hongxiang.kpilot.notes.AtomicCache
import com.hongxiang.kpilot.notes.AtomicCacheException
import com.hongxiang.kpilot.notes.AtomicCacheStage
import com.hongxiang.kpilot.ssh.KindleSshSessionFactory
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import java.io.FilterOutputStream
import java.io.File
import java.io.OutputStream
import java.time.Instant

class VocabularySyncService(context: Context) {
    private val directory = context.noBackupFilesDir.resolve("vocabulary")
    private fun cacheFile(settings: ConnectionSettings) = directory.resolve("${settings.cacheScope()}/vocab.db")
    fun migrateLegacyCache(settings: ConnectionSettings) {
        if (settings.host.isBlank()) return
        val original = directory.resolve("vocab.db"); val destination = cacheFile(settings)
        if (original.isFile && !destination.exists()) {
            check(destination.parentFile?.let { it.isDirectory || it.mkdirs() } == true && original.renameTo(destination))
        }
    }

    fun sync(settings: ConnectionSettings, onProgress: (Long, Long?) -> Unit): VocabularySnapshot = try {
        withSession(settings) { session ->
            val cacheFile = cacheFile(settings)
            var validatedLookups: List<VocabularyLookup>? = null
            AtomicCache.writeValidated(
                destination = cacheFile,
                writer = { output ->
                    execute(session, "cat ${quote(REMOTE_PATH)}", CountingOutputStream(output) { onProgress(it, null) })
                },
                validate = { temporary -> validatedLookups = VocabularyDatabase.read(temporary) }
            )
            VocabularySnapshot(validatedLookups.orEmpty(), Instant.now(), cacheFile.length())
        }
    } catch (error: AtomicCacheException) {
        throw VocabularySyncException(VocabularyDiagnostics.cache(error.stage), error)
    } catch (error: VocabularySyncException) {
        throw error
    } catch (error: Exception) {
        throw VocabularySyncException(VocabularyDiagnostics.unknown(), error)
    }

    fun loadCached(settings: ConnectionSettings): VocabularySnapshot? {
        val file = cacheFile(settings)
        return if (file.isFile) VocabularySnapshot(VocabularyDatabase.read(file), Instant.ofEpochMilli(file.lastModified()), file.length()) else null
    }

    fun importBytes(settings: ConnectionSettings, bytes: ByteArray): VocabularySnapshot {
        val file = cacheFile(settings)
        var lookups: List<VocabularyLookup> = emptyList()
        AtomicCache.writeValidated(file, { it.write(bytes) }, { lookups = VocabularyDatabase.read(it) })
        return VocabularySnapshot(lookups, Instant.now(), file.length())
    }

    private fun <T> withSession(settings: ConnectionSettings, block: (Session) -> T): T {
        settings.validate()
        val session = KindleSshSessionFactory.create(settings)
        return try {
            session.connect(CONNECT_TIMEOUT_MS)
            block(session)
        } finally { session.disconnect() }
    }

    private fun execute(session: Session, command: String, output: OutputStream) {
        val channel = session.openChannel("exec") as ChannelExec
        val error = java.io.ByteArrayOutputStream()
        channel.setCommand(command)
        // AtomicCache owns this stream and must flush/fsync/close it after SSH exits.
        channel.setOutputStream(output, KEEP_CACHE_OUTPUT_OPEN)
        channel.setErrStream(error)
        try {
            channel.connect(CONNECT_TIMEOUT_MS)
            val deadline = System.currentTimeMillis() + DOWNLOAD_TIMEOUT_MS
            while (!channel.isClosed && System.currentTimeMillis() < deadline) Thread.sleep(25)
            if (!channel.isClosed) throw VocabularySyncException(VocabularyDiagnostics.timeout())
            if (channel.exitStatus != 0) throw VocabularySyncException(VocabularyDiagnostics.remote())
        } finally { channel.disconnect() }
    }

    private class CountingOutputStream(output: OutputStream, private val progress: (Long) -> Unit) : FilterOutputStream(output) {
        private var count = 0L
        override fun write(value: Int) { out.write(value); report(1) }
        override fun write(buffer: ByteArray, offset: Int, length: Int) { out.write(buffer, offset, length); report(length.toLong()) }
        private fun report(delta: Long) { count += delta; progress(count) }
    }

    private fun quote(value: String) = "'${value.replace("'", "'\\''")}'"

    private companion object {
        const val REMOTE_PATH = "/mnt/us/system/vocabulary/vocab.db"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val DOWNLOAD_TIMEOUT_MS = 5 * 60 * 1000L
    }
}

internal const val KEEP_CACHE_OUTPUT_OPEN = true

data class VocabularyDiagnostic(val code: String, val stage: String, val message: String)

class VocabularySyncException(val diagnostic: VocabularyDiagnostic, cause: Throwable? = null) :
    Exception(diagnostic.message, cause)

object VocabularyDiagnostics {
    fun cache(stage: AtomicCacheStage): VocabularyDiagnostic = when (stage) {
        AtomicCacheStage.DIRECTORY -> VocabularyDiagnostic("V-CACHE-DIR", "Phone cache directory", "The private vocabulary cache folder was unavailable. The previous cache was kept.")
        AtomicCacheStage.TEMP_CREATE -> VocabularyDiagnostic("V-CACHE-TEMP", "Phone cache preparation", "Android could not prepare the vocabulary cache. The previous cache was kept.")
        AtomicCacheStage.WRITE -> VocabularyDiagnostic("V-CACHE-WRITE", "Phone cache write", "The vocabulary download could not be stored. The previous cache was kept.")
        AtomicCacheStage.FSYNC -> VocabularyDiagnostic("V-CACHE-FSYNC", "Phone cache storage", "Android could not safely finish storing vocabulary. The previous cache was kept.")
        AtomicCacheStage.VALIDATE -> VocabularyDiagnostic("V-SCHEMA", "Database validation", "This Kindle vocabulary database format is not supported. The previous cache was kept.")
        AtomicCacheStage.COMMIT -> VocabularyDiagnostic("V-CACHE-COMMIT", "Phone cache commit", "Android could not replace the vocabulary cache. The previous cache was kept.")
    }
    fun timeout() = VocabularyDiagnostic("V-TRANSFER-TIMEOUT", "Transfer", "Vocabulary download timed out. The previous cache was kept.")
    fun remote() = VocabularyDiagnostic("V-REMOTE", "Remote database", "Kindle could not provide its vocabulary database. Confirm Vocabulary Builder has been used, then retry.")
    fun unknown() = VocabularyDiagnostic("V-UNKNOWN", "Synchronization", "Vocabulary synchronization failed. Test the connection, then retry and share this diagnostic code.")
}

internal object VocabularyDatabase {
    fun read(file: File): List<VocabularyLookup> {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (_: SQLiteException) {
            error("Could not open this Kindle vocabulary database. The previous cache was kept.")
        }
        return try {
            VocabularySchema.validate(readSchema(db))
            readRows(db).filterNot(::isChinese)
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: SQLiteException) {
            error("This Kindle vocabulary database could not be queried safely. Its schema may differ; the previous cache was kept.")
        } finally { db.close() }
    }

    private fun readSchema(db: SQLiteDatabase): Map<String, Set<String>> {
        val tables = mutableListOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        return tables.associateWith { table ->
            db.rawQuery("PRAGMA table_info(\"${table.replace("\"", "\"\"")}\")", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
        }
    }

    private fun readRows(db: SQLiteDatabase): List<VocabularyLookup> {
        val query = """SELECT l.id AS lookup_id, l.word_key, w.word, w.stem, w.lang AS word_lang,
            b.title AS book_title, b.authors, l.pos, l.usage, l.timestamp
            FROM LOOKUPS l JOIN WORDS w ON l.word_key = w.id JOIN BOOK_INFO b ON l.book_key = b.id
            ORDER BY lower(w.word), l.timestamp, l.pos"""
        return db.rawQuery(query, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val values = (0 until cursor.columnCount).associate { index ->
                        cursor.getColumnName(index) to when (cursor.getType(index)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                            android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                            else -> null
                        }
                    }
                    add(VocabularyRowMapper.from(values))
                }
            }
        }
    }

    private fun isChinese(row: VocabularyLookup): Boolean = row.wordLanguage.startsWith("zh", true) ||
        (row.word + row.stem).any { it.code in 0x3400..0x9FFF || it.code in 0xF900..0xFAFF }
}
