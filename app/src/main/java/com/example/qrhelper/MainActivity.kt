@file:Suppress("UsePropertyAccessSyntax")

package com.example.qrhelper

import com.example.qrhelper.ui.theme.QRHelperTheme
import com.example.qrhelper.ui.theme.DigiDropPalette

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
import android.widget.ImageView
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
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
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
    DETAILS,
    SCANNER,
    SCAN_RESULTS,
    POINT_DETAILS,
    REPORTS,
    SETTINGS,
    HISTORY,
    PRODUCTS,
    UNKNOWN_CODES,
    PHOTO_ROUTE
}


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
            val appearancePrefs = getSharedPreferences("digitdrop_prefs", Context.MODE_PRIVATE)
            var darkTheme by remember { mutableStateOf(appearancePrefs.getBoolean("dark_theme", false)) }
            QRHelperTheme(darkTheme = darkTheme) {
                DigitDropRoot(
                    onThemeChange = { enabled ->
                        darkTheme = enabled
                        appearancePrefs.edit().putBoolean("dark_theme", enabled).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun DigitDropRoot(onThemeChange: (Boolean) -> Unit) {
    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }
    var screenHistory by remember { mutableStateOf(emptyList<AppScreen>()) }
    fun navigateTo(screen: AppScreen) {
        if (screen != currentScreen) {
            screenHistory = AppScreenHistory.push(screenHistory, currentScreen, screen)
            currentScreen = screen
        }
    }
    var photoRouteInitialAction by remember { mutableStateOf<String?>(null) }
    var photoRouteInitialUri by remember { mutableStateOf<Uri?>(null) }
    val windowContext = LocalContext.current
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
    var settingsDarkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", false)) }
    val screenBackgroundColor = MaterialTheme.colorScheme.background

    SideEffect {
        val activity = windowContext as? Activity
        activity?.window?.let { window ->
            val systemBarColor = if (currentScreen == AppScreen.SPLASH) {
                DigiDropPalette.darkBackground
            } else {
                screenBackgroundColor
            }
            window.statusBarColor = systemBarColor.toArgb()
            window.navigationBarColor = systemBarColor.toArgb()
            window.decorView.systemUiVisibility = if (currentScreen != AppScreen.SPLASH && !settingsDarkTheme) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                0
            }
        }
    }
    var sessionScanCodes by remember {
        mutableStateOf(prefs.getStringSet("scan_codes", emptySet())?.toSet().orEmpty())
    }
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

    var routeHeader by remember {
        mutableStateOf(
            PhotoRouteHeader(
                driver = prefs.getString("route_header_driver", "").orEmpty(),
                vehicle = prefs.getString("route_header_vehicle", "").orEmpty(),
                vehicleNumber = prefs.getString("route_header_vehicle_number", "").orEmpty(),
                routeNumber = prefs.getString("route_header_number", "").orEmpty()
            )
        )
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
            runCatching { persistRouteProgress { LogisticsRepository(context).syncProgress(updatedItems) } }
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
            runCatching { persistRouteProgress { LogisticsRepository(context).syncProgress(updatedItems) } }
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
                navigateTo(AppScreen.HUB)
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
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                if (InvoiceImportKind.isImage(mime, uriText)) {
                    // Copy the provider-backed image while the picker grant is still active.
                    // OCR then reads a stable app-owned file instead of a temporary gallery URI.
                    val cachedPhoto = withContext(Dispatchers.IO) {
                        val extension = when {
                            mime.contains("png", ignoreCase = true) -> ".png"
                            mime.contains("webp", ignoreCase = true) -> ".webp"
                            mime.contains("heic", ignoreCase = true) || mime.contains("heif", ignoreCase = true) -> ".heic"
                            else -> ".jpg"
                        }
                        val target = File(context.cacheDir, "route_import_${System.currentTimeMillis()}$extension")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Android не предоставил доступ к выбранной фотографии")
                        check(target.length() > 0L) { "Выбранная фотография оказалась пустой" }
                        target
                    }
                    photoRouteInitialUri = Uri.fromFile(cachedPhoto)
                    photoRouteInitialAction = null
                    navigateTo(AppScreen.PHOTO_ROUTE)
                    return@launch
                }
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
                    // Новый файл накладной не должен наследовать водителя и машину прошлого рейса.
                    routeHeader = PhotoRouteHeader()
                    sessionScanCodes = emptySet()
                    prefs.edit()
                        .remove("route_header_number")
                        .remove("route_header_driver")
                        .remove("route_header_vehicle")
                        .remove("route_header_vehicle_number")
                        .remove("scan_codes")
                        .apply()
                    val withProgress = mergeInvoiceRows(emptyList(), imported).map { item ->
                        item.copy(
                            isDelivered = prefs.getBoolean("delivered_" + routeItemKey(item), false),
                            scannedBoxes = prefs.getInt("scanned_" + routeItemKey(item), 0)
                        )
                    }
                    navigateTo(AppScreen.ROUTE_LIST)
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
            navigateTo(AppScreen.SCANNER)
        } else {
            Toast.makeText(context, "Разрешите доступ к камере", Toast.LENGTH_SHORT).show()
        }
    }

    fun openScanner() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            previousScreen = currentScreen
            navigateTo(AppScreen.SCANNER)
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
            Toast.makeText(
                context,
                "Яндекс Навигатор недоступен. Установите или обновите приложение; маршрут сохранён в DigiDrop.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun navigateBack() {
        val (remainingHistory, destination) = AppScreenHistory.pop(screenHistory, currentScreen)
        screenHistory = remainingHistory
        currentScreen = destination
    }

    BackHandler(enabled = currentScreen != AppScreen.SPLASH) {
        navigateBack()
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = currentScreen, animationSpec = tween(220), label = "screenTransition") { screen ->
        when (screen) {
        AppScreen.SPLASH -> {
            SplashScreen(onFinished = { screenHistory = emptyList(); currentScreen = AppScreen.HUB })
        }
        AppScreen.INVOICE_IMPORT -> {
            InvoiceLandingScreen(
                onBack = { navigateTo(AppScreen.HUB) },
                onTakePhoto = { photoRouteInitialAction = "camera"; photoRouteInitialUri = null; navigateTo(AppScreen.PHOTO_ROUTE) },
                onLoadInvoice = {
                    filePickerLauncher.launch(arrayOf("image/*", "text/*", "application/csv", "application/pdf", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                }
            )
        }
        AppScreen.HUB -> {
            HubScreen(
                routeItems = routeItems,
                routeHeader = routeHeader,
                onOpenMenu = { navigateTo(AppScreen.MORE_MENU) },
                onOpenScanner = { openScanner() },
                onLoadInvoice = { navigateTo(AppScreen.INVOICE_IMPORT) },
                onOpenRoute = { navigateTo(AppScreen.ROUTE_LIST) },
                onOpenReports = { navigateTo(AppScreen.REPORTS) },
                onOpenHistory = {
                    importScope.launch {
                        archivedRoutes = runCatching { LogisticsRepository(context).history() }
                            .getOrElse { error ->
                                Toast.makeText(context, "Не удалось загрузить историю: ${error.message}", Toast.LENGTH_LONG).show()
                                emptyList()
                            }
                        navigateTo(AppScreen.HISTORY)
                    }
                },
                onOpenSettings = { navigateTo(AppScreen.SETTINGS) },
                onThemeToggle = {
                    settingsDarkTheme = !settingsDarkTheme
                    prefs.edit().putBoolean("dark_theme", settingsDarkTheme).apply()
                    onThemeChange(settingsDarkTheme)
                }
            )
        }
        AppScreen.MORE_MENU -> {
            MoreMenuScreen(
                onBack = { navigateTo(AppScreen.HUB) },
                onOpenRoute = { navigateTo(AppScreen.ROUTE_LIST) },
                onOpenReports = { navigateTo(AppScreen.REPORTS) },
                onOpenSettings = { navigateTo(AppScreen.SETTINGS) },
                onOpenHistory = {
                    importScope.launch {
                        archivedRoutes = runCatching { LogisticsRepository(context).history() }
                            .getOrElse { error ->
                                Toast.makeText(context, "Не удалось загрузить историю: ${error.message}", Toast.LENGTH_LONG).show()
                                emptyList()
                            }
                        navigateTo(AppScreen.HISTORY)
                    }
                },
                onOpenProducts = {
                    importScope.launch {
                        productCatalog = runCatching { LogisticsRepository(context).searchProducts("") }.getOrElse { emptyList() }
                        navigateTo(AppScreen.PRODUCTS)
                    }
                },
                onOpenUnknown = {
                    unknownCodes = prefs.getStringSet("unknown_codes", emptySet())?.toList().orEmpty()
                    navigateTo(AppScreen.UNKNOWN_CODES)
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
                initialImageUri = photoRouteInitialUri,
                onInitialActionConsumed = { photoRouteInitialAction = null },
                onInitialImageConsumed = { photoRouteInitialUri = null },
                onBack = { photoRouteInitialAction = null; photoRouteInitialUri = null; navigateBack() },
                onApplyRoute = { imported, header ->
                    routeHeader = header
                    prefs.edit()
                        .putString("route_header_number", header.routeNumber)
                        .putString("route_header_driver", header.driver)
                        .putString("route_header_vehicle", header.vehicle)
                        .putString("route_header_vehicle_number", header.vehicleNumber)
                        .apply()
                    sessionScanCodes = emptySet()
                    prefs.edit().remove("scan_codes").apply()
                    val content = imported.joinToString("\n") { item ->
                        listOf(item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(), item.phone, item.note,
                            item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(), item.ownerName, item.workingHours,
                            item.cashNote, item.extraComment).joinToString("\t") { it.replace("\t", " ") }
                    }
                    prefs.edit().putString("active_manifest", content).putBoolean("manual_route_order", false).apply()
                    routeItems = imported
                    importScope.launch {
                        runCatching { LogisticsRepository(context).replaceActiveRoute(imported) }
                            .onFailure { error ->
                                Toast.makeText(context, "Маршрут добавлен, но база не обновлена: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    navigateTo(AppScreen.ROUTE_LIST)
                    Toast.makeText(context, "Маршрут добавлен: ${imported.size} точек\\n${header.driver} • ${header.vehicle} ${header.vehicleNumber} • рейс ${header.routeNumber}", Toast.LENGTH_LONG).show()
                },
            )
        }
        AppScreen.ROUTE_LIST -> {
            RouteListScreen(
                routeItems = routeItems,
                routeHeader = routeHeader,
                compactMode = settingsCompact,
                onBackToHub = { navigateTo(AppScreen.HUB) },
                onOpenScanner = { openScanner() },
                onEditRoute = { /* Редактирование порядка выполняется прямо в списке маршрута. */ },
                onNavigateSegment = { index, segment ->
                    val manualOrder = prefs.getBoolean("manual_route_order", false)
                    val navigationItems = routeForNavigation(routeItems, manualOrder)
                    if (!manualOrder && navigationItems.map(::routeItemKey) != routeItems.map(::routeItemKey)) {
                        routeItems = navigationItems
                        importScope.launch { runCatching { LogisticsRepository(context).syncOrder(navigationItems) } }
                        val saved = navigationItems.joinToString("\n") { item ->
                            listOf(item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(),
                                item.phone, item.note, item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(),
                                item.ownerName, item.workingHours, item.cashNote, item.extraComment)
                                .joinToString("\t") { it.replace("\t", " ") }
                        }
                        prefs.edit().putString("active_manifest", saved).apply()
                    }
                    val pending = navigationItems.groupBy { PhotoRouteTextParser.canonicalAddressKey(it.address) }
                        .values.filter { group -> deliveryPointStatus(group) != DeliveryPointStatus.DELIVERED }
                    val coords = pending.mapNotNull { group ->
                        group.firstOrNull()?.let { point ->
                            if (point.latitude != null && point.longitude != null) PhotoRouteCoordinate(point.latitude, point.longitude) else null
                        }
                    }
                    val nextSegments = if (coords.size == pending.size && coords.size >= 2)
                        PhotoRouteNavigationUrls.yandexSegments(coords) else emptyList()
                    val chosen = nextSegments.getOrNull(index) ?: segment
                    openYandexNavigator(context, chosen, startsFromPreviousStop = index > 0)
                },
                onRebuildRoute = {
                    val reordered = rebuildRemainingRoute(routeItems)
                    prefs.edit().putBoolean("manual_route_order", false).apply()
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
                onOpenPointMap = { address ->
                    val mapUri = Uri.parse("https://yandex.ru/maps/?text=" + Uri.encode(address))
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, mapUri)) }
                        .onFailure { Toast.makeText(context, "Не удалось открыть точку в Яндекс Картах", Toast.LENGTH_SHORT).show() }
                },
                onCallClient = { phone -> makePhoneCall(phone) },
                onPointClick = { pointItems ->
                    selectedPointItems = pointItems
                    navigateTo(AppScreen.POINT_DETAILS)
                },
                onToggleItem = { item -> updateItemStatus(item) },
                onApplyAlternative = { orderedIds ->
                    val grouped = routeItems.groupBy { PhotoRouteTextParser.canonicalAddressKey(it.address) }
                    val reordered = orderedIds.flatMap { id -> grouped[id].orEmpty() } +
                        grouped.filterKeys { key -> key !in orderedIds }.values.flatten()
                    routeItems = reordered
                    importScope.launch { runCatching { LogisticsRepository(context).syncOrder(reordered) } }
                    val saved = reordered.joinToString("\n") { item ->
                        listOf(item.barcode, item.storeName, item.address, item.itemName, item.totalBoxes.toString(),
                            item.phone, item.note, item.latitude?.toString().orEmpty(), item.longitude?.toString().orEmpty(),
                            item.ownerName, item.workingHours, item.cashNote, item.extraComment)
                            .joinToString("\t") { it.replace("\t", " ") }
                    }
                    prefs.edit().putString("active_manifest", saved).apply()
                    Toast.makeText(context, "Выбранный порядок точек применён", Toast.LENGTH_SHORT).show()
                },
                onMoveGroup = { from, to ->
                    val groups = routeItems.groupBy { PhotoRouteTextParser.canonicalAddressKey(it.address) }.toList().toMutableList()
                    if (from in groups.indices && to in groups.indices) {
                        val moved = groups.removeAt(from)
                        groups.add(to, moved)
                        val reorderedItems = groups.flatMap { it.second }
                        routeItems = reorderedItems
                        prefs.edit().putBoolean("manual_route_order", true).apply()
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
        AppScreen.DETAILS -> {
            selectedItem?.let { item ->
                PointDetailsScreen(
                    item = item,
                    onBack = { navigateTo(AppScreen.ROUTE_LIST) },
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
                navigateTo(AppScreen.ROUTE_LIST)
            }
        }
        AppScreen.POINT_DETAILS -> {
            PointGroupScreen(
                items = selectedPointItems,
                onBack = { navigateTo(AppScreen.ROUTE_LIST) },
                onOpenInvoice = { item ->
                    selectedItem = item
                    navigateTo(AppScreen.DETAILS)
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
                onBack = { navigateTo(AppScreen.SCANNER) },
                onOpenPoint = { item ->
                    selectedItem = item
                    navigateTo(AppScreen.DETAILS)
                }
            )
        }
        AppScreen.REPORTS -> {
            ReportsScreen(
                routeItems,
                onBack = { navigateTo(AppScreen.HUB) },
                onExport = { reportLauncher.launch("DigiDrop_report.csv") },
                onExportExcel = { reportExcelLauncher.launch("DigiDrop_report.xlsx") },
                onExportPdf = { reportPdfLauncher.launch("DigiDrop_report.pdf") }
            )
        }
        AppScreen.SETTINGS -> {
            SettingsScreen(
                offlineMode = settingsOffline, autoSave = settingsAutoSave, compactMode = settingsCompact,
                darkTheme = settingsDarkTheme,
                onBack = { navigateTo(AppScreen.HUB) },
                onDarkThemeChange = { enabled ->
                    settingsDarkTheme = enabled
                    onThemeChange(enabled)
                },
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
                onBack = { navigateTo(AppScreen.HUB) },
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
                                navigateTo(AppScreen.ROUTE_LIST)
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
                onBack = { navigateTo(AppScreen.HUB) },
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
            UnknownCodesScreen(unknownCodes, onBack = { navigateTo(AppScreen.HUB) }, onClear = {
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
                    val currentDeliveryPoint = nextPendingPoint(routeItems)?.firstOrNull()
                    val savedCodes = sessionScanCodes.toMutableSet()
                    val isPlainProductCode = !lookup.hasUniquePackageId
                    when {
                        matches.size > 1 -> {
                            scanMatches = matches
                            navigateTo(AppScreen.SCAN_RESULTS)
                        }
                        matchedItem == null -> {
                            val unknown = prefs.getStringSet("unknown_codes", emptySet())?.toMutableSet() ?: mutableSetOf()
                            unknown.add(normalizedCode)
                            prefs.edit().putStringSet("unknown_codes", unknown).apply()
                            Toast.makeText(context, "Неизвестный код: $normalizedCode", Toast.LENGTH_LONG).show()
                            navigateTo(AppScreen.HUB)
                        }
                        isPlainProductCode -> {
                            if (isDifferentDeliveryPoint(matchedItem, currentDeliveryPoint)) {
                                Toast.makeText(
                                    context,
                                    "Внимание: упаковка относится к другой точке — ${matchedItem.storeName}, ${matchedItem.address}. Текущая точка: ${currentDeliveryPoint?.storeName}.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            val remaining = (matchedItem.totalBoxes - matchedItem.scannedBoxes).coerceAtLeast(0)
                            Toast.makeText(
                                context,
                                "${matchedItem.storeName} • ${matchedItem.itemName} • осталось $remaining из ${matchedItem.totalBoxes}. Обычный товарный EAN не подтверждает отдельную коробку.",
                                Toast.LENGTH_LONG
                            ).show()
                            selectedItem = matchedItem
                            navigateTo(AppScreen.DETAILS)
                        }
                        remainingBoxes(matchedItem) == 0 -> {
                            if (isDifferentDeliveryPoint(matchedItem, currentDeliveryPoint)) {
                                Toast.makeText(
                                    context,
                                    "Внимание: упаковка относится к другой точке — ${matchedItem.storeName}, ${matchedItem.address}. Текущая точка: ${currentDeliveryPoint?.storeName}.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            Toast.makeText(
                                context,
                                "${matchedItem.storeName}: все места по этой позиции уже учтены (${matchedItem.totalBoxes}). Повторный скан не засчитываю.",
                                Toast.LENGTH_LONG
                            ).show()
                            selectedItem = matchedItem
                            navigateTo(AppScreen.DETAILS)
                        }
                        normalizedCode in savedCodes -> {
                            if (isDifferentDeliveryPoint(matchedItem, currentDeliveryPoint)) {
                                Toast.makeText(
                                    context,
                                    "Внимание: упаковка относится к другой точке — ${matchedItem.storeName}, ${matchedItem.address}.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            Toast.makeText(context, "Дубликат уникального кода упаковки: $normalizedCode", Toast.LENGTH_LONG).show()
                            navigateTo(AppScreen.HUB)
                        }
                        else -> {
                            if (isDifferentDeliveryPoint(matchedItem, currentDeliveryPoint)) {
                                Toast.makeText(
                                    context,
                                    "Внимание: упаковка относится к другой точке — ${matchedItem.storeName}, ${matchedItem.address}. Текущая точка: ${currentDeliveryPoint?.storeName}.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
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
                            importScope.launch { runCatching { persistRouteProgress { LogisticsRepository(context).syncProgress(updatedRoute) } } }
                            selectedItem = updated
                            try {
                                ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_ACK, 120)
                            } catch (_: Exception) {}
                            Toast.makeText(context, "Скан принят: ${updated.scannedBoxes} / ${updated.totalBoxes}", Toast.LENGTH_SHORT).show()
                            navigateTo(AppScreen.DETAILS)
                        }
                    }
                }
            )
        }
        }
        if (currentScreen != AppScreen.SPLASH) {
            Box(
                Modifier.align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 12.dp, bottom = 12.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable {
                        settingsDarkTheme = !settingsDarkTheme
                        prefs.edit().putBoolean("dark_theme", settingsDarkTheme).apply()
                        onThemeChange(settingsDarkTheme)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("◐", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "splashAppear"
    )
    val transition = rememberInfiniteTransition(label = "routeSplash")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "routeProgress"
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(2600)
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(DigiDropPalette.darkBackground, DigiDropPalette.darkSurface))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp)
                .graphicsLayer { alpha = appear; scaleX = .97f + .03f * appear; scaleY = .97f + .03f * appear },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.fillMaxWidth().height(210.dp)) {
                val unit = size.minDimension / 10f
                val qrLeft = size.width * .08f
                val qrTop = size.height * .20f
                val qrSize = size.height * .52f
                val cell = qrSize / 7f
                val white = Color(0xFFF7F3EC)
                val orange = DigiDropPalette.accent
                // QR finder squares.
                listOf(0 to 0, 4 to 0, 0 to 4).forEach { (x, y) ->
                    val left = qrLeft + x * cell
                    val top = qrTop + y * cell
                    drawRect(white, Offset(left, top), Size(cell * 3, cell * 3))
                    drawRect(DigiDropPalette.darkSurface, Offset(left + cell * .55f, top + cell * .55f), Size(cell * 1.9f, cell * 1.9f))
                    drawRect(white, Offset(left + cell, top + cell), Size(cell, cell))
                }
                listOf(3 to 3, 5 to 4, 3 to 6, 6 to 6).forEach { (x, y) ->
                    drawRect(white, Offset(qrLeft + x * cell, qrTop + y * cell), Size(cell * .72f, cell * .72f))
                }
                // Scanner sweep.
                val scanY = qrTop + qrSize * progress
                drawLine(orange, Offset(qrLeft - 5.dp.toPx(), scanY), Offset(qrLeft + qrSize + 5.dp.toPx(), scanY),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                val startPoint = Offset(size.width * .61f, size.height * .72f)
                val firstPoint = Offset(size.width * .72f, size.height * .42f)
                val endPoint = Offset(size.width * .91f, size.height * .22f)
                val routeProgress = ((progress - .28f) / .72f).coerceIn(0f, 1f)
                val firstLegProgress = (routeProgress / .5f).coerceIn(0f, 1f)
                val secondLegProgress = ((routeProgress - .5f) / .5f).coerceIn(0f, 1f)
                if (routeProgress > 0f) {
                    drawLine(orange.copy(alpha = .9f), startPoint,
                        Offset(startPoint.x + (firstPoint.x - startPoint.x) * firstLegProgress,
                            startPoint.y + (firstPoint.y - startPoint.y) * firstLegProgress),
                        strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                }
                if (routeProgress > .5f) {
                    drawLine(orange.copy(alpha = .9f), firstPoint,
                        Offset(firstPoint.x + (endPoint.x - firstPoint.x) * secondLegProgress,
                            firstPoint.y + (endPoint.y - firstPoint.y) * secondLegProgress),
                        strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                }
                listOf(startPoint, firstPoint, endPoint).forEachIndexed { index, point ->
                    if (index == 0 || routeProgress >= if (index == 1) .35f else .85f) {
                        drawCircle(orange, radius = if (index == 2) 9.dp.toPx() else 7.dp.toPx(), center = point)
                        drawCircle(DigiDropPalette.darkBackground, radius = 3.dp.toPx(), center = point)
                    }
                }
                // Destination pin.
                if (routeProgress > .82f) {
                    val pin = endPoint
                    drawCircle(orange, radius = 12.dp.toPx(), center = pin)
                    drawCircle(white, radius = 4.dp.toPx(), center = pin)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Digi", color = DigiDropPalette.darkText, fontSize = 35.sp, fontWeight = FontWeight.ExtraBold)
                Text("Drop", color = DigiDropPalette.accentLight, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("МАРШРУТЫ  •  ДОСТАВКА  •  QR", color = DigiDropPalette.darkTextSecondary,
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(42.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(.62f).height(4.dp),
                color = DigiDropPalette.accent,
                trackColor = DigiDropPalette.darkBorder
            )
            Spacer(Modifier.height(12.dp))
            Text("Сканирование  •  Точка  •  Маршрут", color = DigiDropPalette.darkTextSecondary, fontSize = 12.sp)
        }
    }
}


@Composable
fun InvoiceLandingScreen(
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onLoadInvoice: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("← Назад", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(8.dp))
        Text("Добавить маршрут", color = MaterialTheme.colorScheme.onSurface, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("Выберите источник. После обработки откроется список точек маршрута.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        HubTile("Сфотографировать", "Открыть камеру и распознать маршрутный лист", "▧", MaterialTheme.colorScheme.primary,
            Modifier.fillMaxWidth(), onTakePhoto)
        HubTile("Выбрать файл или фото", "Загрузить PDF, Excel, CSV или снимок из галереи", "⇧", MaterialTheme.colorScheme.secondary,
            Modifier.fillMaxWidth(), onLoadInvoice)
    }
}

@Composable
fun HubScreen(
    routeItems: List<RouteItem>,
    routeHeader: PhotoRouteHeader,
    onOpenMenu: () -> Unit,
    onOpenScanner: () -> Unit,
    onLoadInvoice: () -> Unit,
    onOpenRoute: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onThemeToggle: () -> Unit = {}
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding().padding(22.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onLoadInvoice, modifier = Modifier.fillMaxWidth().height(76.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DigiDropPalette.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 1.dp),
                shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text("Маршрут по фото / накладной", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onOpenScanner, modifier = Modifier.fillMaxWidth().height(68.dp),
                shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Сканер", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            if (routeItems.isNotEmpty()) TextButton(onClick = onOpenRoute) {
                Text("Открыть текущий маршрут • ${routeItems.size} позиций")
            }
        }
    }
}

@Composable
private fun HubMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun HubQuickAction(symbol: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.heightIn(min = 84.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .bounceClick { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp, pressedElevation = 1.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val icon = when (label) {
                "Сканер" -> Icons.Outlined.QrCodeScanner
                "Карта" -> Icons.Outlined.Map
                "Отчёты" -> Icons.Outlined.Assessment
                else -> Icons.Outlined.Settings
            }
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(27.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
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
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("← Назад", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp,
            modifier = Modifier.bounceClick { onBack() })
        Text("Дополнительно", color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        HubTile("Точки и карта маршрута", "Список, статусы, порядок и навигация", "⌖", MaterialTheme.colorScheme.primary,
            Modifier.fillMaxWidth(), onOpenRoute)
        HubTile("Следующая точка", "Открыть Яндекс Навигатор", "➜", MaterialTheme.colorScheme.primary,
            Modifier.fillMaxWidth(), onNavigateNext)
        HubTile("Отчёты и экспорт", "Сводка и выгрузки", "▤", DigiDropPalette.accent,
            Modifier.fillMaxWidth(), onOpenReports)
        HubTile("История рейсов", "Архив завершённых маршрутов", "◷", MaterialTheme.colorScheme.secondary,
            Modifier.fillMaxWidth(), onOpenHistory)
        HubTile("Каталог товаров", "Локальная база штрихкодов", "▦", DigiDropPalette.accent,
            Modifier.fillMaxWidth(), onOpenProducts)
        HubTile("Неизвестные коды", "Коды вне накладных", "?", DigiDropPalette.primary,
            Modifier.fillMaxWidth(), onOpenUnknown)
        HubTile("Настройки и резервная копия", "Автосохранение, офлайн-режим, ZIP", "⚙", DigiDropPalette.primary,
            Modifier.fillMaxWidth(), onOpenSettings)
        HubTile("Экспорт CSV", "Выгрузить текущий маршрут", "⇩", DigiDropPalette.accent,
            Modifier.fillMaxWidth(), onExport)
        HubTile("Позвонить диспетчеру", "Связаться по рабочему номеру", "☎", DigiDropPalette.accent,
            Modifier.fillMaxWidth(), onCallDispatcher)
        HubTile("Сбросить прогресс рейса", "Очистить отметки сканирования и статусы", "↺", DigiDropPalette.primaryDark,
            Modifier.fillMaxWidth(), onClick = { confirmReset = true })
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить прогресс рейса?") },
            text = { Text("Будут очищены отметки сканирования, доставки и расхождения. Сам маршрут останется.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onResetRoute() }) { Text("Сбросить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
fun HubTile(
    title: String,
    subtitle: String,
    symbol: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    val tileColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val foreground = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val secondary = if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier.heightIn(min = if (primary) 132.dp else 112.dp)
            .border(1.dp, if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            .bounceClick(scaleDown = 0.985f) { onClick() },
        colors = CardDefaults.cardColors(containerColor = tileColor),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp, pressedElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (primary) {
                Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
            } else {
                Text(symbol, color = accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    title,
                    color = foreground,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    softWrap = true
                )
                Text(
                    subtitle,
                    color = secondary,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 3
                )
            }
            Text("›", color = foreground, fontSize = 26.sp)
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
    val palette = MaterialTheme.colorScheme
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "← Назад",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.bounceClick { onBack() }
                    )
                    Text("СКАНЕР", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(if (isFlashOn) DigiDropPalette.accentDark else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (isFlashOn) palette.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .bounceClick { onFlashToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        FlashlightVector(tint = if (isFlashOn) palette.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
            ) {
                Text(
                    text = "Наведите рамку на штрихкод накладной",
                    color = MaterialTheme.colorScheme.onSurface,
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

                    drawLine(palette.secondary, Offset(0f, 0f), Offset(cornerLen, 0f), strokeWidth = stroke)
                    drawLine(palette.secondary, Offset(0f, 0f), Offset(0f, cornerLen), strokeWidth = stroke)

                    drawLine(palette.secondary, Offset(w, 0f), Offset(w - cornerLen, 0f), strokeWidth = stroke)
                    drawLine(palette.secondary, Offset(w, 0f), Offset(w, cornerLen), strokeWidth = stroke)

                    drawLine(palette.secondary, Offset(0f, h), Offset(cornerLen, h), strokeWidth = stroke)
                    drawLine(palette.secondary, Offset(0f, h), Offset(0f, h - cornerLen), strokeWidth = stroke)

                    drawLine(palette.secondary, Offset(w, h), Offset(w - cornerLen, h), strokeWidth = stroke)
                    drawLine(palette.secondary, Offset(w, h), Offset(w, h - cornerLen), strokeWidth = stroke)

                    val currentLaserY = h * laserY
                    drawLine(
                        color = palette.primary,
                        start = Offset(12.dp.toPx(), currentLaserY),
                        end = Offset(w - 12.dp.toPx(), currentLaserY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(palette.primary))
                        Text("Готов к сканированию", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Наведите камеру на код коробки. DigiDrop автоматически найдёт накладную и точку доставки.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("Поддерживаются: EAN-13 • Data Matrix • QR • Code 128",
                        color = palette.secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
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
    val groups = matches.groupBy { PhotoRouteTextParser.canonicalAddressKey(it.address) }.values.map { group -> (group.first().storeName to group.first().address) to group }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(18.dp)
    ) {
        Text("← Назад к сканеру", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(18.dp))
        Text("Найдено несколько назначений", color = MaterialTheme.colorScheme.onSurface, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Код: $code", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))
        Text("Обычный товарный штрихкод не указывает на одну конкретную коробку. Выбери нужную точку по данным накладной.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(groups, key = { it.first.first + "|" + it.first.second }) { entry ->
                val pointItems = entry.second
                val total = pointItems.sumOf { it.totalBoxes }
                val scanned = pointItems.sumOf { it.scannedBoxes }
                Card(
                    modifier = Modifier.fillMaxWidth().bounceClick { onOpenPoint(pointItems.first()) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(pointItems.first().storeName, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(pointItems.first().address, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("По накладным: $total", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            Text("Осталось: ${(total - scanned).coerceAtLeast(0)}", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${pointItems.size} строк(и) накладной • нажми для подробностей", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StoreGroupCard(
    items: List<RouteItem>,
    compactMode: Boolean = true,
    onClick: () -> Unit,
    onMapClick: () -> Unit,
    onCallClick: () -> Unit
) {
    val first = items.first()
    val total = items.sumOf { it.totalBoxes }
    val delivered = deliveryPointStatus(items) == DeliveryPointStatus.DELIVERED
    val hasProblem = items.any { it.discrepancyQty > 0 }
    Card(
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .bounceClick(scaleDown = 0.985f, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(first.storeName.ifBlank { "Точка доставки" }, color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(first.address, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 2)
                val details = listOf(first.workingHours, first.phone).filter { it.isNotBlank() }.joinToString(" • ")
                if (details.isNotBlank()) Text(details, color = DigiDropPalette.accent, fontSize = 11.sp, maxLines = 1)
                Text("$total мест • ${items.size} накладных",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(when { hasProblem -> "Проблема"; delivered -> "Сдано"; else -> "В пути" },
                    color = when { hasProblem -> Color(0xFFB3261E); delivered -> DigiDropPalette.accent; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(DigiDropPalette.primarySoft)
                        .bounceClick { onMapClick() }, contentAlignment = Alignment.Center) { MapIconVector(DigiDropPalette.accent) }
                    Box(Modifier.size(32.dp).clip(CircleShape).background(DigiDropPalette.primarySoft)
                        .bounceClick { onCallClick() }, contentAlignment = Alignment.Center) { PhoneIconVector(DigiDropPalette.accent) }
                }
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(18.dp)) {
        Text("← Точки маршрута", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(16.dp))
        Text(items.firstOrNull()?.storeName.orEmpty(), color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(items.firstOrNull()?.address.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        items.firstOrNull()?.let { point ->
            if (point.ownerName.isNotBlank()) Text("ИП / контакт: ${point.ownerName}", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            if (point.phone.isNotBlank()) Text("Телефон: ${point.phone}", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            if (point.workingHours.isNotBlank()) Text("Время работы: ${point.workingHours}", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            if (point.cashNote.isNotBlank()) Text("Наличные: ${point.cashNote}", color = DigiDropPalette.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (point.extraComment.isNotBlank()) Text("Дополнительно: ${point.extraComment}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            if (point.note.isNotBlank()) Text("Комментарий: ${point.note}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Накладных/строк: ${items.size}", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Text("Всего мест: $total", color = MaterialTheme.colorScheme.secondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text("Учтено уникальных сканов: $scanned", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        HubActionButton("Проверить и закрыть точку", "Все места должны быть учтены сканами или расхождениями с причиной", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.secondary, onClosePoint)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HubActionButton("Навигатор", "Открыть точку", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.secondary, onNavigate)
            HubActionButton("Позвонить", "Связаться с точкой", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.secondary, onCall)
        }
        Spacer(Modifier.height(14.dp))
        Text("Состав точки", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { item ->
                Card(Modifier.fillMaxWidth().bounceClick { onOpenInvoice(item) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(item.itemName, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Штрихкод: ${item.barcode}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Мест: ${item.totalBoxes}", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                            Text(
                                when { item.discrepancyQty > 0 -> "Расхождение"; item.isDelivered -> "Сдано"; else -> "В пути" },
                                color = when { item.discrepancyQty > 0 -> DigiDropPalette.accentDark; item.isDelivered -> MaterialTheme.colorScheme.secondary; else -> MaterialTheme.colorScheme.primary },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("Нажми, чтобы открыть накладную", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
            .background(MaterialTheme.colorScheme.background)
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
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.bounceClick { onBack() }
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (item.isDelivered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (item.isDelivered) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = when { item.discrepancyQty > 0 -> "Расхождение"; item.isDelivered -> "Сдано"; else -> "В пути" },
                        color = if (item.discrepancyQty > 0) DigiDropPalette.primaryDark else if (item.isDelivered) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = item.storeName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.address,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("ДАННЫЕ ТОЧКИ", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (item.ownerName.isNotBlank()) Text("ИП / контакт: ${item.ownerName}", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    if (item.phone.isNotBlank()) Text("Телефон: ${item.phone}", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    if (item.workingHours.isNotBlank()) Text("Время работы: ${item.workingHours}", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    if (item.cashNote.isNotBlank()) Text("НАЛИЧНЫЕ: ${item.cashNote}", color = DigiDropPalette.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (item.extraComment.isNotBlank()) Text("Дополнительно: ${item.extraComment}", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Состав накладной к выгрузке",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.itemName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Отсканировано: ${item.scannedBoxes} / ${item.totalBoxes}",
                        color = if (item.scannedBoxes >= item.totalBoxes) DigiDropPalette.accent else DigiDropPalette.accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(DigiDropPalette.primarySoft)) {
                        Box(
                            Modifier.fillMaxWidth((item.scannedBoxes.toFloat() / item.totalBoxes.coerceAtLeast(1)).coerceIn(0f, 1f))
                                .height(9.dp).clip(CircleShape).background(DigiDropPalette.accent)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Всего мест к сдаче:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text(
                            text = "" + item.totalBoxes + " мест",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Штрихкод / ТТН:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text(
                            text = item.barcode,
                            color = MaterialTheme.colorScheme.onSurface,
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
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Инструкция к заезду",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.note,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                }
            }

            HubActionButton(
                if (item.signaturePath.isNotBlank()) "Обновить подпись получателя" else "Подпись получателя",
                "Подтверждение передачи груза",
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.onSurface,
                MaterialTheme.colorScheme.secondary
            ) { showSignatureDialog = true }
            if (signaturePreview != null) {
                Image(
                    bitmap = signaturePreview,
                    contentDescription = "Подпись получателя",
                    modifier = Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface).padding(5.dp),
                    contentScale = ContentScale.Fit
                )
                Text("Подпись получателя сохранена на устройстве", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
            }
            HubActionButton(
                if (item.photoDocUri.isNotBlank()) "Заменить фото доказательства" else "Добавить фото доказательства",
                "Сохранить фото в памяти телефона",
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.onSurface,
                MaterialTheme.colorScheme.secondary,
                onAddPhoto
            )
            if (photoPreview != null) {
                Image(
                    bitmap = photoPreview,
                    contentDescription = "Фото доказательства доставки",
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Text("Фото доказательства хранится на устройстве", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
            }
            HubActionButton(
                if (item.discrepancyQty > 0) "Изменить расхождение: ${item.discrepancyQty}" else "Зафиксировать расхождение",
                "Недовоз, брак или излишек",
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.onSurface,
                DigiDropPalette.primary
            ) { showDiscrepancyDialog = true }
            if (item.discrepancyQty > 0) {
                Text(
                    "Текущее расхождение: ${item.discrepancyReason ?: "причина не указана"} • ${item.discrepancyQty} мест",
                    color = DigiDropPalette.primary,
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
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(26.dp))
                        .bounceClick { onCall() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Позвонить", color = MaterialTheme.colorScheme.secondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp))
                        .bounceClick { onNavigate() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Навигатор", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(if (item.isDelivered) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary)
                    .border(
                        1.dp,
                        if (item.isDelivered) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(27.dp)
                    )
                    .bounceClick { onToggleDelivered() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (item.isDelivered) "Отменить сдачу (вернуть в путь)" else "Подтвердить сдачу точки",
                    color = if (item.isDelivered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.background,
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
                                    Text(value, color = if (discrepancyReason == value) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (discrepancyError.isNotBlank()) {
                            Text(discrepancyError, color = DigiDropPalette.primaryDark, fontSize = 12.sp)
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
                Text("Попроси получателя расписаться пальцем в поле ниже.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Canvas(
                    Modifier.fillMaxWidth().height(180.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
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
private fun RoutePhotoThumbnail(uri: Uri) {
    Card(Modifier.size(width = 82.dp, height = 62.dp), shape = RoundedCornerShape(9.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        AndroidView(factory = { ctx -> ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageURI(uri)
        } }, modifier = Modifier.fillMaxSize())
    }
}

private fun formatCompactRouteAddress(rawAddress: String): String {
    val cleaned = rawAddress
        .replace(Regex("""(?iu)\b(?:контакт|телефон|оплата|комментарий|доставка|штрихкод)\b.*$"""), "")
        .replace(Regex("""[=|]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', ',', ';', '.', ':')
    val streetMatch = Regex("""(?iu)(?:ул\.?|улица|пр-т|проспект|пер\.?|переулок|шоссе|наб\.?|набережная|б-р|бульвар)\s*[^,;]+""").find(cleaned)
        ?: return cleaned.take(1).uppercase() + cleaned.drop(1)
    val streetPart = streetMatch.value.trim()
    val houseMatch = Regex("""(?iu)(?:д\.?\s*)?[-–—]?\s*(\d{1,4}\s*[а-яa-z]?)(?:\s*(?:к\.?|корп\.?|стр\.?|строение)\s*\d+[а-яa-z]?)?""")
        .find(cleaned, streetMatch.range.last + 1)
    val house = houseMatch?.groupValues?.getOrNull(1)?.replace(Regex("""\s+"""), "")?.trim()
    return if (house.isNullOrBlank()) streetPart else "$streetPart, $house"
}

@Composable
fun RouteListScreen(
    routeItems: List<RouteItem>,
    routeHeader: PhotoRouteHeader,
    compactMode: Boolean,
    onBackToHub: () -> Unit,
    onOpenScanner: () -> Unit,
    onEditRoute: () -> Unit,
    onNavigateSegment: (Int, List<PhotoRouteCoordinate>) -> Unit,
    onRebuildRoute: () -> Unit,
    onOpenPointMap: (String) -> Unit,
    onCallClient: (String) -> Unit,
    onPointClick: (List<RouteItem>) -> Unit,
    onToggleItem: (RouteItem) -> Unit,
    onMoveGroup: (Int, Int) -> Unit,
    onApplyAlternative: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val groups = remember(routeItems) {
        routeItems.groupBy { PhotoRouteTextParser.canonicalAddressKey(it.address) }.values.toList()
    }
    val pendingGroups = groups.filter { group -> deliveryPointStatus(group) != DeliveryPointStatus.DELIVERED }
    val points = pendingGroups.mapNotNull { group ->
        val first = group.firstOrNull() ?: return@mapNotNull null
        if (first.latitude != null && first.longitude != null) PhotoRouteCoordinate(first.latitude, first.longitude) else null
    }
    val missingCoordinates = points.size != pendingGroups.size
    val segments = remember(points, missingCoordinates) {
        if (!missingCoordinates && points.size >= 2) PhotoRouteNavigationUrls.yandexSegments(points) else emptyList()
    }
    var navigationSegmentIndex by remember(segments) { mutableStateOf(0) }
    val photoPrefs = remember { context.getSharedPreferences("digitdrop_photo_route_session", Context.MODE_PRIVATE) }
    val photos = remember {
        runCatching {
            val array = org.json.JSONArray(photoPrefs.getString("captured_uris", "[]").orEmpty())
            (0 until array.length()).mapNotNull { index -> runCatching { Uri.parse(array.optString(index)) }.getOrNull() }
        }.getOrDefault(emptyList())
    }
    var expandedPoint by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("← Хаб", color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable { onBackToHub() }.padding(6.dp))
            Spacer(Modifier.width(8.dp))
            Text("Маршрут • ${groups.size}", color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenScanner) { Text("Сканер") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            item(key = "route_top") {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (photos.isNotEmpty()) {
                        Text("ФОТО НАКЛАДНОЙ • ${photos.size}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            photos.forEach { RoutePhotoThumbnail(it) }
                        }
                    }
                    val header = listOf(routeHeader.routeNumber, routeHeader.driver, routeHeader.vehicle, routeHeader.vehicleNumber)
                        .filter { it.isNotBlank() }.joinToString(" • ")
                    if (header.isNotBlank()) Text(header, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Button(onClick = onRebuildRoute, enabled = groups.size >= 2,
                        modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(13.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)) {
                        Text("Оптимизировать маршрут", fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = {
                        if (segments.isNotEmpty()) {
                            val index = navigationSegmentIndex.coerceIn(0, segments.lastIndex)
                            onNavigateSegment(index, segments[index])
                            navigationSegmentIndex = nextNavigationSegmentIndex(index, segments.size)
                        } else Toast.makeText(context,
                            if (missingCoordinates) "Не у всех незавершённых точек определены координаты" else "Нужны минимум две незавершённые точки",
                            Toast.LENGTH_LONG).show()
                    }, enabled = segments.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(13.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)) {
                        Text(
                            if (segments.size > 1 && navigationSegmentIndex > 0) "Следующий участок ${navigationSegmentIndex + 1} из ${segments.size}"
                            else "В Яндекс Навигатор",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text("Точки можно переставлять за значок справа. Адрес проверяется отдельно в Яндекс Картах.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (groups.isEmpty()) {
                item(key = "empty") { Text("Маршрут пока пуст. Загрузите накладную или маршрутный лист.",
                    modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                itemsIndexed(groups, key = { index, group ->
                    PhotoRouteTextParser.canonicalAddressKey(group.first().address).ifBlank { "point_${index}" }
                }) { index, group ->
                    val point = group.first()
                    val key = PhotoRouteTextParser.canonicalAddressKey(point.address)
                    val expanded = expandedPoint == key
                    var dragAmount by remember(key) { mutableStateOf(0f) }
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}.", color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(6.dp))
                                Text(point.storeName.ifBlank { "Точка без названия" },
                                    modifier = Modifier.weight(1f).clickable { expandedPoint = if (expanded) null else key },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("⠿", modifier = Modifier.clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp)
                                    .pointerInput(key, index, groups.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragEnd = { dragAmount = 0f },
                                            onDragCancel = { dragAmount = 0f },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragAmount += amount.y
                                                if (kotlin.math.abs(dragAmount) > 40f) {
                                                    val target = (index + if (dragAmount > 0) 1 else -1).coerceIn(0, groups.lastIndex)
                                                    if (target != index) onMoveGroup(index, target)
                                                    dragAmount = 0f
                                                }
                                            }
                                        )
                                    }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (point.workingHours.isNotBlank()) Text("◷ ${point.workingHours}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                if (point.phone.isNotBlank()) {
                                    Spacer(Modifier.width(9.dp))
                                    Text("☎ ${point.phone}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                            val comment = point.extraComment.ifBlank { point.note }
                            if (comment.isNotBlank()) Text(comment, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            val status = deliveryPointStatus(group)
                            val totalBoxes = group.sumOf { it.totalBoxes.coerceAtLeast(0) }
                            val scannedBoxes = group.sumOf { it.scannedBoxes.coerceAtLeast(0) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (status == DeliveryPointStatus.DELIVERED) "СДАНО" else "НЕ СДАНО",
                                    color = if (status == DeliveryPointStatus.DELIVERED)
                                        MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("$scannedBoxes / $totalBoxes мест",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        val targets = if (group.all { it.isDelivered }) group.filter { it.isDelivered }
                                            else group.filterNot { it.isDelivered }
                                        targets.forEach(onToggleItem)
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        if (group.all { it.isDelivered }) "Вернуть" else "Отметить сдачу",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            if (point.address.isNotBlank()) {
                                Text(
                                    formatCompactRouteAddress(point.address),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (point.phone.isNotBlank()) {
                                    Text("Позвонить", modifier = Modifier.clickable { onCallClient(point.phone) }.padding(vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                                }
                                Text("Проверить в Яндексе", modifier = Modifier.clickable { onOpenPointMap(point.address) }.padding(vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                            }
                            if (expanded) {
                                Text("Открыть документы и товары точки",
                                    modifier = Modifier.clickable { onPointClick(group) }.padding(vertical = 5.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopBarSection(
    title: String,
    isFlashOn: Boolean,
    onFlashToggle: () -> Unit
) {
    val palette = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
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
                                    palette.secondary.copy(alpha = 0.55f),
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
                    .background(if (isFlashOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
                    .border(1.dp, if (isFlashOn) palette.secondary else MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                FlashlightVector(tint = if (isFlashOn) palette.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
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
    val icon = when {
        title.contains("наклад", ignoreCase = true) || title.contains("загруз", ignoreCase = true) ->
            com.example.qrhelper.ui.theme.DigiDropIcon.MANIFEST
        title.contains("маршрут", ignoreCase = true) || title.contains("точк", ignoreCase = true) ->
            com.example.qrhelper.ui.theme.DigiDropIcon.ROUTE
        title.contains("скан", ignoreCase = true) || title.contains("штрих", ignoreCase = true) ->
            com.example.qrhelper.ui.theme.DigiDropIcon.SCAN
        title.contains("отч", ignoreCase = true) || title.contains("истор", ignoreCase = true) ->
            com.example.qrhelper.ui.theme.DigiDropIcon.REPORTS
        title.contains("настр", ignoreCase = true) ->
            com.example.qrhelper.ui.theme.DigiDropIcon.SETTINGS
        title.contains("товар", ignoreCase = true) || title.contains("каталог", ignoreCase = true) ->
            com.example.qrhelper.ui.theme.DigiDropIcon.INVENTORY
        else -> com.example.qrhelper.ui.theme.DigiDropIcon.HUB
    }
    com.example.qrhelper.ui.theme.DigiDropActionTile(
        title = title,
        subtitle = subtitle,
        icon = icon,
        accent = if (borderColor != null) borderColor else com.example.qrhelper.ui.theme.DigiDropPalette.accent,
        onClick = onClick
    )
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
                if (item.isDelivered) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                RoundedCornerShape(18.dp)
            )
            .bounceClick(scaleDown = 0.98f) { onCardClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
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
                    color = if (item.isDelivered) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.storeName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = item.itemName,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = "Сканировано: ${item.scannedBoxes} / ${item.totalBoxes}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Мест: " + item.totalBoxes + " • ТТН: " + item.barcode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        .background(MaterialTheme.colorScheme.outline)
                        .bounceClick(scaleDown = 0.88f) { onMapClick() },
                    contentAlignment = Alignment.Center
                ) {
                    MapIconVector(tint = MaterialTheme.colorScheme.secondary)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline)
                        .bounceClick(scaleDown = 0.88f) { onCallClick() },
                    contentAlignment = Alignment.Center
                ) {
                    PhoneIconVector(tint = MaterialTheme.colorScheme.secondary)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (item.isDelivered) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .border(2.dp, if (item.isDelivered) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline, CircleShape)
                        .bounceClick(scaleDown = 0.86f) { onToggleStatus() },
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isDelivered) {
                        CheckmarkVector(tint = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }
    }
}

@Composable
fun MapIconVector(tint: Color) {
    val surfaceColor = MaterialTheme.colorScheme.surface
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
        drawCircle(color = surfaceColor, radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.45f))
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
    val barColor = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
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
                .background(MaterialTheme.colorScheme.primary)
                .bounceClick { onRouteClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Маршрут",
                color = MaterialTheme.colorScheme.background,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(26.dp))
                .bounceClick { onScanClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Сканировать",
                color = MaterialTheme.colorScheme.secondary,
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("← Главная", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(18.dp))
        Text("Отчёты и сверка", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, fontWeight = FontWeight.Bold)
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
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
                shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("Состояние точек", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        ReportStatusBar("Доставлено", delivered, points.size, DigiDropPalette.accent)
        ReportStatusBar("В работе", inProgressPoints, points.size, DigiDropPalette.accent)
        ReportStatusBar("Ожидают", pendingPoints, points.size, DigiDropPalette.accentDark)
        ReportStatusBar("Расхождения", problemPoints, points.size, DigiDropPalette.primaryDark)
        Spacer(Modifier.height(8.dp))
        HubActionButton("Экспорт CSV", "Универсальный табличный формат", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.secondary, onExport)
        Spacer(Modifier.height(8.dp))
        HubActionButton("Экспорт Excel", "Файл XLSX с отдельными колонками и статусами", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.secondary, onExportExcel)
        Spacer(Modifier.height(8.dp))
        HubActionButton("Экспорт PDF", "Печатный отчёт по точкам и грузу", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.secondary, onExportPdf)
        Spacer(Modifier.height(8.dp))
        Text("В отчёты входят штрихкод, точка, адрес, товар, план, факт, расхождения, остаток и статус.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun ReportStatusBar(label: String, value: Int, total: Int, color: Color) {
    val fraction = if (total > 0) (value.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text("$value / $total", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.outline)
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
    darkTheme: Boolean,
    onBack: () -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onOfflineChange: (Boolean) -> Unit,
    onAutoSaveChange: (Boolean) -> Unit,
    onCompactChange: (Boolean) -> Unit,
    onClearUnknown: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onArchiveRoute: () -> Unit
) {
    var showArchiveConfirmation by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(18.dp)) {
        Text("← Главная", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(18.dp))
        Text("Настройки", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        SettingToggleRow("Офлайн-режим", "Не искать координаты через интернет при импорте; сохранить исходный порядок точек. Навигация запускается через Яндекс Навигатор.", offlineMode, onOfflineChange)
        SettingToggleRow("Автосохранение кодов упаковок", "Помнить уже отсканированные уникальные коды между перезапусками. Прогресс рейса всегда сохраняется в Room.", autoSave, onAutoSaveChange)
        SettingToggleRow("Компактный режим", "Уменьшает отступы и плотность карточек маршрута", compactMode, onCompactChange)
        SettingToggleRow("Графитовая тема", "Включает матовую тёмную палитру DigiDrop без чисто чёрного фона", darkTheme, onDarkThemeChange)
        Spacer(Modifier.height(12.dp))
        HubActionButton("Резервная копия", "Сохранить рейс, прогресс, настройки, фото и подписи в ZIP", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.secondary, onBackup)
        Spacer(Modifier.height(8.dp))
        HubActionButton("Восстановить копию", "Загрузить ZIP-резервную копию DigiDrop", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.secondary, onRestore)
        Spacer(Modifier.height(8.dp))
        HubActionButton("Завершить рейс и отправить в архив", "Проверить и сохранить текущий рейс в истории", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, DigiDropPalette.primary) {
            showArchiveConfirmation = true
        }
        Spacer(Modifier.height(8.dp))
        HubActionButton("Очистить неизвестные коды", "Удалить локальный список нераспознанных сканов", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, DigiDropPalette.primary, onClearUnknown)
        Spacer(Modifier.height(14.dp))
        Text("Резервная копия включает маршрут, сканы, расхождения, настройки и локальные фото/подписи.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Text(if (enabled) "ВКЛ" else "ВЫКЛ", color = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier
                    .clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 10.dp)
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(18.dp)) {
        Text("← Главная", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(18.dp))
        Text("История рейсов", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Архив хранится локально на этом устройстве.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        if (routes.isEmpty()) {
            Text("Завершённых или заменённых рейсов пока нет.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(routes, key = { it.routeId }) { route ->
                    val percent = if (route.expectedQty > 0) {
                        (route.scannedQty * 100 / route.expectedQty).coerceIn(0, 100)
                    } else 0
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
                        shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(route.routeNumber, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(formatter.format(Date(route.createdAt)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "${route.completedPointCount} / ${route.pointCount} точек завершено • ${route.scannedQty} / ${route.expectedQty} мест",
                                color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                            Text("Выполнение по сканам: $percent%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { onRestoreRoute(route.routeId) }) {
                                Text("Восстановить этот рейс", color = MaterialTheme.colorScheme.secondary)
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(18.dp)) {
        Text("← Главная", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.bounceClick { onBack() })
        Spacer(Modifier.height(18.dp))
        Text("Неизвестные коды", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Скан-коды, которых нет в загруженных накладных: ${codes.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        if (codes.isEmpty()) {
            Text("Пока нет неизвестных кодов.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(codes) { code ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)) {
                        Text(code, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            HubActionButton("Очистить список", "Удалить неизвестные сканы", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, DigiDropPalette.primary, onClear)
        }
    }
}
