package com.example.qrhelper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Color
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal const val MAX_RUSSIAN_OCR_MODEL_BYTES = 32L * 1024L * 1024L

internal fun installOcrModel(input: InputStream, target: File) {
    val directory = target.parentFile ?: error("У OCR-модели отсутствует папка")
    check(directory.exists() || directory.mkdirs()) { "Не удалось создать папку OCR-модели" }
    val temporary = File(directory, "${target.name}.download")
    val backup = File(directory, "${target.name}.previous")
    try {
        FileOutputStream(temporary).use { output ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_RUSSIAN_OCR_MODEL_BYTES) {
                    "OCR-модель превышает допустимые 32 МБ"
                }
                output.write(buffer, 0, count)
            }
            output.flush()
            output.fd.sync()
        }
        check(temporary.length() >= 1_000_000L) {
            "OCR-модель скачалась не полностью (${temporary.length()} байт)"
        }
        if (backup.exists()) check(backup.delete()) { "Не удалось удалить резерв OCR-модели" }
        if (target.exists()) check(target.renameTo(backup)) { "Не удалось сохранить предыдущую OCR-модель" }
        if (!temporary.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            error("Не удалось установить OCR-модель")
        }
        backup.delete()
    } catch (error: Exception) {
        temporary.delete()
        if (!target.exists() && backup.exists()) backup.renameTo(target)
        throw error
    }
}

/** Shared on-device Russian OCR engine for photo route sheets and scanned PDF invoices. */
internal object RussianOcrEngine {
    suspend fun recognizeUri(
        context: Context,
        imageUri: Uri,
        onProgress: suspend (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        ensureRussianModel(context, onProgress)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openImageStream(context, imageUri).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Файл не является читаемым изображением" }
        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = openImageStream(context, imageUri).use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Не удалось декодировать фотографию")
        try {
            recognizeBitmap(context, bitmap, onProgress)
        } finally {
            bitmap.recycle()
        }
    }


    /**
     * Gallery providers use content:// URIs; DigiDrop's import cache uses file:// URIs.
     * Read app-owned cache files directly instead of relying on ContentResolver for file URIs.
     */
    private fun openImageStream(context: Context, uri: Uri): InputStream {
        return when (uri.scheme?.lowercase()) {
            "file" -> {
                val path = uri.path ?: error("У локальной фотографии отсутствует путь")
                val file = File(path)
                check(file.isFile && file.canRead()) {
                    "Локальная фотография недоступна: ${file.absolutePath}"
                }
                file.inputStream()
            }
            "content", "android.resource" -> context.contentResolver.openInputStream(uri)
                ?: error("Android не открыл выбранную фотографию из хранилища")
            else -> error("Неподдерживаемый источник фотографии: ${uri.scheme ?: "без схемы"}")
        }
    }

    suspend fun recognizeBitmap(
        context: Context,
        bitmap: Bitmap,
        onProgress: suspend (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        ensureRussianModel(context, onProgress)
        withContext(Dispatchers.Main) { onProgress("Выравниваю фото и проверяю четыре ориентации…") }
        val tessDirectory = File(context.filesDir, "tesseract")
        val api = TessBaseAPI()
        try {
            check(api.init(tessDirectory.absolutePath, "rus")) {
                "Tesseract не смог загрузить русскую модель"
            }
            api.setVariable("preserve_interword_spaces", "1")
            api.setVariable("user_defined_dpi", "300")
            var bestText = ""
            var bestScore = Int.MIN_VALUE
            val rotations = intArrayOf(0, 90, 180, 270)
            rotations.forEachIndexed { index, degrees ->
                withContext(Dispatchers.Main) {
                    onProgress("Распознаю страницу: поворот ${degrees}° (${index + 1}/4)…")
                }
                val oriented = rotate(bitmap, degrees)
                val prepared = enhanceForDocument(oriented)
                try {
                    api.setImage(prepared)
                    val text = api.getUTF8Text().orEmpty().trim()
                    val score = scoreRouteText(text)
                    if (score > bestScore) {
                        bestScore = score
                        bestText = text
                    }
                } finally {
                    if (prepared !== oriented) prepared.recycle()
                    if (oriented !== bitmap) oriented.recycle()
                }
            }
            check(bestText.isNotBlank()) { "Не удалось распознать текст ни в одной ориентации" }
            bestText
        } finally {
            api.recycle()
        }
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /** Grayscale + local contrast stretch for photographed paper and faint table print. */
    private fun enhanceForDocument(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        var min = 255
        var max = 0
        val gray = IntArray(pixels.size)
        for (i in pixels.indices) {
            val color = pixels[i]
            val value = (Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114).toInt()
            gray[i] = value
            if (value < min) min = value
            if (value > max) max = value
        }
        val range = (max - min).coerceAtLeast(1)
        for (i in pixels.indices) {
            val value = (((gray[i] - min) * 255) / range).coerceIn(0, 255)
            // Keep faint gray grid lines while making black print easier to separate.
            val contrasted = when {
                value < 105 -> 0
                value > 235 -> 255
                else -> value
            }
            pixels[i] = Color.rgb(contrasted, contrasted, contrasted)
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun scoreRouteText(text: String): Int {
        if (text.isBlank()) return Int.MIN_VALUE
        val lower = text.lowercase(java.util.Locale("ru", "RU")).replace('ё', 'е')
        val addresses = Regex("""(?iu)(?:ул\\.?|улица|проспект|пр\\.?|переулок|пер\\.?|шоссе|наб\\.?|набережная|пл\\.?|площадь)""")
            .findAll(lower).count()
        val numberedRows = Regex("""(?m)^\\s*\\d{1,3}\\s+""").findAll(text).count()
        val cyrillic = text.count { it in 'А'..'я' || it == 'Ё' || it == 'ё' }
        val digits = text.count(Char::isDigit)
        return addresses * 120 + numberedRows * 35 + cyrillic + digits / 2 + text.length / 20
    }

    private suspend fun ensureRussianModel(
        context: Context,
        onProgress: suspend (String) -> Unit
    ) {
        val tessDirectory = File(context.filesDir, "tesseract")
        val dataDirectory = File(tessDirectory, "tessdata")
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) {
            error("Не удалось создать папку OCR-модели")
        }
        val trainedData = File(dataDirectory, "rus.traineddata")
        if (trainedData.exists() && trainedData.length() >= 1_000_000L) return

        val bundledModel = runCatching { context.assets.open("tessdata/rus.traineddata") }.getOrNull()
        if (bundledModel != null) {
            bundledModel.use { input -> installOcrModel(input, trainedData) }
            return
        }

        withContext(Dispatchers.Main) {
            onProgress("Скачиваю русскую OCR-модель (первый запуск)…")
        }
        val connection = (URL("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/rus.traineddata")
            .openConnection() as HttpURLConnection)
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "DigiDrop-Android")
            val responseCode = connection.responseCode
            check(responseCode in 200..299) {
                "Сервер OCR-модели ответил HTTP $responseCode. Проверьте интернет и повторите."
            }
            connection.inputStream.use { input -> installOcrModel(input, trainedData) }
        } finally {
            connection.disconnect()
        }
    }

    private fun sampleSize(width: Int, height: Int, maxDimension: Int = 2400): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / sample > maxDimension) sample *= 2
        return sample
    }
}
