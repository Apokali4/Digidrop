package com.example.qrhelper.ui.theme

import androidx.compose.ui.graphics.Color

/** Shared DigiDrop brand accent with separate calm light and graphite dark surfaces. */
object DigiDropPalette {
    val accent = Color(0xFF278747)
    val accentDark = Color(0xFF1F6F39)
    val accentLight = Color(0xFF3B9B59)
    val onAccent = Color(0xFFFFFFFF)

    // Light theme: warm, low-glare neutral surfaces.
    val lightBackground = Color(0xFFF3F5F1)
    val lightSurface = Color(0xFFFFFFFF)
    val lightSurfaceSoft = Color(0xFFE9EEE8)
    val lightBorder = Color(0xFFD5DED4)
    val lightText = Color(0xFF18221B)
    val lightTextSecondary = Color(0xFF5F6B62)
    val lightDivider = Color(0xFFE0E6DF)

    // Dark theme: graphite, not pure black.
    val darkBackground = Color(0xFF202522)
    val darkSurface = Color(0xFF2A312C)
    val darkSurfaceSoft = Color(0xFF343D36)
    val darkBorder = Color(0xFF465149)
    val darkText = Color(0xFFF1F5F1)
    val darkTextSecondary = Color(0xFFB4C0B7)
    val darkDivider = Color(0xFF3D4840)

    // Backwards-compatible aliases for screens being migrated in later steps.
    val background get() = lightBackground
    val surface get() = lightSurface
    val surfaceSoft get() = lightSurfaceSoft
    val border get() = lightBorder
    val primary get() = accent
    val primaryDark get() = accentDark
    val primarySoft = Color(0xFFE5F1E8)
    val text get() = lightText
    val textSecondary get() = lightTextSecondary
    val divider get() = lightDivider
}
