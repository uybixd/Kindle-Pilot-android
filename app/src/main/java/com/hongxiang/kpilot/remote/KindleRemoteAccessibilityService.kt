package com.hongxiang.kpilot.remote

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.hongxiang.kpilot.data.AppPreferences

class KindleRemoteAccessibilityService : AccessibilityService() {
    private lateinit var preferences: AppPreferences
    private lateinit var dispatcher: RemoteCommandDispatcher

    override fun onServiceConnected() {
        preferences = AppPreferences(this)
        dispatcher = RemoteCommandDispatcher.get(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val result = RemoteKeyPolicy.evaluate(
            event.keyCode,
            event.action,
            event.repeatCount,
            preferences.remoteControlEnabled,
            preferences.volumeKeysEnabled,
            preferences.keyboardKeysEnabled
        )
        result.command?.let(dispatcher::submit)
        return result.consume
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
