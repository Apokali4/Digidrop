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
    @Test fun inheritsMergedStoreAndAddressCellsForContinuationRows() {
        val rows = parseManifest(
            "Штрихкод\tМагазин\tАдрес\tТовар\tКоличество\n" +
                "4601234567890\tМагазин А\tКалуга, Ленина 1\tХлеб\t4\n" +
                "4601234567891\t\t\tМолоко\t2\n" +
                "4601234567892\tМагазин Б\tКалуга, Кирова 2\tВода\t3"
        )
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.address.isNotBlank() })
        assertEquals("Калуга, Ленина 1", rows[1].address)
        assertEquals("Магазин А", rows[1].storeName)
        assertEquals("Калуга, Кирова 2", rows[2].address)
    }

}
