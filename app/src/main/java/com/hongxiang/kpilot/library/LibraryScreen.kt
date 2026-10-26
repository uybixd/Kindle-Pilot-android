package com.hongxiang.kpilot.library

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.i18n.localizedText
import com.hongxiang.kpilot.notes.KindleClipping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Stateless data boundary: navigation within a book and presentation preferences stay in Compose. */
@Composable
fun LibraryScreen(
    snapshot: LibrarySnapshot?,
    clippings: List<KindleClipping>,
    syncing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onSendBooks: () -> Unit,
    onOpenClipping: (KindleClipping) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable(snapshot?.sourceKey) { mutableStateOf("") }
    var grid by rememberSaveable { mutableStateOf(true) }
    var selectedPath by rememberSaveable(snapshot?.sourceKey) { mutableStateOf<String?>(null) }
    val books = snapshot?.books.orEmpty()
    val selected = books.firstOrNull { it.remotePath == selectedPath }
    val clippingIndex = remember(clippings) { LibraryClippingsIndex(clippings) }
    val notesByPath = remember(books, clippingIndex) { books.associate { it.remotePath to clippingIndex.clippings(it) } }
    val visible = remember(books, query) {
        val needle = LibraryRules.normalizeTitle(query)
        books.filter { needle.isBlank() || LibraryRules.normalizeTitle("${it.title} ${it.author.orEmpty()} ${it.fileName}").contains(needle) }
    }

    BackHandler(enabled = selected != null) { selectedPath = null }
    Column(modifier.fillMaxSize()) {
        if (syncing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) {
                    IconButton(onClick = { selectedPath = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回书库"))
                    }
                    Text(selected.title, modifier = Modifier.weight(1f), maxLines = 2,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("${books.size} 本书", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { grid = !grid }) {
                        Icon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            localizedText(if (grid) "切换为列表" else "切换为网格"))
                    }
                }
                IconButton(onClick = onRefresh, enabled = !syncing) {
                    Icon(Icons.Filled.Refresh, localizedText("刷新书库"))
                }
                IconButton(onClick = onSendBooks) {
                    Icon(Icons.AutoMirrored.Filled.Send, localizedText("传书"))
                }
            }
            if (errorMessage != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall)
                        if (snapshot != null) Text("手机中上一次成功同步的书库仍然保留。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (selected == null && books.isNotEmpty()) {
                OutlinedTextField(value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("搜索书名、作者或文件名") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp))
                snapshot?.let {
                    val timestamp = remember(it.syncedAt) {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(it.syncedAt)
                    }
                    Text("上次同步：$timestamp", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        when {
            selected != null -> {
                val notes = notesByPath[selected.remotePath].orEmpty()
                if (notes.isEmpty()) {
                    LibraryEmpty("这本书还没有摘抄", "请先从“摘抄”页同步 Kindle 笔记，再回到书库查看。")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(notes, key = { it.id }) { clipping ->
                            ListItem(
                                headlineContent = { Text(clipping.text.ifBlank { clipping.metadata }, maxLines = 4,
                                    overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Column {
                                        Text(clipping.referenceText, style = MaterialTheme.typography.labelSmall)
                                        clipping.kindleNotes.lastOrNull()?.let { Text(it.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                    }
                                },
                                modifier = Modifier.clickable { onOpenClipping(clipping) }.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
            books.isEmpty() -> LibraryEmpty(
                if (snapshot == null) "尚未读取 Kindle 书库" else "Kindle 书库为空",
                "连接 Kindle 后刷新书库，也可以无线传书。",
                action = {
                    Button(onClick = onRefresh, enabled = !syncing) { Text("刷新书库") }
                    TextButton(onClick = onSendBooks) { Text("选择书籍传送") }
                }
            )
            visible.isEmpty() -> LibraryEmpty("没有找到书籍", "试试其他书名、作者或文件名")
            grid -> LazyVerticalGrid(columns = GridCells.Adaptive(108.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(visible, key = { it.remotePath }) { book ->
                    Column(Modifier.clickable { selectedPath = book.remotePath }.padding(bottom = 8.dp)) {
                        BookCover(book, Modifier.fillMaxWidth().aspectRatio(0.68f))
                        Spacer(Modifier.height(8.dp))
                        Text(book.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${notesByPath[book.remotePath].orEmpty().size} 条摘抄", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BookProgress(book)
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.remotePath }) { book ->
                    ListItem(
                        headlineContent = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                book.author?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                Text("${notesByPath[book.remotePath].orEmpty().size} 条摘抄 · ${book.format}")
                                BookProgress(book)
                            }
                        },
                        leadingContent = { BookCover(book, Modifier.width(54.dp).height(80.dp)) },
                        modifier = Modifier.clickable { selectedPath = book.remotePath }.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryEmpty(title: String, description: String, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}

@Composable
private fun BookProgress(book: KindleBook) {
    book.percentFinished?.let { percent ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (percent >= 99.5) "已读完" else "已读 ${percent.toInt()}%", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BookCover(book: KindleBook, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = book.coverPath) {
        value = book.coverPath?.let { path ->
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!File(path).isFile) return@runCatching null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    var sampling = 1
                    while (bounds.outWidth / sampling > 600 || bounds.outHeight / sampling > 900) sampling *= 2
                    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampling })?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(book.title, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
    }
}
