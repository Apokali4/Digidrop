package com.example.qrhelper

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

internal object PdfInvoiceImporter {
    fun extractText(context: Context, uri: Uri): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Не удалось открыть PDF-файл")
        input.use { stream ->
            PDDocument.load(stream).use { document ->
                require(document.numberOfPages > 0) { "PDF не содержит страниц" }
                require(document.numberOfPages <= 100) { "В PDF слишком много страниц (максимум 100)" }
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                    wordSeparator = "\t"
                    lineSeparator = "\n"
                    pageStart = ""
                    pageEnd = "\n"
                }
                return stripper.getText(document).trim()
            }
        }
    }

    fun parse(context: Context, uri: Uri): List<RouteItem> {
        val text = extractText(context, uri)
        check(text.isNotBlank()) {
            "В PDF нет выделяемого текста. Если это скан, сначала распознайте его через модуль фото-маршрута."
        }
        return parseManifest(text)
    }
}
