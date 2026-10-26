package com.hongxiang.kpilot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hongxiang.kpilot.UiState
import com.hongxiang.kpilot.data.cacheScope
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.notes.KindleClipping
import kotlinx.coroutines.delay

@Composable
fun NoteWritebackSection(note: KindleClipping, state: UiState,
    onSave: (KindleClipping,String)->Unit, onSend:(KindleClipping)->Unit,
    onRestore:(KindleClipping)->Unit, onRetryLoad:()->Unit) {
    val scope = state.settings.cacheScope()
    val draft = state.noteDrafts[note.id]
    val source = draft?.text ?: note.kindleNotes.lastOrNull()?.text.orEmpty()
    var text by rememberSaveable(scope,note.id) { mutableStateOf(source) }
    var lastSource by remember(scope,note.id) { mutableStateOf(source) }
    var confirmingRestore by remember { mutableStateOf(false) }
    val currentText by rememberUpdatedState(text)
    val currentSource by rememberUpdatedState(source)
    val currentSave by rememberUpdatedState(onSave)
    LaunchedEffect(source,state.noteDraftsAvailable) {
        if(text == lastSource) text = source
        lastSource = source
    }
    LaunchedEffect(text,source,state.noteDraftsAvailable) {
        if(state.noteDraftsAvailable && text.trim()!=source) { delay(500);onSave(note,text) }
    }
    DisposableEffect(scope,note.id) { onDispose { if(currentText.trim()!=currentSource) currentSave(note,currentText) } }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("批注",style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(text,{text=it},modifier=Modifier.fillMaxWidth(),minLines=5,
            shape=RoundedCornerShape(14.dp),enabled=state.noteDraftsAvailable)
        if(draft != null) Row(Modifier.fillMaxWidth()) {
            Text(if(draft.text.isBlank()) "已清空" else "已保存",style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(draft.needsSync) TextButton(onClick={confirmingRestore=true},enabled=state.noteWritingId==null&&!state.noteDraftSaving) { Text("恢复 Kindle 原批注") }
        }
        if(draft != null && draft.needsSync) TextButton(onClick={onSend(note)},enabled=
            state.noteWritingId==null&&!state.noteDraftSaving&&!state.notesSyncing&&state.settings.host.isNotBlank()&&text.trim()==draft.text) {
            Text(if(state.noteWritingId==note.id) "写入中…" else if(draft.text.isBlank()) "从当前 Kindle 删除这条批注" else "发送到当前 Kindle")
        }
        if(state.noteWriteMessage.isNotBlank()) Text(state.noteWriteMessage,style=MaterialTheme.typography.bodySmall,
            color=if(state.noteWriteError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if(!state.noteDraftsAvailable&&!state.noteDraftsLoading) TextButton(onClick=onRetryLoad){Text("重试")}
    }
    if(confirmingRestore) AlertDialog(onDismissRequest={confirmingRestore=false},title={Text("恢复 Kindle 原批注？")},
        confirmButton={TextButton(onClick={confirmingRestore=false;text=note.kindleNotes.lastOrNull()?.text.orEmpty();onRestore(note)}){Text("恢复原批注")}},
        dismissButton={TextButton(onClick={confirmingRestore=false}){Text("继续编辑")}})
}
