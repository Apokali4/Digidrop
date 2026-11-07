package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class InvoiceMergeTest {
    @Test
    fun mergesMatchingProductsAtTheSameStoreAndKeepsDifferentStoresSeparate() {
        val existing = listOf(
            RouteItem("4601", "Магазин А", "Адрес А", "Молоко", 2, "", scannedBoxes = 1),
            RouteItem("4601", "Магазин Б", "Адрес Б", "Молоко", 4, "")
        )
        val incoming = listOf(
            RouteItem("4601", "Магазин А", "Адрес А", "Молоко", 3, ""),
            RouteItem("4602", "Магазин А", "Адрес А", "Хлеб", 5, "")
        )

        val merged = mergeInvoiceRows(existing, incoming)

        assertEquals(3, merged.size)
        val milk = merged.single { it.storeName == "Магазин А" && it.barcode == "4601" }
        assertEquals(5, milk.totalBoxes)
        assertEquals(1, milk.scannedBoxes)
        assertEquals(4, merged.single { it.storeName == "Магазин Б" }.totalBoxes)
    }
}
