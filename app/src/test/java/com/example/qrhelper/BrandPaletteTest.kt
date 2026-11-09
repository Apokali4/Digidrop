package com.example.qrhelper

import androidx.compose.ui.graphics.Color
import com.example.qrhelper.ui.theme.DigiDropPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the approved graphite-and-safety-orange brand colors against regressions. */
class BrandPaletteTest {
    @Test
    fun appPaletteUsesGraphiteSurfacesAndSafetyOrangeActions() {
        assertEquals(Color(0xFFECEAE5), DigiDropPalette.background)
        assertEquals(Color(0xFFF8F6F1), DigiDropPalette.surface)
        assertEquals(Color(0xFFF07824), DigiDropPalette.primary)
        assertEquals(Color(0xFF242629), DigiDropPalette.text)
        assertEquals(Color(0xFF1D2023), DigiDropPalette.darkBackground)
        assertEquals(Color(0xFF292D31), DigiDropPalette.darkSurface)
    }
}
