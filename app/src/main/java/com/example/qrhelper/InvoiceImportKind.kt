package com.example.qrhelper

internal object InvoiceImportKind {
    private val imageExtensions = setOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".bmp")

    fun isImage(mimeType: String?, uri: String?): Boolean {
        val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase()
        if (mime.startsWith("image/")) return true
        val path = uri.orEmpty().substringBefore('?').lowercase()
        return imageExtensions.any(path::endsWith)
    }
}
