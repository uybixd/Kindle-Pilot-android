package com.hongxiang.kpilot.data

import org.junit.Assert.*
import org.junit.Test

class NavigationOptionsTest {
    @Test fun defaultsKeepFiveTabsAndMandatoryScreens() {
        assertEquals(5, NavigationOptions.decode(null).size)
        assertTrue(NavigationOptions.decode(null).containsAll(NavigationOptions.required))
        assertEquals(NavigationOptions.required, NavigationOptions.decode(setOf("UNKNOWN")))
    }

    @Test fun limitRequiresHidingAnOptionalTabBeforeAddingAnother() {
        val defaults = NavigationOptions.defaults
        assertEquals(defaults, NavigationOptions.update(defaults, NavigationItem.REVIEW, true))
        val hidden = NavigationOptions.update(defaults, NavigationItem.VOCABULARY, false)
        val updated = NavigationOptions.update(hidden, NavigationItem.REVIEW, true)
        assertTrue(NavigationItem.REVIEW in updated)
        assertFalse(NavigationItem.VOCABULARY in updated)
        assertEquals(updated, NavigationOptions.update(updated, NavigationItem.NOTES, false))
        assertEquals(updated, NavigationOptions.decode(updated.map { it.name }.toSet()))
    }

    @Test fun connectionScopeSurvivesCredentialEditsButSeparatesDevices() {
        val settings = ConnectionSettings(host = " Kindle.Local ")
        assertEquals(settings.cacheScope(), settings.copy(host = "kindle.local", password = "different", eventDevice = "event2").cacheScope())
        assertNotEquals(settings.cacheScope(), settings.copy(host = "other.local").cacheScope())
        assertNotEquals(settings.cacheScope(), settings.copy(port = 2222).cacheScope())
        assertNotEquals(settings.cacheScope(), settings.copy(username = "other").cacheScope())
    }
}
