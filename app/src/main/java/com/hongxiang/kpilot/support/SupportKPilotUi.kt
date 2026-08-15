package com.hongxiang.kpilot.support

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.i18n.localizedText

private const val QQ_GROUP_NUMBER = "945331815"
private const val OFFICIAL_ACCOUNT_QR_ASSET = "mmexport1786535949312.jpeg"
private const val REWARD_QR_ASSET = "mm_reward_qrcode_1786536180927.png"

private enum class QrCodeDialog(
    val title: String,
    val description: String,
    val assetName: String,
    val contentDescription: String
) {
    OFFICIAL_ACCOUNT(
        title = "关注公众号",
        description = "使用微信扫描二维码关注 Xiangware，获取版本更新与使用说明。",
        assetName = OFFICIAL_ACCOUNT_QR_ASSET,
        contentDescription = "KPilot 微信公众号二维码"
    ),
    REWARD(
        title = "赞赏作者",
        description = "感谢你的自愿支持，无论是否赞赏都不会影响任何功能。",
        assetName = REWARD_QR_ASSET,
        contentDescription = "KPilot 作者微信赞赏码"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportKPilotEntry() {
    var showSupport by rememberSaveable { mutableStateOf(false) }
    var qrDialog by remember { mutableStateOf<QrCodeDialog?>(null) }

    ListItem(
        headlineContent = {
            Text("支持 KPilot", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text("KPilot 免费开放全部功能。你的关注、反馈和赞赏，会帮助项目继续维护。")
        },
        leadingContent = {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { showSupport = true }
    )

    if (showSupport) {
        ModalBottomSheet(onDismissRequest = { showSupport = false }) {
            SupportSheetContent(onShowQrCode = { qrDialog = it })
        }
    }

    qrDialog?.let { dialog ->
        SupportQrCodeDialog(dialog = dialog, onDismiss = { qrDialog = null })
    }
}

@Composable
private fun SupportSheetContent(onShowQrCode: (QrCodeDialog) -> Unit) {
    val context = LocalContext.current
    var copied by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Text("支持 KPilot", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "KPilot 免费开放全部功能。你的关注、反馈和赞赏，会帮助项目继续维护。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(Modifier.padding(vertical = 3.dp))
        Text("参与交流", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SupportGroup {
            ListItem(
                headlineContent = { Text("KPilot 用户交流与反馈群") },
                supportingContent = { Text("问题反馈、使用交流与版本通知\nQQ群：$QQ_GROUP_NUMBER") },
                leadingContent = {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    TextButton(
                        onClick = {
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                ClipData.newPlainText("KPilot QQ群", QQ_GROUP_NUMBER)
                            )
                            copied = true
                        }
                    ) {
                        Icon(
                            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (copied) "已复制" else "复制群号")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }

        Text("关注作者", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SupportGroup {
            SupportActionRow(
                icon = Icons.Filled.QrCode2,
                title = "微信公众号 · Xiangware",
                subtitle = "获取版本更新与使用说明",
                action = "查看二维码",
                onClick = { onShowQrCode(QrCodeDialog.OFFICIAL_ACCOUNT) }
            )
        }

        Text("赞赏作者", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SupportGroup {
            SupportActionRow(
                icon = Icons.Filled.FavoriteBorder,
                title = "请作者喝杯咖啡",
                subtitle = "赞赏不会解锁功能，但可能解锁更新",
                action = "查看赞赏码",
                onClick = { onShowQrCode(QrCodeDialog.REWARD) }
            )
        }
    }
}

@Composable
private fun SupportGroup(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        content()
    }
}

@Composable
private fun SupportActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Text(action, color = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SupportQrCodeDialog(dialog: QrCodeDialog, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val qrCode = remember(dialog.assetName) {
        runCatching {
            context.assets.open(dialog.assetName).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
        title = { Text(dialog.title) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(dialog.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (qrCode != null) {
                    Image(
                        bitmap = qrCode,
                        contentDescription = localizedText(dialog.contentDescription),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        "当前安装包中没有找到二维码素材。",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
