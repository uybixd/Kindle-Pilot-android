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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.hongxiang.kpilot.ui.settings.SettingsScreen as AlignedSettingsScreen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hongxiang.kpilot.model.KindleCommand
import com.hongxiang.kpilot.data.NavigationItem
import com.hongxiang.kpilot.data.NavigationOptions
import com.hongxiang.kpilot.library.LibraryScreen
import com.hongxiang.kpilot.ui.NoteWritebackSection
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
    NOTES("摘抄", Icons.AutoMirrored.Filled.Notes),
    VOCABULARY("生词本", Icons.Filled.Spellcheck),
    REMOTE("遥控", Icons.Filled.SettingsRemote),
    LIBRARY("书架", Icons.AutoMirrored.Filled.LibraryBooks),
    SEND("传书状态", Icons.AutoMirrored.Filled.Send),
    SETTINGS("设置", Icons.Filled.Settings);

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
    val context = LocalContext.current
    var destination by rememberSaveable { mutableStateOf(Destination.NOTES) }
    var secondaryParent by rememberSaveable { mutableStateOf(Destination.NOTES) }
    var transientDestination by rememberSaveable { mutableStateOf(false) }
    var noteReturnDestination by rememberSaveable { mutableStateOf(Destination.NOTES) }
    val screenStateHolder = rememberSaveableStateHolder()
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var notesSortMenu by remember { mutableStateOf(false) }
    var pendingNotesExport by remember { mutableStateOf<ClippingsExportFormat?>(null) }
    var pendingVocabularyExport by remember { mutableStateOf<VocabularyExportFormat?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val showingNoteDetail = destination == Destination.NOTES && state.selectedNoteId != null
    val showingSecondaryScreen = transientDestination && destination.navigationItem !in state.navigationItems

    fun navigateTo(item: Destination) {
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (destination != Destination.SETTINGS) TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Text(if (showingNoteDetail) "摘抄详情" else if (destination == Destination.NOTES) state.selectedBook ?: destination.label else destination.label, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    if (showingNoteDetail || showingSecondaryScreen) {
                        IconButton(onClick = {
                            if (showingNoteDetail) closeNote() else {
                                destination = secondaryParent.takeIf { it.navigationItem in state.navigationItems } ?: Destination.NOTES
                                transientDestination = false
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = localizedText("返回"))
                        }
                    }
                },
                actions = {
                    when {
                        destination == Destination.LIBRARY -> {
                            if (state.transfers.isNotEmpty()) IconButton(onClick = { navigateTo(Destination.SEND) }) { Icon(Icons.AutoMirrored.Filled.ListAlt, localizedText("传书状态")) }
                            IconButton(onClick = { bookImporter.launch(SupportedTransferTypes.pickerMimeTypes) }) { Icon(Icons.Filled.Add, localizedText("传书")) }
                        }
                        destination == Destination.NOTES && !showingNoteDetail -> {
                            TactileIconButton(onClick = { viewModel.showNotesBookPicker() }) {
                                Icon(Icons.Filled.FilterList, contentDescription = localizedText("筛选"))
                            }
                            if (state.selectedBook != null) Box {
                                TactileIconButton(onClick = { notesSortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = localizedText("排序")) }
                                DropdownMenu(expanded = notesSortMenu, onDismissRequest = { notesSortMenu = false }) {
                                    NotesSortOrder.entries.forEach { order ->
                                        DropdownMenuItem(text = { Text(if(order == NotesSortOrder.LOCATION) "按位置" else "按时间") },
                                            trailingIcon = { if(state.notesSortOrder == order) Icon(Icons.Filled.CheckCircle,null) },
                                            onClick = { viewModel.setNotesSortOrder(order); notesSortMenu = false })
                                    }
                                }
                            }
                            TactileIconButton(onClick = { exportNotes(ClippingsExportFormat.MARKDOWN) }) {
                                Icon(Icons.Filled.IosShare, contentDescription = localizedText("导出"))
                            }
                        }
                        destination == Destination.VOCABULARY -> {
                            TactileIconButton(onClick = { exportVocabulary(VocabularyExportFormat.MARKDOWN) }) {
                                Icon(Icons.Filled.IosShare, contentDescription = localizedText("导出"))
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!showingNoteDetail) {
                KPilotBottomBar(
                    selected = destination,
                    items = Destination.entries.filter { it.navigationItem in NavigationOptions.required || (it.navigationItem in state.navigationItems && it.navigationItem in setOf(NavigationItem.REMOTE, NavigationItem.VOCABULARY)) },
                    onSelect = { item ->
                        destination = item
                        transientDestination = false
                        noteReturnDestination = Destination.NOTES
                        exportMenuExpanded = false
                    }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            screenStateHolder.SaveableStateProvider(destination.name) {
                when (destination) {
                    Destination.LIBRARY -> LibraryScreen(
                        snapshot = state.library,
                        clippings = state.notes,
                        syncing = state.librarySyncing || state.notesSyncing,
                        errorMessage = state.libraryError,
                        onRefresh = viewModel::syncLibrary,
                        onRefreshClippings = viewModel::refreshLibraryFromKindle,
                        onOpenClipping = { note ->
                            noteReturnDestination = Destination.LIBRARY
                            viewModel.selectNote(note.id)
                            navigateTo(Destination.NOTES)
                        }
                    )
                    Destination.SEND -> SendScreen(viewModel, state)
                    Destination.NOTES -> PullToRefreshBox(isRefreshing = state.notesSyncing, onRefresh = viewModel::refreshLibraryFromKindle) { NotesScreen(viewModel, state, true) }
                    Destination.REMOTE -> PullToRefreshBox(isRefreshing = state.connectionBusy || state.busy, onRefresh = viewModel::refreshRemoteControl) { RemoteScreen(viewModel, state) }
                    Destination.VOCABULARY -> PullToRefreshBox(isRefreshing = state.vocabularySyncing, onRefresh = viewModel::refreshLibraryFromKindle) { VocabularyScreen(viewModel, state, true) }
                    Destination.SETTINGS -> AlignedSettingsScreen(viewModel, state)
                }
            }
        }
    }
}

@Composable
private fun TactileIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, content = content)
}

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
private fun SendScreen(viewModel: MainViewModel, state: UiState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal=20.dp,vertical=12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if(state.transfers.isEmpty()) item { EmptyState(Icons.AutoMirrored.Filled.Send,"暂无传书记录","") }
        items(state.transfers,key=TransferItem::id) { item -> TransferRow(item,viewModel) }
    }
}

@Composable
private fun TransferRow(item: TransferItem, viewModel: MainViewModel) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.displayName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val extension = SupportedTransferTypes.extensionOf(item.displayName)?.uppercase()
                        ?: localizedText("文件")
                    val size = item.sizeBytes?.let { Formatter.formatFileSize(context, it) }
                        ?: localizedText("未知大小")
                    Text("$extension · $size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(item.status.userLabel(), style = MaterialTheme.typography.labelLarge, color = item.status.statusColor())
            }
            if (item.status == TransferStatus.UPLOADING || item.status == TransferStatus.COMPLETED) {
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
            }
            item.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesScreen(viewModel: MainViewModel, state: UiState, searchVisible: Boolean) {
    val notesListState = rememberLazyListState()
    val selected = state.selectedNoteId?.let { id -> state.notes.firstOrNull { it.id == id } }
    if (selected != null) {
        NoteDetail(selected, viewModel, state)
        return
    }

    val visible = viewModel.visibleNotes()
    val bookCounts = state.notes.groupingBy { it.bookTitle }.eachCount().toSortedMap()
    val showBooks = state.notesBookPickerVisible

    if (showBooks) {
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
        Column(
            Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.notesError) Text(state.notesMessage, style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(vertical=8.dp))
            if (searchVisible) {
                OutlinedTextField(
                    value = state.notesQuery,
                    onValueChange = viewModel::updateNotesQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
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
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp)
            ) {
                items(visible, key = KindleClipping::id) { note ->
                    NoteRow(note, showBook = state.selectedBook == null, onClick = { viewModel.selectNote(note.id) })
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: KindleClipping, showBook: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showBook) Text(note.bookTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(note.text.ifBlank { note.metadata }, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        },
        supportingContent = { note.addedAt?.let { Text(formatNoteDate(it), style = MaterialTheme.typography.labelSmall) } },
        trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) {
            if (note.kindleNotes.isNotEmpty()) Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        } },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(Modifier.padding(start = 16.dp))
}

@Composable
private fun NoteDetail(note: KindleClipping, viewModel: MainViewModel, state: UiState) {
    var information by rememberSaveable(note.id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { androidx.compose.foundation.text.selection.SelectionContainer { Text(note.text.ifBlank { note.metadata },style=MaterialTheme.typography.headlineSmall) } }
        if(note.kind == ClippingKind.HIGHLIGHT) item { NoteWritebackSection(note,state,viewModel::saveNoteDraft,viewModel::sendNoteToKindle,viewModel::discardNoteDraft,viewModel::reloadNoteDrafts) }
        item { TextButton(onClick={information=!information}) { Text("书籍信息");Icon(if(information) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,null) } }
        if(information) item {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(note.bookTitle);note.author?.let{Text(it)};Text(note.kind.userLabel())
                Text(note.referenceText);note.addedAt?.let{Text(formatDate(it))}
            }
        }
    }
}

@Composable
private fun VocabularyScreen(viewModel: MainViewModel, state: UiState, searchVisible: Boolean) {
    val visible = viewModel.visibleVocabularyWords()
    val selected = state.selectedVocabularyKey?.let { key -> state.vocabularyWords.firstOrNull { it.key == key } }
    selected?.let { word ->
        AlertDialog(onDismissRequest = { viewModel.selectVocabularyWord(null) }, title = { Text(word.displayWord) },
            text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(word.lookups, key = { it.id }) { lookup ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (lookup.usage.isNotBlank()) Text(lookup.usage)
                        Text(lookup.bookDisplay, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        lookup.position?.let { Text("位置 $it", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            } }, confirmButton = { TextButton(onClick = { viewModel.selectVocabularyWord(null) }) { Text("完成") } })
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(value = state.vocabularyQuery, onValueChange = viewModel::updateVocabularyQuery,
            placeholder = { Text("搜索") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        if (state.vocabularyError) Text(state.vocabularyMessage, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (visible.isEmpty()) item { EmptyState(Icons.Filled.Spellcheck, if(state.vocabularyWords.isEmpty()) "还没有生词" else "没有找到单词", "") }
            items(visible, key = VocabularyWord::key) { word ->
                ListItem(headlineContent = { Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(word.displayWord, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${word.lookups.size} 次", style = MaterialTheme.typography.labelSmall)
                } }, supportingContent = { word.lookups.firstOrNull()?.let { Text(it.usage.ifBlank { it.bookDisplay },maxLines=2,overflow=TextOverflow.Ellipsis) } },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,null) }, modifier = Modifier.clickable { viewModel.selectVocabularyWord(word.key) })
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun RemoteScreen(viewModel: MainViewModel, state: UiState) {
    val connected = state.connected
    val busy = state.busy || state.connectionBusy
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp,vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Box(Modifier.size(8.dp).background(if(connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(if(connected) "Kindle" else "Kindle 未连接",style=MaterialTheme.typography.titleSmall); Text(if(busy) "处理中" else if(connected) "已连接" else "未连接",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(state.settings.host,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.outline)
        }
        SettingsGroup {
            Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Button(onClick={viewModel.submit(KindleCommand.PREVIOUS)},enabled=connected&&!busy,modifier=Modifier.weight(1f).height(64.dp),shape=RoundedCornerShape(14.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,null);Text("上一页") }
                Button(onClick={viewModel.submit(KindleCommand.NEXT)},enabled=connected&&!busy,modifier=Modifier.weight(1f).height(64.dp),shape=RoundedCornerShape(14.dp)) { Text("下一页");Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,null) }
            }
            HorizontalDivider(Modifier.padding(horizontal=14.dp))
            SwitchRow(Icons.AutoMirrored.Filled.VolumeUp,"音量键翻页","",state.volumeKeysEnabled,connected,viewModel::setVolumeKeysEnabled)
            HorizontalDivider(Modifier.padding(horizontal=14.dp))
            SwitchRow(Icons.Filled.Watch,"小米手环",if(state.xiaomiBandConnected) "已连接" else if(state.xiaomiBandEnabled) "未连接" else "",state.xiaomiBandEnabled,connected,viewModel::setXiaomiBandEnabled)
        }
        if (!connected || state.missingFlipCommands.isNotEmpty()) SettingsGroup {
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(if(state.settings.host.isBlank()) "请先在设置中连接 Kindle" else if(!connected) "Kindle 未连接" else "需要录制翻页手势",style=MaterialTheme.typography.titleMedium)
                if(connected) state.missingFlipCommands.forEach { definition -> OutlinedButton(onClick={viewModel.recordFlipCommand(definition)},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Filled.RadioButtonChecked,null);Spacer(Modifier.width(8.dp));Text("录制${definition.title}") } }
            }
        }
        if(state.isError) Text(state.status,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }
    if(state.remoteRecordingCommandId!=null) AlertDialog(onDismissRequest={},title={Text("正在录制")},text={LinearProgressIndicator(Modifier.fillMaxWidth())},confirmButton={})
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
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
        .setPrimaryClip(ClipData.newPlainText("KPilot 摘抄诊断码", code))
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

private val noteDate = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault())
private val detailDate = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(ZoneId.systemDefault())
private fun formatNoteDate(instant: Instant) = noteDate.format(instant)
private fun formatDate(instant: Instant) = detailDate.format(instant)
