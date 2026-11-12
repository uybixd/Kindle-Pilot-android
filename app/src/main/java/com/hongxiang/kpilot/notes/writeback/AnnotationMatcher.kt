package com.hongxiang.kpilot.notes.writeback

import com.hongxiang.kpilot.notes.KindleClipping
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

internal data class SidecarCandidate(val path: String, val bytes: ByteArray, val store: KindleReaderDataStore)
internal data class AnnotationMatch(val candidate: SidecarCandidate, val highlight: SidecarAnnotation) {
    val isKfx: Boolean get() = candidate.path.endsWith(".yjr", ignoreCase = true)
}

internal object AnnotationMatcher {
    fun titleSimilarity(path: String, clipping: KindleClipping): Double {
        val file = File(path)
        val titles = listOf(clipping.bookTitle, listOfNotNull(clipping.bookTitle, clipping.author).joinToString(" "))
            .map(::normalize).filter(String::isNotEmpty)
        val filenames = listOf(file.nameWithoutExtension.replace(Regex("[0-9a-fA-F]{32}$"), ""),
            file.parentFile?.nameWithoutExtension.orEmpty()).map(::normalize)
        return titles.maxOfOrNull { title -> filenames.maxOf { similarity(it, title) } } ?: 0.0
    }

    /** Reject equal identities across files rather than guessing which copy owns a clipping. */
    fun match(candidates: List<SidecarCandidate>, clipping: KindleClipping): AnnotationMatch =
        matchOrNull(candidates, clipping) ?: error("没有找到与这条标注匹配的 Kindle 批注文件")

    fun matchOrNull(candidates: List<SidecarCandidate>, clipping: KindleClipping): AnnotationMatch? {
        val ranked = mutableListOf<Pair<Double, AnnotationMatch>>()
        val timestampFallback = mutableListOf<AnnotationMatch>()
        candidates.forEach { candidate ->
            val similarity = titleSimilarity(candidate.path, clipping)
            val kfx = candidate.path.endsWith(".yjr", true)
            // KFX offsets are opaque, so an unrelated file can only be rejected by title.
            // AZW3/MOBI sidecars are matched by location/time even when the file name
            // differs from the My Clippings title (for example, an English metadata title).
            if (kfx && similarity < 0.12) return@forEach
            candidate.store.annotations().filter { it.type == KindleReaderDataStore.HIGHLIGHT }.forEach { annotation ->
                val match = AnnotationMatch(candidate, annotation)
                val timestamp = clipping.addedAt?.toEpochMilli()
                val timeDistance = timestamp?.let {
                    normalizedTimeDistance(annotation.creationTime, it)
                }
                if (!kfx && fullTitleMatches(candidate.path, clipping) && timeDistance != null && timeDistance < 1) {
                    timestampFallback += match
                }
                if (kfx) {
                    if (timeDistance != null && timeDistance <= 2) ranked += timeDistance to match
                } else {
                    val a = annotation.locationStart ?: return@forEach
                    val b = clipping.locationStart ?: clipping.locationEnd ?: return@forEach
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

    private fun timeDistance(creationTime: Long, addedAt: Long): Double =
        abs(creationTime.toDouble() - addedAt.toDouble()) / 1000.0

    fun sameCreationTime(annotation: SidecarAnnotation, clipping: KindleClipping): Boolean =
        clipping.addedAt?.let { normalizedTimeDistance(annotation.creationTime, it.toEpochMilli()) < 1 } == true

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
        val offset = (raw / 900.0).roundToLong() * 900.0
        val residual = abs(raw - offset)
        return if (residual <= 2.0) residual else raw
    }

    fun fullTitleMatches(path: String, clipping: KindleClipping): Boolean {
        val joined = normalize(
            listOfNotNull(clipping.bookTitle, clipping.author).filter(String::isNotBlank).joinToString(" ")
        )
        val exactTitles = setOf(normalize(clipping.bookTitle), joined).filter(String::isNotEmpty).toSet()
        val file = File(path)
        val filenames = listOf(file.nameWithoutExtension, file.parentFile?.nameWithoutExtension.orEmpty())
            .map { normalize(it.replace(Regex("[0-9a-fA-F]{32}$"), "")) }
        // Match the same strong identity as iOS: an exact title, or the full
        // title+author inside a sidecar name that carries extra edition text.
        return filenames.any { name -> name in exactTitles || (joined.isNotEmpty() && name.contains(joined)) }
    }

    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun similarity(left: String, right: String): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left.contains(right) || right.contains(left)) return minOf(left.length, right.length).toDouble() / maxOf(left.length, right.length)
        fun grams(value: String) = if (value.length < 3) setOf(value) else value.windowed(3).toSet()
        val a = grams(left)
        val b = grams(right)
        return a.intersect(b).size.toDouble() / a.union(b).size
    }
}
