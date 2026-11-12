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

    @Test fun hiddenBackupsAndInternalDirectoriesAreNeverBooks() {
        assertFalse(LibraryRules.isBookPath("/mnt/us/documents/.My Clippings.kpilot-backup-1780226200-1234abcd.txt"))
        assertFalse(LibraryRules.isBookPath("/mnt/us/documents/.My Clippings.kpilot-backup-01234567-89AB-CDEF-0123-456789ABCDEF.txt"))
        assertFalse(LibraryRules.isBookPath("/mnt/us/documents/.kpilot-note-backup-1780226200.bak"))
        assertFalse(LibraryRules.isBookPath("/mnt/us/documents/.backups/Book.azw3"))
        assertFalse(LibraryRules.isBookPath("/mnt/us/documents/Folder/.Book.pdf"))
        assertTrue(LibraryRules.isBookPath("/mnt/us/documents/Folder/Book.kpilot-backup-guide.pdf"))
    }

    @Test fun thumbnailsAreLimitedToKindleCoverFolders() {
        assertEquals("/mnt/us/system/thumbnails/a.jpg", LibraryRules.thumbnailPath("file:///mnt/us/system/thumbnails/a.jpg"))
        assertNull(LibraryRules.thumbnailPath("/mnt/us/documents/a.jpg"))
        assertNull(LibraryRules.thumbnailPath("/mnt/us/system/thumbnails/../../documents/a.jpg"))
        assertNull(LibraryRules.thumbnailPath("/mnt/us/system/thumbnails/a.sh"))
    }

    @Test fun coverDimensionsMatchIosMinimumSize() {
        listOf(32 to 48, 64 to 96, 96 to 128, 99 to 100, 100 to 99, 0 to 100, 100 to 0, -1 to 180, 120 to -1)
            .forEach { (width, height) ->
                assertFalse("$width × $height", LibraryRules.isUsableCoverSize(width, height))
            }
        listOf(100 to 100, 107 to 160, 120 to 180, 320 to 500).forEach { (width, height) ->
            assertTrue("$width × $height", LibraryRules.isUsableCoverSize(width, height))
        }
    }

    @Test fun coverRepairOnlyAcceptsMobiFamilyBooksOnKindle() {
        assertTrue(LibraryRules.isCoverRepairPath("/mnt/us/documents/Book.azw3"))
        assertTrue(LibraryRules.isCoverRepairPath("/mnt/us/documents/Folder/Book.AZW"))
        assertTrue(LibraryRules.isCoverRepairPath("/mnt/us/documents/Book.mobi"))
        assertTrue(LibraryRules.isCoverRepairPath("/mnt/us/documents/Book.prc"))
        for (path in listOf(
            "/mnt/us/documents/Book.epub",
            "/mnt/us/documents/Book.pdf",
            "/mnt/us/documents/Book.kfx",
            "/mnt/us/documents/.hidden/Book.mobi",
            "/mnt/us/system/thumbnails/Book.mobi",
            "/mnt/us/documents/Book.sdr/Book.mobi"
        )) assertFalse(path, LibraryRules.isCoverRepairPath(path))
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

    @Test fun normalizedIndexMergesTitleVariantsDeduplicatesAndSortsLocations() {
        val first = clipping("first", "Café Stories", "Jane Doe", 5)
        val index = LibraryClippingsIndex(listOf(
            clipping("later", "Ｃafé   Stories", "Jane Doe", 20),
            first,
            first.copy(bookTitle = "cafe stories"),
            clipping("unrelated", "Café Stories Two", "Jane Doe", 1)
        ))
        val book = KindleBook("/mnt/us/documents/Cafe Stories (Jane Doe).azw3", author = "Doe, Jane")
        assertEquals(listOf("first", "later"), index.clippings(book).map { it.id })
    }

    private fun clipping(id: String, title: String, author: String?, location: Int) = KindleClipping(
        id, "book-$id", title, author, ClippingKind.HIGHLIGHT, location.toString(), location, location,
        null, null, "metadata", "A passage", "raw"
    )
}
