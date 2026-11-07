package com.example.qrhelper

import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.googlecode.tesseract.android.TessBaseAPI
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

internal object PhotoRouteTextParser {
    private val addressHint = Regex(
        """(?i)(ул\.?|улица|пр\.?|проспект|пер\.?|переулок|ш\.?|шоссе|наб\.?|набережная|пл\.?|площадь|б-р|бул\.?|дер\.?|д\.?|корп\.?|стр\.?|кв\.?|г\.?|город|пос\.?|село|деревня)"""
    )
    private val houseNumber = Regex("""\b\d{1,4}[А-Яа-яA-Za-z]?([/-]\d{1,4})?\b""")
    private val metadataHint = Regex("""(?i)(маршрут|накладная|телефон|тел\\.?|диспетчер|итого|всего|получатель|водитель|заказ)""")

    fun extractCandidates(text: String): List<String> {
        val cleaned = text.lineSequence()
            .map { it.replace(Regex("""^[\s•*№\-–—]+"""), "").trim() }
            .filter { line ->
                line.length >= 8 && !metadataHint.containsMatchIn(line) &&
                    (addressHint.containsMatchIn(line) ||
                        (houseNumber.containsMatchIn(line) && line.any { it.isLetter() }))
            }
            .map { it.replace(Regex("""\s{2,}"""), " ") }
            .distinctBy { it.lowercase(Locale("ru", "RU")).replace(Regex("""[^\p{L}\p{N}]"""), "") }
            .toList()
        return cleaned
    }
}

private suspend fun runRussianOcr(context: android.content.Context, imageUri: Uri): String = withContext(Dispatchers.IO) {
    val tessDir = File(context.filesDir, "tesseract")
    val dataDir = File(tessDir, "tessdata")
    if (!dataDir.exists()) dataDir.mkdirs()
    val trainedData = File(dataDir, "rus.traineddata")
    if (!trainedData.exists() || trainedData.length() < 1_000_000L) {
        val connection = (URL("https://github.com/tesseract-ocr/tessdata_fast/raw/refs/heads/main/rus.traineddata").openConnection() as HttpURLConnection)
        connection.connectTimeout = 20000
        connection.readTimeout = 60000
        connection.setRequestProperty("User-Agent", "DigiDrop-Android")
        connection.inputStream.use { input -> trainedData.outputStream().use { output -> input.copyTo(output) } }
        connection.disconnect()
    }
    val bitmap = context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
        ?: error("Не удалось открыть фотографию")
    val api = TessBaseAPI()
    try {
        check(api.init(tessDir.absolutePath, "rus+eng")) { "Не удалось запустить русский OCR" }
        api.setImage(bitmap)
        api.getUTF8Text().orEmpty()
    } finally {
        api.recycle()
        bitmap.recycle()
    }
}

private data class LocatedPhotoStop(val address: String, val latitude: Double, val longitude: Double)

private suspend fun geocodePhotoStops(context: android.content.Context, addresses: List<String>): Pair<List<LocatedPhotoStop>, List<String>> =
    withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale("ru", "RU"))
        val found = mutableListOf<LocatedPhotoStop>()
        val failed = mutableListOf<String>()
        for (address in addresses) {
            try {
                val result = geocoder.getFromLocationName(address, 1)?.firstOrNull()
                if (result == null) failed += address
                else found += LocatedPhotoStop(address, result.latitude, result.longitude)
            } catch (_: Exception) {
                failed += address
            }
        }
        found to failed
    }

private data class OptimizedPhotoRoute(val stops: List<LocatedPhotoStop>, val geometryJson: String)

private suspend fun optimizePhotoRoute(stops: List<LocatedPhotoStop>): OptimizedPhotoRoute = withContext(Dispatchers.IO) {
    require(stops.size >= 2) { "Для оптимизации нужны минимум две найденные точки" }
    val coordinates = stops.joinToString(";") { "${it.longitude},${it.latitude}" }
    val endpoint = "https://router.project-osrm.org/trip/v1/driving/$coordinates?source=first&destination=last&roundtrip=false&overview=full&geometries=geojson"
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 30000
    try {
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        check(json.optString("code") == "Ok") { "Сервис маршрутизации не смог построить маршрут" }
        val trip = json.getJSONArray("trips").getJSONObject(0)
        val waypoints = json.getJSONArray("waypoints")
        val ordered = (0 until waypoints.length()).sortedBy { waypoints.getJSONObject(it).getInt("waypoint_index") }
            .map { stops[it] }
        val geometry = trip.getJSONObject("geometry").toString()
        OptimizedPhotoRoute(ordered, geometry)
    } finally {
        connection.disconnect()
    }
}

@Composable
fun PhotoRoutePlannerScreen(
    onBack: () -> Unit,
    onApplyRoute: (List<RouteItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rawText by remember { mutableStateOf("") }
    var addresses by remember { mutableStateOf(listOf<String>()) }
    var located by remember { mutableStateOf(listOf<LocatedPhotoStop>()) }
    var routeGeometry by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Сфотографируйте маршрутный лист. Русский текст распознаётся на устройстве после загрузки языковой модели.") }
    var busy by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            status = "Распознаю текст. При первом запуске загружается русская OCR-модель."
            try {
                rawText = runRussianOcr(context, uri)
                addresses = PhotoRouteTextParser.extractCandidates(rawText)
                status = if (addresses.isEmpty()) "Текст распознан, но адреса не выделены автоматически. Исправьте или вставьте адреса вручную." else "Найдено строк-кандидатов: ${addresses.size}. Проверьте их перед построением маршрута."
            } catch (error: Exception) {
                status = "Ошибка OCR: ${error.message ?: "неизвестная ошибка"}"
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(BgDark).statusBarsPadding().padding(16.dp)) {
        Text("‹ Главная", color = MintAccent, modifier = Modifier.clickable { onBack() })
        Spacer(Modifier.height(10.dp))
        Text("Маршрут по фотографии", color = TextLight, style = MaterialTheme.typography.headlineSmall)
        Text(status, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { photoPicker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Обработка…" else "Сфотографировать / выбрать лист")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = rawText,
            onValueChange = { rawText = it },
            label = { Text("Распознанный текст (можно исправить)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 220.dp),
            minLines = 4
        )
        OutlinedButton(onClick = {
            addresses = PhotoRouteTextParser.extractCandidates(rawText)
            status = "Кандидатов адресов: ${addresses.size}. Проверьте список ниже."
        }, modifier = Modifier.fillMaxWidth()) { Text("Выделить адреса из текста") }
        Spacer(Modifier.height(8.dp))
        Text("Адреса для маршрута: ${addresses.size}", color = TextLight, style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(addresses) { index, address ->
                OutlinedTextField(
                    value = address,
                    onValueChange = { value -> addresses = addresses.toMutableList().also { it[index] = value } },
                    label = { Text("Точка ${index + 1}") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { addresses = addresses.filterIndexed { i, _ -> i != index } }) { Text("Убрать") }
                    }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { addresses = addresses + "" }, modifier = Modifier.weight(1f)) { Text("+ Точка") }
            Button(onClick = {
                scope.launch {
                    busy = true
                    try {
                        val clean = addresses.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                        require(clean.size >= 2) { "Добавьте минимум два адреса" }
                        status = "Ищу координаты адресов…"
                        val (resolved, failed) = geocodePhotoStops(context, clean)
                        located = resolved
                        if (failed.isNotEmpty()) {
                            status = "Не удалось определить адреса: ${failed.joinToString("; ")}. Исправьте их и повторите."
                        } else if (resolved.size < 2) {
                            status = "Найдено меньше двух координат. Проверьте адреса."
                        } else {
                            status = "Оптимизирую дорожный порядок точек…"
                            val optimized = optimizePhotoRoute(resolved)
                            located = optimized.stops
                            routeGeometry = optimized.geometryJson
                            status = "Маршрут построен: ${optimized.stops.size} точек. Порядок рассчитан по дорожной сети OSRM."
                        }
                    } catch (error: Exception) {
                        status = "Не удалось построить маршрут: ${error.message ?: "ошибка сети"}"
                    } finally { busy = false }
                }
            }, enabled = !busy && addresses.count { it.isNotBlank() } >= 2, modifier = Modifier.weight(1f)) {
                Text(if (busy) "Подождите…" else "Оптимизировать")
            }
        }
        if (located.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Карта и порядок посещения", color = TextLight, style = MaterialTheme.typography.titleMedium)
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                factory = { view ->
                    WebView(view).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = WebViewClient()
                    }
                },
                update = { web ->
                    val markers = located.joinToString(",") { "[${it.latitude},${it.longitude}]" }
                    val geo = routeGeometry.ifBlank { "null" }
                    val html = """
                        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
                        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script></head>
                        <body style="margin:0"><div id="map" style="height:280px"></div><script>
                        const pts=[$markers]; const map=L.map('map').setView(pts[0],12);
                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
                        pts.forEach((p,i)=>L.marker(p).addTo(map).bindPopup('Точка '+(i+1)));
                        const geom=$geo; if(geom){const line=L.geoJSON(geom).addTo(map);map.fitBounds(line.getBounds().pad(0.12));}
                        else if(pts.length>1){map.fitBounds(pts);}
                        </script></body></html>
                    """.trimIndent()
                    web.loadDataWithBaseURL("https://www.openstreetmap.org", html, "text/html", "UTF-8", null)
                }
            )
            Text(located.mapIndexed { index, stop -> "${index + 1}. ${stop.address}" }.joinToString("\n"), color = TextMuted, style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                val items = located.mapIndexed { index, stop ->
                    RouteItem(
                        barcode = "PHOTO-${index + 1}",
                        storeName = "Точка ${index + 1}",
                        address = stop.address,
                        itemName = "Доставка по маршрутному листу",
                        totalBoxes = 0,
                        phone = "",
                        note = "Импортировано из фотографии",
                        latitude = stop.latitude,
                        longitude = stop.longitude
                    )
                }
                onApplyRoute(items)
            }, modifier = Modifier.fillMaxWidth()) { Text("Применить маршрут в DigiDrop") }
        }
    }
}
