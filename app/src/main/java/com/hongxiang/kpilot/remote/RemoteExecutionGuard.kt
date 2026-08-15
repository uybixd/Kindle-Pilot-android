package com.hongxiang.kpilot.remote

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import java.io.Closeable

/** Keeps the CPU and Wi-Fi radio available from the hardware-key callback through SSH completion. */
class RemoteExecutionGuard(context: Context) {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    fun acquire(): Closeable {
        val wakeLock = runCatching {
            appContext.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${appContext.packageName}:page-turn")
                .apply {
                    setReferenceCounted(false)
                    acquire(LOCK_TIMEOUT_MS)
                }
        }.getOrNull()
        val wifiLock = runCatching {
            appContext.getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "${appContext.packageName}:page-turn-wifi")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }.getOrNull()
        return Closeable {
            runCatching { if (wifiLock?.isHeld == true) wifiLock.release() }
            runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
        }
    }

    private companion object {
        const val LOCK_TIMEOUT_MS = 20_000L
    }
}
