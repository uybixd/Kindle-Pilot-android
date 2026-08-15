package com.hongxiang.kpilot.remote

import android.view.KeyEvent
import com.hongxiang.kpilot.model.KindleCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteKeyPolicyTest {
    @Test fun masterOffPassesKeysAndNeverCommands() {
        assertFalse(RemoteCommandGate.allows(false))
        val result = RemoteKeyPolicy.evaluate(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, false, true)
        assertNull(result.command)
        assertFalse(result.consume)
    }

    @Test fun mappingOffPassesKeysEvenWhenMasterOn() {
        val result = RemoteKeyPolicy.evaluate(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0, true, false)
        assertNull(result.command)
        assertFalse(result.consume)
    }

    @Test fun enabledMappingUsesRequestedDirectionsAndConsumesBothKeyPhases() {
        val up = RemoteKeyPolicy.evaluate(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, true, true)
        val down = RemoteKeyPolicy.evaluate(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0, true, true)
        val release = RemoteKeyPolicy.evaluate(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0, true, true)
        assertEquals(KindleCommand.PREVIOUS, up.command)
        assertEquals(KindleCommand.NEXT, down.command)
        assertTrue(up.consume)
        assertTrue(down.consume)
        assertNull(release.command)
        assertTrue(release.consume)
    }

    @Test fun keyRepeatDoesNotQueueMoreCommands() {
        val repeat = RemoteKeyPolicy.evaluate(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 2, true, true)
        assertNull(repeat.command)
        assertTrue(repeat.consume)
    }

    @Test fun keyboardDirectionsMapUpAndLeftBackDownAndRightForward() {
        val up = RemoteKeyPolicy.evaluate(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN, 0, true, false, true)
        val left = RemoteKeyPolicy.evaluate(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN, 0, true, false, true)
        val down = RemoteKeyPolicy.evaluate(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 0, true, false, true)
        val right = RemoteKeyPolicy.evaluate(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN, 0, true, false, true)

        assertEquals(KindleCommand.PREVIOUS, up.command)
        assertEquals(KindleCommand.PREVIOUS, left.command)
        assertEquals(KindleCommand.NEXT, down.command)
        assertEquals(KindleCommand.NEXT, right.command)
        assertTrue(listOf(up, left, down, right).all(RemoteKeyResult::consume))
    }

    @Test fun keyboardDirectionsPassThroughWhenKeyboardMappingIsOff() {
        val result = RemoteKeyPolicy.evaluate(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN, 0, true, true, false)
        assertNull(result.command)
        assertFalse(result.consume)
    }

    @Test fun backgroundRuntimeOnlyRunsForAnEnabledHardwareMapping() {
        assertFalse(RemoteBackgroundPolicy.shouldRun(false, true, true))
        assertFalse(RemoteBackgroundPolicy.shouldRun(true, false, false))
        assertTrue(RemoteBackgroundPolicy.shouldRun(true, true, false))
        assertTrue(RemoteBackgroundPolicy.shouldRun(true, false, true))
    }
}
