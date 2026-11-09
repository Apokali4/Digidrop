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
