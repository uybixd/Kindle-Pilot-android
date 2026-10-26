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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hongxiang.kpilot.model.KindleCommand
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
    SEND("传书", Icons.AutoMirrored.Filled.Send),
    NOTES("摘抄", Icons.AutoMirrored.Filled.Notes),
    VOCABULARY("生词", Icons.Filled.Spellcheck),
    REMOTE("遥控", Icons.Filled.SettingsRemote),
    SETTINGS("设置", Icons.Filled.Settings)
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
    var notesSearchVisible by rememberSaveable { mutableStateOf(false) }
    var vocabularySearchVisible by rememberSaveable { mutableStateOf(false) }
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var pendingNotesExport by remember { mutableStateOf<ClippingsExportFormat?>(null) }
    var pendingVocabularyExport by remember { mutableStateOf<VocabularyExportFormat?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val showingNoteDetail = destination == Destination.NOTES && state.selectedNoteId != null

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

    BackHandler(showingNoteDetail) { viewModel.selectNote(null) }

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Text(if (showingNoteDetail) "摘抄详情" else destination.label, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    if (showingNoteDetail) {
                        IconButton(onClick = { viewModel.selectNote(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = localizedText("返回"))
                        }
                    }
                },
                actions = {
                    when {
                        destination == Destination.NOTES && !showingNoteDetail -> {
                            TactileIconButton(onClick = {
                                notesSearchVisible = !notesSearchVisible
                                if (!notesSearchVisible) viewModel.updateNotesQuery("")
                            }) {
                                Icon(if (notesSearchVisible) Icons.Filled.Close else Icons.Filled.Search, contentDescription = localizedText("搜索摘抄"))
                            }
                            SyncAction(state.notesSyncing, viewModel::syncNotes, "同步摘抄")
                            Box {
                                TactileIconButton(onClick = { exportMenuExpanded = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = localizedText("导出摘抄"))
                                }
                                DropdownMenu(expanded = exportMenuExpanded, onDismissRequest = { exportMenuExpanded = false }) {
                                    ClippingsExportFormat.entries.forEach { format ->
                                        DropdownMenuItem(
                                            text = { Text("导出 ${format.menuLabel()}") },
                                            enabled = viewModel.visibleNotes().isNotEmpty(),
                                            onClick = {
                                                exportMenuExpanded = false
                                                exportNotes(format)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        destination == Destination.VOCABULARY -> {
                            TactileIconButton(onClick = {
                                vocabularySearchVisible = !vocabularySearchVisible
                                if (!vocabularySearchVisible) viewModel.updateVocabularyQuery("")
                            }) {
                                Icon(if (vocabularySearchVisible) Icons.Filled.Close else Icons.Filled.Search, contentDescription = localizedText("搜索生词"))
                            }
                            SyncAction(state.vocabularySyncing, viewModel::syncVocabulary, "同步生词")
                            Box {
                                TactileIconButton(onClick = { exportMenuExpanded = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = localizedText("导出生词"))
                                }
                                DropdownMenu(expanded = exportMenuExpanded, onDismissRequest = { exportMenuExpanded = false }) {
                                    VocabularyExportFormat.entries.forEach { format ->
                                        DropdownMenuItem(
                                            text = { Text("导出 ${format.name}") },
                                            enabled = viewModel.visibleVocabularyWords().isNotEmpty(),
                                            onClick = {
                                                exportMenuExpanded = false
                                                exportVocabulary(format)
                                            }
                                        )
                                    }
                                }
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
                    onSelect = { item ->
                        destination = item
                        exportMenuExpanded = false
                    }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (destination) {
                Destination.SEND -> SendScreen(viewModel, state)
                Destination.NOTES -> NotesScreen(viewModel, state, notesSearchVisible)
                Destination.REMOTE -> RemoteScreen(viewModel, state)
                Destination.VOCABULARY -> VocabularyScreen(viewModel, state, vocabularySearchVisible)
                Destination.SETTINGS -> SettingsScreen(viewModel, state)
            }
        }
    }
}

@Composable
private fun SyncAction(syncing: Boolean, onClick: () -> Unit, description: String) {
    if (syncing) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    } else {
        TactileIconButton(onClick = onClick) { Icon(Icons.Filled.Sync, contentDescription = localizedText(description)) }
    }
}

@Composable
private fun TactileIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 3.dp).size(42.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun KPilotBottomBar(selected: Destination, onSelect: (Destination) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 7.dp
    ) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp)) {
            Destination.entries.forEach { item ->
                val isSelected = selected == item
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                        .clickable { onSelect(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                    contentDescription = localizedText(item.label),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp).size(29.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SendScreen(viewModel: MainViewModel, state: UiState) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), viewModel::addTransferUris)
    val readyCount = state.transfers.count { it.status == TransferStatus.READY }
    val sending = state.transfers.any {
        it.status in setOf(TransferStatus.CHECKING, TransferStatus.WAITING_FOR_COLLISION, TransferStatus.UPLOADING)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            if (state.settings.host.isBlank()) {
                CompactBanner("请先在设置中填写 Kindle 地址", error = true)
            }
        }
        item {
            Button(
                onClick = { picker.launch(SupportedTransferTypes.pickerMimeTypes) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.FilePresent, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("选择书籍")
            }
        }
        if (readyCount > 0) {
            item {
                FilledTonalButton(
                    onClick = viewModel::sendReadyTransfers,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sending
                ) { Text("发送 $readyCount 本到 Kindle") }
            }
        }
        if (state.transfers.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = "还没有待发送的书籍",
                    subtitle = "支持 EPUB、PDF、MOBI、AZW3 和 TXT"
                )
            }
        } else {
            item {
                Text("发送队列", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(state.transfers, key = TransferItem::id) { item ->
                TransferRow(item, viewModel)
            }
            item {
                Text(
                    state.transferMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
            if (TransferQueueRules.canRetry(item.status) || TransferQueueRules.canRemove(item.status)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (TransferQueueRules.canRetry(item.status)) {
                        TextButton(onClick = { viewModel.retryTransfer(item.id) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Text("重试")
                        }
                    }
                    if (TransferQueueRules.canRemove(item.status)) {
                        TextButton(onClick = { viewModel.removeTransfer(item.id) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                            Text("移除")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesScreen(viewModel: MainViewModel, state: UiState, searchVisible: Boolean) {
    val notesListState = rememberLazyListState()
    val selected = state.selectedNoteId?.let { id -> state.notes.firstOrNull { it.id == id } }
    if (selected != null) {
        NoteDetail(selected)
        return
    }

    val context = LocalContext.current
    val visible = viewModel.visibleNotes()
    val bookCounts = state.notes.groupingBy { it.bookTitle }.eachCount().toSortedMap()
    var showBooks by remember { mutableStateOf(false) }
    var showFailureReason by remember { mutableStateOf(false) }

    if (showBooks) {
        ModalBottomSheet(onDismissRequest = { showBooks = false }) {
            Text("选择书籍", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("全部书籍") },
                        supportingContent = { Text("${state.notes.size} 条") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                        trailingContent = { if (state.selectedBook == null) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { viewModel.selectNotesBook(null); showBooks = false }
                    )
                }
                items(bookCounts.entries.toList(), key = { it.key }) { (book, count) ->
                    ListItem(
                        headlineContent = { Text(book, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("$count 条") },
                        trailingContent = { if (state.selectedBook == book) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { viewModel.selectNotesBook(book); showBooks = false }
                    )
                }
            }
        }
    }

    if (showFailureReason) {
        AlertDialog(
            onDismissRequest = { showFailureReason = false },
            icon = { Icon(Icons.Filled.ErrorOutline, contentDescription = null) },
            title = { Text("同步失败") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.notesMessage)
                    state.notesDiagnosticStage?.let { Text("阶段：$it") }
                    state.notesDiagnosticCode?.let { code ->
                        AssistChip(
                            onClick = { copyDiagnosticCode(context, code) },
                            label = { Text("诊断码 $code · 点按复制") }
                        )
                    }
                    Text("手机中上一次成功同步的内容仍然保留。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showFailureReason = false }) { Text("关闭") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (state.notesSyncing) {
            val total = state.notesTotal
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (state.notesDownloaded.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Column(
            Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.notesError) {
                CompactErrorBanner(
                    message = state.notesMessage,
                    retryEnabled = NotesRetryPolicy.canRetry(state.settings.host, state.notesSyncing),
                    onRetry = viewModel::syncNotes,
                    onDetails = { showFailureReason = true }
                )
            }
            if (searchVisible) {
                OutlinedTextField(
                    value = state.notesQuery,
                    onValueChange = viewModel::updateNotesQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索摘抄、书名或笔记") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            if (state.notes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxHeight().clickable { showBooks = true }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            state.selectedBook ?: "全部书籍",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${visible.size} 条",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    // 全部书籍固定按时间排序，仅书内可切换按位置/按时间。
                    Row(
                        modifier = Modifier.weight(0.82f).fillMaxHeight().then(
                            if (state.selectedBook == null) Modifier else Modifier.clickable {
                                viewModel.setNotesSortOrder(
                                    if (state.notesSortOrder == NotesSortOrder.LOCATION) NotesSortOrder.ADDED_AT else NotesSortOrder.LOCATION
                                )
                            }
                        ).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (state.selectedBook != null && state.notesSortOrder == NotesSortOrder.LOCATION) "按位置" else "按时间",
                            maxLines = 1,
                            softWrap = false,
                            color = if (state.selectedBook == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.selectedBook != null) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        when {
            state.notes.isEmpty() && !state.notesSyncing -> EmptyState(
                icon = Icons.AutoMirrored.Filled.Notes,
                title = if (state.notesLastSync == null) "还没有摘抄" else "Kindle 摘抄为空",
                subtitle = "点按右上角同步，从 Kindle 读取摘抄"
            )
            visible.isEmpty() -> EmptyState(Icons.Filled.Search, "没有找到内容", "试试其他关键词或书籍")
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
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(note.kind.labelColor(), CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(note.kind.userLabel(), style = MaterialTheme.typography.labelLarge, color = note.kind.labelColor())
                if (note.kindleNotes.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            "${note.kindleNotes.size} 条笔记",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                note.addedAt?.let {
                    Text(formatNoteDate(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                note.text.ifBlank { "无摘抄文字" },
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBook) {
                    Text(
                        note.bookTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = localizedText("查看摘抄详情"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    }
}

@Composable
private fun NoteDetail(note: KindleClipping) {
    var showDetails by rememberSaveable(note.id) { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.kind.userLabel(), color = note.kind.labelColor(), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    note.addedAt?.let(::formatDate) ?: note.referenceText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Text(note.text.ifBlank { "无摘抄文字" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Normal)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(note.bookTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                note.author?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (note.kindleNotes.isNotEmpty()) {
            item { Text("Kindle 笔记", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold) }
            items(note.kindleNotes, key = KindleNote::id) { kindleNote ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Text("笔记", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.weight(1f))
                            Text(kindleNote.referenceText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(kindleNote.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        item {
            TextButton(onClick = { showDetails = !showDetails }) {
                Icon(if (showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                Text(if (showDetails) "收起原始信息" else "查看原始信息")
            }
        }
        if (showDetails) {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        note.addedAt?.let { Text("添加时间  ${formatDate(it)}", style = MaterialTheme.typography.bodySmall) }
                        Text(note.metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun VocabularyScreen(viewModel: MainViewModel, state: UiState, searchVisible: Boolean) {
    val visible = viewModel.visibleVocabularyWords()
    val selected = state.selectedVocabularyKey?.let { key -> state.vocabularyWords.firstOrNull { it.key == key } }
    selected?.let { word ->
        AlertDialog(
            onDismissRequest = { viewModel.selectVocabularyWord(null) },
            title = { Text(word.displayWord) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(word.lookups, key = { it.id }) { lookup ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(lookup.bookDisplay, style = MaterialTheme.typography.titleSmall)
                            lookup.position?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                            if (lookup.usage.isNotBlank()) Text(lookup.usage)
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.selectVocabularyWord(null) }) { Text("关闭") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (state.vocabularySyncing) {
            val total = state.vocabularyTotal
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (state.vocabularyDownloaded.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.vocabularyError) {
                CompactErrorBanner(state.vocabularyMessage, !state.vocabularySyncing, viewModel::syncVocabulary)
            }
            if (searchVisible) {
                OutlinedTextField(
                    value = state.vocabularyQuery,
                    onValueChange = viewModel::updateVocabularyQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索单词、书名或上下文") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            if (state.vocabularyWords.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${visible.size} 个单词",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilterChip(
                        selected = true,
                        onClick = {
                            viewModel.setVocabularySortOrder(
                                if (state.vocabularySortOrder == VocabularySortOrder.ALPHABETICAL) {
                                    VocabularySortOrder.LOOKUP_COUNT
                                } else {
                                    VocabularySortOrder.ALPHABETICAL
                                }
                            )
                        },
                        label = {
                            Text(
                                if (state.vocabularySortOrder == VocabularySortOrder.LOOKUP_COUNT) "按查询次数" else "按字母"
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }
        }
        when {
            state.vocabularyWords.isEmpty() && !state.vocabularySyncing -> EmptyState(Icons.Filled.Spellcheck, "还没有生词", "点按右上角同步 Kindle 生词本")
            visible.isEmpty() -> EmptyState(Icons.Filled.Search, "没有找到单词", "试试其他关键词")
            else -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp)
            ) {
                items(visible, key = VocabularyWord::key) { word ->
                    Column(
                        Modifier.fillMaxWidth().clickable { viewModel.selectVocabularyWord(word.key) }.padding(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(word.displayWord, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${word.lookups.size} 次", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        word.lookups.firstOrNull()?.let {
                            Text(it.bookDisplay, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (it.usage.isNotBlank()) Text(it.usage, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteScreen(viewModel: MainViewModel, state: UiState) {
    val context = LocalContext.current
    var pendingRecording by remember { mutableStateOf<FlipCommandDefinition?>(null) }
    val setRemoteEnabled: (Boolean) -> Unit = { enabled ->
        viewModel.setRemoteControlEnabled(enabled)
    }
    pendingRecording?.let { definition ->
        AlertDialog(
            onDismissRequest = { pendingRecording = null },
            icon = { Icon(Icons.Filled.RadioButtonChecked, contentDescription = null) },
            title = { Text("录制${definition.title}") },
            text = {
                Text("点按开始后，请在 5 秒内到 Kindle 上执行一次${definition.title}手势。旧录制会被替换。")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRecording = null
                    viewModel.recordFlipCommand(definition)
                }) { Text("开始录制") }
            },
            dismissButton = { TextButton(onClick = { pendingRecording = null }) { Text("取消") } }
        )
    }
    state.remoteRecordingCommandId?.let { commandId ->
        val definition = FlipCommandDefinition.all.firstOrNull { it.id == commandId }
        AlertDialog(
            onDismissRequest = {},
            icon = { CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp) },
            title = { Text("正在录制${definition?.title.orEmpty()}") },
            text = { Text("请现在到 Kindle 上执行一次对应的翻页手势，录制将在 5 秒后自动结束。") },
            confirmButton = {}
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).background(
                        if (state.remoteControlEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape
                    )
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("翻页遥控", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (state.remoteControlEnabled) "已开启" else "已关闭", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.remoteControlEnabled, onCheckedChange = setRemoteEnabled)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.submit(KindleCommand.PREVIOUS) },
                enabled = state.remoteControlEnabled,
                modifier = Modifier.weight(1f).height(88.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(30.dp))
                Text("上一页")
            }
            Button(
                onClick = { viewModel.submit(KindleCommand.NEXT) },
                enabled = state.remoteControlEnabled,
                modifier = Modifier.weight(1f).height(88.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("下一页")
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(30.dp))
            }
        }

        FlipCommandPanel(
            state = state,
            onCheck = viewModel::checkFlipCommands,
            onRecord = { pendingRecording = it }
        )

        Text("控制方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SettingsGroup {
            SwitchRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "手机音量键",
                subtitle = "音量加上一页，音量减下一页",
                checked = state.volumeKeysEnabled,
                enabled = state.remoteControlEnabled,
                onCheckedChange = viewModel::setVolumeKeysEnabled
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
            SwitchRow(
                icon = Icons.Filled.Keyboard,
                title = "蓝牙键盘方向键",
                subtitle = "↑、← 上一页，↓、→ 下一页",
                checked = state.keyboardKeysEnabled,
                enabled = state.remoteControlEnabled,
                onCheckedChange = viewModel::setKeyboardKeysEnabled
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
            SwitchRow(
                icon = Icons.Filled.Watch,
                title = "小米手环",
                subtitle = when {
                    state.xiaomiBandConnected -> "手机已连接"
                    state.xiaomiBandEnabled -> "等待手环连接"
                    else -> "未开启"
                },
                checked = state.xiaomiBandEnabled,
                onCheckedChange = viewModel::setXiaomiBandEnabled
            )
            if (state.xiaomiBandEnabled && !state.xiaomiBandConnected) {
                Text(
                    state.xiaomiBandMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.xiaomiBandError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 4.dp)
                )
                TextButton(onClick = viewModel::reconnectXiaomiBand, modifier = Modifier.padding(start = 48.dp)) { Text("重新连接") }
            }
            HorizontalDivider(Modifier.padding(start = 56.dp))
            ListItem(
                headlineContent = { Text("后台音量键") },
                supportingContent = { Text(if (state.accessibilityServiceEnabled) "系统权限已开启" else "需要辅助功能权限") },
                leadingContent = { Icon(Icons.Filled.Accessibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            )
        }
        if (state.busy || state.isError) CompactBanner(state.status, state.isError, state.busy)
    }
}

@Composable
private fun FlipCommandPanel(
    state: UiState,
    onCheck: () -> Unit,
    onRecord: (FlipCommandDefinition) -> Unit
) {
    var showAllCommands by rememberSaveable { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.RadioButtonChecked, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("翻页命令", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onCheck, enabled = !state.busy) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (state.remoteCommandsChecked) "刷新" else "检查")
                }
            }
            when {
                !state.remoteCommandsChecked -> {
                    Text(
                        "首次翻页前会自动检查 Kindle 中的 4 个事件文件，也可以现在检查。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showAllCommands = !showAllCommands }) {
                        Icon(if (showAllCommands) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        Text(if (showAllCommands) "收起录制选项" else "查看录制选项")
                    }
                    if (showAllCommands) {
                        FlipCommandRows(
                            definitions = FlipCommandDefinition.all,
                            checked = false,
                            missing = emptyList(),
                            enabled = !state.busy,
                            onRecord = onRecord
                        )
                    }
                }
                state.missingFlipCommands.isEmpty() -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("4 个翻页命令都已就绪", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showAllCommands = !showAllCommands }) {
                            Text(if (showAllCommands) "收起" else "管理")
                        }
                    }
                    if (showAllCommands) {
                        FlipCommandRows(
                            definitions = FlipCommandDefinition.all,
                            checked = true,
                            missing = emptyList(),
                            enabled = !state.busy,
                            onRecord = onRecord
                        )
                    }
                }
                else -> {
                    Text(
                        "缺少 ${state.missingFlipCommands.size} 个命令。点按录制后，请在 5 秒内操作 Kindle。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlipCommandRows(
                        definitions = if (showAllCommands) FlipCommandDefinition.all else state.missingFlipCommands,
                        checked = true,
                        missing = state.missingFlipCommands,
                        enabled = !state.busy,
                        onRecord = onRecord
                    )
                    TextButton(onClick = { showAllCommands = !showAllCommands }) {
                        Icon(if (showAllCommands) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        Text(if (showAllCommands) "只看缺失命令" else "管理全部命令")
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipCommandRows(
    definitions: List<FlipCommandDefinition>,
    checked: Boolean,
    missing: List<FlipCommandDefinition>,
    enabled: Boolean,
    onRecord: (FlipCommandDefinition) -> Unit
) {
    definitions.forEachIndexed { index, definition ->
        val ready = checked && definition !in missing
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ready) Icons.Filled.CheckCircle else Icons.Filled.Info,
                contentDescription = null,
                tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(definition.title, fontWeight = FontWeight.Medium)
                Text(
                    definition.remotePath.substringAfterLast('/'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { onRecord(definition) }, enabled = enabled) {
                Icon(Icons.Filled.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (ready) "重新录制" else "录制")
            }
        }
        if (index != definitions.lastIndex) HorizontalDivider()
    }
}

@Composable
private fun SettingsScreen(viewModel: MainViewModel, state: UiState) {
    val settings = state.settings
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            title = { Text("连接 Kindle") },
            text = {
                Text(
                    "请确保 Kindle 已开启 SSH（UsbNet 或 UsbNetLite），并与手机处于可互通的网络。翻页还需要 Kindle 中已有 FlipCmd 事件文件。后台或熄屏运行可能受 Android 或厂商电池策略限制；是否在系统设置中允许 KPilot 不受电池优化限制，由你自行决定。"
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("知道了") } }
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Kindle 连接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = settings.host,
            onValueChange = { viewModel.updateSettings(settings.copy(host = it.trim())) },
            label = { Text("IP 地址或主机名") },
            placeholder = { Text("例如 192.168.15.244") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        OutlinedTextField(
            value = settings.password,
            onValueChange = { viewModel.updateSettings(settings.copy(password = it)) },
            label = { Text("密码（可留空）") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Icon(if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            Text(if (showAdvanced) "收起高级设置" else "高级设置")
        }
        if (showAdvanced) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = settings.port.toString(),
                    onValueChange = { value -> value.toIntOrNull()?.let { viewModel.updateSettings(settings.copy(port = it)) } },
                    label = { Text("端口") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = settings.username,
                    onValueChange = { viewModel.updateSettings(settings.copy(username = it.trim())) },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(16.dp)
                )
            }
            OutlinedTextField(
                value = settings.eventDevice,
                onValueChange = { viewModel.updateSettings(settings.copy(eventDevice = it.trim())) },
                label = { Text("触控设备（留空自动检测）") },
                placeholder = { Text("例如 event1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedButton(
                onClick = viewModel::detectTouchDevice,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("自动检测触控设备")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = viewModel::save, modifier = Modifier.weight(1f)) { Text("保存") }
            OutlinedButton(onClick = viewModel::testConnection, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("测试连接") }
        }
        if (state.busy || state.isError || state.status != "请填写 Kindle 连接信息") {
            CompactBanner(state.status, state.isError, state.busy)
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        ListItem(
            headlineContent = { Text("连接帮助") },
            supportingContent = { Text("SSH 与翻页功能的准备事项") },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { showHelp = true }
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        SupportKPilotEntry()
    }
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
