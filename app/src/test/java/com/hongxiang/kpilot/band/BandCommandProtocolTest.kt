package com.hongxiang.kpilot.band

import com.hongxiang.kpilot.model.KindleCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BandCommandProtocolTest {
    @Test fun decodesPreviousAndNextCommands() {
        val previous = BandCommandProtocol.decode(
            """{"version":1,"type":"kindle.page","action":"previous","requestId":"100-1"}""".toByteArray()
        )
        val next = BandCommandProtocol.decode(
            """{"requestId":"100-2","action":"next","type":"kindle.page","version":1}""".toByteArray()
        )

        assertEquals(KindleCommand.PREVIOUS, previous?.command)
        assertEquals("100-1", previous?.requestId)
        assertEquals(KindleCommand.NEXT, next?.command)
    }

    @Test fun rejectsUnknownOrOversizedPayloads() {
        assertNull(BandCommandProtocol.decode("""{"version":2,"type":"kindle.page","action":"next","requestId":"1"}""".toByteArray()))
        assertNull(BandCommandProtocol.decode("""{"version":1,"type":"kindle.page","action":"delete","requestId":"1"}""".toByteArray()))
        assertNull(BandCommandProtocol.decode(ByteArray(2049) { 'a'.code.toByte() }))
        assertNull(BandCommandProtocol.decode(byteArrayOf(0xc3.toByte(), 0x28)))
    }

    @Test fun acknowledgementContainsOnlyValidatedFields() {
        val text = BandCommandProtocol.acknowledgement("100-1", "accepted").toString(Charsets.UTF_8)
        assertTrue(text.contains("\"requestId\":\"100-1\""))
        assertTrue(text.contains("\"status\":\"accepted\""))
    }

    @Test fun recentIdsRejectDuplicatesAndExpireOldEntries() {
        var time = 1_000L
        val ids = RecentBandCommandIds(retentionMillis = 100, now = { time })

        assertFalse(ids.isDuplicate("a"))
        assertTrue(ids.isDuplicate("a"))
        time += 101
        assertFalse(ids.isDuplicate("a"))
    }
}
