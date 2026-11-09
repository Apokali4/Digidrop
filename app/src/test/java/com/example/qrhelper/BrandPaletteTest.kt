package com.example.qrhelper

import androidx.compose.ui.graphics.Color
import com.example.qrhelper.ui.theme.DigiDropPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandPaletteTest {
    @Test
    fun appPaletteUsesGraphiteSurfacesAndSafetyOrangeActions() {
        assertEquals(Color(0xFFF0EFEC), DigiDropPalette.background)
        assertEquals(Color(0xFFFAF9F6), DigiDropPalette.surface)
        assertEquals(Color(0xFFF07824), DigiDropPalette.primary)
        assertEquals(Color(0xFF242629), DigiDropPalette.text)
        assertEquals(Color(0xFF1D2023), DigiDropPalette.darkBackground)
        assertEquals(Color(0xFF292D31), DigiDropPalette.darkSurface)
    }
}
