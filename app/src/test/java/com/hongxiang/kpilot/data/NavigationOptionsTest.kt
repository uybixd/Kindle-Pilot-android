package com.hongxiang.kpilot.data

import org.junit.Assert.*
import org.junit.Test

class NavigationOptionsTest {
    @Test fun defaultsKeepFiveTabsAndMandatoryScreens() {
        assertEquals(5, NavigationOptions.decode(null).size)
        assertTrue(NavigationOptions.decode(null).containsAll(NavigationOptions.required))
        assertEquals(NavigationOptions.required, NavigationOptions.decode(setOf("UNKNOWN")))
    }

    @Test fun onlyVocabularyAndRemoteCanBeHidden() {
        val defaults = NavigationOptions.defaults
        assertEquals(defaults,NavigationOptions.update(defaults,NavigationItem.REVIEW,true))
        assertEquals(defaults,NavigationOptions.update(defaults,NavigationItem.SEND,true))
        assertEquals(defaults,NavigationOptions.update(defaults,NavigationItem.LIBRARY,false))
        assertEquals(defaults,NavigationOptions.update(defaults,NavigationItem.NOTES,false))
        assertEquals(defaults,NavigationOptions.update(defaults,NavigationItem.SETTINGS,false))
        val hidden = NavigationOptions.update(NavigationOptions.update(defaults,NavigationItem.VOCABULARY,false),NavigationItem.REMOTE,false)
        assertEquals(NavigationOptions.required,hidden)
        assertEquals(hidden,NavigationOptions.decode(hidden.map{it.name}.toSet()))
        assertEquals(defaults,NavigationOptions.decode(setOf("NOTES","VOCABULARY","REMOTE","REVIEW","SEND")))
    }

    @Test fun connectionScopeSurvivesCredentialEditsButSeparatesDevices() {
        val settings = ConnectionSettings(host = " Kindle.Local ")
        assertEquals(settings.cacheScope(), settings.copy(host = "kindle.local", password = "different", eventDevice = "event2").cacheScope())
        assertNotEquals(settings.cacheScope(), settings.copy(host = "other.local").cacheScope())
        assertNotEquals(settings.cacheScope(), settings.copy(port = 2222).cacheScope())
        assertNotEquals(settings.cacheScope(), settings.copy(username = "other").cacheScope())
    }
}
