package com.hongxiang.kpilot.data

import android.content.Context
import com.hongxiang.kpilot.notes.AtomicCache
import java.io.File

/**
 * Moves caches from the old host/port/username namespace to the stable Kindle
 * identity namespace. Existing target data always wins; nothing is overwritten.
 */
object DataScopeMigration {
    private val scopePattern = Regex("[a-f0-9]{64}")

    fun migrate(context: Context, from: String, to: String) =
        migrate(context.noBackupFilesDir, from, to)

    internal fun migrate(root: File, from: String, to: String) {
        if (from == to) return
        require(scopePattern.matches(from) && scopePattern.matches(to))
        listOf("library", "notes", "vocabulary").forEach { kind ->
            moveDirectory(root.resolve(kind).resolve(from), root.resolve(kind).resolve(to))
        }
        moveFile(root.resolve("note-drafts/$from.dat"), root.resolve("note-drafts/$to.dat"))
        moveFile(root.resolve("learning/$from.json"), root.resolve("learning/$to.json"))
        rewriteSnapshotSourceKey(root.resolve("library/$to/snapshot.json"), from, to)
    }

    private fun moveDirectory(source: File, target: File) {
        if (!source.isDirectory || target.exists()) return
        val parent = target.parentFile ?: return
        if (!parent.isDirectory && !parent.mkdirs()) return
        if (source.renameTo(target)) return
        runCatching {
            source.copyRecursively(target, overwrite = false)
            source.deleteRecursively()
        }
    }

    private fun moveFile(source: File, target: File) {
        if (!source.isFile || target.exists()) return
        val parent = target.parentFile ?: return
        if (!parent.isDirectory && !parent.mkdirs()) return
        if (source.renameTo(target)) return
        runCatching {
            AtomicCache.write(target) { output -> source.inputStream().use { it.copyTo(output) } }
            source.delete()
        }
    }

    private fun rewriteSnapshotSourceKey(snapshot: File, from: String, to: String) {
        if (!snapshot.isFile) return
        runCatching {
            val original = snapshot.readText()
            val updated = original.replace(
                Regex("(\"sourceKey\"\\s*:\\s*)\"$from\""),
                "$1\"$to\""
            )
            if (updated == original) return
            AtomicCache.write(snapshot) { it.write(updated.toByteArray(Charsets.UTF_8)) }
        }
    }
}
