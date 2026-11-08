@file:Suppress("UsePropertyAccessSyntax")

package com.example.qrhelper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import android.location.Geocoder
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.qrhelper.LogisticsRepository
import com.example.qrhelper.data.ProductEntity
import com.example.qrhelper.RouteHistorySnapshot
import com.example.qrhelper.domain.DeliveryPointSnapshot
import com.example.qrhelper.domain.DeliveryPointStatus
import com.example.qrhelper.domain.PointFilter
import com.example.qrhelper.domain.RouteProgress
import com.example.qrhelper.domain.RouteStop
import com.example.qrhelper.domain.RouteOrderOptimizer
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import java.io.InputStream
import java.io.File
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
    val scannedBoxes: Int = 0,
    val ownerName: String = "",
    val workingHours: String = "",
    val cashNote: String = "",
    val extraComment: String = "",
    val photoDocUri: String = "",
    val signaturePath: String = "",
    val discrepancyQty: Int = 0,
    val discrepancyReason: String? = null
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

internal fun parseXlsx(input: InputStream): List<RouteItem> {
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
    val sheetNames = entries.keys
        .filter { it.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml")) }
        .sortedBy { Regex("sheet([0-9]+)").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
    if (sheetNames.isEmpty()) return emptyList()
    val sheets = sheetNames.map { sheetName ->
        readXlsxXml(entries.getValue(sheetName).toString(Charsets.UTF_8), shared)
    }
    return parseXlsxSheets(sheets)
}

internal fun parseXlsxSheets(sheets: List<List<List<String>>>): List<RouteItem> =
    sheets.flatMap { rows ->
        val tsv = rows.joinToString("\n") { row ->
            row.joinToString("\t") { value -> "\"" + value.replace("\"", "\"\"") + "\"" }
        }
        parseManifest(tsv)
    }

internal fun parseManifest(text: String): List<RouteItem> {
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
        "phone" to setOf("phone", "телефон", "контакт", "мобильный", "номертелефона"),
        "owner" to setOf("owner", "ип", "фио", "предприниматель", "владелец", "контактноелицо"),
        "hours" to setOf("hours", "времяработы", "графикработы", "режимработы", "работает"),
        "cash" to setOf("cash", "наличные", "деньги", "забратьденьги", "оплата", "инкассация"),
        "note" to setOf("note", "комментарий", "примечание", "инструкция", "условиядоставки"),
        "extra" to setOf("extra", "дополнительно", "допкомментарий"),
        "lat" to setOf("lat", "latitude", "широта"),
        "lon" to setOf("lon", "lng", "longitude", "долгота")
    )
    val normalized = first.map(::normalize)
    val hasHeader = normalized.any { it in aliases.values.flatten() }
    val columns = if (hasHeader) aliases.mapValues { (_, names) -> normalized.indexOfFirst { it in names } } else
        mapOf("barcode" to 0, "store" to 1, "address" to 2, "item" to 3, "boxes" to 4, "phone" to 5, "note" to 6, "lat" to 7, "lon" to 8, "owner" to 9, "hours" to 10, "cash" to 11, "extra" to 12)
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
            latitude = latitude, longitude = longitude,
            ownerName = value(row, "owner"),
            workingHours = value(row, "hours"),
            cashNote = value(row, "cash"),
            extraComment = value(row, "extra")
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
        // На этапе проверки сохраняем исходную последовательность из маршрутного листа.
        resolved
    }

const val DISPATCHER_PHONE = "+74842998877"

enum class AppScreen {
    SPLASH,
    HUB,
    MORE_MENU,
    INVOICE_IMPORT,
    ROUTE_LIST,
    ROUTE_EDITOR,
    DETAILS,
    SCANNER,
    SCAN_RESULTS,
    POINT_DETAILS,
    REPORTS,
    SETTINGS,
    HISTORY,
    PRODUCTS,
    UNKNOWN_CODES,
    PHOTO_ROUTE,
    OFFLINE_MAP
}

// Тёплая песочно-бежeвая палитра DigiDrop.
val BgDark = Color(0xFFF1EBDD)
val CardSurface = Color(0xFFFBF7EF)
val CardBorder = Color(0xFFD8CBB8)
val EmeraldPrimary = Color(0xFF78866B)
val MintAccent = Color(0xFF80664E)
val TextLight = Color(0xFF302B25)
val TextMuted = Color(0xFF746B60)

@Composable
fun Modifier.bounceClick(
    scaleDown: Float = 0.975f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "bounceScale"
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null
        ) { onClick() }
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
    var offlineMapRefresh by remember { mutableStateOf(0) }
    var photoRouteInitialAction by remember { mutableStateOf<String?>(null) }
    val windowContext = LocalContext.current
    SideEffect {
        val activity = windowContext as? Activity
        activity?.window?.let { window ->
            if (currentScreen == AppScreen.SPLASH) {
                window.statusBarColor = Color(0xFF04142D).toArgb()
                window.decorView.systemUiVisibility = 0
            } else {
                window.statusBarColor = BgDark.toArgb()
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }
    var previousScreen by remember { mutableStateOf(AppScreen.HUB) }
    var selectedItem by remember { mutableStateOf<RouteItem?>(null) }
    var selectedPointItems by remember { mutableStateOf<List<RouteItem>>(emptyList()) }
    var scanMatches by remember { mutableStateOf<List<RouteItem>>(emptyList()) }
    val context = LocalContext.current
    val importScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("digitdrop_prefs", Context.MODE_PRIVATE) }
    var settingsOffline by remember { mutableStateOf(prefs.getBoolean("offline_mode", true)) }
    var settingsAutoSave by remember { mutableStateOf(prefs.getBoolean("auto_save", true)) }
    var settingsCompact by remember { mutableStateOf(prefs.getBoolean("compact_mode", false)) }
    var sessionScanCodes by remember {
        mutableStateOf(prefs.getStringSet("scan_codes", emptySet())?.toSet().orEmpty())
    }
    var appendInvoiceToRoute by remember { mutableStateOf(false) }
    var unknownCodes by remember { mutableStateOf(prefs.getStringSet("unknown_codes", emptySet<String>())?.toList().orEmpty()) }

    var cameraXCamera by remember { mutableStateOf<Camera?>(null) }
    var isFlashlightOn by remember { mutableStateOf(false) }

    fun toggleFlashlight() {
        val activeCamera = cameraXCamera
        if (activeCamera == null) {
            Toast.makeText(context, "Камера ещё запускается", Toast.LENGTH_SHORT).show()
            return
        }
        val newState = !isFlashlightOn
        activeCamera.cameraControl.enableTorch(newState)
            .addListener({
                isFlashlightOn = newState
            }, ContextCompat.getMainExecutor(context))
    }

    var archivedRoutes by remember { mutableStateOf(emptyList<RouteHistorySnapshot>()) }
    var productCatalog by remember { mutableStateOf(emptyList<ProductEntity>()) }

    var routeItems by remember {
        mutableStateOf(
            parseManifest(prefs.getString("active_manifest", "").orEmpty()).map { item ->
                val savedDelivered = prefs.getBoolean("delivered_" + routeItemKey(item), item.isDelivered)
                val savedScanned = prefs.getInt("scanned_" + routeItemKey(item), item.scannedBoxes)
                item.copy(isDelivered = savedDelivered, scannedBoxes = savedScanned)
            }
        )
    }

    LaunchedEffect(Unit) {
        runCatching {
            val repository = LogisticsRepository(context)
            val restored = repository.activeRouteItems()
            if (restored.isNotEmpty()) {
                routeItems = restored
                val manifest = restored.joinToString("\n") { item ->
                    listOf(
                        item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(),
                        item.phone, item.note, item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(),
                        item.ownerName, item.workingHours, item.cashNote, item.extraComment
                    ).joinToString("\t") { it.replace("\t", " ") }
                }
                prefs.edit().putString("active_manifest", manifest).apply()
            } else if (routeItems.isNotEmpty()) {
                repository.replaceActiveRoute(routeItems)
            }
        }.onFailure { error ->
            Toast.makeText(context, "Локальная база пока недоступна: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun updateItemStatus(item: RouteItem) {
        val nextStatus = !item.isDelivered
        if (settingsAutoSave) prefs.edit().putBoolean("delivered_" + routeItemKey(item), nextStatus).apply()
        val updatedItems = routeItems.map { current ->
            if (routeItemKey(current) == routeItemKey(item)) current.copy(isDelivered = nextStatus) else current
        }
        routeItems = updatedItems
        importScope.launch {
            runCatching { persistProgressIfEnabled(settingsAutoSave) { LogisticsRepository(context).syncProgress(updatedItems) } }
                .onFailure { error ->
                    Toast.makeText(context, "Не удалось сохранить статус в базе: ${error.message}", Toast.LENGTH_SHORT).show()
                }
        }
        if (selectedItem?.let(::routeItemKey) == routeItemKey(item)) {
            selectedItem = selectedItem?.copy(isDelivered = nextStatus)
        }
    }

    fun updateItemDiscrepancy(item: RouteItem, quantity: Int, reason: String) {
        val updatedItems = routeItems.map { current ->
            if (routeItemKey(current) == routeItemKey(item)) {
                current.copy(discrepancyQty = quantity.coerceAtLeast(0), discrepancyReason = reason)
            } else current
        }
        routeItems = updatedItems
        selectedItem = updatedItems.firstOrNull { routeItemKey(it) == routeItemKey(item) }
        importScope.launch {
            runCatching { persistProgressIfEnabled(settingsAutoSave) { LogisticsRepository(context).syncProgress(updatedItems) } }
                .onFailure { error ->
                    Toast.makeText(context, "Не удалось сохранить расхождение: ${error.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    fun archiveCurrentRoute() {
        if (routeItems.isEmpty()) {
            Toast.makeText(context, "Нет активного рейса для архивации", Toast.LENGTH_SHORT).show()
            return
        }
        importScope.launch {
            runCatching {
                LogisticsRepository(context).archiveActiveRoute()
                val editor = prefs.edit().remove("active_manifest").remove("scan_codes")
                routeItems.forEach { item ->
                    editor.remove("delivered_" + routeItemKey(item))
                    editor.remove("scanned_" + routeItemKey(item))
                }
                editor.apply()
                routeItems = emptyList()
                selectedItem = null
                selectedPointItems = emptyList()
                currentScreen = AppScreen.HUB
                Toast.makeText(context, "Рейс отправлен в историю", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, "Не удалось архивировать рейс: ${error.message}", Toast.LENGTH_LONG).show()
            }
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
        editor.apply()
        sessionScanCodes = emptySet()
        val resetItems = routeItems.map {
            it.copy(isDelivered = false, scannedBoxes = 0, discrepancyQty = 0, discrepancyReason = null)
        }
        routeItems = resetItems
        importScope.launch { runCatching { LogisticsRepository(context).syncProgress(resetItems) } }
        Toast.makeText(context, "Статусы точек сброшены", Toast.LENGTH_SHORT).show()
    }

    val reportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val header = "Штрихкод;Точка;Адрес;Товар;Всего мест;Отсканировано;Расхождение;Причина;Осталось;Статус\n"
                val rows = routeItems.joinToString("\n") { item ->
                    listOf(
                        item.barcode, item.storeName, item.address, item.itemName,
                        item.totalBoxes.toString(), item.scannedBoxes.toString(),
                        item.discrepancyQty.toString(), item.discrepancyReason.orEmpty(),
                        (item.totalBoxes - item.scannedBoxes - item.discrepancyQty).coerceAtLeast(0).toString(),
                        when {
                            item.discrepancyQty > 0 -> "Расхождение"
                            item.isDelivered -> "Доставлено"
                            item.scannedBoxes > 0 -> "В работе"
                            else -> "Ожидает"
                        }
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

    val mapFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importScope.launch {
                try {
                    withContext(Dispatchers.IO) { OfflineMapStore.import(context, uri) }
                    offlineMapRefresh++
                    Toast.makeText(context, "Офлайн-карта импортирована", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    Toast.makeText(context, error.message ?: "Ошибка импорта карты", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val photoEvidenceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val item = selectedItem
        if (uri != null && item != null) {
            importScope.launch {
                runCatching {
                    val directory = File(context.filesDir, "delivery-evidence").apply { mkdirs() }
                    val target = File(directory, "photo_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Не удалось прочитать выбранное фото")
                    val updated = routeItems.map {
                        if (routeItemKey(it) == routeItemKey(item)) it.copy(photoDocUri = target.absolutePath) else it
                    }
                    routeItems = updated
                    selectedItem = updated.firstOrNull { routeItemKey(it) == routeItemKey(item) }
                    LogisticsRepository(context).savePhotoForPoint(item.storeName, item.address, target.absolutePath)
                    Toast.makeText(context, "Фото доказательства сохранено", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Не удалось сохранить фото: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val reportExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { ReportExporter.writeXlsx(it, routeItems) }
                ?: error("Не удалось открыть файл для записи")
            Toast.makeText(context, "Excel-отчёт сохранён", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(context, "Ошибка Excel-экспорта: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    val reportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { ReportExporter.writePdf(it, routeItems) }
                ?: error("Не удалось открыть файл для записи")
            Toast.makeText(context, "PDF-отчёт сохранён", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(context, "Ошибка PDF-экспорта: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) importScope.launch {
            runCatching {
                val catalog = LogisticsRepository(context).allProducts().map {
                    ProductCatalogRecord(barcode = it.barcode, productName = it.productName)
                }
                val snapshot = BackupSnapshot(
                    routeItems = routeItems,
                    offlineMode = settingsOffline,
                    autoSave = settingsAutoSave,
                    compactMode = settingsCompact,
                    unknownCodes = unknownCodes,
                    scanCodes = sessionScanCodes.toList(),
                    catalog = catalog
                )
                context.contentResolver.openOutputStream(uri)?.use {
                    BackupManager.writeBackup(it, snapshot, context.filesDir)
                } ?: error("Не удалось открыть файл резервной копии")
                Toast.makeText(context, "Резервная копия сохранена", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, "Ошибка резервного копирования: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) importScope.launch {
            runCatching {
                val snapshot = context.contentResolver.openInputStream(uri)?.use {
                    BackupManager.readBackup(it, context.filesDir)
                } ?: error("Не удалось прочитать резервную копию")
                routeItems = snapshot.routeItems
                settingsOffline = snapshot.offlineMode
                settingsAutoSave = snapshot.autoSave
                settingsCompact = snapshot.compactMode
                unknownCodes = snapshot.unknownCodes
                sessionScanCodes = snapshot.scanCodes.toSet()
                val manifest = snapshot.routeItems.joinToString("\n") { item ->
                    listOf(
                        item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(),
                        item.phone, item.note, item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(),
                        item.ownerName, item.workingHours, item.cashNote, item.extraComment
                    ).joinToString("\t") { it.replace("\t", " ") }
                }
                prefs.edit()
                    .putString("active_manifest", manifest)
                    .putBoolean("offline_mode", snapshot.offlineMode)
                    .putBoolean("auto_save", snapshot.autoSave)
                    .putBoolean("compact_mode", snapshot.compactMode)
                    .putStringSet("unknown_codes", snapshot.unknownCodes.toSet())
                    .putStringSet("scan_codes", snapshot.scanCodes.toSet())
                    .apply()
                val repository = LogisticsRepository(context)
                repository.replaceActiveRoute(snapshot.routeItems)
                repository.restoreProducts(snapshot.catalog)
                Toast.makeText(context, "Резервная копия восстановлена", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, "Не удалось восстановить копию: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) importScope.launch {
            try {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val uriText = uri.toString().lowercase()
                val isPdf = mime.contains("pdf") || uriText.endsWith(".pdf")
                val isExcel = mime.contains("spreadsheet") || uriText.endsWith(".xlsx")
                val imported = withContext(Dispatchers.IO) {
                    if (isPdf) {
                        PdfInvoiceImporter.parse(context, uri) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            if (isExcel) parseXlsx(inputStream)
                            else parseManifest(inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                        }.orEmpty()
                    }
                }
                if (imported.isNotEmpty()) {
                    if (!appendInvoiceToRoute) {
                        sessionScanCodes = emptySet()
                        prefs.edit().remove("scan_codes").apply()
                    }
                    val withProgress = if (appendInvoiceToRoute && routeItems.isNotEmpty()) {
                        mergeInvoiceRows(routeItems, imported)
                    } else {
                        mergeInvoiceRows(emptyList(), imported).map { item ->
                            item.copy(
                                isDelivered = prefs.getBoolean("delivered_" + routeItemKey(item), false),
                                scannedBoxes = prefs.getInt("scanned_" + routeItemKey(item), 0)
                            )
                        }
                    }
                    currentScreen = AppScreen.ROUTE_LIST
                    val ordered = if (settingsOffline) withProgress else geocodeRoute(context, withProgress)
                    routeItems = ordered
                    val manifest = ordered.joinToString("\n") { item ->
                        listOf(
                            item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(),
                            item.phone, item.note, item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(),
                            item.ownerName, item.workingHours, item.cashNote, item.extraComment
                        ).joinToString("\t") { it.replace("\t", " ") }
                    }
                    prefs.edit().putString("active_manifest", manifest).apply()
                    LogisticsRepository(context).replaceActiveRoute(ordered)
                    val optimized = ordered.map(::routeItemKey) != withProgress.map(::routeItemKey)
                    val geocoded = ordered.count { it.latitude != null && it.longitude != null }
                    Toast.makeText(
                        context,
                        "Загружено позиций: ${ordered.size}" + when {
                            appendInvoiceToRoute -> " • накладная объединена с текущим рейсом"
                            optimized -> " • точки упорядочены примерно по расстоянию"
                            geocoded == ordered.size && ordered.size > 1 -> " • координаты найдены, порядок уже близкий"
                            else -> " • порядок из накладной сохранён"
                        },
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Не удалось найти строки накладной. Нужны штрихкод, точка/адрес и количество мест.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (ex: Exception) {
                Toast.makeText(context, "Ошибка чтения: " + ex.message, Toast.LENGTH_LONG).show()
            } finally {
                appendInvoiceToRoute = false
            }
        } else {
            appendInvoiceToRoute = false
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
            Toast.makeText(context, "У точки нет адреса или координат", Toast.LENGTH_SHORT).show()
            return
        }

        // DigiDrop хранит и управляет маршрутом. Яндекс Навигатор получает только
        // назначение текущей точки и отвечает за голосовое ведение до неё.
        val navigatorUri = if (latitude != null && longitude != null) {
            Uri.parse("yandexnavi://build_route_on_map")
                .buildUpon()
                .appendQueryParameter("lat_to", latitude.toString())
                .appendQueryParameter("lon_to", longitude.toString())
                .build()
        } else {
            Uri.parse("yandexnavi://map_search")
                .buildUpon()
                .appendQueryParameter("text", address)
                .build()
        }
        val navigatorIntent = Intent(Intent.ACTION_VIEW, navigatorUri)
            .setPackage("ru.yandex.yandexnavi")

        try {
            context.startActivity(navigatorIntent)
        } catch (_: Exception) {
            // Если Навигатор не установлен/не доступен, пробуем универсальную ссылку Яндекса.
            try {
                val fallback = if (latitude != null && longitude != null) {
                    "https://yandex.ru/maps/?rtext=~$latitude,$longitude&rtt=auto"
                } else {
                    "https://yandex.ru/maps/?rtext=~" + Uri.encode(address) + "&rtt=auto"
                }
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallback)))
            } catch (_: Exception) {
                Toast.makeText(context, "Яндекс Навигатор недоступен. Точка сохранена в DigiDrop.", Toast.LENGTH_LONG).show()
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
            AppScreen.ROUTE_EDITOR -> AppScreen.ROUTE_LIST
            AppScreen.OFFLINE_MAP -> AppScreen.ROUTE_LIST
            AppScreen.HUB -> AppScreen.HUB
            AppScreen.MORE_MENU -> AppScreen.HUB
            AppScreen.INVOICE_IMPORT -> AppScreen.HUB
            AppScreen.REPORTS, AppScreen.SETTINGS, AppScreen.HISTORY, AppScreen.PRODUCTS, AppScreen.UNKNOWN_CODES, AppScreen.PHOTO_ROUTE -> AppScreen.HUB
            AppScreen.SPLASH -> AppScreen.HUB
        }
    }

    Crossfade(targetState = currentScreen, animationSpec = tween(220), label = "screenTransition") { screen ->
    when (screen) {
        AppScreen.SPLASH -> {
            SplashScreen(onFinished = { currentScreen = AppScreen.HUB })
        }
        AppScreen.INVOICE_IMPORT -> {
            InvoiceLandingScreen(
                onBack = { currentScreen = AppScreen.HUB },
                hasActiveRoute = routeItems.isNotEmpty(),
                onTakePhoto = { photoRouteInitialAction = "camera"; currentScreen = AppScreen.PHOTO_ROUTE },
                onChoosePhoto = { photoRouteInitialAction = "gallery"; currentScreen = AppScreen.PHOTO_ROUTE },
                onLoadInvoice = {
                    appendInvoiceToRoute = false
                    filePickerLauncher.launch(arrayOf("text/*", "application/csv", "application/pdf", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                },
                onAppendInvoice = {
                    appendInvoiceToRoute = routeItems.isNotEmpty()
                    filePickerLauncher.launch(arrayOf("text/*", "application/csv", "application/pdf", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                },
                onPhotoRoute = { currentScreen = AppScreen.PHOTO_ROUTE }
            )
        }
        AppScreen.HUB -> {
            HubScreen(
                onOpenMenu = { currentScreen = AppScreen.MORE_MENU },
                onOpenScanner = { openScanner() },
                onLoadInvoice = { currentScreen = AppScreen.INVOICE_IMPORT }
            )
        }
        AppScreen.MORE_MENU -> {
            MoreMenuScreen(
                onBack = { currentScreen = AppScreen.HUB },
                onOpenRoute = { currentScreen = AppScreen.ROUTE_LIST },
                onOpenReports = { currentScreen = AppScreen.REPORTS },
                onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                onOpenHistory = {
                    importScope.launch {
                        archivedRoutes = runCatching { LogisticsRepository(context).history() }
                            .getOrElse { error ->
                                Toast.makeText(context, "Не удалось загрузить историю: ${error.message}", Toast.LENGTH_LONG).show()
                                emptyList()
                            }
                        currentScreen = AppScreen.HISTORY
                    }
                },
                onOpenProducts = {
                    importScope.launch {
                        productCatalog = runCatching { LogisticsRepository(context).searchProducts("") }.getOrElse { emptyList() }
                        currentScreen = AppScreen.PRODUCTS
                    }
                },
                onOpenUnknown = {
                    unknownCodes = prefs.getStringSet("unknown_codes", emptySet())?.toList().orEmpty()
                    currentScreen = AppScreen.UNKNOWN_CODES
                },
                onExport = { reportLauncher.launch("DigiDrop_report.csv") },
                onCallDispatcher = { makePhoneCall(DISPATCHER_PHONE) },
                onResetRoute = { resetRouteProgress() },
                onNavigateNext = {
                    val nextItem = nextPendingPoint(routeItems)?.firstOrNull()
                    if (nextItem == null) Toast.makeText(context, "Сначала загрузите накладную", Toast.LENGTH_SHORT).show()
                    else launchNavigation(nextItem.address, nextItem.latitude, nextItem.longitude)
                }
            )
        }
        AppScreen.PHOTO_ROUTE -> {
            PhotoRoutePlannerScreen(
                initialAction = photoRouteInitialAction,
                onBack = { photoRouteInitialAction = null; currentScreen = AppScreen.HUB },
                onApplyRoute = { imported ->
                    sessionScanCodes = emptySet()
                    prefs.edit().remove("scan_codes").apply()
                    val content = imported.joinToString("\n") { item ->
                        listOf(item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(), item.phone, item.note,
                            item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(), item.ownerName, item.workingHours,
                            item.cashNote, item.extraComment).joinToString("\t") { it.replace("\t", " ") }
                    }
                    prefs.edit().putString("active_manifest", content).apply()
                    routeItems = imported
                    importScope.launch {
                        runCatching { LogisticsRepository(context).replaceActiveRoute(imported) }
                            .onFailure { error ->
                                Toast.makeText(context, "Маршрут добавлен, но база не обновлена: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    currentScreen = AppScreen.ROUTE_LIST
                    Toast.makeText(context, "Маршрут добавлен: ${imported.size} точек", Toast.LENGTH_LONG).show()
                }
            )
        }
        AppScreen.OFFLINE_MAP -> {
            OfflineMapScreen(
                routeItems = routeItems,
                refreshKey = offlineMapRefresh,
                onBack = { currentScreen = AppScreen.ROUTE_LIST },
                onImportMap = { mapFileLauncher.launch(arrayOf("*/*")) },
                onGeocodeMissing = {
                    importScope.launch {
                        val resolved = geocodeRoute(context, routeItems)
                        routeItems = resolved
                        val saved = resolved.joinToString("\n") { item ->
                            listOf(
                                item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(),
                                item.phone, item.note, item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(),
                                item.ownerName, item.workingHours, item.cashNote, item.extraComment
                            ).joinToString("\t") { it.replace("\t", " ") }
                        }
                        prefs.edit().putString("active_manifest", saved).apply()
                        val found = resolved.count { it.latitude != null && it.longitude != null }
                        Toast.makeText(context, "Координаты определены: $found из ${resolved.size}", Toast.LENGTH_LONG).show()
                    }
                },
                onSelectPoint = { items ->
                    selectedPointItems = items
                    currentScreen = AppScreen.POINT_DETAILS
                }
            )
        }
        AppScreen.ROUTE_LIST -> {
            RouteListScreen(
                routeItems = routeItems,
                compactMode = settingsCompact,
                onBackToHub = { currentScreen = AppScreen.HUB },
                onOpenScanner = { openScanner() },
                onEditRoute = { currentScreen = AppScreen.ROUTE_EDITOR },
                onOpenOfflineMap = { currentScreen = AppScreen.OFFLINE_MAP },
                onRebuildRoute = {
                    val reordered = rebuildRemainingRoute(routeItems)
                    if (reordered.map(::routeItemKey) == routeItems.map(::routeItemKey)) {
                        Toast.makeText(context, "Маршрут уже оптимален или не хватает координат точек", Toast.LENGTH_LONG).show()
                    } else {
                        routeItems = reordered
                        importScope.launch { runCatching { LogisticsRepository(context).syncOrder(reordered) } }
                        val saved = reordered.joinToString("\n") { item ->
                            listOf(
                                item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(),
                                item.phone, item.note, item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(),
                                item.ownerName, item.workingHours, item.cashNote, item.extraComment
                            ).joinToString("\t") { it.replace("\t", " ") }
                        }
                        prefs.edit().putString("active_manifest", saved).apply()
                        Toast.makeText(context, "Оставшийся маршрут перестроен", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenPointMap = { address -> launchNavigation(address) },
                onCallClient = { phone -> makePhoneCall(phone) },
                onPointClick = { pointItems ->
                    selectedPointItems = pointItems
                    currentScreen = AppScreen.POINT_DETAILS
                },
                onToggleItem = { item -> updateItemStatus(item) },
                onMoveGroup = { from, to ->
                    val groups = routeItems.groupBy { it.storeName to it.address }.toList().toMutableList()
                    if (from in groups.indices && to in groups.indices) {
                        val moved = groups.removeAt(from)
                        groups.add(to, moved)
                        val reorderedItems = groups.flatMap { it.second }
                        routeItems = reorderedItems
                        importScope.launch { runCatching { LogisticsRepository(context).syncOrder(reorderedItems) } }
                        val saved = routeItems.joinToString("\n") { item ->
                            listOf(item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(),
                                item.phone, item.note, item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(),
                                item.ownerName, item.workingHours, item.cashNote, item.extraComment)
                                .joinToString("\t") { it.replace("\t", " ") }
                        }
                        prefs.edit().putString("active_manifest", saved).apply()
                    }
                }
            )
        }
        AppScreen.ROUTE_EDITOR -> {
            RouteEditorScreen(
                routeItems = routeItems,
                onBack = { currentScreen = AppScreen.ROUTE_LIST },
                onSave = { editedItems ->
                    importScope.launch {
                        runCatching { LogisticsRepository(context).updateActiveRoute(editedItems) }
                            .onSuccess {
                                routeItems = editedItems
                                val manifest = editedItems.joinToString("\n") { item ->
                                    listOf(
                                        item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(),
                                        item.phone, item.note, item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(),
                                        item.ownerName, item.workingHours, item.cashNote, item.extraComment
                                    ).joinToString("\t") { it.replace("\t", " ") }
                                }
                                prefs.edit().putString("active_manifest", manifest).remove("scan_codes").apply()
                                currentScreen = AppScreen.ROUTE_LIST
                                Toast.makeText(context, "Маршрут сохранён", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { error ->
                                Toast.makeText(context, "Не удалось сохранить маршрут: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                }
            )
        }
        AppScreen.DETAILS -> {
            selectedItem?.let { item ->
                PointDetailsScreen(
                    item = item,
                    onBack = { currentScreen = AppScreen.ROUTE_LIST },
                    onNavigate = { launchNavigation(item.address, item.latitude, item.longitude) },
                    onCall = { makePhoneCall(item.phone) },
                    onToggleDelivered = { updateItemStatus(item) },
                    onSetDiscrepancy = { quantity, reason -> updateItemDiscrepancy(item, quantity, reason) },
                    onAddPhoto = { photoEvidenceLauncher.launch("image/*") },
                    onCaptureSignature = { bitmap ->
                        importScope.launch {
                            runCatching {
                                val directory = File(context.filesDir, "delivery-evidence").apply { mkdirs() }
                                val target = File(directory, "signature_${System.currentTimeMillis()}.png")
                                target.outputStream().use { output ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                                }
                                val updated = routeItems.map {
                                    if (routeItemKey(it) == routeItemKey(item)) it.copy(signaturePath = target.absolutePath) else it
                                }
                                routeItems = updated
                                selectedItem = updated.firstOrNull { routeItemKey(it) == routeItemKey(item) }
                                LogisticsRepository(context).saveSignatureForPoint(item.storeName, item.address, target.absolutePath)
                                Toast.makeText(context, "Подпись получателя сохранена", Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                Toast.makeText(context, "Не удалось сохранить подпись: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
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
                onCall = { makePhoneCall(selectedPointItems.firstOrNull()?.phone.orEmpty()) },
                onClosePoint = {
                    val point = selectedPointItems.firstOrNull()
                    if (point != null) importScope.launch {
                        val closed = runCatching {
                            LogisticsRepository(context).closePoint(point.storeName, point.address)
                        }.getOrElse { error ->
                            Toast.makeText(context, "Ошибка проверки точки: ${error.message}", Toast.LENGTH_LONG).show()
                            false
                        }
                        if (closed) {
                            val updated = routeItems.map {
                                if (it.storeName == point.storeName && it.address == point.address) it.copy(isDelivered = true) else it
                            }
                            routeItems = updated
                            selectedPointItems = updated.filter { it.storeName == point.storeName && it.address == point.address }
                            val editor = prefs.edit()
                            selectedPointItems.forEach { editor.putBoolean("delivered_" + routeItemKey(it), true) }
                            editor.apply()
                            Toast.makeText(context, "Точка закрыта и отмечена как сданная", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Точку нельзя закрыть: проверь сканы и причины расхождений", Toast.LENGTH_LONG).show()
                        }
                    }
                }
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
            ReportsScreen(
                routeItems,
                onBack = { currentScreen = AppScreen.HUB },
                onExport = { reportLauncher.launch("DigiDrop_report.csv") },
                onExportExcel = { reportExcelLauncher.launch("DigiDrop_report.xlsx") },
                onExportPdf = { reportPdfLauncher.launch("DigiDrop_report.pdf") }
            )
        }
        AppScreen.SETTINGS -> {
            SettingsScreen(
                offlineMode = settingsOffline, autoSave = settingsAutoSave, compactMode = settingsCompact,
                onBack = { currentScreen = AppScreen.HUB },
                onOfflineChange = { settingsOffline = it; prefs.edit().putBoolean("offline_mode", it).apply() },
                onAutoSaveChange = { settingsAutoSave = it; prefs.edit().putBoolean("auto_save", it).apply() },
                onCompactChange = { settingsCompact = it; prefs.edit().putBoolean("compact_mode", it).apply() },
                onClearUnknown = { prefs.edit().remove("unknown_codes").apply(); unknownCodes = emptyList() },
                onBackup = { backupExportLauncher.launch("DigiDrop_backup.zip") },
                onRestore = { backupImportLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                onArchiveRoute = { archiveCurrentRoute() }
            )
        }
        AppScreen.HISTORY -> {
            HistoryScreen(
                routes = archivedRoutes,
                onBack = { currentScreen = AppScreen.HUB },
                onRestoreRoute = { routeId ->
                    importScope.launch {
                        runCatching { LogisticsRepository(context).restoreArchivedRoute(routeId) }
                            .onSuccess { restored ->
                                routeItems = restored
                                sessionScanCodes = emptySet()
                                val manifest = restored.joinToString("\n") { item ->
                                    listOf(
                                        item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(),
                                        item.phone, item.note, item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(),
                                        item.ownerName, item.workingHours, item.cashNote, item.extraComment
                                    ).joinToString("\t") { it.replace("\t", " ") }
                                }
                                prefs.edit().putString("active_manifest", manifest).apply()
                                currentScreen = AppScreen.ROUTE_LIST
                                Toast.makeText(context, "Архивный рейс восстановлен", Toast.LENGTH_LONG).show()
                            }
                            .onFailure { error ->
                                Toast.makeText(context, "Не удалось восстановить рейс: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                }
            )
        }
        AppScreen.PRODUCTS -> {
            ProductCatalogScreen(
                products = productCatalog,
                onBack = { currentScreen = AppScreen.HUB },
                onSearch = { query ->
                    importScope.launch {
                        productCatalog = runCatching { LogisticsRepository(context).searchProducts(query) }
                            .getOrElse { error ->
                                Toast.makeText(context, "Ошибка каталога: ${error.message}", Toast.LENGTH_SHORT).show()
                                emptyList()
                            }
                    }
                }
            )
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
                onCameraBound = { cameraXCamera = it },
                onBack = { currentScreen = previousScreen },
                onBarcodeScanned = { scannedCode ->
                    val normalizedCode = scannedCode.trim()
                    val lookup = normalizeScannedBarcode(normalizedCode)
                    val matches = routeItems.filter { item -> lookup.candidates.any { it.equals(item.barcode.trim(), ignoreCase = true) } }
                    val matchedItem = matches.singleOrNull()
                    val savedCodes = sessionScanCodes.toMutableSet()
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
                            sessionScanCodes = savedCodes
                            val itemKey = routeItemKey(matchedItem)
                            if (settingsAutoSave) {
                                prefs.edit().putStringSet("scan_codes", savedCodes).putInt(
                                    "scanned_" + itemKey,
                                    prefs.getInt("scanned_" + itemKey, matchedItem.scannedBoxes) + 1
                                ).apply()
                            }
                            val updated = matchedItem.copy(scannedBoxes = matchedItem.scannedBoxes + 1)
                            val updatedRoute = routeItems.map { if (routeItemKey(it) == itemKey) updated else it }
                            routeItems = updatedRoute
                            importScope.launch { runCatching { persistProgressIfEnabled(settingsAutoSave) { LogisticsRepository(context).syncProgress(updatedRoute) } } }
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
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "splashAppear"
    )
    val pulse = rememberInfiniteTransition(label = "splashPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "logoGlow"
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(1700)
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFFF4EBDD), Color(0xFFE8DCC9), Color(0xFFD8C8B1))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                Brush.radialGradient(listOf(Color(0x55A68B68), Color.Transparent)),
                radius = w * 0.72f,
                center = Offset(w * 0.5f, h * 0.43f)
            )
            // Лесные силуэты по краям, дорога и небольшой фургон.
            fun tree(centerX: Float, baseY: Float, height: Float, tint: Color) {
                val treePath = Path().apply {
                    moveTo(centerX, baseY - height)
                    lineTo(centerX - height * 0.34f, baseY - height * 0.46f)
                    lineTo(centerX - height * 0.19f, baseY - height * 0.46f)
                    lineTo(centerX - height * 0.42f, baseY - height * 0.12f)
                    lineTo(centerX + height * 0.42f, baseY - height * 0.12f)
                    lineTo(centerX + height * 0.19f, baseY - height * 0.46f)
                    lineTo(centerX + height * 0.34f, baseY - height * 0.46f)
                    close()
                }
                drawPath(treePath, tint)
                drawLine(tint, Offset(centerX, baseY - height * 0.12f), Offset(centerX, baseY), height * 0.08f)
            }
            val forest = Color(0x55708065)
            listOf(0.04f to 0.76f, 0.14f to 0.82f, 0.25f to 0.77f, 0.83f to 0.82f, 0.94f to 0.76f)
                .forEach { (x, y) -> tree(w * x, h * y, h * 0.18f, forest) }
            val road = Path().apply {
                moveTo(w * 0.47f, h * 1.04f)
                cubicTo(w * 0.42f, h * 0.88f, w * 0.64f, h * 0.80f, w * 0.54f, h * 0.67f)
                cubicTo(w * 0.48f, h * 0.59f, w * 0.53f, h * 0.54f, w * 0.56f, h * 0.50f)
            }
            drawPath(road, Color(0x66847F73), style = Stroke(width = w * 0.13f, cap = StrokeCap.Round))
            drawPath(road, Color(0x99F5F0E7), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            val truckX = w * 0.58f
            val truckY = h * 0.70f
            drawRoundRect(Color(0xFF667B68), Offset(truckX, truckY), Size(w * 0.22f, h * 0.075f), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
            drawRoundRect(Color(0xFF536B5C), Offset(truckX + w * 0.16f, truckY + h * 0.012f), Size(w * 0.095f, h * 0.063f), androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
            drawRoundRect(Color(0xFFB9C9BE), Offset(truckX + w * 0.177f, truckY + h * 0.019f), Size(w * 0.055f, h * 0.025f), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            drawCircle(Color(0xFF3F4942), w * 0.018f, Offset(truckX + w * 0.055f, truckY + h * 0.075f))
            drawCircle(Color(0xFF3F4942), w * 0.205f, Offset(truckX + w * 0.205f, truckY + h * 0.075f))
            // Тонкая маршрутная линия с контрольными точками.
            val routePath = Path().apply {
                moveTo(w * 0.12f, h * 0.78f)
                cubicTo(w * 0.34f, h * 0.66f, w * 0.25f, h * 0.48f, w * 0.52f, h * 0.50f)
                cubicTo(w * 0.76f, h * 0.52f, w * 0.64f, h * 0.30f, w * 0.88f, h * 0.22f)
            }
            drawPath(routePath, Color(0x557B6A52), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            listOf(Offset(w * 0.12f, h * 0.78f), Offset(w * 0.52f, h * 0.50f), Offset(w * 0.88f, h * 0.22f))
                .forEach { drawCircle(Color(0xFF8A765D), radius = 6.dp.toPx(), center = it) }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp).padding(top = 22.dp)
                .graphicsLayer { alpha = appear; scaleX = 0.96f + appear * 0.04f; scaleY = 0.96f + appear * 0.04f },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(112.dp).graphicsLayer { scaleX = glow; scaleY = glow }
                    .clip(RoundedCornerShape(34.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF8D7658), Color(0xFF5E5945)))),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(66.dp)) {
                    val p = Path().apply {
                        moveTo(size.width * 0.5f, size.height * 0.04f)
                        lineTo(size.width * 0.88f, size.height * 0.26f)
                        lineTo(size.width * 0.88f, size.height * 0.70f)
                        lineTo(size.width * 0.5f, size.height * 0.94f)
                        lineTo(size.width * 0.12f, size.height * 0.70f)
                        lineTo(size.width * 0.12f, size.height * 0.26f)
                        close()
                    }
                    drawPath(p, Color(0xFFF7F0E5), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    drawLine(Color(0xFFF7F0E5), Offset(size.width * 0.27f, size.height * 0.53f), Offset(size.width * 0.46f, size.height * 0.68f), 4.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(Color(0xFFF7F0E5), Offset(size.width * 0.46f, size.height * 0.68f), Offset(size.width * 0.75f, size.height * 0.36f), 4.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Digi", color = Color(0xFF342D25), fontSize = 35.sp, fontWeight = FontWeight.ExtraBold)
                Text("Drop", color = Color(0xFF80664E), fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("МАРШРУТЫ  •  ДОСТАВКА  •  QR", color = Color(0xFF746756), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(56.dp))
            Box(Modifier.fillMaxWidth(0.58f).height(5.dp).clip(CircleShape).background(Color(0x668A765D))) {
                Box(Modifier.fillMaxWidth(appear.coerceIn(0.12f, 1f)).fillMaxHeight().clip(CircleShape).background(Color(0xFF80664E)))
            }
            Spacer(Modifier.height(12.dp))
            Text("Подготавливаем маршрут", color = Color(0xFF746756), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun InvoiceLandingScreen(
    hasActiveRoute: Boolean,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onLoadInvoice: () -> Unit,
    onAppendInvoice: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(BgDark).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("← Назад", color = MintAccent, fontSize = 16.sp,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(8.dp))
        Text("Загрузить накладную", color = TextLight, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("Выберите источник. После обработки откроется список точек маршрута.",
            color = TextMuted, fontSize = 15.sp)
        HubTile("Сфотографировать", "Снять накладную камерой", "◎", EmeraldPrimary,
            Modifier.fillMaxWidth(), onTakePhoto)
        HubTile("Выбрать фото", "Загрузить снимок из галереи", "▧", Color(0xFF3978A8),
            Modifier.fillMaxWidth(), onChoosePhoto)
        HubTile("Загрузить файл", "PDF, Excel или CSV", "⇧", MintAccent,
            Modifier.fillMaxWidth(), onLoadInvoice)
        if (hasActiveRoute) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onAppendInvoice) {
                Text("Добавить накладную к текущему рейсу", color = MintAccent)
            }
        }
    }
}

@Composable
fun HubScreen(
    onOpenMenu: () -> Unit,
    onOpenScanner: () -> Unit,
    onLoadInvoice: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(BgDark)
            .statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("DigiDrop", color = TextLight, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Рабочее место водителя", color = TextMuted, fontSize = 16.sp)
            }
            TextButton(onClick = onOpenMenu) {
                Text("Меню  ⋯", color = MintAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        HubTile(
            "QR-сканер",
            "Сканировать код накладной",
            "▦",
            Color(0xFF3978A8),
            Modifier.fillMaxWidth().height(190.dp),
            onOpenScanner
        )
        HubTile(
            "Загрузить накладную",
            "Фото, галерея или файл",
            "⇧",
            EmeraldPrimary,
            Modifier.fillMaxWidth().height(190.dp),
            onLoadInvoice
        )
    }
}

@Composable
fun MoreMenuScreen(
    onBack: () -> Unit,
    onOpenRoute: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenUnknown: () -> Unit,
    onExport: () -> Unit,
    onCallDispatcher: () -> Unit,
    onResetRoute: () -> Unit,
    onNavigateNext: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(BgDark).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("← Назад", color = MintAccent, fontSize = 16.sp,
            modifier = Modifier.bounceClick { onBack() })
        Text("Дополнительно", color = TextLight, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        HubTile("Точки и карта маршрута", "Список, статусы, порядок и навигация", "⌖", EmeraldPrimary,
            Modifier.fillMaxWidth(), onOpenRoute)
        HubTile("Следующая точка", "Открыть Яндекс Навигатор", "➜", EmeraldPrimary,
            Modifier.fillMaxWidth(), onNavigateNext)
        HubTile("Отчёты и экспорт", "Сводка и выгрузки", "▤", Color(0xFF3978A8),
            Modifier.fillMaxWidth(), onOpenReports)
        HubTile("История рейсов", "Архив завершённых маршрутов", "◷", MintAccent,
            Modifier.fillMaxWidth(), onOpenHistory)
        HubTile("Каталог товаров", "Локальная база штрихкодов", "▦", Color(0xFF6E69A8),
            Modifier.fillMaxWidth(), onOpenProducts)
        HubTile("Неизвестные коды", "Коды вне накладных", "?", Color(0xFFB27A35),
            Modifier.fillMaxWidth(), onOpenUnknown)
        HubTile("Настройки и резервная копия", "Автосохранение, офлайн-режим, ZIP", "⚙", Color(0xFF687C70),
            Modifier.fillMaxWidth(), onOpenSettings)
        HubTile("Экспорт CSV", "Выгрузить текущий маршрут", "⇩", Color(0xFF3978A8),
            Modifier.fillMaxWidth(), onExport)
        HubTile("Позвонить диспетчеру", "Связаться по рабочему номеру", "☎", Color(0xFF286BC4),
            Modifier.fillMaxWidth(), onCallDispatcher)
        HubTile("Сбросить прогресс рейса", "Очистить отметки сканирования и статусы", "↺", Color(0xFFB45C4C),
            Modifier.fillMaxWidth(), onResetRoute)
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
        modifier = modifier.heightIn(min = 112.dp)
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
            .bounceClick(scaleDown = 0.985f) { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(symbol, color = accent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = TextLight, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextMuted, fontSize = 14.sp, maxLines = 2)
            }
            Text("›", color = MintAccent, fontSize = 28.sp)
        }
    }
}

// --- БОЕВОЙ СКАНЕР ШТРИХКОДОВ НА CAMERAX + ML KIT ---
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun LiveCameraScannerScreen(
    isFlashOn: Boolean,
    onFlashToggle: () -> Unit,
    onCameraBound: (Camera) -> Unit,
    onBack: () -> Unit,
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasScanned by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    var lastRaw by remember { mutableStateOf("") }
    var lastScanAt by remember { mutableStateOf(0L) }

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

                    val barcodeScanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_UPC_A, Barcode.FORMAT_CODE_128).build())
                    val analysisExecutor = Executors.newSingleThreadExecutor()

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
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
                        val boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        onCameraBound(boundCamera)
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
    compactMode: Boolean = false,
    onClick: () -> Unit,
    onMapClick: () -> Unit,
    onCallClick: () -> Unit
) {
    val first = items.first()
    val total = items.sumOf { it.totalBoxes }
    val scanned = items.sumOf { it.scannedBoxes }
    val delivered = deliveryPointStatus(items) == DeliveryPointStatus.DELIVERED
    val hasProblem = items.any { it.discrepancyQty > 0 }
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, when { hasProblem -> Color(0xFFB3261E); delivered -> CardBorder; else -> EmeraldPrimary.copy(alpha = 0.6f) }, RoundedCornerShape(18.dp))
            .bounceClick(scaleDown = 0.98f, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(if (compactMode) 10.dp else 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when { hasProblem -> "Проблема / расхождение"; delivered -> "Доставлено"; else -> "В пути" },
                    color = when { hasProblem -> Color(0xFFB3261E); delivered -> MintAccent; else -> EmeraldPrimary },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(first.storeName, color = TextLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(first.address, color = TextMuted, fontSize = 13.sp)
                if (first.ownerName.isNotBlank()) Text(first.ownerName, color = TextLight, fontSize = 12.sp, maxLines = 1)
                if (first.workingHours.isNotBlank()) Text("Работа: ${first.workingHours}", color = TextMuted, fontSize = 12.sp, maxLines = 1)
                if (first.cashNote.isNotBlank()) Text("НАЛИЧНЫЕ: ${first.cashNote}", color = Color(0xFF8A5A24), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                if (first.note.isNotBlank()) Text(first.note, color = TextMuted, fontSize = 12.sp, maxLines = 2)
                Spacer(Modifier.height(if (compactMode) 4.dp else 8.dp))
                Text("$total мест • ${items.size} строк накладных", color = TextLight, fontSize = if (compactMode) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold)
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
    onCall: () -> Unit,
    onClosePoint: () -> Unit
) {
    val total = items.sumOf { it.totalBoxes }
    val scanned = items.sumOf { it.scannedBoxes }
    Column(Modifier.fillMaxSize().background(BgDark).padding(18.dp)) {
        Text("← Точки маршрута", color = MintAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(16.dp))
        Text(items.firstOrNull()?.storeName.orEmpty(), color = TextLight, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(items.firstOrNull()?.address.orEmpty(), color = TextMuted, fontSize = 14.sp)
        items.firstOrNull()?.let { point ->
            if (point.ownerName.isNotBlank()) Text("ИП / контакт: ${point.ownerName}", color = TextLight, fontSize = 14.sp)
            if (point.phone.isNotBlank()) Text("Телефон: ${point.phone}", color = TextLight, fontSize = 14.sp)
            if (point.workingHours.isNotBlank()) Text("Время работы: ${point.workingHours}", color = TextLight, fontSize = 14.sp)
            if (point.cashNote.isNotBlank()) Text("Наличные: ${point.cashNote}", color = Color(0xFF8A5A24), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (point.extraComment.isNotBlank()) Text("Дополнительно: ${point.extraComment}", color = TextMuted, fontSize = 13.sp)
            if (point.note.isNotBlank()) Text("Комментарий: ${point.note}", color = TextMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Накладных/строк: ${items.size}", color = TextLight, fontSize = 14.sp)
            Text("Всего мест: $total", color = MintAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text("Учтено уникальных сканов: $scanned", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        HubActionButton("Проверить и закрыть точку", "Все места должны быть учтены сканами или расхождениями с причиной", CardSurface, TextLight, MintAccent, onClosePoint)
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
                            Text(
                                when { item.discrepancyQty > 0 -> "Расхождение"; item.isDelivered -> "Сдано"; else -> "В пути" },
                                color = when { item.discrepancyQty > 0 -> Color(0xFFB3261E); item.isDelivered -> MintAccent; else -> EmeraldPrimary },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("Нажми, чтобы открыть накладную", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// --- ЭКРАН ДЕТАЛЬНОЙ НАКЛАДНОЙ (ЧТО ВНУТРИ) ---
private fun decodeEvidencePreview(path: String): androidx.compose.ui.graphics.ImageBitmap? {
    if (path.isBlank()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}

@Composable
fun PointDetailsScreen(
    item: RouteItem,
    onBack: () -> Unit,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onToggleDelivered: () -> Unit,
    onSetDiscrepancy: (Int, String) -> Unit,
    onAddPhoto: () -> Unit,
    onCaptureSignature: (Bitmap) -> Unit
) {
    var showSignatureDialog by remember(item.barcode) { mutableStateOf(false) }
    var showDiscrepancyDialog by remember(item.barcode) { mutableStateOf(false) }
    var discrepancyQtyText by remember(item.barcode) { mutableStateOf("1") }
    var discrepancyReason by remember(item.barcode) { mutableStateOf("НЕДОВОЗ") }
    var discrepancyError by remember(item.barcode) { mutableStateOf("") }
    val photoPreview = remember(item.photoDocUri) { decodeEvidencePreview(item.photoDocUri) }
    val signaturePreview = remember(item.signaturePath) { decodeEvidencePreview(item.signaturePath) }
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
                        text = when { item.discrepancyQty > 0 -> "Расхождение"; item.isDelivered -> "Сдано"; else -> "В пути" },
                        color = if (item.discrepancyQty > 0) Color(0xFFB3261E) else if (item.isDelivered) BgDark else MintAccent,
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
                modifier = Modifier.fillMaxWidth().border(1.dp, CardBorder, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("ДАННЫЕ ТОЧКИ", color = MintAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (item.ownerName.isNotBlank()) Text("ИП / контакт: ${item.ownerName}", color = TextLight, fontSize = 15.sp)
                    if (item.phone.isNotBlank()) Text("Телефон: ${item.phone}", color = TextLight, fontSize = 15.sp)
                    if (item.workingHours.isNotBlank()) Text("Время работы: ${item.workingHours}", color = TextLight, fontSize = 15.sp)
                    if (item.cashNote.isNotBlank()) Text("НАЛИЧНЫЕ: ${item.cashNote}", color = Color(0xFF8A5A24), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (item.extraComment.isNotBlank()) Text("Дополнительно: ${item.extraComment}", color = TextLight, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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

            HubActionButton(
                if (item.signaturePath.isNotBlank()) "Обновить подпись получателя" else "Подпись получателя",
                "Подтверждение передачи груза",
                CardSurface,
                TextLight,
                MintAccent
            ) { showSignatureDialog = true }
            if (signaturePreview != null) {
                Image(
                    bitmap = signaturePreview,
                    contentDescription = "Подпись получателя",
                    modifier = Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White).padding(5.dp),
                    contentScale = ContentScale.Fit
                )
                Text("Подпись получателя сохранена на устройстве", color = MintAccent, fontSize = 12.sp)
            }
            HubActionButton(
                if (item.photoDocUri.isNotBlank()) "Заменить фото доказательства" else "Добавить фото доказательства",
                "Сохранить фото в памяти телефона",
                CardSurface,
                TextLight,
                MintAccent,
                onAddPhoto
            )
            if (photoPreview != null) {
                Image(
                    bitmap = photoPreview,
                    contentDescription = "Фото доказательства доставки",
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Text("Фото доказательства хранится на устройстве", color = MintAccent, fontSize = 12.sp)
            }
            HubActionButton(
                if (item.discrepancyQty > 0) "Изменить расхождение: ${item.discrepancyQty}" else "Зафиксировать расхождение",
                "Недовоз, брак или излишек",
                CardSurface,
                TextLight,
                Color(0xFFD18B26)
            ) { showDiscrepancyDialog = true }
            if (item.discrepancyQty > 0) {
                Text(
                    "Текущее расхождение: ${item.discrepancyReason ?: "причина не указана"} • ${item.discrepancyQty} мест",
                    color = Color(0xFFD18B26),
                    fontSize = 12.sp
                )
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
        if (showSignatureDialog) {
            SignatureCaptureDialog(
                onDismiss = { showSignatureDialog = false },
                onSave = { bitmap ->
                    onCaptureSignature(bitmap)
                    showSignatureDialog = false
                }
            )
        }
        if (showDiscrepancyDialog) {
            AlertDialog(
                onDismissRequest = { showDiscrepancyDialog = false },
                title = { Text("Расхождение по накладной") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = discrepancyQtyText,
                            onValueChange = { discrepancyQtyText = it.filter(Char::isDigit).take(5); discrepancyError = "" },
                            label = { Text("Количество мест") },
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("НЕДОВОЗ", "БРАК", "ИЗЛИШЕК").forEach { value ->
                                TextButton(onClick = { discrepancyReason = value; discrepancyError = "" }) {
                                    Text(value, color = if (discrepancyReason == value) MintAccent else TextMuted)
                                }
                            }
                        }
                        if (discrepancyError.isNotBlank()) {
                            Text(discrepancyError, color = Color(0xFFB3261E), fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val quantity = discrepancyQtyText.toIntOrNull()
                        val maxAllowed = if (discrepancyReason == "ИЗЛИШЕК") Int.MAX_VALUE
                            else (item.totalBoxes - item.scannedBoxes).coerceAtLeast(0)
                        when {
                            quantity == null || quantity <= 0 -> discrepancyError = "Укажи количество больше нуля"
                            quantity > maxAllowed -> discrepancyError = "Количество превышает остаток по накладной ($maxAllowed)"
                            else -> {
                                onSetDiscrepancy(quantity, discrepancyReason)
                                showDiscrepancyDialog = false
                            }
                        }
                    }) { Text("Сохранить") }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscrepancyDialog = false }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
fun SignatureCaptureDialog(
    onDismiss: () -> Unit,
    onSave: (Bitmap) -> Unit
) {
    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }
    var padSize by remember { mutableStateOf(IntSize(1, 1)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подпись получателя") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Попроси получателя расписаться пальцем в поле ниже.", color = TextMuted, fontSize = 12.sp)
                Canvas(
                    Modifier.fillMaxWidth().height(180.dp)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .onSizeChanged { padSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { point -> strokes.add(mutableStateListOf(point)) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    strokes.lastOrNull()?.add(change.position)
                                }
                            )
                        }
                ) {
                    strokes.forEach { points ->
                        if (points.size == 1) {
                            drawCircle(Color.Black, radius = 2.dp.toPx(), center = points.first())
                        } else {
                            for (index in 1 until points.size) {
                                drawLine(Color.Black, points[index - 1], points[index], strokeWidth = 3.dp.toPx())
                            }
                        }
                    }
                }
                TextButton(onClick = { strokes.clear() }) { Text("Очистить поле") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (strokes.isNotEmpty() && padSize.width > 1 && padSize.height > 1) {
                    val bitmap = Bitmap.createBitmap(padSize.width, padSize.height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.BLACK
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 6f
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    strokes.forEach { points ->
                        if (points.isNotEmpty()) {
                            val path = android.graphics.Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            canvas.drawPath(path, paint)
                        }
                    }
                    onSave(bitmap)
                }
            }) { Text("Сохранить подпись") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// --- СПИСОК ТОЧЕК ---
@Composable
fun RouteListScreen(
    routeItems: List<RouteItem>,
    compactMode: Boolean,
    onBackToHub: () -> Unit,
    onOpenScanner: () -> Unit,
    onEditRoute: () -> Unit,
    onOpenOfflineMap: () -> Unit,
    onRebuildRoute: () -> Unit,
    onOpenPointMap: (String) -> Unit,
    onCallClient: (String) -> Unit,
    onPointClick: (List<RouteItem>) -> Unit,
    onToggleItem: (RouteItem) -> Unit,
    onMoveGroup: (Int, Int) -> Unit
) {
    var pointFilter by remember { mutableStateOf(PointFilter.ALL) }
    var manualDestination by remember { mutableStateOf("") }

    val allGroups = remember(routeItems) {
        routeItems.groupBy { it.storeName to it.address }.toList()
    }
    val displayedGroups = remember(allGroups, pointFilter) {
        val snapshots = allGroups.mapIndexed { index, entry ->
            val group = entry.second
            val expected = group.sumOf { it.totalBoxes.coerceAtLeast(0) }
            val scanned = group.sumOf { it.scannedBoxes.coerceAtLeast(0) }
            val status = deliveryPointStatus(group)
            DeliveryPointSnapshot(
                id = entry.first.first + "|" + entry.first.second,
                sortOrder = index,
                status = status,
                expectedUnits = expected,
                scannedUnits = scanned,
                discrepancyUnits = group.sumOf { it.discrepancyQty.coerceAtLeast(0) }
            )
        }
        val visibleIds = RouteProgress.filter(snapshots, pointFilter).map { it.id }.toSet()
        allGroups.filter { (key, _) -> key.first + "|" + key.second in visibleIds }
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

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(
                            PointFilter.ALL to "Все",
                            PointFilter.ACTIVE to "В пути",
                            PointFilter.DELIVERED to "Сданы",
                            PointFilter.PROBLEMS to "Проблемы"
                        ).forEach { (filter, label) ->
                            TextButton(onClick = { pointFilter = filter }) {
                                Text(
                                    label,
                                    color = if (pointFilter == filter) MintAccent else TextMuted,
                                    fontWeight = if (pointFilter == filter) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEditRoute) { Text("Редактировать / добавить / удалить точки", color = MintAccent) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = manualDestination,
                    onValueChange = { manualDestination = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Адрес или точка на карте") },
                    placeholder = { Text("Введите адрес вручную") }
                )
                Button(
                    onClick = { if (manualDestination.isNotBlank()) onOpenPointMap(manualDestination.trim()) },
                    enabled = manualDestination.isNotBlank()
                ) { Text("Маршрут") }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRebuildRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Перестроить маршрут", maxLines = 1, fontSize = 12.sp)
                }
                TextButton(
                    onClick = onOpenOfflineMap,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Офлайн-карта", maxLines = 1, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 7.dp else 14.dp)
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
                                    if (pointFilter != PointFilter.ALL && routeItems.isNotEmpty()) "Нет точек в этом фильтре" else "Рейс пока пуст",
                                    color = TextLight, fontSize = 19.sp, fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (pointFilter != PointFilter.ALL && routeItems.isNotEmpty()) "Выберите другой фильтр, чтобы увидеть остальные точки" else "Вернитесь на главный экран и загрузите накладную CSV или TSV",
                                    color = TextMuted, fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(displayedGroups, key = { _, group -> group.first.first + "|" + group.first.second }) { index, group ->
                        val originalIndex = allGroups.indexOfFirst { it.first == group.first }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                StoreGroupCard(
                                    items = group.second,
                                    compactMode = compactMode,
                                    onClick = { onPointClick(group.second) },
                                    onMapClick = { onOpenPointMap(group.second.first().address) },
                                    onCallClick = { onCallClient(group.second.first().phone) }
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                TextButton(enabled = pointFilter == PointFilter.ALL && originalIndex > 0, onClick = { onMoveGroup(originalIndex, originalIndex - 1) }) { Text("↑") }
                                TextButton(enabled = pointFilter == PointFilter.ALL && originalIndex in 0 until allGroups.lastIndex, onClick = { onMoveGroup(originalIndex, originalIndex + 1) }) { Text("↓") }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            BottomActionButtons(
                onRouteClick = {
                    val nextPoint = nextPendingPoint(routeItems)?.firstOrNull()?.address.orEmpty()
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

@Composable
fun ReportsScreen(
    routeItems: List<RouteItem>,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit
) {
    val points = routeItems.groupBy { it.storeName to it.address }
    val delivered = points.values.count { group -> deliveryPointStatus(group) == DeliveryPointStatus.DELIVERED }
    val problemPoints = points.values.count { group -> deliveryPointStatus(group) == DeliveryPointStatus.PROBLEM }
    val inProgressPoints = points.values.count { group -> deliveryPointStatus(group) == DeliveryPointStatus.IN_PROGRESS }
    val pendingPoints = (points.size - delivered - problemPoints - inProgressPoints).coerceAtLeast(0)
    val totalBoxes = routeItems.sumOf { it.totalBoxes }
    val scanned = routeItems.sumOf { it.scannedBoxes }
    val discrepancies = routeItems.sumOf { it.discrepancyQty }
    val remaining = (totalBoxes - scanned - discrepancies).coerceAtLeast(0)
    Column(Modifier.fillMaxSize().background(BgDark).verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("← Главная", color = MintAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(18.dp))
        Text("Отчёты и сверка", color = TextLight, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        val stats = listOf(
            "Точек всего" to points.size.toString(),
            "Точек доставлено" to "$delivered / ${points.size}",
            "Точек с расхождениями" to problemPoints.toString(),
            "Мест по накладным" to totalBoxes.toString(),
            "Отсканировано" to scanned.toString(),
            "Расхождения" to discrepancies.toString(),
            "Осталось учесть" to remaining.toString(),
            "Строк накладных" to routeItems.size.toString()
        )
        stats.forEach { (label, value) ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = TextMuted, fontSize = 14.sp)
                    Text(value, color = TextLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("Состояние точек", color = TextLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        ReportStatusBar("Доставлено", delivered, points.size, Color(0xFF43875B))
        ReportStatusBar("В работе", inProgressPoints, points.size, Color(0xFF3978A8))
        ReportStatusBar("Ожидают", pendingPoints, points.size, Color(0xFFB18A38))
        ReportStatusBar("Расхождения", problemPoints, points.size, Color(0xFFB45C4C))
        Spacer(Modifier.height(8.dp))
        HubActionButton("Экспорт CSV", "Универсальный табличный формат", CardSurface, TextLight, MintAccent, onExport)
        Spacer(Modifier.height(8.dp))
        HubActionButton("Экспорт Excel", "Файл XLSX с отдельными колонками и статусами", CardSurface, TextLight, MintAccent, onExportExcel)
        Spacer(Modifier.height(8.dp))
        HubActionButton("Экспорт PDF", "Печатный отчёт по точкам и грузу", CardSurface, TextLight, MintAccent, onExportPdf)
        Spacer(Modifier.height(8.dp))
        Text("В отчёты входят штрихкод, точка, адрес, товар, план, факт, расхождения, остаток и статус.", color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ReportStatusBar(label: String, value: Int, total: Int, color: Color) {
    val fraction = if (total > 0) (value.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextMuted, fontSize = 12.sp)
            Text("$value / $total", color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(CardBorder)
        ) {
            Box(
                Modifier.fillMaxWidth(fraction).height(8.dp).background(color, RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
fun SettingsScreen(
    offlineMode: Boolean,
    autoSave: Boolean,
    compactMode: Boolean,
    onBack: () -> Unit,
    onOfflineChange: (Boolean) -> Unit,
    onAutoSaveChange: (Boolean) -> Unit,
    onCompactChange: (Boolean) -> Unit,
    onClearUnknown: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onArchiveRoute: () -> Unit
) {
    var showArchiveConfirmation by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(BgDark).padding(18.dp)) {
        Text("← Главная", color = MintAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(18.dp))
        Text("Настройки", color = TextLight, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        SettingToggleRow("Офлайн-режим", "Не искать координаты через интернет при импорте. Встроенная карта работает без сети.", offlineMode, onOfflineChange)
        SettingToggleRow("Автосохранение", "Сохранять прогресс сканирования на устройстве", autoSave, onAutoSaveChange)
        SettingToggleRow("Компактный режим", "Уменьшает отступы и плотность карточек маршрута", compactMode, onCompactChange)
        Spacer(Modifier.height(12.dp))
        HubActionButton("Резервная копия", "Сохранить рейс, прогресс, настройки, фото и подписи в ZIP", CardSurface, TextLight, MintAccent, onBackup)
        Spacer(Modifier.height(8.dp))
        HubActionButton("Восстановить копию", "Загрузить ZIP-резервную копию DigiDrop", CardSurface, TextLight, MintAccent, onRestore)
        Spacer(Modifier.height(8.dp))
        HubActionButton("Завершить рейс и отправить в архив", "Проверить и сохранить текущий рейс в истории", CardSurface, TextLight, Color(0xFFD18B26)) {
            showArchiveConfirmation = true
        }
        Spacer(Modifier.height(8.dp))
        HubActionButton("Очистить неизвестные коды", "Удалить локальный список нераспознанных сканов", CardSurface, TextLight, Color(0xFFD18B26), onClearUnknown)
        Spacer(Modifier.height(14.dp))
        Text("Резервная копия включает маршрут, сканы, расхождения, настройки и локальные фото/подписи.", color = TextMuted, fontSize = 12.sp)
    }
    if (showArchiveConfirmation) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirmation = false },
            title = { Text("Отправить рейс в архив?") },
            text = { Text("Текущий маршрут исчезнет из активного списка, но останется в истории на этом устройстве.") },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveConfirmation = false
                    onArchiveRoute()
                }) { Text("Архивировать") }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirmation = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
fun SettingToggleRow(title: String, subtitle: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextMuted, fontSize = 11.sp)
            }
            Text(if (enabled) "ВКЛ" else "ВЫКЛ", color = if (enabled) MintAccent else TextMuted,
                fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier
                    .clip(RoundedCornerShape(10.dp)).background(BgDark).padding(horizontal = 12.dp, vertical = 10.dp)
                    .bounceClick { onChange(!enabled) })
        }
    }
}

@Composable
internal fun HistoryScreen(
    routes: List<RouteHistorySnapshot>,
    onBack: () -> Unit,
    onRestoreRoute: (Long) -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU")) }
    Column(Modifier.fillMaxSize().background(BgDark).padding(18.dp)) {
        Text("← Главная", color = MintAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(18.dp))
        Text("История рейсов", color = TextLight, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Архив хранится локально на этом устройстве.", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        if (routes.isEmpty()) {
            Text("Завершённых или заменённых рейсов пока нет.", color = TextMuted, fontSize = 14.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(routes, key = { it.routeId }) { route ->
                    val percent = if (route.expectedQty > 0) {
                        (route.scannedQty * 100 / route.expectedQty).coerceIn(0, 100)
                    } else 0
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(route.routeNumber, color = TextLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(formatter.format(Date(route.createdAt)), color = TextMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "${route.completedPointCount} / ${route.pointCount} точек завершено • ${route.scannedQty} / ${route.expectedQty} мест",
                                color = MintAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                            Text("Выполнение по сканам: $percent%", color = TextMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { onRestoreRoute(route.routeId) }) {
                                Text("Восстановить этот рейс", color = MintAccent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnknownCodesScreen(codes: List<String>, onBack: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BgDark).padding(18.dp)) {
        Text("← Главная", color = MintAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(18.dp))
        Text("Неизвестные коды", color = TextLight, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Скан-коды, которых нет в загруженных накладных: ${codes.size}", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        if (codes.isEmpty()) {
            Text("Пока нет неизвестных кодов.", color = TextMuted, fontSize = 14.sp)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(codes) { code ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(12.dp)) {
                        Text(code, Modifier.padding(14.dp), color = TextLight, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            HubActionButton("Очистить список", "Удалить неизвестные сканы", CardSurface, TextLight, Color(0xFFD18B26), onClear)
        }
    }
}
