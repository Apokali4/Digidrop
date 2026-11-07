package com.example.qrhelper

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExporterTest {
    @Test fun xlsxContainsInvoiceRowsAndEscapesXmlCharacters() {
        val output = ByteArrayOutputStream()
        ReportExporter.writeXlsx(
            output,
            listOf(
                RouteItem(
                    barcode = "4601234567890",
                    storeName = "Магазин & Сын",
                    address = "Калуга <центр>",
                    itemName = "Молоко",
                    totalBoxes = 5,
                    phone = "",
                    scannedBoxes = 3,
                    discrepancyQty = 1,
                    discrepancyReason = "НЕДОВОЗ"
                )
            )
        )
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("Магазин &amp; Сын"))
        assertTrue(sheet.contains("Калуга &lt;центр&gt;"))
        assertTrue(sheet.contains("НЕДОВОЗ"))
        assertTrue(sheet.contains("<v>5</v>"))
        assertTrue(sheet.contains("<v>3</v>"))
    }
}
