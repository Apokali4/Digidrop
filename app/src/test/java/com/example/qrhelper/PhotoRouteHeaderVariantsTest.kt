package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoRouteHeaderVariantsTest {
    @Test fun recognizesCommonHeaderWordingVariantsFromPrintedRouteSheets() {
        val text = listOf(
            "№ точки\tКод торговой точки\tНаименование торговой точки\tАдрес магазина",
            "12\t1708\tМагазин Север\tКалуга, ул. Кирова, д. 8"
        ).joinToString("\n")

        val row = PhotoRouteTableParser.parse(text, "Калуга").single()

        assertEquals("12", row.pointNumber)
        assertEquals("1708", row.customerCode)
        assertEquals("Магазин Север", row.storeName)
        assertEquals("Калуга, ул. Кирова, д. 8", row.address)
    }

    @Test fun recognizesAlternativeRecipientAndDeliveryAddressHeaders() {
        val text = listOf(
            "П/П\tПолучатель груза\tФактический адрес",
            "3\tООО Ромашка\tКалуга, ул. Ленина, д. 14"
        ).joinToString("\n")

        val row = PhotoRouteTableParser.parse(text, "Калуга").single()

        assertEquals("3", row.pointNumber)
        assertEquals("ООО Ромашка", row.organization)
        assertEquals("Калуга, ул. Ленина, д. 14", row.address)
    }
}
