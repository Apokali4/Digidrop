package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceRowValidationTest {
    @Test fun rejectsHeaderLabelsAndTinyOcrFragmentsAsBarcodes() {
        assertFalse(isUsableInvoiceBarcode("Штрихкод"))
        assertFalse(isUsableInvoiceBarcode("Код"))
        assertFalse(isUsableInvoiceBarcode("12"))
        assertTrue(isUsableInvoiceBarcode("4601234567890"))
        assertTrue(isUsableInvoiceBarcode("1708"))
    }

    @Test fun parsesOnlyPositiveWholeBoxQuantities() {
        assertEquals(12, parseInvoiceQuantity("12"))
        assertEquals(12, parseInvoiceQuantity(" 1 2 "))
        assertEquals(1, parseInvoiceQuantity(""))
        assertNull(parseInvoiceQuantity("0"))
        assertNull(parseInvoiceQuantity("-2"))
        assertNull(parseInvoiceQuantity("1.5"))
        assertNull(parseInvoiceQuantity("много"))
        assertNull(parseInvoiceQuantity("100001"))
    }

    @Test fun acceptsCoordinatesOnlyAsAValidPair() {
        assertEquals(54.51 to 36.26, parseCoordinatePair("54,51", "36,26"))
        assertNull(parseCoordinatePair("54.51", ""))
        assertNull(parseCoordinatePair("91", "36"))
        assertNull(parseCoordinatePair("54", "181"))
        assertNull(parseCoordinatePair("NaN", "36"))
    }
}
