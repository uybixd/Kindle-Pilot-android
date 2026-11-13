package com.hongxiang.kpilot.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text as ContentText
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hongxiang.kpilot.EmptyState
import com.hongxiang.kpilot.InlineSearchField
import com.hongxiang.kpilot.MainViewModel
import com.hongxiang.kpilot.UiState
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.i18n.localizedText
import com.hongxiang.kpilot.ui.theme.KPilotTheme
import com.hongxiang.kpilot.vocabulary.VocabularyLookup
import com.hongxiang.kpilot.vocabulary.VocabularySortOrder
import com.hongxiang.kpilot.vocabulary.VocabularyWord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VocabularyScreen(
    viewModel: MainViewModel,
    state: UiState,
    searchVisible: Boolean,
    onCloseSearch: () -> Unit,
    searchFocusRequested: Boolean,
    onSearchFocused: () -> Unit
) {
    val listState = rememberLazyListState()
    if (state.vocabularyReviewPresented) {
        VocabularyReviewDialog(viewModel, state, onClose = viewModel::dismissVocabularyReview)
        return
    }
    val visible = remember(state.vocabularyWords, state.vocabularyQuery, state.vocabularySortOrder, state.vocabularyDefinitions) {
        viewModel.visibleVocabularyWords()
    }
    val selected = state.selectedVocabularyKey?.let { key -> state.vocabularyWords.firstOrNull { it.key == key } }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val isActive = LocalTabActive.current
    LaunchedEffect(isActive) {
        if (!isActive) sortMenuExpanded = false
    }

    if (selected != null && isActive) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectVocabularyWord(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background
        ) {
            VocabularyDetail(
                selected,
                definition = state.vocabularyDefinitions[selected.key]?.definition.orEmpty(),
                onSaveDefinition = { viewModel.saveVocabularyDefinition(selected.key, it) },
                onClose = { viewModel.selectVocabularyWord(null) }
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (searchVisible) Box(Modifier.padding(horizontal = readingListInset, vertical = 8.dp)) {
            InlineSearchField(
                state.vocabularyQuery, viewModel::updateVocabularyQuery,
                "搜索单词、释义、书名或上下文", onCloseSearch, searchFocusRequested, onSearchFocused
            )
        }
        if (!state.learningError && state.learningMessage.isNotBlank()) Text(
            state.learningMessage,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        if (state.vocabularyWords.isNotEmpty()) Row(
            Modifier.fillMaxWidth().padding(start = readingListInset, end = 24.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(
                        state.vocabularySortOrder.label(),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(Icons.Filled.ExpandMore, null, Modifier.padding(start = 4.dp).size(18.dp))
                }
                DropdownMenu(expanded = sortMenuExpanded && isActive, onDismissRequest = { sortMenuExpanded = false }) {
                    VocabularySortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label()) },
                            trailingIcon = {
                                if (state.vocabularySortOrder == order) Icon(Icons.Filled.Check, null)
                            },
                            onClick = {
                                viewModel.setVocabularySortOrder(order)
                                listState.requestScrollToItem(0)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Text(
                "${visible.size} 个单词",
                style = MaterialTheme.typography.bodySmall,
                color = readingListSecondaryText,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = readingListInset, end = readingListInset, top = 4.dp, bottom = 24.dp)
        ) {
            if (visible.isEmpty()) item {
                EmptyState(
                    Icons.Filled.Spellcheck,
                    if (state.vocabularyWords.isEmpty()) "还没有生词" else "没有找到单词",
                    ""
                )
            }
            if (state.learningLoaded && state.vocabularyQuery.isBlank() && state.vocabularyWords.isNotEmpty()) {
                item(key = "review-entry") {
                    val queue = viewModel.vocabularyReviewQueue()
                    VocabularyReviewEntry(
                        newCount = queue.newCount,
                        learningCount = queue.learningCount,
                        reviewCount = queue.reviewCount,
                        onClick = viewModel::presentVocabularyReview
                    )
                }
            }
            itemsIndexed(visible, key = { _, word -> word.key }) { index, word ->
                VocabularyWordRow(
                    word,
                    hasDefinition = state.vocabularyDefinitions[word.key]?.definition?.isNotBlank() == true,
                    first = index == 0,
                    last = index == visible.lastIndex,
                    onClick = { viewModel.selectVocabularyWord(word.key) }
                )
            }
        }
    }
}

private fun VocabularySortOrder.label(): String = when (this) {
    VocabularySortOrder.ALPHABETICAL -> "按字母"
    VocabularySortOrder.LOOKUP_COUNT -> "按查询次数"
}

@Composable
private fun VocabularyWordRow(
    word: VocabularyWord,
    hasDefinition: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit
) {
    val preview = word.lookups.firstOrNull { it.usage.isNotBlank() }?.usage
        ?: word.lookups.firstOrNull()?.bookDisplay.orEmpty()

    ReadingListRow(first = first, last = last, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f).alignByBaseline(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ContentText(
                    word.displayWord,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (hasDefinition) Icon(
                    Icons.Outlined.EditNote,
                    localizedText("已有释义"),
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "${word.lookups.size} 次",
                style = MaterialTheme.typography.bodySmall,
                color = readingListSecondaryText,
                modifier = Modifier.alignByBaseline(),
                maxLines = 1
            )
        }
        if (preview.isNotBlank()) ContentText(
            preview,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = readingListSecondaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun VocabularyDetail(
    word: VocabularyWord,
    definition: String,
    onSaveDefinition: (String) -> Unit,
    onClose: () -> Unit
) {
    var draft by remember(word.key, definition) { mutableStateOf(definition) }
    val context = LocalContext.current
    val lookupWord = word.lookups.lastOrNull()?.word.orEmpty()
    val dictionaryIntent = remember(lookupWord) {
        Intent(Intent.ACTION_VIEW, Uri.parse("dict://$lookupWord"))
    }
    val hasDictionary = remember(dictionaryIntent) {
        context.packageManager.resolveActivity(dictionaryIntent, 0) != null
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${word.lookups.size} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, localizedText("关闭")) }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(key = "word-heading") {
                SelectionContainer {
                    Text(word.displayWord, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                }
            }
            item(key = "definition") {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(
                        Modifier.fillMaxWidth().padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "释义",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            if (hasDictionary) TextButton(onClick = {
                                runCatching { context.startActivity(dictionaryIntent) }
                            }) { Text("系统词典") }
                        }
                        word.dictionaryEntry?.let { VocabularyDictionaryContent(it, lookupWord) }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            label = { Text("自定义释义") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = { onSaveDefinition(draft) },
                                enabled = draft.trim() != definition.trim()
                            ) { Text("保存释义") }
                        }
                    }
                }
            }
            items(word.lookups, key = VocabularyLookup::id) { lookup ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (lookup.usage.isNotBlank()) SelectionContainer {
                            Text(lookup.usage, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 29.sp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(lookup.bookDisplay, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            lookup.position?.let {
                                Text("位置 $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Vocabulary · grouped list", locale = "zh", showBackground = true, widthDp = 390)
@Preview(name = "Vocabulary · narrow large text", locale = "en", showBackground = true, widthDp = 320, fontScale = 1.4f)
@Composable
private fun VocabularyRowsPreview() {
    KPilotTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(readingListInset)) {
                VocabularyWordRow(previewWord("serendipity", "The Little Prince"), hasDefinition = true,
                    first = true, last = false, onClick = {})
                VocabularyWordRow(previewWord("uncharacteristically", "瓦尔登湖：一个人的自由生活"), hasDefinition = false,
                    first = false, last = false, onClick = {})
                VocabularyWordRow(previewWord("wander", "A Room of One’s Own", usage = ""), hasDefinition = false,
                    first = false, last = true, onClick = {})
            }
        }
    }
}

@Preview(name = "Vocabulary · reading context", locale = "zh", showBackground = true, widthDp = 390, heightDp = 720)
@Preview(name = "Vocabulary · narrow context", locale = "en", showBackground = true, widthDp = 320, heightDp = 480, fontScale = 2f)
@Composable
private fun VocabularyDetailPreview() {
    KPilotTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            VocabularyDetail(
                previewWord("uncharacteristically", "The Little Prince"),
                definition = "in a way that is not typical or usual",
                onSaveDefinition = {},
                onClose = {}
            )
        }
    }
}

private fun previewWord(
    word: String,
    title: String,
    usage: String = "There is a quiet kind of serendipity in finding the right book at just the right moment."
) = VocabularyWord(
    key = word,
    displayWord = word,
    lookups = listOf(
        VocabularyLookup(
            id = word, wordKey = word, word = word, stem = word, wordLanguage = "en",
            bookTitle = title, authors = "", position = "1248",
            usage = usage, timestamp = null
        )
    )
)
