package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSheetParserTest {
    @Test fun keepsStoreHoursSeparateFromDeliveryWindowInRouteSheet() {
        val text = listOf(
            "№\tКлиент\tАдрес\tВремя работы\tОкно доставки",
            "1\tМагазин Север\tКалуга, ул. Победы, д. 2\t08:00-22:00\t09:00-11:00"
        ).joinToString("\n")

        val item = RouteSheetParser.parse(text).single()

        assertEquals("08:00-22:00", item.workingHours)
        assertEquals("09:00-11:00", item.timeSlot)
    }

    @Test fun parsesDeliveryWindowFromCsvManifestWithoutMixingItIntoStoreHours() {
        val manifest = listOf(
            "barcode\tstore\taddress\titem\tboxes\tphone\tnote\tlat\tlon\towner\thours\tcash\textra\tОкно доставки",
            "4601234567890\tМагазин Север\tКалуга, ул. Победы, 2\tМолоко\t2\t\t\t\t\tИП Иванов\t08:00-22:00\t\t\t09:00-11:00"
        ).joinToString("\n")

        val item = parseManifest(manifest).single()

        assertEquals("08:00-22:00", item.workingHours)
        assertEquals("09:00-11:00", item.timeSlot)
    }

    @Test fun oldManifestWithoutDeliveryWindowColumnStillImports() {
        val manifest = listOf(
            "barcode\tstore\taddress\titem\tboxes\tphone\tnote\tlat\tlon\towner\thours\tcash\textra",
            "4601234567890\tМагазин Север\tКалуга, ул. Победы, 2\tМолоко\t2\t\t\t\t\tИП Иванов\t08:00-22:00\t\t"
        ).joinToString("\n")

        val item = parseManifest(manifest).single()

        assertEquals("08:00-22:00", item.workingHours)
        assertEquals("", item.timeSlot)
    }

    @Test fun parsesDispatchRouteRowsWithoutCargoBarcodeOrQuantity() {
        val text = """
            Задание на отгрузку №Л00000006924
            №\tКод\tКлиент\tАдрес доставки\tКомментарий
            1\t1708\tБалтика ПК ООО\tг. Калуга, ул. Врубовая, д. 2, Бристоль\t
            2\t1646\tАльфа Рязань ООО (Красное и Белое)\tг. Калуга, ул. В. Никитиной, д. 21 Б, корп. 2, Красное и Белое\t
            3\t1708\tБалтика ПК ООО\tг. Калуга, ул. В. Никитиной, д. 21 Б, корп. 2, Красное и Белое\tКомментарий
        """.trimIndent().replace("\\t", "\t")

        val result = RouteSheetParser.parse(text)

        assertEquals(3, result.size)
        assertEquals("Балтика ПК ООО", result[0].storeName)
        assertEquals("1708", result[0].itemName.substringAfter("код "))
        assertEquals("г. Калуга, ул. Врубовая, д. 2, Бристоль", result[0].address)
        assertEquals(1, result[0].totalBoxes)
        assertTrue(result.all { it.barcode.isBlank() })
    }

    @Test fun parsesRowsFromContinuationPageWithoutHeader() {
        val text = """
            24\t97\tАльбион-2002 ООО\tг. Калуга, ул. Тульская, д. 32, Бристоль\t
            25\t1631\tАльфа-М ООО (Красное и Белое)\tг. Калуга, ул. Болдина, д. 9, кв. 1, Красное и Белое\t
            26\t1708\tБалтика ПК ООО\tг. Калуга, ул. Болдина, д. 9, кв. 1, Красное и Белое\t
        """.trimIndent().replace("\\t", "\t")

        val result = RouteSheetParser.parse(text)

        assertEquals(3, result.size)
        assertEquals("Альбион-2002 ООО", result.first().storeName)
        assertEquals("97", result.first().itemName.substringAfter("код "))
    }

    @Test fun doesNotMergeDifferentClientsAtSameAddress() {
        val text = """
            №\tКод\tКлиент\tАдрес доставки\tКомментарий
            1\t1646\tАльфа Рязань ООО\tг. Калуга, ул. Болдина, д. 9\t
            2\t1708\tБалтика ПК ООО\tг. Калуга, ул. Болдина, д. 9\t
        """.trimIndent().replace("\\t", "\t")

        val result = RouteSheetParser.parse(text)

        assertEquals(2, result.size)
        assertEquals(result[0].address, result[1].address)
        assertTrue(result[0].storeName != result[1].storeName)
        assertTrue(result[0].storeName != result[1].storeName)
    }

    @Test fun parsesWhitespaceSeparatedOcrRouteRows() {
        val text = """
            №  Код  Клиент  Адрес доставки  Комментарий
            1  1708  Балтика ПК ООО  г. Калуга, ул. Врубовая, д. 2, Бристоль
            2  1646  Альфа Рязань ООО  г. Калуга, ул. Болдина, д. 9, Красное и Белое
        """.trimIndent()

        val result = RouteSheetParser.parse(text)

        assertEquals(2, result.size)
    }
    @Test fun parsesMultipleWorkbookSheetsAsOneRouteList() {
        val sheets = listOf(
            listOf(
                listOf("№", "Код", "Клиент", "Адрес доставки", "Комментарий"),
                listOf("1", "1708", "Балтика ПК ООО", "г. Калуга, ул. Врубовая, д. 2", "")
            ),
            listOf(
                listOf("№", "Код", "Клиент", "Адрес доставки", "Комментарий"),
                listOf("2", "1646", "Альфа Рязань ООО", "г. Калуга, ул. Болдина, д. 9", "")
            )
        )
        val result = RouteSheetParser.parseSheets(sheets)
        assertEquals(2, result.size)
        assertEquals("Балтика ПК ООО", result[0].storeName)
        assertEquals("Альфа Рязань ООО", result[1].storeName)
    }

    @Test fun ocrImportIgnoresUnnumberedHeaderAndFooterAddresses() {
        val text = """
            Задание на отгрузку №Л00000006924
            г. Калуга, ул. Ленина, д. 13
            1 1708 Балтика ПК ООО г. Калуга, ул. Врубовая, д. 2, Бристоль
            Итого: 1 точка
            ул. Победы, д. 7
        """.trimIndent()

        val result = RouteSheetParser.parseOcrText(text)

        assertEquals(1, result.size)
        assertEquals("г. Калуга, ул. Врубовая, д. 2", result.single().address)
        assertEquals("Бристоль", result.single().storeName)
    }

    @Test fun preservesRepeatedAddressRowsForDifferentCustomersInOcrText() {
        val text = """
            1 1708 Балтика ПК ООО г. Калуга, ул. Болдина, д. 9, Красное и Белое
            2 1646 Альфа Рязань ООО г. Калуга, ул. Болдина, д. 9, Красное и Белое
            3 1631 Альфа-М ООО г. Калуга, ул. Советская, д. 12
        """.trimIndent()
        val result = RouteSheetParser.parseOcrText(text)
        assertEquals(3, result.size)
        assertEquals(result[0].address, result[1].address)
    }


    @Test fun keepsContinuationRowsAttachedToPreviousDelivery() {
        val text = """
            №\tКод\tКлиент\tАдрес доставки\tКомментарий
            1\t1708\tБалтика ПК ООО\tг. Калуга, ул. Болдина, д. 9\t
            \t\tДополнительные условия\t\tДоставить до 10:00
        """.trimIndent().replace("\\t", "\t")

        val result = RouteSheetParser.parse(text)

        assertEquals(1, result.size)
        assertTrue(result.single().note.contains("Дополнительные условия"))
        assertTrue(result.single().note.contains("Доставить до 10:00"))
    }

}
