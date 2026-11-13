package com.hongxiang.kpilot.review

import com.hongxiang.kpilot.notes.ClippingKind
import com.hongxiang.kpilot.notes.KindleClipping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

class ReviewPlannerTest {
    private val utc = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-10T08:00:00Z").toEpochMilli()

    @Test fun onlyHighlightsAndNotesWithTextAreReviewable() {
        assertTrue(ClippingsReviewPlanner.isEligible(clipping("a", "Book", ClippingKind.HIGHLIGHT, "text")))
        assertTrue(ClippingsReviewPlanner.isEligible(clipping("b", "Book", ClippingKind.NOTE, "note")))
        assertFalse(ClippingsReviewPlanner.isEligible(clipping("c", "Book", ClippingKind.BOOKMARK, "text")))
        assertFalse(ClippingsReviewPlanner.isEligible(clipping("d", "Book", ClippingKind.HIGHLIGHT, "  ")))
    }

    @Test fun deckSkipsExcludedAndReviewsLessSeenClippingsFirst() {
        val notes = listOf(
            clipping("zero-a", "A", ClippingKind.HIGHLIGHT, "a"),
            clipping("zero-b", "B", ClippingKind.HIGHLIGHT, "b"),
            clipping("one", "C", ClippingKind.HIGHLIGHT, "c"),
            clipping("excluded", "D", ClippingKind.HIGHLIGHT, "d")
        )
        val deck = ClippingsReviewPlanner.makeDeck(
            notes,
            excludedIds = setOf("excluded"),
            reviewCounts = mapOf("zero-a" to 0, "zero-b" to 0, "one" to 1),
            random = Random(7)
        )
        assertEquals(3, deck.size)
        assertFalse("excluded" in deck)
        assertTrue(deck.indexOf("one") > deck.indexOf("zero-a"))
        assertTrue(deck.indexOf("one") > deck.indexOf("zero-b"))
    }

    @Test fun bookClippingsUseBookIdentityAndSortByLocationThenPage() {
        val first = clipping("first", "Book", ClippingKind.HIGHLIGHT, "First").copy(locationStart = 5)
        val next = clipping("next", "Book", ClippingKind.NOTE, "Next").copy(locationEnd = 25)
        val pageTwo = clipping("page-two", "Book", ClippingKind.HIGHLIGHT, "Page two").copy(page = "2")
        val pageTen = clipping("page-ten", "Book", ClippingKind.HIGHLIGHT, "Page ten").copy(page = "10-12")
        val unknown = clipping("unknown", "Book", ClippingKind.HIGHLIGHT, "Unknown location")
        val bookmark = clipping("bookmark", "Book", ClippingKind.BOOKMARK, "Bookmark")
        val otherBook = first.copy(id = "other", bookId = "different-book")

        val ordered = ClippingsReviewPlanner.bookClippings(
            listOf(unknown, pageTen, next, otherBook, bookmark, pageTwo, first), first.bookId
        )

        assertEquals(listOf(first, next, pageTwo, pageTen, unknown), ordered)
    }

    @Test fun deckInterleavesBooksWhenPossible() {
        val notes = listOf(
            clipping("a1", "A", ClippingKind.HIGHLIGHT, "1"),
            clipping("a2", "A", ClippingKind.HIGHLIGHT, "2"),
            clipping("b1", "B", ClippingKind.HIGHLIGHT, "3"),
            clipping("b2", "B", ClippingKind.HIGHLIGHT, "4")
        )
        repeat(20) {
            val deck = ClippingsReviewPlanner.makeDeck(notes, random = Random(it))
            deck.zipWithNext().forEach { (left, right) ->
                val leftBook = notes.first { note -> note.id == left }.bookId
                val rightBook = notes.first { note -> note.id == right }.bookId
                assertFalse("adjacent $left/$right in seed $it", leftBook == rightBook)
            }
        }
    }

    @Test fun newWordGraduatesThroughLearningSteps() {
        val first = requireNotNull(
            VocabularyReviewPlanner.updatedRecord("word", null, VocabularyReviewResult.GOOD, now, utc)
        )
        assertEquals(VocabularyReviewState.LEARNING, first.state)
        assertEquals(1, first.learningStep)
        assertEquals(now + 600_000, first.dueAt)
        assertEquals(1, first.reviewCount)

        val second = requireNotNull(
            VocabularyReviewPlanner.updatedRecord("word", first, VocabularyReviewResult.GOOD, now + 600_000, utc)
        )
        assertEquals(VocabularyReviewState.REVIEW, second.state)
        assertEquals(1, second.intervalDays)
        val expectedDue = LocalDate.of(2026, 11, 11).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expectedDue, second.dueAt)
    }

    @Test fun reviewAgainReentersLearningAndReducesEase() {
        val review = VocabularyReviewRecord(
            "word", reviewCount = 4, state = VocabularyReviewState.REVIEW,
            intervalDays = 10, easeFactor = 2.5, dueAt = now - 86_400_000
        )
        val result = requireNotNull(
            VocabularyReviewPlanner.updatedRecord("word", review, VocabularyReviewResult.AGAIN, now, utc)
        )
        assertEquals(VocabularyReviewState.RELEARNING, result.state)
        assertEquals(2, result.intervalDays)
        assertEquals(2.3, result.easeFactor, 0.0001)
        assertEquals(now + 60_000, result.dueAt)
        assertEquals(1, result.forgottenCount)
        assertEquals(5, result.reviewCount)
    }

    @Test fun reviewEasyGraduatesFurtherAndRaisesEase() {
        val review = VocabularyReviewRecord(
            "word", state = VocabularyReviewState.REVIEW, intervalDays = 1,
            easeFactor = 2.5, dueAt = now
        )
        val result = requireNotNull(
            VocabularyReviewPlanner.updatedRecord("word", review, VocabularyReviewResult.EASY, now, utc)
        )
        assertEquals(VocabularyReviewState.REVIEW, result.state)
        assertEquals(4, result.intervalDays)
        assertEquals(2.65, result.easeFactor, 0.0001)
    }

    @Test fun queueDrawsReadyLearningThenDueReviewsThenNewAndExcludesSkipped() {
        val keys = listOf("new", "learning-due", "learning-wait", "review-due", "review-wait", "skipped", "new")
        val records = mapOf(
            "learning-due" to VocabularyReviewRecord(
                "learning-due", state = VocabularyReviewState.LEARNING, dueAt = now - 1_000
            ),
            "learning-wait" to VocabularyReviewRecord(
                "learning-wait", state = VocabularyReviewState.LEARNING, dueAt = now + 60_000
            ),
            "review-due" to VocabularyReviewRecord(
                "review-due", state = VocabularyReviewState.REVIEW, intervalDays = 2,
                dueAt = now - 86_400_000
            ),
            "review-wait" to VocabularyReviewRecord(
                "review-wait", state = VocabularyReviewState.REVIEW, intervalDays = 2,
                dueAt = now + 86_400_000
            )
        )
        val queue = VocabularyReviewPlanner.makeQueue(
            keys, records, excluding = setOf("skipped"), now = now, zone = utc
        )
        assertEquals("learning-due", queue.draw())
        assertEquals(1, queue.newCount)
        assertEquals(2, queue.learningCount)
        assertEquals(1, queue.reviewCount)
        assertEquals(now + 60_000, queue.nextLearningDueAt)

        val excluded = setOf("skipped", "learning-due")
        assertEquals("review-due", VocabularyReviewPlanner.makeQueue(keys, records, excluded, now, utc).draw())
        assertEquals("new", VocabularyReviewPlanner.makeQueue(keys, records, excluded + "review-due", now, utc).draw())
        assertNull(VocabularyReviewPlanner.makeQueue(keys, records, excluded + setOf("review-due", "new"), now, utc).draw())
    }

    @Test fun drawsRandomlyAmongDueReviewsAndAmongNewWords() {
        val dueKeys = setOf("due-today", "overdue", "due-tonight")
        val newKeys = setOf("apple", "banana", "cherry")
        val keys = (dueKeys + newKeys + "tomorrow").sorted()
        val records = mapOf(
            "due-today" to now,
            "overdue" to now - 86_400_000,
            "due-tonight" to now + 12 * 3_600_000,
            "tomorrow" to now + 24 * 3_600_000
        ).mapValues { (key, due) ->
            VocabularyReviewRecord(key, state = VocabularyReviewState.REVIEW, dueAt = due)
        }
        val dueQueue = VocabularyReviewPlanner.makeQueue(keys, records, now = now, zone = utc)
        val newQueue = VocabularyReviewPlanner.makeQueue(keys, records, dueKeys, now, utc)
        val random = Random(7)

        assertEquals(dueKeys, List(40) { dueQueue.draw(random) }.toSet())
        assertEquals(newKeys, List(40) { newQueue.draw(random) }.toSet())
    }

    @Test fun reviewIsDueAcrossTheWholeLocalDay() {
        val due = LocalDate.of(2026, 11, 10).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val record = VocabularyReviewRecord(
            "word", state = VocabularyReviewState.REVIEW, dueAt = due
        )
        assertTrue(VocabularyReviewPlanner.isDue(record, now, utc))
        assertFalse(
            VocabularyReviewPlanner.isDue(record, due - 1, utc)
        )
        assertNull(VocabularyReviewPlanner.dueDate(VocabularyReviewRecord("new")))
    }

    @Test fun aWaitingLearningCardIsNotShownAgainImmediately() {
        val learning = requireNotNull(
            VocabularyReviewPlanner.updatedRecord(
                "abolish", null, VocabularyReviewResult.GOOD, now, utc
            )
        )
        val queue = VocabularyReviewPlanner.makeQueue(
            listOf("abolish", "abortion"),
            mapOf("abolish" to learning),
            now = now,
            zone = utc
        )
        assertEquals("abortion", queue.draw())
        assertEquals(1, queue.learningCount)
        assertEquals(now + 600_000, queue.nextLearningDueAt)
        val later = VocabularyReviewPlanner.makeQueue(
            listOf("abolish", "abortion"), mapOf("abolish" to learning), now = now + 600_000, zone = utc
        )
        assertEquals("abolish", later.draw())
    }

    private fun clipping(id: String, book: String, kind: ClippingKind, text: String) = KindleClipping(
        id = id,
        bookId = book,
        bookTitle = book,
        author = null,
        kind = kind,
        location = null,
        locationStart = null,
        locationEnd = null,
        page = null,
        addedAt = null,
        metadata = "",
        text = text,
        raw = text
    )
}
