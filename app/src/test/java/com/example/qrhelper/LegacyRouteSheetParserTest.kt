package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LegacyRouteSheetParserTest {
    @Test fun parsesOldRouteSheetWithHeadersAndNoQuantityColumn() {
        val text = """
            Задание на отгрузку №Л00000006924
            №\tКод\tКлиент\tАдрес доставки\tКомментарий
            1\t1708\tБалтика ПК ООО\tг. Калуга, ул. Врубовая, д. 2, Бристоль\t
            2\t1646\tАльфа Рязань ООО (Красное и Белое)\tг. Калуга, ул. В. Никитиной, д. 21 Б, корп. 2, Красное и Белое\t
        """.trimIndent()
        val rows = LegacyRouteSheetParser.parse(text)
        assertNotNull(rows)
        assertEquals(2, rows!!.size)
        assertEquals("1708", rows[0].barcode)
        assertEquals("Балтика ПК ООО", rows[0].storeName)
        assertEquals("г. Калуга, ул. Врубовая, д. 2, Бристоль", rows[0].address)
        assertEquals(1, rows[0].totalBoxes)
    }

    @Test fun parsesContinuationPhotoWithoutRepeatedHeader() {
        val text = """
            24\t97\tАльбион-2002 ООО\tг. Калуга, ул. Тульская, д. 32, Бристоль\t
            25\t1631\tАльфа-М ООО (Красное и Белое)\tг. Калуга, ул. Болдина, д. 9, кв. 1, Красное и Белое\t
        """.trimIndent()
        val rows = LegacyRouteSheetParser.parse(text)
        assertNotNull(rows)
        assertEquals(2, rows!!.size)
        assertEquals("97", rows[0].barcode)
        assertEquals("Альбион-2002 ООО", rows[0].storeName)
        assertEquals(1, rows[1].totalBoxes)
    }

    @Test fun ignoresPageHeadersAndRowsWithoutDeliveryAddress() {
        val text = """
            Задание на отгрузку №Л00000006924
            №\tКод\tКлиент\tАдрес доставки\tКомментарий
            1\t1708\tБалтика ПК ООО\tг. Калуга, ул. Врубовая, д. 2, Бристоль\t
            Итого документов: 36
        """.trimIndent()
        assertEquals(1, LegacyRouteSheetParser.parse(text)!!.size)
    }
}
