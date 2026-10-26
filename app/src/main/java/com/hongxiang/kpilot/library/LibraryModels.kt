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
        val titles = setOf(title, LibraryRules.fileTitle(remotePath))
        val authorSuffix = Regex("^(.*?)\\s*\\(([^()]+)\\)\\s*$")
        return items.filter { clipping ->
            val clippingTitle = LibraryRules.normalizeTitle(clipping.bookTitle)
            val knownAuthors = listOfNotNull(author, clipping.author)
                .map(LibraryRules::normalizeTitle).filter(String::isNotEmpty)
            val titleMatches = titles.any { candidate ->
                LibraryRules.normalizeTitle(candidate) == clippingTitle ||
                    authorSuffix.matchEntire(candidate)?.let { match ->
                        // Parentheses can be part of the actual title. Strip them only
                        // when the suffix is a known author, never as a partial-title guess.
                        LibraryRules.normalizeTitle(match.groupValues[2]) in knownAuthors &&
                            LibraryRules.normalizeTitle(match.groupValues[1]) == clippingTitle
                    } == true
            }
            // cc.db exposes a sorting/collation name, which can differ from the
            // author displayed in My Clippings (for example “Doe, Jane” vs “Jane Doe”).
            // Like iOS, match exact normalized book titles rather than rejecting
            // a valid book because these two author representations differ.
            titleMatches
        }.sortedWith(compareBy<KindleClipping> { it.locationStart ?: Int.MAX_VALUE }
            .thenBy { it.addedAt ?: Instant.MIN })
    }
}

data class LibrarySnapshot(
    val books: List<KindleBook>,
    val syncedAt: Instant,
    val sourceKey: String
)

/** Avoid scanning and normalizing the entire clipping collection once for every book. */
class LibraryClippingsIndex(items: List<KindleClipping>) {
    private val byTitle = items.groupBy { LibraryRules.normalizeTitle(it.bookTitle) }

    fun clippings(book: KindleBook): List<KindleClipping> {
        val titles = listOf(book.title, LibraryRules.fileTitle(book.remotePath))
        val possibleTitles = titles + titles.map { it.replace(Regex("\\s*\\([^()]*\\)\\s*$"), "") }
        val candidates = possibleTitles.map(LibraryRules::normalizeTitle).distinct()
            .flatMap { byTitle[it].orEmpty() }.distinctBy { it.id }
        // The final matcher validates possible author suffixes before returning anything.
        return book.matchingClippings(candidates)
    }
}

object LibraryRules {
    // TXT is also supported by Android's existing wireless book transfer.
    val supportedExtensions = setOf("azw3", "mobi", "epub", "pdf", "azw", "azw4", "kfx", "prc", "txt")

    fun fileTitle(path: String): String = path.substringAfterLast('/').substringBeforeLast('.')

    fun normalizeTitle(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ").trim()

    fun isBookPath(path: String): Boolean = path.startsWith("/mnt/us/documents/") &&
        !path.substringAfterLast('/').equals("My Clippings.txt", ignoreCase = true) &&
        path.split('/').none { it == ".." || it.endsWith(".sdr", ignoreCase = true) } &&
        path.substringAfterLast('.', "").lowercase(Locale.ROOT) in supportedExtensions

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
