package com.hongxiang.kpilot.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hongxiang.kpilot.MainActivity
import com.hongxiang.kpilot.R
import com.hongxiang.kpilot.data.AppPreferences
import com.hongxiang.kpilot.i18n.localizeAppText

class ScreenOffRemoteService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val preferences = AppPreferences(this)
        if (!RemoteBackgroundPolicy.shouldRun(
                preferences.remoteControlEnabled,
                preferences.volumeKeysEnabled,
                preferences.keyboardKeysEnabled
            ) || !AccessibilityStatus.isRemoteServiceEnabled(this)
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                localizeAppText("熄屏翻页"),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = localizeAppText("保持音量键和蓝牙键盘翻页在熄屏后可用")
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_remote_notification)
        .setContentTitle(localizeAppText("KPilot 熄屏翻页运行中"))
        .setContentText(localizeAppText("音量键或蓝牙键盘可控制 Kindle 翻页"))
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setSilent(true)
        .build()

    companion object {
        private const val CHANNEL_ID = "screen_off_remote"
        private const val NOTIFICATION_ID = 1001

        fun sync(context: Context) {
            val appContext = context.applicationContext
            val preferences = AppPreferences(appContext)
            val intent = Intent(appContext, ScreenOffRemoteService::class.java)
            if (RemoteBackgroundPolicy.shouldRun(
                    preferences.remoteControlEnabled,
                    preferences.volumeKeysEnabled,
                    preferences.keyboardKeysEnabled
                ) && AccessibilityStatus.isRemoteServiceEnabled(appContext)
            ) {
                runCatching { ContextCompat.startForegroundService(appContext, intent) }
            } else {
                appContext.stopService(intent)
            }
        }
    }
}
