package com.hongxiang.kpilot.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LibraryDeletionTest {
    @Test fun deletesOnlyTheBookUnlessReadingDataIsRequested() {
        assertEquals(
            "set -e; rm -f -- '/mnt/us/documents/Book.azw3'; sync",
            LibraryDeletion.command("/mnt/us/documents/Book.azw3", includingReadingData = false)
        )
        assertEquals(
            "set -e; rm -f -- '/mnt/us/documents/Book.azw3'; " +
                "rm -rf -- '/mnt/us/documents/Book.sdr'; sync",
            LibraryDeletion.command("/mnt/us/documents/Book.azw3", includingReadingData = true)
        )
    }

    @Test fun keepsTheSidecarBesideBooksInFoldersAndQuotesApostrophes() {
        assertEquals(
            "set -e; rm -f -- '/mnt/us/documents/Folder/Reader'\\''s Guide.mobi'; " +
                "rm -rf -- '/mnt/us/documents/Folder/Reader'\\''s Guide.sdr'; sync",
            LibraryDeletion.command(
                "/mnt/us/documents/Folder/Reader's Guide.mobi",
                includingReadingData = true
            )
        )
    }

    @Test fun rejectsPathsThatAreNotSafeKindleBooks() {
        for (path in listOf(
            "/mnt/us/documents/../system/Book.azw3",
            "/mnt/us/system/thumbnails/Book.azw3",
            "/mnt/us/documents/Book.sdr/Book.azw3",
            "/mnt/us/documents/My Clippings.txt",
            "/mnt/us/documents/Book.exe"
        )) assertThrows(path, IllegalArgumentException::class.java) {
            LibraryDeletion.command(path, includingReadingData = true)
        }
    }
}
