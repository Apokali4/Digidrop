package com.example.qrhelper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Shared on-device Russian OCR engine for photo route sheets and scanned PDF invoices. */
internal object RussianOcrEngine {
    suspend fun recognizeUri(
        context: Context,
        imageUri: Uri,
        onProgress: suspend (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        ensureRussianModel(context, onProgress)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: error("Не удалось открыть фотографию")
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Файл не является читаемым изображением" }
        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Не удалось декодировать фотографию")
        try {
            recognizeBitmap(context, bitmap, onProgress)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun recognizeBitmap(
        context: Context,
        bitmap: Bitmap,
        onProgress: suspend (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        ensureRussianModel(context, onProgress)
        withContext(Dispatchers.Main) { onProgress("Распознаю русский текст на устройстве…") }
        val tessDirectory = File(context.filesDir, "tesseract")
        val api = TessBaseAPI()
        try {
            check(api.init(tessDirectory.absolutePath, "rus")) {
                "Tesseract не смог загрузить русскую модель"
            }
            api.setVariable("preserve_interword_spaces", "1")
            api.setImage(bitmap)
            api.getUTF8Text().orEmpty().trim()
        } finally {
            api.recycle()
        }
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
            val assetCopy = File(dataDirectory, "rus.traineddata.asset")
            bundledModel.use { input ->
                assetCopy.outputStream().use { output -> input.copyTo(output) }
            }
            if (assetCopy.length() >= 1_000_000L) {
                if (trainedData.exists()) trainedData.delete()
                check(assetCopy.renameTo(trainedData)) { "Не удалось установить встроенную OCR-модель" }
                return
            }
            assetCopy.delete()
        }

        withContext(Dispatchers.Main) {
            onProgress("Скачиваю русскую OCR-модель (первый запуск)…")
        }
        val temporary = File(dataDirectory, "rus.traineddata.download")
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
            connection.inputStream.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            check(temporary.length() >= 1_000_000L) {
                "OCR-модель скачалась не полностью (${temporary.length()} байт)"
            }
            if (trainedData.exists() && !trainedData.delete()) {
                error("Не удалось обновить старую OCR-модель")
            }
            check(temporary.renameTo(trainedData)) { "Не удалось сохранить OCR-модель" }
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun sampleSize(width: Int, height: Int, maxDimension: Int = 2400): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / sample > maxDimension) sample *= 2
        return sample
    }
}
