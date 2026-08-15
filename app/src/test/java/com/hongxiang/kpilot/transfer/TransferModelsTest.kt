package com.hongxiang.kpilot.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferModelsTest {
    @Test
    fun supportsTransferFormatsCaseInsensitively() {
        listOf("book.epub", "paper.PDF", "old.mobi", "kindle.AZW3", "note.txt").forEach {
            assertTrue(it, SupportedTransferTypes.isSupported(it))
        }
        listOf("note.md", "archive.zip", "no_extension").forEach {
            assertFalse(it, SupportedTransferTypes.isSupported(it))
        }
    }

    @Test
    fun safeNamingUsesLeafAndQuotesShellMetacharacters() {
        assertEquals("reader's guide.epub", RemoteDocumentNaming.safeFileName("folder/reader's guide.epub"))
        assertEquals("'reader'\\''s guide.epub'", RemoteDocumentNaming.shellQuote("reader's guide.epub"))
    }

    @Test
    fun parsesOnlySupportedDocumentPaths() {
        val output = """/mnt/us/documents/One.epub
/mnt/us/documents/folder/Two.PDF
/mnt/us/documents/note.txt
/tmp/Three.mobi
/mnt/us/documents/One.epub"""
        assertEquals(
            listOf("/mnt/us/documents/One.epub", "/mnt/us/documents/folder/Two.PDF", "/mnt/us/documents/note.txt"),
            RemoteDocumentNaming.parseRemotePaths(output)
        )
    }

    @Test
    fun collisionMatchesFileNameOrNormalizedTitle() {
        val remote = listOf(
            "/mnt/us/documents/My   Book.pdf",
            "/mnt/us/documents/Other.epub"
        )
        assertEquals(listOf(remote[0]), RemoteDocumentNaming.collisions("my book.EPUB", remote))
        assertEquals(listOf(remote[1]), RemoteDocumentNaming.collisions("Other.epub", remote))
    }

    @Test
    fun queueOnlyRetriesTerminalNonSuccessStatesAndNeverRemovesUploading() {
        assertTrue(TransferQueueRules.canRetry(TransferStatus.FAILED))
        assertTrue(TransferQueueRules.canRetry(TransferStatus.SKIPPED))
        assertFalse(TransferQueueRules.canRetry(TransferStatus.COMPLETED))
        assertFalse(TransferQueueRules.canRemove(TransferStatus.CHECKING))
        assertFalse(TransferQueueRules.canRemove(TransferStatus.WAITING_FOR_COLLISION))
        assertFalse(TransferQueueRules.canRemove(TransferStatus.UPLOADING))
        assertTrue(TransferQueueRules.canRemove(TransferStatus.READY))
        assertTrue(TransferQueueRules.canRemove(TransferStatus.COMPLETED))
    }
}
