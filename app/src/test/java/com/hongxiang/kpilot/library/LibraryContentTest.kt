package com.hongxiang.kpilot.library

import com.hongxiang.kpilot.notes.ClippingKind
import com.hongxiang.kpilot.notes.KindleClipping
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class LibraryContentTest {
    private val book = KindleBook("/mnt/us/documents/Book (Author).azw3")
    private val snapshot = LibrarySnapshot(listOf(book, KindleBook("/mnt/us/documents/Empty.pdf")), Instant.EPOCH, "kindle-a")

    @Test fun backgroundPreparationPreservesMatchesCountsAndLocationOrder() = runBlocking {
        val clippings = listOf(clipping("later", 20), clipping("first", 5), clipping("other", 1).copy(bookTitle = "Other"))
        val content = flowOf(LibraryContentInput(snapshot, clippings)).prepareLibraryContent().single()
        assertSame(snapshot, content.snapshot)
        assertEquals(listOf("first", "later"), content.notesByPath[book.remotePath]?.map { it.id })
        assertEquals(emptyList<KindleClipping>(), content.notesByPath[snapshot.books[1].remotePath])
    }

    @Test fun preparationUsesWorkerDispatcher() = runBlocking {
        val worker = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "library-preparation").also(worker::set) }
            .asCoroutineDispatcher().use { dispatcher ->
                val content = flowOf(LibraryContentInput(snapshot, emptyList()))
                    .prepareLibraryContent(dispatcher) { input ->
                        assertSame(worker.get(), Thread.currentThread())
                        LibraryContent(input.snapshot)
                    }.single()
                assertSame(snapshot, content.snapshot)
            }
    }

    @Test fun duplicateInputsAndTabResubscriptionsReusePreparedContent() = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val inputs = MutableSharedFlow<LibraryContentInput>(replay = 1)
                val preparations = AtomicInteger()
                val retained = inputs.prepareLibraryContent { input ->
                    preparations.incrementAndGet()
                    LibraryContent(input.snapshot, mapOf(book.remotePath to input.clippings))
                }.stateIn(scope, SharingStarted.Eagerly, LibraryContent())
                val input = LibraryContentInput(snapshot, listOf(clipping("first", 5)))
                inputs.emit(input)
                val firstVisit = retained.first { it.snapshot === snapshot }
                // Leaving and revisiting the tab creates a new collector, not a new index.
                assertSame(firstVisit, retained.first())
                inputs.emit(input.copy())
                val updated = snapshot.copy(syncedAt = Instant.EPOCH.plusSeconds(1))
                inputs.emit(input.copy(snapshot = updated))
                retained.first { it.snapshot === updated }
                // A reloaded but structurally equal snapshot must still replace the
                // retained reference; otherwise notesFor would hide its matches forever.
                val reloaded = updated.copy()
                inputs.emit(input.copy(snapshot = reloaded))
                retained.first { it.snapshot === reloaded }
                inputs.emit(input.copy(snapshot = reloaded, clippings = input.clippings + clipping("new", 10)))
                retained.first { it.notesByPath[book.remotePath]?.size == 2 }
                assertEquals(4, preparations.get())
            } finally { scope.cancel() }
        }
    }

    @Test fun newSnapshotCancelsOutdatedPreparation() = runBlocking {
        withTimeout(5_000) {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val updated = snapshot.copy(sourceKey = "kindle-b")
            val results = flow {
                emit(LibraryContentInput(snapshot, emptyList()))
                started.await()
                emit(LibraryContentInput(updated, emptyList()))
            }.prepareLibraryContent { input ->
                if (input.snapshot === snapshot) {
                    started.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                }
                LibraryContent(input.snapshot)
            }.toList()
            assertTrue(cancelled.isCompleted)
            assertEquals(1, results.size)
            assertSame(updated, results.single().snapshot)
        }
    }

    @Test fun previousSnapshotMatchesAreHiddenWhileNewDataIsPrepared() {
        val matches = mapOf(book.remotePath to listOf(clipping("first", 5)))
        val content = LibraryContent(snapshot, matches)
        assertSame(matches, content.notesFor(snapshot))
        assertTrue(content.notesFor(snapshot.copy(sourceKey = "kindle-b")).isEmpty())
        assertTrue(content.notesFor(snapshot.copy(syncedAt = Instant.EPOCH.plusSeconds(1))).isEmpty())
        assertTrue(content.notesFor(null).isEmpty())
    }

    private fun clipping(id: String, location: Int) = KindleClipping(
        id, "book", "Book", "Author", ClippingKind.HIGHLIGHT, location.toString(), location, location,
        null, null, "metadata", "A passage", "raw"
    )
}
