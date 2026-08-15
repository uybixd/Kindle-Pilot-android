package com.hongxiang.kpilot.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ClippingsTest {
    private val fixture = """Sample Book (Example Author)
- Your Highlight on page 12 | Added on Saturday, January 1, 2022

First fixture highlight.
==========
Sample Book (Example Author)
- Your Note on page 13 | Added on Saturday, January 1, 2022

Fixture note, with "quotes".
==========
第二本书
- 您在位置 20 的标注 | 添加于 2022年1月1日

仅用于测试。
==========
Sample Book (Example Author)
- Your Highlight on page 12 | Added on Saturday, January 1, 2022

First fixture highlight.
==========
"""

    @Test
    fun decodesUtf8AndPracticalUtf16Forms() {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + fixture.toByteArray()
        val leBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + fixture.toByteArray(Charsets.UTF_16LE)
        val beBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + fixture.toByteArray(Charsets.UTF_16BE)
        assertEquals(fixture, ClippingsTextDecoder.decode(utf8Bom))
        assertEquals(fixture, ClippingsTextDecoder.decode(leBom))
        assertEquals(fixture, ClippingsTextDecoder.decode(beBom))
        assertEquals(fixture, ClippingsTextDecoder.decode(fixture.toByteArray(Charsets.UTF_16LE)))
    }

    @Test
    fun parsesKindsAuthorsUnicodeAndDeduplicates() {
        val result = ClippingsParser.parseDetailed(fixture)
        val items = result.items
        assertEquals(3, items.size)
        assertEquals(1, result.filteredDuplicateCount)
        assertEquals(0, result.unrecognizedBlocks)
        assertEquals(listOf(ClippingKind.HIGHLIGHT, ClippingKind.NOTE, ClippingKind.HIGHLIGHT), items.map { it.kind })
        assertEquals("Example Author", items.first().author)
        assertEquals("第二本书", items.last().bookTitle)
        assertEquals(2, items.groupBy { it.bookTitle }.size)
    }

    @Test
    fun removesContainedHighlightsAndAttachesNotesToTheBestRange() {
        val text = """Range Book (Author)
- Your Highlight at location 100-105 | Added on January 1, 2025 1:00 PM

中间一句。
==========
Range Book (Author)
- Your Highlight at location 100-105 | Added on January 1, 2025 1:01 PM

开头，中间一句。结尾。
==========
Range Book (Author)
- Your Note at location 102 | Added on January 1, 2025 1:02 PM

这是对应这段划线的批注。
==========
"""
        val result = ClippingsParser.parseDetailed(text)

        assertEquals(1, result.filteredDuplicateCount)
        assertEquals(1, result.items.size)
        val highlight = result.items.single()
        assertEquals("开头，中间一句。结尾。", highlight.text)
        assertEquals(1, highlight.kindleNotes.size)
        assertEquals("这是对应这段划线的批注。", highlight.kindleNotes.single().text)
        assertEquals("位置 100-105", highlight.referenceText)
    }

    @Test
    fun leavesUnmatchedKindleNotesAsTopLevelRecords() {
        val text = """Range Book
- Your Highlight at location 10-12

A useful highlight.
==========
Range Book
- Your Note at location 20

Unmatched note
==========
"""
        val items = ClippingsParser.parse(text)
        assertEquals(2, items.size)
        assertEquals(ClippingKind.NOTE, items.last().kind)
    }

    @Test
    fun recognizesSingleWordsAndSingleCjkCharacters() {
        assertTrue(SingleSelectionClassifier.isSingleSelection("atrocious"))
        assertTrue(SingleSelectionClassifier.isSingleSelection("“decimate”"))
        assertTrue(SingleSelectionClassifier.isSingleSelection("mother-in-law"))
        assertTrue(SingleSelectionClassifier.isSingleSelection("佛"))
        assertFalse(SingleSelectionClassifier.isSingleSelection("two words"))
        assertFalse(SingleSelectionClassifier.isSingleSelection("佛珠"))
    }

    @Test
    fun filtersBareSingleSelectionsButKeepsAnnotatedWordsAndStandaloneNotes() {
        val text = """Word Book
- Your Highlight at location 1

atrocious
==========
Word Book
- Your Highlight at location 2

佛
==========
Word Book
- Your Highlight at location 3

retained
==========
Word Book
- Your Note at location 3

This word has a useful note.
==========
Word Book
- Your Note at location 9

remember
==========
"""
        val result = ClippingsParser.parseDetailed(text)

        assertEquals(2, result.filteredSingleSelectionCount)
        assertEquals(2, result.items.size)
        assertEquals("retained", result.items.first().text)
        assertEquals(1, result.items.first().kindleNotes.size)
        assertEquals(ClippingKind.NOTE, result.items.last().kind)
    }

    @Test
    fun acceptsAValidFileWhenEveryHighlightIsFiltered() {
        val bytes = """Word Book
- Your Highlight at location 1

word
==========
""".toByteArray()
        val (result, issue) = ClippingsValidation.inspect(bytes)
        assertEquals(null, issue)
        assertEquals(1, result.filteredSingleSelectionCount)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun acceptsValidEmptyClippingsAndCommonKindleFormatting() {
        val emptyForms = listOf(byteArrayOf(), "\uFEFF\r\n".toByteArray())
        emptyForms.forEach { bytes ->
            val (result, issue) = ClippingsValidation.inspect(bytes)
            assertTrue(result.isBlank)
            assertEquals(null, issue)
        }

        val chinese = """中文书名
- 您在位置 8 的高亮 | 添加于 2025年1月1日

合成测试内容。
============
""".trimIndent().replace("\n", "\r\n")
        val (result, issue) = ClippingsValidation.inspect(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + chinese.toByteArray()
        )
        assertEquals(null, issue)
        assertEquals(1, result.items.size)
        assertEquals(ClippingKind.HIGHLIGHT, result.items.single().kind)
    }

    @Test
    fun rejectsIncompleteOrUnrecognizedNonEmptyDownloadsWithCountsOnly() {
        val incomplete = ClippingsValidation.inspect(
            "Book\n- Your Highlight on page 1\n\ntruncated".toByteArray()
        ).second
        assertEquals(ClippingsFormatIssue.Reason.INCOMPLETE, incomplete?.reason)

        val unknown = ClippingsValidation.inspect(
            "not a clipping record\n==========\n".toByteArray()
        ).second
        assertEquals(ClippingsFormatIssue.Reason.UNRECOGNIZED, unknown?.reason)
        val diagnostic = NotesDiagnostics.format(unknown!!)
        assertEquals("N-FORMAT-UNRECOGNIZED", diagnostic.code)
        assertFalse(diagnostic.message.contains("not a clipping"))
    }

    @Test
    fun exportsVisibleFixtureRecordsAsMarkdownTextAndCsv() {
        val items = ClippingsParser.parse(fixture).take(2)
        val markdown = ClippingsExporter.export(items, ClippingsExportFormat.MARKDOWN)
        val text = ClippingsExporter.export(items, ClippingsExportFormat.PLAIN_TEXT)
        val csv = ClippingsExporter.export(items, ClippingsExportFormat.CSV)
        assertTrue(markdown.startsWith("# Sample Book\n\n*Example Author*"))
        assertTrue(markdown.contains("First fixture highlight."))
        assertTrue(markdown.contains("> **笔记：** Fixture note, with \"quotes\"."))
        assertFalse(markdown.contains("添加时间"))
        assertFalse(markdown.contains("Your Highlight"))
        assertFalse(markdown.contains("###"))
        assertTrue(text.startsWith("Sample Book (Example Author)\r\n- Your Highlight"))
        assertTrue(text.endsWith("\r\n==========\r\n"))
        assertFalse(text.contains("标注 ·"))
        assertTrue(csv.startsWith("book_title,author,type,page,location,added_at,text,kindle_notes,metadata"))
        assertTrue(csv.contains("\"Fixture note, with \"\"quotes\"\".\""))
    }

    @Test
    fun markdownKeepsReadingContentMinimalAndOmitsBookmarks() {
        val items = ClippingsParser.parse(
            """Book (Author)
- Your Highlight at location 10-12

Highlighted text.
==========
Book (Author)
- Your Note at location 11

My attached thought.
==========
Book (Author)
- Your Bookmark at location 20

==========
"""
        )

        val markdown = ClippingsExporter.export(items, ClippingsExportFormat.MARKDOWN)

        assertEquals(
            """# Book

*Author*

Highlighted text.

> **批注：** My attached thought.
""",
            markdown
        )
        assertFalse(markdown.contains("Bookmark"))
        assertFalse(markdown.contains("location"))
    }

    @Test
    fun selectedBookUsesSafeBookSpecificExportNames() {
        assertEquals(
            "Book_Name_ Clippings.md",
            ClippingsExporter.fileName("Book/Name?", ClippingsExportFormat.MARKDOWN)
        )
        assertEquals(
            "Book_Name_ Clippings.txt",
            ClippingsExporter.fileName("Book/Name?", ClippingsExportFormat.PLAIN_TEXT)
        )
        assertEquals("My Clippings.txt", ClippingsExporter.fileName(null, ClippingsExportFormat.PLAIN_TEXT))
        assertEquals("Kindle_Notes.csv", ClippingsExporter.fileName(null, ClippingsExportFormat.CSV))
    }

    @Test
    fun plainTextRestoresAttachedNotesAsOriginalKindleRecords() {
        val items = ClippingsParser.parse(
            """Book (Author)
- Your Highlight at location 10-12

Highlighted text.
==========
Book (Author)
- Your Note at location 11

Attached note.
==========
"""
        )

        val text = ClippingsExporter.export(items, ClippingsExportFormat.PLAIN_TEXT)

        assertEquals(2, "==========".toRegex().findAll(text).count())
        assertTrue(text.contains("- Your Highlight at location 10-12\r\n\r\nHighlighted text."))
        assertTrue(text.contains("- Your Note at location 11\r\n\r\nAttached note."))
    }

    @Test
    fun failedAtomicWritePreservesPriorGoodCache() {
        val directory = Files.createTempDirectory("kpilot-cache-test").toFile()
        try {
            val cache = File(directory, "My Clippings.txt").apply { writeText("prior-good-cache") }
            runCatching {
                AtomicCache.write(cache) { output ->
                    output.write("partial-new-cache".toByteArray())
                    error("synthetic transfer failure")
                }
            }
            assertEquals("prior-good-cache", cache.readText())
            assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith(".My Clippings.txt.download-") })

            runCatching {
                AtomicCache.writeValidated(
                    cache,
                    { it.write("complete-but-invalid".toByteArray()) },
                    { error("synthetic schema validation failure") }
                )
            }
            assertEquals("prior-good-cache", cache.readText())

            AtomicCache.write(cache) { it.write("complete-new-cache".toByteArray()) }
            assertEquals("complete-new-cache", cache.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun abandonedDownloadsAreRemovedWithoutTouchingTheCacheOrOtherFiles() {
        val directory = Files.createTempDirectory("kpilot-abandoned-download-test").toFile()
        try {
            val cache = File(directory, "My Clippings.txt").apply { writeText("good-cache") }
            File(directory, ".My Clippings.txt.download-old.tmp").writeText("partial")
            File(directory, ".My Clippings.txt.download-old.keep").writeText("unrelated")
            File(directory, ".vocab.db.download-old.tmp").writeText("other-cache")

            assertEquals(1, AtomicCache.deleteAbandonedDownloads(cache))
            assertEquals("good-cache", cache.readText())
            assertTrue(File(directory, ".My Clippings.txt.download-old.keep").isFile)
            assertTrue(File(directory, ".vocab.db.download-old.tmp").isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cacheFailuresIdentifyStageAndAlwaysPreservePriorCache() {
        val directory = Files.createTempDirectory("kpilot-cache-stages").toFile()
        try {
            val cache = File(directory, "My Clippings.txt").apply { writeText("prior-good-cache") }
            val writeFailure = runCatching {
                AtomicCache.write(cache) { throw IOException("synthetic private detail") }
            }.exceptionOrNull() as AtomicCacheException
            assertEquals(AtomicCacheStage.WRITE, writeFailure.stage)
            assertEquals("prior-good-cache", cache.readText())

            val syncFailure = runCatching {
                AtomicCache.writeValidated(cache, { it.write("new".toByteArray()) }, { },
                    syncFile = { throw IOException("synthetic sync detail") })
            }.exceptionOrNull() as AtomicCacheException
            assertEquals(AtomicCacheStage.FSYNC, syncFailure.stage)
            assertEquals("prior-good-cache", cache.readText())

            val commitFailure = runCatching {
                AtomicCache.writeValidated(cache, { it.write("new".toByteArray()) }, { },
                    commit = { _, _ -> throw IOException("synthetic rename detail") })
            }.exceptionOrNull() as AtomicCacheException
            assertEquals(AtomicCacheStage.COMMIT, commitFailure.stage)
            assertEquals("prior-good-cache", cache.readText())
            assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith(".My Clippings.txt.download-") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cacheDiagnosticsAreStageSpecificAndSanitized() {
        val expected = mapOf(
            AtomicCacheStage.DIRECTORY to "N-CACHE-DIR",
            AtomicCacheStage.TEMP_CREATE to "N-CACHE-TEMP",
            AtomicCacheStage.WRITE to "N-CACHE-WRITE",
            AtomicCacheStage.FSYNC to "N-CACHE-FSYNC",
            AtomicCacheStage.VALIDATE to "N-CACHE-VALIDATE",
            AtomicCacheStage.COMMIT to "N-CACHE-COMMIT"
        )
        expected.forEach { (stage, code) ->
            val diagnostic = NotesDiagnostics.cache(stage)
            assertEquals(code, diagnostic.code)
            assertTrue(diagnostic.message.contains("previous cache was kept", ignoreCase = true))
            assertFalse(diagnostic.message.contains("/"))
        }
    }

    @Test
    fun classifiesDownloadFailuresWithoutLeakingShellCommands() {
        val missing = NotesDiagnostics.remoteFailure(1, "cat: can't open '/private/path': No such file or directory")
        val denied = NotesDiagnostics.remoteFailure(1, "Permission denied: /private/path")
        val generic = NotesDiagnostics.remoteFailure(1, "unexpected busybox diagnostic /private/path")
        assertEquals("N-REMOTE-404", missing.code)
        assertEquals("N-REMOTE-403", denied.code)
        assertEquals("N-REMOTE-EXIT", generic.code)
        assertTrue(missing.message.contains("was not found"))
        assertTrue(denied.message.contains("denied access"))
        assertFalse(missing.message.contains("/private/path"))
        assertFalse(denied.message.contains("/private/path"))
        assertFalse(generic.message.contains("busybox"))
    }

    @Test
    fun retryRequiresConnectionAndStopsWhileSyncIsRunning() {
        assertFalse(NotesRetryPolicy.canRetry("", syncing = false))
        assertFalse(NotesRetryPolicy.canRetry("kindle", syncing = true))
        assertTrue(NotesRetryPolicy.canRetry("kindle", syncing = false))
    }
}
