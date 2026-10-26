package com.hongxiang.kpilot.data

import android.content.Context
import androidx.core.content.edit

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    var navigationItems: Set<NavigationItem>
        get() = NavigationOptions.decode(preferences.getStringSet("navigation_items", null))
        set(value) { preferences.edit { putStringSet("navigation_items", value.map { it.name }.toSet()) } }
    var remoteControlEnabled: Boolean
        get() = preferences.getBoolean("remote_control_enabled", false)
        set(value) { preferences.edit { putBoolean("remote_control_enabled", value) } }
    var volumeKeysEnabled: Boolean
        get() = preferences.getBoolean("volume_keys_enabled", true)
        set(value) { preferences.edit { putBoolean("volume_keys_enabled", value) } }
    var keyboardKeysEnabled: Boolean
        get() = preferences.getBoolean("keyboard_keys_enabled", false)
        set(value) { preferences.edit { putBoolean("keyboard_keys_enabled", value) } }
    var xiaomiBandEnabled: Boolean
        get() = preferences.getBoolean("xiaomi_band_enabled", false)
        set(value) { preferences.edit { putBoolean("xiaomi_band_enabled", value) } }
}
