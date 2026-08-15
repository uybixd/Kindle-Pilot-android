package com.hongxiang.kpilot.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/** Stores exports in the user-visible Download/kpilot_export directory. */
class DownloadExportStore(private val context: Context) {
    fun clearLegacyPrivateExports() {
        context.cacheDir.resolve("shared-notes").deleteRecursively()
        context.cacheDir.resolve("shared-vocabulary").deleteRecursively()
    }

    fun write(displayName: String, mimeType: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(displayName, mimeType, content)
        } else {
            writeLegacy(displayName, mimeType, content)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeWithMediaStore(displayName: String, mimeType: String, content: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_DIRECTORY/"
        var existingUri: Uri? = null

        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(displayName, relativePath),
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                val uri = Uri.withAppendedPath(collection, cursor.getLong(idColumn).toString())
                if (existingUri == null) existingUri = uri else resolver.delete(uri, null, null)
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val created = existingUri == null
        val uri = existingUri?.also { resolver.update(it, values, null, null) }
            ?: checkNotNull(resolver.insert(collection, values)) { "无法在下载目录创建导出文件" }

        try {
            checkNotNull(resolver.openOutputStream(uri, "wt")) { "无法写入导出文件" }.use { output ->
                output.writer(Charsets.UTF_8).use { it.write(content) }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (error: Throwable) {
            if (created) resolver.delete(uri, null, null)
            else resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun writeLegacy(displayName: String, mimeType: String, content: String) {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            EXPORT_DIRECTORY
        ).apply { check(isDirectory || mkdirs()) { "无法创建下载目录" } }
        val file = File(directory, displayName)
        file.writeText(content, Charsets.UTF_8)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
    }

    companion object {
        const val EXPORT_DIRECTORY = "kpilot_export"
        const val DISPLAY_PATH = "Download/$EXPORT_DIRECTORY"
    }
}
