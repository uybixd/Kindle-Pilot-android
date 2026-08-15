package com.hongxiang.kpilot.notes

internal object SingleSelectionClassifier {
    private val whitespace = Regex("\\s+")
    private val wordPunctuation = setOf('\''.code, '’'.code, '-'.code, '‐'.code, '‑'.code, '–'.code)

    fun isSingleSelection(content: String): Boolean {
        if (normalizedSingleWord(content) != null) return true
        val stripped = stripEdgePunctuation(content.trim())
        val codePoints = stripped.asCodePoints()
        return codePoints.size == 1 && isCjk(codePoints.single())
    }

    internal fun normalizedSingleWord(content: String): String? {
        val text = content.trim().replace(whitespace, " ")
        if (text.isEmpty()) return null
        val parts = text.split(' ').map(::stripEdgePunctuation).filter(String::isNotEmpty)
        if (parts.size != 1) return null
        val word = parts.single()
        val codePoints = word.asCodePoints()
        if (codePoints.any(::isCjk) || codePoints.none { Character.isLetter(it) }) return null
        if (codePoints.any { codePoint ->
                !Character.isLetter(codePoint) && codePoint !in wordPunctuation
            }) return null
        return word.lowercase()
    }

    private fun stripEdgePunctuation(value: String): String {
        val codePoints = value.asCodePoints().toMutableList()
        while (codePoints.firstOrNull()?.let(::isEdgePunctuation) == true) codePoints.removeAt(0)
        while (codePoints.lastOrNull()?.let(::isEdgePunctuation) == true) codePoints.removeAt(codePoints.lastIndex)
        return buildString { codePoints.forEach(::appendCodePoint) }
    }

    private fun isEdgePunctuation(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        Character.MATH_SYMBOL.toInt(),
        Character.CURRENCY_SYMBOL.toInt(),
        Character.MODIFIER_SYMBOL.toInt(),
        Character.OTHER_SYMBOL.toInt() -> true
        else -> false
    }

    private fun isCjk(codePoint: Int): Boolean = codePoint in 0x3400..0x4DBF ||
        codePoint in 0x4E00..0x9FFF ||
        codePoint in 0xF900..0xFAFF ||
        codePoint in 0x20000..0x2A6DF ||
        codePoint in 0x2A700..0x2B73F ||
        codePoint in 0x2B740..0x2B81F ||
        codePoint in 0x2B820..0x2CEAF ||
        codePoint in 0x2CEB0..0x2EBEF ||
        codePoint in 0x30000..0x3134F

    private fun String.asCodePoints(): List<Int> = buildList {
        var index = 0
        while (index < length) {
            val codePoint = Character.codePointAt(this@asCodePoints, index)
            add(codePoint)
            index += Character.charCount(codePoint)
        }
    }
}
