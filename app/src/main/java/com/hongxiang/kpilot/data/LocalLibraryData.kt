package com.hongxiang.kpilot.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalLibraryData(private val context: Context) {
    fun readImport(uri: Uri, maximumBytes: Int = 64 * 1024 * 1024): ByteArray {
        return requireNotNull(context.contentResolver.openInputStream(uri)).use { source ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            while (true) { val count = source.read(buffer); if (count < 0) break; require(output.size().toLong()+count <= maximumBytes) { "文件过大" }; output.write(buffer,0,count) }
            output.toByteArray()
        }
    }
    fun exportBackup(uri: Uri) {
        requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.putNextEntry(ZipEntry("format.txt")); zip.write("KPilot Android library backup v1\n".toByteArray()); zip.closeEntry()
                listOf(context.noBackupFilesDir,context.filesDir).forEachIndexed { index, root ->
                    root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") && !it.relativeTo(root).path.startsWith("ssh/") }.forEach { file ->
                        zip.putNextEntry(ZipEntry("${if(index==0) "library" else "local"}/${file.relativeTo(root).invariantSeparatorsPath}"))
                        file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
            }
        }
    }
    fun clear() {
        context.noBackupFilesDir.listFiles().orEmpty().forEach { check(it.deleteRecursively()) }
        context.filesDir.listFiles().orEmpty().forEach { check(it.deleteRecursively()) }
        context.cacheDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
    }
}
