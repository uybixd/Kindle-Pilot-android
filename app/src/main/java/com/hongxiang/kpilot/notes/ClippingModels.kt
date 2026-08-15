package com.hongxiang.kpilot.notes

import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

enum class ClippingKind { HIGHLIGHT, NOTE, BOOKMARK, UNKNOWN }

data class KindleNote(
    val id: String,
    val location: String?,
    val locationStart: Int?,
    val locationEnd: Int?,
    val page: String?,
    val addedAt: Instant?,
    val metadata: String,
    val text: String,
    val raw: String
) {
    val referenceText: String
        get() = listOfNotNull(page?.let { "页 $it" }, location?.let { "位置 $it" }).joinToString(" · ")
}

data class KindleClipping(
    val id: String,
    val bookId: String,
    val bookTitle: String,
    val author: String?,
    val kind: ClippingKind,
    val location: String?,
    val locationStart: Int?,
    val locationEnd: Int?,
    val page: String?,
    val addedAt: Instant?,
    val metadata: String,
    val text: String,
    val raw: String,
    val kindleNotes: List<KindleNote> = emptyList()
) {
    val referenceText: String
        get() = listOfNotNull(page?.let { "页 $it" }, location?.let { "位置 $it" }).joinToString(" · ")
}

data class ClippingsSnapshot(
    val items: List<KindleClipping>,
    val syncedAt: Instant,
    val sourceBytes: Long,
    val filteredDuplicateCount: Int = 0,
    val filteredSingleSelectionCount: Int = 0
)

object ClippingsTextDecoder {
    fun decode(bytes: ByteArray): String {
        if (bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }
        if (bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        }
        if (bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        }
        val sample = bytes.take(256)
        val evenZeros = sample.withIndex().count { it.index % 2 == 0 && it.value == 0.toByte() }
        val oddZeros = sample.withIndex().count { it.index % 2 == 1 && it.value == 0.toByte() }
        return when {
            oddZeros > sample.size / 8 && oddZeros > evenZeros * 2 -> bytes.toString(Charsets.UTF_16LE)
            evenZeros > sample.size / 8 && evenZeros > oddZeros * 2 -> bytes.toString(Charsets.UTF_16BE)
            else -> bytes.toString(Charsets.UTF_8)
        }.removePrefix("\uFEFF")
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}

object NotesRetryPolicy {
    fun canRetry(host: String, syncing: Boolean): Boolean = host.isNotBlank() && !syncing
}

object ClippingsParser {
    private val separator = Regex("(?m)^={10,}\\s*$")
    private val trailingSeparator = Regex("={10,}\\s*$")
    private val locationPattern = Regex(
        "(?:位置|location|loc\\.)\\s*#?\\s*([0-9]+)(?:\\s*[-–—]\\s*#?\\s*([0-9]+))?",
        RegexOption.IGNORE_CASE
    )
    private val pagePatterns = listOf(
        Regex("page\\s*[:：]?\\s*([0-9][0-9,\\-–—]*)", RegexOption.IGNORE_CASE),
        Regex("第\\s*([0-9][0-9,\\-–—]*)\\s*页"),
        Regex("页码\\s*[:：]?\\s*([0-9][0-9,\\-–—]*)")
    )
    private val whitespace = Regex("\\s+")
    private val combiningMarks = Regex("\\p{M}+")
    private val englishDateFormats = listOf(
        "EEEE, MMMM d, uuuu h:mm:ss a",
        "EEEE, MMMM d, uuuu h:mm a",
        "EEEE, MMMM d, uuuu",
        "MMMM d, uuuu h:mm:ss a",
        "MMMM d, uuuu h:mm a",
        "MMMM d, uuuu"
    ).map { pattern ->
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.US)
    }
    private val chineseDatePattern = Regex(
        "(\\d{4})年(\\d{1,2})月(\\d{1,2})日(?:[^上下午\\d]*(上午|下午))?[^\\d]*(\\d{1,2})?[:：]?(\\d{2})?[:：]?(\\d{2})?"
    )

    fun parse(text: String): List<KindleClipping> = parseDetailed(text).items

    fun parseDetailed(text: String): ClippingsParseResult {
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(separator).filter { it.isNotBlank() }
        val parsed = blocks.mapNotNull(::parseBlock)

        val exactSeen = linkedSetOf<String>()
        val exactRecords = parsed.filter { exactSeen.add(it.id) }
        val contained = deduplicateContained(exactRecords)
        val filteredDuplicateCount = parsed.size - exactRecords.size + contained.filteredCount
        val merged = mergeKindleNotes(contained.records)
        val items = merged.filterNot { record ->
            record.kind == ClippingKind.HIGHLIGHT &&
                record.kindleNotes.isEmpty() &&
                SingleSelectionClassifier.isSingleSelection(record.text)
        }

        return ClippingsParseResult(
            items = items,
            recordBlocks = blocks.size,
            unrecognizedBlocks = blocks.size - parsed.size,
            filteredDuplicateCount = filteredDuplicateCount,
            filteredSingleSelectionCount = merged.size - items.size,
            isBlank = normalized.isBlank(),
            hasSeparator = separator.containsMatchIn(normalized),
            hasTrailingSeparator = trailingSeparator.containsMatchIn(normalized)
        )
    }

    private fun parseBlock(rawBlock: String): KindleClipping? {
        val raw = rawBlock.trim()
        val lines = raw.lines().dropWhile(String::isBlank).dropLastWhile(String::isBlank)
        if (lines.size < 2) return null
        val (title, author) = parseTitle(lines.first().removePrefix("\uFEFF").trim())
        if (title.isBlank()) return null
        val metadataIndex = lines.indexOfFirst { it.trimStart().startsWith("-") }
        if (metadataIndex < 1) return null
        val metadata = lines[metadataIndex].trim()
        val content = lines.drop(metadataIndex + 1).dropWhile(String::isBlank).dropLastWhile(String::isBlank)
            .joinToString("\n").trim()
        val kind = when {
            metadata.contains("highlight", true) || metadata.contains("标注") || metadata.contains("高亮") || metadata.contains("划线") -> ClippingKind.HIGHLIGHT
            metadata.contains("note", true) || metadata.contains("笔记") || metadata.contains("批注") -> ClippingKind.NOTE
            metadata.contains("bookmark", true) || metadata.contains("书签") -> ClippingKind.BOOKMARK
            else -> ClippingKind.UNKNOWN
        }
        val locationMatch = locationPattern.find(metadata)
        val locationStart = locationMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        val locationEnd = locationMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: locationStart
        val location = locationStart?.let { start -> if (locationEnd == start) "$start" else "$start-$locationEnd" }
        val page = pagePatterns.firstNotNullOfOrNull { it.find(metadata)?.groupValues?.getOrNull(1) }
        val id = sha256(listOf(title, author.orEmpty(), metadata, content).joinToString("\u001F"))
        val bookId = sha256(listOf(title, author.orEmpty()).joinToString("\u001F"))
        return KindleClipping(
            id = id,
            bookId = bookId,
            bookTitle = title,
            author = author,
            kind = kind,
            location = location,
            locationStart = locationStart,
            locationEnd = locationEnd,
            page = page,
            addedAt = parseAddedAt(metadata),
            metadata = metadata,
            text = content,
            raw = raw
        )
    }

    private fun deduplicateContained(records: List<KindleClipping>): DeduplicationResult {
        val candidates = records.mapIndexedNotNull { index, record ->
            if (record.kind !in setOf(ClippingKind.HIGHLIGHT, ClippingKind.NOTE)) return@mapIndexedNotNull null
            val comparable = normalizedComparableText(record.text)
            if (comparable.isEmpty()) return@mapIndexedNotNull null
            DeduplicationCandidate(index, record, comparable, comparable.replace(" ", ""))
        }
        val byKey = candidates.groupBy { it.record.bookId to it.record.kind }
        val removed = mutableSetOf<Int>()
        candidates.forEach { candidate ->
            if (candidate.index in removed) return@forEach
            val peers = byKey[candidate.record.bookId to candidate.record.kind].orEmpty()
            if (peers.any { peer ->
                    peer.index != candidate.index &&
                        compatibleRanges(candidate.record, peer.record) &&
                        shouldRemove(candidate, peer)
                }) {
                removed += candidate.index
            }
        }
        return DeduplicationResult(
            records = records.filterIndexed { index, _ -> index !in removed },
            filteredCount = removed.size
        )
    }

    private fun shouldRemove(candidate: DeduplicationCandidate, peer: DeduplicationCandidate): Boolean {
        if (candidate.comparableText == peer.comparableText) return peer.index < candidate.index
        if (peer.comparableText.length <= candidate.comparableText.length) return false
        return peer.comparableText.contains(candidate.comparableText) || peer.compactedText.contains(candidate.compactedText)
    }

    private fun compatibleRanges(left: KindleClipping, right: KindleClipping): Boolean {
        val leftStart = left.locationStart ?: left.locationEnd ?: return false
        val rightStart = right.locationStart ?: right.locationEnd ?: return false
        val leftEnd = left.locationEnd ?: leftStart
        val rightEnd = right.locationEnd ?: rightStart
        return rangeDistance(leftStart, leftEnd, rightStart, rightEnd) <= 1
    }

    private fun mergeKindleNotes(records: List<KindleClipping>): List<KindleClipping> {
        val highlights = records.filter { it.kind == ClippingKind.HIGHLIGHT }
        val matchedNoteIds = mutableSetOf<String>()
        val notesByHighlight = mutableMapOf<String, MutableList<KindleNote>>()

        records.filter { it.kind == ClippingKind.NOTE }.forEach { note ->
            val target = highlights.asSequence()
                .filter { it.bookId == note.bookId }
                .mapNotNull { highlight -> noteMatchScore(note, highlight)?.let { it to highlight } }
                .minWithOrNull(compareBy<Pair<NoteMatchScore, KindleClipping>> { it.first.containment }
                    .thenBy { it.first.distance }
                    .thenBy { it.first.span })
                ?.second
                ?: return@forEach
            notesByHighlight.getOrPut(target.id) { mutableListOf() } += note.asKindleNote()
            matchedNoteIds += note.id
        }

        return records.mapNotNull { record ->
            if (record.kind == ClippingKind.NOTE && record.id in matchedNoteIds) return@mapNotNull null
            val notes = notesByHighlight[record.id].orEmpty().sortedWith(
                compareBy<KindleNote> { it.locationStart ?: Int.MAX_VALUE }
                    .thenBy { it.addedAt ?: Instant.MAX }
                    .thenBy { it.id }
            )
            record.copy(kindleNotes = notes)
        }
    }

    private fun noteMatchScore(note: KindleClipping, highlight: KindleClipping): NoteMatchScore? {
        val noteStart = note.locationStart ?: return null
        val highlightStart = highlight.locationStart ?: return null
        val noteEnd = note.locationEnd ?: noteStart
        val highlightEnd = highlight.locationEnd ?: highlightStart
        val contains = noteStart in highlightStart..highlightEnd
        val overlaps = noteStart <= highlightEnd && noteEnd >= highlightStart
        if (!contains && !overlaps) return null
        return NoteMatchScore(
            containment = if (contains) 0 else 1,
            distance = rangeDistance(noteStart, noteEnd, highlightStart, highlightEnd),
            span = highlightEnd - highlightStart
        )
    }

    private fun KindleClipping.asKindleNote() = KindleNote(
        id, location, locationStart, locationEnd, page, addedAt, metadata, text, raw
    )

    private fun rangeDistance(startA: Int, endA: Int, startB: Int, endB: Int): Int = when {
        startA <= endB && endA >= startB -> 0
        endA < startB -> startB - endA
        else -> startA - endB
    }

    private fun normalizedComparableText(text: String): String {
        val widthFolded = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val decomposed = Normalizer.normalize(widthFolded, Normalizer.Form.NFD)
        return decomposed.replace(combiningMarks, "").lowercase(Locale.ROOT)
            .replace(whitespace, " ").trim()
    }

    private fun parseTitle(value: String): Pair<String, String?> {
        val match = Regex("^(.*?)\\s*\\(([^()]*)\\)\\s*$").matchEntire(value)
        return if (match != null) {
            match.groupValues[1].trim() to match.groupValues[2].trim().takeIf(String::isNotEmpty)
        } else value to null
    }

    private fun parseAddedAt(metadata: String): Instant? {
        val candidate = listOf("Added on", "添加于").firstNotNullOfOrNull { marker ->
            metadata.indexOf(marker, ignoreCase = true).takeIf { it >= 0 }
                ?.let { metadata.substring(it + marker.length).trim() }
        } ?: metadata.substringAfterLast('|').trim()

        englishDateFormats.forEach { formatter ->
            runCatching { LocalDateTime.parse(candidate, formatter).atZone(ZoneId.systemDefault()).toInstant() }
                .getOrNull()?.let { return it }
            runCatching { LocalDate.parse(candidate, formatter).atStartOfDay(ZoneId.systemDefault()).toInstant() }
                .getOrNull()?.let { return it }
        }

        val chinese = chineseDatePattern.find(candidate) ?: return null
        val year = chinese.groupValues[1].toInt()
        val month = chinese.groupValues[2].toInt()
        val day = chinese.groupValues[3].toInt()
        val meridiem = chinese.groupValues[4]
        var hour = chinese.groupValues[5].toIntOrNull() ?: 0
        val minute = chinese.groupValues[6].toIntOrNull() ?: 0
        val second = chinese.groupValues[7].toIntOrNull() ?: 0
        if (meridiem == "下午" && hour < 12) hour += 12
        if (meridiem == "上午" && hour == 12) hour = 0
        return runCatching {
            LocalDateTime.of(year, month, day, hour, minute, second).atZone(ZoneId.systemDefault()).toInstant()
        }.getOrNull()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private data class DeduplicationCandidate(
        val index: Int,
        val record: KindleClipping,
        val comparableText: String,
        val compactedText: String
    )
    private data class DeduplicationResult(val records: List<KindleClipping>, val filteredCount: Int)
    private data class NoteMatchScore(val containment: Int, val distance: Int, val span: Int)
}

data class ClippingsParseResult(
    val items: List<KindleClipping>,
    val recordBlocks: Int,
    val unrecognizedBlocks: Int,
    val filteredDuplicateCount: Int,
    val filteredSingleSelectionCount: Int,
    val isBlank: Boolean,
    val hasSeparator: Boolean,
    val hasTrailingSeparator: Boolean
)

data class ClippingsFormatIssue(val recordBlocks: Int, val unrecognizedBlocks: Int, val reason: Reason) {
    enum class Reason { UNRECOGNIZED, INCOMPLETE }
}

object ClippingsValidation {
    fun inspect(bytes: ByteArray): Pair<ClippingsParseResult, ClippingsFormatIssue?> {
        val result = ClippingsParser.parseDetailed(ClippingsTextDecoder.decode(bytes))
        if (result.isBlank) return result to null
        if (!result.hasSeparator || !result.hasTrailingSeparator) {
            return result to ClippingsFormatIssue(result.recordBlocks, result.unrecognizedBlocks, ClippingsFormatIssue.Reason.INCOMPLETE)
        }
        if (result.recordBlocks > 0 && result.unrecognizedBlocks == result.recordBlocks) {
            return result to ClippingsFormatIssue(result.recordBlocks, result.unrecognizedBlocks, ClippingsFormatIssue.Reason.UNRECOGNIZED)
        }
        return result to null
    }
}
