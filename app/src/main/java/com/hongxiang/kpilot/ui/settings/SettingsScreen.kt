package com.hongxiang.kpilot.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hongxiang.kpilot.MainViewModel
import com.hongxiang.kpilot.UiState
import com.hongxiang.kpilot.data.NavigationItem
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.i18n.localizedText
import com.hongxiang.kpilot.support.SupportKPilotEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Page(val title: String) { HOME("设置"), CONNECTION("Kindle 连接"), NAVIGATION("底部导航栏"), LOCAL("本地数据"), ABOUT("关于") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, state: UiState) {
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var profilesExpanded by remember { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }
    var importVocabulary by rememberSaveable { mutableStateOf(false) }
    val importing = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.importLocalFile(it, importVocabulary) } }
    val exporting = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(viewModel::exportLocalBackup) }
    val busy = state.connectionBusy || state.localDataBusy || state.busy || state.notesSyncing || state.vocabularySyncing || state.librarySyncing || state.noteWritingId != null || state.noteDraftSaving
    val context = LocalContext.current
    BackHandler(page != Page.HOME) { page = Page.HOME }
    Scaffold(contentWindowInsets = WindowInsets(0,0,0,0), containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text(page.title) }, navigationIcon = {
            if (page != Page.HOME) IconButton(onClick = { page = Page.HOME }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回")) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when(page) {
                Page.HOME -> {
                    Group {
                        Entry("Kindle 连接", Icons.Filled.Lan, subtitle = if (state.settings.host.isBlank()) "填写 IP 地址和 SSH 密码" else "${state.profiles.firstOrNull { it.id == state.activeProfileId }?.name ?: "Kindle"} · ${if(state.connected) "已连接" else "未连接"}", onClick = { page = Page.CONNECTION })
                        if (state.settings.host.isNotBlank()) { HorizontalDivider(); ConnectRow(viewModel,state,busy) }
                    }
                    Group {
                        Entry("底部导航栏", Icons.Filled.ViewAgenda, onClick = { page = Page.NAVIGATION })
                        HorizontalDivider(); Entry("本地数据", Icons.Filled.Storage, onClick = { page = Page.LOCAL })
                    }
                    Group { Entry("关于",Icons.Filled.Info, onClick = { page = Page.ABOUT }) }
                    Group { SupportKPilotEntry(compact = true) }
                }
                Page.CONNECTION -> {
                    Group("当前 Kindle") {
                        Box {
                            Entry("当前 Kindle", Icons.Filled.Tablet, value = state.profiles.firstOrNull { it.id == state.activeProfileId }?.name ?: "Kindle", onClick = { if(!busy) profilesExpanded = true })
                            DropdownMenu(expanded = profilesExpanded, onDismissRequest = { profilesExpanded = false }) {
                                state.profiles.forEach { profile -> DropdownMenuItem(text={ Text(profile.name) }, onClick={profilesExpanded=false;viewModel.selectProfile(profile.id)}) }
                            }
                        }
                        Field("配置名称",state.profiles.firstOrNull { it.id == state.activeProfileId }?.name.orEmpty(),!busy,viewModel::renameProfile)
                        Field("IP 地址",state.settings.host,!busy,{viewModel.updateSettings(state.settings.copy(host=it.trim()))})
                        Field("SSH 密码",state.settings.password,!busy,{viewModel.updateSettings(state.settings.copy(password=it))},password=true)
                        Entry("高级设置",Icons.Filled.Tune,onClick={advanced=!advanced})
                        if(advanced) {
                            Field("用户名",state.settings.username,!busy,{viewModel.updateSettings(state.settings.copy(username=it.trim()))})
                            Field("端口",state.settings.port.toString(),!busy,{text->text.toIntOrNull()?.let{viewModel.updateSettings(state.settings.copy(port=it))}},number=true)
                            Field("触控设备（自动识别）",state.settings.eventDevice,!busy,{viewModel.updateSettings(state.settings.copy(eventDevice=it.trim()))})
                            TextButton(onClick=viewModel::forgetHostKeys,enabled=!busy,modifier=Modifier.padding(horizontal=8.dp)) { Text("清除已记录的主机密钥",color=MaterialTheme.colorScheme.error) }
                        }
                    }
                    Group { ConnectRow(viewModel,state,busy) }
                    Group("连接状态") { Text(state.connectionMessage,modifier=Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium,color=if(state.connectionError)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                    Group {
                        Action("添加 Kindle",Icons.Filled.Add,!busy,viewModel::addProfile)
                        if(state.profiles.size>1) TextButton(onClick={confirmingRemove=true},enabled=!busy,modifier=Modifier.padding(horizontal=8.dp)) { Text("删除当前配置",color=MaterialTheme.colorScheme.error) }
                    }
                }
                Page.NAVIGATION -> Group {
                    Toggle("显示遥控器",NavigationItem.REMOTE in state.navigationItems){viewModel.setNavigationItem(NavigationItem.REMOTE,it)}
                    HorizontalDivider();Toggle("显示生词本",NavigationItem.VOCABULARY in state.navigationItems){viewModel.setNavigationItem(NavigationItem.VOCABULARY,it)}
                }
                Page.LOCAL -> {
                    Group("导入与备份") {
                        Action("导入摘抄文件",Icons.AutoMirrored.Filled.NoteAdd,!busy){importVocabulary=false;importing.launch(arrayOf("text/*","application/octet-stream"))}
                        HorizontalDivider();Action("导入生词数据库",Icons.Filled.Spellcheck,!busy){importVocabulary=true;importing.launch(arrayOf("*/*"))}
                        HorizontalDivider();Action("导出本机资料库备份",Icons.Filled.IosShare,!busy){exporting.launch("KPilot-library-backup.zip")}
                    }
                    Group("资料库状态") {
                        Value("待保存更改", "${state.noteDrafts.values.count{it.needsSync}} 项")
                        HorizontalDivider();Value("本机资料库",if(state.localDataBusy) "处理中" else "已保存")
                        HorizontalDivider();Value("摘抄","${state.notes.size} 条")
                        HorizontalDivider();Value("生词记录","${state.vocabularyWords.sumOf{it.lookups.size}} 条")
                        HorizontalDivider();Value("Kindle 书籍","${state.library?.books?.size ?: 0} 本")
                    }
                    Group("缓存") { Value("摘抄缓存",date(state.notesLastSync));HorizontalDivider();Value("生词缓存",date(state.vocabularyLastSync)) }
                    Group { TextButton(onClick={confirmingClear=true},enabled=!busy,modifier=Modifier.fillMaxWidth().padding(6.dp)) { Text("清除所有本地数据",color=MaterialTheme.colorScheme.error) } }
                    if(state.localDataBusy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(24.dp))
                    if(state.localDataMessage.isNotBlank()) Text(state.localDataMessage,color=if(state.localDataError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,modifier=Modifier.padding(horizontal=12.dp))
                }
                Page.ABOUT -> Group {
                    Value("KPilot","Android")
                    HorizontalDivider();Value("版本",remember { context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty() })
                    HorizontalDivider();Value("许可证","GPL-3.0")
                }
            }
        }
    }
    if(confirmingClear) AlertDialog(onDismissRequest={confirmingClear=false},title={Text("清除所有本地数据？")},text={Text("仅清除本机资料，Kindle 上的数据不变。")},confirmButton={TextButton(onClick={confirmingClear=false;viewModel.clearLocalData()}){Text("清除",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={confirmingClear=false}){Text("取消")}})
    if(confirmingRemove) AlertDialog(onDismissRequest={confirmingRemove=false},title={Text("删除当前配置？")},confirmButton={TextButton(onClick={confirmingRemove=false;viewModel.removeProfile()}){Text("删除")}},dismissButton={TextButton(onClick={confirmingRemove=false}){Text("取消")}})
}

@Composable private fun Group(title:String?=null,content:@Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        title?.let{Text(it,modifier=Modifier.padding(start=16.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){Column(content=content)}
    }
}
@Composable private fun Entry(title:String,icon:ImageVector,subtitle:String?=null,value:String?=null,onClick:()->Unit) {
    ListItem(headlineContent={Text(title)},supportingContent=subtitle?.let{{Text(it)}},leadingContent={Icon(icon,null,tint=MaterialTheme.colorScheme.primary)},trailingContent={Row(verticalAlignment=Alignment.CenterVertically){value?.let{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,null)}},colors=ListItemDefaults.colors(containerColor=androidx.compose.ui.graphics.Color.Transparent),modifier=Modifier.clickable(onClick=onClick))
}
@Composable private fun ConnectRow(viewModel:MainViewModel,state:UiState,busy:Boolean) { Action("连接 Kindle",Icons.Filled.Lan,!busy&&state.settings.host.isNotBlank(),viewModel::connectKindle) }
@Composable private fun Action(title:String,icon:ImageVector,enabled:Boolean=true,onClick:()->Unit) { TextButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)){Icon(icon,null);Spacer(Modifier.width(12.dp));Text(title,modifier=Modifier.weight(1f));} }
@Composable private fun Value(label:String,value:String) { Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(label,modifier=Modifier.weight(1f));Text(value,color=MaterialTheme.colorScheme.onSurfaceVariant)} }
@Composable private fun Toggle(title:String,checked:Boolean,onChange:(Boolean)->Unit) { Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text(title,modifier=Modifier.weight(1f));Switch(checked,onChange)} }
@Composable private fun Field(label:String,value:String,enabled:Boolean,onChange:(String)->Unit,password:Boolean=false,number:Boolean=false) { OutlinedTextField(value,onChange,enabled=enabled,label={Text(label)},singleLine=true,visualTransformation=if(password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,keyboardOptions=KeyboardOptions(keyboardType=if(number) KeyboardType.Number else if(password) KeyboardType.Password else KeyboardType.Text),modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp)) }
private fun date(value:Instant?):String = value?.let{DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(it)} ?: "暂无缓存"
