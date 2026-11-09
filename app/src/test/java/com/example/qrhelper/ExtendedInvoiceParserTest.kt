package com.example.qrhelper

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedInvoiceParserTest {
    private fun assertRouteRow(rows: List<RouteItem>, client: String, addressPart: String) {
        assertTrue(rows.any { it.storeName == client && it.address.contains(addressPart) })
        assertTrue(rows.all { it.barcode.isBlank() })
    }

    @Test fun convertsHtmlRouteTable() {
        val html = """<table><tr><th>№</th><th>Код</th><th>Клиент</th><th>Адрес доставки</th><th>Комментарий</th></tr><tr><td>1</td><td>1708</td><td>Балтика ПК ООО</td><td>Калуга, ул. Ленина, д. 1</td><td>Доставить до 10:00</td></tr></table>"""
        assertRouteRow(ExtendedInvoiceParser.parse(InvoiceDocumentFormat.HTML, html.toByteArray()), "Балтика ПК ООО", "Ленина")
    }

    @Test fun convertsJsonRouteRows() {
        val json = """[{"№":"1","Код":"1646","Клиент":"Альфа Рязань ООО","Адрес доставки":"Калуга, ул. Кирова, д. 2","Комментарий":"Приём до 10:00"}]"""
        assertRouteRow(ExtendedInvoiceParser.parse(InvoiceDocumentFormat.JSON, json.toByteArray()), "Альфа Рязань ООО", "Кирова")
    }

    @Test fun convertsXmlRouteRows() {
        val xml = """<root><row><cell>№</cell><cell>Код</cell><cell>Клиент</cell><cell>Адрес доставки</cell><cell>Комментарий</cell></row><row><cell>1</cell><cell>1708</cell><cell>Балтика ПК ООО</cell><cell>Калуга, ул. Гагарина, д. 3</cell><cell>Звонить заранее</cell></row></root>"""
        assertRouteRow(ExtendedInvoiceParser.parse(InvoiceDocumentFormat.XML, xml.toByteArray()), "Балтика ПК ООО", "Гагарина")
    }

    @Test fun parsesLegacyXlsRouteRows() {
        val bytes = ByteArrayOutputStream().use { output ->
            HSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("Маршрут")
                val header = sheet.createRow(0)
                listOf("№", "Код", "Клиент", "Адрес доставки", "Комментарий").forEachIndexed { index, value ->
                    header.createCell(index).setCellValue(value)
                }
                val row = sheet.createRow(1)
                listOf("1", "1708", "Балтика ПК ООО", "Калуга, ул. Мира, д. 4", "Доставка до 12:00").forEachIndexed { index, value ->
                    row.createCell(index).setCellValue(value)
                }
                workbook.write(output)
            }
            output.toByteArray()
        }
        assertRouteRow(ExtendedInvoiceParser.parse(InvoiceDocumentFormat.XLS, bytes), "Балтика ПК ООО", "Мира")
    }

    @Test fun parsesRtfRouteRows() {
        val rtf = """{\rtf1\ansi №\tab Код\tab Клиент\tab Адрес доставки\tab Комментарий\par 1\tab 1646\tab Альфа Рязань ООО\tab Калуга, ул. Победы, д. 5\tab Забрать документы}"""
        assertRouteRow(ExtendedInvoiceParser.parse(InvoiceDocumentFormat.RTF, rtf.toByteArray()), "Альфа Рязань ООО", "Победы")
    }

    @Test fun parsesOdsRouteTable() {
        val xml = """<office:document-content xmlns:table="urn:table" xmlns:text="urn:text"><table:table><table:table-row><table:table-cell><text:p>№</text:p></table:table-cell><table:table-cell><text:p>Код</text:p></table:table-cell><table:table-cell><text:p>Клиент</text:p></table:table-cell><table:table-cell><text:p>Адрес доставки</text:p></table:table-cell><table:table-cell><text:p>Комментарий</text:p></table:table-cell></table:table-row><table:table-row><table:table-cell><text:p>1</text:p></table:table-cell><table:table-cell><text:p>1708</text:p></table:table-cell><table:table-cell><text:p>Балтика ПК ООО</text:p></table:table-cell><table:table-cell><text:p>Калуга, ул. Театральная, д. 7</text:p></table:table-cell><table:table-cell><text:p>Доставить до 10:00</text:p></table:table-cell></table:table-row></table:table></office:document-content>"""
        assertRouteRow(ExtendedInvoiceParser.parse(InvoiceDocumentFormat.ODS, zippedEntry("content.xml", xml)), "Балтика ПК ООО", "Театральная")
    }

    @Test fun parsesOdtRouteTable() {
        val xml = """<office:document-content xmlns:text="urn:text"><text:table><text:table-row><text:table-cell><text:p>№</text:p></text:table-cell><text:table-cell><text:p>Код</text:p></text:table-cell><text:table-cell><text:p>Клиент</text:p></text:table-cell><text:table-cell><text:p>Адрес доставки</text:p></text:table-cell><text:table-cell><text:p>Комментарий</text:p></text:table-cell></text:table-row><text:table-row><text:table-cell><text:p>1</text:p></text:table-cell><text:table-cell><text:p>1646</text:p></text:table-cell><text:table-cell><text:p>Альфа Рязань ООО</text:p></text:table-cell><text:table-cell><text:p>Калуга, ул. Суворова, д. 8</text:p></text:table-cell><text:table-cell><text:p>Въезд со двора</text:p></text:table-cell></text:table-row></text:table></office:document-content>"""
        assertRouteRow(ExtendedInvoiceParser.parse(InvoiceDocumentFormat.ODT, zippedEntry("content.xml", xml)), "Альфа Рязань ООО", "Суворова")
    }

    @Test fun parsesDocxRouteTable() {
        val xml = """<w:document xmlns:w="urn:word"><w:body><w:tbl><w:tr><w:tc><w:p><w:r><w:t>№</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Код</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Клиент</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Адрес доставки</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Комментарий</w:t></w:r></w:p></w:tc></w:tr><w:tr><w:tc><w:p><w:r><w:t>1</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>1708</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Балтика ПК ООО</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Калуга, ул. Кирова, д. 9</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Позвонить перед въездом</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>"""
        assertRouteRow(ExtendedInvoiceParser.parse(InvoiceDocumentFormat.DOCX, zippedEntry("word/document.xml", xml)), "Балтика ПК ООО", "Кирова")
    }

    @Test fun decodesRussianCp1251InRtfRouteRows() {
        val rtf = """{\rtf1\ansi\ansicpg1251 №\tab Код\tab Клиент\tab Адрес доставки\tab Комментарий\par 1\tab 1708\tab \'cc\'e0\'e3\'e0\'e7\'e8\'ed \'c1\'ee\'eb\'e5\'e5\'e2\tab Калуга, ул. Ленина, д. 1\tab Хлеб}"""
        assertRouteRow(ExtendedInvoiceParser.parse(InvoiceDocumentFormat.RTF, rtf.toByteArray()), "Магазин Болеев", "Ленина")
    }

    private fun zippedEntry(name: String, xml: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }
}
