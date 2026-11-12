package com.hongxiang.kpilot.review

import com.hongxiang.kpilot.notes.ClippingKind
import com.hongxiang.kpilot.notes.KindleClipping
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

enum class ClippingReviewAction { NEXT, FAVORITE, EXCLUDE }

data class ClippingReviewRecord(
    val clippingId: String,
    val reviewCount: Int = 0,
    val lastReviewedAt: Long? = null,
    val isFavorite: Boolean = false,
    val favoriteUpdatedAt: Long = 0,
    val isExcluded: Boolean = false,
    val excludedUpdatedAt: Long = 0,
    val origin: String = ""
) {
    internal fun withFavorite(value: Boolean, now: Long, phoneId: String): ClippingReviewRecord =
        copy(isFavorite = value, favoriteUpdatedAt = nextEditTime(now, favoriteUpdatedAt), origin = phoneId)

    internal fun withExcluded(value: Boolean, now: Long, phoneId: String): ClippingReviewRecord =
        copy(isExcluded = value, excludedUpdatedAt = nextEditTime(now, excludedUpdatedAt), origin = phoneId)

    private fun nextEditTime(now: Long, previous: Long): Long =
        maxOf(now, previous.coerceAtMost(Long.MAX_VALUE - 1) + 1)
}

data class ClippingReviewUndo(
    val id: Long,
    val clippingId: String,
    val action: ClippingReviewAction,
    val previous: ClippingReviewRecord?,
    val expiresAt: Long
)

object ClippingsReviewPlanner {
    fun isEligible(clipping: KindleClipping): Boolean =
        (clipping.kind == ClippingKind.HIGHLIGHT || clipping.kind == ClippingKind.NOTE) &&
            clipping.text.isNotBlank()

    fun bookClippings(clippings: List<KindleClipping>, bookId: String): List<KindleClipping> =
        clippings.filter { it.bookId == bookId && isEligible(it) }.sortedWith(
            compareBy<KindleClipping> { it.locationStart ?: it.locationEnd ?: Int.MAX_VALUE }
                .thenBy { it.page?.substringBefore('-')?.replace(",", "")?.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.addedAt ?: Instant.MAX }
                .thenBy { it.id }
        )

    fun makeDeck(
        clippings: List<KindleClipping>,
        excludedIds: Set<String> = emptySet(),
        reviewCounts: Map<String, Int> = emptyMap(),
        random: Random = Random.Default
    ): List<String> {
        val eligible = clippings.filter { isEligible(it) && it.id !in excludedIds }
        val tiers = eligible.groupBy { max(0, reviewCounts[it.id] ?: 0) }
        val deck = mutableListOf<String>()
        var previousBookId: String? = null
        for (reviewCount in tiers.keys.sorted()) {
            val groups = tiers.getValue(reviewCount)
                .groupBy { it.bookId }
                .mapValues { (_, values) -> values.shuffled(random).toMutableList() }
                .toMutableMap()
            while (groups.isNotEmpty()) {
                val bookIds = groups.keys.shuffled(random).toMutableList()
                if (bookIds.size > 1) {
                    val firstDifferent = bookIds.indexOfFirst { it != previousBookId }
                    if (bookIds.first() == previousBookId && firstDifferent > 0) {
                        val first = bookIds[0]
                        bookIds[0] = bookIds[firstDifferent]
                        bookIds[firstDifferent] = first
                    }
                }
                for (bookId in bookIds) {
                    val values = groups[bookId] ?: continue
                    val clipping = values.removeLastOrNull() ?: continue
                    deck += clipping.id
                    previousBookId = bookId
                    if (values.isEmpty()) groups.remove(bookId)
                }
            }
        }
        return deck
    }

    fun reviewDayKey(now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()

    fun pruneDailyCounts(
        counts: Map<String, Int>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        keepDays: Long = 30
    ): Map<String, Int> {
        val oldest = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(keepDays)
        return counts.filterKeys { key ->
            val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@filterKeys false
            !date.isBefore(oldest)
        }
    }
}

enum class VocabularyReviewResult { AGAIN, HARD, GOOD, EASY, SKIPPED }

enum class VocabularyReviewState { NEW, LEARNING, REVIEW, RELEARNING }

data class VocabularyReviewRecord(
    val wordKey: String,
    val reviewCount: Int = 0,
    val forgottenCount: Int = 0,
    val lastReviewedAt: Long? = null,
    val state: VocabularyReviewState = VocabularyReviewState.NEW,
    val learningStep: Int = 0,
    val intervalDays: Int = 1,
    val easeFactor: Double = 2.5,
    val dueAt: Long? = null,
    val updatedAt: Long = 0,
    val origin: String = ""
) {
    init {
        require(reviewCount >= 0 && forgottenCount >= 0)
        require(learningStep in 0..1)
        require(intervalDays in 1..36_500)
        require(easeFactor.isFinite() && easeFactor in 1.3..3.0)
    }
}

data class VocabularyReviewQueue(
    val wordKeys: List<String>,
    val newCount: Int,
    val learningCount: Int,
    val reviewCount: Int,
    val nextLearningDueAt: Long?
)

data class VocabularyReviewUndo(
    val wordKey: String,
    val previous: VocabularyReviewRecord?
)

/** Anki-style learning steps and an SM-2-inspired review schedule, matching iOS. */
object VocabularyReviewPlanner {
    private const val MAX_INTERVAL_DAYS = 36_500

    fun updatedRecord(
        wordKey: String,
        current: VocabularyReviewRecord?,
        result: VocabularyReviewResult,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): VocabularyReviewRecord? {
        if (result == VocabularyReviewResult.SKIPPED) return current
        var record = current ?: VocabularyReviewRecord(wordKey)

        if (record.state == VocabularyReviewState.REVIEW) {
            val intervals = reviewIntervals(record, now, zone)
            when (result) {
                VocabularyReviewResult.AGAIN -> {
                    record = record.copy(
                        state = VocabularyReviewState.RELEARNING,
                        learningStep = 0,
                        intervalDays = max(1, record.intervalDays / 5),
                        easeFactor = max(1.3, record.easeFactor - 0.2),
                        dueAt = now + 60_000
                    )
                }
                VocabularyReviewResult.HARD -> {
                    record = graduate(record, intervals.hard, now, zone)
                        .copy(easeFactor = max(1.3, record.easeFactor - 0.15))
                }
                VocabularyReviewResult.GOOD -> record = graduate(record, intervals.good, now, zone)
                VocabularyReviewResult.EASY -> {
                    record = graduate(record, intervals.easy, now, zone)
                        .copy(easeFactor = min(3.0, record.easeFactor + 0.15))
                }
                VocabularyReviewResult.SKIPPED -> Unit
            }
        } else {
            val relearning = record.state == VocabularyReviewState.RELEARNING
            record = if (record.state == VocabularyReviewState.NEW) {
                record.copy(state = VocabularyReviewState.LEARNING)
            } else record
            when (result) {
                VocabularyReviewResult.AGAIN -> record = record.copy(
                    learningStep = 0,
                    dueAt = now + 60_000
                )
                VocabularyReviewResult.HARD -> {
                    val seconds = if (relearning) 90L else if (record.learningStep == 0) 330L else 600L
                    record = record.copy(dueAt = now + seconds * 1_000)
                }
                VocabularyReviewResult.GOOD -> {
                    record = if (relearning || record.learningStep == 1) {
                        graduate(record, if (relearning) record.intervalDays else 1, now, zone)
                    } else {
                        record.copy(learningStep = 1, dueAt = now + 600_000)
                    }
                }
                VocabularyReviewResult.EASY -> record = graduate(
                    record,
                    if (relearning) max(4, record.intervalDays + 1) else 4,
                    now,
                    zone
                )
                VocabularyReviewResult.SKIPPED -> Unit
            }
        }

        return record.copy(
            reviewCount = record.reviewCount + 1,
            lastReviewedAt = now,
            forgottenCount = record.forgottenCount + if (result == VocabularyReviewResult.AGAIN) 1 else 0
        )
    }

    fun dueDate(record: VocabularyReviewRecord?): Long? =
        record?.takeIf { it.state != VocabularyReviewState.NEW }?.dueAt

    fun isDue(
        record: VocabularyReviewRecord?,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val due = dueDate(record) ?: return false
        return if (record!!.state == VocabularyReviewState.LEARNING ||
            record.state == VocabularyReviewState.RELEARNING
        ) {
            due <= now
        } else {
            val current = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val dueDay = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
            !dueDay.isAfter(current)
        }
    }

    fun makeQueue(
        wordKeys: List<String>,
        records: Map<String, VocabularyReviewRecord>,
        excluding: Set<String> = emptySet(),
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): VocabularyReviewQueue {
        val seen = mutableSetOf<String>()
        val learning = mutableListOf<Pair<String, Long>>()
        val reviews = mutableListOf<Pair<String, Long>>()
        val new = mutableListOf<String>()
        val waitingDue = mutableListOf<Long>()
        for (key in wordKeys) {
            if (key in excluding || !seen.add(key)) continue
            val record = records[key]
            if (record == null || record.state == VocabularyReviewState.NEW) {
                new += key
                continue
            }
            if (record.state == VocabularyReviewState.LEARNING ||
                record.state == VocabularyReviewState.RELEARNING
            ) {
                val due = record.dueAt ?: now
                if (due <= now) learning += key to due else waitingDue += due
            } else if (isDue(record, now, zone)) {
                reviews += key to (record.dueAt ?: now)
            }
        }
        learning.sortWith(compareBy({ it.second }, { it.first }))
        reviews.sortWith(compareBy({ it.second }, { it.first }))
        return VocabularyReviewQueue(
            wordKeys = learning.map { it.first } + reviews.map { it.first } + new,
            newCount = new.size,
            learningCount = learning.size + waitingDue.size,
            reviewCount = reviews.size,
            nextLearningDueAt = waitingDue.minOrNull()
        )
    }

    private fun graduate(
        record: VocabularyReviewRecord,
        days: Int,
        now: Long,
        zone: ZoneId
    ): VocabularyReviewRecord {
        val interval = days.coerceIn(1, MAX_INTERVAL_DAYS)
        val due = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .plusDays(interval.toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()
        return record.copy(
            state = VocabularyReviewState.REVIEW,
            learningStep = 0,
            intervalDays = interval,
            dueAt = due
        )
    }

    private data class ReviewIntervals(val hard: Int, val good: Int, val easy: Int)

    private fun reviewIntervals(
        record: VocabularyReviewRecord,
        now: Long,
        zone: ZoneId
    ): ReviewIntervals {
        val due = record.dueAt ?: now
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dueDay = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        val overdue = max(0L, ChronoUnit.DAYS.between(dueDay, today)).toDouble()
        val interval = record.intervalDays.toDouble()
        val hard = min(MAX_INTERVAL_DAYS, max(record.intervalDays + 1, (interval * 1.2).roundToInt()))
        val good = min(MAX_INTERVAL_DAYS, max(hard + 1, ((interval + overdue / 2) * record.easeFactor).roundToInt()))
        val easy = min(MAX_INTERVAL_DAYS, max(good + 1, ((interval + overdue) * record.easeFactor * 1.3).roundToInt()))
        return ReviewIntervals(hard, good, easy)
    }
}

data class VocabularyDefinition(
    val definition: String = "",
    val note: String = "",
    val updatedAt: Long = 0,
    val origin: String = ""
)
