package com.hongxiang.kpilot.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KindleIdentityServiceTest {
    @Test fun parsesOnlySafeSingleLineIds() {
        val id = UUID.randomUUID().toString()
        assertEquals(id, KindleIdentityService.parse("$id\n"))
        assertEquals(id, KindleIdentityService.parse("  $id  \nignored"))
        assertNull(KindleIdentityService.parse(""))
        assertNull(KindleIdentityService.parse("bad id"))
        assertNull(KindleIdentityService.parse("a".repeat(129)))
    }

    @Test fun commandCreatesTheIdentityWithAnAtomicRename() {
        val id = UUID.randomUUID().toString()
        val command = KindleIdentityService.command(id)
        assertTrue(command.contains("/mnt/us/.kpilot"))
        assertTrue(command.contains("device-id"))
        assertTrue(command.contains("mkdir"))
        assertTrue(command.contains("mv -f"))
        assertTrue(command.contains("'$id'"))
        assertTrue(command.contains("cat"))
    }

    @Test fun rejectsUnsafeProposedIds() {
        assertThrows(IllegalArgumentException::class.java) {
            KindleIdentityService.command("bad'id")
        }
    }
}
