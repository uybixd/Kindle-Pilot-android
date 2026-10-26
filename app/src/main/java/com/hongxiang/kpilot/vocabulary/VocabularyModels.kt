package com.hongxiang.kpilot.vocabulary

import java.time.Instant
import java.util.Locale

data class VocabularyLookup(
    val id: String,
    val wordKey: String,
    val word: String,
    val stem: String,
    val wordLanguage: String,
    val bookTitle: String,
    val authors: String,
    val position: String?,
    val usage: String,
    val timestamp: Long?
) {
    val displayWord: String get() = if (stem.isNotBlank() && !stem.equals(word, true)) "$word ($stem)" else word
    val bookDisplay: String get() = if (authors.isBlank()) bookTitle else "$bookTitle — $authors"
}

data class VocabularyWord(
    val key: String,
    val displayWord: String,
    val lookups: List<VocabularyLookup>
)

enum class VocabularySortOrder { ALPHABETICAL, LOOKUP_COUNT }

object VocabularyOrdering {
    fun sort(words: List<VocabularyWord>, order: VocabularySortOrder): List<VocabularyWord> = when (order) {
        VocabularySortOrder.ALPHABETICAL -> words.sortedBy { it.displayWord.lowercase(Locale.ROOT) }
        VocabularySortOrder.LOOKUP_COUNT -> words.sortedWith(
            compareByDescending<VocabularyWord> { it.lookups.size }
                .thenBy { it.displayWord.lowercase(Locale.ROOT) }
        )
    }
}

data class VocabularySnapshot(
    val lookups: List<VocabularyLookup>,
    val syncedAt: Instant,
    val sourceBytes: Long
) {
    val words: List<VocabularyWord> get() = lookups.groupBy(VocabularyLookup::wordKey)
        .map { (key, records) -> VocabularyWord(key, records.first().displayWord, records) }
        .sortedBy { it.displayWord.lowercase() }
}

object VocabularySchema {
    val required = mapOf(
        "LOOKUPS" to setOf("id", "word_key", "book_key", "pos", "usage", "timestamp"),
        "WORDS" to setOf("id", "word", "stem", "lang"),
        "BOOK_INFO" to setOf("id", "title", "authors")
    )

    fun validate(schema: Map<String, Set<String>>) {
        val normalized = schema.mapKeys { it.key.uppercase() }
            .mapValues { (_, columns) -> columns.map(String::lowercase).toSet() }
        val missing = required.mapNotNull { (table, columns) ->
            val actual = normalized[table] ?: return@mapNotNull "$table table"
            val missingColumns = columns - actual
            missingColumns.takeIf(Set<String>::isNotEmpty)?.let { "$table columns: ${it.sorted().joinToString()}" }
        }
        require(missing.isEmpty()) {
            "This Kindle vocabulary database schema is not supported (${missing.joinToString("; ")}). The previous cache was kept."
        }
    }
}

object VocabularyRowMapper {
    fun from(values: Map<String, Any?>): VocabularyLookup = VocabularyLookup(
        id = values["lookup_id"]?.toString().orEmpty(),
        wordKey = values["word_key"]?.toString().orEmpty(),
        word = values["word"]?.toString().orEmpty(),
        stem = values["stem"]?.toString().orEmpty(),
        wordLanguage = values["word_lang"]?.toString().orEmpty(),
        bookTitle = values["book_title"]?.toString().orEmpty().ifBlank { "Untitled" },
        authors = values["authors"]?.toString().orEmpty(),
        position = values["pos"]?.toString()?.takeIf(String::isNotBlank),
        usage = values["usage"]?.toString().orEmpty().replace('\u00A0', ' ').split(Regex("\\s+")).joinToString(" ").trim(),
        timestamp = (values["timestamp"] as? Number)?.toLong() ?: values["timestamp"]?.toString()?.toLongOrNull()
    )
}

enum class VocabularyExportFormat(val extension: String, val mimeType: String) {
    MARKDOWN("md", "text/markdown"), TEXT("txt", "text/plain"), CSV("csv", "text/csv")
}

object VocabularyExporter {
    fun export(words: List<VocabularyWord>, format: VocabularyExportFormat): String = when (format) {
        VocabularyExportFormat.MARKDOWN -> words.joinToString("\n\n") { word -> "## ${word.displayWord}\n\n" + word.lookups.joinToString("\n\n") { "> ${it.usage}\n\n${it.bookDisplay}" } }
        VocabularyExportFormat.TEXT -> words.joinToString("\n\n") { word ->
            buildString {
                append(word.displayWord).append('\n').append("=".repeat(word.displayWord.length.coerceAtLeast(1)))
                word.lookups.forEach { lookup ->
                    append("\n\n").append(lookup.bookDisplay)
                    lookup.position?.let { append(" · ").append(it) }
                    if (lookup.usage.isNotBlank()) append('\n').append(lookup.usage)
                }
            }
        }
        VocabularyExportFormat.CSV -> buildString {
            append("word,stem,book_title,authors,position,usage,timestamp\n")
            words.flatMap(VocabularyWord::lookups).forEach { row ->
                append(listOf(row.word, row.stem, row.bookTitle, row.authors, row.position.orEmpty(), row.usage, row.timestamp?.toString().orEmpty())
                    .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
                append('\n')
            }
        }.trimEnd()
    }
}
