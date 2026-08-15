package com.hongxiang.kpilot.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class KindleSshClientTest {
    @Test
    fun detectsOrientation() {
        assertEquals("portrait", KindleSshClient.detectOrientation("mode \"1264x1680\""))
        assertEquals("landscape", KindleSshClient.detectOrientation("mode \"1680x1264\""))
    }

    @Test
    fun detectsPortraitFromKindleGeometryBlock() {
        val fbset = """mode "kindle"
    # D: 60.000 MHz, H: 40.000 kHz, V: 60.000 Hz
    geometry 1272 1696 1272 3392 8
    timings 0 0 0 0 0 0 0
endmode"""

        assertEquals("portrait", KindleSshClient.detectOrientation(fbset))
    }

    @Test
    fun detectsLandscapeFromGeometryWhitespace() {
        assertEquals(
            "landscape",
            KindleSshClient.detectOrientation("  geometry\t1696 1272 3392 1272 8  ")
        )
    }

    @Test
    fun defaultsToPortraitWhenFbsetOrientationIsUnclear() {
        assertEquals(
            OrientationResult("portrait", usedPortraitFallback = true),
            KindleSshClient.resolveOrientation("mode \"kindle\"\ntimings 0 0 0 0 0 0 0")
        )
        assertEquals(
            OrientationResult("portrait", usedPortraitFallback = true),
            KindleSshClient.resolveOrientation("geometry 1000 1000 1000 2000 8")
        )
    }

    @Test
    fun validDimensionsDoNotReportFallback() {
        assertEquals(
            OrientationResult("portrait", usedPortraitFallback = false),
            KindleSshClient.resolveOrientation("geometry 1272 1696 1272 3392 8")
        )
        assertEquals(
            OrientationResult("landscape", usedPortraitFallback = false),
            KindleSshClient.resolveOrientation("mode \"1696x1272\"")
        )
    }

    @Test
    fun detectsPtMtEvent() {
        val devices = """I: Bus=0018
N: Name="pt_mt"
H: Handlers=event3
B: ABS=123

I: Bus=0019
N: Name="other"
H: Handlers=event4"""
        assertEquals("event3", KindleSshClient.detectTouchDevice(devices))
    }
}
