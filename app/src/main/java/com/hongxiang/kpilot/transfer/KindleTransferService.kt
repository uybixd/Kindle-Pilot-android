package com.hongxiang.kpilot.transfer

import android.content.ContentResolver
import android.net.Uri
import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.ssh.KindleSshSessionFactory
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import java.io.FilterInputStream
import java.io.InputStream
import java.util.UUID

class KindleTransferService(private val contentResolver: ContentResolver) {
    fun listRemoteDocuments(settings: ConnectionSettings): List<String> = withSession(settings) { session ->
        val predicates = SupportedTransferTypes.extensions.sorted()
            .joinToString(" -o ") { "-iname ${RemoteDocumentNaming.shellQuote("*.$it")}" }
        val directory = RemoteDocumentNaming.shellQuote(RemoteDocumentNaming.DIRECTORY)
        val command = "if [ -d $directory ]; then find $directory -type f \\( $predicates \\) -print; fi"
        RemoteDocumentNaming.parseRemotePaths(execute(session, command))
    }

    fun upload(
        settings: ConnectionSettings,
        uri: Uri,
        displayName: String,
        allowOverwrite: Boolean,
        onProgress: (Long) -> Unit
    ): String = withSession(settings) { session ->
        val safeName = RemoteDocumentNaming.safeFileName(displayName)
        val remotePath = "${RemoteDocumentNaming.DIRECTORY}/$safeName"
        val extension = SupportedTransferTypes.extensionOf(safeName) ?: "upload"
        val tempPath = "${RemoteDocumentNaming.DIRECTORY}/.kpilot-upload-${UUID.randomUUID()}.$extension"
        val quotedRemote = RemoteDocumentNaming.shellQuote(remotePath)
        val quotedTemp = RemoteDocumentNaming.shellQuote(tempPath)
        val commit = if (allowOverwrite) {
            "mv -f $quotedTemp $quotedRemote"
        } else {
            "if [ -e $quotedRemote ]; then rm -f $quotedTemp; echo 'Destination appeared after collision check' >&2; exit 73; fi; " +
                "mv $quotedTemp $quotedRemote"
        }
        val command = "set -e; mkdir -p ${RemoteDocumentNaming.shellQuote(RemoteDocumentNaming.DIRECTORY)}; " +
            "cat > $quotedTemp; $commit"
        val input = contentResolver.openInputStream(uri) ?: error("Could not open selected file")
        input.use { source -> executeUpload(session, command, ProgressInputStream(source, onProgress)) }
        remotePath
    }

    private fun <T> withSession(settings: ConnectionSettings, block: (Session) -> T): T {
        settings.validate()
        val session = KindleSshSessionFactory.create(settings)
        return try {
            session.connect(TIMEOUT_MS)
            block(session)
        } finally {
            session.disconnect()
        }
    }

    private fun execute(session: Session, command: String): String {
        val channel = session.openChannel("exec") as ChannelExec
        val stdout = java.io.ByteArrayOutputStream()
        val stderr = java.io.ByteArrayOutputStream()
        channel.setCommand(command)
        channel.setOutputStream(stdout)
        channel.setErrStream(stderr)
        try {
            channel.connect(TIMEOUT_MS)
            await(channel, UPLOAD_TIMEOUT_MS)
            check(channel.exitStatus == 0) {
                stderr.toString("UTF-8").trim().ifBlank { "Kindle command failed (${channel.exitStatus})" }
            }
            return stdout.toString("UTF-8").trim()
        } finally {
            channel.disconnect()
        }
    }

    private fun executeUpload(session: Session, command: String, input: InputStream) {
        val channel = session.openChannel("exec") as ChannelExec
        val stderr = java.io.ByteArrayOutputStream()
        channel.setCommand(command)
        channel.setInputStream(input)
        channel.setErrStream(stderr)
        try {
            channel.connect(TIMEOUT_MS)
            await(channel, UPLOAD_TIMEOUT_MS)
            check(channel.exitStatus == 0) {
                stderr.toString("UTF-8").trim().ifBlank { "Upload failed (${channel.exitStatus})" }
            }
        } finally {
            channel.disconnect()
        }
    }

    private fun await(channel: ChannelExec, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!channel.isClosed && System.currentTimeMillis() < deadline) Thread.sleep(25)
        check(channel.isClosed) { "Kindle transfer timed out" }
    }

    private class ProgressInputStream(
        source: InputStream,
        private val progress: (Long) -> Unit
    ) : FilterInputStream(source) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) report(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) report(it.toLong()) }
        private fun report(delta: Long) {
            count += delta
            progress(count)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val UPLOAD_TIMEOUT_MS = 30 * 60 * 1000L
    }
}
