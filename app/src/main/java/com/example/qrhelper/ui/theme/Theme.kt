package com.example.qrhelper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ColorError = androidx.compose.ui.graphics.Color(0xFFCF625B)

private val DigiDropLightScheme = lightColorScheme(
    primary = DigiDropPalette.accent,
    onPrimary = DigiDropPalette.onAccent,
    primaryContainer = DigiDropPalette.primarySoft,
    onPrimaryContainer = DigiDropPalette.lightText,
    secondary = DigiDropPalette.accentDark,
    onSecondary = DigiDropPalette.onAccent,
    secondaryContainer = DigiDropPalette.primarySoft,
    onSecondaryContainer = DigiDropPalette.lightText,
    tertiary = DigiDropPalette.accent,
    onTertiary = DigiDropPalette.onAccent,
    background = DigiDropPalette.lightBackground,
    onBackground = DigiDropPalette.lightText,
    surface = DigiDropPalette.lightSurface,
    onSurface = DigiDropPalette.lightText,
    surfaceVariant = DigiDropPalette.lightSurfaceSoft,
    onSurfaceVariant = DigiDropPalette.lightTextSecondary,
    outline = DigiDropPalette.lightBorder,
    outlineVariant = DigiDropPalette.lightDivider,
    error = ColorError,
    onError = DigiDropPalette.onAccent
)

private val DigiDropDarkScheme = darkColorScheme(
    primary = DigiDropPalette.accentLight,
    onPrimary = DigiDropPalette.darkBackground,
    primaryContainer = DigiDropPalette.accentDark,
    onPrimaryContainer = DigiDropPalette.onAccent,
    secondary = DigiDropPalette.accentLight,
    onSecondary = DigiDropPalette.darkBackground,
    secondaryContainer = DigiDropPalette.darkSurfaceSoft,
    onSecondaryContainer = DigiDropPalette.darkText,
    tertiary = DigiDropPalette.accentLight,
    onTertiary = DigiDropPalette.darkBackground,
    background = DigiDropPalette.darkBackground,
    onBackground = DigiDropPalette.darkText,
    surface = DigiDropPalette.darkSurface,
    onSurface = DigiDropPalette.darkText,
    surfaceVariant = DigiDropPalette.darkSurfaceSoft,
    onSurfaceVariant = DigiDropPalette.darkTextSecondary,
    outline = DigiDropPalette.darkBorder,
    outlineVariant = DigiDropPalette.darkDivider,
    error = ColorError,
    onError = DigiDropPalette.onAccent
)

/** Follows the device appearance setting; both themes share the same green brand tiles. */
@Composable
fun QRHelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DigiDropDarkScheme else DigiDropLightScheme,
        typography = Typography,
        content = content
    )
}
