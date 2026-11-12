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
    val syncedRevision: String? = null,
    // null is an unknown baseline (including unsent version 1 drafts); empty means no note.
    val kindleBaseText: String? = null
) {
    val needsSync: Boolean get() = revision != syncedRevision

    fun acknowledge(sentRevision: String): NoteDraft =
        if (revision == sentRevision) copy(syncedRevision = sentRevision) else this

    fun revise(text: String): NoteDraft =
        if (this.text == text) this else copy(text = text, revision = UUID.randomUUID().toString())

    fun acceptKindle(text: String): NoteDraft =
        copy(text = text, syncedRevision = revision, kindleBaseText = text)
}

internal fun rememberKindleNoteBaseline(
    clippingId: String,
    previous: NoteDraft?,
    kindleText: String,
    hasExportedNote: Boolean
): NoteDraft? {
    if (previous?.needsSync == true) return previous
    // A verified note is also the base of the next edit, even when the
    // newest export already displays the same text.
    if (previous == null && kindleText.isEmpty() && !hasExportedNote) return null
    return (previous ?: NoteDraft(clippingId, kindleText)).acceptKindle(kindleText)
}

internal fun stageNoteDraft(
    clippingId: String,
    previous: NoteDraft?,
    text: String,
    hasKindleNote: Boolean,
    writeInFlight: Boolean,
    kindleBaseText: String? = null
): NoteDraft? {
    val trimmed = text.trim()
    // A pending write may already have installed the old text. Preserve an empty
    // revision until its deletion can be delivered, even before that write is acknowledged.
    // A saved draft may have been delivered before a lost connection or local-cache
    // failure. Clearing an existing copy must therefore always leave a tombstone.
    if (trimmed.isEmpty() && !hasKindleNote && previous == null && !writeInFlight) return null
    return previous?.revise(trimmed)?.let {
        if (previous.needsSync) it else it.copy(kindleBaseText = previous.text)
    } ?: NoteDraft(clippingId, trimmed, kindleBaseText = kindleBaseText)
}

/** Cleared text is retained as a deletion marker; a successful send retains the local copy. */
class NoteDraftStore(private val directory: File) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "note-drafts"))

    fun load(scope: String): Map<String, NoteDraft> {
        val source = file(scope)
        if (!source.exists()) return emptyMap()
        return DataInputStream(source.inputStream().buffered()).use { input ->
            check(input.readInt() == MAGIC) { "无法读取本地批注草稿" }
            val version = input.readInt()
            check(version in 1..VERSION) { "无法读取本地批注草稿" }
            val count = input.readInt()
            check(count in 0..100_000) { "本地批注草稿数量无效" }
            val result = linkedMapOf<String, NoteDraft>()
            repeat(count) {
                val id = input.readUTF()
                val revision = input.readUTF()
                val synced = input.readUTF().ifEmpty { null }
                val text = readText(input)
                val baseText = if (version >= 2 && input.readBoolean()) readText(input) else null
                check(id !in result) { "本地批注草稿重复" }
                result[id] = NoteDraft(id, text, revision, synced, baseText)
            }
            check(input.read() == -1) { "本地批注草稿格式无效" }
            result
        }
    }

    fun save(scope: String, drafts: Map<String, NoteDraft>) {
        require(drafts.size <= 100_000)
        // Validate before touching the existing file.
        drafts.values.forEach { draft ->
            require(listOfNotNull(draft.text, draft.kindleBaseText).all {
                it.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES
            }) { "批注内容过长" }
        }
        AtomicCache.write(file(scope)) { stream ->
            val output = DataOutputStream(stream)
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeInt(drafts.size)
            drafts.toSortedMap().values.forEach { draft ->
                output.writeUTF(draft.clippingId)
                output.writeUTF(draft.revision)
                output.writeUTF(draft.syncedRevision.orEmpty())
                writeText(output, draft.text)
                output.writeBoolean(draft.kindleBaseText != null)
                draft.kindleBaseText?.let { writeText(output, it) }
            }
            output.flush()
        }
    }

    private fun readText(input: DataInputStream): String {
        val size = input.readInt()
        check(size in 0..MAX_TEXT_BYTES) { "本地批注草稿过大" }
        return ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
    }

    private fun writeText(output: DataOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun file(scope: String): File {
        require(scope.matches(Regex("[a-f0-9]{64}")))
        return File(directory, "$scope.dat")
    }

    private companion object {
        const val MAGIC = 0x4B504E44
        const val VERSION = 2
        const val MAX_TEXT_BYTES = 1_000_000
    }
}
