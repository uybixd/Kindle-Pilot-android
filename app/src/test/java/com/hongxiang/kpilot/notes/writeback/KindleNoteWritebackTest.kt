package com.hongxiang.kpilot.notes.writeback

import com.hongxiang.kpilot.notes.ClippingsParser
import com.hongxiang.kpilot.notes.ClippingsTextDecoder
import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.notes.NoteDraft
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class KindleNoteWritebackTest {
    private val time = 1_777_777_770_000L
    private val path = "/mnt/us/documents/Example.sdr/Example.azw3r"
    private val source get() = source()
    private val clipping get() = ClippingsParser.parse(source).single()

    @Test fun codecRoundTripsAllSupportedTagsWithoutChangingOtherObjects() {
        val store = fixture()
        store.values += KrdsValue.ObjectValue("unrelated.object", mutableListOf(
            KrdsValue.Utf("", true), KrdsValue.Utf("中文 \u0000 😀"),
            KrdsValue.Scalar(0, byteArrayOf(1)), KrdsValue.Scalar(4, byteArrayOf(0x7F, 0xF0.toByte(), 0, 0, 0, 0, 0, 1)),
            KrdsValue.Scalar(5, byteArrayOf(0xFF.toByte(), 0xFE.toByte())),
            KrdsValue.Scalar(6, byteArrayOf(0x7F, 0xC0.toByte(), 0, 1)),
            KrdsValue.Scalar(7, byteArrayOf(-8)), KrdsValue.Scalar(9, byteArrayOf(0xFE.toByte()))
        ))
        val bytes = store.encoded()
        assertArrayEquals(bytes, KindleReaderDataStore.decode(bytes).encoded())
    }

    @Test fun malformedSidecarsAreRejected() {
        val valid = fixture().encoded()
        expectFailure { KindleReaderDataStore.decode(valid.copyOf(valid.size - 1)) }
        expectFailure { KindleReaderDataStore.decode(valid + byteArrayOf(0)) }
        expectFailure { KindleReaderDataStore.decode(valid.copyOf().also { it[0] = 1 }) }
    }

    @Test fun insertEditAndClearPreserveHighlightAndCreationTime() {
        val store = fixture()
        val highlight = store.annotations().single()
        val originalHighlight = highlight.copy()
        val created = store.updateNote(highlight, emptySet(), "first", time + 1000, false)!!
        assertEquals(highlight.startPosition, created.startPosition)
        val edited = store.updateNote(highlight, setOf("stale draft text"), "second", time + 2000, false)!!
        assertEquals(created.creationTime, edited.creationTime)
        assertEquals(2, store.annotations().size)
        assertEquals(originalHighlight, store.annotations().first())
        store.updateNote(highlight, setOf("second"), "", time + 3000, false)
        assertEquals(listOf(originalHighlight), store.annotations())
    }

    @Test fun kfxNotesUseHighlightEndPosition() {
        val store = fixture("opaque:start", "opaque:end")
        val highlight = store.annotations().single()
        val note = store.updateNote(highlight, emptySet(), "KFX note", time, true)!!
        assertEquals("opaque:end", note.startPosition)
        assertEquals("opaque:end", note.endPosition)
        val candidates = listOf(SidecarCandidate(path.replace("azw3r", "yjr"), store.encoded(), store))
        assertEquals(highlight, AnnotationMatcher.match(candidates, clipping).highlight)
        expectFailure { AnnotationMatcher.match(candidates, clipping.copy(addedAt = null)) }
    }

    @Test fun uncertainAndDuplicateIdentitiesAreRejected() {
        val store = fixture()
        val candidate = SidecarCandidate(path, store.encoded(), store)
        expectFailure { AnnotationMatcher.match(listOf(candidate, candidate.copy(path = path.replace("Example.sdr", "Example copy.sdr"))), clipping) }
        val shifted = clipping.copy(locationStart = 111, locationEnd = 113)
        assertEquals(store.annotations().single(), AnnotationMatcher.match(listOf(candidate), shifted).highlight)
        expectFailure { AnnotationMatcher.match(listOf(candidate), shifted.copy(addedAt = null)) }
        expectFailure { AnnotationMatcher.match(listOf(candidate.copy(path = path.replace("Example", "Other"))), shifted) }
    }

    @Test fun titleWithoutLongAuthorCanMatchAndFallbackDoesNotMatchASequel() {
        val store = fixture()
        val candidate = SidecarCandidate(path, store.encoded(), store)
        val book = clipping.copy(author = "An Extremely Long Author Name That Is Not In The Filename")
        assertEquals(1.0, AnnotationMatcher.titleSimilarity(path, book), 0.0)
        assertEquals(store.annotations().single(), AnnotationMatcher.match(listOf(candidate), book).highlight)
        val differentLocation = book.copy(locationStart = 500, locationEnd = 501)
        assertEquals(store.annotations().single(), AnnotationMatcher.match(listOf(candidate), differentLocation).highlight)
        expectFailure { AnnotationMatcher.match(listOf(candidate.copy(path = path.replace("Example", "Example Sequel"))), differentLocation) }
    }

    @Test fun nonKfxSidecarWithUnrelatedFileNameStillMatchesByLocationAndTime() {
        val store = fixture()
        val candidate = SidecarCandidate(
            "/mnt/us/documents/In Praise of Idleness.sdr/In Praise of Idleness.azw3r",
            store.encoded(), store
        )
        val book = clipping.copy(bookTitle = "赞美闲散", author = "罗素")
        assertEquals(0.0, AnnotationMatcher.titleSimilarity(candidate.path, book), 0.0)
        assertEquals(store.annotations().single(), AnnotationMatcher.match(listOf(candidate), book).highlight)
    }

    @Test fun timestampFallbackAcceptsSidecarSuffixWhenFullTitleIsContained() {
        val store = fixture()
        val candidate = SidecarCandidate(
            "/mnt/us/documents/Example (Writer) (Z-Library).sdr/Example (Writer) (Z-Library).azw3r",
            store.encoded(), store
        )
        val shifted = clipping.copy(locationStart = 500, locationEnd = 501, author = "Writer")
        assertEquals(store.annotations().single(), AnnotationMatcher.match(listOf(candidate), shifted).highlight)
    }

    @Test fun lowTitleSimilarityDoesNotBypassTheTimestampLimit() {
        val store = fixture()
        val candidate = SidecarCandidate(
            "/mnt/us/documents/In Praise of Idleness.sdr/In Praise of Idleness.azw3r",
            store.encoded(), store
        )
        val book = clipping.copy(
            bookTitle = "赞美闲散",
            author = null,
            addedAt = clipping.addedAt?.plusSeconds(1_000)
        )
        expectFailure { AnnotationMatcher.match(listOf(candidate), book) }
    }

    @Test fun kindleAndPhoneTimeZoneDifferenceDoesNotBlockAnExactLocation() {
        val store = fixture()
        val candidate = SidecarCandidate(path, store.encoded(), store)
        // My Clippings keeps Kindle-local wall time while the sidecar stores an
        // absolute epoch, so a Kindle set to UTC and a UTC+8 phone differ by 8h.
        val book = clipping.copy(addedAt = clipping.addedAt?.plusSeconds(8 * 3_600))
        assertEquals(store.annotations().single(), AnnotationMatcher.match(listOf(candidate), book).highlight)
        val halfHourBook = clipping.copy(addedAt = clipping.addedAt?.plusSeconds(30 * 60 + 1))
        assertEquals(
            store.annotations().single(),
            AnnotationMatcher.match(listOf(candidate), halfHourBook).highlight
        )
    }

    @Test fun largeTimeDifferenceThatIsNotATimeZoneOffsetIsStillRejected() {
        val store = fixture()
        val candidate = SidecarCandidate(path, store.encoded(), store)
        val book = clipping.copy(addedAt = clipping.addedAt?.plusSeconds(50 * 60))
        expectFailure { AnnotationMatcher.match(listOf(candidate), book) }
    }

    @Test fun addingNotePreservesTrailingFieldsInAnnotationCache() {
        val store = fixture()
        val cache = store.values.first() as KrdsValue.ObjectValue
        cache.values += KrdsValue.Utf("unknown future cache metadata")
        val highlight = store.annotations().single()
        store.updateNote(highlight, emptySet(), "note", time, false)
        val verified = KindleReaderDataStore.decode(store.encoded())
        assertEquals(2, verified.annotations().size)
        assertEquals(KrdsValue.Utf("unknown future cache metadata"), (verified.values.first() as KrdsValue.ObjectValue).values.last())
    }

    @Test fun transactionWritesNativeAndClippingsFilesAndKeepsBackups() {
        val connection = FakeConnection()
        val result = service().write(connection, clipping, "Written from Android")
        val fresh = ClippingsParser.parse(ClippingsTextDecoder.decode(result.clippingsBytes)).single()
        assertEquals("Written from Android", fresh.kindleNotes.single().text)
        assertEquals("Written from Android", KindleReaderDataStore.decode(connection.files[path]!!).annotations().last().note)
        assertEquals(2, result.backupPaths.size)
        assertTrue(result.backupPaths.all { connection.files.containsKey(it) })
    }

    @Test fun syncWritesPhoneOnlyEditAndAcknowledgesAnIdenticalRetryWithoutWriting() {
        val connection = FakeConnection()
        val draft = NoteDraft(clipping.id, "from phone", kindleBaseText = "")
        val writer = service()
        val first = writer.syncDraft(connection, clipping, draft, null) as NoteDraftSyncResult.Accepted
        assertNotNull(first.written)
        val retry = writer.syncDraft(connection, clipping, draft, null) as NoteDraftSyncResult.Accepted
        assertEquals("from phone", retry.text)
        assertNull(retry.written)
        assertEquals(2, connection.replaces)
    }

    @Test fun staleClippingsCannotHideKindleDeletionOrOverrideAConflictChoice() {
        val connection = FakeConnection(source("old note"))
        val draft = NoteDraft(clipping.id, "from phone", kindleBaseText = "old note")
        val writer = service()
        val conflict = (writer.syncDraft(connection, clipping, draft, null) as NoteDraftSyncResult.Conflict).conflict
        assertEquals("", conflict.kindleText)
        assertEquals(0, connection.replaces)

        val store = fixture()
        store.updateNote(store.annotations().single(), emptySet(), "new Kindle text", time + 2000, false)
        connection.files[path] = store.encoded()
        val refreshed = writer.syncDraft(connection, clipping, draft, NoteConflictChoice(conflict, true))
            as NoteDraftSyncResult.Conflict
        assertEquals("new Kindle text", refreshed.conflict.kindleText)
        assertEquals(0, connection.replaces)
        val accepted = writer.syncDraft(connection, clipping, draft, NoteConflictChoice(refreshed.conflict, false))
            as NoteDraftSyncResult.Accepted
        assertEquals("new Kindle text", accepted.text)
        assertEquals(0, connection.replaces)
    }

    @Test fun pullRecognizesDeletedExportedNoteEvenIfItsHighlightWasAlsoDeleted() {
        val exported = ClippingsParser.parse(source("Ceui")).single()
        val store = fixture("9000", "9150")
        val remaining = store.annotations().single()
        store.updateNote(remaining, emptySet(), "another note", time + 10_000, false)
        val connection = FakeConnection(source("Ceui"), store)
        val archived = exported.copy(addedAt = exported.addedAt?.minusSeconds(100))
        val surviving = exported.copy(id = "surviving", locationStart = 61, locationEnd = 62, kindleNotes = emptyList())
        val result = service().readCurrentAnnotations(connection, listOf(archived, surviving))
        assertEquals("", result.notes[archived.id])
        assertEquals(setOf(archived.id), result.removedHighlights)
        assertEquals(setOf(archived.id, surviving.id), result.checkedHighlights)
        assertTrue(service().readCurrentAnnotations(connection, listOf(archived)).removedHighlights.isEmpty())
        assertTrue(service().readCurrentAnnotations(connection, listOf(archived.copy(bookTitle = "Unrelated"))).notes.isEmpty())
        assertEquals(0, connection.replaces)
    }

    @Test fun deletedAndRecreatedHighlightUsesLiveInstanceBeforeDeduplicating() {
        val metadataTime = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy h:mm:ss a", Locale.US)
            .withZone(ZoneId.systemDefault())
        val recreatedTime = time + 30_000
        val recreated = source.replace(
            metadataTime.format(Instant.ofEpochMilli(time)), metadataTime.format(Instant.ofEpochMilli(recreatedTime))
        )
        val exported = source("old note") + recreated
        val store = fixture()
        val tree = (store.values.first() as KrdsValue.ObjectValue).values[2] as KrdsValue.ObjectValue
        val native = tree.values[1] as KrdsValue.ObjectValue
        native.values[2] = KrdsValue.LongValue(recreatedTime)
        native.values[3] = KrdsValue.LongValue(recreatedTime)
        val connection = FakeConnection(exported, store)
        val records = ClippingsParser.parseDetailed(exported, includeSourceHighlights = true).sourceHighlights
        assertEquals(2, records.size)
        val (old, live) = records
        val state = service().readCurrentAnnotations(connection, records)
        assertEquals(setOf(old.id), state.removedHighlights)
        val visible = ClippingsParser.parseDetailed(exported, state.removedHighlights, setOf(live.id)).items
        assertEquals(live.id, visible.single().id)
        assertEquals("", state.notes[live.id])

        expectFailure {
            service().syncDraft(connection, old, NoteDraft(old.id, "archived draft", kindleBaseText = ""), null)
        }
        assertEquals(0, connection.replaces)
        val result = service().syncDraft(connection, live, NoteDraft(live.id, "new note", kindleBaseText = ""), null)
        assertTrue(result is NoteDraftSyncResult.Accepted)
        assertEquals("new note", KindleReaderDataStore.decode(connection.files.getValue(path)).annotations().last().note)
    }

    @Test fun keepingPhoneEditsNativeNoteEvenWhenItsTextIsMissingFromClippings() {
        val store = fixture()
        val highlight = store.annotations().single()
        val native = store.updateNote(highlight, emptySet(), "from Kindle", time + 2000, true)!!
        val connection = FakeConnection(source("base"), store)
        val draft = NoteDraft(clipping.id, "from phone", kindleBaseText = "base")
        val writer = service()
        val conflict = (writer.syncDraft(connection, clipping, draft, null) as NoteDraftSyncResult.Conflict).conflict
        writer.syncDraft(connection, clipping, draft, NoteConflictChoice(conflict, true))
        val notes = KindleReaderDataStore.decode(connection.files.getValue(path)).annotations()
        assertEquals(listOf(highlight, native.copy(note = "from phone", modificationTime = time + 1000)), notes)
    }

    @Test fun oneConflictDoesNotPreventAnIndependentDraftInTheSameBookFromWriting() {
        val secondSource = source().replace("11-13", "21-23")
            .replace("A complete example highlight.", "A second highlight.")
        val clippings = ClippingsParser.parse(source + secondSource)
        val first = clippings.first { it.locationStart == 11 }
        val second = clippings.first { it.locationStart == 21 }
        val store = fixture()
        val firstHighlight = store.annotations().single()
        val cache = store.values.first() as KrdsValue.ObjectValue
        val tree = cache.values[2] as KrdsValue.ObjectValue
        val secondCache = fixture("3000", "3300").values.first() as KrdsValue.ObjectValue
        val secondTree = secondCache.values[2] as KrdsValue.ObjectValue
        tree.values[0] = KrdsValue.Integer(2)
        tree.values.add(secondTree.values[1])
        store.updateNote(firstHighlight, emptySet(), "Kindle edit", time + 2000, false)

        val connection = FakeConnection(source + secondSource, store)
        val writer = service()
        val conflict = writer.syncDraft(connection, first, NoteDraft(first.id, "phone one", kindleBaseText = ""), null)
        assertTrue(conflict is NoteDraftSyncResult.Conflict)
        val accepted = writer.syncDraft(connection, second, NoteDraft(second.id, "phone two", kindleBaseText = ""), null)
        assertTrue(accepted is NoteDraftSyncResult.Accepted)
        val notes = KindleReaderDataStore.decode(connection.files.getValue(path)).annotations().mapNotNull { it.note }
        assertEquals(listOf("Kindle edit", "phone two"), notes)
        assertEquals(2, connection.replaces)
    }

    @Test fun threeWayComparisonPreservesUnchangedAndUnknownBaselines() {
        assertEquals(NoteSyncAction.KEEP_KINDLE, noteSyncAction("base", "base", "Kindle only"))
        assertEquals(NoteSyncAction.WRITE_PHONE, noteSyncAction("base", "", "base"))
        assertEquals(NoteSyncAction.ASK, noteSyncAction("base", "", "Kindle edit"))
        assertEquals(NoteSyncAction.ASK, noteSyncAction(null, "legacy draft", ""))
        assertEquals(NoteSyncAction.KEEP_KINDLE, noteSyncAction(null, "same", "same"))
    }

    @Test fun mergerReplacesLatestKindleTextAndPreservesUtf16Bom() {
        val noteStore = fixture()
        val note = noteStore.updateNote(noteStore.annotations().single(), emptySet(), "new", time + 1000, false)
        val oldSource = source("latest Kindle text")
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + oldSource.toByteArray(Charsets.UTF_16LE)
        val updated = ClippingsNoteMerger.merge(bytes, clipping, "new", note, false)
        assertEquals(0xFF.toByte(), updated[0])
        val text = ClippingsTextDecoder.decode(updated)
        assertFalse(text.contains("latest Kindle text"))
        assertEquals("new", ClippingsParser.parse(text).single().kindleNotes.single().text)
    }

    @Test fun clearingRemovesNotesFromBothFilesAndRetainsHighlight() {
        val store = fixture()
        store.updateNote(store.annotations().single(), emptySet(), "old note", time + 1000, false)
        val connection = FakeConnection(source("old note"), store)
        service().write(connection, ClippingsParser.parse(source("old note")).single(), "   ")
        assertEquals(1, KindleReaderDataStore.decode(connection.files[path]!!).annotations().size)
        assertTrue(ClippingsParser.parse(ClippingsTextDecoder.decode(connection.files[KindleNoteWritebackService.CLIPPINGS_PATH]!!)).single().kindleNotes.isEmpty())
    }

    @Test fun mergerPreservesUtf16WithoutBomAndOriginalLineEndings() {
        val store = fixture()
        val note = store.updateNote(store.annotations().single(), emptySet(), "new note", time, false)
        for (charset in listOf(Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            val original = source("old").replace("\n", "\r\n").toByteArray(charset)
            val merged = ClippingsNoteMerger.merge(original, clipping, "new note", note, false)
            val text = merged.toString(charset)
            assertTrue(text.contains("\r\n"))
            assertFalse(text.replace("\r\n", "").contains('\n'))
            assertFalse(text.startsWith("\uFEFF"))
            assertEquals("new note", ClippingsParser.parse(ClippingsTextDecoder.decode(merged)).single().kindleNotes.single().text)
        }
    }

    @Test fun failedSecondReplaceRestoresBothFilesEvenIfItFailedAfterRename() {
        val connection = FakeConnection(failAfterReplace = 2)
        val originalSidecar = connection.files[path]!!.copyOf()
        val originalClippings = connection.files[KindleNoteWritebackService.CLIPPINGS_PATH]!!.copyOf()
        expectFailure { service().write(connection, clipping, "must roll back") }
        assertArrayEquals(originalSidecar, connection.files[path])
        assertArrayEquals(originalClippings, connection.files[KindleNoteWritebackService.CLIPPINGS_PATH])
        assertEquals(2, connection.restored.size)
    }

    @Test fun corruptSidecarReadbackRestoresOriginal() {
        val connection = FakeConnection(corruptReadback = true)
        val original = connection.files[path]!!.copyOf()
        expectFailure { service().write(connection, clipping, "cannot verify") }
        assertArrayEquals(original, connection.files[path])
        assertEquals(1, connection.restored.size)
    }

    @Test fun externalModificationAfterReplaceIsKeptAndRecoveryFailureReportsBackup() {
        val connection = FakeConnection(failAfterReplace = 1, externalModification = true)
        val error = assertThrows(IllegalStateException::class.java) { service().write(connection, clipping, "our text") }
        assertTrue(error.message.orEmpty().contains("Kindle 备份"))
        assertEquals("external update", connection.files[path]!!.toString(Charsets.UTF_8))
        assertTrue(connection.restored.isEmpty())
    }

    @Test fun readerOpenOrUnknownAndMissingClippingNeverModifyFiles() {
        for (state in listOf("activeApp=reader", "activeView=KPP_READER", "activeApp=\nactiveView=\nactiveContext=")) {
            val connection = FakeConnection(readerState = state)
            expectFailure { service().write(connection, clipping, "text") }
            assertEquals(0, connection.replaces)
        }
        val missing = FakeConnection(source = source.replace("A complete example highlight.", "Different passage."))
        expectFailure { service().write(missing, clipping, "text") }
        assertEquals(0, missing.replaces)
    }

    @Test fun unsafeTextNeverChangesRemoteFiles() {
        val connection = FakeConnection()
        expectFailure { service().write(connection, clipping, "bad\n==========\nrecord") }
        expectFailure { service().write(connection, clipping, "中".repeat(22000)) }
        assertEquals(0, connection.replaces)
    }


    private fun service() = KindleNoteWritebackService({ error("Unused in offline tests") }, { time + 1000 })

    private fun source(note: String? = null): String {
        val date = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy h:mm:ss a", Locale.US)
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
        return "Example\n- Your Highlight on Location 11-13 | Added on $date\n\nA complete example highlight.\n==========\n" +
            (note?.let { "Example\n- Your Note on Location 11-13 | Added on $date\n\n$it\n==========\n" } ?: "")
    }

    private fun fixture(start: String = "1500", end: String = "1800"): KindleReaderDataStore {
        val highlight = KrdsValue.ObjectValue("annotation.personal.highlight", mutableListOf(
            KrdsValue.Utf(start), KrdsValue.Utf(end), KrdsValue.LongValue(time), KrdsValue.LongValue(time), KrdsValue.Utf("0\uFFFC0")
        ))
        val tree = KrdsValue.ObjectValue("saved.avl.interval.tree", mutableListOf(KrdsValue.Integer(1), highlight))
        return KindleReaderDataStore(mutableListOf(KrdsValue.ObjectValue("annotation.cache.object",
            mutableListOf(KrdsValue.Integer(1), KrdsValue.Integer(1), tree))))
    }

    private fun expectFailure(block: () -> Unit) {
        try { block() } catch (_: Exception) { return }
        fail("Expected the unsafe or failed write to be rejected")
    }

    private inner class FakeConnection(
        source: String = this@KindleNoteWritebackTest.source,
        store: KindleReaderDataStore = fixture(),
        private val failAfterReplace: Int? = null,
        private val corruptReadback: Boolean = false,
        private val externalModification: Boolean = false,
        private val readerState: String = "activeApp=home"
    ) : NoteWritebackConnection {
        val files = mutableMapOf(path to store.encoded(), KindleNoteWritebackService.CLIPPINGS_PATH to source.toByteArray())
        val restored = mutableListOf<String>()
        var replaces = 0
        override fun exec(command: String) = if (command.contains("lipc-get-prop")) readerState else path
        override fun download(path: String): ByteArray {
            if (corruptReadback && replaces > 0 && path == this@KindleNoteWritebackTest.path) return byteArrayOf(0)
            return files[path]!!.copyOf()
        }
        override fun replace(path: String, original: ByteArray, updated: ByteArray, backup: String) {
            check(files[path]!!.contentEquals(original))
            files[backup] = files[path]!!.copyOf()
            files[path] = updated.copyOf()
            replaces++
            if (replaces == failAfterReplace) {
                if (externalModification) files[path] = "external update".toByteArray()
                error("Network failure after remote rename")
            }
        }
        override fun restore(path: String, backup: String, written: ByteArray) {
            files[backup]?.let {
                if (!files[path]!!.contentEquals(it)) {
                    check(files[path]!!.contentEquals(written)) { "File changed after writing" }
                    files[path] = it.copyOf()
                }
                restored += path
            }
        }
        override fun close() = Unit
    }
}
