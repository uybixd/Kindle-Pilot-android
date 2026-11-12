package com.hongxiang.kpilot.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningMergeTest {
    @Test
    fun `favorite flag uses latest update`() {
        val local = clipping(isFavorite = true, favoriteUpdatedAt = 200)
        val remote = clipping(isFavorite = false, favoriteUpdatedAt = 300)

        val merged = LearningMerge.merge(state(clipping = local), state(clipping = remote)).clippingReviews.getValue(ID)

        assertFalse(merged.isFavorite)
        assertEquals(300, merged.favoriteUpdatedAt)
    }

    @Test
    fun `legacy favorite conflict keeps the favorite`() {
        val starred = clipping(isFavorite = true)
        val unstarred = clipping(isFavorite = false)

        val forward = LearningMerge.merge(state(clipping = starred), state(clipping = unstarred))
        val reverse = LearningMerge.merge(state(clipping = unstarred), state(clipping = starred))

        assertTrue(forward.clippingReviews.getValue(ID).isFavorite)
        assertTrue(reverse.clippingReviews.getValue(ID).isFavorite)
    }

    @Test
    fun `excluded flag merges independently from favorite`() {
        val local = clipping(isFavorite = true, favoriteUpdatedAt = 100, isExcluded = false, excludedUpdatedAt = 0)
        val remote = clipping(isFavorite = false, favoriteUpdatedAt = 50, isExcluded = true, excludedUpdatedAt = 200)

        val merged = LearningMerge.merge(state(clipping = local), state(clipping = remote)).clippingReviews.getValue(ID)

        assertTrue(merged.isFavorite)
        assertTrue(merged.isExcluded)
        assertEquals(100, merged.favoriteUpdatedAt)
        assertEquals(200, merged.excludedUpdatedAt)
    }

    @Test
    fun `review counts add up by maximum`() {
        val local = clipping(reviewCount = 3, lastReviewedAt = 500)
        val remote = clipping(reviewCount = 7, lastReviewedAt = null)

        val merged = LearningMerge.merge(state(clipping = local), state(clipping = remote)).clippingReviews.getValue(ID)

        assertEquals(7, merged.reviewCount)
        assertEquals(500L, merged.lastReviewedAt)
    }

    @Test
    fun `vocabulary record keeps the newest version`() {
        val local = vocabulary(reviewCount = 2, updatedAt = 100, origin = "phone-a")
        val remote = vocabulary(reviewCount = 5, updatedAt = 200, origin = "phone-b")

        val merged = LearningMerge.merge(state(vocabulary = local), state(vocabulary = remote)).vocabularyReviews.getValue(KEY)

        assertEquals(remote, merged)
    }

    @Test
    fun `vocabulary timestamp tie uses origin as tiebreak`() {
        val local = vocabulary(reviewCount = 1, updatedAt = 100, origin = "phone-a")
        val remote = vocabulary(reviewCount = 9, updatedAt = 100, origin = "phone-b")

        val forward = LearningMerge.merge(state(vocabulary = local), state(vocabulary = remote))
        val reverse = LearningMerge.merge(state(vocabulary = remote), state(vocabulary = local))

        assertEquals(remote, forward.vocabularyReviews.getValue(KEY))
        assertEquals(remote, reverse.vocabularyReviews.getValue(KEY))
    }

    @Test
    fun `newer empty definition is a tombstone`() {
        val local = VocabularyDefinition("old meaning", updatedAt = 100, origin = "phone-a")
        val remote = VocabularyDefinition("", updatedAt = 200, origin = "phone-b")

        val merged = LearningMerge.merge(state(definition = local), state(definition = remote)).definitions.getValue(KEY)

        assertEquals(remote, merged)
        assertEquals("", merged.definition)
    }

    @Test
    fun `legacy empty definition does not erase an existing one`() {
        val saved = VocabularyDefinition("meaning", updatedAt = 0)
        val empty = VocabularyDefinition("", updatedAt = 0)

        val forward = LearningMerge.merge(state(definition = saved), state(definition = empty))
        val reverse = LearningMerge.merge(state(definition = empty), state(definition = saved))

        assertEquals("meaning", forward.definitions.getValue(KEY).definition)
        assertEquals("meaning", reverse.definitions.getValue(KEY).definition)
    }

    @Test
    fun `newer definition wins`() {
        val local = VocabularyDefinition("short", updatedAt = 300, origin = "phone-a")
        val remote = VocabularyDefinition("detailed", updatedAt = 200, origin = "phone-b")

        val merged = LearningMerge.merge(state(definition = local), state(definition = remote)).definitions.getValue(KEY)

        assertEquals("short", merged.definition)
    }

    @Test
    fun `daily counts stay phone local`() {
        val local = LearningState(
            clippingDailyCounts = mapOf("2026-11-10" to 4),
            clippingReviews = mapOf(ID to clipping())
        )
        val remote = LearningState(
            clippingDailyCounts = mapOf("2026-11-10" to 99),
            clippingReviews = mapOf(ID to clipping(reviewCount = 2))
        )

        val merged = LearningMerge.merge(local, remote)

        assertEquals(mapOf("2026-11-10" to 4), merged.clippingDailyCounts)
        assertEquals(2, merged.clippingReviews.getValue(ID).reviewCount)
    }

    @Test
    fun `shared data merge is commutative`() {
        val left = LearningState(
            clippingReviews = mapOf(
                ID to clipping(reviewCount = 4, isFavorite = true, favoriteUpdatedAt = 100, isExcluded = true, excludedUpdatedAt = 0)
            ),
            vocabularyReviews = mapOf(KEY to vocabulary(reviewCount = 1, updatedAt = 100, origin = "phone-a")),
            definitions = mapOf(KEY to VocabularyDefinition("one", updatedAt = 100, origin = "phone-a"))
        )
        val right = LearningState(
            clippingReviews = mapOf(
                ID to clipping(reviewCount = 6, isFavorite = false, favoriteUpdatedAt = 100, isExcluded = false, excludedUpdatedAt = 100)
            ),
            vocabularyReviews = mapOf(KEY to vocabulary(reviewCount = 9, updatedAt = 100, origin = "phone-b")),
            definitions = mapOf(KEY to VocabularyDefinition("two", updatedAt = 100, origin = "phone-b"))
        )

        val forward = LearningMerge.merge(left, right)
        val reverse = LearningMerge.merge(right, left)

        // Latest-writer flags: the tie at 100 with no origin keeps the favorite,
        // while the later excluded change at 100 clears the exclusion.
        val expectedClipping = ClippingReviewRecord(
            clippingId = ID,
            reviewCount = 6,
            isFavorite = true,
            favoriteUpdatedAt = 100,
            isExcluded = false,
            excludedUpdatedAt = 100
        )
        assertEquals(expectedClipping, forward.clippingReviews.getValue(ID))
        assertEquals(expectedClipping, reverse.clippingReviews.getValue(ID))
        assertEquals(forward.vocabularyReviews, reverse.vocabularyReviews)
        assertEquals(forward.definitions, reverse.definitions)
    }

    @Test
    fun `union keeps records that exist on only one side`() {
        val local = LearningState(clippingReviews = mapOf("local" to clipping().copy(clippingId = "local")))
        val remote = LearningState(
            clippingReviews = mapOf("remote" to clipping().copy(clippingId = "remote")),
            definitions = mapOf(KEY to VocabularyDefinition("remote meaning"))
        )

        val merged = LearningMerge.merge(local, remote)

        assertEquals(setOf("local", "remote"), merged.clippingReviews.keys)
        assertEquals("remote meaning", merged.definitions.getValue(KEY).definition)
        assertNull(merged.vocabularyReviews[KEY])
    }

    @Test
    fun `codec round trips metadata and omits daily counts from the shared payload`() {
        val original = LearningState(
            clippingReviews = mapOf(ID to clipping(isFavorite = true, favoriteUpdatedAt = 123, origin = "phone-a")),
            clippingDailyCounts = mapOf("2026-11-10" to 5),
            vocabularyReviews = mapOf(KEY to vocabulary(reviewCount = 3, updatedAt = 456, origin = "phone-a")),
            definitions = mapOf(KEY to VocabularyDefinition("meaning", "note", updatedAt = 789, origin = "phone-a"))
        )

        val local = LearningCodec.decode(LearningCodec.encode(original))
        val shared = LearningCodec.decode(LearningCodec.encode(original, includeDailyCounts = false))

        assertEquals(original, local)
        assertEquals(emptyMap<String, Int>(), shared.clippingDailyCounts)
        assertEquals(original.clippingReviews, shared.clippingReviews)
        assertEquals(original.vocabularyReviews, shared.vocabularyReviews)
        assertEquals(original.definitions, shared.definitions)
    }

    @Test
    fun `codec reads legacy records without metadata`() {
        val legacy = """
            {"schema":1,"clippings":{"$ID":{"reviewCount":2,"isFavorite":true,"isExcluded":false}},
             "vocabulary":{"$KEY":{"reviewCount":1,"state":"LEARNING"}},
             "definitions":{"$KEY":{"definition":"old"}}}
        """.trimIndent()

        val state = LearningCodec.decode(legacy)

        assertEquals(2, state.clippingReviews.getValue(ID).reviewCount)
        assertTrue(state.clippingReviews.getValue(ID).isFavorite)
        assertEquals(0, state.clippingReviews.getValue(ID).favoriteUpdatedAt)
        assertEquals(VocabularyReviewState.LEARNING, state.vocabularyReviews.getValue(KEY).state)
        assertEquals("old", state.definitions.getValue(KEY).definition)
    }

    @Test(expected = UnsupportedLearningSchemaException::class)
    fun `codec refuses a newer schema`() {
        LearningCodec.decode("""{"schema":3,"clippings":{}}""")
    }

    private fun state(
        clipping: ClippingReviewRecord? = null,
        vocabulary: VocabularyReviewRecord? = null,
        definition: VocabularyDefinition? = null
    ) = LearningState(
        clippingReviews = clipping?.let { mapOf(ID to it) }.orEmpty(),
        vocabularyReviews = vocabulary?.let { mapOf(KEY to it) }.orEmpty(),
        definitions = definition?.let { mapOf(KEY to it) }.orEmpty()
    )

    private fun clipping(
        reviewCount: Int = 0,
        lastReviewedAt: Long? = null,
        isFavorite: Boolean = false,
        favoriteUpdatedAt: Long = 0,
        isExcluded: Boolean = false,
        excludedUpdatedAt: Long = 0,
        origin: String = ""
    ) = ClippingReviewRecord(
        clippingId = ID,
        reviewCount = reviewCount,
        lastReviewedAt = lastReviewedAt,
        isFavorite = isFavorite,
        favoriteUpdatedAt = favoriteUpdatedAt,
        isExcluded = isExcluded,
        excludedUpdatedAt = excludedUpdatedAt,
        origin = origin
    )

    private fun vocabulary(
        reviewCount: Int = 0,
        updatedAt: Long = 0,
        origin: String = ""
    ) = VocabularyReviewRecord(
        wordKey = KEY,
        reviewCount = reviewCount,
        state = VocabularyReviewState.REVIEW,
        intervalDays = 3,
        dueAt = 1_000,
        updatedAt = updatedAt,
        origin = origin
    )

    private companion object {
        const val ID = "clip-1"
        const val KEY = "serendipity"
    }
}
