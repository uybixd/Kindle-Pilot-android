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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    snapshot: LibrarySnapshot?, clippings: List<KindleClipping>, syncing: Boolean,
    errorMessage: String?, onRefresh: () -> Unit, onRefreshClippings: () -> Unit,
    onOpenClipping: (KindleClipping) -> Unit, modifier: Modifier = Modifier
) {
    var selectedPath by rememberSaveable(snapshot?.sourceKey) { mutableStateOf<String?>(null) }
    val books = snapshot?.books.orEmpty()
    val selected = books.firstOrNull { it.remotePath == selectedPath }
    val index = remember(clippings) { LibraryClippingsIndex(clippings) }
    val notesByPath = remember(books,index) { books.associate { it.remotePath to index.clippings(it) } }
    BackHandler(selected != null) { selectedPath = null }
    PullToRefreshBox(isRefreshing = syncing, onRefresh = if(selected != null) onRefreshClippings else onRefresh, modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if(selected != null) Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={selectedPath=null}) { Icon(Icons.AutoMirrored.Filled.ArrowBack,localizedText("返回书库")) }
                Text(selected.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            errorMessage?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall) }
            if(selected != null) {
                val notes = notesByPath[selected.remotePath].orEmpty()
                LazyColumn(Modifier.fillMaxSize()) {
                    if(notes.isEmpty()) item { LibraryEmpty("这本书还没有摘抄", "") }
                    items(notes,key={it.id}) { clipping ->
                        ListItem(headlineContent={Text(clipping.text.ifBlank{clipping.metadata},maxLines=4,overflow=TextOverflow.Ellipsis)},
                            supportingContent={Text(clipping.referenceText,style=MaterialTheme.typography.labelSmall)},
                            modifier=Modifier.clickable{onOpenClipping(clipping)})
                    }
                }
            } else if(books.isEmpty()) {
                LazyColumn(Modifier.fillMaxSize()) { item { LibraryEmpty("尚未读取 Kindle 书库", "") } }
            } else LazyVerticalGrid(columns=GridCells.Fixed(3),modifier=Modifier.fillMaxSize(),
                contentPadding=androidx.compose.foundation.layout.PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=28.dp),
                horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                items(books,key={it.remotePath}) { book ->
                    Column(Modifier.clickable{selectedPath=book.remotePath},verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Box {
                            BookCover(book,Modifier.fillMaxWidth().aspectRatio(.68f))
                            if((book.percentFinished ?: 0.0)>0) Surface(color=MaterialTheme.colorScheme.surface.copy(alpha=.9f),modifier=Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                                BookProgress(book)
                            }
                        }
                        Text(book.title,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("${notesByPath[book.remotePath].orEmpty().size} 条摘抄",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
            if(description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
