package com.hongxiang.kpilot.widget

import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.review.ClippingReviewRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** One favorite clipping prepared for the home screen widget. */
data class FavoriteWidgetNote(
    val id: String,
    val text: String,
    val bookTitle: String,
    val author: String? = null,
    val addedAtMillis: Long? = null
)

internal fun widgetFavorites(
    notes: List<KindleClipping>,
    reviews: Map<String, ClippingReviewRecord>
): List<FavoriteWidgetNote> {
    val favoriteIds = reviews.values.asSequence()
        .filter { it.isFavorite }
        .mapTo(mutableSetOf()) { it.clippingId }
    return notes.asSequence()
        .filter { it.id in favoriteIds && it.text.isNotBlank() }
        .map { FavoriteWidgetNote(it.id, it.text, it.bookTitle, it.author, it.addedAt?.toEpochMilli()) }
        .toList()
}

internal object FavoriteWidgetCodec {
    fun encode(notes: List<FavoriteWidgetNote>): String {
        val array = JSONArray()
        notes.sortedBy { it.id }.forEach { note ->
            array.put(JSONObject().apply {
                put("id", note.id)
                put("text", note.text)
                put("bookTitle", note.bookTitle)
                note.author?.takeIf { it.isNotBlank() }?.let { put("author", it) }
                note.addedAtMillis?.let { put("addedAtMillis", it) }
            })
        }
        return array.toString()
    }

    fun decode(text: String): List<FavoriteWidgetNote> {
        val array = JSONArray(text)
        val notes = mutableListOf<FavoriteWidgetNote>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val body = item.optString("text")
            if (id.isEmpty() || body.isBlank()) continue
            notes += FavoriteWidgetNote(
                id = id,
                text = body,
                bookTitle = item.optString("bookTitle"),
                author = item.optString("author").trim().takeIf { it.isNotEmpty() },
                addedAtMillis = if (item.has("addedAtMillis") && !item.isNull("addedAtMillis")) {
                    item.optLong("addedAtMillis").takeIf { it > 0 }
                } else null
            )
        }
        return notes.sortedBy { it.id }
    }
}

/**
 * Mirrors the iOS timeline: the same favorite stays visible for a whole local
 * calendar day, then the next one is selected. The widget's arrow adds a manual
 * offset without changing the day.
 */
internal object FavoriteWidgetSelection {
    fun note(
        notes: List<FavoriteWidgetNote>,
        date: LocalDate,
        offset: Long = 0
    ): FavoriteWidgetNote? {
        if (notes.isEmpty()) return null
        val index = Math.floorMod(date.toEpochDay() + offset, notes.size.toLong()).toInt()
        return notes[index]
    }
}
