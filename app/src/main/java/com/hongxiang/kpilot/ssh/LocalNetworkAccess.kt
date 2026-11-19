package com.hongxiang.kpilot.ssh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) gates every socket to a local-network address behind the
 * ACCESS_LOCAL_NETWORK runtime permission for apps targeting API 37 or higher.
 *
 * Without the grant the kernel silently drops outgoing packets, so JSch only sees an
 * unanswered TCP handshake and finally reports "timeout: socket is not established".
 */
object LocalNetworkAccess {
    private val required: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

    fun granted(context: Context): Boolean =
        !required ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED

    const val DENIED_MESSAGE =
        "缺少本地网络权限：Android 17 起连接同一 Wi-Fi 下的 Kindle 需要允许 KPilot 访问本地网络，" +
            "请在系统弹窗中授权（或到系统设置 → 应用 → KPilot → 权限中开启）"
}
