package com.hongxiang.kpilot.library

import com.hongxiang.kpilot.notes.KindleClipping
import java.net.URI
import java.net.URLDecoder
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

data class KindleBook(
    val remotePath: String,
    val title: String = LibraryRules.fileTitle(remotePath),
    val author: String? = null,
    val coverPath: String? = null,
    val percentFinished: Double? = null,
    val contentSize: Long? = null
) {
    val fileName: String get() = remotePath.substringAfterLast('/')
    val format: String get() = fileName.substringAfterLast('.', "").uppercase(Locale.ROOT)

    fun matchingClippings(items: List<KindleClipping>): List<KindleClipping> {
        val matcher = BookTitleMatcher(this)
        return items.filter { clipping ->
            matcher.matches(LibraryRules.normalizeTitle(clipping.bookTitle),
                clipping.author?.let(LibraryRules::normalizeTitle))
        }.sortedWith(clippingOrder)
    }
}

data class LibrarySnapshot(
    val books: List<KindleBook>,
    val syncedAt: Instant,
    val sourceKey: String
)

data class CoverRepairResult(val message: String, val coverPath: String?)

/** Avoid scanning and normalizing the entire clipping collection once for every book. */
class LibraryClippingsIndex(items: List<KindleClipping>) {
    private data class Entry(val clipping: KindleClipping, val author: String?)
    private val byTitle = run {
        // A book title and author occur in many records. Normalize each distinct
        // string once, then reuse those values for every book lookup.
        val normalized = HashMap<String, String>()
        fun normalize(value: String) = normalized.getOrPut(value) { LibraryRules.normalizeTitle(value) }
        items.groupBy { normalize(it.bookTitle) }.mapValues { (_, clippings) ->
            clippings.map { Entry(it, it.author?.let(::normalize)) }
        }
    }

    fun clippings(book: KindleBook): List<KindleClipping> {
        val matcher = BookTitleMatcher(book)
        return matcher.possibleTitles.flatMap { title ->
            byTitle[title].orEmpty().filter { matcher.matches(title, it.author) }.map { it.clipping }
        }.distinctBy { it.id }.sortedWith(clippingOrder)
    }
}

private val clippingOrder = compareBy<KindleClipping> { it.locationStart ?: Int.MAX_VALUE }
    .thenBy { it.addedAt ?: Instant.MIN }
private val authorSuffix = Regex("^(.*?)\\s*\\(([^()]+)\\)\\s*$")

/** Only strip a parenthetical suffix when it is a known author, never a partial-title guess. */
private class BookTitleMatcher(book: KindleBook) {
    private val titles = setOf(book.title, LibraryRules.fileTitle(book.remotePath))
    private val fullTitles = titles.map(LibraryRules::normalizeTitle).toSet()
    private val author = book.author?.let(LibraryRules::normalizeTitle)
    private val suffixes = titles.mapNotNull { candidate ->
        authorSuffix.matchEntire(candidate)?.let { match ->
            LibraryRules.normalizeTitle(match.groupValues[1]) to LibraryRules.normalizeTitle(match.groupValues[2])
        }
    }
    val possibleTitles = (fullTitles + suffixes.map { it.first }).toSet()

    fun matches(title: String, clippingAuthor: String?): Boolean = title in fullTitles || suffixes.any { (base, suffix) ->
        title == base && suffix.isNotEmpty() && (suffix == author || suffix == clippingAuthor)
    }
}

object LibraryRules {
    const val MAX_COVER_BOOK_BYTES = 64L * 1024 * 1024

    // TXT is also supported by Android's existing wireless book transfer.
    val supportedExtensions = setOf("azw3", "mobi", "epub", "pdf", "azw", "azw4", "kfx", "prc", "txt")
    // Match iOS: only these formats carry the EXTH embedded cover Kindle can reuse.
    val coverRepairExtensions = setOf("azw3", "azw", "mobi", "prc")
    private val combiningMarks = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")

    // Match iOS: tiny catalog images are not useful book covers when enlarged on the shelf.
    fun isUsableCoverSize(width: Int, height: Int): Boolean = width >= 100 && height >= 100

    fun fileTitle(path: String): String = path.substringAfterLast('/').substringBeforeLast('.')

    fun normalizeTitle(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKD)
        .replace(combiningMarks, "").lowercase(Locale.ROOT)
        .replace(whitespace, " ").trim()

    fun isBookPath(path: String): Boolean = path.startsWith("/mnt/us/documents/") &&
        !path.substringAfterLast('/').equals("My Clippings.txt", ignoreCase = true) &&
        path.split('/').none { it.startsWith('.') || it.endsWith(".sdr", ignoreCase = true) } &&
        path.substringAfterLast('.', "").lowercase(Locale.ROOT) in supportedExtensions

    fun isCoverRepairPath(path: String): Boolean = isBookPath(path) &&
        path.substringAfterLast('.', "").lowercase(Locale.ROOT) in coverRepairExtensions

    fun normalizeRemotePath(value: String): String = try {
        if (value.startsWith("file://")) URI(value).path
        else URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) { value }

    fun thumbnailPath(value: String?): String? {
        val path = value?.let(::normalizeRemotePath) ?: return null
        val allowed = path.startsWith("/mnt/us/system/thumbnails/") ||
            path.startsWith("/mnt/us/amazon-cover-bug/")
        return path.takeIf {
            allowed && it.split('/').none { component -> component == ".." } &&
                it.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png")
        }
    }
}
