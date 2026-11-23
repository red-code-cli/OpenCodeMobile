package com.opencode.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

@Immutable
data class OcColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceSel: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val badge: Color,
    val danger: Color,
    val divider: Color,
    val success: Color,
)

val DarkOc = OcColors(
    true, Color(0xFF161616), Color(0xFF232323), Color(0xFF2D2D2D), Color(0xFF3B3B3B),
    Color.White, Color(0xFF9B9B9B), Color(0xFF3B50FF), Color(0xFF0A84FF),
    Color(0xFFFF3B30), Color(0xFF3A3A3A), Color(0xFF30D158)
)
val LightOc = OcColors(
    false, Color(0xFFF5F5F5), Color(0xFFFFFFFF), Color(0xFFEAEAEA), Color(0xFFDADADA),
    Color(0xFF111111), Color(0xFF6B6B6B), Color(0xFF3B50FF), Color(0xFF0A84FF),
    Color(0xFFE5342B), Color(0xFFD6D6D6), Color(0xFF1FA84A)
)

val LocalOc = staticCompositionLocalOf { DarkOc }

object Oc {
    val c: OcColors @Composable @ReadOnlyComposable get() = LocalOc.current
}

enum class ThemeMode { System, Light, Dark }

@Composable
fun OpenCodeTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val oc = if (dark) DarkOc else LightOc
    val scheme = if (dark) darkColorScheme(
        background = oc.bg, surface = oc.bg, onSurface = oc.text,
        onBackground = oc.text, primary = oc.accent
    ) else lightColorScheme(
        background = oc.bg, surface = oc.bg, onSurface = oc.text,
        onBackground = oc.text, primary = oc.accent
    )
    CompositionLocalProvider(LocalOc provides oc) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
