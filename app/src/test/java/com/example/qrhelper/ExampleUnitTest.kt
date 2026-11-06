package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun parsesRussianManifestHeadersAndQuotedFields() {
        val input = "Штрихкод;Магазин;Адрес;Товар;Мест;Телефон\n" +
            "4601234567890;\"Северный; склад\";\"ул. Гагарина, 8\";Вода 1,5 л;12;+79990000000"
        val result = parseManifest(input)
        assertEquals(1, result.size)
        assertEquals("Северный; склад", result[0].storeName)
        assertEquals("ул. Гагарина, 8", result[0].address)
        assertEquals(12, result[0].totalBoxes)
    }

    @Test
    fun parsesHeaderlessTabSeparatedRows() {
        val input = "4601234567890\tСеверный\tул. Гагарина, 8\tВода\t12\t+79990000000\tРазгрузка"
        val result = parseManifest(input)
        assertEquals(1, result.size)
        assertEquals("Северный", result[0].storeName)
        assertEquals(12, result[0].totalBoxes)
        assertEquals("+79990000000", result[0].phone)
    }

    @Test
    fun skipsRowsWithoutBarcodeOrDestination() {
        val input = "Штрихкод;Магазин;Адрес;Товар;Мест\n" +
            ";Северный;ул. Гагарина;Вода;2\n" +
            "4601234567890;;;Вода;2"
        assertTrue(parseManifest(input).isEmpty())
    }
}
