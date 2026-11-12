package com.hongxiang.kpilot.widget

import android.content.Context
import androidx.core.content.edit
import com.hongxiang.kpilot.notes.AtomicCache
import java.io.File
import java.time.LocalDate

/**
 * App-private replica of the favorites shown by the widget. It is written by the
 * app whenever clippings or favorite flags change, so the widget never needs the
 * encrypted Kindle connection settings or an SSH session.
 */
class FavoriteWidgetStore(context: Context) {
    private val context = context.applicationContext
    private val file: File = this.context.noBackupFilesDir.resolve(FILE_NAME)
    private val preferences = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    internal var style: FavoriteWidgetStyle
        get() = FavoriteWidgetStyle.fromKey(preferences.getString(KEY_STYLE, null))
        set(value) { preferences.edit { putString(KEY_STYLE, value.key) } }

    @Synchronized
    fun load(): List<FavoriteWidgetNote> {
        if (!file.isFile) return emptyList()
        return runCatching { FavoriteWidgetCodec.decode(file.readText()) }.getOrDefault(emptyList())
    }

    /** @return true when the stored data changed and the widget should reload. */
    @Synchronized
    fun save(notes: List<FavoriteWidgetNote>): Boolean {
        val encoded = FavoriteWidgetCodec.encode(notes)
        if (file.isFile && runCatching { file.readText() }.getOrNull() == encoded) return false
        AtomicCache.write(file) { it.write(encoded.toByteArray(Charsets.UTF_8)) }
        return true
    }

    @Synchronized
    fun clear() {
        file.delete()
        preferences.edit { clear() }
    }

    @Synchronized
    fun offset(): Long = preferences.getLong(KEY_OFFSET, 0L)

    @Synchronized
    fun noteCount(): Int = load().size

    /** @return true when there is more than one favorite to rotate through. */
    @Synchronized
    fun advance(): Boolean {
        val count = noteCount()
        if (count <= 1) return false
        val next = Math.floorMod(offset() + 1, count.toLong())
        preferences.edit { putLong(KEY_OFFSET, next) }
        return true
    }

    @Synchronized
    fun currentNote(date: LocalDate = LocalDate.now()): FavoriteWidgetNote? =
        FavoriteWidgetSelection.note(load(), date, offset())

    private companion object {
        const val FILE_NAME = "favorite-widget.json"
        const val PREFERENCES_NAME = "favorite_widget"
        const val KEY_OFFSET = "offset"
        const val KEY_STYLE = "style"
    }
}
