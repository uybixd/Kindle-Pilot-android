package com.hongxiang.kpilot.review

import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.data.cacheScope
import com.hongxiang.kpilot.notes.ClippingKind
import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.notes.KindleNote
import com.hongxiang.kpilot.notes.NoteDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

class ReviewTest {
    private val now = Instant.parse("2026-10-26T15:59:59.123456789Z")
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun deckContainsEachEligibleNoteOnceAndSkipsExcludedRecords() {
        val values = listOf(
            clipping("a"), clipping("b", kind = ClippingKind.NOTE), clipping("a"),
            clipping("bookmark", kind = ClippingKind.BOOKMARK), clipping("empty", text = " \n "),
            clipping("unknown", kind = ClippingKind.UNKNOWN), clipping("excluded")
        )
        val deck = ReviewPlanner.makeDeck(values, mapOf("excluded" to ReviewRecord(excluded = true)), Random(5))
        assertEquals(setOf("a", "b"), deck.toSet())
        assertEquals(2, deck.size)
    }

    @Test
    fun deckInterleavesBooksWithinLowCountTiers() {
        val values = (1..3).flatMap { n -> listOf(clipping("a$n", "a"), clipping("b$n", "b"), clipping("c$n", "c")) }
        val books = values.associate { it.id to it.bookId }
        repeat(20) { seed ->
            val deck = ReviewPlanner.makeDeck(values, random = Random(seed))
            deck.zipWithNext().forEach { (a, b) -> assertNotEquals(books[a], books[b]) }
        }
        val records = mapOf("a1" to ReviewRecord(reviewCount = 3), "b1" to ReviewRecord(reviewCount = 1), "c1" to ReviewRecord(reviewCount = -1))
        val deck = ReviewPlanner.makeDeck(values, records, Random(5))
        assertEquals("a1", deck.last())
        assertEquals("b1", deck[deck.lastIndex - 1])
        assertTrue(deck.take(7).all { (records[it]?.reviewCount ?: 0) <= 0 })
    }

    @Test
    fun eachBatchContainsNineAndNextBatchContinuesWithoutRepeating() {
        val values = (1..12).map { clipping("$it", "book-${it % 3}") }
        val session = session(values)
        assertEquals(9, session.snapshot.batchTotal)
        val first = session.snapshot.batch.toSet()
        repeat(9) { session.markDisplayed(now); session.complete(ReviewAction.NEXT) }
        assertEquals(null, session.current)
        assertEquals(9, session.todayCount(now))
        session.nextBatch()
        assertEquals(3, session.snapshot.batchTotal)
        assertTrue(session.snapshot.batch.none { it in first })
    }

    @Test
    fun displayedCardIsCountedOnceAcrossUpdatesUndoAndAppRestart() {
        val values = listOf(clipping("a"), clipping("b"))
        val session = session(values)
        val id = session.current!!.id
        session.markDisplayed(now)
        session.markDisplayed(now.plusSeconds(1))
        session.updateNotes(values)
        session.markDisplayed(now)
        assertEquals(1, session.snapshot.records[id]!!.reviewCount)
        assertEquals(1, session.todayCount(now))
        val restarted = ReviewSession(session.snapshot, values, Random(1), zone)
        restarted.markDisplayed(now.plusSeconds(2))
        assertEquals(1, restarted.todayCount(now))
        restarted.complete(ReviewAction.EXCLUDE)
        assertTrue(restarted.snapshot.records[id]!!.excluded)
        restarted.undoLastAction()
        restarted.markDisplayed(now)
        assertEquals(id, restarted.current!!.id)
        assertFalse(restarted.snapshot.records[id]!!.excluded)
        assertEquals(1, restarted.todayCount(now))
    }

    @Test
    fun favoriteAndSkipCanBeUndoneAndFavoritesSurviveRestoreExcluded() {
        val values = listOf(clipping("a"), clipping("b"))
        val session = session(values)
        val favorite = session.current!!.id
        session.markDisplayed(now)
        session.complete(ReviewAction.FAVORITE)
        assertTrue(session.snapshot.records[favorite]!!.favorite)
        session.undoLastAction()
        assertEquals(favorite, session.current!!.id)
        assertFalse(session.snapshot.records[favorite]!!.favorite)
        session.complete(ReviewAction.FAVORITE)
        val excluded = session.current!!.id
        session.complete(ReviewAction.EXCLUDE)
        assertEquals(1, session.eligibleCount)
        assertEquals(1, session.excludedCount)
        session.restoreExcluded()
        assertEquals(2, session.eligibleCount)
        assertEquals(0, session.excludedCount)
        assertTrue(session.snapshot.records[favorite]!!.favorite)
        assertFalse(session.snapshot.records[excluded]!!.excluded)
        session.complete(ReviewAction.NEXT)
        session.undoLastAction()
        assertEquals(2, session.snapshot.batch.size)
    }

    @Test
    fun sourceUpdateDropsMissingCardsAndAddsNewOnesWithoutReaddingConsumedCards() {
        val values = listOf(clipping("a"), clipping("b"), clipping("c"))
        val session = session(values)
        val consumed = session.current!!.id
        session.complete(ReviewAction.NEXT)
        val removed = session.current!!.id
        val updated = values.filterNot { it.id == removed } + clipping("new")
        session.updateNotes(updated)
        assertFalse(consumed in session.snapshot.deck)
        assertFalse(removed in session.snapshot.deck)
        assertFalse(removed in session.snapshot.batch)
        assertTrue("new" in session.snapshot.deck)
        session.updateNotes(updated)
        assertEquals(1, session.snapshot.deck.count { it == "new" })
    }

    @Test
    fun loadingBeforeClippingsArrivePreservesTheSavedBatch() {
        val values = (1..11).map { clipping("$it") }
        val original = session(values)
        original.markDisplayed(now)
        val restarted = ReviewSession(original.snapshot, emptyList(), Random(4), zone)
        assertEquals(original.snapshot, restarted.snapshot)
        assertEquals(null, restarted.current)
        restarted.updateNotes(values)
        assertEquals(original.current!!.id, restarted.current!!.id)
        restarted.markDisplayed(now)
        assertEquals(1, restarted.todayCount(now))
    }

    @Test
    fun newlySyncedUnseenCardsArePrioritizedInTheRemainingDeck() {
        val values = listOf(clipping("old-a"), clipping("old-b"))
        val records = values.associate { it.id to ReviewRecord(reviewCount = 3) }
        val session = ReviewSession(ReviewSnapshot(records = records), values, Random(4), zone)
        val active = session.current!!.id
        session.updateNotes(values + clipping("new"))
        assertEquals("new", session.snapshot.deck.first())
        assertEquals(active, session.current!!.id)
    }

    @Test
    fun dailyCountUsesDeviceDateAndPrunesOldHistory() {
        val values = listOf(clipping("a"), clipping("b"))
        val session = ReviewSession(ReviewSnapshot(dailyCounts = mapOf("2025-01-01" to 7)), values, Random(4), zone)
        session.markDisplayed(now)
        session.complete(ReviewAction.NEXT)
        session.markDisplayed(now.plusSeconds(2))
        assertEquals(1, session.snapshot.dailyCounts["2026-10-26"])
        assertEquals(1, session.snapshot.dailyCounts["2026-10-27"])
        assertFalse(session.snapshot.dailyCounts.containsKey("2025-01-01"))
    }

    @Test
    fun historyRoundTripsAllProgressAndRemainsIsolatedPerKindle() {
        val directory = Files.createTempDirectory("review-history").toFile()
        try {
            val settings = ConnectionSettings(host = "Kindle", username = "root")
            val store = ReviewHistoryStore(File(directory, "${settings.cacheScope()}.review"))
            val values = (1..12).map { clipping("$it") }
            val session = session(values)
            session.markDisplayed(now)
            session.complete(ReviewAction.FAVORITE)
            session.markDisplayed(now.plusSeconds(1))
            session.complete(ReviewAction.EXCLUDE)
            store.save(session.snapshot)
            assertEquals(session.snapshot, store.load())
            val other = ReviewHistoryStore(File(directory, "${settings.copy(host = "another-kindle").cacheScope()}.review"))
            assertEquals(ReviewSnapshot(), other.load())
            assertEquals(settings.cacheScope(), settings.copy(host = " KINDLE ", password = "new-password").cacheScope())
            assertNotEquals(settings.cacheScope(), settings.copy(port = 2222).cacheScope())
            assertNotEquals(settings.cacheScope(), settings.copy(username = "other").cacheScope())
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun corruptHistoryFailsWithoutReplacingTheStoredFile() {
        val directory = Files.createTempDirectory("review-corrupt").toFile()
        try {
            val file = File(directory, "history.review").apply { writeText("not a valid history") }
            assertTrue(runCatching { ReviewHistoryStore(file).load() }.isFailure)
            assertEquals("not a valid history", file.readText())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun localDraftOverridesSyncedAnnotationAndEmptyDraftSuppressesIt() {
        val source = clipping("a").copy(kindleNotes = listOf(KindleNote("note", "10", 10, 10, null, now, "metadata", "Synced thought", "raw")))
        assertEquals("Synced thought", reviewAnnotation(source, emptyMap()))
        assertEquals("Local thought", reviewAnnotation(source, mapOf("a" to NoteDraft("a", "Local thought"))))
        assertEquals(null, reviewAnnotation(source, mapOf("a" to NoteDraft("a", ""))))
        assertEquals(null, reviewAnnotation(source, mapOf("a" to NoteDraft("a", "  \n "))))
    }

    @Test
    fun failedSaveRetryKeepsActionsAndDoesNotCountTheCardAgain() = runBlocking {
        val directory = Files.createTempDirectory("review-retry").toFile()
        try {
            val blocked = File(directory, "blocked").apply { writeText("blocks directory creation") }
            val settings = ConnectionSettings(host = "kindle")
            val notes = listOf(clipping("a"), clipping("b"))
            val controller = ReviewController(blocked, this)
            controller.updateSource(settings, notes)
            assertEquals(ReviewFailure.SAVE, controller.state.value.failure)
            val favorite = controller.state.value.current!!.id
            controller.complete(ReviewAction.FAVORITE).join()
            assertEquals(2, controller.state.value.todayCount)
            blocked.delete()
            blocked.mkdirs()
            controller.retry(settings, notes).join()
            assertEquals(null, controller.state.value.failure)
            assertEquals(2, controller.state.value.todayCount)
            val snapshot = ReviewHistoryStore(File(blocked, "${settings.cacheScope()}.review")).load()
            assertTrue(snapshot.records[favorite]!!.favorite)
            assertEquals(1, snapshot.records[favorite]!!.reviewCount)
            val restarted = ReviewController(blocked, this)
            restarted.updateSource(settings, notes)
            assertEquals(2, restarted.state.value.todayCount)
            assertEquals(controller.state.value.current!!.id, restarted.state.value.current!!.id)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun failedLoadRetryReloadsHistoryAfterTheFileIsRecovered() = runBlocking {
        val directory = Files.createTempDirectory("review-reload").toFile()
        try {
            val settings = ConnectionSettings(host = "kindle")
            val file = File(directory, "${settings.cacheScope()}.review").apply { writeText("corrupt history") }
            val controller = ReviewController(directory, this)
            val notes = listOf(clipping("a"))
            controller.updateSource(settings, notes)
            assertEquals(ReviewFailure.LOAD, controller.state.value.failure)
            assertEquals("corrupt history", file.readText())
            ReviewHistoryStore(file).save(ReviewSnapshot())
            controller.retry(settings, notes).join()
            assertEquals(null, controller.state.value.failure)
            assertEquals("a", controller.state.value.current!!.id)
            assertEquals(1, controller.state.value.todayCount)
        } finally { directory.deleteRecursively() }
    }

    private fun session(notes: List<KindleClipping>) = ReviewSession(notes = notes, random = Random(4), zone = zone)

    private fun clipping(id: String, book: String = "book", kind: ClippingKind = ClippingKind.HIGHLIGHT, text: String = "A passage worth revisiting") =
        KindleClipping(id, book, "Book $book", "Author", kind, "10-12", 10, 12, "2", now, "metadata", text, text)
}
