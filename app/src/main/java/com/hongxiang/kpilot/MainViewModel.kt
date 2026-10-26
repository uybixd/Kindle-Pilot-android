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
import com.hongxiang.kpilot.library.LibraryService
import com.hongxiang.kpilot.library.LibrarySnapshot
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
import com.hongxiang.kpilot.notes.writeback.KindleNoteWritebackService
import com.hongxiang.kpilot.notes.NotesDiagnostics
import com.hongxiang.kpilot.notes.NotesSyncException
import com.hongxiang.kpilot.ssh.KindleSshClient
import com.hongxiang.kpilot.remote.AccessibilityStatus
import com.hongxiang.kpilot.remote.FlipCommandDefinition
import com.hongxiang.kpilot.remote.RemoteCommandDispatcher
import com.hongxiang.kpilot.remote.ScreenOffRemoteService
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    val noteDrafts: Map<String, NoteDraft> = emptyMap(),
    val noteDraftsLoading: Boolean = true,
    val noteDraftsAvailable: Boolean = false,
    val noteDraftSaving: Boolean = false,
    val noteWritingId: String? = null,
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
    val vocabularyDiagnosticCode: String? = null
)

enum class NotesSortOrder { LOCATION, ADDED_AT }

class MainViewModel(application: Application) : AndroidViewModel(application), CommandSink {
    private val store = SecureSettingsStore(application)
    private val profiles = KindleProfiles(application)
    private val localData = LocalLibraryData(application)
    private val ssh = KindleSshClient()
    private val transferService = KindleTransferService(application.contentResolver)
    private val clippingsService = ClippingsSyncService(application)
    private val libraryService = LibraryService(application)
    private val noteDraftStore = NoteDraftStore(application)
    private val writebackService = KindleNoteWritebackService(application)
    private val draftMutex = Mutex()
    private var pendingDraftSaves = 0
    private val vocabularyService = VocabularySyncService(application)
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

    init {
        setRemoteControlEnabled(false)
        setKeyboardKeysEnabled(false)
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
        mutableState.update {
            if (changedDevice) it.copy(
                settings = settings, connected = false, connectionMessage = "未连接", connectionError = false, isError = false, library = null, libraryError = null,
                notes = emptyList(), notesLastSync = null, selectedBook = null, selectedNoteId = null,
                notesMessage = "从 Kindle 同步摘抄", notesError = false,
                vocabularyWords = emptyList(), vocabularyLastSync = null, selectedVocabularyKey = null, vocabularyError = false,
                noteDrafts = emptyMap(), noteDraftsLoading = true, noteDraftsAvailable = false,
                noteWriteMessage = "", noteWriteError = false
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
                if (state.value.settings.cacheScope() == scope && snapshot != null &&
                    (state.value.vocabularyLastSync == null || !snapshot.syncedAt.isBefore(state.value.vocabularyLastSync))) applyVocabularySnapshot(snapshot)
            }.onFailure { error ->
                if (state.value.settings.cacheScope() == scope) mutableState.update { it.copy(vocabularyError = true, vocabularyMessage = error.message ?: "无法读取生词缓存") }
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
                    it.copy(notesError = true, notesMessage = error.message ?: "无法读取摘抄缓存")
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

    fun setNavigationItem(item: NavigationItem, visible: Boolean) {
        val updated = NavigationOptions.update(state.value.navigationItems, item, visible)
        preferences.navigationItems = updated
        mutableState.update { it.copy(navigationItems = updated) }
    }

    fun reloadNoteDrafts() {
        if (state.value.noteDraftSaving || state.value.noteWritingId != null || state.value.noteDraftsLoading) return
        mutableState.update { it.copy(noteDraftsLoading = true, noteDraftsAvailable = false, noteWriteMessage = "", noteWriteError = false) }
        loadDeviceData(state.value.settings)
    }

    fun syncLibrary() {
        if (state.value.librarySyncing || state.value.localDataBusy) return
        val settings = state.value.settings
        val scope = settings.cacheScope()
        mutableState.update { it.copy(librarySyncing = true, libraryError = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { libraryService.sync(settings) } }
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
                        val staged = stageNoteDraft(note.id, drafts[note.id], text, note.kindleNotes.isNotEmpty(), writeInFlight)
                        if (staged == null) drafts.remove(note.id) else drafts[note.id] = staged
                        noteDraftStore.save(scope, drafts)
                        drafts.toMap()
                    }
                }
            }.onSuccess { drafts ->
                if (state.value.settings.cacheScope() == scope) mutableState.update {
                    it.copy(noteDrafts = drafts, noteWriteMessage = "", noteWriteError = false)
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
                        val drafts = noteDraftStore.load(scope) - note.id
                        noteDraftStore.save(scope, drafts)
                        drafts
                    }
                }
            }.onSuccess { drafts ->
                if (state.value.settings.cacheScope() == scope) mutableState.update {
                    it.copy(noteDrafts = drafts, noteWriteMessage = "已恢复同步时的 Kindle 批注", noteWriteError = false)
                }
            }.onFailure { error ->
                if (state.value.settings.cacheScope() == scope) mutableState.update {
                    it.copy(noteWriteError = true, noteWriteMessage = error.message ?: "无法恢复原批注")
                }
            }
            mutableState.update { it.copy(noteDraftSaving = false) }
        }
    }

    fun sendNoteToKindle(note: KindleClipping) {
        val current = state.value
        if (current.notesSyncing || current.noteWritingId != null || current.noteDraftSaving || !current.noteDraftsAvailable) return
        val draft = current.noteDrafts[note.id] ?: return
        val settings = current.settings
        val scope = settings.cacheScope()
        mutableState.update { it.copy(noteWritingId = note.id, noteWriteMessage = "正在写入 Kindle 批注…", noteWriteError = false) }
        viewModelScope.launch {
            var delivered = false
            runCatching {
                val result = withContext(Dispatchers.IO) { writebackService.write(settings, note, draft.text) }
                delivered = true
                val snapshot = withContext(Dispatchers.IO) { clippingsService.importWrittenBytes(settings, result.clippingsBytes) }
                val drafts = draftMutex.withLock {
                    withContext(Dispatchers.IO) {
                        val latest = noteDraftStore.load(scope).toMutableMap()
                        latest[note.id]?.let { latest[note.id] = it.acknowledge(draft.revision) }
                        noteDraftStore.save(scope, latest)
                        latest.toMap()
                    }
                }
                snapshot to drafts
            }.onSuccess { (snapshot, drafts) ->
                if (state.value.settings.cacheScope() == scope) {
                    applyNotesSnapshot(snapshot)
                    mutableState.update {
                        it.copy(noteDrafts = drafts, noteWriteError = false, noteWriteMessage =
                            if (draft.text.isBlank()) "已删除 Kindle 批注，划线保留" else "批注已写入 Kindle，本地副本已保留")
                    }
                }
            }.onFailure { error ->
                if (state.value.settings.cacheScope() == scope) mutableState.update {
                    it.copy(noteWriteError = true, noteWriteMessage = if (delivered)
                        "Kindle 已完成写入，但本机保存失败。草稿仍保留，请同步摘抄后重试。"
                    else error.message ?: "写入失败，本地草稿已保留")
                }
            }
            mutableState.update { it.copy(noteWritingId = null) }
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
        val scope = settings.cacheScope()
        mutableState.update { it.copy(connectionBusy = true, connectionMessage = "正在连接 Kindle…", connectionError = false, status = "正在连接 Kindle…", isError = false) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                store.save(settings); profiles.saveActive(settings)
                ssh.test(settings)
                val event = settings.eventDevice.ifBlank { runCatching { ssh.detectTouchDevice(settings) }.getOrDefault("") }
                settings.copy(eventDevice = event).also { store.save(it); profiles.saveActive(it) }
            } }.onSuccess { resolved ->
                if (state.value.settings.cacheScope() == scope) {
                    mutableState.update { it.copy(settings = resolved, connected = true, connectionMessage = "已连接", connectionError = false, status = "已连接", isError = false) }
                    remoteDispatcher.connectionSettingsChanged()
                    setRemoteControlEnabled(true)
                    checkFlipCommands()
                }
            }.onFailure { error ->
                if (state.value.settings.cacheScope() == scope) mutableState.update { it.copy(connected = false, connectionMessage = error.message ?: "连接失败", connectionError = true, status = error.message ?: "连接失败", isError = true) }
            }
            mutableState.update { it.copy(connectionBusy = false) }
        }
    }

    fun refreshLibraryFromKindle() { syncNotes(); syncVocabulary(); syncLibrary() }
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
            if (state.value.settings.cacheScope() == settings.cacheScope()) applyVocabularySnapshot(snapshot)
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
            preferences.navigationItems = NavigationOptions.defaults
            mutableState.value = UiState(profiles = profiles.all(), activeProfileId = profiles.activeId,
                navigationItems = NavigationOptions.defaults, noteDraftsLoading = false, noteDraftsAvailable = true)
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
        }.onFailure { error ->
            collisionDecision = null
            mutableState.update { it.copy(collision = null, transferMessage = error.message ?: "发送失败") }
            updateTransfer(id) { it.copy(status = TransferStatus.FAILED, error = error.message ?: "发送失败") }
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
        val scope = settings.cacheScope()
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
            runCatching {
                withContext(Dispatchers.IO) {
                    clippingsService.sync(settings) { downloaded, total ->
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
                    applyNotesSnapshot(snapshot)
                    mutableState.update {
                        it.copy(
                            notesSyncing = false,
                            notesMessage = if (snapshot.items.isEmpty()) {
                                "同步完成，My Clippings.txt 中还没有内容"
                            } else {
                                "已同步 ${snapshot.items.size} 条摘抄"
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

    fun updateNotesQuery(query: String) {
        mutableState.update { it.copy(notesQuery = query, selectedNoteId = null) }
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
        mutableState.update { it.copy(notesMessage = error.message ?: "无法创建导出文件", notesError = true) }
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
    }

    fun syncVocabulary() {
        if (state.value.vocabularySyncing || state.value.localDataBusy) return
        val settings = state.value.settings
        val scope = settings.cacheScope()
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
            runCatching {
                withContext(Dispatchers.IO) {
                    vocabularyService.sync(settings) { downloaded, total ->
                        if (state.value.settings.cacheScope() == scope) mutableState.update {
                            it.copy(vocabularyDownloaded = downloaded, vocabularyTotal = total)
                        }
                    }
                }
            }.fold(
                onSuccess = { snapshot ->
                    if (state.value.settings.cacheScope() != scope) { mutableState.update { it.copy(vocabularySyncing = false) }; return@fold }
                    applyVocabularySnapshot(snapshot)
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
        val query = state.value.vocabularyQuery.trim()
        val visible = state.value.vocabularyWords.filter { word ->
            query.isEmpty() || word.displayWord.contains(query, true) || word.lookups.any {
                listOf(it.bookTitle, it.authors, it.usage).any { field -> field.contains(query, true) }
            }
        }
        return VocabularyOrdering.sort(visible, state.value.vocabularySortOrder)
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
            it.copy(vocabularyMessage = error.message ?: "无法创建生词导出文件", vocabularyError = true)
        }
    }.getOrNull()

    private fun applyVocabularySnapshot(snapshot: VocabularySnapshot) {
        mutableState.update {
            it.copy(
                vocabularyWords = snapshot.words,
                vocabularyLastSync = snapshot.syncedAt,
                selectedVocabularyKey = it.selectedVocabularyKey?.takeIf { key -> snapshot.words.any { word -> word.key == key } },
                vocabularyMessage = if (snapshot.words.isEmpty()) "没有找到可用的生词" else "已载入 ${snapshot.words.size} 个生词",
                vocabularyError = false
            )
        }
    }
}
