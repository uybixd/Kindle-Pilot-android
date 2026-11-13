package com.hongxiang.kpilot.notes.writeback

import android.content.Context
import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.notes.ClippingKind
import com.hongxiang.kpilot.notes.ClippingsValidation
import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.notes.NoteDraft
import com.hongxiang.kpilot.support.SyncTrace
import java.security.MessageDigest
import java.util.UUID

data class WritebackResult(
    val clippingsBytes: ByteArray,
    val sidecarPath: String,
    val backupPaths: List<String>
)

/** Isolated transport makes the complete two-file transaction testable without a Kindle. */
internal interface NoteWritebackConnection : AutoCloseable {
    fun exec(command: String): String
    fun download(path: String): ByteArray
    fun downloadMany(paths: List<String>): List<ByteArray> = paths.map(::download)
    fun replace(path: String, original: ByteArray, updated: ByteArray, backup: String)
    fun restore(path: String, backup: String, written: ByteArray)
}

class KindleNoteWritebackService internal constructor(
    private val connectionFactory: (ConnectionSettings) -> NoteWritebackConnection,
    private val now: () -> Long = System::currentTimeMillis
) {
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this({ SshNoteWritebackConnection(it) })

    fun write(settings: ConnectionSettings, clipping: KindleClipping, text: String): WritebackResult {
        settings.validate()
        return connectionFactory(settings).use { connection -> write(connection, clipping, text) }
    }

    internal fun write(connection: NoteWritebackConnection, clipping: KindleClipping, text: String): WritebackResult {
        val normalizedText = validateText(text)
        ensureReaderClosed(connection)
        return commit(connection, prepare(connection, clipping), normalizedText)
    }

    internal fun connect(settings: ConnectionSettings): NoteWritebackConnection {
        settings.validate()
        return connectionFactory(settings)
    }

    internal fun syncDraft(
        connection: NoteWritebackConnection,
        clipping: KindleClipping,
        draft: NoteDraft,
        choice: NoteConflictChoice?
    ): NoteDraftSyncResult {
        ensureReaderClosed(connection)
        val prepared = prepare(connection, clipping)
        val match = prepared.match
        require(AnnotationMatcher.sameCreationTime(match.highlight, prepared.clipping)) {
            "这条草稿对应的 Kindle 划线已改变或无法核对，已保留草稿"
        }
        val notes = currentNotes(match)
        val kindle = notes.singleOrNull()?.note?.let(::normalizedNoteText).orEmpty()
        val phone = normalizedNoteText(draft.text)
        val base = draft.kindleBaseText?.let(::normalizedNoteText)
        val version = MessageDigest.getInstance("SHA-256")
            .digest(listOf(match.candidate.path, match.highlight, notes).toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val conflict = NoteConflict(clipping.id, draft.revision, phone, kindle, base != null, version)
        val action = if (choice == null) noteSyncAction(base, phone, kindle) else when {
            choice.conflict != conflict -> NoteSyncAction.ASK
            choice.keepPhone -> NoteSyncAction.WRITE_PHONE
            else -> NoteSyncAction.KEEP_KINDLE
        }
        return when (action) {
            NoteSyncAction.ASK -> NoteDraftSyncResult.Conflict(conflict)
            NoteSyncAction.KEEP_KINDLE -> NoteDraftSyncResult.Accepted(kindle)
            NoteSyncAction.WRITE_PHONE -> {
                if (phone == kindle) return NoteDraftSyncResult.Accepted(kindle)
                val result = commit(connection, prepared, validateText(phone), notes)
                NoteDraftSyncResult.Accepted(phone, result)
            }
        }
    }

    private fun validateText(text: String): String {
        val normalizedText = normalizedNoteText(text)
        require(!Regex("(?m)^={10,}\\s*$").containsMatchIn(normalizedText) && '\u0000' !in normalizedText) {
            "批注不能包含独立的 Kindle 记录分隔行或空字符"
        }
        require(normalizedText.toByteArray(Charsets.UTF_8).size <= 65535) { "批注内容过长（UTF-8 最多 65535 字节）" }
        return normalizedText
    }

    private fun prepare(connection: NoteWritebackConnection, clipping: KindleClipping): PreparedWrite {
        require(clipping.kind == ClippingKind.HIGHLIGHT) { "只能给已有的 Kindle 标注新增或编辑批注" }
        require(clipping.locationStart != null || clipping.locationEnd != null) { "这条标注没有可匹配的位置" }
        val originalClippings = connection.download(CLIPPINGS_PATH)
        val (snapshot, issue) = ClippingsValidation.inspect(originalClippings, setOf(clipping.id))
        require(issue == null) { "Kindle 的 My Clippings.txt 格式不完整，已停止写入" }
        val fresh = snapshot.items.firstOrNull { it.id == clipping.id }
            ?: error("Kindle 的 My Clippings.txt 中已找不到这条标注")
        val candidates = readCandidates(connection, sidecarPaths(connection), listOf(fresh), verifyRoundTrip = true)
        return PreparedWrite(fresh, AnnotationMatcher.match(candidates, fresh))
    }

    internal data class CurrentAnnotations(
        val notes: Map<String, String>,
        val removedHighlights: Set<String>,
        val checkedHighlights: Set<String>
    )

    internal fun readCurrentAnnotations(
        settings: ConnectionSettings,
        clippings: List<KindleClipping>
    ): CurrentAnnotations = connect(settings).use { readCurrentAnnotations(it, clippings) }

    internal fun readCurrentAnnotations(
        connection: NoteWritebackConnection,
        clippings: List<KindleClipping>
    ): CurrentAnnotations {
        val span = SyncTrace.begin("annotations-read")
        var sidecarCount = 0
        var candidateCount = 0
        try {
            val paths = span.stage("list_sidecars") { sidecarPaths(connection) }
                .also { sidecarCount = it.size }
            val candidates = span.stage("download_decode") {
                readCandidates(connection, paths, clippings, verifyRoundTrip = false)
            }.also { candidateCount = it.size }
            val matcher = MatchIndex(candidates, paths)
            val notes = mutableMapOf<String, String>()
            val removed = mutableSetOf<String>()
            val checked = mutableSetOf<String>()
            for (book in clippings.groupBy { it.bookId }.values) {
                val path = matcher.fullTitlePath(book.first())
                val candidate = path?.let(matcher::candidate)?.takeIf { !it.path.endsWith(".yjr", true) }
                val nativeHighlights = candidate?.highlights.orEmpty()
                val exportsByTime = book.groupingBy { it.addedAt }.eachCount()
                val missing = mutableListOf<KindleClipping>()
                var confirmedBook = false
                for (clipping in book) {
                    checkInterrupted()
                    if (candidate == null && clipping.kindleNotes.isEmpty()) continue
                    val match = try {
                        if (candidate == null) matcher.matchOrNull(clipping)
                        else {
                            // Repeated exports can share the same position. Only the creation
                            // timestamp can distinguish a deleted highlight from a re-created one.
                            val timestamp = clipping.addedAt?.toEpochMilli()
                            if (timestamp == null || exportsByTime[clipping.addedAt] != 1) continue
                            val exact = nativeHighlights.filter {
                                kotlin.math.abs(it.creationTime - timestamp) < 1000
                            }
                            require(exact.size <= 1)
                            exact.singleOrNull()?.let { AnnotationMatch(candidate, it) }
                        }
                    } catch (_: IllegalArgumentException) {
                        continue
                    }
                    if (match != null) {
                        confirmedBook = confirmedBook || candidate != null
                        checked += clipping.id
                        runCatching { currentNotes(match).singleOrNull()?.note?.let(::normalizedNoteText).orEmpty() }
                            .onSuccess { notes[clipping.id] = it }
                    } else {
                        if (clipping.addedAt != null && clipping.locationStart != null &&
                            Regex("""\d{1,2}[:：]\d{2}[:：]\d{2}""").containsMatchIn(clipping.metadata)
                        ) missing += clipping
                        if (matcher.exportedNotesAreGone(clipping)) notes[clipping.id] = ""
                    }
                }
                if (confirmedBook) {
                    missing.forEach {
                        checked += it.id
                        removed += it.id
                    }
                }
            }
            span.mark("match")
            return CurrentAnnotations(notes, removed, checked)
        } finally {
            span.end("sidecars=$sidecarCount candidates=$candidateCount clips=${clippings.size}")
        }
    }

    private fun sidecarPaths(connection: NoteWritebackConnection): List<String> =
        connection.exec(LIST_SIDECARS).lineSequence().map(String::trim)
            .filter { it.startsWith("/mnt/us/documents/") && !it.contains('\u0000') }
            .filter { it.endsWith(".azw3r", true) || it.endsWith(".mbp1", true) || it.endsWith(".yjr", true) }
            .distinct().toList()

    private fun readCandidates(
        connection: NoteWritebackConnection,
        paths: List<String>,
        clippings: List<KindleClipping>,
        verifyRoundTrip: Boolean
    ): List<SidecarCandidate> {
        require(paths.isNotEmpty()) { "Kindle 中没有找到 AZW3、MOBI 或 KFX 批注文件" }
        val clippingKeys = clippings.map(::ClippingKey)
        // KFX records carry no usable reading offsets, so reject unrelated files by title.
        // AZW3/MOBI must still be decoded: their sidecar name can differ from the
        // My Clippings title while the location and creation time still identify it.
        val downloadPaths = paths.filter { path ->
            checkInterrupted()
            val key = SidecarKey(path)
            !key.isKfx || clippingKeys.any { key.titleSimilarity(it) >= 0.12 }
        }
        // One SSH exec for all sidecars instead of one round trip per file. Fall back to
        // individual reads if the batch response is rejected, so one unreadable file
        // cannot fail the whole annotation refresh.
        val batched = downloadPaths.takeIf { it.isNotEmpty() }?.let { requested ->
            runCatching { connection.downloadMany(requested) }
                .getOrNull()
                ?.takeIf { it.size == requested.size }
        }
        return downloadPaths.mapIndexedNotNull { index, path ->
            checkInterrupted()
            val bytes = batched?.get(index)
                ?: runCatching { connection.download(path) }.getOrNull()
                ?: return@mapIndexedNotNull null
            runCatching {
                val store = KindleReaderDataStore.decode(bytes)
                val annotations = store.annotations()
                if (verifyRoundTrip) {
                    require(store.encoded().contentEquals(bytes)) { "Kindle sidecar 无法无损重编码" }
                }
                SidecarCandidate(path, bytes, store, annotations)
            }.getOrElse { error ->
                if (error is InterruptedException) throw error
                null // Unsupported sidecars are never modified.
            }
        }
    }

    private fun currentNotes(match: AnnotationMatch): List<SidecarAnnotation> {
        val annotations = match.candidate.annotations
        val highlights = match.candidate.highlights
        val target = match.highlight
        val notes = annotations.filter { it.type == KindleReaderDataStore.NOTE }.filter { note ->
            val exact = highlights.filter { highlight ->
                note.endPosition == highlight.endPosition &&
                    (note.startPosition == highlight.startPosition || note.startPosition == highlight.endPosition)
            }
            val owners = exact.ifEmpty { highlights.filter { it.contains(note) } }
            require(owners.isNotEmpty() || !target.mightOverlap(note)) {
                "无法确定这条 Kindle 批注的位置，已保留草稿"
            }
            require(target !in owners || owners.size == 1) { "这条 Kindle 批注可能属于多条划线，无法安全同步" }
            target in owners
        }
        require(notes.size <= 1) { "这条划线有多条 Kindle 批注，无法安全同步" }
        return notes
    }

    private fun SidecarAnnotation.contains(note: SidecarAnnotation): Boolean {
        if (note.startPosition == startPosition && note.endPosition == startPosition) return true
        val start = startPosition.toLongOrNull() ?: return false
        val end = endPosition.toLongOrNull() ?: return false
        val noteStart = note.startPosition.toLongOrNull() ?: return false
        val noteEnd = note.endPosition.toLongOrNull() ?: return false
        return noteStart >= start && noteEnd >= noteStart && noteEnd <= end
    }

    private fun SidecarAnnotation.mightOverlap(note: SidecarAnnotation): Boolean {
        val start = startPosition.toLongOrNull() ?: return true
        val end = endPosition.toLongOrNull() ?: return true
        val noteStart = note.startPosition.toLongOrNull() ?: return true
        val noteEnd = note.endPosition.toLongOrNull() ?: return true
        return noteStart <= end && noteEnd >= start
    }

    private data class PreparedWrite(val clipping: KindleClipping, val match: AnnotationMatch)

    private fun commit(
        connection: NoteWritebackConnection,
        prepared: PreparedWrite,
        normalizedText: String,
        verifiedNotes: List<SidecarAnnotation>? = null
    ): WritebackResult {
        val (fresh, match) = prepared
        val originalStore = match.candidate.store
        val updatedStore = KindleReaderDataStore.decode(match.candidate.bytes)
        val newNote = updatedStore.updateNote(match.highlight, fresh.kindleNotes.map { it.text }.toSet(),
            normalizedText, now(), match.isKfx, verifiedNotes)
        val sidecarBytes = updatedStore.encoded()
        val verifiedStore = KindleReaderDataStore.decode(sidecarBytes)
        require(verifiedStore.annotations().filter { it.type == KindleReaderDataStore.HIGHLIGHT } ==
            originalStore.annotations().filter { it.type == KindleReaderDataStore.HIGHLIGHT }) {
            "本地笔记校验失败：原标注发生变化"
        }
        if (newNote != null) require(verifiedStore.annotations().contains(newNote)) { "本地批注校验失败" }

        // Register each backup before its replace: an SSH failure can arrive after mv succeeded.
        val attempted = mutableListOf<AttemptedWrite>()
        try {
            checkInterrupted()
            ensureReaderClosed(connection)
            val sidecarBackup = backupPath(match.candidate.path)
            attempted += AttemptedWrite(match.candidate.path, sidecarBackup, sidecarBytes)
            connection.replace(match.candidate.path, match.candidate.bytes, sidecarBytes, sidecarBackup)
            require(connection.download(match.candidate.path).contentEquals(sidecarBytes)) { "Kindle 批注文件写入校验失败" }

            checkInterrupted()
            ensureReaderClosed(connection)
            // Merge into a fresh download so a note edited since staging is replaced, not duplicated.
            val latestClippings = connection.download(CLIPPINGS_PATH)
            val merged = ClippingsNoteMerger.merge(latestClippings, fresh, normalizedText, newNote, match.isKfx)
            val clippingsBackup = backupPath(CLIPPINGS_PATH)
            attempted += AttemptedWrite(CLIPPINGS_PATH, clippingsBackup, merged)
            connection.replace(CLIPPINGS_PATH, latestClippings, merged, clippingsBackup)
            val verified = connection.download(CLIPPINGS_PATH)
            require(verified.contentEquals(merged)) { "My Clippings.txt 写入校验失败" }
            // A reader opened during writing may flush stale cached annotations; do not mark the draft synced.
            ensureReaderClosed(connection)
            require(connection.download(match.candidate.path).contentEquals(sidecarBytes)) {
                "Kindle 阅读器修改了批注文件，请返回主页后重试"
            }
            checkInterrupted()
            return WritebackResult(verified, match.candidate.path, attempted.map { it.backup })
        } catch (failure: Exception) {
            // Recovery gets an uncancelled thread even when a blocking SSH call was interrupted.
            val wasInterrupted = Thread.interrupted()
            val rollbackFailures = mutableListOf<Exception>()
            attempted.asReversed().forEach { write ->
                try { connection.restore(write.path, write.backup, write.written) } catch (rollback: Exception) { rollbackFailures += rollback }
            }
            if (wasInterrupted) Thread.currentThread().interrupt()
            if (rollbackFailures.isNotEmpty()) {
                val recovery = IllegalStateException(
                    "${failure.message ?: "批注写入失败"}；自动恢复备份失败，请保留草稿。Kindle 备份：${attempted.joinToString { it.backup }}", failure)
                rollbackFailures.forEach(recovery::addSuppressed)
                throw recovery
            }
            throw failure
        }
    }

    private data class AttemptedWrite(val path: String, val backup: String, val written: ByteArray)

    private fun backupPath(path: String) = path.substringBeforeLast('/') + "/.kpilot-note-backup-${now()}-${UUID.randomUUID()}.bak"

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Kindle 笔记写入已中断")
    }

    private fun ensureReaderClosed(connection: NoteWritebackConnection) {
        val state = connection.exec(READER_STATE)
        require(readerStateAvailable(state)) { "无法确认 Kindle 阅读器状态，请回到主页后重试" }
        require(!readerIsOpen(state)) { "请退出 Kindle 阅读器，回到主页后再次写入批注" }
    }

    internal companion object {
        const val CLIPPINGS_PATH = "/mnt/us/documents/My Clippings.txt"
        const val LIST_SIDECARS = "if [ -d /mnt/us/documents ]; then find /mnt/us/documents -type f \\( -iname '*.azw3r' -o -iname '*.mbp1' -o -iname '*.yjr' \\) -print; fi"
        val READER_STATE = """
            active_app=${'$'}(lipc-get-prop com.lab126.appmgrd activeApp 2>/dev/null || true)
            active_view=${'$'}(lipc-get-prop com.lab126.appmgrd activeView 2>/dev/null || true)
            active_context=${'$'}(lipc-get-prop com.lab126.appmgrd activeContext 2>/dev/null || true)
            printf 'activeApp=%s\nactiveView=%s\nactiveContext=%s\n' "${'$'}active_app" "${'$'}active_view" "${'$'}active_context"
        """.trimIndent()

        fun readerStateAvailable(state: String) = state.lineSequence().any { line ->
            line.substringBefore('=').lowercase() in setOf("activeapp", "activeview", "activecontext") &&
                line.substringAfter('=', "").isNotBlank()
        }

        fun readerIsOpen(state: String): Boolean {
            val normalized = state.lowercase()
            return normalized.lineSequence().any { it.startsWith("activeapp=") && it.contains("reader") } ||
                normalized.contains("activeview=kpp_reader") || normalized.contains("activecontext=kpp_reader") ||
                normalized.contains("view=kpp_reader")
        }
    }
}
