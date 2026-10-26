package com.hongxiang.kpilot.vocabulary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyTest {
    private val validSchema = mapOf(
        "LOOKUPS" to setOf("id", "word_key", "book_key", "pos", "usage", "timestamp"),
        "WORDS" to setOf("id", "word", "stem", "lang"),
        "BOOK_INFO" to setOf("id", "title", "authors")
    )

    @Test fun acceptsExpectedKindleSchemaCaseInsensitively() {
        VocabularySchema.validate(validSchema.mapKeys { it.key.lowercase() })
    }

    @Test fun sshProducerMustNotOwnOrCloseAtomicCacheStream() {
        assertTrue(KEEP_CACHE_OUTPUT_OPEN)
        val diagnostic = VocabularyDiagnostics.cache(com.hongxiang.kpilot.notes.AtomicCacheStage.FSYNC)
        assertEquals("V-CACHE-FSYNC", diagnostic.code)
        assertTrue(diagnostic.message.contains("previous cache was kept", ignoreCase = true))
    }

    @Test fun reportsSchemaMismatchWithoutDatabaseContent() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            VocabularySchema.validate(validSchema - "WORDS")
        }
        assertTrue(error.message.orEmpty().contains("WORDS table"))
        assertTrue(error.message.orEmpty().contains("previous cache was kept", true))
    }

    @Test fun mapsSyntheticRowsAndNormalizesUsage() {
        val row = VocabularyRowMapper.from(
            mapOf(
                "lookup_id" to "l1", "word_key" to "w1", "word" to "running", "stem" to "run",
                "word_lang" to "en", "book_title" to "Fixture Book", "authors" to "Fixture Author",
                "pos" to "42", "usage" to "a\u00A0  fixture\n sentence", "timestamp" to 1234L
            )
        )
        assertEquals("running (run)", row.displayWord)
        assertEquals("a fixture sentence", row.usage)
        assertEquals(1234L, row.timestamp)
    }

    @Test fun groupsAndExportsOnlySyntheticInputs() {
        val first = VocabularyRowMapper.from(mapOf("lookup_id" to "1", "word_key" to "word", "word" to "test", "book_title" to "Book A", "usage" to "first"))
        val second = VocabularyRowMapper.from(mapOf("lookup_id" to "2", "word_key" to "word", "word" to "test", "book_title" to "Book B", "usage" to "second, quoted"))
        val snapshot = VocabularySnapshot(listOf(first, second), java.time.Instant.EPOCH, 0)
        assertEquals(1, snapshot.words.size)
        assertTrue(VocabularyExporter.export(snapshot.words, VocabularyExportFormat.TEXT).contains("Book A"))
        val csv = VocabularyExporter.export(snapshot.words, VocabularyExportFormat.CSV)
        assertTrue(csv.startsWith("word,stem,book_title"))
        val markdown = VocabularyExporter.export(snapshot.words, VocabularyExportFormat.MARKDOWN)
        assertTrue(markdown.startsWith("## test"))
        assertTrue(markdown.contains("> first"))
        assertTrue(markdown.contains("Book B"))
        assertTrue(csv.contains("\"second, quoted\""))
    }

    @Test fun sortsVocabularyByLookupCountWithAlphabeticalTies() {
        fun word(name: String, count: Int) = VocabularyWord(
            key = name,
            displayWord = name,
            lookups = List(count) { index ->
                VocabularyLookup(
                    id = "$name-$index",
                    wordKey = name,
                    word = name,
                    stem = name,
                    wordLanguage = "en",
                    bookTitle = "Book",
                    authors = "",
                    position = null,
                    usage = "",
                    timestamp = null
                )
            }
        )
        val words = listOf(word("zebra", 5), word("beta", 3), word("alpha", 3))

        assertEquals(
            listOf("zebra", "alpha", "beta"),
            VocabularyOrdering.sort(words, VocabularySortOrder.LOOKUP_COUNT).map { it.displayWord }
        )
        assertEquals(
            listOf("alpha", "beta", "zebra"),
            VocabularyOrdering.sort(words, VocabularySortOrder.ALPHABETICAL).map { it.displayWord }
        )
    }
}
