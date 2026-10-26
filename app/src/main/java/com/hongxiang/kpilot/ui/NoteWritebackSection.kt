package com.hongxiang.kpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hongxiang.kpilot.UiState
import com.hongxiang.kpilot.data.cacheScope
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.notes.KindleClipping

@Composable
fun NoteWritebackSection(
    note: KindleClipping,
    state: UiState,
    onSave: (KindleClipping, String) -> Unit,
    onSend: (KindleClipping) -> Unit,
    onRestore: (KindleClipping) -> Unit,
    onRetryLoad: () -> Unit
) {
    val scope = state.settings.cacheScope()
    val draft = state.noteDrafts[note.id]
    val storedText = draft?.text ?: note.kindleNotes.lastOrNull()?.text.orEmpty()
    var editing by rememberSaveable(scope, note.id) { mutableStateOf(false) }
    var editorText by rememberSaveable(scope, note.id) { mutableStateOf(storedText) }
    var saveRequested by rememberSaveable(scope, note.id) { mutableStateOf(false) }
    var confirmingSend by rememberSaveable(scope, note.id) { mutableStateOf(false) }
    var confirmingRestore by rememberSaveable(scope, note.id) { mutableStateOf(false) }
    val writing = state.noteWritingId != null

    LaunchedEffect(state.noteDraftSaving, state.noteWriteError, saveRequested) {
        if (saveRequested && !state.noteDraftSaving) {
            if (!state.noteWriteError) editing = false
            saveRequested = false
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { if (!state.noteDraftSaving) editing = false },
            title = { Text("编辑批注") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editorText,
                        onValueChange = { editorText = it },
                        label = { Text("我的批注") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 10,
                        enabled = !state.noteDraftSaving
                    )
                    Text("先保存到本机，再发送到 Kindle。清空后发送可删除 Kindle 批注，划线会保留。", style = MaterialTheme.typography.bodySmall)
                    if (state.noteWriteError) Text(state.noteWriteMessage, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(note, editorText)
                    saveRequested = true
                }, enabled = !state.noteDraftSaving) { Text(if (state.noteDraftSaving) "保存中…" else "保存到本机") }
            },
            dismissButton = { TextButton(onClick = { editing = false }, enabled = !state.noteDraftSaving) { Text("取消") } }
        )
    }

    if (confirmingSend && draft != null) {
        AlertDialog(
            onDismissRequest = { confirmingSend = false },
            title = { Text(if (draft.text.isBlank()) "删除 Kindle 批注" else "发送批注到 Kindle") },
            text = { Text("请先在 Kindle 退出阅读器并返回主页。写入时会备份原文件，并校验写入结果。") },
            confirmButton = {
                TextButton(onClick = { confirmingSend = false; onSend(note) }) { Text("发送到 Kindle") }
            },
            dismissButton = { TextButton(onClick = { confirmingSend = false }) { Text("取消") } }
        )
    }
    if (confirmingRestore) {
        AlertDialog(
            onDismissRequest = { confirmingRestore = false },
            title = { Text("恢复同步时的 Kindle 批注") },
            text = { Text("将移除本机保存的批注副本，重新显示上次同步的 Kindle 批注。") },
            confirmButton = {
                TextButton(onClick = { confirmingRestore = false; onRestore(note) }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { confirmingRestore = false }) { Text("取消") } }
        )
    }

    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("我的批注", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    state.noteDraftsLoading -> "正在读取本地批注…"
                    draft?.text?.isBlank() == true && draft.needsSync -> "已清空，等待从 Kindle 删除批注"
                    draft?.text?.isBlank() == true -> "批注已清空"
                    storedText.isBlank() -> "为这条标注写下你的想法"
                    else -> storedText
                },
                style = MaterialTheme.typography.bodyLarge
            )
            if (draft != null) Text(
                if (draft.needsSync) "本地副本 · 待写入 Kindle" else "本地副本 · 已写入 Kindle",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editorText = storedText; editing = true },
                    enabled = state.noteDraftsAvailable && !state.noteDraftSaving) { Text("编辑批注") }
                Button(onClick = { confirmingSend = true }, enabled =
                    draft?.needsSync == true && !writing && !state.notesSyncing &&
                        !state.noteDraftSaving && state.settings.host.isNotBlank()) {
                    Text(if (state.noteWritingId == note.id) "写入中…" else "发送到 Kindle")
                }
            }
            if (draft != null) TextButton(onClick = { confirmingRestore = true }, enabled = !writing && !state.noteDraftSaving) {
                Text("恢复同步时的 Kindle 批注")
            }
            if (state.noteWriteMessage.isNotBlank()) Text(
                state.noteWriteMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.noteWriteError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!state.noteDraftsAvailable && !state.noteDraftsLoading) {
                TextButton(onClick = onRetryLoad) { Text("重试") }
            }
        }
    }
}
