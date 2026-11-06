@file:Suppress("UsePropertyAccessSyntax")

package com.example.qrhelper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.location.Geocoder
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.view.SoundEffectConstants
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import java.io.InputStream
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.time.Duration.Companion.milliseconds

data class RouteItem(
    val barcode: String,
    val storeName: String,
    val address: String,
    val itemName: String,
    val totalBoxes: Int,
    val phone: String,
    val note: String = "Разгрузка через пандус",
    val isDelivered: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val scannedBoxes: Int = 0
)

// Реальный рейс появляется только после загрузки файла накладной.
val INITIAL_DEMO_ROUTE: List<RouteItem> = emptyList()


private fun routeItemKey(item: RouteItem): String =
    listOf(item.barcode, item.storeName, item.address, item.itemName, item.phone).joinToString("|")

private data class BarcodeLookup(val candidates: Set<String>, val hasUniquePackageId: Boolean)

private fun normalizeScannedBarcode(raw: String): BarcodeLookup {
    val value = raw.trim().removePrefix("]C1").replace("(", "").replace(")", "")
    val candidates = linkedSetOf(value)
    val gtin = Regex("01(\\d{14})").find(value)?.groupValues?.getOrNull(1)
    if (gtin != null) candidates.add(gtin)
    val hasSscc = Regex("00\\d{18}").containsMatchIn(value)
    val hasSerial = Regex("21[A-Za-z0-9-]{1,20}").containsMatchIn(value)
    val digitsOnly = value.all { it.isDigit() }
    val plainRetailCode = digitsOnly && value.length in setOf(8, 12, 13, 14)
    return BarcodeLookup(candidates, hasSscc || hasSerial || (!plainRetailCode && gtin == null && value.length > 14))
}

private fun splitCsvLine(line: String, delimiter: Char): List<String> {
    val result = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                field.append('"'); index++
            }
            char == '"' -> quoted = !quoted
            char == delimiter && !quoted -> {
                result.add(field.toString().trim()); field.setLength(0)
            }
            else -> field.append(char)
        }
        index++
    }
    result.add(field.toString().trim())
    return result
}

private fun xlsxColumnIndex(reference: String): Int {
    var value = 0
    for (char in reference.takeWhile { it.isLetter() }) {
        value = value * 26 + (char.uppercaseChar() - 'A' + 1)
    }
    return (value - 1).coerceAtLeast(0)
}

private fun readXlsxXml(xml: String, sharedStrings: List<String> = emptyList()): List<List<String>> {
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(StringReader(xml))
    val rows = mutableListOf<List<String>>()
    var row: MutableMap<Int, String>? = null
    var cellIndex = -1
    var cellType = ""
    var cellValue = StringBuilder()
    var capture = false
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "row" -> row = mutableMapOf()
                "c" -> {
                    cellIndex = xlsxColumnIndex(parser.getAttributeValue(null, "r") ?: "A")
                    cellType = parser.getAttributeValue(null, "t") ?: ""
                    cellValue = StringBuilder()
                }
                "v", "t" -> if (cellIndex >= 0) capture = true
            }
            XmlPullParser.TEXT -> if (capture) cellValue.append(parser.text)
            XmlPullParser.END_TAG -> when (parser.name) {
                "v", "t" -> capture = false
                "c" -> {
                    val raw = cellValue.toString()
                    val value = if (cellType == "s") sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                    row?.set(cellIndex, value)
                    cellIndex = -1
                }
                "row" -> {
                    val current = row.orEmpty()
                    val max = current.keys.maxOrNull() ?: -1
                    rows.add((0..max).map { current[it].orEmpty() })
                    row = null
                }
            }
        }
        event = parser.next()
    }
    return rows
}

private fun parseXlsx(input: InputStream): List<RouteItem> {
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && (entry.name == "xl/sharedStrings.xml" || entry.name.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml")))) {
                entries[entry.name] = zip.readBytes()
            }
            zip.closeEntry()
        }
    }
    val sharedXml = entries["xl/sharedStrings.xml"]?.toString(Charsets.UTF_8)
    val shared = if (sharedXml == null) emptyList() else {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(sharedXml))
        val values = mutableListOf<String>()
        var inString = false
        var current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "si") { inString = true; current = StringBuilder() }
                XmlPullParser.TEXT -> if (inString) current.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") { values.add(current.toString()); inString = false }
            }
            event = parser.next()
        }
        values
    }
    val sheetName = entries.keys.filter { it.startsWith("xl/worksheets/sheet") }.sorted().firstOrNull()
        ?: return emptyList()
    val rows = readXlsxXml(entries.getValue(sheetName).toString(Charsets.UTF_8), shared)
    val tsv = rows.joinToString("\n") { row ->
        row.joinToString("\t") { value -> "\"" + value.replace("\"", "\"\"") + "\"" }
    }
    return parseManifest(tsv)
}

private fun parseManifest(text: String): List<RouteItem> {
    val lines = text.removePrefix("\uFEFF").lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        .toList()
    if (lines.isEmpty()) return emptyList()

    val delimiter = listOf(';', '\t', ',').maxByOrNull { candidate ->
        splitCsvLine(lines.first(), candidate).size
    } ?: ';'
    val first = splitCsvLine(lines.first(), delimiter)
    fun normalize(value: String) = value.trim().lowercase().replace("ё", "е")
        .replace(Regex("[\\s_№.-]+"), "")
    val aliases = mapOf(
        "barcode" to setOf("barcode", "штрихкод", "штрихкодгруза", "код", "кодтовара", "кодгруза", "qr", "qrкод", "этикетка"),
        "store" to setOf("store", "магазин", "торговаяточка", "получатель", "наименованиеполучателя", "получательнаименование", "клиент", "названиеточки", "объект"),
        "address" to setOf("address", "адрес", "адресдоставки", "адресполучателя"),
        "item" to setOf("item", "товар", "наименование", "наименованиетовара", "номенклатура", "груз"),
        "boxes" to setOf("boxes", "мест", "колвомест", "количествомест", "местколво", "колвоупаковок", "коробки", "количество", "шт") ,
        "phone" to setOf("phone", "телефон", "контакт"),
        "note" to setOf("note", "комментарий", "примечание"),
        "lat" to setOf("lat", "latitude", "широта"),
        "lon" to setOf("lon", "lng", "longitude", "долгота")
    )
    val normalized = first.map(::normalize)
    val hasHeader = normalized.any { it in aliases.values.flatten() }
    val columns = if (hasHeader) aliases.mapValues { (_, names) -> normalized.indexOfFirst { it in names } } else
        mapOf("barcode" to 0, "store" to 1, "address" to 2, "item" to 3, "boxes" to 4, "phone" to 5, "note" to 6, "lat" to 7, "lon" to 8)
    fun value(row: List<String>, key: String): String {
        val i = columns[key] ?: -1
        return if (i >= 0 && i < row.size) row[i].trim() else ""
    }
    val parsed = mutableListOf<RouteItem>()
    for (lineIndex in (if (hasHeader) 1 else 0) until lines.size) {
        val row = splitCsvLine(lines[lineIndex], delimiter)
        val barcode = value(row, "barcode")
        val store = value(row, "store")
        val address = value(row, "address")
        if (barcode.isBlank() || (store.isBlank() && address.isBlank())) continue
        val boxes = value(row, "boxes").replace(',', '.').toDoubleOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
        val latitude = value(row, "lat").replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
        val longitude = value(row, "lon").replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
        parsed.add(RouteItem(
            barcode = barcode, storeName = store.ifBlank { "Точка без названия" },
            address = address, itemName = value(row, "item").ifBlank { "Груз по накладной" },
            totalBoxes = boxes, phone = value(row, "phone"),
            note = value(row, "note").ifBlank { "Разгрузка по накладной" },
            latitude = latitude, longitude = longitude
        ))
    }
    return parsed
}

private fun orderRouteByCoordinates(items: List<RouteItem>): List<RouteItem> {
    if (items.size < 3 || items.any { it.latitude == null || it.longitude == null }) return items
    val remaining = items.drop(1).toMutableList()
    val ordered = mutableListOf(items.first())
    fun distance(a: RouteItem, b: RouteItem): Double {
        val lat1 = Math.toRadians(a.latitude!!); val lat2 = Math.toRadians(b.latitude!!)
        val dLat = lat2 - lat1; val dLon = Math.toRadians(b.longitude!! - a.longitude!!)
        val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 6371000.0 * 2 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
    }
    while (remaining.isNotEmpty()) {
        val next = remaining.minByOrNull { distance(ordered.last(), it) }!!
        ordered.add(next); remaining.remove(next)
    }
    return ordered
}


private suspend fun geocodeRoute(context: Context, items: List<RouteItem>): List<RouteItem> =
    withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext items
        val geocoder = Geocoder(context, Locale("ru", "RU"))
        val resolved = items.map { item ->
            if (item.latitude != null && item.longitude != null || item.address.isBlank()) item
            else try {
                val point = geocoder.getFromLocationName(item.address, 1)?.firstOrNull()
                if (point != null) item.copy(latitude = point.latitude, longitude = point.longitude) else item
            } catch (_: Exception) { item }
        }
        orderRouteByCoordinates(resolved)
    }

const val DISPATCHER_PHONE = "+74842998877"

enum class AppScreen {
    SPLASH,
    HUB,
    ROUTE_LIST,
    DETAILS,
    SCANNER,
    SCAN_RESULTS,
    POINT_DETAILS,
    REPORTS,
    SETTINGS,
    HISTORY,
    UNKNOWN_CODES
}

val BgDark = Color(0xFFE7ECF1)
val CardSurface = Color(0xFFF7F9FC)
val CardBorder = Color(0xFFC9D3DB)
val EmeraldPrimary = Color(0xFF2D7BFF)
val MintAccent = Color(0xFF1767D2)
val TextLight = Color(0xFF1F2B36)
val TextMuted = Color(0xFF586979)

@Composable
fun Modifier.bounceClick(
    scaleDown: Float = 0.94f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "bounceScale"
    )
    val view = LocalView.current

    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null
        ) {
            view.playSoundEffect(SoundEffectConstants.CLICK)
            onClick()
        }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigitDropRoot()
        }
    }
}

@Composable
fun DigitDropRoot() {
    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }
    var previousScreen by remember { mutableStateOf(AppScreen.HUB) }
    var selectedItem by remember { mutableStateOf<RouteItem?>(null) }
    var selectedPointItems by remember { mutableStateOf<List<RouteItem>>(emptyList()) }
    var scanMatches by remember { mutableStateOf<List<RouteItem>>(emptyList()) }
    var settingsOffline by remember { mutableStateOf(prefs.getBoolean("offline_mode", true)) }
    var settingsAutoSave by remember { mutableStateOf(prefs.getBoolean("auto_save", true)) }
    var settingsCompact by remember { mutableStateOf(prefs.getBoolean("compact_mode", false)) }
    var unknownCodes by remember { mutableStateOf(prefs.getStringSet("unknown_codes", emptySet())?.toList().orEmpty()) }
    val context = LocalContext.current
    val importScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("digitdrop_prefs", Context.MODE_PRIVATE) }

    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val cameraId = remember {
        try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    var isFlashlightOn by remember { mutableStateOf(false) }

    fun toggleFlashlight() {
        if (cameraId != null) {
            try {
                val newState = !isFlashlightOn
                cameraManager.setTorchMode(cameraId, newState)
                isFlashlightOn = newState
            } catch (ex: Exception) {
                Toast.makeText(context, "Фонарик: " + ex.message, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Вспышка не найдена", Toast.LENGTH_SHORT).show()
        }
    }

    var routeItems by remember {
        mutableStateOf(
            parseManifest(prefs.getString("active_manifest", "").orEmpty()).map { item ->
                val savedDelivered = prefs.getBoolean("delivered_" + routeItemKey(item), item.isDelivered)
                val savedScanned = prefs.getInt("scanned_" + routeItemKey(item), item.scannedBoxes)
                item.copy(isDelivered = savedDelivered, scannedBoxes = savedScanned)
            }
        )
    }

    fun updateItemStatus(item: RouteItem) {
        val nextStatus = !item.isDelivered
        prefs.edit().putBoolean("delivered_" + routeItemKey(item), nextStatus).apply()
        routeItems = routeItems.map { current ->
            if (routeItemKey(current) == routeItemKey(item)) current.copy(isDelivered = nextStatus) else current
        }
        if (selectedItem?.let(::routeItemKey) == routeItemKey(item)) {
            selectedItem = selectedItem?.copy(isDelivered = nextStatus)
        }
    }

    fun resetRouteProgress() {
        val editor = prefs.edit()
        routeItems.forEach { item ->
            editor.remove("delivered_" + routeItemKey(item))
            editor.remove("scanned_" + routeItemKey(item))
        }
        editor.remove("scan_codes")
        editor.remove("unknown_codes")
        editor.remove("active_manifest")
        editor.apply()
        routeItems = routeItems.map { it.copy(isDelivered = false, scannedBoxes = 0) }
        Toast.makeText(context, "Статусы точек сброшены", Toast.LENGTH_SHORT).show()
    }

    val reportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val header = "Штрихкод;Точка;Адрес;Товар;Всего мест;Отсканировано;Осталось;Статус\n"
                val rows = routeItems.joinToString("\n") { item ->
                    listOf(
                        item.barcode, item.storeName, item.address, item.itemName,
                        item.totalBoxes.toString(), item.scannedBoxes.toString(),
                        (item.totalBoxes - item.scannedBoxes).coerceAtLeast(0).toString(),
                        if (item.isDelivered) "Доставлено" else "В пути"
                    ).joinToString(";") { value -> "\"" + value.replace("\"", "\"\"") + "\"" }
                }
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write("\uFEFF")
                    writer.write(header)
                    writer.write(rows)
                }
                Toast.makeText(context, "Отчёт сохранён", Toast.LENGTH_SHORT).show()
            } catch (ex: Exception) {
                Toast.makeText(context, "Не удалось сохранить отчёт: " + ex.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val isExcel = mime.contains("spreadsheet") || uri.toString().lowercase().endsWith(".xlsx")
                val imported = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    if (isExcel) parseXlsx(inputStream)
                    else parseManifest(inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                }.orEmpty()
                if (imported.isNotEmpty()) {
                        val content = imported.joinToString("\n") { item ->
                            listOf(item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(), item.phone, item.note,
                                item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty()).joinToString("\t") { it.replace("\t", " ") }
                        }
                        prefs.edit().putString("active_manifest", content).apply()
                        val withProgress = imported.map { item ->
                            item.copy(
                                isDelivered = prefs.getBoolean("delivered_" + routeItemKey(item), false),
                                scannedBoxes = prefs.getInt("scanned_" + routeItemKey(item), 0)
                            )
                        }
                        routeItems = withProgress
                        importScope.launch {
                            val ordered = geocodeRoute(context, withProgress)
                            routeItems = ordered
                            val optimized = ordered.map { it.barcode } != withProgress.map { it.barcode }
                            val geocoded = ordered.count { it.latitude != null && it.longitude != null }
                            Toast.makeText(
                                context,
                                "Загружено позиций: ${ordered.size}" + when {
                                    optimized -> " • точки упорядочены примерно по расстоянию"
                                    geocoded == ordered.size && ordered.size > 1 -> " • координаты найдены, порядок уже близкий"
                                    else -> " • порядок из накладной сохранён"
                                },
                                Toast.LENGTH_LONG
                            ).show()
                        }
                } else {
                    Toast.makeText(context, "Не удалось найти строки накладной. Нужны штрихкод, точка/адрес и количество мест.", Toast.LENGTH_LONG).show()
                }
            } catch (ex: Exception) {
                Toast.makeText(context, "Ошибка чтения: " + ex.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun makePhoneCall(phone: String) {
        if (phone.isBlank()) {
            Toast.makeText(context, "Номер телефона не указан", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_DIAL, ("tel:" + Uri.encode(phone)).toUri())
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Звонилка не найдена", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            currentScreen = AppScreen.SCANNER
        } else {
            Toast.makeText(context, "Разрешите доступ к камере", Toast.LENGTH_SHORT).show()
        }
    }

    fun openScanner() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            previousScreen = currentScreen
            currentScreen = AppScreen.SCANNER
        } else {
            previousScreen = currentScreen
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchNavigation(address: String, latitude: Double? = null, longitude: Double? = null) {
        if (address.isBlank() && (latitude == null || longitude == null)) {
            Toast.makeText(context, "У точки нет адреса или координат", Toast.LENGTH_SHORT).show(); return
        }
        val query = if (latitude != null && longitude != null) "$latitude,$longitude" else address
        val uriString = "geo:0,0?q=" + Uri.encode(query)
        val mapIntent = Intent(Intent.ACTION_VIEW, uriString.toUri())
        try {
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            try {
                val webMap = Uri.parse("https://yandex.ru/maps/?text=" + Uri.encode(query))
                context.startActivity(Intent(Intent.ACTION_VIEW, webMap))
            } catch (_: Exception) {
                Toast.makeText(context, "Не найдено приложение карт или браузер", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = currentScreen != AppScreen.SPLASH) {
        currentScreen = when (currentScreen) {
            AppScreen.SCANNER -> previousScreen
            AppScreen.SCAN_RESULTS -> AppScreen.SCANNER
            AppScreen.POINT_DETAILS -> AppScreen.ROUTE_LIST
            AppScreen.DETAILS -> AppScreen.ROUTE_LIST
            AppScreen.ROUTE_LIST -> AppScreen.HUB
            AppScreen.HUB -> AppScreen.HUB
            AppScreen.REPORTS, AppScreen.SETTINGS, AppScreen.HISTORY, AppScreen.UNKNOWN_CODES -> AppScreen.HUB
            AppScreen.SPLASH -> AppScreen.HUB
        }
    }

    when (currentScreen) {
        AppScreen.SPLASH -> {
            SplashScreen(onFinished = { currentScreen = AppScreen.HUB })
        }
        AppScreen.HUB -> {
            HubScreen(
                routeItems = routeItems,
                isFlashOn = isFlashlightOn,
                onFlashToggle = { toggleFlashlight() },
                onOpenRoute = { currentScreen = AppScreen.ROUTE_LIST },
                onOpenScanner = { openScanner() },
                onLoadFile = { filePickerLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                onExportReport = { reportLauncher.launch("DigiDrop_report.csv") },
                onCallDispatcher = { makePhoneCall(DISPATCHER_PHONE) },
                onResetRoute = { resetRouteProgress() },
                onOpenReports = { currentScreen = AppScreen.REPORTS },
                onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                onOpenHistory = { currentScreen = AppScreen.HISTORY },
                onOpenUnknown = { unknownCodes = prefs.getStringSet("unknown_codes", emptySet())?.toList().orEmpty(); currentScreen = AppScreen.UNKNOWN_CODES },
                onNavigateNext = {
                    val nextItem = routeItems.firstOrNull { !it.isDelivered }
                    if (nextItem == null) Toast.makeText(context, "Сначала загрузите накладную", Toast.LENGTH_SHORT).show()
                    else launchNavigation(nextItem.address, nextItem.latitude, nextItem.longitude)
                }
            )
        }
        AppScreen.ROUTE_LIST -> {
            RouteListScreen(
                routeItems = routeItems,
                isFlashOn = isFlashlightOn,
                onFlashToggle = { toggleFlashlight() },
                onBackToHub = { currentScreen = AppScreen.HUB },
                onOpenScanner = { openScanner() },
                onOpenPointMap = { address -> launchNavigation(address) },
                onCallClient = { phone -> makePhoneCall(phone) },
                onPointClick = { pointItems ->
                    selectedPointItems = pointItems
                    currentScreen = AppScreen.POINT_DETAILS
                },
                onToggleItem = { item -> updateItemStatus(item) }
            )
        }
        AppScreen.DETAILS -> {
            selectedItem?.let { item ->
                PointDetailsScreen(
                    item = item,
                    onBack = { currentScreen = AppScreen.ROUTE_LIST },
                    onNavigate = { launchNavigation(item.address, item.latitude, item.longitude) },
                    onCall = { makePhoneCall(item.phone) },
                    onToggleDelivered = { updateItemStatus(item) }
                )
            } ?: run {
                currentScreen = AppScreen.ROUTE_LIST
            }
        }
        AppScreen.POINT_DETAILS -> {
            PointGroupScreen(
                items = selectedPointItems,
                onBack = { currentScreen = AppScreen.ROUTE_LIST },
                onOpenInvoice = { item ->
                    selectedItem = item
                    currentScreen = AppScreen.DETAILS
                },
                onNavigate = {
                    val item = selectedPointItems.firstOrNull()
                    if (item != null) launchNavigation(item.address, item.latitude, item.longitude)
                },
                onCall = { makePhoneCall(selectedPointItems.firstOrNull()?.phone.orEmpty()) }
            )
        }
        AppScreen.SCAN_RESULTS -> {
            ScanResultsScreen(
                code = scanMatches.firstOrNull()?.barcode.orEmpty(),
                matches = scanMatches,
                onBack = { currentScreen = AppScreen.SCANNER },
                onOpenPoint = { item ->
                    selectedItem = item
                    currentScreen = AppScreen.DETAILS
                }
            )
        }
        AppScreen.REPORTS -> {
            ReportsScreen(routeItems, onBack = { currentScreen = AppScreen.HUB }, onExport = { reportLauncher.launch("DigiDrop_report.csv") })
        }
        AppScreen.SETTINGS -> {
            SettingsScreen(
                offlineMode = settingsOffline, autoSave = settingsAutoSave, compactMode = settingsCompact,
                onBack = { currentScreen = AppScreen.HUB },
                onOfflineChange = { settingsOffline = it; prefs.edit().putBoolean("offline_mode", it).apply() },
                onAutoSaveChange = { settingsAutoSave = it; prefs.edit().putBoolean("auto_save", it).apply() },
                onCompactChange = { settingsCompact = it; prefs.edit().putBoolean("compact_mode", it).apply() },
                onClearUnknown = { prefs.edit().remove("unknown_codes").apply(); unknownCodes = emptyList() }
            )
        }
        AppScreen.HISTORY -> {
            HistoryScreen(routeItems, onBack = { currentScreen = AppScreen.HUB })
        }
        AppScreen.UNKNOWN_CODES -> {
            UnknownCodesScreen(unknownCodes, onBack = { currentScreen = AppScreen.HUB }, onClear = {
                prefs.edit().remove("unknown_codes").apply(); unknownCodes = emptyList()
            })
        }
        AppScreen.SCANNER -> {
            LiveCameraScannerScreen(
                isFlashOn = isFlashlightOn,
                onFlashToggle = { toggleFlashlight() },
                onBack = { currentScreen = previousScreen },
                onBarcodeScanned = { scannedCode ->
                    val normalizedCode = scannedCode.trim()
                    val lookup = normalizeScannedBarcode(normalizedCode)
                    val matches = routeItems.filter { item -> lookup.candidates.any { it.equals(item.barcode.trim(), ignoreCase = true) } }
                    val matchedItem = matches.singleOrNull()
                    val savedCodes = prefs.getStringSet("scan_codes", emptySet())?.toMutableSet() ?: mutableSetOf()
                    val isPlainProductCode = !lookup.hasUniquePackageId
                    when {
                        matches.size > 1 -> {
                            scanMatches = matches
                            currentScreen = AppScreen.SCAN_RESULTS
                        }
                        matchedItem == null -> {
                            val unknown = prefs.getStringSet("unknown_codes", emptySet())?.toMutableSet() ?: mutableSetOf()
                            unknown.add(normalizedCode)
                            prefs.edit().putStringSet("unknown_codes", unknown).apply()
                            Toast.makeText(context, "Неизвестный код: $normalizedCode", Toast.LENGTH_LONG).show()
                            currentScreen = AppScreen.HUB
                        }
                        isPlainProductCode -> {
                            val remaining = (matchedItem.totalBoxes - matchedItem.scannedBoxes).coerceAtLeast(0)
                            Toast.makeText(
                                context,
                                "${matchedItem.storeName} • ${matchedItem.itemName} • осталось $remaining из ${matchedItem.totalBoxes}. Обычный товарный EAN не подтверждает отдельную коробку.",
                                Toast.LENGTH_LONG
                            ).show()
                            selectedItem = matchedItem
                            currentScreen = AppScreen.DETAILS
                        }
                        normalizedCode in savedCodes -> {
                            Toast.makeText(context, "Дубликат уникального кода упаковки: $normalizedCode", Toast.LENGTH_LONG).show()
                            currentScreen = AppScreen.HUB
                        }
                        else -> {
                            savedCodes.add(normalizedCode)
                            val itemKey = routeItemKey(matchedItem)
                            prefs.edit().putStringSet("scan_codes", savedCodes).putInt(
                                "scanned_" + itemKey,
                                prefs.getInt("scanned_" + itemKey, matchedItem.scannedBoxes) + 1
                            ).apply()
                            val updated = matchedItem.copy(scannedBoxes = matchedItem.scannedBoxes + 1)
                            routeItems = routeItems.map { if (routeItemKey(it) == itemKey) updated else it }
                            selectedItem = updated
                            try {
                                ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_ACK, 120)
                            } catch (_: Exception) {}
                            Toast.makeText(context, "Скан принят: ${updated.scannedBoxes} / ${updated.totalBoxes}", Toast.LENGTH_SHORT).show()
                            currentScreen = AppScreen.DETAILS
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var startAnim by remember { mutableStateOf(false) }

    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.7f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "splashScale"
    )

    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "splashAlpha"
    )

    LaunchedEffect(Unit) {
        startAnim = true
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 90)
        } catch (_: Exception) {}

        delay(1400.milliseconds)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scaleAnim)
                .alpha(alphaAnim)
        ) {
            Canvas(modifier = Modifier.size(86.dp)) {
                val w = size.width
                val h = size.height
                val markerPath = Path().apply {
                    moveTo(w * 0.5f, h * 0.15f)
                    cubicTo(w * 0.3f, h * 0.15f, w * 0.18f, h * 0.32f, w * 0.18f, h * 0.5f)
                    cubicTo(w * 0.18f, h * 0.72f, w * 0.5f, h * 0.95f, w * 0.5f, h * 0.95f)
                    cubicTo(w * 0.5f, h * 0.95f, w * 0.82f, h * 0.72f, w * 0.82f, h * 0.5f)
                    cubicTo(w * 0.82f, h * 0.32f, w * 0.7f, h * 0.15f, w * 0.5f, h * 0.15f)
                    close()
                }
                drawPath(markerPath, color = EmeraldPrimary)
                drawCircle(color = BgDark, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.45f))
                drawCircle(color = MintAccent, radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.45f))
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "DigiDrop",
                color = TextLight,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Маршруты и накладные",
                color = MintAccent,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun HubScreen(
    routeItems: List<RouteItem>,
    isFlashOn: Boolean,
    onFlashToggle: () -> Unit,
    onOpenRoute: () -> Unit,
    onOpenScanner: () -> Unit,
    onLoadFile: () -> Unit,
    onExportReport: () -> Unit,
    onCallDispatcher: () -> Unit,
    onResetRoute: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenUnknown: () -> Unit,
    onNavigateNext: () -> Unit
) {
    val pointGroups = routeItems.groupBy { it.storeName to it.address }
    val totalPoints = pointGroups.size
    val deliveredCount = pointGroups.values.count { group -> group.all { it.isDelivered } }
    val remainingBoxes = routeItems.filter { !it.isDelivered }.sumOf { it.totalBoxes }
    val progress = if (totalPoints == 0) 0f else deliveredCount.toFloat() / totalPoints

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgDark).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("DigiDrop", color = TextLight, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("Логистика • Сборка • Доставка", color = TextMuted, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(CardSurface).border(1.dp, CardBorder, CircleShape)
                        .bounceClick { onFlashToggle() },
                    contentAlignment = Alignment.Center
                ) { FlashlightVector(tint = if (isFlashOn) EmeraldPrimary else TextMuted) }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, Color(0xFF2D7BFF).copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .bounceClick(scaleDown = 0.97f) { onLoadFile() },
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F0FF)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFD2E5FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⇧", color = Color(0xFF1767D2), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Загрузить накладную", color = Color(0xFF15365D), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Excel XLSX • CSV • TSV", color = Color(0xFF46627F), fontSize = 12.sp)
                    }
                    Text("›", color = Color(0xFF1767D2), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                    .bounceClick(scaleDown = 0.985f) { onOpenRoute() },
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("АКТИВНЫЙ РЕЙС", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Сбросить", color = Color(0xFFD94B4B), fontSize = 12.sp,
                            modifier = Modifier.bounceClick { onResetRoute() })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom) {
                        Column {
                            Text("$deliveredCount / $totalPoints", color = Color(0xFF1767D2), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text("точек доставлено", color = TextMuted, fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$remainingBoxes", color = Color(0xFF16834A), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Text("мест осталось", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color(0xFFD9E2EA))) {
                        Box(Modifier.fillMaxWidth(progress).height(10.dp).clip(CircleShape).background(Color(0xFF34C759)))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(if (totalPoints == 0) "Загрузите накладную, чтобы начать рейс" else "Нажмите, чтобы открыть список точек",
                        color = TextMuted, fontSize = 12.sp)
                }
            }
        }
        item {
            Text("Рабочие инструменты", color = TextLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HubTile("Накладные", "Список точек и сдача", "▤", Color(0xFF2D7BFF), Modifier.weight(1f), onOpenRoute)
                HubTile("Сканер", "Приём и отгрузка", "▣", Color(0xFF168F78), Modifier.weight(1f), onOpenScanner)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HubTile("Файлы", "Другие форматы", "▱", Color(0xFF527DA8), Modifier.weight(1f), onLoadFile)
                HubTile("Маршрут", "Следующая точка", "⌖", Color(0xFF16834A), Modifier.weight(1f), onNavigateNext)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HubTile("Отчёты", "Сводка и экспорт", "▥", Color(0xFF16834A), Modifier.weight(1f), onOpenReports)
                HubTile("Настройки", "Режимы и параметры", "⚙", Color(0xFF527DA8), Modifier.weight(1f), onOpenSettings)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HubTile("История", "Состояние рейса", "◷", Color(0xFF527DA8), Modifier.weight(1f), onOpenHistory)
                HubTile("Неизвестные коды", "Проверить сканы", "?", Color(0xFFD18B26), Modifier.weight(1f), onOpenUnknown)
            }
        }
        item {
            HubTile("Экспорт отчёта", "CSV • сверка рейса", "⇩", Color(0xFF16834A), Modifier.fillMaxWidth(), onExportReport)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().bounceClick { onCallDispatcher() },
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE2ECF7)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("☎", color = Color(0xFF1767D2), fontSize = 25.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Связь с диспетчером", color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Быстрый звонок на склад", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
fun HubTile(
    title: String,
    subtitle: String,
    symbol: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(132.dp).border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .bounceClick(scaleDown = 0.97f) { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(symbol, color = accent, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Column {
                Text(title, color = TextLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

// --- БОЕВОЙ СКАНЕР ШТРИХКОДОВ НА CAMERAX + ML KIT ---
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun LiveCameraScannerScreen(
    isFlashOn: Boolean,
    onFlashToggle: () -> Unit,
    onBack: () -> Unit,
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasScanned by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "scannerLaser")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserPosition"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val barcodeScanner = BarcodeScanning.getClient()
                    val analysisExecutor = Executors.newSingleThreadExecutor()

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !hasScanned) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val rawValue = barcode.rawValue
                                        if (!rawValue.isNullOrBlank() && !hasScanned) {
                                            hasScanned = true
                                            onBarcodeScanned(rawValue)
                                            break
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Камера недоступна: " + e.message, Toast.LENGTH_SHORT).show()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FC)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "← Назад",
                        color = Color(0xFF1F2B36),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.bounceClick { onBack() }
                    )
                    Text("СКАНЕР", color = Color(0xFF586979), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(if (isFlashOn) Color(0xFFDDF5E7) else Color(0xFFE7ECF1))
                            .border(1.dp, if (isFlashOn) Color(0xFF34C759) else Color(0xFFC9D3DB), CircleShape)
                            .bounceClick { onFlashToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        FlashlightVector(tint = if (isFlashOn) Color(0xFF16834A) else Color(0xFF586979))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FC)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Наведите рамку на штрихкод накладной",
                    color = Color(0xFF1F2B36),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier.size(280.dp, 200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cornerLen = 36.dp.toPx()
                    val stroke = 4.dp.toPx()

                    drawLine(MintAccent, Offset(0f, 0f), Offset(cornerLen, 0f), strokeWidth = stroke)
                    drawLine(MintAccent, Offset(0f, 0f), Offset(0f, cornerLen), strokeWidth = stroke)

                    drawLine(MintAccent, Offset(w, 0f), Offset(w - cornerLen, 0f), strokeWidth = stroke)
                    drawLine(MintAccent, Offset(w, 0f), Offset(w, cornerLen), strokeWidth = stroke)

                    drawLine(MintAccent, Offset(0f, h), Offset(cornerLen, h), strokeWidth = stroke)
                    drawLine(MintAccent, Offset(0f, h), Offset(0f, h - cornerLen), strokeWidth = stroke)

                    drawLine(MintAccent, Offset(w, h), Offset(w - cornerLen, h), strokeWidth = stroke)
                    drawLine(MintAccent, Offset(w, h), Offset(w, h - cornerLen), strokeWidth = stroke)

                    val currentLaserY = h * laserY
                    drawLine(
                        color = EmeraldPrimary,
                        start = Offset(12.dp.toPx(), currentLaserY),
                        end = Offset(w - 12.dp.toPx(), currentLaserY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))


            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ScanResultsScreen(
    code: String,
    matches: List<RouteItem>,
    onBack: () -> Unit,
    onOpenPoint: (RouteItem) -> Unit
) {
    val groups = matches.groupBy { it.storeName to it.address }.toList()
    Column(
        modifier = Modifier.fillMaxSize().background(BgDark).padding(18.dp)
    ) {
        Text("← Назад к сканеру", color = MintAccent, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(18.dp))
        Text("Найдено несколько назначений", color = TextLight, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Код: $code", color = TextMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))
        Text("Обычный товарный штрихкод не указывает на одну конкретную коробку. Выбери нужную точку по данным накладной.",
            color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(groups, key = { it.first.first + "|" + it.first.second }) { entry ->
                val pointItems = entry.second
                val total = pointItems.sumOf { it.totalBoxes }
                val scanned = pointItems.sumOf { it.scannedBoxes }
                Card(
                    modifier = Modifier.fillMaxWidth().bounceClick { onOpenPoint(pointItems.first()) },
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(pointItems.first().storeName, color = TextLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(pointItems.first().address, color = TextMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("По накладным: $total", color = TextLight, fontSize = 14.sp)
                            Text("Осталось: ${(total - scanned).coerceAtLeast(0)}", color = MintAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${pointItems.size} строк(и) накладной • нажми для подробностей", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StoreGroupCard(
    items: List<RouteItem>,
    onClick: () -> Unit,
    onMapClick: () -> Unit,
    onCallClick: () -> Unit
) {
    val first = items.first()
    val total = items.sumOf { it.totalBoxes }
    val scanned = items.sumOf { it.scannedBoxes }
    val delivered = items.all { it.isDelivered }
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, if (delivered) CardBorder else EmeraldPrimary.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .bounceClick(scaleDown = 0.98f, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (delivered) "Доставлено" else "В пути", color = if (delivered) MintAccent else EmeraldPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(first.storeName, color = TextLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(first.address, color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("$total мест • ${items.size} строк накладных", color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Позиций: ${items.map { it.itemName }.distinct().size} • Учтено сканов: $scanned", color = TextMuted, fontSize = 11.sp)
                Text(items.take(2).joinToString(" · ") { it.itemName }, color = MintAccent, fontSize = 12.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(CardBorder).bounceClick { onMapClick() }, contentAlignment = Alignment.Center) { MapIconVector(MintAccent) }
                Box(Modifier.size(38.dp).clip(CircleShape).background(CardBorder).bounceClick { onCallClick() }, contentAlignment = Alignment.Center) { PhoneIconVector(MintAccent) }
                Text("Открыть", color = MintAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PointGroupScreen(
    items: List<RouteItem>,
    onBack: () -> Unit,
    onOpenInvoice: (RouteItem) -> Unit,
    onNavigate: () -> Unit,
    onCall: () -> Unit
) {
    val total = items.sumOf { it.totalBoxes }
    val scanned = items.sumOf { it.scannedBoxes }
    Column(Modifier.fillMaxSize().background(BgDark).padding(18.dp)) {
        Text("← Точки маршрута", color = MintAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(16.dp))
        Text(items.firstOrNull()?.storeName.orEmpty(), color = TextLight, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(items.firstOrNull()?.address.orEmpty(), color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Накладных/строк: ${items.size}", color = TextLight, fontSize = 14.sp)
            Text("Всего мест: $total", color = MintAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text("Учтено уникальных сканов: $scanned", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HubActionButton("Навигатор", "Открыть точку", CardSurface, TextLight, MintAccent, onNavigate)
            HubActionButton("Позвонить", "Связаться с точкой", CardSurface, TextLight, MintAccent, onCall)
        }
        Spacer(Modifier.height(14.dp))
        Text("Состав точки", color = TextLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { item ->
                Card(Modifier.fillMaxWidth().bounceClick { onOpenInvoice(item) },
                    colors = CardDefaults.cardColors(containerColor = CardSurface), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(item.itemName, color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Штрихкод: ${item.barcode}", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Мест: ${item.totalBoxes}", color = TextLight, fontSize = 13.sp)
                            Text(if (item.isDelivered) "Сдано" else "В пути", color = if (item.isDelivered) MintAccent else EmeraldPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Нажми, чтобы открыть накладную", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// --- ЭКРАН ДЕТАЛЬНОЙ НАКЛАДНОЙ (ЧТО ВНУТРИ) ---
@Composable
fun PointDetailsScreen(
    item: RouteItem,
    onBack: () -> Unit,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onToggleDelivered: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "← Точки маршрута",
                    color = MintAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.bounceClick { onBack() }
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (item.isDelivered) EmeraldPrimary else CardSurface)
                        .border(1.dp, if (item.isDelivered) MintAccent else CardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (item.isDelivered) "Сдано" else "В пути",
                        color = if (item.isDelivered) BgDark else MintAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = item.storeName,
                color = TextLight,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.address,
                color = TextMuted,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Состав накладной к выгрузке",
                        color = MintAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.itemName,
                        color = TextLight,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Отсканировано: ${item.scannedBoxes} / ${item.totalBoxes}",
                        color = if (item.scannedBoxes >= item.totalBoxes) Color(0xFF16834A) else Color(0xFF1767D2),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(Color(0xFFD9E2EA))) {
                        Box(
                            Modifier.fillMaxWidth((item.scannedBoxes.toFloat() / item.totalBoxes.coerceAtLeast(1)).coerceIn(0f, 1f))
                                .height(9.dp).clip(CircleShape).background(Color(0xFF34C759))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Всего мест к сдаче:", color = TextMuted, fontSize = 14.sp)
                        Text(
                            text = "" + item.totalBoxes + " мест",
                            color = EmeraldPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Штрихкод / ТТН:", color = TextMuted, fontSize = 14.sp)
                        Text(
                            text = item.barcode,
                            color = TextLight,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Инструкция к заезду",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.note,
                        color = TextLight,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(CardSurface)
                        .border(1.5.dp, MintAccent, RoundedCornerShape(26.dp))
                        .bounceClick { onCall() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Позвонить", color = MintAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(CardSurface)
                        .border(1.dp, CardBorder, RoundedCornerShape(26.dp))
                        .bounceClick { onNavigate() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Навигатор", color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(if (item.isDelivered) CardSurface else EmeraldPrimary)
                    .border(
                        1.dp,
                        if (item.isDelivered) CardBorder else EmeraldPrimary,
                        RoundedCornerShape(27.dp)
                    )
                    .bounceClick { onToggleDelivered() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (item.isDelivered) "Отменить сдачу (вернуть в путь)" else "Подтвердить сдачу точки",
                    color = if (item.isDelivered) TextMuted else BgDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- СПИСОК ТОЧЕК ---
@Composable
fun RouteListScreen(
    routeItems: List<RouteItem>,
    isFlashOn: Boolean,
    onFlashToggle: () -> Unit,
    onBackToHub: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenPointMap: (String) -> Unit,
    onCallClient: (String) -> Unit,
    onPointClick: (List<RouteItem>) -> Unit,
    onToggleItem: (RouteItem) -> Unit
) {
    var hideDelivered by remember { mutableStateOf(false) }

    val displayedGroups = remember(routeItems, hideDelivered) {
        (if (hideDelivered) routeItems.filter { !it.isDelivered } else routeItems)
            .groupBy { it.storeName to it.address }.toList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.bounceClick { onBackToHub() }
                ) {
                    Text(
                        text = "← Хаб",
                        color = MintAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Точки",
                        color = TextLight,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (hideDelivered) EmeraldPrimary else CardSurface)
                            .border(1.dp, if (hideDelivered) MintAccent else CardBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .bounceClick { hideDelivered = !hideDelivered }
                    ) {
                        Text(
                            text = if (hideDelivered) "Только в пути" else "Все точки",
                            color = if (hideDelivered) BgDark else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .bounceClick { onFlashToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AnimatedVisibility(
                                visible = isFlashOn,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Canvas(modifier = Modifier.size(46.dp)) {
                                    val path = Path().apply {
                                        moveTo(size.width * 0.5f, size.height * 0.35f)
                                        lineTo(size.width * 0.05f, size.height)
                                        lineTo(size.width * 0.95f, size.height)
                                        close()
                                    }
                                    drawPath(
                                        path = path,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                MintAccent.copy(alpha = 0.55f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isFlashOn) EmeraldPrimary.copy(alpha = 0.25f) else Color.Transparent)
                                .border(1.dp, if (isFlashOn) MintAccent else CardBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            FlashlightVector(tint = if (isFlashOn) MintAccent else TextMuted)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (displayedGroups.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
                                .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("▤", color = EmeraldPrimary, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    if (hideDelivered && routeItems.isNotEmpty()) "Все точки уже сданы" else "Рейс пока пуст",
                                    color = TextLight, fontSize = 19.sp, fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (hideDelivered && routeItems.isNotEmpty()) "Переключите фильтр, чтобы увидеть завершённые точки" else "Вернитесь на главный экран и загрузите накладную CSV или TSV",
                                    color = TextMuted, fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    items(displayedGroups, key = { it.first.first + "|" + it.first.second }) { group ->
                        StoreGroupCard(
                            items = group.second,
                            onClick = { onPointClick(group.second) },
                            onMapClick = { onOpenPointMap(group.second.first().address) },
                            onCallClick = { onCallClient(group.second.first().phone) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            BottomActionButtons(
                onRouteClick = {
                    val nextPoint = routeItems.firstOrNull { !it.isDelivered }?.address.orEmpty()
                    onOpenPointMap(nextPoint)
                },
                onScanClick = onOpenScanner
            )
        }
    }
}

@Composable
fun TopBarSection(
    title: String,
    isFlashOn: Boolean,
    onFlashToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextLight,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )

        Box(
            modifier = Modifier
                .size(56.dp)
                .bounceClick { onFlashToggle() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedVisibility(
                    visible = isFlashOn,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Canvas(modifier = Modifier.size(56.dp)) {
                        val path = Path().apply {
                            moveTo(size.width * 0.5f, size.height * 0.35f)
                            lineTo(size.width * 0.05f, size.height)
                            lineTo(size.width * 0.95f, size.height)
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MintAccent.copy(alpha = 0.55f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isFlashOn) EmeraldPrimary.copy(alpha = 0.25f) else Color.Transparent)
                    .border(1.dp, if (isFlashOn) MintAccent else CardBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                FlashlightVector(tint = if (isFlashOn) MintAccent else TextMuted)
            }
        }
    }
}

@Composable
fun HubActionButton(
    title: String,
    subtitle: String,
    buttonColor: Color,
    textColor: Color,
    borderColor: Color? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (borderColor != null) Modifier.border(1.5.dp, borderColor, RoundedCornerShape(18.dp)) else Modifier)
            .bounceClick { onClick() },
        colors = CardDefaults.cardColors(containerColor = buttonColor),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = if (textColor == BgDark) BgDark.copy(alpha = 0.8f) else TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun RouteItemCard(
    item: RouteItem,
    onCardClick: () -> Unit,
    onMapClick: () -> Unit,
    onCallClick: () -> Unit,
    onToggleStatus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (item.isDelivered) CardBorder else EmeraldPrimary.copy(alpha = 0.6f),
                RoundedCornerShape(18.dp)
            )
            .bounceClick(scaleDown = 0.98f) { onCardClick() },
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (item.isDelivered) "Доставлено" else "В пути",
                    color = if (item.isDelivered) MintAccent else EmeraldPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.storeName,
                    color = TextLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.address,
                    color = TextMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = item.itemName,
                    color = EmeraldPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = "Сканировано: ${item.scannedBoxes} / ${item.totalBoxes}",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Мест: " + item.totalBoxes + " • ТТН: " + item.barcode,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                BarcodeGraphic(isCompleted = item.isDelivered)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CardBorder)
                        .bounceClick(scaleDown = 0.88f) { onMapClick() },
                    contentAlignment = Alignment.Center
                ) {
                    MapIconVector(tint = MintAccent)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CardBorder)
                        .bounceClick(scaleDown = 0.88f) { onCallClick() },
                    contentAlignment = Alignment.Center
                ) {
                    PhoneIconVector(tint = MintAccent)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (item.isDelivered) EmeraldPrimary else Color.Transparent)
                        .border(2.dp, if (item.isDelivered) MintAccent else CardBorder, CircleShape)
                        .bounceClick(scaleDown = 0.86f) { onToggleStatus() },
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isDelivered) {
                        CheckmarkVector(tint = BgDark)
                    }
                }
            }
        }
    }
}

@Composable
fun MapIconVector(tint: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            cubicTo(w * 0.25f, h * 0.1f, w * 0.15f, h * 0.35f, w * 0.15f, h * 0.5f)
            cubicTo(w * 0.15f, h * 0.75f, w * 0.5f, h * 0.95f, w * 0.5f, h * 0.95f)
            cubicTo(w * 0.5f, h * 0.95f, w * 0.85f, h * 0.75f, w * 0.85f, h * 0.5f)
            cubicTo(w * 0.85f, h * 0.35f, w * 0.75f, h * 0.1f, w * 0.5f, h * 0.1f)
            close()
        }
        drawPath(path, color = tint)
        drawCircle(color = CardSurface, radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.45f))
    }
}

@Composable
fun PhoneIconVector(tint: Color) {
    Canvas(modifier = Modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.15f)
            lineTo(w * 0.4f, h * 0.15f)
            lineTo(w * 0.48f, h * 0.38f)
            lineTo(w * 0.35f, h * 0.48f)
            cubicTo(w * 0.42f, h * 0.62f, w * 0.58f, h * 0.78f, w * 0.72f, h * 0.85f)
            lineTo(w * 0.82f, h * 0.72f)
            lineTo(w * 1.05f, h * 0.8f)
            lineTo(w * 1.05f, h * 1.0f)
            cubicTo(w * 0.45f, h * 1.05f, w * 0.15f, h * 0.75f, w * 0.2f, h * 0.15f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun FlashlightVector(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val headPath = Path().apply {
            moveTo(w * 0.25f, h * 0.15f)
            lineTo(w * 0.75f, h * 0.15f)
            lineTo(w * 0.65f, h * 0.45f)
            lineTo(w * 0.35f, h * 0.45f)
            close()
        }
        drawPath(headPath, tint)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.35f, h * 0.45f),
            size = Size(w * 0.3f, h * 0.45f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
    }
}

@Composable
fun CheckmarkVector(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.5f)
            lineTo(w * 0.45f, h * 0.75f)
            lineTo(w * 0.85f, h * 0.25f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun BarcodeGraphic(isCompleted: Boolean) {
    val barColor = if (isCompleted) TextMuted.copy(alpha = 0.35f) else MintAccent.copy(alpha = 0.7f)
    Row(
        modifier = Modifier
            .width(130.dp)
            .height(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val weights = listOf(3, 1, 4, 2, 1, 5, 2, 3, 1, 4, 2, 3, 1)
        for (weight in weights) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(weight.dp)
                    .background(barColor)
            )
        }
    }
}

@Composable
fun BottomActionButtons(
    onRouteClick: () -> Unit,
    onScanClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(EmeraldPrimary)
                .bounceClick { onRouteClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Маршрут",
                color = BgDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(CardSurface)
                .border(1.5.dp, MintAccent, RoundedCornerShape(26.dp))
                .bounceClick { onScanClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Сканировать",
                color = MintAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}