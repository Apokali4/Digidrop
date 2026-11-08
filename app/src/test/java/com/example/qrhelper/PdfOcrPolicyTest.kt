package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfOcrPolicyTest {
    @Test fun runsOcrOnlyWhenPdfHasNoExtractableText() {
        assertTrue(shouldRunPdfOcr(""))
        assertTrue(shouldRunPdfOcr("  \n "))
        assertFalse(shouldRunPdfOcr("Штрихкод\tМагазин\tАдрес"))
    }

    @Test fun prefersEmbeddedPdfTextAndUsesOcrAsFallback() {
        assertEquals("Текст PDF", selectPdfInvoiceText(" Текст PDF ", "OCR-текст"))
        assertEquals("OCR-текст", selectPdfInvoiceText("  ", " OCR-текст "))
    }

    @Test fun preservesColumnBoundariesWhenOcrUsesRepeatedSpaces() {
        assertEquals(
            "Штрихкод\tМагазин\tАдрес\n4601234567890\tМагазин А\tКалуга, Ленина 1",
            normalizeOcrTableText("Штрихкод   Магазин  Адрес\n4601234567890  Магазин А   Калуга, Ленина 1")
        )
    }
}
