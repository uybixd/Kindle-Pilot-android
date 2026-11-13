package com.hongxiang.kpilot.notes

import android.content.Context
import java.io.IOException
import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.data.cacheScope
import com.hongxiang.kpilot.ssh.KindleSshSessionFactory
import com.hongxiang.kpilot.support.SyncTrace
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import java.io.FilterOutputStream
import java.io.OutputStream
import java.time.Instant

class ClippingsSyncService(context: Context) {
    private val cacheDirectory = context.noBackupFilesDir.resolve("notes")
    private fun cacheFile(settings: ConnectionSettings) = cacheDirectory.resolve("${settings.cacheScope()}/My Clippings.txt")

    /** Assign the pre-device-namespace cache to the previously saved connection once. */
    fun migrateLegacyCache(settings: ConnectionSettings) {
        if (settings.host.isBlank()) return
        val legacy = cacheDirectory.resolve("My Clippings.txt")
        val destination = cacheFile(settings)
        if (!legacy.isFile || destination.exists()) return
        check(destination.parentFile?.let { it.isDirectory || it.mkdirs() } == true && legacy.renameTo(destination)) {
            "无法迁移原有摘抄缓存，原文件仍然保留"
        }
    }

    fun sync(
        settings: ConnectionSettings,
        onProgress: (downloaded: Long, total: Long?) -> Unit
    ): ClippingsSnapshot {
        val span = SyncTrace.begin("notes")
        var bytes = 0L
        var items = -1
        var parsed: ClippingsParseResult? = null
        try {
            return withSession(settings, span) { session ->
                val cacheFile = cacheFile(settings)
                // Deliberately mirrors the proven Mac protocol: one quoted cat stream.
                // Do not require wc/stat: Kindle BusyBox variants are not consistent.
                try {
                    AtomicCache.writeValidated(
                        destination = cacheFile,
                        writer = { output ->
                            span.stage("download") {
                                download(session, shellQuote(REMOTE_PATH), CountingOutputStream(output) { current ->
                                    onProgress(current, null)
                                })
                            }
                        },
                        validate = { temporary ->
                            span.stage("validate") {
                                val result = ClippingsParser.parseDetailed(
                                    ClippingsTextDecoder.decode(temporary.readBytes()),
                                    includeSourceHighlights = true
                                )
                                ClippingsValidation.issue(result)?.let { throw ClippingsFormatException(it) }
                                parsed = result
                            }
                        }
                    )
                } catch (error: NotesSyncException) {
                    throw error
                } catch (error: AtomicCacheException) {
                    val format = error.cause as? ClippingsFormatException
                    throw NotesSyncException(
                        format?.let { NotesDiagnostics.format(it.issue) } ?: NotesDiagnostics.cache(error.stage),
                        error
                    )
                }
                bytes = cacheFile.length()
                span.stage("parse") {
                    // Reuse the parse performed during validation instead of reading and
                    // decoding the same file a second time.
                    val result = checkNotNull(parsed) { "Clippings validation result missing" }
                    ClippingsSnapshot(
                        items = result.items,
                        syncedAt = Instant.now(),
                        sourceBytes = bytes,
                        filteredDuplicateCount = result.filteredDuplicateCount,
                        filteredSingleSelectionCount = result.filteredSingleSelectionCount,
                        sourceHighlights = result.sourceHighlights
                    )
                }.also { items = it.items.size }
            }
        } catch (error: NotesSyncException) {
            throw error
        } catch (error: AtomicCacheException) {
            throw NotesSyncException(NotesDiagnostics.cache(error.stage), error)
        } catch (error: Exception) {
            throw NotesSyncException(NotesDiagnostics.unknown(), error)
        } finally {
            span.end("bytes=$bytes items=$items")
        }
    }

    fun loadCached(settings: ConnectionSettings): ClippingsSnapshot? {
        val file = cacheFile(settings)
        return if (file.isFile) parseCache(file, Instant.ofEpochMilli(file.lastModified()), highlightStates(settings)) else null
    }

    private fun removedFile(settings: ConnectionSettings) = cacheFile(settings).resolveSibling("removed-highlights")

    private fun highlightStates(settings: ConnectionSettings): Map<String, Boolean> {
        val file = removedFile(settings)
        if (!file.isFile) return emptyMap()
        return file.readLines().filter { it.isNotBlank() }.associate { line ->
            line.removePrefix("+") to line.startsWith("+")
        }
    }

    internal fun rememberRemovedHighlights(settings: ConnectionSettings, checked: Set<String>, removed: Set<String>) {
        val existing = highlightStates(settings)
        val updated = existing + checked.associateWith { it !in removed }
        if (updated != existing) AtomicCache.write(removedFile(settings)) { output ->
            output.write(updated.toSortedMap().entries.joinToString("\n") { (id, present) ->
                if (present) "+$id" else id
            }.toByteArray())
        }
    }

    internal fun hideRemovedHighlights(settings: ConnectionSettings, snapshot: ClippingsSnapshot): ClippingsSnapshot {
        return parseCache(cacheFile(settings), snapshot.syncedAt, highlightStates(settings))
    }

    fun importWrittenBytes(settings: ConnectionSettings, bytes: ByteArray): ClippingsSnapshot {
        val file = cacheFile(settings)
        AtomicCache.writeValidated(file, { it.write(bytes) }, { temporary ->
            check(ClippingsValidation.inspect(temporary.readBytes()).second == null) { "写回后的摘抄缓存格式无效" }
        })
        return parseCache(file, Instant.now(), highlightStates(settings))
    }

    private fun parseCache(
        cacheFile: java.io.File,
        syncedAt: Instant,
        states: Map<String, Boolean> = emptyMap(),
        includeSourceHighlights: Boolean = false
    ): ClippingsSnapshot {
        val bytes = cacheFile.readBytes()
        val result = ClippingsParser.parseDetailed(
            ClippingsTextDecoder.decode(bytes),
            states.filterValues { !it }.keys,
            states.filterValues { it }.keys,
            includeSourceHighlights
        )
        return ClippingsSnapshot(
            items = result.items,
            syncedAt = syncedAt,
            sourceBytes = bytes.size.toLong(),
            filteredDuplicateCount = result.filteredDuplicateCount,
            filteredSingleSelectionCount = result.filteredSingleSelectionCount,
            sourceHighlights = result.sourceHighlights
        )
    }

    private fun <T> withSession(settings: ConnectionSettings, span: SyncTrace, block: (Session) -> T): T {
        settings.validate()
        val session = KindleSshSessionFactory.create(settings)
        return try {
            try {
                span.stage("connect") { session.connect(CONNECT_TIMEOUT_MS) }
            } catch (error: JSchException) {
                throw NotesSyncException(NotesDiagnostics.connect(error.message), error)
            }
            block(session)
        } finally {
            session.disconnect()
        }
    }

    private fun download(session: Session, quotedPath: String, output: OutputStream) {
        execute(session, "cat $quotedPath", output)
    }

    private fun execute(session: Session, command: String, output: OutputStream) {
        val channel = session.openChannel("exec") as ChannelExec
        val error = java.io.ByteArrayOutputStream()
        channel.setCommand(command)
        // JSch otherwise closes our FileOutputStream as soon as the channel exits.
        // AtomicCache must retain ownership so it can flush/fsync/close in order.
        channel.setOutputStream(output, true)
        channel.setErrStream(error)
        try {
            try {
                channel.connect(CONNECT_TIMEOUT_MS)
            } catch (error: JSchException) {
                throw NotesSyncException(NotesDiagnostics.transferChannel(), error)
            }
            val deadline = System.currentTimeMillis() + DOWNLOAD_TIMEOUT_MS
            while (!channel.isClosed && System.currentTimeMillis() < deadline) Thread.sleep(25)
            if (!channel.isClosed) throw NotesSyncException(NotesDiagnostics.timeout())
            if (channel.exitStatus != 0) {
                throw NotesSyncException(
                    NotesDiagnostics.remoteFailure(channel.exitStatus, error.toString("UTF-8"))
                )
            }
        } finally {
            channel.disconnect()
        }
    }

    private class CountingOutputStream(
        output: OutputStream,
        private val progress: (Long) -> Unit
    ) : FilterOutputStream(output) {
        private var count = 0L
        override fun write(value: Int) {
            out.write(value)
            report(1)
        }
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            report(length.toLong())
        }
        private fun report(delta: Long) {
            count += delta
            progress(count)
        }
    }

    private fun shellQuote(value: String) = "'${value.replace("'", "'\\''")}'"

    private companion object {
        const val REMOTE_PATH = "/mnt/us/documents/My Clippings.txt"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val DOWNLOAD_TIMEOUT_MS = 5 * 60 * 1000L
    }
}

data class NotesDiagnostic(val code: String, val stage: String, val message: String)

class NotesSyncException(val diagnostic: NotesDiagnostic, cause: Throwable? = null) :
    Exception(diagnostic.message, cause)

class ClippingsFormatException(val issue: ClippingsFormatIssue) :
    IOException("Downloaded clippings did not pass structural validation")

object NotesDiagnostics {
    fun remoteFailure(exitCode: Int, stderr: String): NotesDiagnostic {
        val normalized = stderr.lowercase()
        return when {
            normalized.contains("no such file") || normalized.contains("not found") ->
                NotesDiagnostic("N-REMOTE-404", "Remote file", "My Clippings.txt was not found on this Kindle. Create a highlight or note on Kindle, then try again.")
            normalized.contains("permission denied") || normalized.contains("operation not permitted") ->
                NotesDiagnostic("N-REMOTE-403", "Remote file", "Kindle denied access to My Clippings.txt. Check UsbNet SSH permissions.")
            exitCode == 255 -> NotesDiagnostic("N-REMOTE-255", "Remote command", "The SSH connection closed before Notes could be downloaded. Run Test connection in Settings.")
            else -> NotesDiagnostic("N-REMOTE-EXIT", "Remote command", "Kindle could not provide My Clippings.txt. Confirm the file exists and SSH can read it.")
        }
    }

    fun connect(rawMessage: String?): NotesDiagnostic {
        val normalized = rawMessage.orEmpty().lowercase()
        return when {
            normalized.contains("auth fail") || normalized.contains("authentication") ->
                NotesDiagnostic("N-CONNECT-AUTH", "SSH authentication", "SSH authentication failed. Check the Kindle username and password in Settings.")
            normalized.contains("timeout") || normalized.contains("socket") || normalized.contains("connection") ->
                NotesDiagnostic("N-CONNECT-REACH", "SSH connection", "Could not reach Kindle over SSH. Confirm it is online, then run Test connection in Settings.")
            else -> NotesDiagnostic("N-CONNECT-START", "SSH connection", "Could not start the Notes SSH connection. Check Settings and try Test connection.")
        }
    }

    fun transferChannel() = NotesDiagnostic("N-TRANSFER-CHANNEL", "Transfer", "SSH connected, but the Notes transfer could not start. Try again.")
    fun timeout() = NotesDiagnostic("N-TRANSFER-TIMEOUT", "Transfer", "Notes download timed out. The previous synced notes were kept.")
    fun cache(stage: AtomicCacheStage): NotesDiagnostic = when (stage) {
        AtomicCacheStage.DIRECTORY -> NotesDiagnostic("N-CACHE-DIR", "Phone cache directory", "Notes downloaded, but the private cache folder was unavailable. The previous cache was kept.")
        AtomicCacheStage.TEMP_CREATE -> NotesDiagnostic("N-CACHE-TEMP", "Phone cache preparation", "Notes downloaded, but the phone could not prepare a private temporary cache. The previous cache was kept.")
        AtomicCacheStage.WRITE -> NotesDiagnostic("N-CACHE-WRITE", "Phone cache write", "Notes downloaded, but the private temporary cache could not be written. The previous cache was kept.")
        AtomicCacheStage.FSYNC -> NotesDiagnostic("N-CACHE-FSYNC", "Phone cache storage", "Notes downloaded, but Android could not safely finish storing the temporary cache. The previous cache was kept.")
        AtomicCacheStage.VALIDATE -> NotesDiagnostic("N-CACHE-VALIDATE", "Phone cache validation", "The downloaded Notes cache could not be validated. The previous cache was kept.")
        AtomicCacheStage.COMMIT -> NotesDiagnostic("N-CACHE-COMMIT", "Phone cache commit", "Notes downloaded, but Android could not replace the private cache. The previous cache was kept.")
    }
    fun format(issue: ClippingsFormatIssue): NotesDiagnostic = when (issue.reason) {
        ClippingsFormatIssue.Reason.INCOMPLETE -> NotesDiagnostic(
            "N-FORMAT-INCOMPLETE", "Notes format",
            "The download did not end with a complete Kindle clipping separator, so the previous cache was kept. Detected ${issue.recordBlocks} record block(s)."
        )
        ClippingsFormatIssue.Reason.UNRECOGNIZED -> NotesDiagnostic(
            "N-FORMAT-UNRECOGNIZED", "Notes format",
            "The downloaded file had ${issue.recordBlocks} record block(s), but none matched a supported Kindle clipping format. The previous cache was kept."
        )
    }
    fun unknown() = NotesDiagnostic("N-UNKNOWN", "Synchronization", "Notes synchronization failed at an unknown stage. Run Test connection, then retry and share this diagnostic code.")
}
