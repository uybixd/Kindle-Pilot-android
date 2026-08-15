package com.hongxiang.kpilot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SmartisanTealDark,
    secondary = Color(0xFFC9C6C2),
    tertiary = Color(0xFFE4BE62),
    background = DarkBackground,
    surface = DarkBackground
)

private val LightColorScheme = lightColorScheme(
    primary = SmartisanTeal,
    onPrimary = Color.White,
    primaryContainer = SmartisanSelected,
    onPrimaryContainer = SmartisanGraphite,
    secondary = SmartisanGraphite,
    onSecondary = Color.White,
    secondaryContainer = SmartisanNoteBadge,
    onSecondaryContainer = SmartisanGraphite,
    tertiary = SmartisanGold,
    background = SmartisanIvory,
    surface = SmartisanIvory,
    surfaceVariant = SmartisanPearl,
    onSurface = SmartisanGraphite,
    onSurfaceVariant = SmartisanMuted,
    outline = SmartisanMuted,
    outlineVariant = SmartisanDivider,
    surfaceContainerLowest = SmartisanIvory,
    surfaceContainerLow = SmartisanPearlLow,
    surfaceContainer = SmartisanPearl,
    surfaceContainerHigh = SmartisanSelected
)

@Composable
fun KPilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
