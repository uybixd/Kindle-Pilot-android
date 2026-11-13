package com.hongxiang.kpilot.vocabulary

import java.io.InputStream
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipInputStream

enum class WiktionaryEdition(val domain: String) {
    ENGLISH("en.wiktionary.org"), SIMPLE_ENGLISH("simple.wiktionary.org")
}

data class VocabularyDictionarySense(val partOfSpeech: String, val gloss: String)

data class VocabularyDictionaryEntry(
    val headword: String,
    val edition: WiktionaryEdition,
    val ipa: String,
    val senses: List<VocabularyDictionarySense>
)

internal object VocabularyDictionary {
    const val ASSET = "dictionary/wiktionary-en.zip"

    fun match(
        lookups: List<VocabularyLookup>,
        openDictionary: () -> InputStream
    ): Map<String, VocabularyDictionaryEntry> {
        val candidates = lookups.distinctBy(VocabularyLookup::wordKey)
            .filter { it.wordLanguage.isBlank() || it.wordLanguage.isEnglish() }
            .associate { lookup ->
                lookup.wordKey to listOf(lookup.word, lookup.stem).map(::normalize).filter(String::isNotEmpty)
            }
        val wanted = candidates.values.flatten().mapTo(hashSetOf(), ::lowercase)
        if (wanted.isEmpty()) return emptyMap()

        val entries = ZipInputStream(openDictionary()).use { archive ->
            if (archive.nextEntry == null) return emptyMap()
            archive.bufferedReader().useLines { lines ->
                lines.filter { lowercase(it.substringBefore('\t')) in wanted }
                    .mapNotNull(::parseEntry)
                    .associateBy(VocabularyDictionaryEntry::headword)
            }
        }
        return buildMap {
            for ((wordKey, spellings) in candidates) {
                val entry = spellings.firstNotNullOfOrNull { entries[it] ?: entries[lowercase(it)] }
                if (entry != null) put(wordKey, entry)
            }
        }
    }

    private fun parseEntry(line: String): VocabularyDictionaryEntry? {
        val fields = line.split('\t')
        if (fields.size < 5 || fields.size % 2 == 0 || fields[2].isBlank()) return null
        val edition = when (fields[1]) {
            "en" -> WiktionaryEdition.ENGLISH
            "simple" -> WiktionaryEdition.SIMPLE_ENGLISH
            else -> return null
        }
        val senses = (3 until fields.size step 2).mapNotNull { index ->
            val partOfSpeech = fields[index]
            val gloss = fields[index + 1]
            if (partOfSpeech.isBlank() || gloss.isBlank()) null else VocabularyDictionarySense(partOfSpeech, gloss)
        }
        return if (senses.isEmpty()) null else VocabularyDictionaryEntry(fields[0], edition, fields[2], senses)
    }

    private fun normalize(word: String): String =
        Normalizer.normalize(word.trim().replace('’', '\''), Normalizer.Form.NFC)

    private fun lowercase(word: String): String = word.lowercase(Locale.ROOT)

    private fun String.isEnglish(): Boolean =
        when (lowercase(trim()).substringBefore('-').substringBefore('_')) {
            "en", "eng" -> true
            else -> false
        }
}
