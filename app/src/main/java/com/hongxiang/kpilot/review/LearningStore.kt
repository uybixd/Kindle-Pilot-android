package com.hongxiang.kpilot.review

import android.content.Context
import com.hongxiang.kpilot.notes.AtomicCache
import org.json.JSONObject
import java.io.File

data class LearningState(
    val clippingReviews: Map<String, ClippingReviewRecord> = emptyMap(),
    val clippingDailyCounts: Map<String, Int> = emptyMap(),
    val vocabularyReviews: Map<String, VocabularyReviewRecord> = emptyMap(),
    val definitions: Map<String, VocabularyDefinition> = emptyMap()
)

/**
 * JSON codec shared by the local replica and the Kindle-hosted state file.
 * Daily counts remain phone-local and are omitted from the shared payload.
 */
internal object LearningCodec {
    const val SCHEMA = 2

    fun encode(state: LearningState, includeDailyCounts: Boolean = true): String {        val root = JSONObject()
        root.put("schema", SCHEMA)
        root.put("clippings", JSONObject().apply {
            state.clippingReviews.values.forEach { record ->
                put(record.clippingId, JSONObject().apply {
                    put("reviewCount", record.reviewCount.coerceAtLeast(0))
                    record.lastReviewedAt?.let { put("lastReviewedAt", it) }
                    put("isFavorite", record.isFavorite)
                    put("favoriteUpdatedAt", record.favoriteUpdatedAt)
                    put("isExcluded", record.isExcluded)
                    put("excludedUpdatedAt", record.excludedUpdatedAt)
                    put("origin", record.origin)
                })
            }
        })
        if (includeDailyCounts) {
            root.put("dailyCounts", JSONObject().apply {
                state.clippingDailyCounts.forEach { (key, value) -> put(key, value.coerceAtLeast(0)) }
            })
        }
        root.put("vocabulary", JSONObject().apply {
            state.vocabularyReviews.values.forEach { record ->
                put(record.wordKey, JSONObject().apply {
                    put("reviewCount", record.reviewCount)
                    put("forgottenCount", record.forgottenCount)
                    record.lastReviewedAt?.let { put("lastReviewedAt", it) }
                    put("state", record.state.name)
                    put("learningStep", record.learningStep)
                    put("intervalDays", record.intervalDays)
                    put("easeFactor", record.easeFactor)
                    record.dueAt?.let { put("dueAt", it) }
                    put("updatedAt", record.updatedAt)
                    put("origin", record.origin)
                })
            }
        })
        root.put("definitions", JSONObject().apply {
            state.definitions.forEach { (key, value) ->
                put(key, JSONObject().apply {
                    put("definition", value.definition)
                    put("note", value.note)
                    put("updatedAt", value.updatedAt)
                    put("origin", value.origin)
                })
            }
        })
        return root.toString()
    }

    fun decode(text: String): LearningState {
        val root = JSONObject(text)
        val schema = root.optInt("schema", 1)
        if (schema > SCHEMA) throw UnsupportedLearningSchemaException(schema)
        val clippings = mutableMapOf<String, ClippingReviewRecord>()
        root.optJSONObject("clippings")?.let { objectValue ->
            objectValue.keys().forEach { id ->
                val item = objectValue.optJSONObject(id) ?: return@forEach
                clippings[id] = ClippingReviewRecord(
                    clippingId = id,
                    reviewCount = item.optInt("reviewCount", 0).coerceAtLeast(0),
                    lastReviewedAt = item.optLongOrNull("lastReviewedAt"),
                    isFavorite = item.optBoolean("isFavorite", false),
                    favoriteUpdatedAt = item.optLong("favoriteUpdatedAt", 0).coerceAtLeast(0),
                    isExcluded = item.optBoolean("isExcluded", false),
                    excludedUpdatedAt = item.optLong("excludedUpdatedAt", 0).coerceAtLeast(0),
                    origin = item.optString("origin").take(128)
                )
            }
        }
        val dailyCounts = mutableMapOf<String, Int>()
        root.optJSONObject("dailyCounts")?.let { objectValue ->
            objectValue.keys().forEach { key -> dailyCounts[key] = objectValue.optInt(key, 0).coerceAtLeast(0) }
        }
        val vocabulary = mutableMapOf<String, VocabularyReviewRecord>()
        root.optJSONObject("vocabulary")?.let { objectValue ->
            objectValue.keys().forEach { key ->
                val item = objectValue.optJSONObject(key) ?: return@forEach
                val state = runCatching {
                    VocabularyReviewState.valueOf(item.optString("state", "NEW"))
                }.getOrDefault(VocabularyReviewState.NEW)
                runCatching {
                    VocabularyReviewRecord(
                        wordKey = key,
                        reviewCount = item.optInt("reviewCount", 0).coerceAtLeast(0),
                        forgottenCount = item.optInt("forgottenCount", 0).coerceAtLeast(0),
                        lastReviewedAt = item.optLongOrNull("lastReviewedAt"),
                        state = state,
                        learningStep = item.optInt("learningStep", 0).coerceIn(0, 1),
                        intervalDays = item.optInt("intervalDays", 1).coerceIn(1, 36_500),
                        easeFactor = item.optDouble("easeFactor", 2.5).coerceIn(1.3, 3.0),
                        dueAt = item.optLongOrNull("dueAt"),
                        updatedAt = item.optLong("updatedAt", 0).coerceAtLeast(0),
                        origin = item.optString("origin").take(128)
                    )
                }.onSuccess { vocabulary[key] = it }
            }
        }
        val definitions = mutableMapOf<String, VocabularyDefinition>()
        root.optJSONObject("definitions")?.let { objectValue ->
            objectValue.keys().forEach { key ->
                val item = objectValue.optJSONObject(key) ?: return@forEach
                definitions[key] = VocabularyDefinition(
                    item.optString("definition").trim(),
                    item.optString("note").trim(),
                    item.optLong("updatedAt", 0).coerceAtLeast(0),
                    item.optString("origin").take(128)
                )
            }
        }
        return LearningState(clippings, dailyCounts, vocabulary, definitions)
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null
}

/** Refuse to rewrite a file that a newer app version may have extended. */
internal class UnsupportedLearningSchemaException(val schema: Int) :
    IllegalArgumentException("Unsupported learning schema $schema")

/** Per-Kindle local learning data. */
class LearningStore(context: Context) {
    private val directory = context.noBackupFilesDir.resolve("learning")
    private val scopePattern = Regex("[a-f0-9]{64}")

    fun load(scope: String): LearningState {
        val file = file(scope)
        if (!file.isFile) return LearningState()
        return runCatching { LearningCodec.decode(file.readText()) }.getOrDefault(LearningState())
    }

    fun save(scope: String, state: LearningState) {
        AtomicCache.write(file(scope)) {
            it.write(LearningCodec.encode(state, includeDailyCounts = true).toByteArray(Charsets.UTF_8))
        }
    }

    private fun file(scope: String): File {
        require(scope.matches(scopePattern))
        return directory.resolve("$scope.json")
    }
}
