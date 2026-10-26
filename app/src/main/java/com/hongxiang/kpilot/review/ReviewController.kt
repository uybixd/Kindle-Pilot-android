package com.hongxiang.kpilot.review

import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.data.cacheScope
import com.hongxiang.kpilot.notes.KindleClipping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

data class ReviewUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val current: KindleClipping? = null,
    val record: ReviewRecord? = null,
    val batchTotal: Int = 0,
    val batchRemaining: Int = 0,
    val todayCount: Int = 0,
    val candidateCount: Int = 0,
    val favoriteCount: Int = 0,
    val excludedCount: Int = 0,
    val canUndo: Boolean = false,
    val error: String? = null,
    val failure: ReviewFailure? = null
)

enum class ReviewFailure { LOAD, SAVE }

/** Serializes state changes and disk writes, including source changes, on one mutex. */
class ReviewController(private val directory: File, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(ReviewUiState())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var sourceScope: String? = null
    private var store: ReviewHistoryStore? = null
    private var session: ReviewSession? = null

    suspend fun updateSource(settings: ConnectionSettings, notes: List<KindleClipping>) = mutex.withLock {
        val namespace = settings.cacheScope()
        mutableState.value = mutableState.value.copy(loading = true)
        try {
            if (sourceScope != namespace || session == null) {
                val nextStore = ReviewHistoryStore(File(directory, "$namespace.review"))
                val snapshot = withContext(Dispatchers.IO) { nextStore.load() }
                session = ReviewSession(snapshot, notes)
                store = nextStore
                sourceScope = namespace
            } else session?.updateNotes(notes)
            session?.markDisplayed(Instant.now())
            saveAndPublish()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // A failed decode must not overwrite the only copy of the user's history.
            mutableState.value = ReviewUiState(loading = false, error = "无法载入复习记录，请重试", failure = ReviewFailure.LOAD)
        }
    }

    fun complete(action: ReviewAction) = mutate {
        complete(action)
        markDisplayed(Instant.now())
    }

    fun undo() = mutate { undoLastAction(); markDisplayed(Instant.now()) }
    fun nextBatch() = mutate { nextBatch(); markDisplayed(Instant.now()) }
    fun restoreExcluded() = mutate { restoreExcluded(); markDisplayed(Instant.now()) }

    fun retrySave() = scope.launch { mutex.withLock { saveAndPublish() } }

    fun retry(settings: ConnectionSettings, notes: List<KindleClipping>) = scope.launch {
        if (mutableState.value.failure == ReviewFailure.SAVE) mutex.withLock { saveAndPublish() }
        else updateSource(settings, notes)
    }

    private fun mutate(action: ReviewSession.() -> Unit) = scope.launch {
        mutex.withLock {
            val currentSession = session ?: return@withLock
            mutableState.value = mutableState.value.copy(saving = true)
            currentSession.action()
            saveAndPublish()
        }
    }

    private suspend fun saveAndPublish() {
        val value = session ?: return
        val failure = try {
            withContext(Dispatchers.IO) { store?.save(value.snapshot) }
            false
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            true
        }
        mutableState.value = ReviewUiState(
            loading = false,
            current = value.current,
            record = value.current?.id?.let(value.snapshot.records::get),
            batchTotal = value.snapshot.batchTotal,
            batchRemaining = value.snapshot.batch.size,
            todayCount = value.todayCount(Instant.now()),
            candidateCount = value.eligibleCount,
            favoriteCount = value.favoriteCount,
            excludedCount = value.excludedCount,
            canUndo = value.undo != null,
            error = if (failure) "复习记录尚未保存，请重试" else null,
            failure = if (failure) ReviewFailure.SAVE else null
        )
    }
}
