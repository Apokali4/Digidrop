package com.example.qrhelper

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Converts supported office/structured document containers into a tabular text
 * representation consumed by the same manifest column mapper as CSV/XLSX.
 */
internal object ExtendedInvoiceParser {
    fun parse(format: InvoiceDocumentFormat, bytes: ByteArray): List<RouteItem> {
        val text = when (format) {
            InvoiceDocumentFormat.XLS -> legacyXlsToTsv(bytes)
            InvoiceDocumentFormat.DOCX -> docxToTsv(bytes)
            InvoiceDocumentFormat.XML -> xmlToTsv(bytes.toString(Charsets.UTF_8))
            InvoiceDocumentFormat.JSON -> jsonToTsv(bytes.toString(Charsets.UTF_8))
            InvoiceDocumentFormat.HTML -> htmlToTsv(bytes.toString(Charsets.UTF_8))
            InvoiceDocumentFormat.TEXT, InvoiceDocumentFormat.CSV, InvoiceDocumentFormat.TSV ->
                bytes.toString(Charsets.UTF_8)
            else -> error("Формат не поддерживается этим обработчиком: $format")
        }
        return parseManifest(text)
    }

    private fun legacyXlsToTsv(bytes: ByteArray): String {
        HSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val formatter = DataFormatter()
            val evaluator = workbook.creationHelper.createFormulaEvaluator()
            return buildString {
                for (sheet in workbook) {
                    for (row in sheet) {
                        val last = (row.lastCellNum.toInt()).coerceAtLeast(0)
                        append((0 until last).joinToString("\t") { index ->
                            formatter.formatCellValue(row.getCell(index), evaluator)
                                .replace("\t", " ").replace("\n", " ")
                        }).append('\n')
                    }
                    append('\n')
                }
            }
        }
    }

    private fun docxToTsv(bytes: ByteArray): String {
        val xml = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var result = ""
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    result = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
            }
            result
        }
        require(xml.isNotBlank()) { "В DOCX не найден основной документ Word" }
        return tableXmlToTsv(xml, "w:tr", "w:tc")
    }

    private fun xmlToTsv(xml: String): String {
        val table = tableXmlToTsv(xml, "tr|row", "td|th|cell")
        if (table.isNotBlank()) return table
        val cleaned = xml.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
        return cleaned.lineSequence().map(String::trim).filter(String::isNotBlank).joinToString("\n")
    }

    private fun tableXmlToTsv(xml: String, rowTag: String, cellTag: String): String {
        val rows = Regex("<(?:$rowTag)(?:\\s[^>]*)?>(.*?)</(?:$rowTag)>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
        return rows.map { row ->
            Regex("<(?:$cellTag)(?:\\s[^>]*)?>(.*?)</(?:$cellTag)>", RegexOption.DOT_MATCHES_ALL)
                .findAll(row.groupValues[1]).joinToString("\t") { cell ->
                    Regex("<(?:w:t|t)(?:\\s[^>]*)?>(.*?)</(?:w:t|t)>", RegexOption.DOT_MATCHES_ALL)
                        .findAll(cell.groupValues[1]).joinToString("") { decodeXml(it.groupValues[1]) }
                        .ifBlank { decodeXml(cell.groupValues[1].replace(Regex("<[^>]+>"), "")) }
                }
        }.filter(String::isNotBlank).joinToString("\n")
    }

    private fun jsonToTsv(json: String): String {
        val objects = Regex("\\{([^{}]*)\\}", RegexOption.DOT_MATCHES_ALL).findAll(json).map { it.groupValues[1] }.toList()
        val pairs = objects.map { objectText ->
            Regex("\"([^\"]+)\"\\s*:\\s*(\"(?:\\\\.|[^\"])*\"|-?\\d+(?:\\.\\d+)?|true|false|null)", RegexOption.IGNORE_CASE)
                .findAll(objectText).associate { match ->
                    match.groupValues[1] to match.groupValues[2].trim('"').replace("\\\"", "\"")
                }
        }.filter { it.isNotEmpty() }
        require(pairs.isNotEmpty()) { "JSON не содержит массива объектов с полями накладной" }
        val headers = pairs.flatMap { it.keys }.distinct()
        return (listOf(headers.joinToString("\t")) + pairs.map { row ->
            headers.joinToString("\t") { row[it].orEmpty().replace("\t", " ") }
        }).joinToString("\n")
    }

    private fun htmlToTsv(html: String): String {
        val rows = Regex("<tr\\b[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html).map { row ->
                Regex("<(?:td|th)\\b[^>]*>(.*?)</(?:td|th)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .findAll(row.groupValues[1]).joinToString("\t") { cell ->
                        decodeXml(cell.groupValues[1].replace(Regex("<[^>]+>"), " ").trim())
                    }
            }.filter(String::isNotBlank).toList()
        if (rows.isNotEmpty()) return rows.joinToString("\n")
        return html.replace(Regex("<(script|style)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun decodeXml(value: String): String = value
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
}
