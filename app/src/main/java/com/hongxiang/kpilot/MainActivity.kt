package com.hongxiang.kpilot

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.hongxiang.kpilot.ui.settings.SettingsScreen as AlignedSettingsScreen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.hongxiang.kpilot.model.KindleCommand
import com.hongxiang.kpilot.data.NavigationItem
import com.hongxiang.kpilot.data.NavigationOptions
import com.hongxiang.kpilot.library.LibraryScreen
import com.hongxiang.kpilot.ui.NoteWritebackSection
import com.hongxiang.kpilot.ui.ClippingPreviewRow
import com.hongxiang.kpilot.ui.ClippingReviewDialog
import com.hongxiang.kpilot.ui.ClippingReviewEntry
import com.hongxiang.kpilot.ui.hasReviewableClippings
import com.hongxiang.kpilot.ui.reviewCountToday
import com.hongxiang.kpilot.ui.VocabularyScreen
import com.hongxiang.kpilot.ui.NavigationIcons
import com.hongxiang.kpilot.ui.LocalTabActive
import com.hongxiang.kpilot.ui.RetainedTabHost
import com.hongxiang.kpilot.ui.readingListInset
import com.hongxiang.kpilot.ui.readingListSecondaryText
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.i18n.localizeAppText
import com.hongxiang.kpilot.i18n.localizedText
import com.hongxiang.kpilot.notes.ClippingKind
import com.hongxiang.kpilot.notes.ClippingsExportFormat
import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.notes.KindleNote
import com.hongxiang.kpilot.notes.NotesRetryPolicy
import com.hongxiang.kpilot.remote.AccessibilityStatus
import com.hongxiang.kpilot.remote.FlipCommandDefinition
import com.hongxiang.kpilot.remote.RemoteKeyPolicy
import com.hongxiang.kpilot.ssh.LocalNetworkAccess
import com.hongxiang.kpilot.support.SupportKPilotEntry
import com.hongxiang.kpilot.transfer.CollisionDecision
import com.hongxiang.kpilot.transfer.SupportedTransferTypes
import com.hongxiang.kpilot.transfer.TransferItem
import com.hongxiang.kpilot.transfer.TransferQueueRules
import com.hongxiang.kpilot.transfer.TransferStatus
import com.hongxiang.kpilot.ui.theme.KPilotTheme
import com.hongxiang.kpilot.vocabulary.VocabularyExportFormat
import com.hongxiang.kpilot.vocabulary.VocabularySortOrder
import com.hongxiang.kpilot.vocabulary.VocabularyWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun commandForVolumeKey(keyCode: Int): KindleCommand? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_UP -> KindleCommand.PREVIOUS
    KeyEvent.KEYCODE_VOLUME_DOWN -> KindleCommand.NEXT
    else -> null
}

private enum class Destination(val label: String, val icon: ImageVector) {
    NOTES("摘抄", NavigationIcons.Notes),
    VOCABULARY("生词本", NavigationIcons.Vocabulary),
    REMOTE("遥控", NavigationIcons.Remote),
    LIBRARY("书架", NavigationIcons.Library),
    SEND("传书状态", Icons.AutoMirrored.Filled.Send),
    SETTINGS("设置", NavigationIcons.Settings);

    val navigationItem: NavigationItem get() = NavigationItem.valueOf(name)
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KPilotTheme { KPilotApp(viewModel) } }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshRemoteRuntimeStatus()
        viewModel.refreshXiaomiBand()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!AccessibilityStatus.isRemoteServiceEnabled(this)) {
            val state = viewModel.state.value
            val result = RemoteKeyPolicy.evaluate(
                keyCode, KeyEvent.ACTION_DOWN, event.repeatCount,
                state.remoteControlEnabled, state.volumeKeysEnabled, state.keyboardKeysEnabled
            )
            result.command?.let(viewModel::submit)
            if (result.consume) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!AccessibilityStatus.isRemoteServiceEnabled(this)) {
            val state = viewModel.state.value
            val result = RemoteKeyPolicy.evaluate(
                keyCode, KeyEvent.ACTION_UP, event.repeatCount,
                state.remoteControlEnabled, state.volumeKeysEnabled, state.keyboardKeysEnabled
            )
            if (result.consume) return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KPilotApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val libraryContent by viewModel.libraryContent.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var destination by rememberSaveable { mutableStateOf(Destination.NOTES) }
    var secondaryParent by rememberSaveable { mutableStateOf(Destination.NOTES) }
    var transientDestination by rememberSaveable { mutableStateOf(false) }
    var noteReturnDestination by rememberSaveable { mutableStateOf(Destination.NOTES) }
    val screenStateHolder = rememberSaveableStateHolder()
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var notesSearchVisible by rememberSaveable { mutableStateOf(false) }
    var vocabularySearchVisible by rememberSaveable { mutableStateOf(false) }
    var notesSearchFocusRequested by remember { mutableStateOf(false) }
    var vocabularySearchFocusRequested by remember { mutableStateOf(false) }
    var pendingNotesExport by remember { mutableStateOf<ClippingsExportFormat?>(null) }
    var pendingVocabularyExport by remember { mutableStateOf<VocabularyExportFormat?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val showingNoteDetail = destination == Destination.NOTES && state.selectedNoteId != null
    val showingSecondaryScreen = transientDestination && destination.navigationItem !in state.navigationItems

    // Android 17 (API 37) silently drops LAN traffic until ACCESS_LOCAL_NETWORK is granted,
    // which otherwise only surfaces as an SSH "socket is not established" timeout.
    var connectAfterLocalNetworkGrant by rememberSaveable { mutableStateOf(false) }
    var localNetworkPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val shouldConnect = connectAfterLocalNetworkGrant
        connectAfterLocalNetworkGrant = false
        if (!granted) {
            Toast.makeText(
                context,
                context.localizeAppText("未授予本地网络权限，无法连接同一 Wi-Fi 下的 Kindle"),
                Toast.LENGTH_LONG
            ).show()
        } else if (shouldConnect) {
            viewModel.connectKindle()
        }
    }

    fun connectKindleWithLocalNetworkPermission() {
        if (LocalNetworkAccess.granted(context)) {
            viewModel.connectKindle()
        } else {
            connectAfterLocalNetworkGrant = true
            localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    LaunchedEffect(state.settings.host) {
        if (!localNetworkPermissionRequested && state.settings.host.isNotBlank() &&
            !LocalNetworkAccess.granted(context)
        ) {
            localNetworkPermissionRequested = true
            localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    fun navigateTo(item: Destination) {
        if (item != destination) {
            focusManager.clearFocus()
            keyboard?.hide()
            notesSearchFocusRequested = false
            vocabularySearchFocusRequested = false
            if (state.notesBookPickerVisible) viewModel.hideNotesBookPicker()
        }
        secondaryParent = destination
        destination = item
        transientDestination = item.navigationItem !in state.navigationItems
        exportMenuExpanded = false
    }

    fun closeNote() {
        viewModel.selectNote(null)
        if (noteReturnDestination != Destination.NOTES) navigateTo(noteReturnDestination)
        noteReturnDestination = Destination.NOTES
    }

    LaunchedEffect(state.navigationItems) {
        if (!transientDestination && destination.navigationItem !in state.navigationItems) destination = Destination.NOTES
    }

    fun exportNotesNow(format: ClippingsExportFormat) {
        coroutineScope.launch {
            val path = withContext(Dispatchers.IO) { viewModel.createNotesExport(format) }
            path?.let { Toast.makeText(context, context.localizeAppText("已导出至 $it"), Toast.LENGTH_LONG).show() }
        }
    }

    fun exportVocabularyNow(format: VocabularyExportFormat) {
        coroutineScope.launch {
            val path = withContext(Dispatchers.IO) { viewModel.createVocabularyExport(format) }
            path?.let { Toast.makeText(context, context.localizeAppText("已导出至 $it"), Toast.LENGTH_LONG).show() }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingNotesExport?.let(::exportNotesNow)
                ?: pendingVocabularyExport?.let(::exportVocabularyNow)
        } else {
            Toast.makeText(
                context,
                context.localizeAppText("需要存储权限才能写入 Download/kpilot_export"),
                Toast.LENGTH_LONG
            ).show()
        }
        pendingNotesExport = null
        pendingVocabularyExport = null
    }

    fun exportNotes(format: ClippingsExportFormat) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotesExport = format
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            exportNotesNow(format)
        }
    }

    fun exportVocabulary(format: VocabularyExportFormat) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingVocabularyExport = format
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            exportVocabularyNow(format)
        }
    }

    val bookImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { viewModel.addTransferUris(uris); viewModel.sendReadyTransfers(); navigateTo(Destination.SEND) }
    }

    BackHandler(showingNoteDetail || showingSecondaryScreen) {
        if (showingNoteDetail) closeNote() else {
            destination = secondaryParent.takeIf { it.navigationItem in state.navigationItems } ?: Destination.NOTES
            transientDestination = false
        }
    }

    fun closeNotesSearch() {
        focusManager.clearFocus()
        keyboard?.hide()
        notesSearchVisible = false
        notesSearchFocusRequested = false
        viewModel.updateNotesQuery("")
    }

    fun closeVocabularySearch() {
        focusManager.clearFocus()
        keyboard?.hide()
        vocabularySearchVisible = false
        vocabularySearchFocusRequested = false
        viewModel.updateVocabularyQuery("")
    }

    BackHandler(!showingNoteDetail && !state.notesBookPickerVisible && (
        destination == Destination.NOTES && notesSearchVisible ||
            destination == Destination.VOCABULARY && vocabularySearchVisible
        )) {
        if (destination == Destination.NOTES) closeNotesSearch() else closeVocabularySearch()
    }

    state.collision?.let { collision ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.FilePresent, contentDescription = null) },
            title = { Text("Kindle 中已有同名书籍") },
            text = { Text("${collision.displayName} 与 Kindle 中的文件匹配。是否覆盖？") },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveCollision(CollisionDecision.OVERWRITE) }) { Text("覆盖") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveCollision(CollisionDecision.SKIP) }) { Text("跳过") }
            }
        )
    }

    if (state.noteConflictReviewOpen && state.noteWritingId == null) {
        state.noteConflicts.values.firstOrNull()?.let { conflict ->
            val note = state.notes.firstOrNull { it.id == conflict.clippingId }
            AlertDialog(
                onDismissRequest = { viewModel.reviewNoteConflicts(false) },
                title = { Text("批注待确认（${state.noteConflicts.size} 条）") },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        note?.let {
                            Text(it.bookTitle, style = MaterialTheme.typography.titleSmall)
                            Text(it.text.ifBlank { it.metadata }, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                        if (!conflict.baselineKnown) Text("这条草稿缺少原批注版本，请选择要保留的内容。")
                        Text("手机", style = MaterialTheme.typography.labelLarge)
                        if (conflict.phoneText.isBlank()) Text("无批注")
                        else androidx.compose.material3.Text(conflict.phoneText)
                        Text("Kindle", style = MaterialTheme.typography.labelLarge)
                        if (conflict.kindleText.isBlank()) Text("无批注")
                        else androidx.compose.material3.Text(conflict.kindleText)
                        if (state.noteWriteError) Text(state.noteWriteMessage, color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.resolveNoteConflict(conflict, true) }) { Text("保留手机") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.reviewNoteConflicts(false) }) { Text("稍后处理") }
                    TextButton(onClick = { viewModel.resolveNoteConflict(conflict, false) }) { Text("保留 Kindle") }
                }
            )
        }
    }

    val pageTopBar: @Composable (Destination) -> Unit = { page ->
        val pageShowingNoteDetail = page == Destination.NOTES && state.selectedNoteId != null
        val pageShowingSecondaryScreen = LocalTabActive.current && showingSecondaryScreen
        val navigationIcon: @Composable () -> Unit = {
            if (pageShowingNoteDetail || pageShowingSecondaryScreen) {
                IconButton(onClick = {
                    if (pageShowingNoteDetail) closeNote() else {
                        destination = secondaryParent.takeIf { it.navigationItem in state.navigationItems } ?: Destination.NOTES
                        transientDestination = false
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = localizedText("返回"))
                }
            }
        }
        if (page.isReadingList(pageShowingNoteDetail)) {
            val isNotes = page == Destination.NOTES
            val searchVisible = if (isNotes) notesSearchVisible else vocabularySearchVisible
            CenterAlignedTopAppBar(
                expandedHeight = 56.dp,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                title = {
                    Text(page.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = navigationIcon,
                actions = {
                    Surface(
                        modifier = Modifier.padding(end = 12.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        Row {
                            IconButton(onClick = {
                                if (isNotes) {
                                    if (searchVisible) closeNotesSearch() else {
                                        notesSearchVisible = true
                                        notesSearchFocusRequested = true
                                    }
                                } else {
                                    if (searchVisible) closeVocabularySearch() else {
                                        vocabularySearchVisible = true
                                        vocabularySearchFocusRequested = true
                                    }
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = localizedText(if (isNotes) "搜索摘抄" else "搜索生词"),
                                    tint = if (searchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = {
                                if (isNotes) exportNotes(ClippingsExportFormat.MARKDOWN)
                                else exportVocabulary(VocabularyExportFormat.MARKDOWN)
                            }) {
                                Icon(Icons.Filled.IosShare, contentDescription = localizedText(if (isNotes) "导出摘抄" else "导出"))
                            }
                        }
                    }
                }
            )
        } else TopAppBar(
            modifier = Modifier.padding(horizontal = 8.dp),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            title = {
                Text(if (pageShowingNoteDetail) "摘抄详情" else page.label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = navigationIcon,
            actions = {
                if (page == Destination.LIBRARY) {
                    if (state.transfers.isNotEmpty()) IconButton(onClick = { navigateTo(Destination.SEND) }) { Icon(Icons.AutoMirrored.Filled.ListAlt, localizedText("传书状态")) }
                    IconButton(onClick = { bookImporter.launch(SupportedTransferTypes.pickerMimeTypes) }) { Icon(Icons.Filled.Add, localizedText("传书")) }
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!showingNoteDetail) {
                KPilotBottomBar(
                    selected = destination,
                    items = Destination.entries.filter { it.navigationItem in NavigationOptions.required || (it.navigationItem in state.navigationItems && it.navigationItem in setOf(NavigationItem.REMOTE, NavigationItem.VOCABULARY)) },
                    onSelect = { item ->
                        navigateTo(item)
                        transientDestination = false
                        noteReturnDestination = Destination.NOTES
                        exportMenuExpanded = false
                    }
                )
            }
        }
    ) { padding ->
        RetainedTabHost(selected = destination, modifier = Modifier.padding(padding).fillMaxSize()) { page ->
            screenStateHolder.SaveableStateProvider(page.name) {
                if (page == Destination.SETTINGS) {
                    AlignedSettingsScreen(viewModel, state, onOpenClipping = { note ->
                        noteReturnDestination = Destination.SETTINGS
                        viewModel.selectNote(note.id)
                        navigateTo(Destination.NOTES)
                    }, onConnect = { connectKindleWithLocalNetworkPermission() })
                } else Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = if (page.isReadingList(state.selectedNoteId != null)) MaterialTheme.colorScheme.surfaceContainer
                    else MaterialTheme.colorScheme.background,
                    topBar = { pageTopBar(page) }
                ) { pagePadding ->
                    Box(Modifier.padding(pagePadding).fillMaxSize()) {
                        when (page) {
                            Destination.LIBRARY -> LibraryScreen(
                                snapshot = state.library,
                                clippingsByPath = libraryContent.notesFor(state.library),
                                coverLoader = viewModel.libraryCovers,
                                syncing = state.librarySyncing || state.notesSyncing,
                                repairingPath = state.libraryRepairingPath,
                                repairMessage = state.libraryRepairMessage,
                                repairError = state.libraryRepairError,
                                coverRevision = state.libraryCoverRevision,
                                deletingPath = state.libraryDeletingPath,
                                deleteMessage = state.libraryDeleteMessage,
                                deleteError = state.libraryDeleteError,
                                onRefresh = viewModel::syncLibrary,
                                onRefreshClippings = viewModel::refreshLibraryFromKindle,
                                onOpenClipping = { note ->
                                    noteReturnDestination = Destination.LIBRARY
                                    viewModel.selectNote(note.id)
                                    navigateTo(Destination.NOTES)
                                },
                                onRepairCover = viewModel::repairLibraryCover,
                                onDismissRepairMessage = viewModel::dismissLibraryRepairMessage,
                                onDeleteBook = viewModel::deleteLibraryBook,
                                onDismissDeleteMessage = viewModel::dismissLibraryDeleteMessage
                            )
                            Destination.SEND -> SendScreen(state)
                            Destination.NOTES -> PullToRefreshBox(isRefreshing = state.notesSyncing && LocalTabActive.current, onRefresh = viewModel::refreshLibraryFromKindle) {
                                NotesScreen(viewModel, state, notesSearchVisible, ::closeNotesSearch,
                                    notesSearchFocusRequested, { notesSearchFocusRequested = false })
                            }
                            Destination.REMOTE -> PullToRefreshBox(isRefreshing = (state.connectionBusy || state.busy) && LocalTabActive.current, onRefresh = viewModel::refreshRemoteControl) { RemoteScreen(viewModel, state) }
                            Destination.VOCABULARY -> PullToRefreshBox(isRefreshing = state.vocabularySyncing && LocalTabActive.current, onRefresh = viewModel::refreshLibraryFromKindle) {
                                VocabularyScreen(viewModel, state, vocabularySearchVisible, ::closeVocabularySearch,
                                    vocabularySearchFocusRequested, { vocabularySearchFocusRequested = false })
                            }
                            Destination.SETTINGS -> Unit
                        }
                    }
                }
            }
        }
    }

    val canShowReadingErrors = when (destination) {
        Destination.NOTES -> !showingNoteDetail && !state.clippingReviewPresented && !state.notesBookPickerVisible
        Destination.VOCABULARY -> !state.vocabularyReviewPresented && state.selectedVocabularyKey == null
        Destination.LIBRARY -> state.libraryRepairingPath == null && state.libraryRepairMessage == null &&
            state.libraryDeletingPath == null && state.libraryDeleteMessage == null
        else -> false
    }
    if (canShowReadingErrors && state.collision == null && !state.noteConflictReviewOpen &&
        !state.notesSyncing && !state.vocabularySyncing && !state.librarySyncing && !state.learningSyncing
    ) {
        val messages = listOfNotNull(
            state.notesMessage.takeIf { state.notesError },
            state.vocabularyMessage.takeIf { state.vocabularyError },
            state.libraryError,
            state.learningMessage.takeIf { state.learningError }
        ).distinct()
        if (messages.isNotEmpty()) {
            val codes = listOfNotNull(
                state.notesDiagnosticCode.takeIf { state.notesError },
                state.vocabularyDiagnosticCode.takeIf { state.vocabularyError }
            ).distinct()
            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                title = { Text("操作失败") },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        messages.forEach { Text(it) }
                        codes.forEach { code ->
                            TextButton(onClick = { copyDiagnosticCode(context, code) }) {
                                Text("诊断码 $code · 点按复制")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissReadingErrors(state) }) { Text("确定") }
                }
            )
        }
    }
}

private fun Destination.isReadingList(showingNoteDetail: Boolean): Boolean =
    this == Destination.VOCABULARY || (this == Destination.NOTES && !showingNoteDetail)

@Composable
private fun KPilotBottomBar(selected: Destination, items: List<Destination>, onSelect: (Destination) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 7.dp
    ) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp)) {
            items.forEach { item ->
                val isSelected = selected == item
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(item) }),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = localizedText(item.label),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )

                    }
                }
            }
        }
    }
}

@Composable
private fun SendScreen(state: UiState) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.transfers.isEmpty()) item { EmptyState(Icons.AutoMirrored.Filled.Send, "暂无传书记录", "") }
        items(state.transfers, key = TransferItem::id) { item -> TransferRow(item) }
    }
}

@Composable
private fun TransferRow(item: TransferItem) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(item.displayName, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val extension = SupportedTransferTypes.extensionOf(item.displayName)?.uppercase()
                    ?: localizedText("文件")
                val size = item.sizeBytes?.let { Formatter.formatFileSize(context, it) }
                    ?: localizedText("未知大小")
                Text("$extension · $size", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(item.status.userLabel(), style = MaterialTheme.typography.labelMedium, color = item.status.statusColor())
            }
            if (item.status == TransferStatus.UPLOADING) {
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
            }
            item.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            item.coverMessage?.let {
                Text(
                    it,
                    color = if (item.coverNeedsAttention) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesScreen(viewModel: MainViewModel, state: UiState, searchVisible: Boolean, onCloseSearch: () -> Unit,
    searchFocusRequested: Boolean, onSearchFocused: () -> Unit) {
    if (state.clippingReviewPresented) {
        ClippingReviewDialog(
            state = state,
            onClose = viewModel::dismissClippingReview,
            onClippingDisplayed = viewModel::markClippingReviewDisplayed,
            onAction = viewModel::performClippingReviewAction,
            onFocusClipping = viewModel::focusClippingReview,
            onUndo = viewModel::undoClippingReviewAction,
            onRestore = viewModel::restoreExcludedReviewClippings
        )
        return
    }
    val notesListState = rememberLazyListState()
    val selected = state.selectedNoteId?.let { id -> state.notes.firstOrNull { it.id == id } }
    if (selected != null) {
        NoteDetail(selected, viewModel, state)
        return
    }

    val visible = remember(state.notes, state.notesQuery, state.selectedBook, state.notesSortOrder) {
        viewModel.visibleNotes()
    }
    val isActive = LocalTabActive.current
    val showBooks = state.notesBookPickerVisible && isActive
    var sortMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (!isActive) sortMenuExpanded = false
    }

    if (showBooks) {
        val bookCounts = remember(state.notes) { state.notes.groupingBy { it.bookTitle }.eachCount().toSortedMap() }
        ModalBottomSheet(onDismissRequest = { viewModel.hideNotesBookPicker() }) {
            Text("选择书籍", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("全部书籍") },
                        supportingContent = { Text("${state.notes.size} 条") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                        trailingContent = { if (state.selectedBook == null) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { viewModel.selectNotesBook(null); viewModel.hideNotesBookPicker() }
                    )
                }
                items(bookCounts.entries.toList(), key = { it.key }) { (book, count) ->
                    ListItem(
                        headlineContent = { Text(book, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("$count 条") },
                        trailingContent = { if (state.selectedBook == book) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { viewModel.selectNotesBook(book); viewModel.hideNotesBookPicker() }
                    )
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        val pendingDraftCount = state.noteDrafts.values.count { it.needsSync }
        if (pendingDraftCount > 0 || state.noteWriteMessage.isNotBlank()) {
            Column(Modifier.padding(horizontal = readingListInset)) {
                if (pendingDraftCount > 0) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${pendingDraftCount} 条批注待同步", Modifier.weight(1f))
                        if (state.noteConflicts.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.reviewNoteConflicts(true) },
                                enabled = state.noteWritingId == null
                            ) { Text("处理冲突") }
                        }
                        TextButton(
                            onClick = viewModel::syncPendingNoteDrafts,
                            enabled = state.noteDraftsAvailable && !state.noteDraftSaving && !state.notesSyncing &&
                                state.noteWritingId == null && !state.localDataBusy && !state.connectionBusy &&
                                state.settings.host.isNotBlank()
                        ) { Text("同步批注") }
                    }
                }
                if (state.noteWriteMessage.isNotBlank()) {
                    Text(
                        state.noteWriteMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.noteWriteError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        val showReviewStatus = !state.learningError && state.learningMessage.isNotBlank()
        if (searchVisible || showReviewStatus) Column(
            Modifier.padding(horizontal = readingListInset, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showReviewStatus) Text(
                state.learningMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            if (searchVisible) {
                InlineSearchField(
                    value = state.notesQuery,
                    onValueChange = viewModel::updateNotesQuery,
                    placeholder = "搜索摘抄、书名或笔记",
                    onClose = onCloseSearch,
                    focusRequested = searchFocusRequested,
                    onFocusHandled = onSearchFocused
                )
            }
        }

        if (state.notes.isNotEmpty()) Row(
            Modifier.fillMaxWidth().padding(start = readingListInset, end = 24.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::showNotesBookPicker, modifier = Modifier.weight(1f, fill = false)) {
                    Text(state.selectedBook ?: "全部书籍", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Filled.ExpandMore, null, Modifier.padding(start = 4.dp).size(18.dp))
                }
                if (state.selectedBook != null) Box {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = localizedText("排序"),
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = sortMenuExpanded && isActive, onDismissRequest = { sortMenuExpanded = false }) {
                        NotesSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(if (order == NotesSortOrder.LOCATION) "按位置" else "按时间") },
                                trailingIcon = { if (state.notesSortOrder == order) Icon(Icons.Filled.CheckCircle, null) },
                                onClick = {
                                    viewModel.setNotesSortOrder(order)
                                    notesListState.requestScrollToItem(0)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Text("${visible.size} 条摘抄", style = MaterialTheme.typography.bodySmall,
                color = readingListSecondaryText,
                modifier = Modifier.padding(start = 8.dp))
        }

        when {
            state.notes.isEmpty() && !state.notesSyncing -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { EmptyState(
                icon = Icons.AutoMirrored.Filled.Notes,
                title = if (state.notesLastSync == null) "还没有摘抄" else "Kindle 摘抄为空",
                subtitle = ""
            ) }
            visible.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { EmptyState(Icons.Filled.Search, "没有找到内容", "") }
            else -> LazyColumn(
                state = notesListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = readingListInset, end = readingListInset, top = 4.dp, bottom = 24.dp)
            ) {
                if (state.notesQuery.isBlank() && state.selectedBook == null && hasReviewableClippings(state)) {
                    item(key = "review-entry") {
                        ClippingReviewEntry(
                            todayCount = reviewCountToday(state),
                            onClick = viewModel::presentClippingReview
                        )
                    }
                }
                itemsIndexed(visible, key = { _, note -> note.id }) { index, note ->
                    ClippingPreviewRow(
                        note,
                        showBook = state.selectedBook == null,
                        hasAnnotation = state.noteDrafts[note.id]?.let { it.text.isNotBlank() }
                            ?: note.kindleNotes.isNotEmpty(),
                        first = index == 0,
                        last = index == visible.lastIndex,
                        onClick = { viewModel.selectNote(note.id) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun InlineSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, onClose: () -> Unit,
    focusRequested: Boolean, onFocusHandled: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val isActive = LocalTabActive.current
    LaunchedEffect(focusRequested, isActive) {
        if (focusRequested && isActive) {
            focusRequester.requestFocus()
            keyboard?.show()
            onFocusHandled()
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = {
                if (value.isNotEmpty()) {
                    onValueChange("")
                    focusRequester.requestFocus()
                    keyboard?.show()
                } else {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    onClose()
                }
            }) {
                Icon(Icons.Filled.Close, contentDescription = localizedText(if (value.isNotEmpty()) "清除搜索" else "取消搜索"))
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); keyboard?.hide() }),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
    )
}

@Composable
private fun NoteDetail(note: KindleClipping, viewModel: MainViewModel, state: UiState) {
    var information by rememberSaveable(note.id) { mutableStateOf(false) }
    var editing by rememberSaveable(note.id) { mutableStateOf(false) }
    val hasAnnotation = state.noteDrafts[note.id]?.let { it.needsSync || it.text.isNotBlank() }
        ?: note.kindleNotes.isNotEmpty()
    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        item {
            Text(note.bookTitle, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(note.text.ifBlank { note.metadata },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 34.sp))
            }
        }
        if (note.kind == ClippingKind.HIGHLIGHT) item {
            if (state.noteDraftsLoading && !state.noteDraftsAvailable) {
                Text("正在读取本地批注…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (editing || hasAnnotation || !state.noteDraftsAvailable) {
                NoteWritebackSection(note, state, viewModel::saveNoteDraft, viewModel::sendNoteToKindle,
                    viewModel::discardNoteDraft, viewModel::reloadNoteDrafts)
            } else {
                OutlinedButton(onClick = { editing = true }, shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加批注")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = { information = !information }) {
                    Text("书籍信息")
                    Icon(if (information) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
                if (information) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    note.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(note.kind.userLabel(), style = MaterialTheme.typography.bodyMedium)
                    if (note.referenceText.isNotBlank()) Text(note.referenceText,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    note.addedAt?.let { Text(formatDate(it), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun RemoteScreen(viewModel: MainViewModel, state: UiState) {
    val connected = state.connected
    val busy = state.busy || state.connectionBusy
    val isActive = LocalTabActive.current
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingRecording by remember { mutableStateOf<FlipCommandDefinition?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(8.dp).background(
                if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
            Text(if (connected) "Kindle" else "Kindle 未连接", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else if (connected) Text("已连接", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilledTonalButton(
                onClick = { viewModel.submit(KindleCommand.PREVIOUS) }, enabled = connected && !busy,
                modifier = Modifier.weight(1f).heightIn(min = 136.dp), shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, Modifier.size(32.dp))
                    Text("上一页")
                }
            }
            Button(
                onClick = { viewModel.submit(KindleCommand.NEXT) }, enabled = connected && !busy,
                modifier = Modifier.weight(1f).heightIn(min = 136.dp), shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(32.dp))
                    Text("下一页")
                }
            }
        }
        SettingsGroup {
            SwitchRow(Icons.AutoMirrored.Filled.VolumeUp, "音量键翻页", "", state.volumeKeysEnabled,
                connected, viewModel::setVolumeKeysEnabled)
            SwitchRow(Icons.Filled.Watch, "小米手环",
                if (state.xiaomiBandConnected) "已连接" else if (state.xiaomiBandEnabled) "未连接" else "",
                state.xiaomiBandEnabled, connected, viewModel::setXiaomiBandEnabled)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { advancedExpanded = !advancedExpanded }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
                Text(if (advancedExpanded) "收起高级设置" else "高级设置",
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Icon(if (advancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            if (advancedExpanded) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
                    FlipCommandDefinition.all.forEach { definition ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(definition.title, modifier = Modifier.weight(1f))
                            TextButton(onClick = { pendingRecording = definition }, enabled = connected && !busy) {
                                Text(if (definition in state.missingFlipCommands) "录制" else "重新录制")
                            }
                        }
                    }
                }
            }
        }
        if (!connected || (!advancedExpanded && state.missingFlipCommands.isNotEmpty())) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (state.settings.host.isBlank()) "请先在设置中连接 Kindle"
                    else if (!connected) "Kindle 未连接" else "需要录制翻页手势",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (connected) state.missingFlipCommands.forEach { definition ->
                    OutlinedButton(onClick = { viewModel.recordFlipCommand(definition) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Filled.RadioButtonChecked, null)
                        Spacer(Modifier.width(8.dp))
                        Text("录制${definition.title}")
                    }
                }
            }
        }
        if (state.isError) Text(state.status, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (isActive) pendingRecording?.let { definition ->
        AlertDialog(
            onDismissRequest = { pendingRecording = null },
            title = { Text("录制${definition.title}") },
            text = {
                Text("点按开始后，请在 5 秒内到 Kindle 上执行一次${definition.title}手势。旧录制会被替换。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRecording = null
                        viewModel.recordFlipCommand(definition)
                    },
                    enabled = connected && !busy
                ) { Text("开始录制") }
            },
            dismissButton = { TextButton(onClick = { pendingRecording = null }) { Text("取消") } }
        )
    }
    if (isActive && state.remoteRecordingCommandId != null) AlertDialog(
        onDismissRequest = {}, title = { Text("正在录制") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("请现在到 Kindle 上执行一次对应的翻页手势，录制将在 5 秒后自动结束。")
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column { content() }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if(subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if(subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompactBanner(message: String, error: Boolean = false, busy: Boolean = false) {
    val container = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(14.dp), color = container) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(if (error) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = content)
        }
    }
}

@Composable
private fun CompactErrorBanner(
    message: String,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onDetails: (() -> Unit)? = null
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row {
                TextButton(onClick = onRetry, enabled = retryEnabled) { Text("重试") }
                if (onDetails != null) TextButton(onClick = onDetails) { Text("详情") }
            }
        }
    }
}

private fun copyDiagnosticCode(context: Context, code: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("KPilot", code))
}

private fun ClippingsExportFormat.menuLabel() = when (this) {
    ClippingsExportFormat.MARKDOWN -> "Markdown"
    ClippingsExportFormat.PLAIN_TEXT -> "TXT"
    ClippingsExportFormat.CSV -> "CSV"
}

private fun ClippingKind.userLabel() = when (this) {
    ClippingKind.HIGHLIGHT -> "标注"
    ClippingKind.NOTE -> "笔记"
    ClippingKind.BOOKMARK -> "书签"
    ClippingKind.UNKNOWN -> "摘抄"
}

@Composable
private fun ClippingKind.labelColor() = when (this) {
    ClippingKind.HIGHLIGHT -> MaterialTheme.colorScheme.tertiary
    ClippingKind.NOTE -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun TransferStatus.statusColor() = when (this) {
    TransferStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    TransferStatus.FAILED -> MaterialTheme.colorScheme.error
    TransferStatus.SKIPPED -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun TransferStatus.userLabel() = when (this) {
    TransferStatus.READY -> "待发送"
    TransferStatus.CHECKING -> "检查中"
    TransferStatus.WAITING_FOR_COLLISION -> "等待确认"
    TransferStatus.UPLOADING -> "发送中"
    TransferStatus.COMPLETED -> "已发送"
    TransferStatus.SKIPPED -> "已跳过"
    TransferStatus.FAILED -> "失败"
}

private val detailDate = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(ZoneId.systemDefault())
private fun formatDate(instant: Instant) = detailDate.format(instant)
