package com.example.qrhelper

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedInvoiceParserTest {
    @Test fun convertsHtmlTablesToManifestRows() {
        val rows = ExtendedInvoiceParser.parse(
            InvoiceDocumentFormat.HTML,
            """<table><tr><th>Штрихкод</th><th>Магазин</th><th>Адрес</th><th>Товар</th><th>Количество</th></tr><tr><td>4601234567890</td><td>Магазин А</td><td>Калуга, Ленина 1</td><td>Молоко</td><td>4</td></tr></table>""".toByteArray()
        )
        assertTrue(rows.any { it.storeName == "Магазин А" && it.totalBoxes == 4 })
    }

    @Test fun convertsJsonArrayOfObjectsToManifestRows() {
        val rows = ExtendedInvoiceParser.parse(
            InvoiceDocumentFormat.JSON,
            """[{"Штрихкод":"4601234567890","Магазин":"Магазин Б","Адрес":"Калуга, Кирова 2","Товар":"Хлеб","Количество":3}]""".toByteArray()
        )
        assertTrue(rows.any { it.storeName == "Магазин Б" && it.totalBoxes == 3 })
    }

    @Test fun convertsXmlTableRowsToManifestRows() {
        val rows = ExtendedInvoiceParser.parse(
            InvoiceDocumentFormat.XML,
            """<root><row><cell>Штрихкод</cell><cell>Магазин</cell><cell>Адрес</cell><cell>Товар</cell><cell>Количество</cell></row><row><cell>4601234567890</cell><cell>Магазин В</cell><cell>Калуга, Гагарина 3</cell><cell>Сок</cell><cell>2</cell></row></root>""".toByteArray()
        )
        assertTrue(rows.any { it.storeName == "Магазин В" && it.totalBoxes == 2 })
    }
    @Test fun parsesLegacyXlsWorkbookRows() {
        val bytes = ByteArrayOutputStream().use { output ->
            HSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("Накладная")
                val header = sheet.createRow(0)
                listOf("Штрихкод", "Магазин", "Адрес", "Товар", "Количество").forEachIndexed { index, value ->
                    header.createCell(index).setCellValue(value)
                }
                val row = sheet.createRow(1)
                listOf("4601234567890", "Магазин Г", "Калуга, Мира 4", "Вода", "5").forEachIndexed { index, value ->
                    row.createCell(index).setCellValue(value)
                }
                workbook.write(output)
            }
            output.toByteArray()
        }
        val rows = ExtendedInvoiceParser.parse(InvoiceDocumentFormat.XLS, bytes)
        assertTrue(rows.any { it.storeName == "Магазин Г" && it.totalBoxes == 5 })
    }

    @Test fun parsesRtfTabularInvoiceText() {
        val rtf = """{\\rtf1\\ansi Штрихкод\\tab Магазин\\tab Адрес\\tab Товар\\tab Количество\\par 4601234567890\\tab Магазин Д\\tab Калуга, Победы 5\\tab Чай\\tab 6}"""
        val rows = ExtendedInvoiceParser.parse(InvoiceDocumentFormat.RTF, rtf.toByteArray())
        assertTrue(rows.any { it.storeName == "Магазин Д" && it.totalBoxes == 6 })
    }

    @Test fun parsesOdsSpreadsheetTable() {
        val xml = """<office:document-content xmlns:table="urn:table" xmlns:text="urn:text"><table:table><table:table-row><table:table-cell><text:p>Штрихкод</text:p></table:table-cell><table:table-cell><text:p>Магазин</text:p></table:table-cell><table:table-cell><text:p>Адрес</text:p></table:table-cell><table:table-cell><text:p>Товар</text:p></table:table-cell><table:table-cell><text:p>Количество</text:p></table:table-cell></table:table-row><table:table-row><table:table-cell><text:p>4601234567890</text:p></table:table-cell><table:table-cell><text:p>Магазин Ж</text:p></table:table-cell><table:table-cell><text:p>Калуга, Театральная 7</text:p></table:table-cell><table:table-cell><text:p>Сахар</text:p></table:table-cell><table:table-cell><text:p>7</text:p></table:table-cell></table:table-row></table:table></office:document-content>"""
        val bytes = zippedContent(xml)
        val rows = ExtendedInvoiceParser.parse(InvoiceDocumentFormat.ODS, bytes)
        assertTrue(rows.any { it.storeName == "Магазин Ж" && it.totalBoxes == 7 })
    }

    @Test fun parsesOdtTableRows() {
        val xml = """<office:document-content xmlns:text="urn:text"><text:table><text:table-row><text:table-cell><text:p>Штрихкод</text:p></text:table-cell><text:table-cell><text:p>Магазин</text:p></text:table-cell><text:table-cell><text:p>Адрес</text:p></text:table-cell><text:table-cell><text:p>Товар</text:p></text:table-cell><text:table-cell><text:p>Количество</text:p></text:table:table-cell></text:table-row><text:table-row><text:table-cell><text:p>4601234567890</text:p></text:table-cell><text:table-cell><text:p>Магазин З</text:p></text:table-cell><text:table-cell><text:p>Калуга, Суворова 8</text:p></text:table-cell><text:table-cell><text:p>Крупа</text:p></text:table-cell><text:table-cell><text:p>8</text:p></text:table-cell></text:table-row></text:table></office:document-content>"""
        val bytes = zippedContent(xml)
        val rows = ExtendedInvoiceParser.parse(InvoiceDocumentFormat.ODT, bytes)
        assertTrue(rows.any { it.storeName == "Магазин З" && it.totalBoxes == 8 })
    }

    private fun zippedContent(xml: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("content.xml"))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }

}
