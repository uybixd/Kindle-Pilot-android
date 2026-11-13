package com.hongxiang.kpilot.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FavoriteWidgetTest {
    private val notes = listOf(
        note("c", "Third"),
        note("a", "First"),
        note("b", "Second")
    )

    @Test
    fun `existing preferences without a style use paper`() {
        assertEquals(FavoriteWidgetStyle.PAPER, FavoriteWidgetStyle.fromKey(null))
        assertEquals(FavoriteWidgetStyle.PAPER, FavoriteWidgetStyle.fromKey("unknown"))
        assertEquals(FavoriteWidgetStyle.TERMINAL, FavoriteWidgetStyle.fromKey("terminal"))
        assertEquals(FavoriteWidgetStyle.TERMINAL_AMBER, FavoriteWidgetStyle.fromKey("terminal_amber"))
    }

    @Test
    fun `selection stays on the same favorite for a whole day`() {
        val date = LocalDate.of(2026, 11, 10)

        assertEquals(
            FavoriteWidgetSelection.note(notes, date),
            FavoriteWidgetSelection.note(notes, date)
        )
    }

    @Test
    fun `selection rotates at the next calendar day`() {
        val first = FavoriteWidgetSelection.note(notes, LocalDate.of(2026, 11, 10))
        val next = FavoriteWidgetSelection.note(notes, LocalDate.of(2026, 11, 11))

        assertEquals(notes[1], first)
        assertEquals(notes[2], next)
    }

    @Test
    fun `cycle visits every favorite`() {
        val selected = (0 until 6).map { offset ->
            FavoriteWidgetSelection.note(notes, LocalDate.of(2026, 11, 10), offset.toLong())
        }

        assertEquals(setOf(notes[0], notes[1], notes[2]), selected.toSet())
        assertEquals(selected[0], selected[3])
    }

    @Test
    fun `empty and single favorites behave like iOS`() {
        assertNull(FavoriteWidgetSelection.note(emptyList(), LocalDate.of(2026, 11, 10)))

        val single = listOf(note("only", "Only"))
        assertEquals(single[0], FavoriteWidgetSelection.note(single, LocalDate.of(2026, 11, 10), 99))
    }

    @Test
    fun `negative offsets wrap around`() {
        assertEquals(
            FavoriteWidgetSelection.note(notes, LocalDate.of(2026, 11, 10), 2),
            FavoriteWidgetSelection.note(notes, LocalDate.of(2026, 11, 10), -1)
        )
    }

    @Test
    fun `codec keeps metadata and sorts by id`() {
        val original = listOf(
            FavoriteWidgetNote("b", "Second", "Book B", "Author", 1_800_000_000_000),
            FavoriteWidgetNote("a", "First", "Book A")
        )

        val restored = FavoriteWidgetCodec.decode(FavoriteWidgetCodec.encode(original))

        assertEquals(listOf("a", "b"), restored.map { it.id })
        assertEquals("Book B", restored[1].bookTitle)
        assertEquals("Author", restored[1].author)
        assertEquals(1_800_000_000_000, restored[1].addedAtMillis)
        assertNull(restored[0].author)
        assertNull(restored[0].addedAtMillis)
    }

    @Test
    fun `codec reads old data without optional fields`() {
        val restored = FavoriteWidgetCodec.decode(
            """[{"id":"legacy","text":"Old note","bookTitle":"Book"}]"""
        )

        assertEquals(1, restored.size)
        assertEquals("legacy", restored[0].id)
        assertNull(restored[0].author)
        assertNull(restored[0].addedAtMillis)
    }

    @Test
    fun `codec drops blank favorites`() {
        val restored = FavoriteWidgetCodec.decode(
            """[{"id":"blank","text":"  ","bookTitle":"Book"},{"id":"ok","text":"Text","bookTitle":"Book"}]"""
        )

        assertEquals(listOf("ok"), restored.map { it.id })
    }

    private fun note(id: String, text: String) = FavoriteWidgetNote(
        id = id,
        text = text,
        bookTitle = "Book $id",
        author = null,
        addedAtMillis = null
    )
}
