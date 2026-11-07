package com.example.qrhelper

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.googlecode.tesseract.android.TessBaseAPI
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal object PhotoRouteTextParser {
    private val addressHint = Regex(
        """(?iu)(?<![А-Яа-яЁёA-Za-z])(ул\.?|улица|пр\.?|проспект|пер\.?|переулок|ш\.?|шоссе|наб\.?|набережная|пл\.?|площадь|б-р|бул\.?|дер\.?|д\.?|корп\.?|стр\.?|кв\.?|г\.?|город|пос\.?|село|деревня)(?![А-Яа-яЁёA-Za-z])"""
    )
    private val houseNumber = Regex("""\b\d{1,4}[А-Яа-яA-Za-z]?([/-]\d{1,4})?\b""")
    private val metadataHint = Regex("""(?iu)(маршрут|накладная|телефон|тел\.?|диспетчер|итого|всего|получатель|водитель|заказ)""")

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

internal object PhotoOcrImageSizing {
    fun sampleSize(width: Int, height: Int, maxDimension: Int = 2400): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / sample > maxDimension) sample *= 2
        return sample
    }
}

private suspend fun runRussianOcr(
    context: android.content.Context,
    imageUri: Uri,
    onProgress: suspend (String) -> Unit
): String = withContext(Dispatchers.IO) {
    val tessDir = File(context.filesDir, "tesseract")
    val dataDir = File(tessDir, "tessdata")
    if (!dataDir.exists() && !dataDir.mkdirs()) error("Не удалось создать папку OCR-модели")
    val trainedData = File(dataDir, "rus.traineddata")

    if (!trainedData.exists() || trainedData.length() < 1_000_000L) {
        withContext(Dispatchers.Main) { onProgress("Скачиваю русскую OCR-модель (первый запуск)…") }
        val temporary = File(dataDir, "rus.traineddata.download")
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
            if (trainedData.exists() && !trainedData.delete()) error("Не удалось обновить старую OCR-модель")
            check(temporary.renameTo(trainedData)) { "Не удалось сохранить OCR-модель" }
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    withContext(Dispatchers.Main) { onProgress("Подготавливаю фотографию для распознавания…") }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(imageUri)
        ?: error("Не удалось открыть фотографию")
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Файл не является читаемым изображением" }
    val options = BitmapFactory.Options().apply {
        inSampleSize = PhotoOcrImageSizing.sampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: error("Не удалось декодировать фотографию")
    val api = TessBaseAPI()
    try {
        withContext(Dispatchers.Main) { onProgress("Распознаю русский текст на устройстве…") }
        // Загружается только rus.traineddata, поэтому запрашивать отсутствующий eng.traineddata нельзя.
        check(api.init(tessDir.absolutePath, "rus")) {
            "Tesseract не смог загрузить русскую модель. Выберите фото ещё раз для повторной загрузки."
        }
        api.setImage(bitmap)
        api.getUTF8Text().orEmpty().trim()
    } finally {
        api.recycle()
        bitmap.recycle()
    }
}

private data class LocatedPhotoStop(val address: String, val latitude: Double, val longitude: Double)

internal data class PhotoRouteCoordinate(val latitude: Double, val longitude: Double)

internal object PhotoRouteNavigationUrls {
    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun validate(stops: List<PhotoRouteCoordinate>) {
        require(stops.size >= 2) { "Для навигации нужны минимум две точки" }
    }

    fun yandex(stops: List<PhotoRouteCoordinate>): String {
        validate(stops)
        val routeText = stops.joinToString("~") { "${it.latitude},${it.longitude}" }
        return "https://yandex.ru/maps/?rtext=${encode(routeText)}&rtt=auto"
    }

    fun google(stops: List<PhotoRouteCoordinate>): String {
        validate(stops)
        require(stops.size <= 11) {
            "Google Карты поддерживают не больше 9 промежуточных точек. Для полного маршрута используйте Яндекс Карты."
        }
        val origin = encode("${stops.first().latitude},${stops.first().longitude}")
        val destination = encode("${stops.last().latitude},${stops.last().longitude}")
        val waypoints = stops.drop(1).dropLast(1)
        val waypointPart = if (waypoints.isEmpty()) "" else
            "&waypoints=${encode(waypoints.joinToString("|") { "${it.latitude},${it.longitude}" })}"
        return "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination$waypointPart&travelmode=driving"
    }
}

private fun openExternalMap(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "Не найдено приложение для открытия карт", android.widget.Toast.LENGTH_LONG).show()
    }
}

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
    var status by remember { mutableStateOf("Сфотографируйте маршрутный лист. Русский текст распознаётся на устройстве после загрузки языковой модели.") }
    var busy by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            status = "Распознаю текст. При первом запуске загружается русская OCR-модель."
            try {
                rawText = runRussianOcr(context, uri) { message -> status = message }
                addresses = PhotoRouteTextParser.extractCandidates(rawText)
                status = if (addresses.isEmpty()) "Текст распознан, но адреса не выделены автоматически. Исправьте или вставьте адреса вручную." else "Найдено строк-кандидатов: ${addresses.size}. Проверьте их перед построением маршрута."
            } catch (error: Exception) {
                status = "Ошибка OCR: ${error.message ?: "неизвестная ошибка"}"
            } finally { busy = false }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            addresses.forEachIndexed { index, address ->
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
            Spacer(Modifier.height(12.dp))
            Text("Маршрут готов: ${located.size} точек", color = TextLight, style = MaterialTheme.typography.titleMedium)
            Text(
                "Порядок точек уже оптимизирован. Открой маршрут во внешнем навигаторе: встроенная мини-карта больше не используется.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val points = located.map { PhotoRouteCoordinate(it.latitude, it.longitude) }
                    openExternalMap(context, PhotoRouteNavigationUrls.yandex(points))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Открыть весь маршрут в Яндекс Картах") }
            Button(
                onClick = {
                    val points = located.map { PhotoRouteCoordinate(it.latitude, it.longitude) }
                    runCatching { PhotoRouteNavigationUrls.google(points) }
                        .onSuccess { openExternalMap(context, it) }
                        .onFailure {
                            android.widget.Toast.makeText(context, it.message ?: "Не удалось открыть Google Карты", android.widget.Toast.LENGTH_LONG).show()
                        }
                },
                enabled = located.size <= 11,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Открыть маршрут в Google Картах") }
            if (located.size > 11) {
                Text(
                    "В Google Картах ограничено число промежуточных точек. Яндекс Карты передадут весь список.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                located.mapIndexed { index, stop -> "${index + 1}. ${stop.address}" }.joinToString("\n"),
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = {
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
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Также сохранить маршрут в DigiDrop") }
        }
    }
}
