package com.example.qrhelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DigiDropColorScheme = lightColorScheme(
    primary = DigiDropPalette.primary,
    onPrimary = DigiDropPalette.surface,
    primaryContainer = DigiDropPalette.primarySoft,
    onPrimaryContainer = DigiDropPalette.text,
    secondary = DigiDropPalette.primaryDark,
    onSecondary = DigiDropPalette.surface,
    secondaryContainer = DigiDropPalette.primarySoft,
    onSecondaryContainer = DigiDropPalette.text,
    tertiary = DigiDropPalette.primary,
    onTertiary = DigiDropPalette.surface,
    background = DigiDropPalette.background,
    onBackground = DigiDropPalette.text,
    surface = DigiDropPalette.surface,
    onSurface = DigiDropPalette.text,
    surfaceVariant = DigiDropPalette.surfaceSoft,
    onSurfaceVariant = DigiDropPalette.textSecondary,
    outline = DigiDropPalette.border,
    outlineVariant = DigiDropPalette.divider,
    error = DigiDropPalette.primaryDark,
    onError = DigiDropPalette.surface,
    errorContainer = DigiDropPalette.primarySoft,
    onErrorContainer = DigiDropPalette.text,
    inverseSurface = DigiDropPalette.text,
    inverseOnSurface = DigiDropPalette.surface,
    inversePrimary = DigiDropPalette.primarySoft
)

/** Force the approved light green brand theme, regardless of Android system theme. */
@Composable
fun QRHelperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DigiDropColorScheme, typography = Typography, content = content)
}
