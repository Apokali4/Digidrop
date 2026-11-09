package com.example.qrhelper

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val MAX_XLSX_XML_ENTRY_BYTES = 16L * 1024L * 1024L

internal fun readBoundedInvoiceZipEntry(
    input: InputStream,
    maxBytes: Long = MAX_XLSX_XML_ENTRY_BYTES
): ByteArray {
    require(maxBytes > 0L) { "Лимит распаковки XLSX должен быть положительным" }
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) {
            "Внутренний XML XLSX превышает допустимый размер"
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal const val MAX_XLSX_TOTAL_XML_BYTES = 64L * 1024L * 1024L

internal fun nextXlsxXmlTotal(current: Long, entrySize: Int): Long {
    require(current >= 0L && entrySize >= 0) { "Некорректный размер XLSX" }
    val total = current + entrySize.toLong()
    require(total <= MAX_XLSX_TOTAL_XML_BYTES) {
        "Распакованное содержимое XLSX превышает 64 МБ"
    }
    return total
}

internal const val MAX_XLSX_ENTRIES = 2048

internal fun requireXlsxEntryCount(count: Int) {
    require(count in 0..MAX_XLSX_ENTRIES) {
        "В XLSX слишком много ZIP-записей (максимум 2048)"
    }
}

internal fun requireUniqueXlsxEntry(seen: Set<String>, name: String) {
    require(name !in seen) { "В XLSX обнаружена повторяющаяся XML-запись: $name" }
}
