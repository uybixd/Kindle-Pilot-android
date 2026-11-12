package com.hongxiang.kpilot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KindleConnectionScopeTest {
    private val scopePattern = Regex("[a-f0-9]{64}")

    @Test fun identityScopeIgnoresEndpointChanges() {
        val original = ConnectionSettings(
            host = "192.168.1.10", port = 22, username = "root", kindleId = "device-abc"
        )
        val moved = original.copy(host = "192.168.1.99", port = 2222, username = "admin")
        assertEquals(original.cacheScope(), moved.cacheScope())
        assertTrue(scopePattern.matches(original.cacheScope()))
    }

    @Test fun identityScopeIsSharedOnlyByTheSameKindle() {
        val first = ConnectionSettings(host = "192.168.1.10", kindleId = "device-a")
        val second = first.copy(kindleId = "device-b")
        val otherPhone = first.copy(password = "different-password")
        assertEquals(first.cacheScope(), otherPhone.cacheScope())
        assertNotEquals(first.cacheScope(), second.cacheScope())
    }

    @Test fun legacyScopeFollowsTheEndpointUntilIdentityIsKnown() {
        val first = ConnectionSettings(host = "192.168.1.10", username = "root")
        val second = first.copy(host = "192.168.1.11")
        assertEquals(first.legacyCacheScope(), first.cacheScope())
        assertNotEquals(first.cacheScope(), second.cacheScope())
        assertTrue(scopePattern.matches(first.cacheScope()))
    }
}
