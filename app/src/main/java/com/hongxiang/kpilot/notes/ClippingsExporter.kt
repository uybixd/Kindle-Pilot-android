package com.hongxiang.kpilot.notes

import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ClippingsExportFormat(val extension: String, val mimeType: String) {
    MARKDOWN("md", "text/markdown"),
    PLAIN_TEXT("txt", "text/plain"),
    CSV("csv", "text/csv")
}

object ClippingsExporter {
    private val exportedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    fun export(items: List<KindleClipping>, format: ClippingsExportFormat): String = when (format) {
        ClippingsExportFormat.MARKDOWN -> markdown(items)
        ClippingsExportFormat.PLAIN_TEXT -> plainText(items)
        ClippingsExportFormat.CSV -> csv(items)
    }

    fun fileName(selectedBook: String?, format: ClippingsExportFormat): String {
        val baseName = selectedBook?.let(::safeFileName)?.let { "$it Clippings" }
        return when {
            baseName != null -> "$baseName.${format.extension}"
            format == ClippingsExportFormat.PLAIN_TEXT -> "My Clippings.txt"
            else -> "Kindle_Notes.${format.extension}"
        }
    }

    private fun markdown(items: List<KindleClipping>): String = buildString {
        items.groupBy(KindleClipping::bookId).values.forEach { records ->
            val first = records.first()
            val readable = records.filter { item ->
                item.kind != ClippingKind.BOOKMARK &&
                    (item.text.isNotBlank() || item.kindleNotes.any { it.text.isNotBlank() })
            }
            if (readable.isEmpty()) return@forEach

            if (isNotEmpty()) append("\n\n")
            append("# ").append(markdownInline(first.bookTitle)).append('\n')
            first.author?.takeIf(String::isNotBlank)?.let { author ->
                append("\n*").append(markdownInline(author)).append("*\n")
            }

            readable.forEachIndexed { index, item ->
                append('\n')
                when (item.kind) {
                    ClippingKind.NOTE -> appendLabeledQuote("笔记", item.text)
                    else -> if (item.text.isNotBlank()) append(normalizeMarkdownText(item.text)).append('\n')
                }
                item.kindleNotes.filter { it.text.isNotBlank() }.forEach { note ->
                    append('\n')
                    appendLabeledQuote("批注", note.text)
                }
                if (index != readable.lastIndex) append("\n---\n")
            }
        }
    }.trimEnd() + "\n"

    /** Restores standard My Clippings.txt records from their original record bodies. */
    private fun plainText(items: List<KindleClipping>): String {
        val records = items.flatMap { item -> listOf(item.raw) + item.kindleNotes.map { it.raw } }
            .map(::normalizeKindleRecord)
        if (records.isEmpty()) return ""
        return records.joinToString("\r\n==========\r\n", postfix = "\r\n==========\r\n")
    }

    private fun csv(items: List<KindleClipping>): String = buildString {
        append("book_title,author,type,page,location,added_at,text,kindle_notes,metadata\n")
        items.forEach { item ->
            val attached = item.kindleNotes.joinToString("\n\n") { note ->
                listOf(note.referenceText, note.text).filter(String::isNotEmpty).joinToString(" · ")
            }
            append(
                listOf(
                    item.bookTitle,
                    item.author.orEmpty(),
                    item.kind.name.lowercase(),
                    item.page.orEmpty(),
                    item.location.orEmpty(),
                    item.addedAt?.let(exportedDate::format).orEmpty(),
                    item.text,
                    attached,
                    item.metadata
                ).joinToString(",", transform = ::csvEscape)
            )
            append('\n')
        }
    }.trimEnd()

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun markdownInline(value: String): String = value
        .replace("\\", "\\\\")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("`", "\\`")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("#", "\\#")

    private fun StringBuilder.appendLabeledQuote(label: String, value: String) {
        val lines = normalizeMarkdownText(value).lines()
        append("> **").append(label).append("：**")
        if (lines.isNotEmpty()) append(' ').append(lines.first())
        append('\n')
        lines.drop(1).forEach { line -> append("> ").append(line).append('\n') }
    }

    private fun normalizeMarkdownText(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()

    private fun safeFileName(value: String): String {
        val sanitized = value
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.')
            .take(100)
            .trimEnd(' ', '.')
        return sanitized.ifBlank { "Kindle" }
    }

    private fun normalizeKindleRecord(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
        .replace("\n", "\r\n")
}
