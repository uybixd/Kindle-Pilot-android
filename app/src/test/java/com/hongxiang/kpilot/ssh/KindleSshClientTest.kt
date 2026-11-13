package com.hongxiang.kpilot.ssh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class KindleSshClientTest {
    @get:Rule val temporary = TemporaryFolder()

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

    @Test
    fun detectsPaperwhite4GoodixHandlerFromDeviceLog() {
        val devices = """
            [root@kindle root]# cat /proc/bus/input/devices
            I: Bus=0019 Vendor=0000 Product=0000 Version=0000
            N: Name="20cc000.snvs:snvs-powerkey"
            H: Handlers=kbd event0
            B: EV=3

            I: Bus=0019 Vendor=0000 Product=0000 Version=0000
            N: Name="bd71827-power"
            H: Handlers=kbd event1
            B: EV=3

            I: Bus=0018 Vendor=dead Product=beef Version=28bb
            N: Name="goodix-ts"
            P: Phys=input/ts
            S: Sysfs=/devices/virtual/input/input2
            H: Handlers=event2
            B: PROP=2
            B: EV=b
            B: ABS=6e58000 0
        """.trimIndent().replace("\n", "\r\n")

        assertEquals("event2", KindleSshClient.detectTouchDevice(devices))
        assertEquals("event7", KindleSshClient.detectTouchDevice(devices.replace("Handlers=event2", "Handlers=mouse0 event7")))
    }

    @Test
    fun ignoresEventNumbersOutsideTouchDeviceHandlers() {
        val devices = """
            I: Bus=0018
            N: Name="goodix-ts"
            P: Phys=input/event2
            H: Handlers=mouse0 event2-extra
            B: ABS=6e58000 0

            I: Bus=0019
            N: Name="power"
            H: Handlers=kbd event1
            B: EV=3
        """.trimIndent()

        assertNull(KindleSshClient.detectTouchDevice(devices))
    }

    @Test
    fun stopsStreamingReaderAndSavesBinaryEvents() {
        assumeTrue(File("/bin/sh").canExecute())
        val input = File(temporary.root, "event2")
        val directory = File(temporary.root, "FlipCmd")
        val output = File(directory, "next_portrait.event")
        val mkfifo = ProcessBuilder("mkfifo", input.path).start()
        assertTrue(mkfifo.waitFor(5, TimeUnit.SECONDS))
        assertEquals(0, mkfifo.exitValue())
        val writer = ProcessBuilder(
            "/bin/sh", "-c", """exec 3>"$1"; printf '\000\377\015\012' >&3; exec sleep 30""", "sh", input.path
        ).start()
        try {
            val (status, message) = record(input.path, output)
            assertEquals(message, 0, status)
            assertArrayEquals(byteArrayOf(0, -1, 13, 10), output.readBytes())
            assertEquals(listOf(output.name), directory.list()?.toList())
        } finally {
            writer.destroyForcibly()
        }
    }

    @Test
    fun preservesPreviousRecordingOnEmptyCaptureOrReadError() {
        assumeTrue(File("/bin/sh").canExecute())
        val output = temporary.newFile("next_portrait.event").apply { writeText("previous recording") }

        val (emptyStatus, emptyMessage) = record("/dev/null", output)
        assertEquals(45, emptyStatus)
        assertTrue(emptyMessage, emptyMessage.contains("未采集到触控事件"))
        assertEquals("previous recording", output.readText())

        val missingPath = File(temporary.root, "missing-event2").path
        val (readStatus, readMessage) = record(missingPath, output)
        assertNotEquals(0, readStatus)
        assertNotEquals(45, readStatus)
        assertTrue(readMessage, readMessage.contains(missingPath))
        assertEquals("previous recording", output.readText())
        assertEquals(listOf(output.name), temporary.root.list()?.toList())
    }

    private fun record(input: String, output: File): Pair<Int, String> {
        val command = KindleSshClient.recordFlipCommandScript(input, output.path, durationSeconds = 1)
        val process = ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(true).start()
        try {
            assertTrue("Recording command timed out", process.waitFor(5, TimeUnit.SECONDS))
            return process.exitValue() to process.inputStream.bufferedReader().use { it.readText() }
        } finally {
            process.destroy()
        }
    }
}
