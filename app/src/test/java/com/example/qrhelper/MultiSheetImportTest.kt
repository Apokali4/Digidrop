package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiSheetImportTest {
    @Test fun importsRowsFromEveryWorksheetInWorkbook() {
        val headers = listOf("Штрихкод", "Магазин", "Адрес", "Товар", "Мест")
        val sheetOne = listOf(
            headers,
            listOf("4601234567890", "Магазин А", "Калуга, ул. Ленина, 1", "Товар А", "2")
        )
        val sheetTwo = listOf(
            headers,
            listOf("4601234567891", "Магазин Б", "Калуга, ул. Кирова, 2", "Товар Б", "3")
        )
        val items = parseXlsxSheets(listOf(sheetOne, sheetTwo))
        assertEquals(2, items.size)
        assertEquals(listOf("Магазин А", "Магазин Б"), items.map { it.storeName })
        assertEquals(listOf(2, 3), items.map { it.totalBoxes })
    }
}
