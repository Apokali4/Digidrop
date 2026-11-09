package com.example.qrhelper

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object ReportExporter {
    private val headers = listOf(
        "Штрихкод", "Точка", "Адрес", "Товар", "План мест", "Отсканировано",
        "Расхождение", "Причина", "Остаток", "Статус"
    )

    private fun rows(items: List<RouteItem>): List<List<String>> = items.map { item ->
        listOf(
            item.barcode,
            item.storeName,
            item.address,
            item.itemName,
            item.totalBoxes.toString(),
            item.scannedBoxes.toString(),
            item.discrepancyQty.toString(),
            item.discrepancyReason.orEmpty(),
            remainingBoxes(item).toString(),
            deliveryItemStatusLabel(item)
        )
    }

    fun writeXlsx(output: OutputStream, items: List<RouteItem>) {
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
            entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Отчёт DigiDrop" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""")
            val allRows = listOf(headers) + rows(items)
            val sheet = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                allRows.forEachIndexed { rowIndex, row ->
                    append("<row r=\"").append(rowIndex + 1).append("\">")
                    row.forEachIndexed { columnIndex, value ->
                        val cellRef = columnName(columnIndex + 1) + (rowIndex + 1)
                        val numeric = rowIndex > 0 && columnIndex in 4..6 || rowIndex > 0 && columnIndex == 8
                        if (numeric && value.toIntOrNull() != null) {
                            append("<c r=\"").append(cellRef).append("\"><v>").append(value).append("</v></c>")
                        } else {
                            append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            append(xmlEscape(value))
                            append("</t></is></c>")
                        }
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            entry("xl/worksheets/sheet1.xml", sheet)
        }
    }

    fun writePdf(output: OutputStream, items: List<RouteItem>) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 10f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 18f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = margin

        fun newPage() {
            page?.let(document::finishPage)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            val canvas = page!!.canvas
            canvas.drawText("DigiDrop • Отчёт рейса", margin, 38f, titlePaint)
            canvas.drawText("Страница $pageNumber", pageWidth - 105f, 38f, paint)
            canvas.drawLine(margin, 48f, pageWidth - margin, 48f, paint)
            y = 68f
        }
        fun line(text: String) {
            if (y > pageHeight - margin) newPage()
            page!!.canvas.drawText(text.take(100), margin, y, paint)
            y += 15f
        }

        newPage()
        line("Дата формирования: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(java.util.Date())}")
        line("Строк накладной: ${items.size} • Точек: ${items.map { it.storeName to it.address }.distinct().size}")
        line(" ")
        items.forEachIndexed { index, item ->
            val status = deliveryItemStatusLabel(item).uppercase()
            val discrepancy = if (item.discrepancyQty > 0) {
                " • расхождение: ${item.discrepancyQty} (${item.discrepancyReason ?: "причина не указана"})"
            } else ""
            val content = listOf(
                "${index + 1}. ${item.storeName}",
                "Адрес: ${item.address}",
                "Товар: ${item.itemName} • код: ${item.barcode}",
                "План: ${item.totalBoxes} • скан: ${item.scannedBoxes} • осталось: ${remainingBoxes(item)} • ${status}${discrepancy}",
                if (item.note.isNotBlank()) "Примечание: ${item.note}" else ""
            ).filter { it.isNotBlank() }
            content.forEach { text ->
                text.chunked(96).forEach(::line)
            }
            line(" ")
        }
        page?.let(document::finishPage)
        document.writeTo(output)
        document.close()
    }

    private fun columnName(number: Int): String {
        var value = number
        val result = StringBuilder()
        while (value > 0) {
            val remainder = (value - 1) % 26
            result.insert(0, ('A'.code + remainder).toChar())
            value = (value - 1) / 26
        }
        return result.toString()
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
