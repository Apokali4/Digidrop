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
    val scanCodes: List<String> = emptyList()
)

internal object BackupManager {
    fun writeBackup(output: OutputStream, snapshot: BackupSnapshot, filesDir: File) {
        ZipOutputStream(output).use { zip ->
            val evidenceDirectory = File(filesDir, "delivery-evidence")
            val items = JSONArray()
            snapshot.routeItems.forEachIndexed { index, item ->
                val photoEntry = addEvidence(zip, evidenceDirectory, item.photoDocUri, "evidence/photo_$index")
                val signatureEntry = addEvidence(zip, evidenceDirectory, item.signaturePath, "evidence/signature_$index.png")
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
                .put("routeItems", items)
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun readBackup(input: InputStream, filesDir: File): BackupSnapshot {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        val jsonBytes = entries["backup.json"] ?: error("В архиве отсутствует backup.json")
        val root = JSONObject(jsonBytes.toString(Charsets.UTF_8))
        require(root.optInt("schemaVersion") == 1) { "Неподдерживаемая версия резервной копии" }
        val evidenceDirectory = File(filesDir, "delivery-evidence/restored_${System.currentTimeMillis()}").apply { mkdirs() }
        val route = root.optJSONArray("routeItems") ?: JSONArray()
        val items = (0 until route.length()).map { index ->
            val item = route.getJSONObject(index)
            val photoPath = restoreEvidence(entries, item.optString("photoEntry").takeUnless { it == "null" }, evidenceDirectory)
            val signaturePath = restoreEvidence(entries, item.optString("signatureEntry").takeUnless { it == "null" }, evidenceDirectory)
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
            scanCodes = root.optJSONArray("scanCodes").toStringList()
        )
    }

    private fun addEvidence(zip: ZipOutputStream, directory: File, rawPath: String, entryName: String): String? {
        if (rawPath.isBlank()) return null
        val file = File(rawPath)
        if (!file.isFile || !file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) return null
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        return entryName
    }

    private fun restoreEvidence(entries: Map<String, ByteArray>, entryName: String?, directory: File): String? {
        if (entryName.isNullOrBlank()) return null
        val bytes = entries[entryName] ?: return null
        val target = File(directory, entryName.substringAfterLast('/'))
        target.writeBytes(bytes)
        return target.absolutePath
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }
}
