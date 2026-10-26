package com.hongxiang.kpilot.library

import com.hongxiang.kpilot.notes.ClippingKind
import com.hongxiang.kpilot.notes.KindleClipping
import org.junit.Assert.*
import org.junit.Test

class LibraryModelsTest {
    @Test fun bookPathsExcludeSidecarsAndTraversal() {
        assertTrue(LibraryRules.isBookPath("/mnt/us/documents/书籍/My Book.AZW3"))
        assertTrue(LibraryRules.isBookPath("/mnt/us/documents/Personal.txt"))
        assertFalse(LibraryRules.isBookPath("/mnt/us/documents/Book.sdr/Book.kfx"))
        assertFalse(LibraryRules.isBookPath("/mnt/us/documents/../system/secret.pdf"))
        assertFalse(LibraryRules.isBookPath("/mnt/us/documents/My Clippings.txt.bak"))
        assertFalse(LibraryRules.isBookPath("/mnt/us/documents/My Clippings.txt"))
        assertFalse(LibraryRules.isBookPath("/mnt/us/system/catalog.pdf"))
    }

    @Test fun catalogPathsDecodeUrlsWithoutChangingLiteralPlus() {
        assertEquals("/mnt/us/documents/C++ Guide.pdf", LibraryRules.normalizeRemotePath("file:///mnt/us/documents/C%2B%2B%20Guide.pdf"))
        assertEquals("/mnt/us/documents/C++ Guide.pdf", LibraryRules.normalizeRemotePath("/mnt/us/documents/C++%20Guide.pdf"))
    }

    @Test fun thumbnailsAreLimitedToKindleCoverFolders() {
        assertEquals("/mnt/us/system/thumbnails/a.jpg", LibraryRules.thumbnailPath("file:///mnt/us/system/thumbnails/a.jpg"))
        assertNull(LibraryRules.thumbnailPath("/mnt/us/documents/a.jpg"))
        assertNull(LibraryRules.thumbnailPath("/mnt/us/system/thumbnails/../../documents/a.jpg"))
        assertNull(LibraryRules.thumbnailPath("/mnt/us/system/thumbnails/a.sh"))
    }

    @Test fun matchingUsesNormalizedCatalogAndFileTitlesWithoutRequiringAuthorDisplayFormat() {
        val book = KindleBook("/mnt/us/documents/旧文件名.azw3", "Ｃafé   Stories", "Doe, Jane")
        val matching = book.matchingClippings(listOf(
            clipping("a", "cafe stories", "Jane Doe", 20),
            clipping("b", "Café Stories", null, 5),
            clipping("c", "旧文件名", null, 10),
            clipping("d", "Other Book", "Jane Doe", 1)
        ))
        assertEquals(listOf("b", "c", "a"), matching.map { it.id })
    }

    @Test fun filenameWithAuthorSuffixFindsClippingsWithoutInventingPartialMatches() {
        val book = KindleBook("/mnt/us/documents/Book (Author).mobi")
        assertEquals(listOf("a"), book.matchingClippings(listOf(
            clipping("a", "Book", "Author", 1), clipping("b", "Book Two", "Author", 2)
        )).map { it.id })
    }

    @Test fun parenthesesInRealTitlesDoNotMatchAnUnrelatedShortTitle() {
        val book = KindleBook("/mnt/us/documents/Book (A Different View).mobi", author = "Author")
        assertEquals(listOf("actual"), book.matchingClippings(listOf(
            clipping("short", "Book", "Author", 1),
            clipping("actual", "Book (A Different View)", "Author", 2)
        )).map { it.id })
    }

    @Test fun indexedLookupHandlesAuthorSuffixesAndKeepsRealParentheticalTitlesDistinct() {
        val index = LibraryClippingsIndex(listOf(
            clipping("plain", "Book", "Author", 10),
            clipping("parenthetical", "Book (A Different View)", "Author", 20),
            clipping("other", "Another Book", "Author", 30)
        ))
        assertEquals(listOf("plain"), index.clippings(KindleBook("/mnt/us/documents/Book (Author).mobi")).map { it.id })
        assertEquals(listOf("parenthetical"), index.clippings(KindleBook("/mnt/us/documents/Book (A Different View).mobi", author = "Author")).map { it.id })
        assertTrue(index.clippings(KindleBook("/mnt/us/documents/Book Two.mobi")).isEmpty())
    }

    private fun clipping(id: String, title: String, author: String?, location: Int) = KindleClipping(
        id, "book-$id", title, author, ClippingKind.HIGHLIGHT, location.toString(), location, location,
        null, null, "metadata", "A passage", "raw"
    )
}
