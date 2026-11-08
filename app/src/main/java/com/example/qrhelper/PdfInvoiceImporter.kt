package com.example.qrhelper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

internal fun shouldRunPdfOcr(extractedText: String): Boolean = extractedText.isBlank()

internal fun selectPdfInvoiceText(extractedText: String, recognizedText: String): String =
    extractedText.takeIf { it.isNotBlank() }?.trim() ?: recognizedText.trim()

internal fun normalizeOcrTableText(text: String): String =
    text.lineSequence().map { line ->
        line.trim().replace(Regex(" {2,}"), "\t")
    }.joinToString("\n")

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

    suspend fun parse(
        context: Context,
        uri: Uri,
        onProgress: suspend (String) -> Unit = {}
    ): List<RouteItem> {
        val extracted = extractText(context, uri)
        if (!shouldRunPdfOcr(extracted)) {
            val textRows = runCatching { parseManifest(extracted) }.getOrDefault(emptyList())
            if (textRows.isNotEmpty()) return textRows
        }

        withContext(Dispatchers.Main) {
            onProgress(
                if (extracted.isBlank()) "В PDF нет текстового слоя. Запускаю распознавание страниц…"
                else "Текст PDF найден, но колонки не распознаны. Пробую OCR страниц…"
            )
        }
        val recognized = recognizeScannedPdf(context, uri, onProgress)
        val normalized = normalizeOcrTableText(recognized)
        val invoiceText = selectPdfInvoiceText(normalized, recognized)
        check(invoiceText.isNotBlank()) {
            "В PDF не удалось распознать текст. Проверьте качество скана и попробуйте ещё раз."
        }
        val parsed = listOf(invoiceText, recognized).firstNotNullOfOrNull { candidate ->
            runCatching { parseManifest(candidate) }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
        check(!parsed.isNullOrEmpty()) {
            "OCR распознал текст, но не смог разделить колонки накладной. Лучше загрузить чёткий скан с видимыми границами колонок."
        }
        return parsed
    }

    private suspend fun recognizeScannedPdf(
        context: Context,
        uri: Uri,
        onProgress: suspend (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val temporaryPdf = File(context.cacheDir, "invoice_ocr_${System.currentTimeMillis()}.pdf")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Не удалось открыть PDF для распознавания")
            input.use { source -> temporaryPdf.outputStream().use { target -> source.copyTo(target) } }
            val descriptor = ParcelFileDescriptor.open(temporaryPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(descriptor)
                try {
                    require(renderer.pageCount in 1..100) {
                        "В PDF должно быть от 1 до 100 страниц для распознавания"
                    }
                    buildString {
                        for (pageIndex in 0 until renderer.pageCount) {
                            withContext(Dispatchers.Main) {
                                onProgress("Распознаю PDF: страница ${pageIndex + 1} из ${renderer.pageCount}…")
                            }
                            val page = renderer.openPage(pageIndex)
                            val scale = min(
                                2400f / page.width.toFloat(),
                                2400f / page.height.toFloat()
                            ).coerceAtMost(3f)
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val pageText = RussianOcrEngine.recognizeBitmap(context, bitmap, onProgress)
                                if (pageText.isNotBlank()) append(pageText).append('\n')
                            } finally {
                                bitmap.recycle()
                                page.close()
                            }
                        }
                    }
                } finally {
                    renderer.close()
                }
            } finally {
                descriptor.close()
            }
        } finally {
            temporaryPdf.delete()
        }
    }
}
