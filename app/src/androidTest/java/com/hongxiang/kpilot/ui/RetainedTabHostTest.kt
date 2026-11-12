package com.hongxiang.kpilot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RetainedTabHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun revisitingATabKeepsItsRememberedCompositionAndActiveFlag() {
        val selected = mutableStateOf("notes")
        val creations = mutableMapOf<String, Int>()
        val instances = mutableMapOf<String, Any>()
        val active = mutableMapOf<String, Boolean>()
        compose.setContent {
            RetainedTabHost(selected.value, Modifier.size(160.dp)) { tab ->
                val instance = remember { Any() }
                val isActive = LocalTabActive.current
                SideEffect {
                    if (instances[tab] !== instance) {
                        instances[tab] = instance
                        creations[tab] = creations.getOrDefault(tab, 0) + 1
                    }
                    active[tab] = isActive
                }
                Text(tab, Modifier.testTag(tab))
            }
        }

        compose.onNodeWithTag("notes").assertIsDisplayed()
        compose.runOnIdle { selected.value = "library" }
        compose.onNodeWithTag("library").assertIsDisplayed()
        compose.onNodeWithTag("notes").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(active.getValue("notes"))
            assertTrue(active.getValue("library"))
            selected.value = "notes"
        }
        compose.onNodeWithTag("notes").assertIsDisplayed()
        compose.onNodeWithTag("library").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(mapOf("notes" to 1, "library" to 1), creations)
            assertTrue(active.getValue("notes"))
            assertFalse(active.getValue("library"))
        }
    }

    @Test fun hiddenTabDoesNotInterceptTouches() {
        val selected = mutableStateOf("notes")
        val clicks = mutableMapOf("notes" to 0, "library" to 0)
        compose.setContent {
            RetainedTabHost(selected.value, Modifier.size(160.dp).testTag("host")) { tab ->
                Box(Modifier.fillMaxSize().testTag(tab).clickable { clicks[tab] = clicks.getValue(tab) + 1 }) {
                    Text(tab)
                }
            }
        }

        compose.onNodeWithTag("host").performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(1, clicks.getValue("notes"))
            selected.value = "library"
        }
        compose.onNodeWithTag("library").assertIsDisplayed()
        compose.onNodeWithTag("host").performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(1, clicks.getValue("notes"))
            assertEquals(1, clicks.getValue("library"))
            selected.value = "notes"
        }
        compose.onNodeWithTag("host").performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(2, clicks.getValue("notes"))
            assertEquals(1, clicks.getValue("library"))
        }
    }
}
