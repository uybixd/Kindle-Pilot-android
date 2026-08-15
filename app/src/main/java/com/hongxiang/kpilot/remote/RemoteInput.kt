package com.hongxiang.kpilot.remote

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import com.hongxiang.kpilot.model.KindleCommand

data class RemoteKeyResult(val command: KindleCommand?, val consume: Boolean)

object RemoteCommandGate {
    fun allows(remoteEnabled: Boolean): Boolean = remoteEnabled
}

object RemoteKeyPolicy {
    fun evaluate(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        remoteEnabled: Boolean,
        volumeMappingEnabled: Boolean,
        keyboardMappingEnabled: Boolean = false
    ): RemoteKeyResult {
        if (!RemoteCommandGate.allows(remoteEnabled)) return RemoteKeyResult(null, false)
        val command = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> KindleCommand.PREVIOUS.takeIf { volumeMappingEnabled }
            KeyEvent.KEYCODE_VOLUME_DOWN -> KindleCommand.NEXT.takeIf { volumeMappingEnabled }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_LEFT -> KindleCommand.PREVIOUS.takeIf { keyboardMappingEnabled }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_RIGHT -> KindleCommand.NEXT.takeIf { keyboardMappingEnabled }
            else -> return RemoteKeyResult(null, false)
        } ?: return RemoteKeyResult(null, false)
        return RemoteKeyResult(
            command = command.takeIf { action == KeyEvent.ACTION_DOWN && repeatCount == 0 },
            consume = true
        )
    }
}

object RemoteBackgroundPolicy {
    fun shouldRun(remoteEnabled: Boolean, volumeKeysEnabled: Boolean, keyboardKeysEnabled: Boolean): Boolean =
        remoteEnabled && (volumeKeysEnabled || keyboardKeysEnabled)
}

object AccessibilityStatus {
    fun isRemoteServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val component = ComponentName(context, KindleRemoteAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { ComponentName.unflattenFromString(it.id) == component }
    }
}
