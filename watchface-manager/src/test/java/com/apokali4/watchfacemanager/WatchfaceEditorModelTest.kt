package com.apokali4.watchfacemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchfaceEditorModelTest {
    @Test
    fun defaultDesignContainsEditablePipBoyElements() {
        val design = WatchfaceDesign.default()

        assertTrue(design.elements.containsKey(FaceElementId.TIME))
        assertTrue(design.elements.containsKey(FaceElementId.WATCH_BATTERY))
        assertTrue(design.elements.containsKey(FaceElementId.PHONE_BATTERY))
        assertTrue(design.elements.containsKey(FaceElementId.WEATHER))
        assertTrue(design.elements.containsKey(FaceElementId.VAULT_BOY))
    }

    @Test
    fun elementCanBeMovedResizedHiddenAndRecolored() {
        val original = WatchfaceDesign.default()
        val changed = original.updateElement(
            FaceElementId.TIME,
            ElementStyle(x = 0.42f, y = 0.31f, scale = 1.4f, visible = false, colorArgb = 0xFFFFB000.toInt())
        )

        assertEquals(0.42f, changed.elements.getValue(FaceElementId.TIME).x)
        assertEquals(1.4f, changed.elements.getValue(FaceElementId.TIME).scale)
        assertFalse(changed.elements.getValue(FaceElementId.TIME).visible)
        assertEquals(0xFFFFB000.toInt(), changed.elements.getValue(FaceElementId.TIME).colorArgb)
        assertTrue(original.elements.getValue(FaceElementId.TIME).visible)
    }

    @Test
    fun designRejectsOutOfBoundsPositionAndScale() {
        val original = WatchfaceDesign.default()

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            original.updateElement(FaceElementId.TIME, ElementStyle(x = 1.2f))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            original.updateElement(FaceElementId.TIME, ElementStyle(scale = 3.0f))
        }
    }

    @Test
    fun designSupportsFourPipBoyThemes() {
        assertEquals(
            setOf(PipBoyTheme.GREEN, PipBoyTheme.AMBER, PipBoyTheme.BLUE, PipBoyTheme.WHITE),
            PipBoyTheme.entries.toSet()
        )
    }
}