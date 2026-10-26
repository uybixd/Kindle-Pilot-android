package com.hongxiang.kpilot.review

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.data.cacheScope
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.i18n.localizedText
import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.notes.NoteDraft
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Standalone destination: its local deck and history are scoped to the selected Kindle. */
@Composable
fun ReviewScreen(
    notes: List<KindleClipping>,
    settings: ConnectionSettings,
    modifier: Modifier = Modifier,
    noteDrafts: Map<String, NoteDraft> = emptyMap(),
    onOpenNote: (String) -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val controller = remember(context, coroutineScope) { ReviewController(File(context.filesDir, "review"), coroutineScope) }
    val state by controller.state.collectAsState()
    val scrollState = rememberScrollState()
    val namespace = settings.cacheScope()
    LaunchedEffect(namespace, notes) { controller.updateSource(settings, notes) }
    LaunchedEffect(state.current?.id) { scrollState.scrollTo(0) }

    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("每天重逢一段好文字", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("今天已复习 ${state.todayCount} 条", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (state.canUndo) {
                TextButton(onClick = { controller.undo() }, enabled = !state.saving) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("撤销")
                }
            }
        }

        state.error?.let { error ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { controller.retry(settings, notes) }) { Text("重试") }
                }
            }
        }

        val clipping = state.current
        if (clipping == null) {
            ReviewCompletion(state, onNext = { controller.nextBatch() }, onRestore = { controller.restoreExcluded() })
        } else {
            val completed = (state.batchTotal - state.batchRemaining).coerceAtLeast(0)
            LinearProgressIndicator(
                progress = { if (state.batchTotal == 0) 0f else completed.toFloat() / state.batchTotal },
                modifier = Modifier.fillMaxWidth()
            )
            Text("本组 ${completed + 1} / ${state.batchTotal}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReviewCard(
                clipping = clipping,
                annotation = reviewAnnotation(clipping, noteDrafts),
                favorite = state.record?.favorite == true,
                enabled = !state.saving,
                onOpen = { onOpenNote(clipping.id) },
                onAction = { controller.complete(it) }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { controller.complete(ReviewAction.FAVORITE) }, modifier = Modifier.weight(1f), enabled = !state.saving) {
                    Icon(Icons.Filled.BookmarkBorder, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("收藏")
                }
                Button(onClick = { controller.complete(ReviewAction.NEXT) }, modifier = Modifier.weight(1f), enabled = !state.saving) { Text("下一条") }
            }
            TextButton(onClick = { controller.complete(ReviewAction.EXCLUDE) }, modifier = Modifier.align(Alignment.CenterHorizontally), enabled = !state.saving) {
                Icon(Icons.Filled.VisibilityOff, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("不再复习")
            }
            Text("左滑收藏，右滑不再复习", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (state.favoriteCount > 0) Text("已收藏 ${state.favoriteCount} 条", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.excludedCount > 0 && clipping != null) {
            TextButton(onClick = { controller.restoreExcluded() }, enabled = !state.saving) { Text("恢复不再复习的摘抄（${state.excludedCount} 条）") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ReviewCard(
    clipping: KindleClipping,
    annotation: String?,
    favorite: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onAction: (ReviewAction) -> Unit
) {
    var drag by remember(clipping.id) { mutableFloatStateOf(0f) }
    Card(
        modifier = Modifier.fillMaxWidth().pointerInput(clipping.id, enabled) {
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onHorizontalDrag = { change, offset ->
                    if (enabled) {
                        change.consume()
                        drag += offset
                    }
                },
                onDragCancel = { drag = 0f },
                onDragEnd = {
                    if (enabled) {
                        val threshold = 96.dp.toPx()
                        when {
                            drag < -threshold -> onAction(ReviewAction.FAVORITE)
                            drag > threshold -> onAction(ReviewAction.EXCLUDE)
                        }
                    }
                    drag = 0f
                }
            )
        },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    clipping.addedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: "日期未知",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (favorite) Icon(Icons.Filled.Bookmark, contentDescription = localizedText("已收藏"), tint = MaterialTheme.colorScheme.tertiary)
            }
            Text(clipping.text, style = MaterialTheme.typography.headlineSmall)
            annotation?.let { note ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("我的批注", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(clipping.bookTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                clipping.author?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                clipping.referenceText.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(onClick = onOpen) {
                Text("查看摘抄详情")
                Icon(Icons.Filled.ExpandMore, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ReviewCompletion(state: ReviewUiState, onNext: () -> Unit, onRestore: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (state.candidateCount > 0) "本组复习完成" else "没有可复习的摘抄", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (state.candidateCount > 0) "今天已复习 ${state.todayCount} 条" else "先同步 Kindle 摘抄，再来复习。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.candidateCount > 0) {
                Button(onClick = onNext, enabled = !state.saving) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("再来一组")
                }
            }
            if (state.excludedCount > 0) {
                OutlinedButton(onClick = onRestore, enabled = !state.saving) { Text("恢复不再复习的摘抄（${state.excludedCount} 条）") }
            }
        }
    }
}
