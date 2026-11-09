package com.example.qrhelper

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class BackupSnapshot(
    val routeItems: List<RouteItem>,
    val offlineMode: Boolean = true,
    val autoSave: Boolean = true,
    val compactMode: Boolean = false,
    val unknownCodes: List<String> = emptyList(),
    val scanCodes: List<String> = emptyList(),
    val catalog: List<ProductCatalogRecord> = emptyList()
)

internal fun requireBackupEntryCount(count: Int) {
    require(count in 1..2048) { "В резервной копии слишком много ZIP-файлов (максимум 2048)" }
}

internal object BackupManager {
    private const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
    private const val MAX_JSON_BYTES = 16L * 1024L * 1024L
    private const val MAX_EVIDENCE_BYTES = 12L * 1024L * 1024L
    private const val MAX_ROUTE_ITEMS = 10_000

    fun writeBackup(output: OutputStream, snapshot: BackupSnapshot, filesDir: File) {
        require(snapshot.routeItems.size <= MAX_ROUTE_ITEMS) { "Слишком много строк накладной для резервной копии" }
        // Validate the manifest plus all declared attachments before writing
        // anything, so the app never creates a backup it cannot later restore.
        val declaredEntries = 1 + snapshot.routeItems.sumOf { item ->
            listOf(item.photoDocUri, item.signaturePath).count(String::isNotBlank)
        }
        requireBackupEntryCount(declaredEntries)
        ZipOutputStream(output).use { zip ->
            val evidenceDirectory = File(filesDir, "delivery-evidence")
            val items = JSONArray()
            var evidenceBytes = 0L
            snapshot.routeItems.forEachIndexed { index, item ->
                val photoEntry = addEvidence(zip, evidenceDirectory, item.photoDocUri, "evidence/photo_$index") { size ->
                    evidenceBytes += size
                    require(evidenceBytes <= MAX_ARCHIVE_BYTES) { "Вложения превышают лимит резервной копии 64 МБ" }
                }
                val signatureEntry = addEvidence(zip, evidenceDirectory, item.signaturePath, "evidence/signature_$index.png") { size ->
                    evidenceBytes += size
                    require(evidenceBytes <= MAX_ARCHIVE_BYTES) { "Вложения превышают лимит резервной копии 64 МБ" }
                }
                items.put(JSONObject()
                    .put("barcode", item.barcode)
                    .put("storeName", item.storeName)
                    .put("address", item.address)
                    .put("itemName", item.itemName)
                    .put("totalBoxes", item.totalBoxes)
                    .put("phone", item.phone)
                    .put("note", item.note)
                    .put("isDelivered", item.isDelivered)
                    .put("latitude", item.latitude ?: JSONObject.NULL)
                    .put("longitude", item.longitude ?: JSONObject.NULL)
                    .put("scannedBoxes", item.scannedBoxes)
                    .put("ownerName", item.ownerName)
                    .put("workingHours", item.workingHours)
                    .put("cashNote", item.cashNote)
                    .put("extraComment", item.extraComment)
                    .put("discrepancyQty", item.discrepancyQty)
                    .put("discrepancyReason", item.discrepancyReason ?: JSONObject.NULL)
                    .put("photoEntry", photoEntry ?: JSONObject.NULL)
                    .put("signatureEntry", signatureEntry ?: JSONObject.NULL))
            }
            val json = JSONObject()
                .put("schemaVersion", 1)
                .put("createdAt", System.currentTimeMillis())
                .put("settings", JSONObject()
                    .put("offlineMode", snapshot.offlineMode)
                    .put("autoSave", snapshot.autoSave)
                    .put("compactMode", snapshot.compactMode))
                .put("unknownCodes", JSONArray(snapshot.unknownCodes))
                .put("scanCodes", JSONArray(snapshot.scanCodes))
                .put("products", JSONArray().apply {
                    snapshot.catalog.forEach { product ->
                        put(JSONObject().put("barcode", product.barcode).put("productName", product.productName))
                    }
                })
                .put("routeItems", items)
            val jsonBytes = json.toString().toByteArray(Charsets.UTF_8)
            require(jsonBytes.size.toLong() <= MAX_JSON_BYTES) { "Данные резервной копии превышают лимит JSON 16 МБ" }
            require(evidenceBytes + jsonBytes.size <= MAX_ARCHIVE_BYTES) { "Резервная копия превышает общий лимит 64 МБ" }
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(jsonBytes)
            zip.closeEntry()
        }
    }

    fun cleanupRestoredEvidence(snapshot: BackupSnapshot, filesDir: File) {
        val evidenceRoot = File(filesDir, "delivery-evidence").canonicalFile
        val ownedDirectories = snapshot.routeItems.flatMap { item ->
            listOf(item.photoDocUri, item.signaturePath)
        }.filter(String::isNotBlank).mapNotNull { path ->
            runCatching { File(path).canonicalFile.parentFile }.getOrNull()
        }.filter { directory ->
            directory.parentFile == evidenceRoot &&
                directory.name.matches(Regex("restored_\\d+")) &&
                directory.isDirectory
        }.toSet()
        ownedDirectories.forEach { it.deleteRecursively() }
    }

    fun readBackup(input: InputStream, filesDir: File): BackupSnapshot {
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            var entryCount = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                requireBackupEntryCount(entryCount)
                require(!entry.isDirectory) { "В архиве обнаружена неожиданная папка" }
                require(entry.name == "backup.json" || entry.name.matches(
                    Regex("evidence/(photo_\\d+|signature_\\d+\\.png)")
                )) { "В архиве обнаружен неизвестный файл" }
                require(!entries.containsKey(entry.name)) { "В архиве повторяется файл: ${entry.name}" }
                val limit = if (entry.name == "backup.json") MAX_JSON_BYTES else MAX_EVIDENCE_BYTES
                val bytes = zip.readBounded(limit) { read ->
                    totalBytes += read
                    require(totalBytes <= MAX_ARCHIVE_BYTES) { "Резервная копия превышает 64 МБ" }
                }
                entries[entry.name] = bytes
                zip.closeEntry()
            }
        }

        val jsonBytes = entries["backup.json"] ?: error("В архиве отсутствует backup.json")
        val root = JSONObject(jsonBytes.toString(Charsets.UTF_8))
        require(root.optInt("schemaVersion") == 1) { "Неподдерживаемая версия резервной копии" }
        val route = root.optJSONArray("routeItems") ?: error("В резервной копии отсутствует список точек")
        require(route.length() <= MAX_ROUTE_ITEMS) { "В резервной копии слишком много строк накладной" }

        val evidenceDirectory = File(filesDir, "delivery-evidence/restored_${System.currentTimeMillis()}").apply {
            check(mkdirs() || isDirectory) { "Не удалось подготовить папку фотографий" }
        }
        try {
            val items = (0 until route.length()).map { index ->
                val item = route.getJSONObject(index)
                val photoName = item.optString("photoEntry").takeUnless { it == "null" || it.isBlank() }
                val signatureName = item.optString("signatureEntry").takeUnless { it == "null" || it.isBlank() }
                require(photoName == null || photoName == "evidence/photo_$index") {
                    "Некорректная ссылка на фото в строке ${index + 1}"
                }
                require(signatureName == null || signatureName == "evidence/signature_$index.png") {
                    "Некорректная ссылка на подпись в строке ${index + 1}"
                }
                val photoPath = restoreEvidence(entries, photoName, evidenceDirectory)
                val signaturePath = restoreEvidence(entries, signatureName, evidenceDirectory)
                RouteItem(
                    barcode = item.optString("barcode"),
                    storeName = item.optString("storeName"),
                    address = item.optString("address"),
                    itemName = item.optString("itemName"),
                    totalBoxes = item.optInt("totalBoxes"),
                    phone = item.optString("phone"),
                    note = item.optString("note"),
                    isDelivered = item.optBoolean("isDelivered"),
                    latitude = item.optNullableDouble("latitude"),
                    longitude = item.optNullableDouble("longitude"),
                    scannedBoxes = item.optInt("scannedBoxes"),
                    ownerName = item.optString("ownerName"),
                    workingHours = item.optString("workingHours"),
                    cashNote = item.optString("cashNote"),
                    extraComment = item.optString("extraComment"),
                    photoDocUri = photoPath.orEmpty(),
                    signaturePath = signaturePath.orEmpty(),
                    discrepancyQty = item.optInt("discrepancyQty"),
                    discrepancyReason = item.optString("discrepancyReason").takeUnless { it == "null" || it.isBlank() }
                )
            }
            val settings = root.optJSONObject("settings") ?: JSONObject()
            return BackupSnapshot(
                routeItems = items,
                offlineMode = settings.optBoolean("offlineMode", true),
                autoSave = settings.optBoolean("autoSave", true),
                compactMode = settings.optBoolean("compactMode", false),
                unknownCodes = root.optJSONArray("unknownCodes").toStringList(),
                scanCodes = root.optJSONArray("scanCodes").toStringList(),
                catalog = root.optJSONArray("products").toProductCatalog()
            )
        } catch (error: Exception) {
            evidenceDirectory.deleteRecursively()
            throw error
        }
    }

    private fun InputStream.readBounded(limit: Long, onRead: (Int) -> Unit): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            count += read
            require(count <= limit) { "Файл в резервной копии превышает допустимый размер" }
            onRead(read)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun addEvidence(zip: ZipOutputStream, directory: File, rawPath: String, entryName: String, onEvidenceSize: (Long) -> Unit): String? {
        if (rawPath.isBlank()) return null
        val file = File(rawPath)
        if (!file.isFile || !file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) return null
        val fileSize = file.length()
        require(fileSize <= MAX_EVIDENCE_BYTES) {
            "Файл вложения слишком большой для резервной копии: ${file.name}"
        }
        onEvidenceSize(fileSize)
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        return entryName
    }

    private fun restoreEvidence(entries: Map<String, ByteArray>, entryName: String?, directory: File): String? {
        if (entryName.isNullOrBlank()) return null
        val bytes = entries[entryName]
            ?: error("В резервной копии отсутствует файл вложения: $entryName")
        require(bytes.isNotEmpty() && bytes.size.toLong() <= MAX_EVIDENCE_BYTES) {
            "Файл вложения пустой или превышает допустимый размер"
        }
        val target = File(directory, entryName.substringAfterLast('/'))
        val temporary = File(directory, "." + target.name + ".tmp")
        try {
            java.io.FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            check(temporary.renameTo(target)) { "Не удалось сохранить вложение из резервной копии" }
            return target.absolutePath
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }

    private fun JSONArray?.toProductCatalog(): List<ProductCatalogRecord> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let { item ->
                val barcode = item.optString("barcode").trim()
                val name = item.optString("productName").trim()
                if (barcode.isNotBlank() && name.isNotBlank()) ProductCatalogRecord(barcode, name) else null
            }
        }
}
