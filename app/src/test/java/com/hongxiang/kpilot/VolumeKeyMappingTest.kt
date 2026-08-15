package com.hongxiang.kpilot

import android.view.KeyEvent
import com.hongxiang.kpilot.model.KindleCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeKeyMappingTest {
    @Test
    fun volumeUpSubmitsPrevious() {
        assertEquals(KindleCommand.PREVIOUS, commandForVolumeKey(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun volumeDownSubmitsNext() {
        assertEquals(KindleCommand.NEXT, commandForVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test
    fun unrelatedKeyDoesNotSubmitACommand() {
        assertNull(commandForVolumeKey(KeyEvent.KEYCODE_BACK))
    }
}
