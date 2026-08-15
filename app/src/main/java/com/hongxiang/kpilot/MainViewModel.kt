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
import com.hongxiang.kpilot.export.DownloadExportStore
import com.hongxiang.kpilot.band.XiaomiBandController
import com.hongxiang.kpilot.model.CommandSink
import com.hongxiang.kpilot.model.KindleCommand
import com.hongxiang.kpilot.notes.ClippingsExportFormat
import com.hongxiang.kpilot.notes.ClippingsExporter
import com.hongxiang.kpilot.notes.ClippingsSnapshot
import com.hongxiang.kpilot.notes.ClippingsSyncService
import com.hongxiang.kpilot.notes.KindleClipping
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
import kotlinx.coroutines.withContext
import java.time.Instant

data class UiState(
    val settings: ConnectionSettings = ConnectionSettings(),
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
    private val ssh = KindleSshClient()
    private val transferService = KindleTransferService(application.contentResolver)
    private val clippingsService = ClippingsSyncService(application)
    private val vocabularyService = VocabularySyncService(application)
    private val exportStore = DownloadExportStore(application)
    private val preferences = AppPreferences(application)
    private val remoteDispatcher = RemoteCommandDispatcher.get(application)
    private val xiaomiBandController = XiaomiBandController.get(application)
    private val transferQueue = Channel<String>(Channel.BUFFERED)
    private var collisionDecision: CompletableDeferred<CollisionDecision>? = null
    private val mutableState = MutableStateFlow(
        UiState(
            settings = store.load() ?: ConnectionSettings(),
            remoteControlEnabled = preferences.remoteControlEnabled,
            volumeKeysEnabled = preferences.volumeKeysEnabled,
            keyboardKeysEnabled = preferences.keyboardKeysEnabled,
            accessibilityServiceEnabled = AccessibilityStatus.isRemoteServiceEnabled(application),
            xiaomiBandEnabled = preferences.xiaomiBandEnabled
        )
    )
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    init {
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
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { clippingsService.loadCached() } }
                .getOrNull()?.let(::applyNotesSnapshot)
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { vocabularyService.loadCached() } }
                .onSuccess { it?.let(::applyVocabularySnapshot) }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            vocabularyMessage = error.message ?: "Cached vocabulary is not compatible",
                            vocabularyError = true
                        )
                    }
                }
        }
    }

    fun updateSettings(settings: ConnectionSettings) {
        mutableState.update { it.copy(settings = settings, isError = false) }
    }

    fun save() {
        runCatching { store.save(state.value.settings) }.fold(
            onSuccess = {
                remoteDispatcher.connectionSettingsChanged()
                mutableState.update { it.copy(status = "设置已安全保存", isError = false) }
            },
            onFailure = { error ->
                mutableState.update { it.copy(status = error.message ?: "无法保存设置", isError = true) }
            }
        )
    }

    fun testConnection() {
        val settings = state.value.settings
        mutableState.update { it.copy(busy = true, status = "正在测试 SSH 连接…", isError = false) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { ssh.test(settings) } }.fold(
                onSuccess = { message -> mutableState.update { it.copy(busy = false, status = message) } },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(busy = false, status = error.message ?: "连接失败", isError = true)
                    }
                }
            )
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
        if (state.value.notesSyncing) return
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
            runCatching {
                withContext(Dispatchers.IO) {
                    clippingsService.sync(settings) { downloaded, total ->
                        mutableState.update {
                            it.copy(notesDownloaded = downloaded, notesTotal = total)
                        }
                    }
                }
            }.fold(
                onSuccess = { snapshot ->
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
        mutableState.update { it.copy(selectedNoteId = id) }
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
        val byBook = if (current.selectedBook == null) compareBy<KindleClipping> { it.bookTitle.lowercase() } else compareBy { "" }
        return when (current.notesSortOrder) {
            NotesSortOrder.LOCATION -> filtered.sortedWith(
                byBook.thenBy { it.locationStart ?: Int.MAX_VALUE }.thenBy { it.addedAt ?: Instant.MAX }
            )
            NotesSortOrder.ADDED_AT -> filtered.sortedWith(
                byBook.thenByDescending { it.addedAt ?: Instant.MIN }.thenBy { it.locationStart ?: Int.MAX_VALUE }
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
        if (state.value.vocabularySyncing) return
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
            runCatching {
                withContext(Dispatchers.IO) {
                    vocabularyService.sync(settings) { downloaded, total ->
                        mutableState.update {
                            it.copy(vocabularyDownloaded = downloaded, vocabularyTotal = total)
                        }
                    }
                }
            }.fold(
                onSuccess = { snapshot ->
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
