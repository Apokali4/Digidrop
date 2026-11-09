package com.example.qrhelper

import java.io.ByteArrayOutputStream
import java.io.InputStream

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
            "Файл слишком большой. Максимальный размер накладной или фотографии: 64 МБ"
        }
        output.write(buffer, 0, count)
    }
    require(total > 0L) { "Выбранный файл пустой" }
    return output.toByteArray()
}

internal object InvoiceImportKind {
    private val imageExtensions = setOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".bmp")

    fun isImage(mimeType: String?, uri: String?): Boolean {
        val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase()
        if (mime.startsWith("image/")) return true
        val path = uri.orEmpty().substringBefore('?').lowercase()
        return imageExtensions.any(path::endsWith)
    }
}
