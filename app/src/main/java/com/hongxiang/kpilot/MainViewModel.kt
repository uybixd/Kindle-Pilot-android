package com.hongxiang.kpilot

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.data.AppPreferences
import com.hongxiang.kpilot.data.SecureSettingsStore
import com.hongxiang.kpilot.data.KindleProfiles
import com.hongxiang.kpilot.data.KindleProfile
import com.hongxiang.kpilot.data.LocalLibraryData
import com.hongxiang.kpilot.data.NavigationItem
import com.hongxiang.kpilot.data.NavigationOptions
import com.hongxiang.kpilot.data.cacheScope
import com.hongxiang.kpilot.data.legacyCacheScope
import com.hongxiang.kpilot.data.DataScopeMigration
import com.hongxiang.kpilot.library.LibraryService
import com.hongxiang.kpilot.library.LibrarySnapshot
import com.hongxiang.kpilot.library.KindleBook
import com.hongxiang.kpilot.library.LibraryRules
import com.hongxiang.kpilot.library.BookCoverLoader
import com.hongxiang.kpilot.library.LibraryContent
import com.hongxiang.kpilot.library.LibraryContentInput
import com.hongxiang.kpilot.library.prepareLibraryContent
import com.hongxiang.kpilot.export.DownloadExportStore
import com.hongxiang.kpilot.band.XiaomiBandController
import com.hongxiang.kpilot.model.CommandSink
import com.hongxiang.kpilot.model.KindleCommand
import com.hongxiang.kpilot.notes.ClippingsExportFormat
import com.hongxiang.kpilot.notes.ClippingsExporter
import com.hongxiang.kpilot.notes.ClippingsSnapshot
import com.hongxiang.kpilot.notes.ClippingsSyncService
import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.notes.ClippingKind
import com.hongxiang.kpilot.notes.NoteDraft
import com.hongxiang.kpilot.notes.NoteDraftStore
import com.hongxiang.kpilot.notes.stageNoteDraft
import com.hongxiang.kpilot.notes.rememberKindleNoteBaseline
import com.hongxiang.kpilot.notes.writeback.KindleNoteWritebackService
import com.hongxiang.kpilot.notes.writeback.NoteConflict
import com.hongxiang.kpilot.notes.writeback.NoteConflictChoice
import com.hongxiang.kpilot.notes.writeback.NoteDraftSyncResult
import com.hongxiang.kpilot.notes.writeback.NoteWritebackConnection
import com.hongxiang.kpilot.notes.writeback.normalizedNoteText
import com.hongxiang.kpilot.notes.NotesDiagnostics
import com.hongxiang.kpilot.notes.NotesSyncException
import com.hongxiang.kpilot.ssh.KindleSshClient
import com.hongxiang.kpilot.ssh.KindleIdentityService
import com.hongxiang.kpilot.remote.AccessibilityStatus
import com.hongxiang.kpilot.remote.FlipCommandDefinition
import com.hongxiang.kpilot.remote.RemoteCommandDispatcher
import com.hongxiang.kpilot.remote.ScreenOffRemoteService
import com.hongxiang.kpilot.support.SyncTrace
import com.hongxiang.kpilot.review.ClippingReviewAction
import com.hongxiang.kpilot.review.ClippingReviewRecord
import com.hongxiang.kpilot.review.ClippingReviewUndo
import com.hongxiang.kpilot.review.ClippingsReviewPlanner
import com.hongxiang.kpilot.review.LearningState
import com.hongxiang.kpilot.review.LearningMerge
import com.hongxiang.kpilot.review.LearningStore
import com.hongxiang.kpilot.review.LearningSyncService
import com.hongxiang.kpilot.review.VocabularyDefinition
import com.hongxiang.kpilot.review.VocabularyReviewPlanner
import com.hongxiang.kpilot.review.VocabularyReviewQueue
import com.hongxiang.kpilot.review.VocabularyReviewRecord
import com.hongxiang.kpilot.review.VocabularyReviewResult
import com.hongxiang.kpilot.review.VocabularyReviewUndo
import com.hongxiang.kpilot.transfer.CollisionDecision
import com.hongxiang.kpilot.transfer.KindleTransferService
import com.hongxiang.kpilot.transfer.RemoteDocumentNaming
import com.hongxiang.kpilot.transfer.SupportedTransferTypes
import com.hongxiang.kpilot.transfer.TransferCollision
import com.hongxiang.kpilot.transfer.TransferItem
import com.hongxiang.kpilot.transfer.TransferQueueRules
import com.hongxiang.kpilot.transfer.TransferStatus
import com.hongxiang.kpilot.vocabulary.VocabularyExportFormat
import com.hongxiang.kpilot.vocabulary.VocabularyExporter
import com.hongxiang.kpilot.vocabulary.VocabularyOrdering
import com.hongxiang.kpilot.vocabulary.VocabularySnapshot
import com.hongxiang.kpilot.vocabulary.VocabularySortOrder
import com.hongxiang.kpilot.vocabulary.VocabularySyncService
import com.hongxiang.kpilot.vocabulary.VocabularySyncException
import com.hongxiang.kpilot.vocabulary.VocabularyWord
import com.hongxiang.kpilot.widget.FavoriteWidgetProvider
import com.hongxiang.kpilot.widget.FavoriteWidgetStore
import com.hongxiang.kpilot.widget.FavoriteWidgetStyle
import com.hongxiang.kpilot.widget.widgetFavorites
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant

data class UiState(
    val settings: ConnectionSettings = ConnectionSettings(),
    val profiles: List<KindleProfile> = emptyList(),
    val activeProfileId: String = "",
    val connected: Boolean = false,
    val connectionMessage: String = "未连接",
    val connectionError: Boolean = false,
    val connectionBusy: Boolean = false,
    val localDataBusy: Boolean = false,
    val localDataMessage: String = "",
    val localDataError: Boolean = false,
    val notesBookPickerVisible: Boolean = false,
    val status: String = "请填写 Kindle 连接信息",
    val isError: Boolean = false,
    val busy: Boolean = false,
    val volumeKeysEnabled: Boolean = true,
    val keyboardKeysEnabled: Boolean = false,
    val remoteControlEnabled: Boolean = false,
    val remoteCommandsChecked: Boolean = false,
    val missingFlipCommands: List<FlipCommandDefinition> = emptyList(),
    val remoteRecordingCommandId: String? = null,
    val accessibilityServiceEnabled: Boolean = false,
    val xiaomiBandEnabled: Boolean = false,
    val xiaomiBandConnected: Boolean = false,
    val xiaomiBandMessage: String = "小米手环遥控未开启",
    val xiaomiBandError: Boolean = false,
    val transfers: List<TransferItem> = emptyList(),
    val collision: TransferCollision? = null,
    val transferMessage: String = "请选择要发送的书籍",
    val navigationItems: Set<NavigationItem> = NavigationOptions.defaults,
    val library: LibrarySnapshot? = null,
    val librarySyncing: Boolean = false,
    val libraryError: String? = null,
    val libraryRepairingPath: String? = null,
    val libraryRepairMessage: String? = null,
    val libraryRepairError: Boolean = false,
    val libraryDeletingPath: String? = null,
    val libraryDeleteMessage: String? = null,
    val libraryDeleteError: Boolean = false,
    val libraryCoverRevision: Int = 0,
    val noteDrafts: Map<String, NoteDraft> = emptyMap(),
    val noteDraftsLoading: Boolean = true,
    val noteDraftsAvailable: Boolean = false,
    val noteDraftSaving: Boolean = false,
    val noteWritingId: String? = null,
    val noteConflicts: Map<String, NoteConflict> = emptyMap(),
    val noteConflictReviewOpen: Boolean = false,
    val noteWriteMessage: String = "",
    val noteWriteError: Boolean = false,
    val notes: List<KindleClipping> = emptyList(),
    val notesQuery: String = "",
    val selectedBook: String? = null,
    val selectedNoteId: String? = null,
    val notesSyncing: Boolean = false,
    val notesDownloaded: Long = 0,
    val notesTotal: Long? = null,
    val notesLastSync: Instant? = null,
    val notesFilteredDuplicates: Int = 0,
    val notesFilteredSingleSelections: Int = 0,
    val notesSortOrder: NotesSortOrder = NotesSortOrder.LOCATION,
    val notesMessage: String = "从 Kindle 同步摘抄",
    val notesError: Boolean = false,
    val notesDiagnosticCode: String? = null,
    val notesDiagnosticStage: String? = null,
    val vocabularyWords: List<VocabularyWord> = emptyList(),
    val vocabularyQuery: String = "",
    val vocabularySortOrder: VocabularySortOrder = VocabularySortOrder.ALPHABETICAL,
    val selectedVocabularyKey: String? = null,
    val vocabularySyncing: Boolean = false,
    val vocabularyDownloaded: Long = 0,
    val vocabularyTotal: Long? = null,
    val vocabularyLastSync: Instant? = null,
    val vocabularyMessage: String = "从 Kindle 同步生词",
    val vocabularyError: Boolean = false,
    val vocabularyDiagnosticCode: String? = null,
    val clippingReviewRecords: Map<String, ClippingReviewRecord> = emptyMap(),
    val clippingReviewDeck: List<String> = emptyList(),
    val clippingReviewDailyCounts: Map<String, Int> = emptyMap(),
    val clippingReviewPresented: Boolean = false,
    val clippingReviewRevision: Int = 0,
    val clippingReviewUndo: ClippingReviewUndo? = null,
    val vocabularyReviewRecords: Map<String, VocabularyReviewRecord> = emptyMap(),
    val vocabularyReviewKey: String? = null,
    val vocabularyReviewPresented: Boolean = false,
    val vocabularyReviewUndo: VocabularyReviewUndo? = null,
    val vocabularyDefinitions: Map<String, VocabularyDefinition> = emptyMap(),
    val learningLoaded: Boolean = false,
    val learningSyncing: Boolean = false,
    val learningMessage: String = "",
    val learningError: Boolean = false
)

enum class NotesSortOrder { LOCATION, ADDED_AT }

/** Avoid repeating a failed identity lookup for every concurrent/offline sync. */
private const val IDENTITY_RETRY_MS = 30_000L
private const val LEARNING_SYNC_NOTICE_MS = 5_000L

class MainViewModel(application: Application) : AndroidViewModel(application), CommandSink {
    private val store = SecureSettingsStore(application)
    private val profiles = KindleProfiles(application)
    private val localData = LocalLibraryData(application)
    private val ssh = KindleSshClient()
    private val identityService = KindleIdentityService()
    private val identityMutex = Mutex()
    private var identityAttemptScope: String? = null
    private var identityAttemptAt = 0L
    private val transferService = KindleTransferService(application.contentResolver)
    private val clippingsService = ClippingsSyncService(application)
    private val libraryService = LibraryService(application)
    private val noteDraftStore = NoteDraftStore(application)
    private val writebackService = KindleNoteWritebackService(application)
    private val draftMutex = Mutex()
    private var pendingDraftSaves = 0
    private val vocabularyService = VocabularySyncService(application)
    private val learningStore = LearningStore(application)
    private val learningSyncService = LearningSyncService()
    private val phoneId by lazy { preferences.phoneId }
    private val learningSaveMutex = Mutex()
    private var learningSyncNoticeTimer: Job? = null
    private val favoriteWidgetStore = FavoriteWidgetStore(application)
    private val favoriteWidgetLock = Any()
    private val displayedReviewIds = mutableSetOf<String>()
    private val vocabularyReviewSkippedKeys = mutableSetOf<String>()
    private var clippingReviewUndoTimer: Job? = null
    private val exportStore = DownloadExportStore(application)
    private val preferences = AppPreferences(application)
    private val remoteDispatcher = RemoteCommandDispatcher.get(application)
    private val xiaomiBandController = XiaomiBandController.get(application)
    private val transferQueue = Channel<String>(Channel.BUFFERED)
    private var collisionDecision: CompletableDeferred<CollisionDecision>? = null
    private val mutableState = MutableStateFlow(
        UiState(
            settings = store.load() ?: profiles.settings(),
            profiles = profiles.all(), activeProfileId = profiles.activeId,
            remoteControlEnabled = preferences.remoteControlEnabled,
            volumeKeysEnabled = preferences.volumeKeysEnabled,
            keyboardKeysEnabled = preferences.keyboardKeysEnabled,
            accessibilityServiceEnabled = AccessibilityStatus.isRemoteServiceEnabled(application),
            xiaomiBandEnabled = preferences.xiaomiBandEnabled,
            navigationItems = preferences.navigationItems
        )
    )
    val state: StateFlow<UiState> = mutableState.asStateFlow()
    val libraryContent = state.map { LibraryContentInput(it.library, it.notes) }
        .prepareLibraryContent()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryContent())
    val libraryCovers = BookCoverLoader()

    init {
        viewModelScope.launch {
            state.map { it.library }.distinctUntilChanged { previous, current -> previous === current }
                .collectLatest { snapshot ->
                    // Warm only the first screenful. Decoding stays on IO and the
                    // same bounded cache is reused when the shelf becomes visible.
                    snapshot?.books?.take(9)?.forEach { book -> book.coverPath?.let { libraryCovers.load(it) } }
                }
        }
        setRemoteControlEnabled(false)
        setKeyboardKeysEnabled(false)
        // Re-render existing widgets on launch so layout/font changes from an app
        // update take effect without removing and re-adding the widget.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { FavoriteWidgetProvider.updateAll(getApplication()) }
        }
        viewModelScope.launch(Dispatchers.IO) { exportStore.clearLegacyPrivateExports() }
        viewModelScope.launch {
            remoteDispatcher.state.collect { dispatch ->
                mutableState.update {
                    it.copy(
                        busy = dispatch.busy,
                        status = dispatch.message,
                        isError = dispatch.error,
                        remoteCommandsChecked = dispatch.hasCheckedFlipCommands,
                        missingFlipCommands = dispatch.missingFlipCommands,
                        remoteRecordingCommandId = dispatch.recordingCommandId
                    )
                }
            }
        }
        viewModelScope.launch {
            xiaomiBandController.state.collect { band ->
                mutableState.update {
                    it.copy(
                        xiaomiBandEnabled = band.enabled,
                        xiaomiBandConnected = band.connected,
                        xiaomiBandMessage = band.message,
                        xiaomiBandError = band.error
                    )
                }
            }
        }
        viewModelScope.launch {
            for (itemId in transferQueue) processTransfer(itemId)
        }
        loadDeviceData(state.value.settings, migrateLegacy = true)
    }

    fun updateSettings(settings: ConnectionSettings) {
        val changedDevice = state.value.settings.cacheScope() != settings.cacheScope()
        if (changedDevice) {
            libraryCovers.clear()
            displayedReviewIds.clear()
            vocabularyReviewSkippedKeys.clear()
            clippingReviewUndoTimer?.cancel()
            learningSyncNoticeTimer?.cancel()
        }
        mutableState.update {
            if (changedDevice) it.copy(
                settings = settings, connected = false, connectionMessage = "未连接", connectionError = false, isError = false, library = null, libraryError = null,
                libraryRepairingPath = null, libraryRepairMessage = null, libraryRepairError = false,
                libraryDeletingPath = null, libraryDeleteMessage = null, libraryDeleteError = false,
                notes = emptyList(), notesLastSync = null, selectedBook = null, selectedNoteId = null,
                notesMessage = "从 Kindle 同步摘抄", notesError = false, notesDiagnosticCode = null, notesDiagnosticStage = null,
                vocabularyWords = emptyList(), vocabularyLastSync = null, selectedVocabularyKey = null,
                vocabularyError = false, vocabularyDiagnosticCode = null,
                noteDrafts = emptyMap(), noteDraftsLoading = true, noteDraftsAvailable = false,
                noteWriteMessage = "", noteWriteError = false, noteConflicts = emptyMap(), noteConflictReviewOpen = false,
                clippingReviewRecords = emptyMap(), clippingReviewDeck = emptyList(),
                clippingReviewDailyCounts = emptyMap(), clippingReviewPresented = false,
                clippingReviewRevision = 0, clippingReviewUndo = null,
                vocabularyReviewRecords = emptyMap(), vocabularyReviewKey = null,
                vocabularyReviewPresented = false, vocabularyReviewUndo = null,
                vocabularyDefinitions = emptyMap(),
                learningLoaded = false, learningMessage = "", learningError = false
            ) else it.copy(settings = settings, isError = false)
        }
        if (changedDevice) loadDeviceData(settings)
    }

    private fun loadDeviceData(settings: ConnectionSettings, migrateLegacy: Boolean = false) {
        val scope = settings.cacheScope()
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                if (migrateLegacy) vocabularyService.migrateLegacyCache(settings)
                vocabularyService.loadCached(settings)
            } }.onSuccess { snapshot ->
                if (snapshot != null) applyVocabularySnapshot(scope, snapshot)
            }.onFailure { error ->
                if (state.value.settings.cacheScope() == scope) mutableState.update {
                    it.copy(
                        vocabularyError = true,
                        vocabularyMessage = error.message ?: "无法读取生词缓存",
                        vocabularyDiagnosticCode = null
                    )
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (migrateLegacy) clippingsService.migrateLegacyCache(settings)
                    clippingsService.loadCached(settings)
                }
            }.onSuccess { snapshot ->
                if (state.value.settings.cacheScope() == scope && snapshot != null &&
                    (state.value.notesLastSync == null || !snapshot.syncedAt.isBefore(state.value.notesLastSync))) {
                    applyNotesSnapshot(snapshot)
                }
            }.onFailure { error ->
                if (state.value.settings.cacheScope() == scope) mutableState.update {
                    it.copy(
                        notesError = true,
                        notesMessage = error.message ?: "无法读取摘抄缓存",
                        notesDiagnosticCode = null,
                        notesDiagnosticStage = null
                    )
                }
            }
        }
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { libraryService.loadCached(settings) }
            if (state.value.settings.cacheScope() == scope) mutableState.update {
                if (snapshot != null && (it.library == null || !snapshot.syncedAt.isBefore(it.library.syncedAt))) it.copy(library = snapshot) else it
            }
        }
        viewModelScope.launch {
            val learning = withContext(Dispatchers.IO) { learningStore.load(scope) }
            if (state.value.settings.cacheScope() == scope) mutableState.update {
                if (it.learningLoaded) return@update it
                val loaded = LearningMerge.merge(it.learningSnapshot(), learning)
                it.copy(
                    learningLoaded = true,
                    clippingReviewRecords = loaded.clippingReviews,
                    clippingReviewDailyCounts = learning.clippingDailyCounts + it.clippingReviewDailyCounts,
                    vocabularyReviewRecords = loaded.vocabularyReviews,
                    vocabularyDefinitions = loaded.definitions
                )
            }
            refreshFavoriteWidget()
        }
        viewModelScope.launch {
            runCatching { draftMutex.withLock { withContext(Dispatchers.IO) { noteDraftStore.load(scope) } } }
                .onSuccess { drafts ->
                    if (state.value.settings.cacheScope() == scope) mutableState.update {
                        it.copy(noteDrafts = drafts, noteDraftsLoading = false, noteDraftsAvailable = true)
                    }
                }.onFailure { error ->
                    if (state.value.settings.cacheScope() == scope) mutableState.update {
                        it.copy(noteDraftsLoading = false, noteDraftsAvailable = false,
                            noteWriteError = true, noteWriteMessage = error.message ?: "无法读取本地批注草稿")
                    }
                }
        }
    }

    /**
     * Resolves the stable Kindle identity once per connection and moves the old
     * host/port namespace to it before any sync reads or writes cache data.
     * Failure is non-fatal: the legacy scope keeps working until a later attempt.
     */
    private suspend fun ensureKindleIdentity(
        settings: ConnectionSettings,
        force: Boolean = false
    ): ConnectionSettings {
        if (settings.kindleId.isNotBlank()) return settings
        return identityMutex.withLock {
            val current = state.value.settings
            val legacyScope = settings.legacyCacheScope()
            if (current.legacyCacheScope() != legacyScope) return@withLock current
            if (current.kindleId.isNotBlank()) return@withLock current
            val now = System.currentTimeMillis()
            if (!force && identityAttemptScope == legacyScope &&
                now - identityAttemptAt < IDENTITY_RETRY_MS
            ) return@withLock settings
            identityAttemptScope = legacyScope
            identityAttemptAt = now
            val identity = withContext(Dispatchers.IO) {
                runCatching { identityService.resolve(settings) }.getOrNull()
            } ?: return@withLock settings
            // The user may have switched profiles while the SSH lookup was running.
            if (state.value.settings.legacyCacheScope() != legacyScope) {
                return@withLock state.value.settings
            }
            identityAttemptScope = null
            val resolved = settings.copy(kindleId = identity)
            withContext(Dispatchers.IO) {
                DataScopeMigration.migrate(
                    getApplication(),
                    legacyScope,
                    resolved.cacheScope()
                )
                // A failed save must not abort a sync that can still use this identity now.
                runCatching {
                    store.save(resolved)
                    profiles.saveActive(resolved)
                }
            }
            updateSettings(resolved)
            resolved
        }
    }

    fun setNavigationItem(item: NavigationItem, visible: Boolean) {        val updated = NavigationOptions.update(state.value.navigationItems, item, visible)
        preferences.navigationItems = updated
        mutableState.update { it.copy(navigationItems = updated) }
    }

    fun reloadNoteDrafts() {
        if (state.value.noteDraftSaving || state.value.noteWritingId != null || state.value.noteDraftsLoading) return
        mutableState.update { it.copy(noteDraftsLoading = true, noteDraftsAvailable = false, noteWriteMessage = "", noteWriteError = false) }
        loadDeviceData(state.value.settings)
    }

    fun syncLibrary() {
        if (state.value.librarySyncing || state.value.localDataBusy ||
            state.value.libraryRepairingPath != null || state.value.libraryDeletingPath != null) return
        val settings = state.value.settings
        mutableState.update { it.copy(librarySyncing = true, libraryError = null) }
        viewModelScope.launch {
            val activeSettings = ensureKindleIdentity(settings)
            val scope = activeSettings.cacheScope()
            runCatching { withContext(Dispatchers.IO) { libraryService.sync(activeSettings) } }
                .onSuccess { snapshot ->
                    if (state.value.settings.cacheScope() == scope) mutableState.update { it.copy(library = snapshot) }
                }.onFailure { error ->
                    if (state.value.settings.cacheScope() == scope) mutableState.update {
                        it.copy(libraryError = error.message ?: "书库同步失败，已保留离线书库")
                    }
                }
            mutableState.update { it.copy(librarySyncing = false) }
        }
    }

    fun syncLearning() {
        if (state.value.learningSyncing || state.value.localDataBusy) return
        val settings = state.value.settings
        learningSyncNoticeTimer?.cancel()
        mutableState.update { it.copy(learningSyncing = true, learningMessage = "", learningError = false) }
        viewModelScope.launch {
            val activeSettings = ensureKindleIdentity(settings)
            val scope = activeSettings.cacheScope()
            runCatching {
                val local = withContext(Dispatchers.IO) { learningStore.load(scope) }
                val merged = withContext(Dispatchers.IO) {
                    learningSyncService.sync(activeSettings, local)
                }
                val current = state.value
                if (current.settings.cacheScope() != scope) return@runCatching
                // A review can finish while the SSH round trip is running. Merge the
                // current in-memory state again so the result never loses a local action.
                val finalState = LearningMerge.merge(current.learningSnapshot(), merged)
                mutableState.update {
                    it.copy(
                        clippingReviewRecords = finalState.clippingReviews,
                        clippingReviewDailyCounts = finalState.clippingDailyCounts,
                        vocabularyReviewRecords = finalState.vocabularyReviews,
                        vocabularyDefinitions = finalState.definitions
                    )
                }
                refreshFavoriteWidget()
                saveLearning(scope, finalState)
            }.onSuccess {
                if (state.value.settings.cacheScope() == scope) {
                    mutableState.update { it.copy(learningSyncing = false, learningMessage = "回顾数据已同步") }
                    learningSyncNoticeTimer = viewModelScope.launch {
                        delay(LEARNING_SYNC_NOTICE_MS)
                        mutableState.update { it.copy(learningMessage = "") }
                    }
                } else mutableState.update { it.copy(learningSyncing = false) }
            }.onFailure { error ->
                if (state.value.settings.cacheScope() == scope) mutableState.update {
                    it.copy(
                        learningSyncing = false,
                        learningMessage = error.message ?: "回顾数据同步失败，已保留本地数据",
                        learningError = true
                    )
                } else mutableState.update { it.copy(learningSyncing = false) }
            }
        }
    }

    fun repairLibraryCover(book: KindleBook) {
        if (state.value.librarySyncing || state.value.libraryRepairingPath != null ||
            state.value.libraryDeletingPath != null ||
            state.value.localDataBusy || !LibraryRules.isCoverRepairPath(book.remotePath)) return
        val settings = state.value.settings
        val scope = settings.cacheScope()
        mutableState.update {
            it.copy(libraryRepairingPath = book.remotePath, libraryRepairMessage = null, libraryRepairError = false)
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { libraryService.repairCover(settings, book.remotePath) } }
                .onSuccess { result ->
                    if (state.value.settings.cacheScope() != scope) {
                        mutableState.update { it.copy(libraryRepairingPath = null) }
                        return@onSuccess
                    }
                    // Drop the old bitmap so the shelf can decode the verified thumbnail
                    // that repairCover has just refreshed in the local cover cache.
                    if (result.coverPath != null) libraryCovers.clear()
                    mutableState.update { current ->
                        current.copy(
                            libraryRepairingPath = null,
                            libraryRepairMessage = result.message,
                            libraryRepairError = false,
                            libraryCoverRevision = current.libraryCoverRevision + 1,
                            library = current.library?.let { snapshot ->
                                if (result.coverPath == null || result.coverPath == book.coverPath) snapshot
                                else snapshot.copy(books = snapshot.books.map { existing ->
                                    if (existing.remotePath == book.remotePath) existing.copy(coverPath = result.coverPath)
                                    else existing
                                })
                            }
                        )
                    }
                }.onFailure { error ->
                    if (state.value.settings.cacheScope() == scope) mutableState.update {
                        it.copy(
                            libraryRepairingPath = null,
                            libraryRepairMessage = error.message ?: "封面修复失败",
                            libraryRepairError = true
                        )
                    } else mutableState.update { it.copy(libraryRepairingPath = null) }
                }
        }
    }

    fun dismissLibraryRepairMessage() {
        mutableState.update { it.copy(libraryRepairMessage = null, libraryRepairError = false) }
    }

    fun deleteLibraryBook(book: KindleBook, includingReadingData: Boolean) {
        if (state.value.librarySyncing || state.value.libraryRepairingPath != null ||
            state.value.libraryDeletingPath != null ||
            state.value.localDataBusy || !LibraryRules.isBookPath(book.remotePath)) return
        val settings = state.value.settings
        val scope = settings.cacheScope()
        mutableState.update {
            it.copy(
                libraryDeletingPath = book.remotePath,
                libraryDeleteMessage = null,
                libraryDeleteError = false
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    libraryService.deleteBook(settings, book.remotePath, includingReadingData)
                }
            }.onSuccess {
                if (state.value.settings.cacheScope() != scope) {
                    mutableState.update { it.copy(libraryDeletingPath = null) }
                    return@onSuccess
                }
                mutableState.update { current ->
                    current.copy(
                        libraryDeletingPath = null,
                        libraryDeleteMessage = if (includingReadingData) {
                            "已从 Kindle 删除“${book.title}”，阅读记录已同时删除。"
                        } else {
                            "已从 Kindle 删除“${book.title}”，笔记和阅读进度仍保留。"
                        },
                        libraryDeleteError = false,
                        library = current.library?.let { snapshot ->
                            snapshot.copy(books = snapshot.books.filterNot { it.remotePath == book.remotePath })
                        }
                    )
                }
            }.onFailure { error ->
                if (state.value.settings.cacheScope() == scope) mutableState.update {
                    it.copy(
                        libraryDeletingPath = null,
                        libraryDeleteMessage = error.message ?: "书籍删除失败",
                        libraryDeleteError = true
                    )
                } else mutableState.update { it.copy(libraryDeletingPath = null) }
            }
        }
    }

    fun dismissLibraryDeleteMessage() {
        mutableState.update { it.copy(libraryDeleteMessage = null, libraryDeleteError = false) }
    }

    private fun UiState.learningSnapshot(): LearningState = LearningState(
        clippingReviews = clippingReviewRecords,
        clippingDailyCounts = clippingReviewDailyCounts,
        vocabularyReviews = vocabularyReviewRecords,
        definitions = vocabularyDefinitions
    )

    private fun persistLearning(scope: String = state.value.settings.cacheScope()) {
        val fallback = state.value.learningSnapshot()
        viewModelScope.launch(Dispatchers.IO) {
            learningSaveMutex.withLock {
                // If this Kindle is still active, save whatever the UI holds when the
                // write actually runs. That prevents an older queued snapshot from
                // overwriting a review or a sync result that landed in the meantime.
                val snapshot = state.value.takeIf { it.settings.cacheScope() == scope }
                    ?.learningSnapshot() ?: fallback
                runCatching { learningStore.save(scope, snapshot) }
            }
        }
    }

    private suspend fun saveLearning(scope: String, fallback: LearningState) {
        withContext(Dispatchers.IO) {
            learningSaveMutex.withLock {
                val snapshot = state.value.takeIf { it.settings.cacheScope() == scope }
                    ?.learningSnapshot() ?: fallback
                learningStore.save(scope, snapshot)
            }
        }
    }

    /**
     * Mirrors the current favorites into the widget's private file. Building the
     * list inside the lock makes the newest state win when several review actions
     * or sync results land close together.
     */
    private fun refreshFavoriteWidget() {
        viewModelScope.launch(Dispatchers.IO) {
            synchronized(favoriteWidgetLock) {
                val current = state.value
                val notes = widgetFavorites(current.notes, current.clippingReviewRecords)
                runCatching {
                    if (favoriteWidgetStore.save(notes)) {
                        FavoriteWidgetProvider.updateAll(getApplication())
                    }
                }
            }
        }
    }

    internal fun setFavoriteWidgetStyle(style: FavoriteWidgetStyle) {
        if (favoriteWidgetStore.style == style) return
        favoriteWidgetStore.style = style
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { FavoriteWidgetProvider.updateAll(getApplication()) }
        }
    }

    fun presentClippingReview() {
        clippingReviewUndoTimer?.cancel()
        displayedReviewIds.clear()
        val current = state.value
        val excluded = current.clippingReviewRecords.values.filter { it.isExcluded }
            .mapTo(mutableSetOf()) { it.clippingId }
        val deck = ClippingsReviewPlanner.makeDeck(
            current.notes, excluded, current.clippingReviewRecords.mapValues { it.value.reviewCount }
        )
        mutableState.update {
            it.copy(
                clippingReviewPresented = true,
                clippingReviewDeck = deck,
                clippingReviewRevision = it.clippingReviewRevision + 1,
                clippingReviewUndo = null
            )
        }
    }

    fun dismissClippingReview() {
        clippingReviewUndoTimer?.cancel()
        displayedReviewIds.clear()
        mutableState.update {
            it.copy(clippingReviewPresented = false, clippingReviewDeck = emptyList(), clippingReviewUndo = null)
        }
    }

    fun markClippingReviewDisplayed(clippingId: String) {
        val current = state.value
        if (!current.clippingReviewPresented || current.clippingReviewDeck.firstOrNull() != clippingId) return
        if (!displayedReviewIds.add(clippingId)) return
        val now = System.currentTimeMillis()
        val previous = (current.clippingReviewRecords[clippingId] ?: ClippingReviewRecord(clippingId))
            .let { if (it.origin.isBlank()) it.copy(origin = phoneId) else it }
        val updated = previous.copy(reviewCount = previous.reviewCount + 1, lastReviewedAt = now)
        val day = ClippingsReviewPlanner.reviewDayKey(now)
        val counts = ClippingsReviewPlanner.pruneDailyCounts(
            current.clippingReviewDailyCounts + (day to (current.clippingReviewDailyCounts[day] ?: 0) + 1),
            now
        )
        mutableState.update {
            it.copy(
                clippingReviewRecords = it.clippingReviewRecords + (clippingId to updated),
                clippingReviewDailyCounts = counts
            )
        }
        persistLearning()
    }

    fun focusClippingReview(clippingId: String) {
        val current = state.value
        if (!current.clippingReviewPresented || clippingId == current.clippingReviewDeck.firstOrNull() ||
            !current.isClippingInReviewBook(clippingId)) return
        mutableState.update {
            it.copy(
                clippingReviewDeck = listOf(clippingId) + it.clippingReviewDeck.filterNot { id -> id == clippingId },
                clippingReviewRevision = it.clippingReviewRevision + 1
            )
        }
    }

    private fun UiState.isClippingInReviewBook(clippingId: String): Boolean {
        val anchorId = clippingReviewDeck.firstOrNull() ?: return false
        val bookId = notes.firstOrNull { it.id == anchorId }?.bookId ?: return false
        return notes.any { it.id == clippingId && it.bookId == bookId && ClippingsReviewPlanner.isEligible(it) }
    }

    fun performClippingReviewAction(action: ClippingReviewAction, clippingId: String, inBook: Boolean) {
        val current = state.value
        if (!current.clippingReviewPresented) return
        if (inBook) {
            if (action == ClippingReviewAction.NEXT || !current.isClippingInReviewBook(clippingId)) return
        } else if (current.clippingReviewDeck.firstOrNull() != clippingId) return
        val now = System.currentTimeMillis()
        val previous = current.clippingReviewRecords[clippingId]
        val record = previous ?: ClippingReviewRecord(clippingId)
        val records = when (action) {
            ClippingReviewAction.NEXT -> current.clippingReviewRecords
            ClippingReviewAction.FAVORITE -> current.clippingReviewRecords +
                (clippingId to record.withFavorite(!inBook || !record.isFavorite, now, phoneId))
            ClippingReviewAction.EXCLUDE -> current.clippingReviewRecords +
                (clippingId to record.withExcluded(!inBook || !record.isExcluded, now, phoneId))
        }

        var deck = if (inBook) current.clippingReviewDeck else {
            current.clippingReviewDeck.drop(1).filter { records[it]?.isExcluded != true }
        }
        if (deck.isEmpty()) {
            val excluded = records.values.filter { it.isExcluded }.mapTo(mutableSetOf()) { it.clippingId }
            deck = ClippingsReviewPlanner.makeDeck(
                current.notes, excluded, records.mapValues { it.value.reviewCount }
            )
            if (deck.firstOrNull() == clippingId && deck.size > 1) {
                val index = deck.indexOfFirst { it != clippingId }
                if (index > 0) deck = deck.toMutableList().also {
                    val first = it[0]
                    it[0] = it[index]
                    it[index] = first
                }
            }
            displayedReviewIds.clear()
        }

        val undo = if (action == ClippingReviewAction.NEXT) null else ClippingReviewUndo(
            id = System.nanoTime(),
            clippingId = clippingId,
            action = action,
            previous = previous,
            expiresAt = now + 3_000
        )
        mutableState.update {
            it.copy(
                clippingReviewRecords = records,
                clippingReviewDeck = deck,
                clippingReviewRevision = if (inBook) it.clippingReviewRevision else it.clippingReviewRevision + 1,
                clippingReviewUndo = undo ?: it.clippingReviewUndo
            )
        }
        if (undo != null) {
            clippingReviewUndoTimer?.cancel()
            clippingReviewUndoTimer = viewModelScope.launch {
                delay(3_000)
                mutableState.update {
                    if (it.clippingReviewUndo?.id == undo.id) it.copy(clippingReviewUndo = null) else it
                }
            }
        }
        persistLearning()
        if (action == ClippingReviewAction.FAVORITE) refreshFavoriteWidget()
    }

    fun undoClippingReviewAction(keepBook: Boolean): Boolean {
        val current = state.value
        val undo = current.clippingReviewUndo ?: return false
        val now = System.currentTimeMillis()
        if (undo.expiresAt <= now) {
            mutableState.update { it.copy(clippingReviewUndo = null) }
            return false
        }
        clippingReviewUndoTimer?.cancel()
        val base = current.clippingReviewRecords[undo.clippingId] ?: ClippingReviewRecord(undo.clippingId)
        val restored = when (undo.action) {
            ClippingReviewAction.FAVORITE -> base.withFavorite(undo.previous?.isFavorite == true, now, phoneId)
            ClippingReviewAction.EXCLUDE -> base.withExcluded(undo.previous?.isExcluded == true, now, phoneId)
            ClippingReviewAction.NEXT -> base
        }
        val returnToCard = !keepBook && current.notes.any {
            it.id == undo.clippingId && ClippingsReviewPlanner.isEligible(it)
        }
        if (returnToCard) displayedReviewIds.add(undo.clippingId)
        mutableState.update {
            it.copy(
                clippingReviewRecords = it.clippingReviewRecords + (undo.clippingId to restored),
                clippingReviewDeck = if (returnToCard) {
                    listOf(undo.clippingId) + it.clippingReviewDeck.filterNot { id -> id == undo.clippingId }
                } else it.clippingReviewDeck,
                clippingReviewRevision = if (keepBook) it.clippingReviewRevision else it.clippingReviewRevision + 1,
                clippingReviewUndo = null
            )
        }
        persistLearning()
        if (undo.action == ClippingReviewAction.FAVORITE) refreshFavoriteWidget()
        return true
    }

    fun removeClippingReviewFavorite(clippingId: String) {
        val current = state.value
        val record = current.clippingReviewRecords[clippingId]?.takeIf { it.isFavorite } ?: return
        if (current.localDataBusy) return
        val updated = record.withFavorite(false, System.currentTimeMillis(), phoneId)
        mutableState.update { it.copy(clippingReviewRecords = it.clippingReviewRecords + (clippingId to updated)) }
        persistLearning()
        refreshFavoriteWidget()
    }

    fun restoreExcludedReviewClipping(clippingId: String) {
        val current = state.value
        val record = current.clippingReviewRecords[clippingId]?.takeIf { it.isExcluded } ?: return
        if (current.localDataBusy) return
        val updated = record.withExcluded(false, System.currentTimeMillis(), phoneId)
        mutableState.update { it.copy(clippingReviewRecords = it.clippingReviewRecords + (clippingId to updated)) }
        persistLearning()
    }

    fun restoreExcludedReviewClippings() {
        val current = state.value
        val now = System.currentTimeMillis()
        val records = current.clippingReviewRecords.mapValues { (_, record) ->
            if (record.isExcluded) {
                record.withExcluded(false, now, phoneId)
            } else record
        }
        val deck = ClippingsReviewPlanner.makeDeck(
            current.notes, emptySet(), records.mapValues { it.value.reviewCount }
        )
        displayedReviewIds.clear()
        mutableState.update {
            it.copy(
                clippingReviewRecords = records,
                clippingReviewDeck = deck,
                clippingReviewRevision = it.clippingReviewRevision + 1,
                clippingReviewUndo = null,
                clippingReviewPresented = true
            )
        }
        persistLearning()
    }

    fun vocabularyReviewQueue(now: Long = System.currentTimeMillis()): VocabularyReviewQueue = with(state.value) {
        VocabularyReviewPlanner.makeQueue(
            vocabularyWords.map { it.key },
            vocabularyReviewRecords,
            vocabularyReviewSkippedKeys,
            now
        )
    }

    fun vocabularyReviewSkippedCount(): Int = vocabularyReviewSkippedKeys.size

    fun presentVocabularyReview() {
        val current = state.value
        if (!current.learningLoaded || current.vocabularyWords.isEmpty() || current.vocabularyReviewPresented) return
        vocabularyReviewSkippedKeys.clear()
        val next = vocabularyReviewQueue().draw()
        mutableState.update {
            it.copy(
                vocabularyReviewPresented = true,
                vocabularyReviewKey = next,
                vocabularyReviewUndo = null
            )
        }
    }

    fun refreshVocabularyReview(now: Long = System.currentTimeMillis()) {
        val current = state.value
        if (!current.vocabularyReviewPresented || current.vocabularyReviewKey != null) return
        val next = vocabularyReviewQueue(now).draw() ?: return
        mutableState.update { it.copy(vocabularyReviewKey = next) }
    }

    fun dismissVocabularyReview() {
        vocabularyReviewSkippedKeys.clear()
        mutableState.update {
            it.copy(
                vocabularyReviewPresented = false,
                vocabularyReviewKey = null,
                vocabularyReviewUndo = null
            )
        }
    }

    fun performVocabularyReview(
        result: VocabularyReviewResult,
        wordKey: String,
        now: Long = System.currentTimeMillis()
    ) {
        val current = state.value
        if (current.vocabularyReviewKey != wordKey) return
        val previous = current.vocabularyReviewRecords[wordKey]
        val updated = VocabularyReviewPlanner.updatedRecord(wordKey, previous, result, now)
        val stamped = if (result == VocabularyReviewResult.SKIPPED) null else updated?.copy(
            updatedAt = now, origin = phoneId
        )
        val records = if (stamped == null) current.vocabularyReviewRecords
        else current.vocabularyReviewRecords + (wordKey to stamped)
        if (result == VocabularyReviewResult.SKIPPED) vocabularyReviewSkippedKeys.add(wordKey)
        // Build from the just-updated records; state still holds the old card.
        val next = VocabularyReviewPlanner.makeQueue(
            current.vocabularyWords.map { it.key },
            records,
            vocabularyReviewSkippedKeys,
            now
        ).draw()
        mutableState.update {
            it.copy(
                vocabularyReviewRecords = records,
                vocabularyReviewKey = next,
                vocabularyReviewUndo = VocabularyReviewUndo(wordKey, previous)
            )
        }
        persistLearning()
    }

    fun undoVocabularyReview() {
        val undo = state.value.vocabularyReviewUndo ?: return
        vocabularyReviewSkippedKeys.remove(undo.wordKey)
        val restored = (undo.previous ?: VocabularyReviewRecord(undo.wordKey)).copy(
            updatedAt = System.currentTimeMillis(), origin = phoneId
        )
        val records = state.value.vocabularyReviewRecords + (undo.wordKey to restored)
        mutableState.update {
            it.copy(
                vocabularyReviewRecords = records,
                vocabularyReviewKey = undo.wordKey,
                vocabularyReviewUndo = null
            )
        }
        persistLearning()
    }

    fun saveVocabularyDefinition(wordKey: String, definition: String) {
        val trimmed = definition.trim()
        val existing = state.value.vocabularyDefinitions[wordKey] ?: VocabularyDefinition()
        // Keep an empty record as a tombstone so another phone's old definition
        // cannot be resurrected by the next merge.
        val updated = existing.copy(
            definition = trimmed,
            updatedAt = System.currentTimeMillis(),
            origin = phoneId
        )
        mutableState.update {
            it.copy(vocabularyDefinitions = it.vocabularyDefinitions + (wordKey to updated))
        }
        persistLearning()
    }

    fun saveNoteDraft(note: KindleClipping, text: String) {
        if (!state.value.noteDraftsAvailable || state.value.localDataBusy) return
        if (note.kind != ClippingKind.HIGHLIGHT) return
        val scope = state.value.settings.cacheScope()
        val writeInFlight = state.value.noteWritingId == note.id
        pendingDraftSaves++
        mutableState.update { it.copy(noteDraftSaving = true, noteWriteMessage = "", noteWriteError = false) }
        viewModelScope.launch {
            runCatching {
                draftMutex.withLock {
                    withContext(Dispatchers.IO) {
                        val drafts = noteDraftStore.load(scope).toMutableMap()
                        val kindleTexts = note.kindleNotes.map { normalizedNoteText(it.text) }.distinct()
                        val baseline = if (kindleTexts.isEmpty()) "" else kindleTexts.singleOrNull()
                        val staged = stageNoteDraft(
                            note.id, drafts[note.id], text, note.kindleNotes.isNotEmpty(), writeInFlight, baseline
                        )
                        if (staged == null) drafts.remove(note.id) else drafts[note.id] = staged
                        noteDraftStore.save(scope, drafts)
                        drafts.toMap()
                    }
                }
            }.onSuccess { drafts ->
                if (state.value.settings.cacheScope() == scope) mutableState.update {
                    it.copy(noteDrafts = drafts, noteWriteMessage = "", noteWriteError = false,
                        noteConflicts = it.noteConflicts.filterValues { conflict ->
                            drafts[conflict.clippingId]?.revision == conflict.revision
                        })
                }
            }.onFailure { error ->
                if (state.value.settings.cacheScope() == scope) mutableState.update {
                    it.copy(noteWriteError = true, noteWriteMessage = error.message ?: "无法保存本地批注")
                }
            }
            pendingDraftSaves--
            mutableState.update { it.copy(noteDraftSaving = pendingDraftSaves > 0) }
        }
    }

    fun discardNoteDraft(note: KindleClipping) {
        if (state.value.noteDraftSaving || state.value.noteWritingId != null || !state.value.noteDraftsAvailable) return
        val scope = state.value.settings.cacheScope()
        mutableState.update { it.copy(noteDraftSaving = true) }
        viewModelScope.launch {
            runCatching {
                draftMutex.withLock {
                    withContext(Dispatchers.IO) {
                        val drafts = noteDraftStore.load(scope).toMutableMap()
                        val draft = drafts[note.id]
                        val baseline = draft?.takeIf { it.syncedRevision != null }?.kindleBaseText
                        if (draft != null && baseline != null) drafts[note.id] = draft.acceptKindle(baseline)
                        else drafts.remove(note.id)
                        noteDraftStore.save(scope, drafts)
                        drafts
                    }
                }
            }.onSuccess { drafts ->
                if (state.value.settings.cacheScope() == scope) mutableState.update {
                    it.copy(noteDrafts = drafts, noteWriteMessage = "已恢复同步时的 Kindle 批注", noteWriteError = false,
                        noteConflicts = it.noteConflicts - note.id)
                }
            }.onFailure { error ->
                if (state.value.settings.cacheScope() == scope) mutableState.update {
                    it.copy(noteWriteError = true, noteWriteMessage = error.message ?: "无法恢复原批注")
                }
            }
            mutableState.update { it.copy(noteDraftSaving = false) }
        }
    }

    fun sendNoteToKindle(note: KindleClipping) = syncNoteDrafts(listOf(note.id))

    fun syncPendingNoteDrafts() = syncNoteDrafts(
        state.value.noteDrafts.values.filter(NoteDraft::needsSync).map(NoteDraft::clippingId)
    )

    fun reviewNoteConflicts(open: Boolean) {
        mutableState.update { it.copy(noteConflictReviewOpen = open) }
    }

    fun resolveNoteConflict(conflict: NoteConflict, keepPhone: Boolean) {
        if (state.value.noteConflicts[conflict.clippingId] != conflict) return
        syncNoteDrafts(listOf(conflict.clippingId), NoteConflictChoice(conflict, keepPhone))
    }

    private fun syncNoteDrafts(ids: List<String>, choice: NoteConflictChoice? = null) {
        val current = state.value
        if (current.notesSyncing || current.noteWritingId != null || current.noteDraftSaving ||
            !current.noteDraftsAvailable || current.localDataBusy || current.connectionBusy
        ) return
        val drafts = ids.mapNotNull { current.noteDrafts[it]?.takeIf(NoteDraft::needsSync) }
        if (drafts.isEmpty()) return
        if (choice != null && drafts.single().revision != choice.conflict.revision) return
        val notes = current.notes.associateBy(KindleClipping::id)
        val settings = current.settings
        val scope = settings.cacheScope()
        mutableState.update {
            it.copy(noteWritingId = drafts.first().clippingId, noteWriteMessage = "正在同步批注…", noteWriteError = false)
        }
        viewModelScope.launch {
            var accepted = 0
            var failures = 0
            var firstError: String? = null
            var cacheRefreshFailed = false
            var connection: NoteWritebackConnection? = null
            try {
                for (draft in drafts) {
                    if (state.value.settings.cacheScope() != scope) break
                    val id = draft.clippingId
                    if (state.value.noteDrafts[id]?.revision != draft.revision) continue
                    mutableState.update { it.copy(noteWritingId = id) }
                    try {
                        val note = notes[id] ?: error("找不到这条草稿对应的划线")
                        val result = withContext(Dispatchers.IO) {
                            val active = connection ?: writebackService.connect(settings).also { connection = it }
                            writebackService.syncDraft(active, note, draft, choice)
                        }
                        when (result) {
                            is NoteDraftSyncResult.Conflict -> mutableState.update {
                                if (it.settings.cacheScope() == scope && it.noteDrafts[id]?.revision == draft.revision)
                                    it.copy(noteConflicts = it.noteConflicts + (id to result.conflict))
                                else it
                            }
                            is NoteDraftSyncResult.Accepted -> {
                                val latestDrafts = draftMutex.withLock {
                                    withContext(Dispatchers.IO) {
                                        val latest = noteDraftStore.load(scope).toMutableMap()
                                        val saved = latest[id]
                                        if (saved?.revision == draft.revision) {
                                            latest[id] = saved.acceptKindle(result.text)
                                        } else if (saved != null && result.written != null) {
                                            latest[id] = saved.copy(kindleBaseText = result.text)
                                        }
                                        noteDraftStore.save(scope, latest)
                                        latest.toMap()
                                    }
                                }
                                if (state.value.settings.cacheScope() == scope) mutableState.update {
                                    it.copy(noteDrafts = latestDrafts, noteConflicts = it.noteConflicts - id)
                                }
                                result.written?.let {
                                    try {
                                        val snapshot = withContext(Dispatchers.IO) {
                                            clippingsService.importWrittenBytes(settings, it.clippingsBytes)
                                        }
                                        if (state.value.settings.cacheScope() == scope) applyNotesSnapshot(snapshot)
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        cacheRefreshFailed = true
                                    }
                                }
                                accepted++
                            }
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failures++
                        if (firstError == null) firstError = error.message ?: "无法同步这条批注"
                        withContext(Dispatchers.IO) { runCatching { connection?.close() } }
                        connection = null
                    }
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    runCatching { connection?.close() }
                }
                mutableState.update {
                    if (it.settings.cacheScope() != scope) it.copy(noteWritingId = null)
                    else it.copy(
                        noteWritingId = null,
                        noteConflictReviewOpen = it.noteConflicts.isNotEmpty(),
                        noteWriteError = failures > 0 || cacheRefreshFailed,
                        noteWriteMessage = buildList {
                            if (accepted > 0) add("已同步 ${accepted} 条")
                            if (it.noteConflicts.isNotEmpty()) add("${it.noteConflicts.size} 条待确认")
                            if (failures > 0) add("${failures} 条同步失败：$firstError")
                            if (cacheRefreshFailed) add("本机摘抄缓存更新失败，请重新同步摘抄")
                        }.joinToString("\n")
                    )
                }
            }
        }
    }

    fun save() {
        runCatching { store.save(state.value.settings); profiles.saveActive(state.value.settings) }.fold(
            onSuccess = {
                remoteDispatcher.connectionSettingsChanged()
                mutableState.update { it.copy(status = "设置已安全保存", isError = false) }
            },
            onFailure = { error ->
                mutableState.update { it.copy(status = error.message ?: "无法保存设置", isError = true) }
            }
        )
    }

    fun testConnection() = connectKindle()

    fun connectKindle() {
        val current = state.value
        if (current.connectionBusy || current.localDataBusy) return
        val settings = current.settings
        mutableState.update { it.copy(connectionBusy = true, connectionMessage = "正在连接 Kindle…", connectionError = false, status = "正在连接 Kindle…", isError = false) }
        viewModelScope.launch {
            val activeSettings = ensureKindleIdentity(settings, force = true)
            val scope = activeSettings.cacheScope()
            runCatching { withContext(Dispatchers.IO) {
                store.save(activeSettings); profiles.saveActive(activeSettings)
                ssh.test(activeSettings)
                val event = activeSettings.eventDevice.ifBlank { runCatching { ssh.detectTouchDevice(activeSettings) }.getOrDefault("") }
                activeSettings.copy(eventDevice = event).also { store.save(it); profiles.saveActive(it) }
            } }.onSuccess { resolved ->
                if (state.value.settings.cacheScope() == scope) {
                    mutableState.update { it.copy(settings = resolved, connected = true, connectionMessage = "已连接", connectionError = false, status = "已连接", isError = false) }
                    remoteDispatcher.connectionSettingsChanged()
                    setRemoteControlEnabled(true)
                    checkFlipCommands()
                    syncLearning()
                }
            }.onFailure { error ->
                if (state.value.settings.cacheScope() == scope) mutableState.update { it.copy(connected = false, connectionMessage = error.message ?: "连接失败", connectionError = true, status = error.message ?: "连接失败", isError = true) }
            }
            mutableState.update { it.copy(connectionBusy = false) }
        }
    }

    fun refreshLibraryFromKindle() { syncNotes(); syncVocabulary(); syncLibrary(); syncLearning() }
    fun refreshRemoteControl() { refreshXiaomiBand(); refreshRemoteRuntimeStatus(); connectKindle() }
    fun showNotesBookPicker() { mutableState.update { it.copy(notesBookPickerVisible = true) } }
    fun hideNotesBookPicker() { mutableState.update { it.copy(notesBookPickerVisible = false) } }

    fun renameProfile(name: String) {
        profiles.rename(name)
        mutableState.update { it.copy(profiles = profiles.all()) }
    }
    fun selectProfile(id: String) {
        if (state.value.connectionBusy || state.value.localDataBusy) return
        profiles.select(id)
        activateProfile()
    }
    fun addProfile() { if (!state.value.connectionBusy) { profiles.add(); activateProfile() } }
    fun removeProfile() { if (!state.value.connectionBusy) { profiles.remove(); activateProfile() } }
    private fun activateProfile() {
        setRemoteControlEnabled(false)
        val settings = profiles.settings()
        if (settings.host.isNotBlank()) store.save(settings) else store.clear()
        updateSettings(settings)
        mutableState.update { it.copy(profiles = profiles.all(), activeProfileId = profiles.activeId, connected = false, connectionMessage = "未连接", connectionError = false, status = "未连接", isError = false) }
        remoteDispatcher.connectionSettingsChanged()
    }
    fun forgetHostKeys() {
        runCatching { com.hongxiang.kpilot.ssh.KindleSshSessionFactory.knownHostsFile?.let { if (it.exists()) check(it.delete()) } }
            .onSuccess { mutableState.update { it.copy(connectionMessage = "已清除", connectionError = false) } }
            .onFailure { error -> mutableState.update { it.copy(status = error.message ?: "操作失败", isError = true) } }
    }
    fun importLocalFile(uri: Uri, vocabulary: Boolean) = performLocalAction {
        val settings = state.value.settings
        val bytes = localData.readImport(uri)
        if (vocabulary) {
            val snapshot = vocabularyService.importBytes(settings,bytes)
            applyVocabularySnapshot(settings.cacheScope(), snapshot)
        } else {
            val snapshot = clippingsService.importWrittenBytes(settings,bytes)
            if (state.value.settings.cacheScope() == settings.cacheScope()) applyNotesSnapshot(snapshot)
        }
        "已导入"
    }
    fun exportLocalBackup(uri: Uri) = performLocalAction { localData.exportBackup(uri); "已导出" }
    fun clearLocalData() {
        if (state.value.notesSyncing || state.value.vocabularySyncing || state.value.librarySyncing || state.value.noteWritingId != null || state.value.noteDraftSaving || state.value.localDataBusy || state.value.connectionBusy || state.value.busy) return
        setRemoteControlEnabled(false); setXiaomiBandEnabled(false)
        performLocalAction {
            localData.clear(); store.clear(); profiles.reset()
            libraryCovers.clear()
            preferences.navigationItems = NavigationOptions.defaults
            mutableState.value = UiState(profiles = profiles.all(), activeProfileId = profiles.activeId,
                navigationItems = NavigationOptions.defaults, noteDraftsLoading = false, noteDraftsAvailable = true,
                learningLoaded = true)
            synchronized(favoriteWidgetLock) {
                favoriteWidgetStore.clear()
                FavoriteWidgetProvider.updateAll(getApplication())
            }
            "已清除"
        }
    }
    private fun performLocalAction(action: () -> String) {
        if (state.value.localDataBusy || state.value.noteDraftSaving || state.value.notesSyncing || state.value.vocabularySyncing || state.value.librarySyncing || state.value.noteWritingId != null || state.value.connectionBusy) return
        mutableState.update { it.copy(localDataBusy = true, localDataMessage = "", localDataError = false) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onSuccess { message -> mutableState.update { it.copy(localDataMessage = message, localDataError = false) } }
                .onFailure { error -> mutableState.update { it.copy(localDataMessage = error.message ?: "操作失败", localDataError = true) } }
            mutableState.update { it.copy(localDataBusy = false) }
        }
    }

    fun detectTouchDevice() {
        val settings = state.value.settings
        mutableState.update { it.copy(busy = true, status = "正在检测触控设备…", isError = false) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { ssh.detectTouchDevice(settings) } }.fold(
                onSuccess = { event ->
                    val resolved = settings.copy(eventDevice = event)
                    runCatching { store.save(resolved) }.onSuccess { remoteDispatcher.connectionSettingsChanged() }
                    mutableState.update {
                        it.copy(settings = resolved, busy = false, status = "已检测到 /dev/input/$event", isError = false)
                    }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(busy = false, status = error.message ?: "未检测到触控设备", isError = true)
                    }
                }
            )
        }
    }

    fun setVolumeKeysEnabled(enabled: Boolean) {
        preferences.volumeKeysEnabled = enabled
        ScreenOffRemoteService.sync(getApplication())
        mutableState.update {
            it.copy(
                volumeKeysEnabled = enabled,
                status = if (enabled) "音量键翻页已开启" else "音量键恢复系统音量"
            )
        }
    }

    fun setKeyboardKeysEnabled(enabled: Boolean) {
        preferences.keyboardKeysEnabled = enabled
        ScreenOffRemoteService.sync(getApplication())
        mutableState.update {
            it.copy(
                keyboardKeysEnabled = enabled,
                status = if (enabled) "蓝牙键盘翻页已开启" else "蓝牙键盘方向键已恢复"
            )
        }
    }

    fun setRemoteControlEnabled(enabled: Boolean) {
        preferences.remoteControlEnabled = enabled
        ScreenOffRemoteService.sync(getApplication())
        remoteDispatcher.remoteSettingChanged(enabled)
        mutableState.update { it.copy(remoteControlEnabled = enabled) }
    }

    fun refreshRemoteRuntimeStatus() {
        ScreenOffRemoteService.sync(getApplication())
        mutableState.update {
            it.copy(
                accessibilityServiceEnabled = AccessibilityStatus.isRemoteServiceEnabled(getApplication())
            )
        }
    }

    fun setXiaomiBandEnabled(enabled: Boolean) {
        xiaomiBandController.setEnabled(enabled)
    }

    fun refreshXiaomiBand() {
        xiaomiBandController.refresh()
    }

    fun reconnectXiaomiBand() {
        xiaomiBandController.reconnect()
    }

    override fun submit(command: KindleCommand) {
        remoteDispatcher.submit(command)
    }

    fun checkFlipCommands() {
        remoteDispatcher.checkFlipCommands()
    }

    fun recordFlipCommand(definition: FlipCommandDefinition) {
        remoteDispatcher.recordFlipCommand(definition)
    }

    fun addTransferUris(uris: List<Uri>) {
        val additions = uris.mapNotNull(::readTransferItem)
        val rejectedCount = uris.size - additions.size
        mutableState.update { current ->
            val existingUris = current.transfers.map { it.uri }.toSet()
            val unique = additions.filterNot { it.uri in existingUris }
            current.copy(
                transfers = current.transfers + unique,
                transferMessage = when {
                    rejectedCount > 0 -> "已跳过 $rejectedCount 个不支持的文件"
                    unique.isEmpty() -> "没有新增可发送的文件"
                    else -> "${unique.size} 个文件等待发送"
                }
            )
        }
    }

    fun sendReadyTransfers() {
        val ready = state.value.transfers.filter { it.status == TransferStatus.READY }
        if (ready.isEmpty()) {
            mutableState.update { it.copy(transferMessage = "请先选择文件或重试失败项目") }
            return
        }
        ready.forEach { transferQueue.trySend(it.id) }
        mutableState.update { it.copy(transferMessage = "正在依次发送 ${ready.size} 个文件") }
    }

    fun removeTransfer(id: String) {
        val item = state.value.transfers.firstOrNull { it.id == id } ?: return
        if (!TransferQueueRules.canRemove(item.status)) return
        mutableState.update { it.copy(transfers = it.transfers.filterNot { row -> row.id == id }) }
    }

    fun retryTransfer(id: String) {
        val item = state.value.transfers.firstOrNull { it.id == id } ?: return
        if (!TransferQueueRules.canRetry(item.status)) return
        updateTransfer(id) { TransferQueueRules.retry(it) }
        transferQueue.trySend(id)
    }

    fun resolveCollision(decision: CollisionDecision) {
        collisionDecision?.complete(decision)
    }

    private suspend fun processTransfer(id: String) {
        val item = state.value.transfers.firstOrNull { it.id == id } ?: return
        if (item.status != TransferStatus.READY) return
        updateTransfer(id) { it.copy(status = TransferStatus.CHECKING, error = null) }
        runCatching {
            val settings = state.value.settings
            val existing = withContext(Dispatchers.IO) { transferService.listRemoteDocuments(settings) }
            val matches = RemoteDocumentNaming.collisions(item.displayName, existing)
            var allowOverwrite = false
            if (matches.isNotEmpty()) {
                val deferred = CompletableDeferred<CollisionDecision>()
                collisionDecision = deferred
                mutableState.update {
                    it.copy(
                        collision = TransferCollision(id, item.displayName, matches),
                        transferMessage = "Kindle 中已有匹配的书籍"
                    )
                }
                updateTransfer(id) { it.copy(status = TransferStatus.WAITING_FOR_COLLISION) }
                val decision = deferred.await()
                collisionDecision = null
                mutableState.update { it.copy(collision = null) }
                if (decision == CollisionDecision.SKIP) {
                    updateTransfer(id) { it.copy(status = TransferStatus.SKIPPED, error = "已由用户跳过") }
                    mutableState.update { it.copy(transferMessage = "已跳过 ${item.displayName}") }
                    return
                }
                allowOverwrite = true
            }
            updateTransfer(id) { it.copy(status = TransferStatus.UPLOADING, bytesSent = 0) }
            val remotePath = withContext(Dispatchers.IO) {
                transferService.upload(settings, item.uri, item.displayName, allowOverwrite) { sent ->
                    mutableState.update { current ->
                        current.copy(transfers = current.transfers.map { row ->
                            if (row.id == id) row.copy(bytesSent = sent) else row
                        })
                    }
                }
            }
            updateTransfer(id) { it.copy(status = TransferStatus.COMPLETED, remotePath = remotePath, error = null) }
            mutableState.update { it.copy(transferMessage = "${item.displayName} 已发送") }
            writeUploadedCover(id, item, settings, remotePath)
        }.onFailure { error ->
            collisionDecision = null
            mutableState.update { it.copy(collision = null, transferMessage = error.message ?: "发送失败") }
            updateTransfer(id) { it.copy(status = TransferStatus.FAILED, error = error.message ?: "发送失败") }
        }
    }

    /**
     * Newly uploaded MOBI-family books get the same verified thumbnail as the shelf repair.
     * Cover errors must never turn a successful ebook upload into a failed transfer.
     */
    private suspend fun writeUploadedCover(
        id: String,
        item: TransferItem,
        settings: ConnectionSettings,
        remotePath: String
    ) {
        if (SupportedTransferTypes.extensionOf(item.displayName) !in LibraryRules.coverRepairExtensions) {
            updateTransfer(id) { it.copy(coverMessage = "此格式暂不自动写入 Kindle 封面。") }
            return
        }
        updateTransfer(id) { it.copy(coverMessage = "正在提取并写入 Kindle 封面…", coverNeedsAttention = false) }
        mutableState.update { it.copy(transferMessage = "${item.displayName} 已上传，正在补充封面…") }
        runCatching {
            val localBytes = if ((item.sizeBytes ?: 0L) > LibraryRules.MAX_COVER_BOOK_BYTES) {
                throw IllegalStateException("封面提取仅支持不超过 64 MB 的非空书籍；书籍上传不受影响。")
            } else withContext(Dispatchers.IO) { readBookBytes(item.uri) }
            val result = withContext(Dispatchers.IO) {
                libraryService.repairCover(settings, remotePath, localBytes)
            }
            updateTransfer(id) { it.copy(coverMessage = result.message, coverNeedsAttention = false) }
            val shelfContainsBook = state.value.settings.cacheScope() == settings.cacheScope() &&
                state.value.library?.books?.any { it.remotePath == remotePath } == true
            if (result.coverPath != null && shelfContainsBook) {
                libraryCovers.clear()
                mutableState.update { current ->
                    current.copy(
                        libraryCoverRevision = current.libraryCoverRevision + 1,
                        library = current.library?.let { snapshot ->
                            snapshot.copy(books = snapshot.books.map { existing ->
                                if (existing.remotePath == remotePath) existing.copy(coverPath = result.coverPath)
                                else existing
                            })
                        }
                    )
                }
            }
        }.onFailure { error ->
            updateTransfer(id) {
                it.copy(
                    coverMessage = "书籍已上传；封面未完成：${error.message ?: "未知原因"}",
                    coverNeedsAttention = true
                )
            }
        }
    }

    private fun readBookBytes(uri: Uri): ByteArray {
        val source = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取本地书籍文件")
        return source.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                check(total <= LibraryRules.MAX_COVER_BOOK_BYTES) {
                    "封面提取仅支持不超过 64 MB 的非空书籍；书籍上传不受影响。"
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun readTransferItem(uri: Uri): TransferItem? {
        var name: String? = null
        var size: Long? = null
        getApplication<Application>().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val displayName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: return null
        if (!SupportedTransferTypes.isSupported(displayName)) return null
        return TransferItem(
            uri = uri,
            displayName = runCatching { RemoteDocumentNaming.safeFileName(displayName) }.getOrNull() ?: return null,
            mimeType = getApplication<Application>().contentResolver.getType(uri),
            sizeBytes = size
        )
    }

    private fun updateTransfer(id: String, update: (TransferItem) -> TransferItem) {
        mutableState.update { current ->
            current.copy(transfers = current.transfers.map { if (it.id == id) update(it) else it })
        }
    }

    fun syncNotes() {
        if (state.value.notesSyncing || state.value.noteWritingId != null || state.value.localDataBusy) return
        val settings = state.value.settings
        mutableState.update {
            it.copy(
                notesSyncing = true,
                notesDownloaded = 0,
                notesTotal = null,
                notesMessage = "正在下载 My Clippings.txt…",
                notesError = false,
                notesDiagnosticCode = null,
                notesDiagnosticStage = null
            )
        }
        viewModelScope.launch {
            val activeSettings = ensureKindleIdentity(settings)
            val scope = activeSettings.cacheScope()
            runCatching {
                withContext(Dispatchers.IO) {
                    clippingsService.sync(activeSettings) { downloaded, total ->
                        if (state.value.settings.cacheScope() == scope) mutableState.update {
                            it.copy(notesDownloaded = downloaded, notesTotal = total)
                        }
                    }
                }
            }.fold(
                onSuccess = { snapshot ->
                    if (state.value.settings.cacheScope() != scope) {
                        mutableState.update { it.copy(notesSyncing = false) }
                        return@fold
                    }
                    val visibleSnapshot = reconcileKindleAnnotations(activeSettings, snapshot)
                    if (state.value.settings.cacheScope() != scope) {
                        mutableState.update { it.copy(notesSyncing = false) }
                        return@fold
                    }
                    applyNotesSnapshot(visibleSnapshot)
                    mutableState.update {
                        it.copy(
                            notesSyncing = false,
                            notesMessage = if (visibleSnapshot.items.isEmpty()) {
                                "没有可显示的摘抄"
                            } else {
                                "已同步 ${visibleSnapshot.items.size} 条摘抄"
                            },
                            notesError = false
                        )
                    }
                },
                onFailure = { error ->
                    if (state.value.settings.cacheScope() != scope) {
                        mutableState.update { it.copy(notesSyncing = false) }
                        return@fold
                    }
                    val diagnostic = (error as? NotesSyncException)?.diagnostic ?: NotesDiagnostics.unknown()
                    Log.w("KPilotNotes", "sync_failed code=${diagnostic.code} stage=${diagnostic.stage}")
                    mutableState.update {
                        it.copy(
                            notesSyncing = false,
                            notesMessage = diagnostic.message,
                            notesError = true,
                            notesDiagnosticCode = diagnostic.code,
                            notesDiagnosticStage = diagnostic.stage
                        )
                    }
                }
            )
        }
    }

    private suspend fun reconcileKindleAnnotations(
        settings: ConnectionSettings, snapshot: ClippingsSnapshot
    ): ClippingsSnapshot {
        val span = SyncTrace.begin("notes-annotations")
        try {
            val scope = settings.cacheScope()
            val current = state.value
            if (current.settings.cacheScope() != scope || !current.noteDraftsAvailable) return snapshot
            val synced = current.noteDrafts.filterValues { !it.needsSync }
            val clippings = snapshot.sourceHighlights
            if (clippings.isEmpty()) return snapshot
            try {
                val annotations = withContext(Dispatchers.IO) { writebackService.readCurrentAnnotations(settings, clippings) }
                span.mark("read_annotations")
                val (updated, projected) = draftMutex.withLock {
                    withContext(Dispatchers.IO) {
                        val drafts = noteDraftStore.load(scope).toMutableMap()
                        val pendingIds = drafts.values.filter { it.needsSync }.map { it.clippingId }.toSet()
                        clippingsService.rememberRemovedHighlights(
                            settings, annotations.checkedHighlights, annotations.removedHighlights - pendingIds
                        )
                        val projected = clippingsService.hideRemovedHighlights(settings, snapshot)
                        var changed = false
                        projected.items.forEach { clipping ->
                            val id = clipping.id
                            val text = annotations.notes[id] ?: return@forEach
                            val draft = drafts[id]
                            if (draft == synced[id]) {
                                val verified = rememberKindleNoteBaseline(id, draft, text, clipping.kindleNotes.isNotEmpty())
                                if (verified != null && verified != draft) {
                                    drafts[id] = verified
                                    changed = true
                                }
                            }
                        }
                        if (changed) noteDraftStore.save(scope, drafts)
                        drafts.toMap() to projected
                    }
                }
                span.mark("drafts_hide")
                mutableState.update { if (it.settings.cacheScope() == scope) it.copy(noteDrafts = updated) else it }
                return projected
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("KPilotNotes", "Cannot refresh saved Kindle notes", error)
                return withContext(Dispatchers.IO) {
                    runCatching { clippingsService.hideRemovedHighlights(settings, snapshot) }.getOrDefault(snapshot)
                }
            }
        } finally {
            span.end("highlights=${snapshot.sourceHighlights.size}")
        }
    }

    fun updateNotesQuery(query: String) {
        mutableState.update { it.copy(notesQuery = query, selectedNoteId = null) }
    }

    fun dismissReadingErrors(shown: UiState) {
        mutableState.update { current ->
            if (current.settings.cacheScope() != shown.settings.cacheScope()) return@update current
            val dismissNotes = shown.notesError && current.notesMessage == shown.notesMessage &&
                current.notesDiagnosticCode == shown.notesDiagnosticCode
            val dismissVocabulary = shown.vocabularyError && current.vocabularyMessage == shown.vocabularyMessage &&
                current.vocabularyDiagnosticCode == shown.vocabularyDiagnosticCode
            val dismissLearning = shown.learningError && current.learningError &&
                current.learningMessage == shown.learningMessage
            current.copy(
                notesError = current.notesError && !dismissNotes,
                notesDiagnosticCode = if (dismissNotes) null else current.notesDiagnosticCode,
                notesDiagnosticStage = if (dismissNotes) null else current.notesDiagnosticStage,
                vocabularyError = current.vocabularyError && !dismissVocabulary,
                vocabularyDiagnosticCode = if (dismissVocabulary) null else current.vocabularyDiagnosticCode,
                libraryError = current.libraryError?.takeUnless { it == shown.libraryError },
                learningError = current.learningError && !dismissLearning,
                learningMessage = if (dismissLearning) "" else current.learningMessage
            )
        }
    }

    fun selectNotesBook(book: String?) {
        mutableState.update { it.copy(selectedBook = book, selectedNoteId = null) }
    }

    fun selectNote(id: String?) {
        mutableState.update { it.copy(selectedNoteId = id, noteWriteMessage = "", noteWriteError = false) }
    }

    fun setNotesSortOrder(order: NotesSortOrder) {
        mutableState.update { it.copy(notesSortOrder = order) }
    }

    fun visibleNotes(): List<KindleClipping> {
        val current = state.value
        val query = current.notesQuery.trim()
        val filtered = current.notes.filter { note ->
            (current.selectedBook == null || note.bookTitle == current.selectedBook) &&
                (query.isEmpty() || (listOf(note.bookTitle, note.author.orEmpty(), note.metadata, note.text) +
                    note.kindleNotes.flatMap { listOf(it.metadata, it.text) })
                    .any { it.contains(query, ignoreCase = true) })
        }
        // 全部笔记：有且只有按时间倒序（新笔记在最上），不按书籍分组。
        if (current.selectedBook == null) {
            return filtered.sortedWith(
                compareByDescending<KindleClipping> { it.addedAt ?: Instant.MIN }.thenBy { it.id }
            )
        }
        // 单本书内：可按位置或时间排序。
        return when (current.notesSortOrder) {
            NotesSortOrder.LOCATION -> filtered.sortedWith(
                compareBy<KindleClipping> { it.locationStart ?: Int.MAX_VALUE }.thenBy { it.addedAt ?: Instant.MAX }
            )
            NotesSortOrder.ADDED_AT -> filtered.sortedWith(
                compareByDescending<KindleClipping> { it.addedAt ?: Instant.MIN }.thenBy { it.locationStart ?: Int.MAX_VALUE }
            )
        }
    }

    fun createNotesExport(format: ClippingsExportFormat): String? = runCatching {
        val visible = visibleNotes()
        require(visible.isNotEmpty()) { "没有可导出的摘抄" }
        val fileName = ClippingsExporter.fileName(state.value.selectedBook, format)
        exportStore.write(fileName, format.mimeType, ClippingsExporter.export(visible, format))
        "${DownloadExportStore.DISPLAY_PATH}/$fileName"
    }.onSuccess { path ->
        mutableState.update { it.copy(notesMessage = "已导出至 $path", notesError = false) }
    }.onFailure { error ->
        mutableState.update {
            it.copy(
                notesMessage = error.message ?: "无法创建导出文件",
                notesError = true,
                notesDiagnosticCode = null,
                notesDiagnosticStage = null
            )
        }
    }.getOrNull()

    private fun applyNotesSnapshot(snapshot: ClippingsSnapshot) {
        mutableState.update {
            it.copy(
                notes = snapshot.items,
                notesLastSync = snapshot.syncedAt,
                notesFilteredDuplicates = snapshot.filteredDuplicateCount,
                notesFilteredSingleSelections = snapshot.filteredSingleSelectionCount,
                selectedNoteId = it.selectedNoteId?.takeIf { id -> snapshot.items.any { note -> note.id == id } },
                notesMessage = if (snapshot.items.isEmpty()) "缓存中没有摘抄" else "已载入 ${snapshot.items.size} 条摘抄",
                notesError = false
            )
        }
        refreshFavoriteWidget()
    }

    fun syncVocabulary() {
        if (state.value.vocabularySyncing || state.value.localDataBusy) return
        val settings = state.value.settings
        mutableState.update {
            it.copy(
                vocabularySyncing = true,
                vocabularyDownloaded = 0,
                vocabularyTotal = null,
                vocabularyMessage = "正在下载 vocab.db…",
                vocabularyError = false,
                vocabularyDiagnosticCode = null
            )
        }
        viewModelScope.launch {
            val activeSettings = ensureKindleIdentity(settings)
            val scope = activeSettings.cacheScope()
            runCatching {
                withContext(Dispatchers.IO) {
                    vocabularyService.sync(activeSettings) { downloaded, total ->
                        if (state.value.settings.cacheScope() == scope) mutableState.update {
                            it.copy(vocabularyDownloaded = downloaded, vocabularyTotal = total)
                        }
                    }
                }
            }.fold(
                onSuccess = { snapshot ->
                    if (state.value.settings.cacheScope() != scope) { mutableState.update { it.copy(vocabularySyncing = false) }; return@fold }
                    applyVocabularySnapshot(scope, snapshot)
                    mutableState.update {
                        it.copy(
                            vocabularySyncing = false,
                            vocabularyMessage = "已同步 ${snapshot.words.size} 个生词",
                            vocabularyError = false,
                            vocabularyDiagnosticCode = null
                        )
                    }
                },
                onFailure = { error ->
                    if (state.value.settings.cacheScope() != scope) { mutableState.update { it.copy(vocabularySyncing = false) }; return@fold }
                    val diagnostic = (error as? VocabularySyncException)?.diagnostic
                    mutableState.update {
                        it.copy(
                            vocabularySyncing = false,
                            vocabularyMessage = diagnostic?.message ?: "生词同步失败，已保留上一次缓存",
                            vocabularyError = true,
                            vocabularyDiagnosticCode = diagnostic?.code
                        )
                    }
                }
            )
        }
    }

    fun updateVocabularyQuery(query: String) {
        mutableState.update { it.copy(vocabularyQuery = query, selectedVocabularyKey = null) }
    }

    fun setVocabularySortOrder(order: VocabularySortOrder) {
        mutableState.update { it.copy(vocabularySortOrder = order) }
    }

    fun selectVocabularyWord(key: String?) {
        mutableState.update { it.copy(selectedVocabularyKey = key) }
    }

    fun visibleVocabularyWords(): List<VocabularyWord> {
        val current = state.value
        val query = current.vocabularyQuery.trim()
        val definitions = current.vocabularyDefinitions
        val visible = current.vocabularyWords.filter { word ->
            query.isEmpty() || word.displayWord.contains(query, true) ||
                definitions[word.key]?.definition?.contains(query, true) == true ||
                word.dictionaryEntry?.senses?.any { it.gloss.contains(query, true) } == true || word.lookups.any {
                    listOf(it.bookTitle, it.authors, it.usage).any { field -> field.contains(query, true) }
                }
        }
        return VocabularyOrdering.sort(visible, current.vocabularySortOrder)
    }

    fun createVocabularyExport(format: VocabularyExportFormat): String? = runCatching {
        val visible = visibleVocabularyWords()
        require(visible.isNotEmpty()) { "没有可导出的生词" }
        val fileName = "Kindle_Vocabulary.${format.extension}"
        exportStore.write(fileName, format.mimeType, VocabularyExporter.export(visible, format))
        "${DownloadExportStore.DISPLAY_PATH}/$fileName"
    }.onSuccess { path ->
        mutableState.update {
            it.copy(vocabularyMessage = "已导出至 $path", vocabularyError = false)
        }
    }.onFailure { error ->
        mutableState.update {
            it.copy(
                vocabularyMessage = error.message ?: "无法创建生词导出文件",
                vocabularyError = true,
                vocabularyDiagnosticCode = null
            )
        }
    }.getOrNull()

    private fun applyVocabularySnapshot(scope: String, snapshot: VocabularySnapshot) {
        val words = snapshot.words
        mutableState.update {
            if (it.settings.cacheScope() != scope || it.vocabularyLastSync?.isAfter(snapshot.syncedAt) == true) {
                return@update it
            }
            it.copy(
                vocabularyWords = words,
                vocabularyLastSync = snapshot.syncedAt,
                selectedVocabularyKey = it.selectedVocabularyKey?.takeIf { key -> words.any { word -> word.key == key } },
                vocabularyReviewKey = it.vocabularyReviewKey?.takeIf { key -> words.any { word -> word.key == key } },
                vocabularyReviewUndo = it.vocabularyReviewUndo?.takeIf { undo -> words.any { word -> word.key == undo.wordKey } },
                vocabularyMessage = if (words.isEmpty()) "没有找到可用的生词" else "已载入 ${words.size} 个生词",
                vocabularyError = false
            )
        }
    }

    override fun onCleared() {
        libraryCovers.clear()
        super.onCleared()
    }
}
