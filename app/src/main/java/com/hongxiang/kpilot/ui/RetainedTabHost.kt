package com.hongxiang.kpilot.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Retained screens use this to disable back handlers, dialogs and focus effects while hidden. */
internal val LocalTabActive = compositionLocalOf { true }

/** Keeps visited tab compositions alive, while only the selected tab participates in layout. */
@Composable
internal fun <T : Any> RetainedTabHost(
    selected: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    // This composition-owned set is deliberately not snapshot state: selected already
    // invalidates the host, so a new tab can be composed in this same frame.
    val visited = remember { LinkedHashSet<T>() }
    visited.add(selected)
    val tabs = remember(visited.size) { visited.toList() }
    val hostActive = LocalTabActive.current

    Layout(
        modifier = modifier,
        content = {
            tabs.forEach { tab ->
                key(tab) {
                    val active = hostActive && tab == selected
                    CompositionLocalProvider(LocalTabActive provides active) {
                        Box(
                            Modifier.fillMaxSize().then(
                                if (active) Modifier else Modifier.clearAndSetSemantics { }
                            )
                        ) {
                            content(tab)
                        }
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val placeable = measurables[tabs.indexOf(selected)].measure(constraints)
        layout(placeable.width, placeable.height) {
            // Unplaced children stay composed, but do not draw or receive pointer input.
            placeable.placeRelative(0, 0)
        }
    }
}
