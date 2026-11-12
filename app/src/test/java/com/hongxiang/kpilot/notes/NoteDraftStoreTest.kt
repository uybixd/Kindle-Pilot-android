package com.hongxiang.kpilot.notes

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.DataOutputStream

class NoteDraftStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val scope = "a".repeat(64)

    @Test fun verifiedExportTextRemainsTheBaselineAfterAnOfflineEdit() {
        val verified = rememberKindleNoteBaseline("id", null, "What about now.", true)!!
        val store = NoteDraftStore(temporary.newFolder())
        store.save(scope, mapOf("id" to verified))
        val saved = store.load(scope).getValue("id")
        val edited = stageNoteDraft("id", saved, "改改改", hasKindleNote = true, writeInFlight = false)!!
        assertEquals("What about now.", edited.kindleBaseText)
        assertTrue(edited.needsSync)
        assertEquals(edited, rememberKindleNoteBaseline("id", edited, "new Kindle text", true))
    }

    @Test fun savedDraftsAndDeletionMarkersSurviveRestartAndRemainDeviceScoped() {
        val directory = temporary.newFolder()
        val store = NoteDraftStore(directory)
        val drafts = mapOf("highlight" to NoteDraft("highlight", "中文批注 📚\nsecond line", kindleBaseText = ""),
            "cleared" to NoteDraft("cleared", "", "revision", "revision", "先前的批注"))
        store.save(scope, drafts)
        assertEquals(drafts, NoteDraftStore(directory).load(scope))
        assertTrue(store.load("b".repeat(64)).isEmpty())
        assertFalse(store.load(scope).getValue("cleared").needsSync)
    }

    @Test fun versionOneDraftLoadsWithoutInventingABaseline() {
        val directory = temporary.newFolder()
        DataOutputStream(directory.resolve("$scope.dat").outputStream()).use {
            it.writeInt(0x4B504E44)
            it.writeInt(1)
            it.writeInt(1)
            it.writeUTF("id")
            it.writeUTF("revision")
            it.writeUTF("")
            it.writeInt(6)
            it.writeBytes("legacy")
        }
        val store = NoteDraftStore(directory)
        val drafts = store.load(scope)
        assertEquals(NoteDraft("id", "legacy", "revision"), drafts.getValue("id"))
        store.save(scope, drafts)
        assertEquals(drafts, store.load(scope))
    }

    @Test fun sendingAnOlderRevisionCannotAcknowledgeANewerEdit() {
        val old = NoteDraft("id", "old", "old-version")
        val edited = NoteDraft("id", "new", "new-version")
        assertEquals(edited, edited.acknowledge(old.revision))
        assertTrue(edited.acknowledge(old.revision).needsSync)
        assertFalse(edited.acknowledge(edited.revision).needsSync)
    }

    @Test fun clearingDuringFirstDeliveryKeepsPendingDeletion() {
        val inFlight = NoteDraft("id", "first note", "sending")
        val cleared = stageNoteDraft("id", inFlight, "", hasKindleNote = false, writeInFlight = true)!!
        assertEquals("", cleared.text)
        assertTrue(cleared.needsSync)
        assertTrue(cleared.acknowledge(inFlight.revision).needsSync)
        assertNotNull(stageNoteDraft("id", inFlight, "", hasKindleNote = false, writeInFlight = false))
        assertNull(stageNoteDraft("id", null, "", hasKindleNote = false, writeInFlight = false))
    }

    @Test fun editingRetainsEvidenceOfEarlierDeliveryForLaterDeletion() {
        val sent = NoteDraft("id", "sent", "sent", "sent")
        val edited = sent.revise("new local text")
        assertTrue(edited.needsSync)
        val cleared = stageNoteDraft("id", edited, "", hasKindleNote = false, writeInFlight = false)!!
        assertEquals("", cleared.text)
        assertTrue(cleared.needsSync)
        assertEquals("sent", cleared.syncedRevision)
    }

    @Test fun corruptedStoreIsReportedAndNeverSilentlyReplaced() {
        val directory = temporary.newFolder()
        val file = File(directory, "$scope.dat")
        val broken = byteArrayOf(1, 2, 3)
        file.writeBytes(broken)
        assertThrows(Exception::class.java) { NoteDraftStore(directory).load(scope) }
        assertArrayEquals(broken, file.readBytes())
    }

    @Test fun rejectedOversizedSaveKeepsPreviousDraft() {
        val store = NoteDraftStore(temporary.newFolder())
        val original = mapOf("id" to NoteDraft("id", "original"))
        store.save(scope, original)
        assertThrows(IllegalArgumentException::class.java) {
            store.save(scope, mapOf("id" to NoteDraft("id", "x".repeat(1_000_001))))
        }
        assertEquals(original, store.load(scope))
    }
}
