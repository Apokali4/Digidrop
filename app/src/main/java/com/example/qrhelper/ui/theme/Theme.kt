package com.example.qrhelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DigiDropColors = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF07110D),
    secondary = Color(0xFF34D399),
    background = Color(0xFF0D0F12),
    surface = Color(0xFF171A1F),
    onBackground = Color(0xFFE8ECEF),
    onSurface = Color(0xFFE8ECEF)
)

@Composable
fun QRHelperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DigiDropColors, content = content)
}
