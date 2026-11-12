package com.hongxiang.kpilot.notes.writeback

data class NoteConflict(
    val clippingId: String,
    val revision: String,
    val phoneText: String,
    val kindleText: String,
    val baselineKnown: Boolean,
    val kindleVersion: String
)

internal data class NoteConflictChoice(val conflict: NoteConflict, val keepPhone: Boolean)

internal sealed interface NoteDraftSyncResult {
    data class Accepted(val text: String, val written: WritebackResult? = null) : NoteDraftSyncResult
    data class Conflict(val conflict: NoteConflict) : NoteDraftSyncResult
}

internal enum class NoteSyncAction { KEEP_KINDLE, WRITE_PHONE, ASK }

internal fun noteSyncAction(base: String?, phone: String, kindle: String): NoteSyncAction = when {
    phone == kindle || phone == base -> NoteSyncAction.KEEP_KINDLE
    kindle == base -> NoteSyncAction.WRITE_PHONE
    else -> NoteSyncAction.ASK
}

internal fun normalizedNoteText(text: String) = text.replace("\r\n", "\n").replace('\r', '\n').trim()
