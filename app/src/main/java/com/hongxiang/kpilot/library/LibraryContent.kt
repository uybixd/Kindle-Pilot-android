package com.hongxiang.kpilot.library

import com.hongxiang.kpilot.notes.KindleClipping
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Prepared once per data update and retained by the ViewModel across tab changes. */
class LibraryContent(
    val snapshot: LibrarySnapshot? = null,
    val notesByPath: Map<String, List<KindleClipping>> = emptyMap()
) {
    // Do not display a previous Kindle's matches while the new snapshot is being prepared.
    fun notesFor(snapshot: LibrarySnapshot?): Map<String, List<KindleClipping>> =
        if (this.snapshot === snapshot) notesByPath else emptyMap()
}

internal data class LibraryContentInput(val snapshot: LibrarySnapshot?, val clippings: List<KindleClipping>)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<LibraryContentInput>.prepareLibraryContent(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    prepare: suspend (LibraryContentInput) -> LibraryContent = ::buildLibraryContent
): Flow<LibraryContent> = distinctUntilChanged { previous, current ->
    previous.snapshot === current.snapshot && previous.clippings === current.clippings
}.mapLatest { input ->
    withContext(dispatcher) { prepare(input) }
}

private suspend fun buildLibraryContent(input: LibraryContentInput): LibraryContent {
    val books = input.snapshot?.books.orEmpty()
    if (books.isEmpty()) return LibraryContent(input.snapshot)
    val index = LibraryClippingsIndex(input.clippings)
    val matches = books.associate { book ->
        coroutineContext.ensureActive()
        book.remotePath to index.clippings(book)
    }
    return LibraryContent(input.snapshot, matches)
}
