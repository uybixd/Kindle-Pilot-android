package com.hongxiang.kpilot.ssh

import com.hongxiang.kpilot.data.ConnectionSettings
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Reads the stable Kindle identity used to namespace local data.
 * The first phone creates it atomically; every other phone reuses the same file,
 * so IP changes and multiple phones keep pointing at the same Kindle namespace.
 */
class KindleIdentityService {
    fun resolve(settings: ConnectionSettings): String {
        settings.validate()
        val session = KindleSshSessionFactory.create(settings)
        try {
            session.connect(CONNECT_TIMEOUT_MS)
            repeat(2) {
                val output = execute(session, command(UUID.randomUUID().toString()))
                parse(output)?.let { return it }
            }
            error("无法读取 Kindle 设备标识")
        } finally { session.disconnect() }
    }

    private fun execute(session: Session, command: String): String {
        val channel = session.openChannel("exec") as ChannelExec
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        channel.setCommand(command)
        channel.setOutputStream(stdout)
        channel.setErrStream(stderr)
        try {
            channel.connect(CONNECT_TIMEOUT_MS)
            val deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
            while (!channel.isClosed && System.currentTimeMillis() < deadline) Thread.sleep(20)
            check(channel.isClosed) { "读取 Kindle 设备标识超时" }
            check(channel.exitStatus == 0) {
                stderr.toByteArray().toString(Charsets.UTF_8).trim().ifBlank {
                    "读取 Kindle 设备标识失败 (${channel.exitStatus})"
                }
            }
            return stdout.toByteArray().toString(Charsets.UTF_8)
        } finally { channel.disconnect() }
    }

    internal companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val COMMAND_TIMEOUT_MS = 30_000L
        private val idPattern = Regex("[A-Za-z0-9._-]{1,128}")

        internal fun command(proposedId: String): String {
            require(idPattern.matches(proposedId))
            val quoted = "'${proposedId.replace("'", "'\\''")}'"
            return """
                set -e
                d=/mnt/us/.kpilot
                f=${'$'}d/device-id
                mkdir -p "${'$'}d"
                if [ ! -s "${'$'}f" ]; then
                  lock=${'$'}d/.device-id.lock
                  if mkdir "${'$'}lock" 2>/dev/null; then
                    if [ ! -s "${'$'}f" ]; then
                      printf '%s\n' $quoted > "${'$'}f.tmp"
                      mv -f "${'$'}f.tmp" "${'$'}f"
                    fi
                    rmdir "${'$'}lock" 2>/dev/null || true
                  else
                    i=0
                    while [ ! -s "${'$'}f" ] && [ "${'$'}i" -lt 20 ]; do
                      sleep 1
                      i=${'$'}((i+1))
                    done
                    if [ ! -s "${'$'}f" ]; then rmdir "${'$'}lock" 2>/dev/null || true; fi
                  fi
                fi
                cat "${'$'}f"
            """.trimIndent()
        }

        internal fun parse(output: String): String? = output.lineSequence()
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)
            ?.takeIf(idPattern::matches)
    }
}
