package com.example.qrhelper

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal const val MAX_IMPORT_BYTES = 64L * 1024L * 1024L

internal fun InputStream.readInvoiceBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= MAX_IMPORT_BYTES) {
            "Файл слишком большой. Максимальный размер документа: 64 МБ"
        }
        output.write(buffer, 0, count)
    }
    require(total > 0L) { "Выбранный файл пустой" }
    return output.toByteArray()
}

internal fun decodeInvoiceText(bytes: ByteArray): String {
    val utf8 = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return runCatching { utf8.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") }
        .getOrElse { String(bytes, charset("windows-1251")).removePrefix("\uFEFF") }
}

internal enum class InvoiceDocumentFormat {
    IMAGE, PDF, XLS, XLSX, CSV, TSV, TEXT, DOC, DOCX, RTF, ODS, ODT, XML, JSON, HTML, UNKNOWN
}

internal object InvoiceImportKind {
    private val imageExtensions = setOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".bmp", ".tif", ".tiff")
    private fun extension(uri: String?) = uri.orEmpty().substringBefore('?').substringAfterLast('/', "").let {
        if (it.contains('.')) "." + it.substringAfterLast('.').lowercase() else ""
    }

    fun isImage(mimeType: String?, uri: String?): Boolean {
        val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase()
        return mime.startsWith("image/") || extension(uri) in imageExtensions
    }

    fun detect(mimeType: String?, uri: String?): InvoiceDocumentFormat {
        val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase()
        val ext = extension(uri)
        return when {
            isImage(mime, uri) -> InvoiceDocumentFormat.IMAGE
            ext == ".pdf" || mime == "application/pdf" -> InvoiceDocumentFormat.PDF
            ext == ".xlsx" || mime.contains("spreadsheetml") -> InvoiceDocumentFormat.XLSX
            ext == ".xls" || mime == "application/vnd.ms-excel" -> InvoiceDocumentFormat.XLS
            ext == ".csv" || mime == "application/csv" || mime == "text/csv" -> InvoiceDocumentFormat.CSV
            ext == ".tsv" || mime == "text/tab-separated-values" -> InvoiceDocumentFormat.TSV
            ext == ".doc" || mime == "application/msword" -> InvoiceDocumentFormat.DOC
            ext == ".docx" || mime.contains("wordprocessingml") -> InvoiceDocumentFormat.DOCX
            ext == ".rtf" || mime == "application/rtf" || mime == "text/rtf" -> InvoiceDocumentFormat.RTF
            ext == ".ods" || mime.contains("opendocument.spreadsheet") -> InvoiceDocumentFormat.ODS
            ext == ".odt" || mime.contains("opendocument.text") -> InvoiceDocumentFormat.ODT
            ext == ".xml" || mime == "application/xml" || mime == "text/xml" -> InvoiceDocumentFormat.XML
            ext == ".json" || mime == "application/json" -> InvoiceDocumentFormat.JSON
            ext in setOf(".html", ".htm") || mime == "text/html" -> InvoiceDocumentFormat.HTML
            ext in setOf(".txt", ".log") || mime.startsWith("text/") -> InvoiceDocumentFormat.TEXT
            else -> InvoiceDocumentFormat.UNKNOWN
        }
    }
}
