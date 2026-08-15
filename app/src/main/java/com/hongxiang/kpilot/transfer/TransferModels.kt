package com.hongxiang.kpilot.transfer

import android.net.Uri
import java.util.Locale
import java.util.UUID

enum class TransferStatus {
    READY, CHECKING, WAITING_FOR_COLLISION, UPLOADING, COMPLETED, SKIPPED, FAILED
}

data class TransferItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val status: TransferStatus = TransferStatus.READY,
    val bytesSent: Long = 0,
    val error: String? = null,
    val remotePath: String? = null
) {
    val progress: Float
        get() = when {
            status == TransferStatus.COMPLETED -> 1f
            sizeBytes == null || sizeBytes <= 0 -> 0f
            else -> (bytesSent.toFloat() / sizeBytes.toFloat()).coerceIn(0f, 1f)
        }
}

data class TransferCollision(
    val itemId: String,
    val displayName: String,
    val existingPaths: List<String>
)

enum class CollisionDecision { SKIP, OVERWRITE }

object SupportedTransferTypes {
    val extensions = setOf("epub", "pdf", "mobi", "azw3", "txt")
    val pickerMimeTypes = arrayOf(
        "application/epub+zip",
        "application/pdf",
        "application/x-mobipocket-ebook",
        "application/vnd.amazon.ebook",
        "text/plain"
    )

    fun extensionOf(name: String): String? = name.substringAfterLast('.', "")
        .lowercase(Locale.ROOT)
        .takeIf(String::isNotEmpty)

    fun isSupported(name: String): Boolean = extensionOf(name) in extensions
}

object RemoteDocumentNaming {
    const val DIRECTORY = "/mnt/us/documents"

    fun safeFileName(name: String): String {
        val leaf = name.substringAfterLast('/').substringAfterLast('\\').trim()
        require(leaf.isNotEmpty() && leaf != "." && leaf != "..") { "File name is invalid" }
        require(!leaf.any { it == '\u0000' || it == '\n' || it == '\r' }) { "File name contains unsupported characters" }
        require(SupportedTransferTypes.isSupported(leaf)) { "Unsupported file type" }
        return leaf
    }

    fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    fun normalizedTitle(fileName: String): String = fileName
        .substringAfterLast('/')
        .substringBeforeLast('.', fileName.substringAfterLast('/'))
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()

    fun parseRemotePaths(output: String): List<String> = output.lineSequence()
        .map(String::trim)
        .filter { it.startsWith("$DIRECTORY/") && SupportedTransferTypes.isSupported(it.substringAfterLast('/')) }
        .distinct()
        .toList()

    fun collisions(localName: String, remotePaths: List<String>): List<String> {
        val safeName = safeFileName(localName)
        val title = normalizedTitle(safeName)
        return remotePaths.filter { path ->
            val remoteName = path.substringAfterLast('/')
            remoteName.equals(safeName, ignoreCase = true) || normalizedTitle(remoteName) == title
        }
    }
}

object TransferQueueRules {
    fun canRemove(status: TransferStatus): Boolean = status !in setOf(
        TransferStatus.CHECKING,
        TransferStatus.WAITING_FOR_COLLISION,
        TransferStatus.UPLOADING
    )
    fun canRetry(status: TransferStatus): Boolean = status in setOf(TransferStatus.FAILED, TransferStatus.SKIPPED)
    fun retry(item: TransferItem): TransferItem {
        require(canRetry(item.status))
        return item.copy(status = TransferStatus.READY, bytesSent = 0, error = null, remotePath = null)
    }
}
