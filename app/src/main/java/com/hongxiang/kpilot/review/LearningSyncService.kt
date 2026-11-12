package com.hongxiang.kpilot.review

import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.ssh.KindleSshSessionFactory
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Syncs review/favorite/definition data through a single Kindle-hosted state file.
 * A Kindle-side lock serializes read-merge-write cycles; the final upload is a
 * temp-file rename so readers never see a partial file.
 */
class LearningSyncService {
    fun sync(settings: ConnectionSettings, local: LearningState): LearningState {
        settings.validate()
        val session = KindleSshSessionFactory.create(settings)
        session.setServerAliveInterval(SERVER_ALIVE_INTERVAL_MS)
        session.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX)
        try {
            session.connect(CONNECT_TIMEOUT_MS)
            acquireLock(session)
            try {
                val remoteBytes = execute(session, readCommand(), MAX_STATE_BYTES, READ_TIMEOUT_MS)
                val remoteText = remoteBytes.toString(Charsets.UTF_8).trim()
                val remote = if (remoteText.isEmpty()) LearningState() else runCatching {
                    LearningCodec.decode(remoteText)
                }.getOrElse { error ->
                    if (error is UnsupportedLearningSchemaException) {
                        throw IllegalStateException("Kindle 回顾数据文件版本过新，请先更新应用，未写入", error)
                    }
                    throw IllegalStateException("Kindle 回顾数据文件格式无效，未写入", error)
                }
                val merged = LearningMerge.merge(local, remote)
                val shared = merged.copy(clippingDailyCounts = emptyMap())
                val encoded = LearningCodec.encode(shared, includeDailyCounts = false)
                    .toByteArray(Charsets.UTF_8)
                check(encoded.size <= MAX_STATE_BYTES) { "Kindle 回顾数据过大，无法写入" }
                uploadAtomic(session, encoded)
                val verified = execute(session, readCommand(), MAX_STATE_BYTES, READ_TIMEOUT_MS)
                check(verified.contentEquals(encoded)) { "回顾数据写入校验失败，请稍后重试" }
                return merged
            } finally {
                runCatching { execute(session, releaseLockCommand(), 1024, LOCK_RELEASE_TIMEOUT_MS) }
            }
        } finally {
            session.disconnect()
        }
    }

    private fun acquireLock(session: Session) {
        val output = execute(session, lockCommand(), 16 * 1024, LOCK_TIMEOUT_MS)
        check(output.toString(Charsets.UTF_8).contains("lock-acquired")) {
            "无法获取 Kindle 回顾数据锁，请稍后重试"
        }
    }

    private fun uploadAtomic(session: Session, data: ByteArray) {
        val temporary = "$REMOTE_DIRECTORY/.state-${UUID.randomUUID()}.tmp"
        val command = "set -e; mkdir -p ${quote(REMOTE_DIRECTORY)}; " +
            "cat > ${quote(temporary)}; " +
            "mv -f ${quote(temporary)} ${quote(REMOTE_PATH)}; sync"
        try {
            upload(session, command, data)
        } catch (error: Exception) {
            runCatching { execute(session, "rm -f ${quote(temporary)}", 1024, UPLOAD_TIMEOUT_MS) }
            throw error
        }
    }

    private fun execute(
        session: Session,
        command: String,
        maximumBytes: Int,
        timeoutMs: Long
    ): ByteArray {
        val channel = session.openChannel("exec") as ChannelExec
        val output = BoundedOutput(maximumBytes)
        val error = BoundedOutput(16 * 1024)
        channel.setCommand(command)
        channel.setOutputStream(output, true)
        channel.setErrStream(error, true)
        try {
            channel.connect(CONNECT_TIMEOUT_MS)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                output.failure?.let { throw it }
                error.failure?.let { throw it }
                Thread.sleep(20)
            }
            output.failure?.let { throw it }
            error.failure?.let { throw it }
            check(channel.isClosed) { "同步 Kindle 回顾数据超时" }
            check(channel.exitStatus == 0) {
                error.bytes.toString(Charsets.UTF_8).trim().ifBlank {
                    "同步 Kindle 回顾数据失败 (${channel.exitStatus})"
                }
            }
            return output.bytes
        } finally {
            channel.disconnect()
        }
    }

    private fun upload(session: Session, command: String, data: ByteArray) {
        val channel = session.openChannel("exec") as ChannelExec
        val error = BoundedOutput(16 * 1024)
        channel.setCommand(command)
        channel.setErrStream(error, true)
        channel.setInputStream(ByteArrayInputStream(data))
        try {
            channel.connect(CONNECT_TIMEOUT_MS)
            val deadline = System.currentTimeMillis() + UPLOAD_TIMEOUT_MS
            while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                error.failure?.let { throw it }
                Thread.sleep(20)
            }
            error.failure?.let { throw it }
            check(channel.isClosed) { "写入 Kindle 回顾数据超时" }
            check(channel.exitStatus == 0) {
                error.bytes.toString(Charsets.UTF_8).trim().ifBlank {
                    "写入 Kindle 回顾数据失败 (${channel.exitStatus})"
                }
            }
        } finally {
            channel.disconnect()
        }
    }

    private fun readCommand() =
        "d=${quote(REMOTE_DIRECTORY)}; if [ -f \"\$d/state.json\" ]; then cat \"\$d/state.json\"; fi"

    private fun releaseLockCommand() = "rmdir ${quote(REMOTE_DIRECTORY)}/.lock 2>/dev/null || true"

    private fun quote(value: String) = "'${value.replace("'", "'\\''")}'"

    private fun lockCommand() = """
        set -e
        d=${quote(REMOTE_DIRECTORY)}
        mkdir -p "${'$'}d"
        lock="${'$'}d/.lock"
        i=0
        while ! mkdir "${'$'}lock" 2>/dev/null; do
          i=${'$'}((i+1))
          if [ "${'$'}i" -ge 15 ]; then
            now=${'$'}(date +%s)
            mtime=${'$'}(stat -c %Y "${'$'}lock" 2>/dev/null || echo "${'$'}now")
            if [ "${'$'}((now - mtime))" -ge $STALE_LOCK_SECONDS ]; then
              rmdir "${'$'}lock" 2>/dev/null || true
              if mkdir "${'$'}lock" 2>/dev/null; then break; fi
            fi
            if [ "${'$'}i" -ge 30 ]; then
              echo "无法获取 Kindle 回顾数据锁" >&2
              exit 42
            fi
          fi
          sleep 1
        done
        echo lock-acquired
    """.trimIndent()

    private class BoundedOutput(private val maximum: Int) : OutputStream() {
        private val output = ByteArrayOutputStream()
        @Volatile var failure: IOException? = null
            private set
        val bytes: ByteArray get() = output.toByteArray()
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
        override fun write(data: ByteArray, offset: Int, length: Int) {
            if (output.size().toLong() + length > maximum) {
                val error = IOException("Kindle 回顾数据文件超过允许的大小")
                failure = error
                throw error
            }
            output.write(data, offset, length)
        }
    }

    private companion object {
        const val REMOTE_DIRECTORY = "/mnt/us/kpilot/learning"
        const val REMOTE_PATH = "$REMOTE_DIRECTORY/state.json"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 120_000L
        const val UPLOAD_TIMEOUT_MS = 120_000L
        const val LOCK_TIMEOUT_MS = 45_000L
        const val LOCK_RELEASE_TIMEOUT_MS = 10_000L
        // Longer than the read + upload + verify timeouts below, so a slow but live
        // sync is never mistaken for an abandoned lock.
        const val STALE_LOCK_SECONDS = 900
        const val SERVER_ALIVE_INTERVAL_MS = 15_000
        const val SERVER_ALIVE_COUNT_MAX = 3
        const val MAX_STATE_BYTES = 8 * 1024 * 1024
    }
}
