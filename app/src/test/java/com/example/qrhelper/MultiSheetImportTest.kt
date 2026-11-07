package com.example.qrhelper

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiSheetImportTest {
    private fun sheet(store: String, barcode: String): String {
        val values = listOf(
            listOf("Штрихкод", "Магазин", "Адрес", "Товар", "Мест"),
            listOf(barcode, store, "Калуга, ул. Ленина, 1", "Товар $store", "2")
        )
        return buildString {
            append("<worksheet><sheetData>")
            values.forEachIndexed { rowIndex, row ->
                append("<row r=\"${rowIndex + 1}\">")
                row.forEachIndexed { column, value ->
                    val ref = "${('A'.code + column).toChar()}${rowIndex + 1}"
                    append("<c r=\"$ref\" t=\"inlineStr\"><is><t>")
                    append(value)
                    append("</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
    }

    @Test fun importsRowsFromEveryWorksheetInWorkbook() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            listOf(
                "xl/worksheets/sheet1.xml" to sheet("Магазин А", "4601234567890"),
                "xl/worksheets/sheet2.xml" to sheet("Магазин Б", "4601234567891")
            ).forEach { (name, xml) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        val items = parseXlsx(ByteArrayInputStream(output.toByteArray()))
        assertEquals(2, items.size)
        assertEquals(listOf("Магазин А", "Магазин Б"), items.map { it.storeName })
    }
}
