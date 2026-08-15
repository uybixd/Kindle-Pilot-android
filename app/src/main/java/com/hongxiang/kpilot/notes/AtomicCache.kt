package com.hongxiang.kpilot.notes

import android.system.ErrnoException
import android.system.Os
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

enum class AtomicCacheStage { DIRECTORY, TEMP_CREATE, WRITE, FSYNC, VALIDATE, COMMIT }

class AtomicCacheException(val stage: AtomicCacheStage, cause: Throwable) :
    IOException("Private cache update failed at ${stage.name.lowercase()}", cause)

object AtomicCache {
    /** Removes downloads left behind when Android killed the process during a sync. */
    fun deleteAbandonedDownloads(destination: File): Int {
        val parent = destination.parentFile ?: return 0
        val prefix = ".${destination.name}.download-"
        return parent.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".tmp") }
            .count { it.delete() }
    }

    fun write(destination: File, writer: (OutputStream) -> Unit) =
        writeValidated(destination, writer, { })

    fun writeValidated(
        destination: File,
        writer: (OutputStream) -> Unit,
        validate: (File) -> Unit,
        syncFile: (FileDescriptor) -> Unit = { it.sync() },
        commit: (File, File) -> Unit = ::platformCommit
    ) {
        val parent = destination.parentFile
            ?: throw AtomicCacheException(AtomicCacheStage.DIRECTORY, IOException("Cache has no parent"))
        try {
            if ((!parent.isDirectory && !parent.mkdirs()) || !parent.isDirectory) {
                throw IOException("Cache directory unavailable")
            }
        } catch (error: Exception) {
            if (error is AtomicCacheException) throw error
            throw AtomicCacheException(AtomicCacheStage.DIRECTORY, error)
        }

        val temporary = try {
            File.createTempFile(".${destination.name}.download-", ".tmp", parent)
        } catch (error: Exception) {
            throw AtomicCacheException(AtomicCacheStage.TEMP_CREATE, error)
        }

        try {
            val output = try {
                FileOutputStream(temporary)
            } catch (error: Exception) {
                throw AtomicCacheException(AtomicCacheStage.WRITE, error)
            }
            try {
                output.use { stream ->
                    try {
                        writer(stream)
                        stream.flush()
                    } catch (error: IOException) {
                        throw AtomicCacheException(AtomicCacheStage.WRITE, error)
                    }
                    try {
                        syncFile(stream.fd)
                    } catch (error: Exception) {
                        throw AtomicCacheException(AtomicCacheStage.FSYNC, error)
                    }
                }
            } catch (error: AtomicCacheException) {
                throw error
            } catch (error: IOException) {
                throw AtomicCacheException(AtomicCacheStage.WRITE, error)
            }

            try {
                validate(temporary)
            } catch (error: Exception) {
                throw AtomicCacheException(AtomicCacheStage.VALIDATE, error)
            }
            try {
                commit(temporary, destination)
            } catch (error: Exception) {
                throw AtomicCacheException(AtomicCacheStage.COMMIT, error)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun platformCommit(temporary: File, destination: File) {
        if (isAndroidRuntime()) {
            try {
                Os.rename(temporary.absolutePath, destination.absolutePath)
            } catch (error: ErrnoException) {
                throw IOException("Atomic cache rename failed", error)
            }
        } else if (!temporary.renameTo(destination)) {
            throw IOException("Atomic cache rename failed")
        }
    }

    private fun isAndroidRuntime(): Boolean {
        val vm = System.getProperty("java.vm.name").orEmpty()
        return vm.contains("Dalvik", ignoreCase = true) || vm.contains("ART", ignoreCase = true)
    }
}
