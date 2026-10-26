package com.hongxiang.kpilot.i18n

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** English is the fallback for every non-Chinese system locale. */
private fun isChinese(language: String): Boolean = language.equals("zh", ignoreCase = true)

private val chineseToEnglish = mapOf(
    "Kindle 已完成写入，但本机保存失败。草稿仍保留，请同步摘抄后重试。" to "The note was written to Kindle, but local saving failed. The draft was kept. Sync clippings and retry.",
    "Kindle 标注对象数量不匹配" to "The Kindle annotation count does not match",
    "Kindle sidecar 没有标注缓存" to "The Kindle sidecar has no annotation cache",
    "Kindle 笔记数量无效" to "Invalid Kindle note count",
    "Kindle 笔记字段不足" to "The Kindle note has missing fields",
    "Kindle 标注缓存为空" to "The Kindle annotation cache is empty",
    "Kindle 标注分组无效" to "Invalid Kindle annotation groups",
    "Kindle 标注树无效" to "Invalid Kindle annotation tree",
    "不支持的 Kindle sidecar 格式" to "Unsupported Kindle sidecar format",
    "不支持的 Kindle sidecar 版本" to "Unsupported Kindle sidecar version",
    "Kindle sidecar 对象数量无效" to "Invalid Kindle sidecar object count",
    "Kindle sidecar 尾部存在未解析数据" to "The Kindle sidecar contains unparsed trailing data",
    "Kindle sidecar 嵌套过深" to "The Kindle sidecar is nested too deeply",
    "Kindle sidecar 布尔值无效" to "Invalid Kindle sidecar boolean",
    "Kindle sidecar 字符串标记无效" to "Invalid Kindle sidecar string marker",
    "非空字符串不能使用空标记" to "A nonempty string cannot use an empty marker",
    "Kindle 标注字段不足" to "The Kindle annotation has missing fields",
    "Kindle sidecar 需要整数" to "The Kindle sidecar requires an integer",
    "Kindle sidecar 需要长整数" to "The Kindle sidecar requires a long integer",
    "Kindle sidecar 需要字符串" to "The Kindle sidecar requires a string",
    "Kindle sidecar 需要对象" to "The Kindle sidecar requires an object",
    "批注写入失败" to "Note writing failed",
    "书库" to "Library",
    "复习" to "Review",
    "笔记复习" to "Review clippings",
    "底部导航栏" to "Bottom navigation",
    "最多显示 5 个入口，摘抄和设置始终保留。关闭的入口仍可在下方打开。" to "Show up to five tabs. Clippings and Settings stay visible. Open hidden features below.",
    "返回书库" to "Back to library",
    "切换为列表" to "Switch to list",
    "切换为网格" to "Switch to grid",
    "刷新书库" to "Refresh library",
    "手机中上一次成功同步的书库仍然保留。" to "The last successful library sync remains on this phone.",
    "搜索书名、作者或文件名" to "Search titles, authors, or filenames",
    "这本书还没有摘抄" to "No clippings for this book",
    "请先从“摘抄”页同步 Kindle 笔记，再回到书库查看。" to "Sync Kindle notes from Clippings first, then return to the library.",
    "尚未读取 Kindle 书库" to "Library has not been synced",
    "Kindle 书库为空" to "The Kindle library is empty",
    "连接 Kindle 后刷新书库，也可以无线传书。" to "Connect to Kindle and refresh, or send books wirelessly.",
    "选择书籍传送" to "Choose books to send",
    "没有找到书籍" to "No books found",
    "试试其他书名、作者或文件名" to "Try another title, author, or filename",
    "已读完" to "Finished",
    "无法创建书库缓存目录" to "Could not create the library cache",
    "读取 Kindle 书库超时" to "Kindle library download timed out",
    "Kindle 书库文件超过允许的大小" to "The Kindle library file exceeds the size limit",
    "书库同步失败，已保留离线书库" to "Library sync failed; the offline library was kept",
    "每天重逢一段好文字" to "Rediscover something worth reading",
    "撤销" to "Undo",
    "收藏" to "Favorite",
    "下一条" to "Next",
    "不再复习" to "Exclude from review",
    "左滑收藏，右滑不再复习，上滑下一条" to "Swipe left to favorite, right to exclude, or up for next",
    "左右滑动收藏或不再复习，点击下一条继续" to "Swipe left to favorite or right to exclude. Tap Next to continue.",
    "左滑收藏，右滑不再复习" to "Swipe left to favorite, right to exclude",
    "日期未知" to "Date unknown",
    "已收藏" to "Favorited",
    "我的批注" to "My note",
    "本组复习完成" to "Batch complete",
    "没有可复习的摘抄" to "No clippings to review",
    "先同步 Kindle 摘抄，再来复习。" to "Sync Kindle clippings first, then start reviewing.",
    "再来一组" to "Next batch",
    "无法载入复习记录，请重试" to "Could not load review history. Try again.",
    "复习记录尚未保存，请重试" to "Review history has not been saved. Try again.",
    "编辑批注" to "Edit note",
    "先保存到本机，再发送到 Kindle。清空后发送可删除 Kindle 批注，划线会保留。" to "Save locally, then send to Kindle. Sending an empty note deletes the Kindle note and keeps the highlight.",
    "保存中…" to "Saving…",
    "保存到本机" to "Save locally",
    "删除 Kindle 批注" to "Delete Kindle note",
    "发送批注到 Kindle" to "Send note to Kindle",
    "请先在 Kindle 退出阅读器并返回主页。写入时会备份原文件，并校验写入结果。" to "Return to the Kindle home screen first. Original files are backed up and the result is verified.",
    "发送到 Kindle" to "Send to Kindle",
    "恢复同步时的 Kindle 批注" to "Restore the last synced Kindle note",
    "将移除本机保存的批注副本，重新显示上次同步的 Kindle 批注。" to "Remove the local copy and show the last synced Kindle note.",
    "恢复" to "Restore",
    "正在读取本地批注…" to "Loading local notes…",
    "已清空，等待从 Kindle 删除批注" to "Cleared locally; waiting to delete the Kindle note",
    "批注已清空" to "Note cleared",
    "为这条标注写下你的想法" to "Write your thoughts on this highlight",
    "本地副本 · 待写入 Kindle" to "Local copy · pending delivery",
    "本地副本 · 已写入 Kindle" to "Local copy · sent to Kindle",
    "写入中…" to "Writing…",
    "批注已保存到本机" to "Note saved locally",
    "无法保存本地批注" to "Could not save the local note",
    "已恢复同步时的 Kindle 批注" to "Restored the last synced Kindle note",
    "无法恢复原批注" to "Could not restore the original note",
    "正在写入 Kindle 批注…" to "Writing the Kindle note…",
    "已删除 Kindle 批注，划线保留" to "Kindle note deleted; the highlight was kept",
    "批注已写入 Kindle，本地副本已保留" to "Note written to Kindle; the local copy was kept",
    "写入失败，本地草稿已保留" to "Write failed; the local draft was kept",
    "无法读取本地批注草稿" to "Could not read local note drafts",
    "本地批注草稿数量无效" to "Invalid local draft count",
    "本地批注草稿过大" to "The local note draft is too large",
    "本地批注草稿重复" to "Duplicate local note draft",
    "本地批注草稿格式无效" to "Invalid local draft format",
    "批注内容过长" to "The note is too long",
    "无法迁移原有摘抄缓存，原文件仍然保留" to "Could not migrate the previous clippings cache; the original file was kept",
    "写回后的摘抄缓存格式无效" to "The updated clippings cache is invalid",
    "无法读取摘抄缓存" to "Could not read the clippings cache",
    "只能给已有的 Kindle 标注新增或编辑批注" to "Only existing Kindle highlights support note editing",
    "这条标注没有可匹配的位置" to "This highlight has no location to match",
    "批注不能包含独立的 Kindle 记录分隔行或空字符" to "Notes cannot contain a Kindle separator line or a null character",
    "批注内容过长（UTF-8 最多 65535 字节）" to "The note exceeds the UTF-8 limit of 65535 bytes",
    "Kindle 的 My Clippings.txt 格式不完整，已停止写入" to "My Clippings.txt is incomplete; writing was stopped",
    "Kindle 的 My Clippings.txt 中已找不到这条标注" to "This highlight no longer exists in Kindle My Clippings.txt",
    "Kindle 中没有找到 AZW3、MOBI 或 KFX 批注文件" to "No AZW3, MOBI, or KFX annotation files were found",
    "Kindle sidecar 无法无损重编码" to "The Kindle sidecar cannot be encoded losslessly",
    "本地笔记校验失败：原标注发生变化" to "Local validation failed: the original highlight changed",
    "本地批注校验失败" to "Local note validation failed",
    "Kindle 批注文件写入校验失败" to "Kindle annotation verification failed",
    "My Clippings.txt 写入校验失败" to "My Clippings.txt verification failed",
    "Kindle 阅读器修改了批注文件，请返回主页后重试" to "The reader changed the annotation file. Return to the Kindle home screen and retry.",
    "Kindle 笔记写入已中断" to "Kindle note writing was interrupted",
    "无法确认 Kindle 阅读器状态，请回到主页后重试" to "Could not confirm reader status. Return to the Kindle home screen and retry.",
    "请退出 Kindle 阅读器，回到主页后再次写入批注" to "Exit the Kindle reader and return home before writing notes",
    "多条 Kindle 标注具有相同位置和时间，无法安全确定写入目标" to "Multiple Kindle highlights have the same location and time; writing was stopped",
    "多个 Kindle 文件具有相同标注时间，无法安全确定写入目标" to "Multiple Kindle files have matching annotation times; writing was stopped",
    "没有找到与这条标注匹配的 Kindle 批注文件" to "No Kindle annotation file matches this highlight",
    "Kindle 笔记数据缺失" to "Kindle note data is missing",
    "标注缺少位置" to "The highlight has no location",
    "更新后的 My Clippings.txt 无法解析" to "The updated My Clippings.txt cannot be parsed",
    "更新后丢失了原标注" to "The original highlight is missing after the update",
    "更新后的 My Clippings.txt 笔记校验失败" to "Note verification failed in the updated My Clippings.txt",
    "Kindle SSH 连接已断开，无法写入或恢复备份" to "The Kindle SSH connection closed; writing or recovery is unavailable",
    "Kindle 笔记操作超时" to "The Kindle note operation timed out",
    "Kindle 文件超过笔记写入大小上限" to "The Kindle file exceeds the note-writing size limit",
    "传书" to "Send",
    "摘抄" to "Clippings",
    "生词" to "Vocabulary",
    "遥控" to "Remote",
    "设置" to "Settings",
    "摘抄详情" to "Clipping details",
    "返回" to "Back",
    "搜索摘抄" to "Search clippings",
    "同步摘抄" to "Sync clippings",
    "导出摘抄" to "Export clippings",
    "搜索生词" to "Search vocabulary",
    "同步生词" to "Sync vocabulary",
    "导出生词" to "Export vocabulary",
    "覆盖" to "Overwrite",
    "跳过" to "Skip",
    "取消" to "Cancel",
    "关闭" to "Close",
    "重试" to "Retry",
    "详情" to "Details",
    "保存" to "Save",
    "测试连接" to "Test connection",
    "选择书籍" to "Choose books",
    "发送队列" to "Send queue",
    "文件" to "Files",
    "待发送" to "Ready",
    "发送中" to "Sending",
    "已发送" to "Sent",
    "失败" to "Failed",
    "已跳过" to "Skipped",
    "等待确认" to "Waiting for confirmation",
    "移除" to "Remove",
    "还没有待发送的书籍" to "No books selected yet",
    "支持 EPUB、PDF、MOBI、AZW3 和 TXT" to "Supports EPUB, PDF, MOBI, AZW3, and TXT",
    "还没有摘抄" to "No clippings yet",
    "点按右上角同步，从 Kindle 读取摘抄" to "Tap Sync at the top right to read clippings from Kindle",
    "搜索摘抄、书名或笔记" to "Search clippings, titles, or notes",
    "全部书籍" to "All books",
    "按位置" to "By location",
    "按时间" to "By date",
    "没有找到内容" to "No results",
    "试试其他关键词或书籍" to "Try another keyword or book",
    "同步失败" to "Sync failed",
    "手机中上一次成功同步的内容仍然保留。" to "The last successful sync remains on this phone.",
    "查看摘抄详情" to "View clipping details",
    "书签" to "Bookmark",
    "标注" to "Highlight",
    "笔记" to "Note",
    "Kindle 笔记" to "Kindle notes",
    "无摘抄文字" to "No clipping text",
    "查看原始信息" to "Show original details",
    "收起原始信息" to "Hide original details",
    "还没有生词" to "No vocabulary yet",
    "点按右上角同步 Kindle 生词本" to "Tap Sync at the top right to read Vocabulary Builder",
    "搜索单词、书名或上下文" to "Search words, titles, or context",
    "按字母" to "Alphabetical",
    "按查询次数" to "By lookup count",
    "没有找到单词" to "No words found",
    "试试其他关键词" to "Try another keyword",
    "上一页" to "Previous page",
    "下一页" to "Next page",
    "翻页遥控" to "Page-turn remote",
    "已开启" to "On",
    "已关闭" to "Off",
    "控制方式" to "Controls",
    "手机音量键" to "Phone volume keys",
    "音量加上一页，音量减下一页" to "Volume Up: previous; Volume Down: next",
    "蓝牙键盘方向键" to "Bluetooth keyboard arrows",
    "↑、← 上一页，↓、→ 下一页" to "↑/← previous; ↓/→ next",
    "小米手环" to "Xiaomi Smart Band",
    "手机已连接" to "Phone connected",
    "等待手环连接" to "Waiting for band",
    "未开启" to "Off",
    "重新连接" to "Reconnect",
    "后台音量键" to "Background volume keys",
    "系统权限已开启" to "System permission enabled",
    "需要辅助功能权限" to "Accessibility permission required",
    "翻页命令" to "Page-turn commands",
    "刷新" to "Refresh",
    "检查" to "Check",
    "检查中" to "Checking",
    "收起" to "Collapse",
    "首次翻页前会自动检查 Kindle 中的 4 个事件文件，也可以现在检查。" to "KPilot checks the four Kindle event files before the first page turn. You can also check now.",
    "收起录制选项" to "Hide recording options",
    "查看录制选项" to "Show recording options",
    "4 个翻页命令都已就绪" to "All four page-turn commands are ready",
    "管理" to "Manage",
    "管理全部命令" to "Manage all commands",
    "只看缺失命令" to "Show missing only",
    "录制" to "Record",
    "重新录制" to "Record again",
    "开始录制" to "Start recording",
    "请现在到 Kindle 上执行一次对应的翻页手势，录制将在 5 秒后自动结束。" to "Perform the corresponding page-turn gesture on Kindle now. Recording ends automatically after 5 seconds.",
    "Kindle 连接" to "Kindle connection",
    "Kindle 中已有同名书籍" to "A book with the same name exists on Kindle",
    "Kindle 摘抄为空" to "Kindle clippings are empty",
    "IP 地址或主机名" to "IP address or hostname",
    "例如 192.168.15.244" to "e.g. 192.168.15.244",
    "密码（可留空）" to "Password (optional)",
    "高级设置" to "Advanced settings",
    "收起高级设置" to "Hide advanced settings",
    "端口" to "Port",
    "用户名" to "Username",
    "触控设备（留空自动检测）" to "Touch device (blank for auto-detect)",
    "例如 event1" to "e.g. event1",
    "自动检测触控设备" to "Auto-detect touch device",
    "连接帮助" to "Connection help",
    "SSH 与翻页功能的准备事项" to "SSH and page-turn setup",
    "连接 Kindle" to "Connect to Kindle",
    "知道了" to "Got it",
    "请确保 Kindle 已开启 SSH（UsbNet 或 UsbNetLite），并与手机处于可互通的网络。翻页还需要 Kindle 中已有 FlipCmd 事件文件。后台或熄屏运行可能受 Android 或厂商电池策略限制；是否在系统设置中允许 KPilot 不受电池优化限制，由你自行决定。" to "Make sure SSH (UsbNet or UsbNetLite) is enabled on Kindle and that the phone can reach it over the network. Page turning also requires FlipCmd event files on Kindle. Android or vendor battery policies may limit background or screen-off operation; you can decide whether to exempt KPilot from battery optimization in system settings.",
    "支持 KPilot" to "Support KPilot",
    "KPilot 免费开放全部功能。你的关注、反馈和赞赏，会帮助项目继续维护。" to "All KPilot features are free. Following, feedback, and optional support help keep the project maintained.",
    "参与交流" to "Join the community",
    "KPilot 用户交流与反馈群" to "KPilot community and feedback group",
    "复制群号" to "Copy group number",
    "已复制" to "Copied",
    "关注作者" to "Follow the author",
    "微信公众号 · Xiangware" to "WeChat Official Account · Xiangware",
    "获取版本更新与使用说明" to "Get updates and usage guides",
    "查看二维码" to "View QR code",
    "赞赏作者" to "Support the author",
    "请作者喝杯咖啡" to "Buy the author a coffee",
    "赞赏不会解锁功能，但可能解锁更新" to "Support won't unlock features, but it might unlock updates",
    "查看赞赏码" to "View support QR code",
    "关注公众号" to "Follow the official account",
    "使用微信扫描二维码关注 Xiangware，获取版本更新与使用说明。" to "Scan with WeChat to follow Xiangware for updates and usage guides.",
    "感谢你的自愿支持，无论是否赞赏都不会影响任何功能。" to "Thank you for your optional support. Every feature remains available either way.",
    "当前安装包中没有找到二维码素材。" to "The QR code asset is missing from this build.",
    "请填写 Kindle 连接信息" to "Enter Kindle connection details",
    "请先在设置中填写 Kindle 地址" to "Enter the Kindle address in Settings first",
    "请选择要发送的书籍" to "Choose books to send",
    "从 Kindle 同步摘抄" to "Sync clippings from Kindle",
    "从 Kindle 同步生词" to "Sync vocabulary from Kindle",
    "小米手环遥控未开启" to "Xiaomi Smart Band remote is off",
    "设置已安全保存" to "Settings saved securely",
    "无法保存设置" to "Could not save settings",
    "正在测试 SSH 连接…" to "Testing the SSH connection…",
    "连接失败" to "Connection failed",
    "正在检测触控设备…" to "Detecting the touch device…",
    "未检测到触控设备" to "No touch device detected",
    "音量键翻页已开启" to "Volume-key page turning enabled",
    "音量键恢复系统音量" to "Volume keys restored to system volume",
    "蓝牙键盘翻页已开启" to "Bluetooth keyboard page turning enabled",
    "蓝牙键盘方向键已恢复" to "Bluetooth keyboard arrows restored",
    "没有新增可发送的文件" to "No new supported files",
    "请先选择文件或重试失败项目" to "Choose files or retry failed items first",
    "Kindle 中已有匹配的书籍" to "A matching book already exists on Kindle",
    "已由用户跳过" to "Skipped by user",
    "发送失败" to "Send failed",
    "正在下载 My Clippings.txt…" to "Downloading My Clippings.txt…",
    "同步完成，My Clippings.txt 中还没有内容" to "Sync complete; My Clippings.txt is empty",
    "缓存中没有摘抄" to "No clippings in the cache",
    "没有可导出的摘抄" to "There are no clippings to export",
    "无法创建导出文件" to "Could not create the export file",
    "正在下载 vocab.db…" to "Downloading vocab.db…",
    "生词同步失败，已保留上一次缓存" to "Vocabulary sync failed; the previous cache was kept",
    "没有可导出的生词" to "There is no vocabulary to export",
    "无法创建生词导出文件" to "Could not create the vocabulary export",
    "没有找到可用的生词" to "No usable vocabulary found",
    "翻页遥控未开启" to "Page-turn remote is off",
    "请先开启翻页遥控" to "Enable the page-turn remote first",
    "准备检查翻页命令" to "Preparing to check page-turn commands",
    "连接设置已更新，首次翻页时会检查命令" to "Connection settings updated; commands will be checked on the first page turn",
    "翻页遥控已开启" to "Page-turn remote enabled",
    "首次翻页前正在检查命令…" to "Checking commands before the first page turn…",
    "翻页命令尚未录制完整" to "Page-turn commands are incomplete",
    "正在检查 4 个翻页命令…" to "Checking four page-turn commands…",
    "请先保存 Kindle 连接设置" to "Save the Kindle connection settings first",
    "翻页操作失败" to "Page turn failed",
    "竖屏" to "Portrait ",
    "横屏" to "Landscape ",
    "翻译" to "Translate",
    "没有识别到 /dev/input/eventX" to "Could not detect /dev/input/eventX",
    "无法在下载目录创建导出文件" to "Could not create the export file in Downloads",
    "无法写入导出文件" to "Could not write the export file",
    "无法创建下载目录" to "Could not create the Downloads directory",
    "熄屏翻页" to "Screen-off page turning",
    "保持音量键和蓝牙键盘翻页在熄屏后可用" to "Keep volume-key and Bluetooth-keyboard page turning available while the screen is off",
    "KPilot 熄屏翻页运行中" to "KPilot screen-off page turning is running",
    "音量键或蓝牙键盘可控制 Kindle 翻页" to "Use volume keys or a Bluetooth keyboard to turn Kindle pages",
    "未知大小" to "Unknown size",
    "需要存储权限才能写入 Download/kpilot_export" to "Storage permission is required to write to Download/kpilot_export",
    "KPilot 摘抄诊断码" to "KPilot clipping diagnostic code",
    "KPilot QQ群" to "KPilot QQ group",
    "KPilot 微信公众号二维码" to "KPilot WeChat official account QR code",
    "KPilot 作者微信赞赏码" to "KPilot author support QR code",
    "上午" to "AM",
    "下午" to "PM",
    "添加于" to "Added on",
    "划线" to "Highlight",
    "高亮" to "Highlight",
    "批注" to "Note"
)

private val englishToChinese = chineseToEnglish.entries.associate { (zh, en) -> en to zh } + mapOf(
    "SSH authentication" to "SSH 身份验证",
    "SSH connection" to "SSH 连接",
    "Remote file" to "远程文件",
    "Remote command" to "远程命令",
    "Transfer" to "传输",
    "Synchronization" to "同步",
    "Notes format" to "摘抄格式",
    "Phone cache directory" to "手机缓存目录",
    "Phone cache preparation" to "手机缓存准备",
    "Phone cache write" to "手机缓存写入",
    "Phone cache storage" to "手机缓存存储",
    "Phone cache validation" to "手机缓存验证",
    "Phone cache commit" to "手机缓存提交",
    "Database validation" to "数据库验证",
    "Remote database" to "远程数据库",
    "SSH authentication failed. Check the Kindle username and password in Settings." to "SSH 身份验证失败，请检查设置中的 Kindle 用户名和密码。",
    "Could not reach Kindle over SSH. Confirm it is online, then run Test connection in Settings." to "无法通过 SSH 连接 Kindle，请确认设备在线并在设置中测试连接。",
    "Could not start the Notes SSH connection. Check Settings and try Test connection." to "无法建立摘抄 SSH 连接，请检查设置并测试连接。",
    "My Clippings.txt was not found on this Kindle. Create a highlight or note on Kindle, then try again." to "Kindle 中没有 My Clippings.txt，请先创建一条标注或笔记后重试。",
    "Kindle denied access to My Clippings.txt. Check UsbNet SSH permissions." to "Kindle 拒绝访问 My Clippings.txt，请检查 UsbNet SSH 权限。",
    "The SSH connection closed before Notes could be downloaded. Run Test connection in Settings." to "摘抄下载完成前 SSH 连接已断开，请在设置中测试连接。",
    "Kindle could not provide My Clippings.txt. Confirm the file exists and SSH can read it." to "Kindle 无法提供 My Clippings.txt，请确认文件存在且 SSH 可以读取。",
    "SSH connected, but the Notes transfer could not start. Try again." to "SSH 已连接，但无法开始传输摘抄，请重试。",
    "Notes download timed out. The previous synced notes were kept." to "摘抄下载超时，已保留上次同步内容。",
    "Notes synchronization failed at an unknown stage. Run Test connection, then retry and share this diagnostic code." to "摘抄同步在未知阶段失败，请测试连接后重试并提供诊断码。",
    "Notes downloaded, but the private cache folder was unavailable. The previous cache was kept." to "摘抄已下载，但手机缓存目录不可用，已保留上次缓存。",
    "Notes downloaded, but the phone could not prepare a private temporary cache. The previous cache was kept." to "摘抄已下载，但无法准备临时缓存，已保留上次缓存。",
    "Notes downloaded, but the private temporary cache could not be written. The previous cache was kept." to "摘抄已下载，但无法写入临时缓存，已保留上次缓存。",
    "Notes downloaded, but Android could not safely finish storing the temporary cache. The previous cache was kept." to "摘抄已下载，但 Android 无法安全完成缓存写入，已保留上次缓存。",
    "The downloaded Notes cache could not be validated. The previous cache was kept." to "下载的摘抄缓存未通过验证，已保留上次缓存。",
    "Notes downloaded, but Android could not replace the private cache. The previous cache was kept." to "摘抄已下载，但 Android 无法替换缓存，已保留上次缓存。",
    "Kindle could not provide its vocabulary database. Confirm Vocabulary Builder has been used, then retry." to "Kindle 无法提供生词数据库，请确认使用过生词本后重试。",
    "Vocabulary download timed out. The previous cache was kept." to "生词下载超时，已保留上次缓存。",
    "Vocabulary synchronization failed. Test the connection, then retry and share this diagnostic code." to "生词同步失败，请测试连接后重试并提供诊断码。",
    "This Kindle vocabulary database format is not supported. The previous cache was kept." to "不支持此 Kindle 生词数据库格式，已保留上次缓存。",
    "The private vocabulary cache folder was unavailable. The previous cache was kept." to "无法使用生词缓存目录，已保留上次缓存。",
    "Android could not prepare the vocabulary cache. The previous cache was kept." to "Android 无法准备生词缓存，已保留上次缓存。",
    "The vocabulary download could not be stored. The previous cache was kept." to "无法保存下载的生词，已保留上次缓存。",
    "Android could not safely finish storing vocabulary. The previous cache was kept." to "Android 无法安全保存生词，已保留上次缓存。",
    "Android could not replace the vocabulary cache. The previous cache was kept." to "Android 无法替换生词缓存，已保留上次缓存。"
)

private fun dynamicEnglish(value: String): String? {
    fun replace(pattern: String, replacement: (MatchResult) -> String): String? =
        Regex(pattern).matchEntire(value)?.let(replacement)
    return replace("^(\\d+) 条$", { "${it.groupValues[1]} items" })
        ?: replace("^(\\d+) 本书$", { "${it.groupValues[1]} books" })
        ?: replace("^(\\d+) 条摘抄( · .+)?$", { "${it.groupValues[1]} clippings${it.groupValues[2]}" })
        ?: replace("^上次同步：(.+)$", { "Last synced: ${it.groupValues[1]}" })
        ?: replace("^已读 (\\d+)%$", { "${it.groupValues[1]}% read" })
        ?: replace("^今天已复习 (\\d+) 条$", { "${it.groupValues[1]} reviewed today" })
        ?: replace("^本组 (\\d+) / (\\d+)$", { "Batch ${it.groupValues[1]} / ${it.groupValues[2]}" })
        ?: replace("^已收藏 (\\d+) 条$", { "${it.groupValues[1]} favorites" })
        ?: replace("^恢复不再复习的摘抄（(\\d+) 条）$", { "Restore excluded clippings (${it.groupValues[1]})" })
        ?: replace("^读取 Kindle 书库失败 \\((.+)\\)$", { "Kindle library download failed (${it.groupValues[1]})" })
        ?: replace("^Kindle 笔记操作失败（(.+)）$", { "Kindle note operation failed (${it.groupValues[1]})" })
        ?: replace("^(.+)；自动恢复备份失败，请保留草稿。Kindle 备份：(.+)$", {
            "${localizeAppText(it.groupValues[1], "en")}; backup recovery failed. Keep the draft. Kindle backups: ${it.groupValues[2]}"
        })
        ?: replace("^(\\d+) 个单词$", { "${it.groupValues[1]} words" })
        ?: replace("^(\\d+) 次$", { "${it.groupValues[1]} lookups" })
        ?: replace("^(\\d+) 条笔记$", { "${it.groupValues[1]} notes" })
        ?: replace("^发送 (\\d+) 本到 Kindle$", { "Send ${it.groupValues[1]} to Kindle" })
        ?: replace("^已跳过 (\\d+) 个不支持的文件$", { "Skipped ${it.groupValues[1]} unsupported files" })
        ?: replace("^(\\d+) 个文件等待发送$", { "${it.groupValues[1]} files ready to send" })
        ?: replace("^正在依次发送 (\\d+) 个文件$", { "Sending ${it.groupValues[1]} files in order" })
        ?: replace("^已同步 (\\d+) 条摘抄$", { "Synced ${it.groupValues[1]} clippings" })
        ?: replace("^已载入 (\\d+) 条摘抄$", { "Loaded ${it.groupValues[1]} clippings" })
        ?: replace("^已同步 (\\d+) 个生词$", { "Synced ${it.groupValues[1]} words" })
        ?: replace("^已载入 (\\d+) 个生词$", { "Loaded ${it.groupValues[1]} words" })
        ?: replace("^缺少 (\\d+) 个翻页命令(?:，请先录制)?$", { "${it.groupValues[1]} page-turn commands are missing" })
        ?: replace("^缺少 (\\d+) 个命令。点按录制后，请在 5 秒内操作 Kindle。$", { "${it.groupValues[1]} commands are missing. Tap Record, then perform the gesture on Kindle within 5 seconds." })
        ?: replace("^已导出至 (.+)$", { "Exported to ${it.groupValues[1]}" })
        ?: replace("^已检测到 (.+)$", { "Detected ${it.groupValues[1]}" })
        ?: replace("^(.+) 已发送$", { "${it.groupValues[1]} sent" })
        ?: replace("^已跳过 (.+)$", { "Skipped ${it.groupValues[1]}" })
        ?: replace("^导出 (.+)$", { "Export ${it.groupValues[1]}" })
        ?: replace("^(竖屏|横屏)(上一页|下一页)$", { "${localizeAppText(it.groupValues[1], "en")}${localizeAppText(it.groupValues[2], "en")}" })
        ?: replace("^录制(.+)$", { "Record ${localizeAppText(it.groupValues[1], "en")}" })
        ?: replace("^正在录制(.+)…?$", { "Recording ${localizeAppText(it.groupValues[1], "en")}…" })
        ?: replace("^准备录制(.+)$", { "Preparing to record ${localizeAppText(it.groupValues[1], "en")}" })
        ?: replace("^(.+)录制完成，4 个命令已就绪$", { "${localizeAppText(it.groupValues[1], "en")} recorded; all four commands are ready" })
        ?: replace("^(.+)录制完成，还缺少 (\\d+) 个命令$", { "${localizeAppText(it.groupValues[1], "en")} recorded; ${it.groupValues[2]} commands are still missing" })
        ?: replace("^(.+)录制完成$", { "${localizeAppText(it.groupValues[1], "en")} recorded" })
        ?: replace("^点按开始后，请在 5 秒内到 Kindle 上执行一次(.+)手势。旧录制会被替换。$", { "After tapping Start, perform the ${localizeAppText(it.groupValues[1], "en")} gesture on Kindle within 5 seconds. The previous recording will be replaced." })
        ?: replace("^正在发送(.+)…$", { "Sending ${localizeAppText(it.groupValues[1], "en")}…" })
        ?: replace("^(.+)已加入队列$", { "${localizeAppText(it.groupValues[1], "en")} queued" })
        ?: replace("^(.+)完成$", { "${localizeAppText(it.groupValues[1], "en")} complete" })
        ?: replace("^诊断码 (.+) · 点按复制$", { "Diagnostic code ${it.groupValues[1]} · tap to copy" })
        ?: replace("^阶段：(.+)$", { "Stage: ${it.groupValues[1]}" })
        ?: replace("^添加时间  (.+)$", { "Added  ${it.groupValues[1]}" })
        ?: replace("^(.+) 与 Kindle 中的文件匹配。是否覆盖？$", { "${it.groupValues[1]} matches a file on Kindle. Overwrite it?" })
        ?: replace("^位置 (.+)$", { "Location ${it.groupValues[1]}" })
        ?: replace("^页 (.+)$", { "Page ${it.groupValues[1]}" })
        ?: replace("^问题反馈、使用交流与版本通知\\nQQ群：(\\d+)$", { "Feedback, community help, and release news\nQQ group: ${it.groupValues[1]}" })
}

private fun dynamicChinese(value: String): String? {
    fun replace(pattern: String, replacement: (MatchResult) -> String): String? =
        Regex(pattern).matchEntire(value)?.let(replacement)
    return replace("^Connected to (.+)$", { "已连接到 ${it.groupValues[1]}" })
        ?: replace("^(\\d+) items$", { "${it.groupValues[1]} 条" })
        ?: replace("^(\\d+) words$", { "${it.groupValues[1]} 个单词" })
        ?: replace("^(\\d+) lookups$", { "${it.groupValues[1]} 次" })
        ?: replace("^Exported to (.+)$", { "已导出至 ${it.groupValues[1]}" })
}

fun localizeAppText(value: String, language: String): String = if (isChinese(language)) {
    englishToChinese[value] ?: dynamicChinese(value) ?: value
} else {
    chineseToEnglish[value] ?: dynamicEnglish(value) ?: value
}

fun Context.localizeAppText(value: String): String =
    localizeAppText(value, resources.configuration.locales[0].language)

@Composable
fun localizedText(value: String): String =
    localizeAppText(value, LocalConfiguration.current.locales[0].language)

/** Drop-in Material Text wrapper that localizes canonical app messages at render time. */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) = MaterialText(
    text = localizedText(text),
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontStyle = fontStyle,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    textAlign = textAlign,
    lineHeight = lineHeight,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    minLines = minLines,
    onTextLayout = onTextLayout,
    style = style
)
