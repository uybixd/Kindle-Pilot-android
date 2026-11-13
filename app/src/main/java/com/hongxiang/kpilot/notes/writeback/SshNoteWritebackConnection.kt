package com.hongxiang.kpilot.notes.writeback

import android.util.Log
import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.ssh.KindleSshSessionFactory
import com.hongxiang.kpilot.support.SyncTrace
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.UUID

/** Uses the same quoted exec streams as the existing sync/upload services; SFTP is not required. */
internal class SshNoteWritebackConnection(private val settings: ConnectionSettings) : NoteWritebackConnection {
    private var session: Session = KindleSshSessionFactory.create(settings)

    init {
        try { session.connect(CONNECT_TIMEOUT) } catch (error: Exception) { session.disconnect(); throw error }
    }

    override fun exec(command: String): String = execute(command).toString(Charsets.UTF_8).trim()
    override fun download(path: String): ByteArray = execute("cat ${quote(path)}")

    override fun downloadMany(paths: List<String>): List<ByteArray> {
        if (paths.size <= 1) return paths.map(::download)
        val command = buildString {
            append("for f in")
            paths.forEach { append(' ').append(quote(it)) }
            // NUL-terminated path, a decimal byte count, then exactly that many raw bytes.
            // Binary sidecar content is never scanned for delimiters.
            append("; do printf '%s\\000' \"\$f\"; wc -c < \"\$f\"; cat \"\$f\"; done")
        }
        val output = execute(command)
        return parseBatch(output, paths.size).also {
            runCatching { Log.i(SyncTrace.TAG, "sidecar-batch files=${paths.size} bytes=${output.size}") }
        }
    }

    private fun parseBatch(bytes: ByteArray, expected: Int): List<ByteArray> {
        val result = ArrayList<ByteArray>(expected)
        var index = 0
        while (index < bytes.size) {
            var pathEnd = index
            while (pathEnd < bytes.size && bytes[pathEnd] != 0.toByte()) pathEnd++
            check(pathEnd < bytes.size) { "Kindle 批量读取响应缺少路径分隔符" }
            index = pathEnd + 1
            var sizeEnd = index
            while (sizeEnd < bytes.size && bytes[sizeEnd] != '\n'.code.toByte()) sizeEnd++
            check(sizeEnd < bytes.size) { "Kindle 批量读取响应缺少文件长度" }
            val size = String(bytes, index, sizeEnd - index, Charsets.US_ASCII).trim().toIntOrNull()
            check(size != null && size >= 0) { "Kindle 批量读取响应包含无效文件长度" }
            index = sizeEnd + 1
            check(size <= bytes.size - index) { "Kindle 批量读取响应不完整" }
            result += bytes.copyOfRange(index, index + size)
            index += size
        }
        check(result.size == expected) { "Kindle 批量读取响应数量不匹配" }
        return result
    }

    override fun replace(path: String, original: ByteArray, updated: ByteArray, backup: String) {
        val token = UUID.randomUUID().toString()
        val directory = path.substringBeforeLast('/')
        val expected = "$directory/.kpilot-note-expected-$token.tmp"
        val temporary = "$directory/.kpilot-note-updated-$token.tmp"
        val backupTemporary = "$backup.partial"
        try {
            execute("umask 077; cat > ${quote(expected)}", original)
            // Copy metadata first, then truncate the existing temporary file: native reader access
            // permissions and ownership survive the final rename.
            execute("set -e; cp -p ${quote(path)} ${quote(temporary)}; cat > ${quote(temporary)}", updated)
            // cmp guards against changes made after the phone downloaded the current contents.
            // Backups stay on Kindle; only the temporary upload files are removed here.
            exec(NoteWritebackCommands.commit(path, expected, temporary, backup, backupTemporary))
        } finally {
            runCatching { exec("rm -f ${quote(expected)} ${quote(temporary)} ${quote(backupTemporary)}") }
        }
    }

    override fun restore(path: String, backup: String, written: ByteArray) {
        if (!session.isConnected) {
            session.disconnect()
            session = KindleSshSessionFactory.create(settings)
            try { session.connect(CONNECT_TIMEOUT) } catch (error: Exception) { session.disconnect(); throw error }
        }
        // A failed replace may not have reached backup creation. In that case the original is intact.
        // Restore via rename too, so a failed recovery copy cannot truncate the live file.
        val temporary = "$backup.restore-${UUID.randomUUID()}.tmp"
        val expected = "$temporary.expected"
        try {
            execute("umask 077; cat > ${quote(expected)}", written)
            exec(NoteWritebackCommands.restore(path, backup, expected, temporary))
        } finally { runCatching { exec("rm -f ${quote(temporary)} ${quote(expected)}") } }
    }

    private fun execute(command: String, input: ByteArray? = null): ByteArray {
        if (!session.isConnected) throw IllegalStateException("Kindle SSH 连接已断开，无法写入或恢复备份")
        val channel = session.openChannel("exec") as ChannelExec
        val output = LimitedOutputStream(MAX_RESPONSE_BYTES)
        val stderr = LimitedOutputStream(64 * 1024)
        channel.setCommand(command)
        channel.setOutputStream(output)
        channel.setErrStream(stderr)
        input?.let { channel.setInputStream(ByteArrayInputStream(it)) }
        try {
            channel.connect(CONNECT_TIMEOUT)
            val deadline = System.nanoTime() + COMMAND_TIMEOUT * 1_000_000
            while (!channel.isClosed && System.nanoTime() < deadline) Thread.sleep(20)
            check(channel.isClosed) { "Kindle 笔记操作超时" }
            check(channel.exitStatus == 0) {
                stderr.bytes().toString(Charsets.UTF_8).trim().ifEmpty { "Kindle 笔记操作失败（${channel.exitStatus}）" }
            }
            return output.bytes()
        } finally { channel.disconnect() }
    }

    override fun close() { session.disconnect() }

    private class LimitedOutputStream(private val maximum: Int) : OutputStream() {
        private val output = ByteArrayOutputStream()
        override fun write(value: Int) {
            check(output.size() < maximum) { "Kindle 文件超过笔记写入大小上限" }
            output.write(value)
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            check(length <= maximum - output.size()) { "Kindle 文件超过笔记写入大小上限" }
            output.write(bytes, offset, length)
        }
        fun bytes() = output.toByteArray()
    }

    companion object {
        private const val CONNECT_TIMEOUT = 10_000
        private const val COMMAND_TIMEOUT = 120_000L
        private const val MAX_RESPONSE_BYTES = 64 * 1024 * 1024
        internal fun quote(value: String) = "'${value.replace("'", "'\\''")}'"
    }
}

/** Pure commands are exercised with real local files as well as fake SSH transactions. */
internal object NoteWritebackCommands {
    private fun quote(value: String) = SshNoteWritebackConnection.quote(value)

    fun commit(path: String, expected: String, temporary: String, backup: String, backupTemporary: String): String {
        val unchanged = "cmp -s ${quote(expected)} ${quote(path)}"
        val conflict = "echo 'Kindle file changed during editing' >&2; exit 74"
        return "set -e; if ! $unchanged; then $conflict; fi; " +
            "cp -p ${quote(path)} ${quote(backupTemporary)}; " +
            "cmp -s ${quote(expected)} ${quote(backupTemporary)}; " +
            "mv -f ${quote(backupTemporary)} ${quote(backup)}; " +
            "if ! $unchanged; then $conflict; fi; mv -f ${quote(temporary)} ${quote(path)}; sync"
    }

    fun restore(path: String, backup: String, expected: String, temporary: String): String =
        "set -e; if [ -f ${quote(backup)} ]; then " +
            "if cmp -s ${quote(backup)} ${quote(path)}; then exit 0; fi; " +
            "cp -p ${quote(backup)} ${quote(temporary)}; " +
            "if ! cmp -s ${quote(expected)} ${quote(path)}; then " +
            "echo 'Kindle file changed after writing; backup kept for recovery' >&2; exit 75; fi; " +
            "mv -f ${quote(temporary)} ${quote(path)}; sync; cmp -s ${quote(backup)} ${quote(path)}; fi"
}
