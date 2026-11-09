package com.example.qrhelper.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single DigiDrop design palette.
 * Graphite industrial surfaces with restrained safety-orange accents.
 */
object DigiDropPalette {
    val accent = Color(0xFFF07824)
    val accentDark = Color(0xFFC85B16)
    val accentLight = Color(0xFFFF984A)
    val onAccent = Color(0xFF17191B)

    // Warm neutral light appearance.
    val lightBackground = Color(0xFFF0EFEC)
    val lightSurface = Color(0xFFFAF9F6)
    val lightSurfaceSoft = Color(0xFFE7E5E0)
    val lightBorder = Color(0xFFC9C6BF)
    val lightText = Color(0xFF242629)
    val lightTextSecondary = Color(0xFF65676A)
    val lightDivider = Color(0xFFD9D6D0)

    // Graphite dark appearance, never pure black.
    val darkBackground = Color(0xFF1D2023)
    val darkSurface = Color(0xFF292D31)
    val darkSurfaceSoft = Color(0xFF353A3F)
    val darkBorder = Color(0xFF4B5157)
    val darkText = Color(0xFFF2F1EE)
    val darkTextSecondary = Color(0xFFB7B8B8)
    val darkDivider = Color(0xFF41464B)

    // Compatibility aliases for older screens; new UI should use MaterialTheme.colorScheme.
    val background get() = lightBackground
    val surface get() = lightSurface
    val surfaceSoft get() = lightSurfaceSoft
    val border get() = lightBorder
    val primary get() = accent
    val primaryDark get() = accentDark
    val primarySoft = Color(0xFFFFE5D2)
    val text get() = lightText
    val textSecondary get() = lightTextSecondary
    val divider get() = lightDivider
}
