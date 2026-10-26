package com.hongxiang.kpilot.notes

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

data class NoteDraft(
    val clippingId: String,
    val text: String,
    val revision: String = UUID.randomUUID().toString(),
    val syncedRevision: String? = null
) {
    val needsSync: Boolean get() = revision != syncedRevision

    fun acknowledge(sentRevision: String): NoteDraft =
        if (revision == sentRevision) copy(syncedRevision = sentRevision) else this

    fun revise(text: String): NoteDraft =
        if (this.text == text) this else copy(text = text, revision = UUID.randomUUID().toString())
}

internal fun stageNoteDraft(
    clippingId: String,
    previous: NoteDraft?,
    text: String,
    hasKindleNote: Boolean,
    writeInFlight: Boolean
): NoteDraft? {
    val trimmed = text.trim()
    // A pending write may already have installed the old text. Preserve an empty
    // revision until its deletion can be delivered, even before that write is acknowledged.
    // A saved draft may have been delivered before a lost connection or local-cache
    // failure. Clearing an existing copy must therefore always leave a tombstone.
    if (trimmed.isEmpty() && !hasKindleNote && previous == null && !writeInFlight) return null
    return previous?.revise(trimmed) ?: NoteDraft(clippingId, trimmed)
}

/** Cleared text is retained as a deletion marker; a successful send retains the local copy. */
class NoteDraftStore(private val directory: File) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "note-drafts"))

    fun load(scope: String): Map<String, NoteDraft> {
        val source = file(scope)
        if (!source.exists()) return emptyMap()
        return DataInputStream(source.inputStream().buffered()).use { input ->
            check(input.readInt() == MAGIC && input.readInt() == VERSION) { "无法读取本地批注草稿" }
            val count = input.readInt()
            check(count in 0..100_000) { "本地批注草稿数量无效" }
            val result = linkedMapOf<String, NoteDraft>()
            repeat(count) {
                val id = input.readUTF()
                val revision = input.readUTF()
                val synced = input.readUTF().ifEmpty { null }
                val size = input.readInt()
                check(size in 0..MAX_TEXT_BYTES) { "本地批注草稿过大" }
                val bytes = ByteArray(size).also(input::readFully)
                check(id !in result) { "本地批注草稿重复" }
                result[id] = NoteDraft(id, bytes.toString(Charsets.UTF_8), revision, synced)
            }
            check(input.read() == -1) { "本地批注草稿格式无效" }
            result
        }
    }

    fun save(scope: String, drafts: Map<String, NoteDraft>) {
        require(drafts.size <= 100_000)
        // Validate before touching the existing file.
        drafts.values.forEach { require(it.text.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) { "批注内容过长" } }
        AtomicCache.write(file(scope)) { stream ->
            val output = DataOutputStream(stream)
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeInt(drafts.size)
            drafts.toSortedMap().values.forEach { draft ->
                output.writeUTF(draft.clippingId)
                output.writeUTF(draft.revision)
                output.writeUTF(draft.syncedRevision.orEmpty())
                val bytes = draft.text.toByteArray(Charsets.UTF_8)
                output.writeInt(bytes.size)
                output.write(bytes)
            }
            output.flush()
        }
    }

    private fun file(scope: String): File {
        require(scope.matches(Regex("[a-f0-9]{64}")))
        return File(directory, "$scope.dat")
    }

    private companion object {
        const val MAGIC = 0x4B504E44
        const val VERSION = 1
        const val MAX_TEXT_BYTES = 1_000_000
    }
}
