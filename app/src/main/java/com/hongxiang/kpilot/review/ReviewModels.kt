package com.hongxiang.kpilot.review

import com.hongxiang.kpilot.notes.ClippingKind
import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.notes.NoteDraft
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

data class ReviewRecord(
    val reviewCount: Int = 0,
    val lastReviewedAt: Instant? = null,
    val favorite: Boolean = false,
    val excluded: Boolean = false
)

/** The current batch and displayed IDs are saved too, so reopening never double-counts a card. */
data class ReviewSnapshot(
    val records: Map<String, ReviewRecord> = emptyMap(),
    val deck: List<String> = emptyList(),
    val cycleIds: Set<String> = emptySet(),
    val batch: List<String> = emptyList(),
    val batchTotal: Int = 0,
    val displayedIds: Set<String> = emptySet(),
    val dailyCounts: Map<String, Int> = emptyMap()
)

enum class ReviewAction { NEXT, FAVORITE, EXCLUDE }

/** An empty draft is an intentional clear and must suppress the last synced annotation. */
fun reviewAnnotation(clipping: KindleClipping, drafts: Map<String, NoteDraft>): String? =
    if (clipping.id in drafts) drafts[clipping.id]?.text?.takeIf { it.isNotBlank() }
    else clipping.kindleNotes.lastOrNull { it.text.isNotBlank() }?.text

data class ReviewUndo(
    val clippingId: String,
    val previousRecord: ReviewRecord?,
    val deckIndex: Int,
    val batchIndex: Int,
    val action: ReviewAction
)

object ReviewPlanner {
    const val BATCH_SIZE = 9

    fun isEligible(clipping: KindleClipping): Boolean =
        clipping.kind in setOf(ClippingKind.HIGHLIGHT, ClippingKind.NOTE) && clipping.text.isNotBlank()

    /** Low-count tiers come first; within each tier take one card per book per round. */
    fun makeDeck(
        notes: List<KindleClipping>,
        records: Map<String, ReviewRecord> = emptyMap(),
        random: Random = Random.Default
    ): List<String> {
        val eligible = notes.distinctBy { it.id }.filter { isEligible(it) && records[it.id]?.excluded != true }
        val result = mutableListOf<String>()
        var previousBook: String? = null
        eligible.groupBy { records[it.id]?.reviewCount?.coerceAtLeast(0) ?: 0 }.toSortedMap().values.forEach { tier ->
            val groups = tier.groupBy { it.bookId }.mapValues { it.value.shuffled(random).toMutableList() }.toMutableMap()
            while (groups.isNotEmpty()) {
                val books = groups.keys.shuffled(random).toMutableList()
                if (books.size > 1 && books.first() == previousBook) {
                    val other = books.indexOfFirst { it != previousBook }
                    val first = books[0]
                    books[0] = books[other]
                    books[other] = first
                }
                books.forEach { book ->
                    val values = groups.getValue(book)
                    result += values.removeAt(values.lastIndex).id
                    previousBook = book
                    if (values.isEmpty()) groups.remove(book)
                }
            }
        }
        return result
    }
}

/** Pure state transitions, independent of Android and of persistence. */
class ReviewSession(
    initial: ReviewSnapshot = ReviewSnapshot(),
    notes: List<KindleClipping>,
    private val random: Random = Random.Default,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    var snapshot: ReviewSnapshot = initial
        private set
    var undo: ReviewUndo? = null
        private set
    private var notesById: Map<String, KindleClipping> = emptyMap()

    init { updateNotes(notes) }

    val current: KindleClipping? get() = snapshot.batch.firstOrNull()?.let(notesById::get)
    val eligibleCount: Int get() = notesById.values.count { ReviewPlanner.isEligible(it) && snapshot.records[it.id]?.excluded != true }
    val excludedCount: Int get() = notesById.keys.count { snapshot.records[it]?.excluded == true }
    val favoriteCount: Int get() = notesById.keys.count { snapshot.records[it]?.favorite == true }

    fun todayCount(now: Instant): Int = snapshot.dailyCounts[dayKey(now)] ?: 0

    fun updateNotes(notes: List<KindleClipping>) {
        notesById = notes.associateBy { it.id }
        // Cached clippings load asynchronously. Keep the saved deck until a source is available.
        if (notes.isEmpty()) return
        val eligible = notesById.values.filter { ReviewPlanner.isEligible(it) && snapshot.records[it.id]?.excluded != true }
        val validIds = eligible.map { it.id }.toSet()
        val newNotes = eligible.filter { it.id !in snapshot.cycleIds }
        val remaining = snapshot.deck.filter { it in validIds }.distinct()
        val additions = ReviewPlanner.makeDeck(newNotes, snapshot.records, random)
        snapshot = snapshot.copy(
            deck = (remaining + additions).sortedBy { snapshot.records[it]?.reviewCount?.coerceAtLeast(0) ?: 0 },
            cycleIds = snapshot.cycleIds + validIds,
            batch = snapshot.batch.filter { it in validIds }.distinct(),
            displayedIds = snapshot.displayedIds.intersect(validIds)
        )
        if (snapshot.batchTotal == 0 && snapshot.deck.isNotEmpty()) nextBatch()
        if (undo?.clippingId !in validIds) undo = null
    }

    fun markDisplayed(now: Instant) {
        val id = current?.id ?: return
        if (id in snapshot.displayedIds) return
        val record = snapshot.records[id] ?: ReviewRecord()
        val day = dayKey(now)
        val cutoff = LocalDate.ofInstant(now, zone).minusDays(90).toString()
        snapshot = snapshot.copy(
            records = snapshot.records + (id to record.copy(reviewCount = record.reviewCount + 1, lastReviewedAt = now)),
            displayedIds = snapshot.displayedIds + id,
            dailyCounts = (snapshot.dailyCounts + (day to ((snapshot.dailyCounts[day] ?: 0) + 1))).filterKeys { it >= cutoff }
        )
    }

    fun complete(action: ReviewAction) {
        val id = current?.id ?: return
        val previous = snapshot.records[id]
        val record = previous ?: ReviewRecord()
        undo = ReviewUndo(id, previous, snapshot.deck.indexOf(id).coerceAtLeast(0), 0, action)
        val nextRecord = when (action) {
            ReviewAction.NEXT -> record
            ReviewAction.FAVORITE -> record.copy(favorite = true)
            ReviewAction.EXCLUDE -> record.copy(excluded = true)
        }
        snapshot = snapshot.copy(
            records = snapshot.records + (id to nextRecord),
            deck = snapshot.deck.filterNot { it == id },
            batch = snapshot.batch.filterNot { it == id }
        )
    }

    fun undoLastAction() {
        val action = undo ?: return
        val note = notesById[action.clippingId]
        if (note != null && ReviewPlanner.isEligible(note)) {
            val records = snapshot.records.toMutableMap()
            if (action.previousRecord == null) records.remove(action.clippingId)
            else records[action.clippingId] = action.previousRecord
            snapshot = snapshot.copy(
                records = records,
                deck = snapshot.deck.reinsert(action.clippingId, action.deckIndex),
                batch = snapshot.batch.reinsert(action.clippingId, action.batchIndex)
            )
        }
        undo = null
    }

    fun nextBatch() {
        if (snapshot.deck.isEmpty()) {
            val deck = ReviewPlanner.makeDeck(notesById.values.toList(), snapshot.records, random)
            snapshot = snapshot.copy(deck = deck, cycleIds = deck.toSet())
        }
        val batch = snapshot.deck.take(ReviewPlanner.BATCH_SIZE)
        snapshot = snapshot.copy(batch = batch, batchTotal = batch.size, displayedIds = emptySet())
        undo = null
    }

    fun restoreExcluded() {
        val records = snapshot.records.mapValues { (_, record) -> record.copy(excluded = false) }
        val deck = ReviewPlanner.makeDeck(notesById.values.toList(), records, random)
        snapshot = snapshot.copy(records = records, deck = deck, cycleIds = deck.toSet())
        nextBatch()
    }

    private fun dayKey(now: Instant): String = LocalDate.ofInstant(now, zone).toString()

    private fun List<String>.reinsert(id: String, index: Int): List<String> =
        filterNot { it == id }.toMutableList().apply { add(index.coerceIn(0, size), id) }
}
