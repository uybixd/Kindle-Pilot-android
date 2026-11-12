package com.hongxiang.kpilot.review

/**
 * Deterministic merge for the shared Kindle state.
 * Favorites/hidden flags merge field-by-field; vocabulary and definitions use
 * last-writer-wins with the phone origin as a tiebreaker. Daily counts stay local.
 */
internal object LearningMerge {
    fun merge(local: LearningState, remote: LearningState): LearningState = LearningState(
        clippingReviews = mergeClippings(local.clippingReviews, remote.clippingReviews),
        clippingDailyCounts = local.clippingDailyCounts,
        vocabularyReviews = mergeVocabulary(local.vocabularyReviews, remote.vocabularyReviews),
        definitions = mergeDefinitions(local.definitions, remote.definitions)
    )

    private fun mergeClippings(
        local: Map<String, ClippingReviewRecord>,
        remote: Map<String, ClippingReviewRecord>
    ): Map<String, ClippingReviewRecord> {
        val ids = local.keys + remote.keys
        return ids.associateWith { id ->
            val left = local[id]
            val right = remote[id]
            when {
                left == null -> right!!
                right == null -> left
                else -> ClippingReviewRecord(
                    clippingId = id,
                    reviewCount = maxOf(left.reviewCount, right.reviewCount),
                    lastReviewedAt = maxNullable(left.lastReviewedAt, right.lastReviewedAt),
                    isFavorite = mergeBoolean(
                        left.isFavorite, left.favoriteUpdatedAt,
                        right.isFavorite, right.favoriteUpdatedAt,
                        left.origin, right.origin
                    ),
                    favoriteUpdatedAt = maxOf(left.favoriteUpdatedAt, right.favoriteUpdatedAt),
                    isExcluded = mergeBoolean(
                        left.isExcluded, left.excludedUpdatedAt,
                        right.isExcluded, right.excludedUpdatedAt,
                        left.origin, right.origin
                    ),
                    excludedUpdatedAt = maxOf(left.excludedUpdatedAt, right.excludedUpdatedAt),
                    origin = if (left.origin >= right.origin) left.origin else right.origin
                )
            }
        }
    }

    private fun mergeVocabulary(
        local: Map<String, VocabularyReviewRecord>,
        remote: Map<String, VocabularyReviewRecord>
    ): Map<String, VocabularyReviewRecord> {
        val ids = local.keys + remote.keys
        return ids.associateWith { key ->
            val left = local[key]
            val right = remote[key]
            when {
                left == null -> right!!
                right == null -> left
                else -> chooseVocabulary(left, right)
            }
        }
    }

    private fun mergeDefinitions(
        local: Map<String, VocabularyDefinition>,
        remote: Map<String, VocabularyDefinition>
    ): Map<String, VocabularyDefinition> {
        val keys = local.keys + remote.keys
        return keys.associateWith { key ->
            val left = local[key]
            val right = remote[key]
            when {
                left == null -> right!!
                right == null -> left
                else -> chooseDefinition(left, right)
            }
        }
    }

    private fun chooseVocabulary(
        left: VocabularyReviewRecord,
        right: VocabularyReviewRecord
    ): VocabularyReviewRecord {
        val version = compareVersion(left.updatedAt, left.origin, right.updatedAt, right.origin)
        if (version != 0) return if (version > 0) left else right
        if (left.updatedAt > 0) {
            // Same timestamp and origin cannot normally differ; keep the choice deterministic.
            return if (left.toString() >= right.toString()) left else right
        }
        // Legacy records have no metadata. Keep the most progressed card, then a
        // deterministic tiebreak so both phones converge on the same record.
        if (left.reviewCount != right.reviewCount) return if (left.reviewCount > right.reviewCount) left else right
        if ((left.lastReviewedAt ?: 0) != (right.lastReviewedAt ?: 0)) {
            return if ((left.lastReviewedAt ?: 0) > (right.lastReviewedAt ?: 0)) left else right
        }
        return if (left.toString() >= right.toString()) left else right
    }

    private fun chooseDefinition(
        left: VocabularyDefinition,
        right: VocabularyDefinition
    ): VocabularyDefinition {
        val version = compareVersion(left.updatedAt, left.origin, right.updatedAt, right.origin)
        if (version != 0) return if (version > 0) left else right
        val leftEmpty = left.definition.isEmpty()
        val rightEmpty = right.definition.isEmpty()
        if (leftEmpty != rightEmpty) {
            // A newer empty definition is a deletion; a legacy empty definition just
            // means the word never had one, so prefer the non-empty legacy value.
            return if (left.updatedAt == 0L && right.updatedAt == 0L) {
                if (leftEmpty) right else left
            } else {
                if (leftEmpty) left else right
            }
        }
        return if (left.toString() >= right.toString()) left else right
    }

    private fun mergeBoolean(
        leftValue: Boolean,
        leftAt: Long,
        rightValue: Boolean,
        rightAt: Long,
        leftOrigin: String,
        rightOrigin: String
    ): Boolean {
        if (leftAt != rightAt) return if (leftAt > rightAt) leftValue else rightValue
        if (leftValue == rightValue) return leftValue
        if (leftAt == 0L) return leftValue || rightValue
        val origin = leftOrigin.compareTo(rightOrigin)
        return if (origin > 0) leftValue else if (origin < 0) rightValue else leftValue || rightValue
    }

    private fun compareVersion(
        leftAt: Long,
        leftOrigin: String,
        rightAt: Long,
        rightOrigin: String
    ): Int {
        if (leftAt != rightAt) return leftAt.compareTo(rightAt)
        return leftOrigin.compareTo(rightOrigin)
    }

    private fun maxNullable(left: Long?, right: Long?): Long? = when {
        left == null -> right
        right == null -> left
        else -> maxOf(left, right)
    }
}
