package com.hongxiang.kpilot.ssh

import com.jcraft.jsch.JSch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KindleSshSessionFactoryTest {
    @Test
    fun androidSevenUsesOnlyNonEcHandshakeAlgorithms() {
        val session = JSch().getSession("root", "192.168.1.2", 22)

        KindleSshSessionFactory.configure(session, sdkInt = 25)

        assertEquals(KindleSshSessionFactory.LEGACY_ANDROID_KEX, session.getConfig("kex"))
        assertEquals(
            KindleSshSessionFactory.LEGACY_ANDROID_HOST_KEYS,
            session.getConfig("server_host_key")
        )
        assertFalse(session.getConfig("kex").contains("curve", ignoreCase = true))
        assertFalse(session.getConfig("kex").contains("ecdh", ignoreCase = true))
        assertFalse(session.getConfig("server_host_key").contains("ecdsa", ignoreCase = true))
        assertTrue(session.getConfig("kex").startsWith("diffie-hellman-group14-sha256"))
        assertTrue(session.getConfig("server_host_key").startsWith("rsa-sha2-512"))
    }

    @Test
    fun androidEightAndNewerRetainModernJschAlgorithms() {
        val session = JSch().getSession("root", "192.168.1.2", 22)
        val defaultKex = session.getConfig("kex")
        val defaultHostKeys = session.getConfig("server_host_key")

        KindleSshSessionFactory.configure(session, sdkInt = 26)

        assertEquals(defaultKex, session.getConfig("kex"))
        assertEquals(defaultHostKeys, session.getConfig("server_host_key"))
        assertTrue(session.getConfig("kex").contains("ecdh-sha2-nistp256"))
    }

    @Test
    fun everySessionPrefersTheCredentialsKpilotActuallyProvides() {
        val session = JSch().getSession("root", "192.168.1.2", 22)

        KindleSshSessionFactory.configure(session, sdkInt = 25)

        assertEquals("password,keyboard-interactive", session.getConfig("PreferredAuthentications"))
        assertEquals("no", session.getConfig("StrictHostKeyChecking"))
    }
}
