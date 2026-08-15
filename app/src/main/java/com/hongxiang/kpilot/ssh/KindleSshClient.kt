package com.hongxiang.kpilot.ssh

import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.model.KindleCommand
import com.hongxiang.kpilot.remote.FlipCommandDefinition
import com.hongxiang.kpilot.remote.remoteEventName
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream

internal data class OrientationResult(val orientation: String, val usedPortraitFallback: Boolean)

class KindleSshClient {
    private data class SessionKey(
        val host: String,
        val port: Int,
        val username: String,
        val password: String
    )

    private data class CachedOrientation(
        val result: OrientationResult,
        val detectedAtMs: Long
    )

    private var sessionKey: SessionKey? = null
    private var session: Session? = null
    private var detectedEventDevice: String? = null
    private var cachedOrientation: CachedOrientation? = null

    @Synchronized
    fun test(settings: ConnectionSettings): String = withConnectedSession(settings) { session ->
        check(execute(session, "printf Kindle_Pilot_OK") == "Kindle_Pilot_OK") {
            "Unexpected response from Kindle"
        }
        "Connected to ${settings.host}:${settings.port}"
    }

    @Synchronized
    fun send(settings: ConnectionSettings, command: KindleCommand): String = withConnectedSession(settings) { session ->
        check(command != KindleCommand.TRANSLATE) {
            "Translate is reserved until a Kindle-side translation command is configured"
        }
        val now = System.currentTimeMillis()
        val orientationResult = cachedOrientation
            ?.takeIf { now - it.detectedAtMs < ORIENTATION_CACHE_MS }
            ?.result
            ?: resolveOrientation(execute(session, "fbset")).also {
                cachedOrientation = CachedOrientation(it, now)
            }
        val orientation = orientationResult.orientation
        val event = resolveEventDevice(settings, session)
        val remote = "/mnt/us/FlipCmd/${command.remoteEventName}_${orientation}.event"
        // Exact replay convention from the desktop Python KPilot app.
        execute(
            session,
            "test -s $remote || { echo '缺少翻页命令，请先检查并录制' >&2; exit 44; }; " +
                "cat $remote > /dev/input/$event && /usr/bin/powerd_test -i"
        )
        if (orientationResult.usedPortraitFallback) {
            "${command.label} sent ($orientation, $event). Warning: fbset orientation was unclear; default portrait was used."
        } else {
            "${command.label} sent ($orientation, $event)"
        }
    }

    @Synchronized
    fun missingFlipCommands(settings: ConnectionSettings): List<FlipCommandDefinition> =
        withConnectedSession(settings) { session ->
            val probe = FlipCommandDefinition.all.joinToString("; ") { definition ->
                "test -s ${shellQuote(definition.remotePath)} || echo ${shellQuote(definition.remotePath)}"
            }
            FlipCommandDefinition.missingFromProbeOutput(execute(session, probe))
        }

    @Synchronized
    fun recordFlipCommand(
        settings: ConnectionSettings,
        definition: FlipCommandDefinition,
        durationSeconds: Int = 5
    ): String = withConnectedSession(settings) { session ->
        require(durationSeconds in 1..15)
        val event = resolveEventDevice(settings, session)
        val eventPath = "/dev/input/$event"
        val remotePath = definition.remotePath
        execute(
            session,
            "mkdir -p /mnt/us/FlipCmd; " +
                "rm -f ${shellQuote(remotePath)}; " +
                "timeout $durationSeconds cat ${shellQuote(eventPath)} > ${shellQuote(remotePath)} 2>/dev/null; " +
                "test -s ${shellQuote(remotePath)} && { echo recorded; exit 0; }; " +
                "echo '录制为空，请重试并在 5 秒内操作 Kindle' >&2; exit 45"
        )
        "${definition.title}录制完成"
    }

    @Synchronized
    fun detectTouchDevice(settings: ConnectionSettings): String = withConnectedSession(settings) { session ->
        resolveEventDevice(settings.copy(eventDevice = ""), session)
    }

    private fun resolveEventDevice(settings: ConnectionSettings, session: Session): String =
        settings.eventDevice.ifBlank {
            detectedEventDevice ?: detectTouchDevice(execute(session, "cat /proc/bus/input/devices"))
                ?.also { detectedEventDevice = it }
            ?: error("没有识别到 /dev/input/eventX")
        }

    private fun <T> withConnectedSession(settings: ConnectionSettings, block: (Session) -> T): T {
        settings.validate()
        val connectedSession = connectedSession(settings)
        return try {
            block(connectedSession)
        } catch (failure: Throwable) {
            // A failed command can leave JSch reporting a stale connected session.
            // Do not retry page turns automatically because that could turn twice.
            disconnectSession()
            throw failure
        }
    }

    private fun connectedSession(settings: ConnectionSettings): Session {
        val requestedKey = SessionKey(
            host = settings.host,
            port = settings.port,
            username = settings.username,
            password = settings.password
        )
        if (sessionKey != requestedKey) {
            disconnectSession()
            sessionKey = requestedKey
            detectedEventDevice = null
            cachedOrientation = null
        }
        session?.takeIf { it.isConnected }?.let { return it }

        val newSession = KindleSshSessionFactory.create(settings)
        newSession.setServerAliveInterval(SERVER_ALIVE_INTERVAL_MS)
        newSession.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX)
        try {
            newSession.connect(TIMEOUT_MS)
        } catch (failure: Throwable) {
            newSession.disconnect()
            throw failure
        }
        session = newSession
        return newSession
    }

    private fun disconnectSession() {
        session?.disconnect()
        session = null
    }

    private fun execute(session: Session, command: String): String {
        val channel = session.openChannel("exec") as ChannelExec
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        channel.setCommand(command)
        channel.setOutputStream(stdout)
        channel.setErrStream(stderr)
        try {
            channel.connect(TIMEOUT_MS)
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (!channel.isClosed && System.currentTimeMillis() < deadline) Thread.sleep(CHANNEL_POLL_MS)
            check(channel.isClosed) { "Kindle command timed out" }
            check(channel.exitStatus == 0) {
                stderr.toString("UTF-8").trim().ifBlank { "Kindle command failed (${channel.exitStatus})" }
            }
            return stdout.toString("UTF-8").trim()
        } finally {
            channel.disconnect()
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    companion object {
        internal fun detectOrientation(fbset: String): String {
            return resolveOrientation(fbset).orientation
        }

        internal fun resolveOrientation(fbset: String): OrientationResult {
            val modeDimensions = Regex("(?m)^\\s*mode\\s+\"(\\d+)x(\\d+)\"")
                .find(fbset)
                ?.let { it.groupValues[1].toIntOrNull() to it.groupValues[2].toIntOrNull() }
            // Some Kindle fbset builds omit the mode resolution. In the standard geometry line,
            // the first two values are the visible x/y resolution; later values are virtual size/depth.
            val geometryDimensions = Regex(
                "(?m)^\\s*geometry\\s+(\\d+)\\s+(\\d+)\\s+\\d+\\s+\\d+\\s+\\d+\\s*$"
            ).find(fbset)
                ?.let { it.groupValues[1].toIntOrNull() to it.groupValues[2].toIntOrNull() }
            val dimensions = modeDimensions ?: geometryDimensions
                ?: return OrientationResult("portrait", usedPortraitFallback = true)
            val (width, height) = dimensions
            if (width == null || height == null || width <= 0 || height <= 0 || width == height) {
                return OrientationResult("portrait", usedPortraitFallback = true)
            }
            return if (width > height) {
                OrientationResult("landscape", usedPortraitFallback = false)
            } else {
                OrientationResult("portrait", usedPortraitFallback = false)
            }
        }

        internal fun detectTouchDevice(devices: String): String? = devices
            .split(Regex("(?m)(?=^I:)"))
            .firstOrNull { it.contains("N: Name=\"pt_mt\"") && it.contains(Regex("(?m)^B: ABS=")) }
            ?.let { Regex("event\\d+").find(it)?.value }

        private const val TIMEOUT_MS = 10_000
        private const val CHANNEL_POLL_MS = 5L
        private const val ORIENTATION_CACHE_MS = 10_000L
        private const val SERVER_ALIVE_INTERVAL_MS = 15_000
        private const val SERVER_ALIVE_COUNT_MAX = 2
    }
}
