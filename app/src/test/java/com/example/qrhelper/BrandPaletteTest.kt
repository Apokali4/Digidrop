package com.example.qrhelper

import androidx.compose.ui.graphics.Color
import com.example.qrhelper.ui.theme.DigiDropPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandPaletteTest {
    @Test
    fun appPaletteUsesLightSurfacesAndGreenActions() {
        assertEquals(Color(0xFFF3F2EA), DigiDropPalette.background)
        assertEquals(Color.White, DigiDropPalette.surface)
        assertEquals(Color(0xFF278747), DigiDropPalette.primary)
        assertEquals(Color(0xFF26352C), DigiDropPalette.text)
    }
}
