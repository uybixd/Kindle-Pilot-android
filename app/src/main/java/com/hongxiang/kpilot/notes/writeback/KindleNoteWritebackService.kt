package com.hongxiang.kpilot.notes.writeback

import android.content.Context
import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.notes.ClippingKind
import com.hongxiang.kpilot.notes.ClippingsValidation
import com.hongxiang.kpilot.notes.KindleClipping
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
        require(clipping.kind == ClippingKind.HIGHLIGHT) { "只能给已有的 Kindle 标注新增或编辑批注" }
        require(clipping.locationStart != null || clipping.locationEnd != null) { "这条标注没有可匹配的位置" }
        val normalizedText = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        require(!Regex("(?m)^={10,}\\s*$").containsMatchIn(normalizedText) && '\u0000' !in normalizedText) {
            "批注不能包含独立的 Kindle 记录分隔行或空字符"
        }
        require(normalizedText.toByteArray(Charsets.UTF_8).size <= 65535) { "批注内容过长（UTF-8 最多 65535 字节）" }
        ensureReaderClosed(connection)
        val originalClippings = connection.download(CLIPPINGS_PATH)
        val (snapshot, issue) = ClippingsValidation.inspect(originalClippings)
        require(issue == null) { "Kindle 的 My Clippings.txt 格式不完整，已停止写入" }
        val fresh = snapshot.items.firstOrNull { it.id == clipping.id }
            ?: error("Kindle 的 My Clippings.txt 中已找不到这条标注")
        val paths = connection.exec(LIST_SIDECARS).lineSequence().map(String::trim)
            .filter { it.startsWith("/mnt/us/documents/") && !it.contains('\u0000') }
            .filter { it.endsWith(".azw3r", true) || it.endsWith(".mbp1", true) || it.endsWith(".yjr", true) }
            .distinct().sortedByDescending { AnnotationMatcher.titleSimilarity(it, fresh) }.toList()
        require(paths.isNotEmpty()) { "Kindle 中没有找到 AZW3、MOBI 或 KFX 批注文件" }
        val candidates = paths.mapNotNull { path ->
            checkInterrupted()
            if (AnnotationMatcher.titleSimilarity(path, fresh) < 0.12) return@mapNotNull null
            runCatching {
                val bytes = connection.download(path)
                val store = KindleReaderDataStore.decode(bytes)
                require(store.encoded().contentEquals(bytes)) { "Kindle sidecar 无法无损重编码" }
                store.annotations()
                SidecarCandidate(path, bytes, store)
            }.getOrElse { error ->
                if (error is InterruptedException) throw error
                null // Unsupported sidecars are never modified.
            }
        }
        val match = AnnotationMatcher.match(candidates, fresh)
        val originalStore = match.candidate.store
        val updatedStore = KindleReaderDataStore.decode(match.candidate.bytes)
        val newNote = updatedStore.updateNote(match.highlight, fresh.kindleNotes.map { it.text }.toSet(),
            normalizedText, now(), match.isKfx)
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
