package com.hongxiang.kpilot

import android.app.Application
import com.hongxiang.kpilot.band.XiaomiBandController
import com.hongxiang.kpilot.notes.AtomicCache

class KPilotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // A process killed during SSH download cannot execute AtomicCache's finally block.
        // Clean those partial files once, before any new synchronization can start.
        AtomicCache.deleteAbandonedDownloads(noBackupFilesDir.resolve("notes/My Clippings.txt"))
        AtomicCache.deleteAbandonedDownloads(noBackupFilesDir.resolve("vocabulary/vocab.db"))
        XiaomiBandController.get(this).start(requestPermission = false)
    }
}
