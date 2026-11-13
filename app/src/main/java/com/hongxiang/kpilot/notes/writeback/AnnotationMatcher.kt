package com.hongxiang.kpilot.notes.writeback

import com.hongxiang.kpilot.notes.KindleClipping
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

internal data class SidecarCandidate(
    val path: String,
    val bytes: ByteArray,
    val store: KindleReaderDataStore,
    private val decodedAnnotations: List<SidecarAnnotation>? = null
) {
    /** Decoding the KRDS tree is expensive; every matcher pass reuses the same list. */
    val annotations: List<SidecarAnnotation> by lazy { decodedAnnotations ?: store.annotations() }
    val highlights: List<SidecarAnnotation> by lazy {
        annotations.filter { it.type == KindleReaderDataStore.HIGHLIGHT }
    }
}

internal data class AnnotationMatch(val candidate: SidecarCandidate, val highlight: SidecarAnnotation) {
    val isKfx: Boolean get() = candidate.path.endsWith(".yjr", ignoreCase = true)
}

/** Normalized book identity reused for every sidecar comparison of one clipping. */
internal class ClippingKey(val clipping: KindleClipping) {
    val titles: List<String>
    val titleGrams: List<Set<String>>
    val joined: String
    val exactTitles: Set<String>

    init {
        val joinedRaw = listOfNotNull(clipping.bookTitle, clipping.author)
            .filter(String::isNotBlank).joinToString(" ")
        titles = listOf(clipping.bookTitle, joinedRaw).map(::normalize).filter(String::isNotEmpty)
        titleGrams = titles.map(::grams)
        joined = normalize(joinedRaw)
        exactTitles = setOf(normalize(clipping.bookTitle), joined).filter(String::isNotEmpty).toSet()
    }
}

/** Normalized sidecar filename identity, computed once per sidecar path. */
internal class SidecarKey(val path: String) {
    val isKfx: Boolean = path.endsWith(".yjr", true)
    private val fileNames: List<String>
    private val fileGrams: List<Set<String>>

    init {
        val file = File(path)
        fileNames = listOf(file.nameWithoutExtension, file.parentFile?.nameWithoutExtension.orEmpty())
            .map { normalize(it.replace(HASH_SUFFIX, "")) }
        fileGrams = fileNames.map(::grams)
    }

    fun titleSimilarity(key: ClippingKey): Double {
        if (fileNames.isEmpty() || key.titles.isEmpty()) return 0.0
        var best = 0.0
        for (index in fileNames.indices) {
            val name = fileNames[index]
            val nameGrams = fileGrams[index]
            for (titleIndex in key.titles.indices) {
                val score = similarity(name, key.titles[titleIndex], nameGrams, key.titleGrams[titleIndex])
                if (score > best) best = score
            }
        }
        return best
    }

    fun fullTitleMatches(key: ClippingKey): Boolean = fileNames.any { name ->
        name in key.exactTitles || (key.joined.isNotEmpty() && name.contains(key.joined))
    }
}

/**
 * Per-sync index over the sidecar files and their decoded annotations. Building it once
 * turns the previous per-clipping scan (decode + normalize every sidecar again) into
 * cheap lookups over cached lists.
 */
internal class MatchIndex(
    private val candidates: List<SidecarCandidate>,
    paths: List<String> = candidates.map { it.path }
) {
    private val pathKeys = paths.map(::SidecarKey)
    private val candidateKeys = candidates.map { it to SidecarKey(it.path) }
    private val candidatesByPath = candidates.associateBy { it.path }

    fun candidate(path: String): SidecarCandidate? = candidatesByPath[path]

    fun fullTitlePath(clipping: KindleClipping): String? {
        val key = ClippingKey(clipping)
        var matched: SidecarKey? = null
        for (pathKey in pathKeys) {
            if (!pathKey.fullTitleMatches(key)) continue
            // Ambiguous matches are treated as no match, matching the previous singleOrNull behavior.
            if (matched != null) return null
            matched = pathKey
        }
        return matched?.path
    }

    fun matchOrNull(clipping: KindleClipping): AnnotationMatch? {
        val key = ClippingKey(clipping)
        val timestamp = clipping.addedAt?.toEpochMilli()
        val ranked = mutableListOf<Pair<Double, AnnotationMatch>>()
        val timestampFallback = mutableListOf<AnnotationMatch>()
        candidateKeys.forEach candidateLoop@ { (candidate, candidateKey) ->
            val similarity = candidateKey.titleSimilarity(key)
            val kfx = candidateKey.isKfx
            // KFX offsets are opaque, so an unrelated file can only be rejected by title.
            // AZW3/MOBI sidecars are matched by location/time even when the file name
            // differs from the My Clippings title (for example, an English metadata title).
            if (kfx && similarity < 0.12) return@candidateLoop
            val fullTitle = candidateKey.fullTitleMatches(key)
            candidate.highlights.forEach annotationLoop@ { annotation ->
                val match = AnnotationMatch(candidate, annotation)
                val timeDistance = timestamp?.let {
                    normalizedTimeDistance(annotation.creationTime, it)
                }
                if (!kfx && fullTitle && timeDistance != null && timeDistance < 1) {
                    timestampFallback += match
                }
                if (kfx) {
                    if (timeDistance != null && timeDistance <= 2) ranked += timeDistance to match
                } else {
                    val a = annotation.locationStart ?: return@annotationLoop
                    val b = clipping.locationStart ?: clipping.locationEnd ?: return@annotationLoop
                    val distance = abs(a.toLong() - b) + abs((annotation.locationEnd ?: a).toLong() - (clipping.locationEnd ?: b))
                    if (distance <= 2 && (timeDistance?.let { it <= 300 } ?: (similarity >= 0.18))) {
                        ranked += (distance * 10_000 + (timeDistance ?: 300.0) + (1 - similarity) * 10) to match
                    }
                }
            }
        }
        val sorted = ranked.sortedBy { it.first }
        if (sorted.isNotEmpty()) {
            require(sorted.size == 1 || abs(sorted[0].first - sorted[1].first) > 0.001) {
                "多条 Kindle 标注具有相同位置和时间，无法安全确定写入目标"
            }
            return sorted[0].second
        }
        require(timestampFallback.size <= 1) { "多个 Kindle 文件具有相同标注时间，无法安全确定写入目标" }
        return timestampFallback.singleOrNull()
    }

    fun exportedNotesAreGone(clipping: KindleClipping): Boolean {
        if (clipping.kindleNotes.isEmpty()) return false
        val path = fullTitlePath(clipping) ?: return false
        if (path.endsWith(".yjr", true)) return false
        val annotations = candidatesByPath[path]?.annotations ?: return false
        if (annotations.isEmpty()) return false
        val liveTexts = annotations.mapNotNull { it.note?.let(::normalizedNoteText) }.toSet()
        return clipping.kindleNotes.none { normalizedNoteText(it.text) in liveTexts }
    }
}

internal object AnnotationMatcher {
    fun titleSimilarity(path: String, clipping: KindleClipping): Double =
        SidecarKey(path).titleSimilarity(ClippingKey(clipping))

    /** Reject equal identities across files rather than guessing which copy owns a clipping. */
    fun match(candidates: List<SidecarCandidate>, clipping: KindleClipping): AnnotationMatch =
        matchOrNull(candidates, clipping) ?: error("没有找到与这条标注匹配的 Kindle 批注文件")

    fun matchOrNull(candidates: List<SidecarCandidate>, clipping: KindleClipping): AnnotationMatch? =
        MatchIndex(candidates).matchOrNull(clipping)

    fun sameCreationTime(annotation: SidecarAnnotation, clipping: KindleClipping): Boolean =
        clipping.addedAt?.let { normalizedTimeDistance(annotation.creationTime, it.toEpochMilli()) < 1 } == true

    fun fullTitleMatches(path: String, clipping: KindleClipping): Boolean =
        SidecarKey(path).fullTitleMatches(ClippingKey(clipping))
}

private val HASH_SUFFIX = Regex("[0-9a-fA-F]{32}$")

/** The widest local-time difference between two real time zones (UTC-12 to UTC+14). */
private const val MAX_TIMEZONE_OFFSET_SECONDS = 26 * 60 * 60.0
private const val TIMEZONE_TOLERANCE_SECONDS = 2.0

private fun timeDistance(creationTime: Long, addedAt: Long): Double =
    abs(creationTime.toDouble() - addedAt.toDouble()) / 1000.0

/**
 * My Clippings stores Kindle-local wall time without a zone, while the sidecar
 * stores an absolute epoch. When the Kindle and phone use different time zones,
 * subtract the nearest quarter-hour offset before applying the normal tolerance.
 * Only a near-exact residual is trusted; otherwise the raw difference stands and
 * the ordinary time limit rejects it.
 */
private fun normalizedTimeDistance(creationTime: Long, addedAt: Long): Double {
    val raw = timeDistance(creationTime, addedAt)
    if (raw < 1_800) return raw
    // Only a difference that could actually be a phone/Kindle time-zone offset
    // may be normalized. Without this bound an annotation from another day can
    // land within the two-second residual of a quarter-hour boundary and look
    // like an exact timestamp match (for example, 11 days 21:30:00.847 later).
    if (raw > MAX_TIMEZONE_OFFSET_SECONDS + TIMEZONE_TOLERANCE_SECONDS) return raw
    val offset = (raw / 900.0).roundToLong() * 900.0
    val residual = abs(raw - offset)
    return if (residual <= TIMEZONE_TOLERANCE_SECONDS) residual else raw
}

private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

private fun grams(value: String): Set<String> = if (value.length < 3) setOf(value) else value.windowed(3).toSet()

private fun similarity(left: String, right: String, leftGrams: Set<String>, rightGrams: Set<String>): Double {
    if (left.isEmpty() || right.isEmpty()) return 0.0
    if (left.contains(right) || right.contains(left)) {
        return minOf(left.length, right.length).toDouble() / maxOf(left.length, right.length)
    }
    return leftGrams.intersect(rightGrams).size.toDouble() / leftGrams.union(rightGrams).size
}
