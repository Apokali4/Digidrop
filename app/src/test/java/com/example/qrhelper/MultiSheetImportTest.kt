package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiSheetImportTest {
    @Test fun importsRouteRowsFromEveryWorksheetInWorkbook() {
        val headers = listOf("№", "Код", "Клиент", "Адрес доставки", "Комментарий")
        val sheetOne = listOf(
            headers,
            listOf("1", "1708", "Балтика ПК ООО", "Калуга, ул. Ленина, д. 1", "До 10:00")
        )
        val sheetTwo = listOf(
            headers,
            listOf("2", "1646", "Альфа Рязань ООО", "Калуга, ул. Кирова, д. 2", "Позвонить")
        )
        val items = parseXlsxSheets(listOf(sheetOne, sheetTwo))
        assertEquals(2, items.size)
        assertEquals(listOf("Балтика ПК ООО", "Альфа Рязань ООО"), items.map { it.storeName })
        assertEquals(listOf("Калуга, ул. Ленина, д. 1", "Калуга, ул. Кирова, д. 2"), items.map { it.address })
        assertEquals(listOf("", ""), items.map { it.barcode })
    }
}
