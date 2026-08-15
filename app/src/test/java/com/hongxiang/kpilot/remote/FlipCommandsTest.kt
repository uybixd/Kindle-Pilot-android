package com.hongxiang.kpilot.remote

import com.hongxiang.kpilot.data.ConnectionSettings
import com.hongxiang.kpilot.model.KindleCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipCommandsTest {
    @Test
    fun definesTheFourMacCompatibleFlipCommandFiles() {
        assertEquals(
            listOf(
                "/mnt/us/FlipCmd/next_portrait.event",
                "/mnt/us/FlipCmd/prev_portrait.event",
                "/mnt/us/FlipCmd/next_landscape.event",
                "/mnt/us/FlipCmd/prev_landscape.event"
            ),
            FlipCommandDefinition.all.map { it.remotePath }
        )
        assertEquals(
            listOf("竖屏下一页", "竖屏上一页", "横屏下一页", "横屏上一页"),
            FlipCommandDefinition.all.map { it.title }
        )
    }

    @Test
    fun parsesOnlyMissingPathsFromTheProbe() {
        val output = """
            /mnt/us/FlipCmd/prev_portrait.event
            ignored diagnostic
            /mnt/us/FlipCmd/next_landscape.event
        """.trimIndent()

        assertEquals(
            listOf(
                FlipCommandDefinition(KindleCommand.PREVIOUS, KindleScreenOrientation.PORTRAIT),
                FlipCommandDefinition(KindleCommand.NEXT, KindleScreenOrientation.LANDSCAPE)
            ),
            FlipCommandDefinition.missingFromProbeOutput(output)
        )
    }

    @Test
    fun firstTurnAndConnectionChangesRequireANewCheck() {
        val first = ConnectionSettings(host = "kindle")
        val changed = first.copy(host = "kindle-new")

        assertTrue(requiresFlipCommandCheck(null, first, hasChecked = false))
        assertTrue(requiresFlipCommandCheck(first, first, hasChecked = false))
        assertFalse(requiresFlipCommandCheck(first, first, hasChecked = true))
        assertTrue(requiresFlipCommandCheck(first, changed, hasChecked = true))
    }
}
