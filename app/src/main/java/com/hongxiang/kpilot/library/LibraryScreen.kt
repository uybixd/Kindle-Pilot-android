package com.hongxiang.kpilot.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.i18n.localizedText
import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.ui.theme.SmartisanTeal
import com.hongxiang.kpilot.ui.LocalTabActive
import com.hongxiang.kpilot.ui.ClippingPreviewCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    snapshot: LibrarySnapshot?, clippingsByPath: Map<String, List<KindleClipping>>,
    coverLoader: BookCoverLoader, syncing: Boolean,
    repairingPath: String?, repairMessage: String?, repairError: Boolean, coverRevision: Int,
    deletingPath: String?, deleteMessage: String?, deleteError: Boolean,
    onRefresh: () -> Unit, onRefreshClippings: () -> Unit,
    onOpenClipping: (KindleClipping) -> Unit,
    onRepairCover: (KindleBook) -> Unit, onDismissRepairMessage: () -> Unit,
    onDeleteBook: (KindleBook, Boolean) -> Unit, onDismissDeleteMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPath by rememberSaveable(snapshot?.sourceKey) { mutableStateOf<String?>(null) }
    var pendingActionPath by rememberSaveable(snapshot?.sourceKey) { mutableStateOf<String?>(null) }
    var pendingRepairPath by rememberSaveable(snapshot?.sourceKey) { mutableStateOf<String?>(null) }
    var pendingDeletionPath by rememberSaveable(snapshot?.sourceKey) { mutableStateOf<String?>(null) }
    val books = snapshot?.books.orEmpty()
    val selected = books.firstOrNull { it.remotePath == selectedPath }
    val pendingAction = books.firstOrNull { it.remotePath == pendingActionPath }
    val pendingRepair = books.firstOrNull { it.remotePath == pendingRepairPath }
    val pendingDeletion = books.firstOrNull { it.remotePath == pendingDeletionPath }
    val gridState = key(snapshot?.sourceKey) { rememberLazyGridState() }
    val isActive = LocalTabActive.current
    val canManage = !syncing && repairingPath == null && deletingPath == null
    BackHandler(isActive && selected != null) { selectedPath = null }
    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = syncing && isActive,
            onRefresh = if (selected != null) onRefreshClippings else onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {
                if (selected != null) {
                    val notes = clippingsByPath[selected.remotePath]
                    if (notes == null) {
                        Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                            BookClippingsHeader(selected, null, onBack = { selectedPath = null })
                        }
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item(key = "book-header") {
                            BookClippingsHeader(selected, notes.size, onBack = { selectedPath = null })
                        }
                        if (notes.isEmpty()) item {
                            LibraryEmpty("这本书还没有摘抄", Modifier.fillParentMaxHeight(.7f))
                        }
                        items(notes, key = { it.id }) { clipping ->
                            ClippingPreviewCard(clipping, showBook = false, onClick = { onOpenClipping(clipping) })
                        }
                    }
                } else if (books.isEmpty()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            LibraryEmpty(
                                if (snapshot == null) "尚未读取 Kindle 书库" else "Kindle 书库为空",
                                Modifier.fillParentMaxSize()
                            )
                        }
                    }
                } else LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    items(books, key = { it.remotePath }) { book ->
                        Column(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(
                                onClick = { selectedPath = book.remotePath },
                                onLongClickLabel = if (canManage) localizedText("更多操作") else null,
                                onLongClick = if (canManage) ({ pendingActionPath = book.remotePath }) else null
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BookCover(book, coverLoader, coverRevision, Modifier.fillMaxWidth().aspectRatio(.70f))
                            Text(
                                book.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    if (isActive && pendingAction != null) {
        val book = pendingAction
        ModalBottomSheet(onDismissRequest = { pendingActionPath = null }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                if (LibraryRules.isCoverRepairPath(book.remotePath)) {
                    ListItem(
                        headlineContent = { Text("修复 Kindle 封面") },
                        leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                        modifier = Modifier.clickable {
                            pendingActionPath = null
                            pendingRepairPath = book.remotePath
                        }
                    )
                }
                ListItem(
                    headlineContent = {
                        Text("从 Kindle 删除", color = MaterialTheme.colorScheme.error)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Delete, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable {
                        pendingActionPath = null
                        pendingDeletionPath = book.remotePath
                    }
                )
                TextButton(
                    onClick = { pendingActionPath = null },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                ) { Text("取消") }
            }
        }
    }
    if (isActive && pendingDeletion != null) {
        val book = pendingDeletion
        ModalBottomSheet(onDismissRequest = { pendingDeletionPath = null }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("从 Kindle 删除“${book.title}”？", style = MaterialTheme.typography.titleLarge)
                Text(
                    "仅删除书籍会保留笔记和阅读进度；删除书籍和阅读记录会同时删除笔记和阅读进度，无法在 App 内撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ListItem(
                    headlineContent = {
                        Text("仅删除书籍", color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable {
                        pendingDeletionPath = null
                        onDeleteBook(book, false)
                    }
                )
                ListItem(
                    headlineContent = {
                        Text("删除书籍和阅读记录", color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable {
                        pendingDeletionPath = null
                        onDeleteBook(book, true)
                    }
                )
                TextButton(
                    onClick = { pendingDeletionPath = null },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("取消") }
            }
        }
    }
    pendingRepair?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingRepairPath = null },
            title = { Text("修复“${book.title}”的 Kindle 封面？") },
            text = {
                Text(
                    "从书籍提取封面并直接替换 Kindle 书架缩略图（不备份原图）。" +
                        "不修改正文、笔记或阅读进度。Kindle 联网后可能再次覆盖封面，届时可重复修复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRepairPath = null
                    onRepairCover(book)
                }) { Text("替换封面") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRepairPath = null }) { Text("取消") }
            }
        )
    }
    if (repairingPath != null) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                val title = books.firstOrNull { it.remotePath == repairingPath }?.title
                Text(if (title != null) "正在修复“$title”的 Kindle 封面…" else "正在修复 Kindle 封面…")
            },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("正在从书籍提取封面并写入 Kindle…")
                }
            },
            confirmButton = {}
        )
    }
    repairMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissRepairMessage,
            title = { Text(if (repairError) "封面修复失败" else "封面修复完成") },
            text = {
                Text(
                    message,
                    color = if (repairError) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissRepairMessage) { Text("确定") }
            }
        )
    }
    if (deletingPath != null) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                val title = books.firstOrNull { it.remotePath == deletingPath }?.title
                Text(if (title != null) "正在从 Kindle 删除“$title”…" else "正在从 Kindle 删除书籍…")
            },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("正在删除 Kindle 上的书籍文件…")
                }
            },
            confirmButton = {}
        )
    }
    deleteMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissDeleteMessage,
            title = { Text(if (deleteError) "书籍删除失败" else "书籍删除完成") },
            text = {
                Text(
                    message,
                    color = if (deleteError) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissDeleteMessage) { Text("确定") }
            }
        )
    }
    }
}

@Composable
private fun BookClippingsHeader(book: KindleBook, count: Int?, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回书库"))
        }
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleLarge)
            count?.let {
                Text("$it 条摘抄", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LibraryEmpty(title: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BookCover(book: KindleBook, loader: BookCoverLoader, revision: Int, modifier: Modifier) {
    var bitmap by remember(book.coverPath, loader, revision) { mutableStateOf(loader.cached(book.coverPath)) }
    LaunchedEffect(book.coverPath, loader, revision) {
        bitmap = book.coverPath?.let { loader.load(it) }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: BookCoverPlaceholder(book, Modifier.fillMaxSize())
        book.percentFinished?.takeIf { it > 0 }?.let { percent ->
            LinearProgressIndicator(
                progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = .8f)
            )
        }
    }
}

@Composable
private fun BookCoverPlaceholder(book: KindleBook, modifier: Modifier) {
    Box(
        modifier.background(Brush.linearGradient(listOf(lerp(SmartisanTeal, Color.Black, .12f), SmartisanTeal))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            Icon(
                if (book.format == "PDF") Icons.Filled.Description else Icons.Filled.Book,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White.copy(alpha = .7f)
            )
            Text(
                book.title,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = Color.White
            )
        }
    }
}
