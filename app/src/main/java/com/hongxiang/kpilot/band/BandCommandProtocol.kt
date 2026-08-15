package com.hongxiang.kpilot.band

import com.hongxiang.kpilot.model.KindleCommand
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.LinkedHashMap

data class BandPageCommand(
    val command: KindleCommand,
    val requestId: String
)

object BandCommandProtocol {
    private const val MAX_PAYLOAD_BYTES = 2 * 1024
    private val requestIdPattern = Regex("[A-Za-z0-9._:-]{1,80}")

    fun decode(payload: ByteArray): BandPageCommand? {
        if (payload.isEmpty() || payload.size > MAX_PAYLOAD_BYTES) return null
        val json = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload))
                .toString()
        }.getOrNull() ?: return null

        if (numberField(json, "version") != 1) return null
        if (stringField(json, "type") != "kindle.page") return null
        val requestId = stringField(json, "requestId")
            ?.takeIf(requestIdPattern::matches)
            ?: return null
        val command = when (stringField(json, "action")) {
            "previous" -> KindleCommand.PREVIOUS
            "next" -> KindleCommand.NEXT
            else -> return null
        }
        return BandPageCommand(command, requestId)
    }

    fun acknowledgement(requestId: String, status: String): ByteArray {
        require(requestIdPattern.matches(requestId))
        require(status in setOf("accepted", "duplicate", "remote_off", "invalid"))
        return """{"version":1,"type":"kindle.page.ack","requestId":"$requestId","status":"$status"}"""
            .toByteArray(Charsets.UTF_8)
    }

    private fun stringField(json: String, name: String): String? =
        Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"([^\\\"\\\\]*)\\\"")
            .find(json)
            ?.groupValues
            ?.get(1)

    private fun numberField(json: String, name: String): Int? =
        Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*(\\d+)")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
}

class RecentBandCommandIds(
    private val retentionMillis: Long = 30_000,
    private val maxEntries: Int = 64,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val seen = LinkedHashMap<String, Long>()

    @Synchronized
    fun isDuplicate(requestId: String): Boolean {
        val currentTime = now()
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (currentTime - iterator.next().value > retentionMillis) iterator.remove()
        }
        if (seen.containsKey(requestId)) return true
        seen[requestId] = currentTime
        while (seen.size > maxEntries) seen.remove(seen.entries.first().key)
        return false
    }
}
