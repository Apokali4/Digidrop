package com.example.qrhelper

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores the user's regional basemap in app-private storage.
 *
 * PMTiles is intentionally validated by its file signature, not by its filename:
 * a renamed PDF must never be accepted as an offline map.
 */
internal object OfflineMapStore {
    private const val MAP_DIRECTORY = "offline-maps"
    private const val MAP_FILE = "kaluga-region.pmtiles"
    private val PMTILES_MAGIC = byteArrayOf(0x50, 0x4d, 0x54, 0x69, 0x6c, 0x65, 0x73)

    fun installedMap(context: Context): File? =
        File(context.filesDir, "$MAP_DIRECTORY/$MAP_FILE").takeIf { it.isFile && it.length() > 127 }

    suspend fun installBundledMap(context: Context): File? = withContext(Dispatchers.IO) {
        val existing = installedMap(context)
        if (existing != null) return@withContext existing
        val asset = runCatching { context.assets.open(MAP_FILE) }.getOrNull()
            ?: return@withContext null
        val directory = File(context.filesDir, MAP_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "Не удалось создать папку офлайн-карт" }
        val temporary = File(directory, "$MAP_FILE.installing")
        val destination = File(directory, MAP_FILE)
        try {
            asset.use { input -> FileOutputStream(temporary).use { output -> input.copyTo(output, 128 * 1024) } }
            check(temporary.length() > 127) { "Встроенная карта повреждена" }
            val signature = ByteArray(PMTILES_MAGIC.size)
            FileInputStream(temporary).use { input ->
                check(input.read(signature) == signature.size && signature.contentEquals(PMTILES_MAGIC)) {
                    "Встроенная карта имеет неверный формат"
                }
            }
            if (destination.exists()) destination.delete()
            check(temporary.renameTo(destination)) { "Не удалось установить встроенную карту" }
            destination
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    fun mapLibreSource(file: File): String =
        "pmtiles://file://" + file.absolutePath

    fun import(context: Context, source: Uri): File {
        val directory = File(context.filesDir, MAP_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "Не удалось создать папку офлайн-карт" }

        val temporary = File(directory, "$MAP_FILE.importing")
        val destination = File(directory, MAP_FILE)
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: error("Не удалось открыть выбранный файл карты")
            input.use { stream ->
                FileOutputStream(temporary).use { output -> stream.copyTo(output, 128 * 1024) }
            }

            check(temporary.length() > 127) { "Файл карты слишком маленький или пустой" }
            val signature = ByteArray(PMTILES_MAGIC.size)
            FileInputStream(temporary).use { input ->
                check(input.read(signature) == signature.size && signature.contentEquals(PMTILES_MAGIC)) {
                    "Нужен архив PMTiles (.pmtiles). Выбранный файл имеет другой формат."
                }
            }

            if (destination.exists()) check(destination.delete()) { "Не удалось заменить старую карту" }
            check(temporary.renameTo(destination)) { "Не удалось сохранить офлайн-карту" }
            return destination
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }
}
