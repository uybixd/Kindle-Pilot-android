package com.hongxiang.kpilot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hongxiang.kpilot.MainViewModel
import com.hongxiang.kpilot.UiState
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.review.ClippingsReviewPlanner
import com.hongxiang.kpilot.review.VocabularyReviewPlanner
import com.hongxiang.kpilot.review.VocabularyReviewQueue
import com.hongxiang.kpilot.review.VocabularyReviewResult
import com.hongxiang.kpilot.review.VocabularyReviewState
import com.hongxiang.kpilot.vocabulary.VocabularyWord
import kotlinx.coroutines.delay

internal fun reviewCountToday(state: UiState): Int =
    state.clippingReviewDailyCounts[ClippingsReviewPlanner.reviewDayKey(System.currentTimeMillis())] ?: 0

internal fun hasReviewableClippings(state: UiState): Boolean =
    state.notes.any(ClippingsReviewPlanner::isEligible)

@Composable
internal fun ClippingReviewEntry(todayCount: Int, onClick: () -> Unit) = ReadingReviewEntry(
    icon = Icons.Filled.AutoAwesome,
    summary = if (todayCount == 0) "遇见一条过去的摘抄" else "今天已回顾 $todayCount 条",
    onClick = onClick
)

@Composable
internal fun VocabularyReviewEntry(
    newCount: Int,
    learningCount: Int,
    reviewCount: Int,
    onClick: () -> Unit
) = ReadingReviewEntry(
    icon = Icons.Filled.Style,
    summary = "新词 $newCount · 学习中 $learningCount · 待复习 $reviewCount",
    onClick = onClick
)

@Composable
private fun ReadingReviewEntry(icon: ImageVector, summary: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(
            Modifier.padding(horizontal = readingListInset, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("回顾", style = MaterialTheme.typography.labelLarge)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = readingListSecondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f))
        }
    }
}

@Composable
internal fun VocabularyReviewDialog(model: MainViewModel, state: UiState, onClose: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val word = state.vocabularyReviewKey?.let { key ->
        state.vocabularyWords.firstOrNull { it.key == key }
    }
    var answerVisible by remember(word?.key) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
            model.refreshVocabularyReview(now)
        }
    }
    val queue = model.vocabularyReviewQueue(now)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onClose) { Text("关闭") }
                    Text(
                        "回顾",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(
                        onClick = {
                            answerVisible = false
                            model.undoVocabularyReview()
                        },
                        enabled = state.vocabularyReviewUndo != null
                    ) {
                        Icon(Icons.Filled.Undo, "撤销上次评分")
                    }
                }
                if (word == null) {
                    VocabularyCompletionContent(
                        queue = queue,
                        skippedCount = model.vocabularyReviewSkippedCount(),
                        now = now,
                        onClose = onClose
                    )
                } else {
                    VocabularyReviewContent(
                        word = word,
                        state = state,
                        model = model,
                        now = now,
                        answerVisible = answerVisible,
                        onReveal = { answerVisible = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun VocabularyReviewContent(
    word: VocabularyWord,
    state: UiState,
    model: MainViewModel,
    now: Long,
    answerVisible: Boolean,
    onReveal: () -> Unit
) {
    val lookup = word.lookups.lastOrNull()
    val definition = state.vocabularyDefinitions[word.key]?.definition.orEmpty()
    val queue = model.vocabularyReviewQueue(now)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QueueCount("新词", queue.newCount, Modifier.weight(1f))
            QueueCount("学习中", queue.learningCount, Modifier.weight(1f))
            QueueCount("待复习", queue.reviewCount, Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onReveal)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stateLabel(state.vocabularyReviewRecords[word.key]?.state ?: VocabularyReviewState.NEW),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        Text(
                            word.displayWord,
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 48.sp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!answerVisible) {
                        Text(
                            "先在心里回忆词义，再显示答案",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        lookup?.stem?.takeIf { it.isNotBlank() && !it.equals(lookup.word, true) }?.let {
                            Text(
                                "原形：$it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            definition.ifBlank { "还没有释义，可以稍后在生词详情中补充。" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (definition.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                        lookup?.usage?.takeIf { it.isNotBlank() }?.let {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "上下文",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                SelectionContainer { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                        lookup?.bookDisplay?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.vocabularyDefinitions[word.key]?.note?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        if (answerVisible) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RatingButton("忘记", VocabularyReviewResult.AGAIN, word, state, model, now, Modifier.weight(1f))
                    RatingButton("困难", VocabularyReviewResult.HARD, word, state, model, now, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RatingButton("记得", VocabularyReviewResult.GOOD, word, state, model, now, Modifier.weight(1f))
                    RatingButton("简单", VocabularyReviewResult.EASY, word, state, model, now, Modifier.weight(1f))
                }
                TextButton(
                    onClick = { model.performVocabularyReview(VocabularyReviewResult.SKIPPED, word.key, now) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("稍后再学") }
            }
        } else {
            Button(
                onClick = onReveal,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
            ) { Text("显示答案") }
        }
    }
}

@Composable
private fun RatingButton(
    title: String,
    result: VocabularyReviewResult,
    word: VocabularyWord,
    state: UiState,
    model: MainViewModel,
    now: Long,
    modifier: Modifier
) {
    val updated = VocabularyReviewPlanner.updatedRecord(
        word.key, state.vocabularyReviewRecords[word.key], result, now
    )
    val hint = updated?.let { record ->
        if (record.state == VocabularyReviewState.REVIEW) "${record.intervalDays} 天"
        else {
            val seconds = (((record.dueAt ?: now) - now).coerceAtLeast(1_000)) / 1_000
            if (seconds < 60) "$seconds 秒" else "${seconds / 60} 分钟"
        }
    }.orEmpty()
    OutlinedButton(
        onClick = { model.performVocabularyReview(result, word.key, now) },
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun QueueCount(title: String, count: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VocabularyCompletionContent(
    queue: VocabularyReviewQueue,
    skippedCount: Int,
    now: Long,
    onClose: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            null,
            Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(20.dp))
        Text(
            if (queue.nextLearningDueAt == null) "当前复习已完成" else "稍后继续学习",
            style = MaterialTheme.typography.titleLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.size(10.dp))
        Text(
            when {
                queue.nextLearningDueAt != null -> "还有学习中的单词，将在到期后自动出现。"
                skippedCount > 0 -> "当前没有可学的单词，跳过的单词下次打开时会重新加入。"
                else -> "新词和当前到期的单词已学完，可以休息了。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        queue.nextLearningDueAt?.let { due ->
            Spacer(Modifier.size(8.dp))
            Text(relativeDue(due - now), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.size(24.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("先休息") }
    }
}

private fun relativeDue(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) / 1_000)
    return when {
        seconds < 60 -> "$seconds 秒后"
        seconds < 3_600 -> "${seconds / 60} 分钟后"
        else -> "${seconds / 3_600} 小时后"
    }
}

private fun stateLabel(state: VocabularyReviewState): String = when (state) {
    VocabularyReviewState.NEW -> "新词"
    VocabularyReviewState.LEARNING -> "学习中"
    VocabularyReviewState.REVIEW -> "待复习"
    VocabularyReviewState.RELEARNING -> "重新学习"
}
