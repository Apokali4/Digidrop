package com.example.qrhelper

import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import android.widget.Toast
import android.view.ViewGroup
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

internal data class PhotoStopMetadata(
    val storeName: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val workingHours: String = "",
    val cashNote: String = "",
    val comment: String = ""
)

internal object PhotoRouteTextParser {
    private val streetHint = Regex(
        """(?iu)(?<![А-Яа-яЁёA-Za-z])(ул\.?|улица|пр\.?|проспект|пер\.?|переулок|ш\.?|шоссе|наб\.?|набережная|пл\.?|площадь|б-р|бул\.?)(?![А-Яа-яЁёA-Za-z])"""
    )
    private val houseAfterStreet = Regex(
        """(?iu)(?:,?\s*(?:д\.?|дом)\s*№?\s*)?\b(\d{1,4}[А-Яа-яA-Za-z]?(?:(?:[/\-]|к)\d{1,4}[А-Яа-яA-Za-z]?)?)\b"""
    )
    private val metadataHint = Regex("""(?iu)(маршрут|накладная|телефон|тел\.?|диспетчер|итого|всего|получатель|водитель|заказ)""")

    private fun cleanAddressFragment(line: String, city: String): String? {
        val street = streetHint.find(line) ?: return null
        val house = houseAfterStreet.find(line, street.range.last + 1) ?: return null
        val cityMatch = if (city.isNotBlank()) {
            Regex(Regex.escape(city.trim()), RegexOption.IGNORE_CASE).find(line)
                ?.takeIf { it.range.first <= street.range.first }
        } else {
            Regex("""(?iu)\bг\.?\s*[А-ЯЁ][А-Яа-яЁё-]+""").find(line)
                ?.takeIf { it.range.first <= street.range.first }
        }
        val start = cityMatch?.range?.first ?: street.range.first
        return line.substring(start, house.range.last + 1)
            .replace(Regex("""\s*,\s*"""), ", ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', ';', '|', '.', ':')
            .takeIf { it.length >= 8 }
    }

    fun extractCandidates(text: String, city: String = ""): List<String> =
        text.lineSequence()
            .map { it.replace(Regex("""^[\s•*№\-–—]+"""), "").trim() }
            .filter { it.length >= 8 && !metadataHint.containsMatchIn(it) }
            .mapNotNull { cleanAddressFragment(it, city) }
            .distinctBy { it.lowercase(Locale("ru", "RU")).replace(Regex("""[^\p{L}\p{N}]"""), "") }
            .toList()

    fun extractMetadata(text: String, address: String): PhotoStopMetadata {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val streetToken = streetHint.find(address)?.value?.lowercase(Locale("ru", "RU")).orEmpty()
        val houseToken = houseAfterStreet.find(address)?.groupValues?.getOrNull(1).orEmpty()
        val index = lines.indexOfFirst { line ->
            val lower = line.lowercase(Locale("ru", "RU"))
            streetToken.isNotBlank() && lower.contains(streetToken) && houseToken.isNotBlank() && lower.contains(houseToken)
        }
        val block = if (index >= 0) lines.subList(maxOf(0, index - 4), minOf(lines.size, index + 7)) else emptyList()
        val phone = Regex("""(?iu)(?:\+7|8)?[\s(\-]*\d{3}[)\s\-]*\d{3}[\s\-]*\d{2}[\s\-]*\d{2}""")
            .find(block.joinToString(" "))?.value.orEmpty().trim()
        val hours = Regex("""(?iu)\b\d{1,2}[:.]\d{2}\s*[-–—до]+\s*\d{1,2}[:.]\d{2}\b""")
            .find(block.joinToString(" "))?.value.orEmpty()
        val cash = block.firstOrNull { Regex("""(?iu)(налич|деньг|забрат|оплат|инкассац|касс)""").containsMatchIn(it) }.orEmpty()
        val owner = block.firstOrNull { Regex("""(?iu)\b(ИП|ООО|АО|ИП\s+[А-ЯЁ])""").containsMatchIn(it) }.orEmpty()
        val shop = block.firstOrNull {
            it != address && Regex("""(?iu)(магазин|маркет|продукт|торгов|пятероч|магнит|доставка|павильон)""").containsMatchIn(it)
        }.orEmpty()
        val comment = block.firstOrNull {
            it != cash && Regex("""(?iu)(коммент|подъезд|въезд|позвон|до закрыт|разгруз|ворот|забрать|передать)""").containsMatchIn(it)
        }.orEmpty()
        return PhotoStopMetadata(shop, owner, phone, hours, cash, comment)
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

    const val YANDEX_MAX_STOPS = 20

    fun yandexSegments(stops: List<PhotoRouteCoordinate>): List<List<PhotoRouteCoordinate>> {
        validate(stops)
        if (stops.size <= YANDEX_MAX_STOPS) return listOf(stops)
        val segments = mutableListOf<List<PhotoRouteCoordinate>>()
        var start = 0
        while (start < stops.lastIndex) {
            val endExclusive = minOf(start + YANDEX_MAX_STOPS, stops.size)
            segments += stops.subList(start, endExclusive).toList()
            if (endExclusive == stops.size) break
            // Последняя точка участка становится начальной точкой следующего.
            start = endExclusive - 1
        }
        return segments
    }

    fun yandex(stops: List<PhotoRouteCoordinate>): String {
        validate(stops)
        require(stops.size <= YANDEX_MAX_STOPS) {
            "Яндекс Карты принимают не больше 20 точек за один участок"
        }
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

internal object PhotoRouteGeocodingQuery {
    fun build(address: String, city: String): String {
        val cleanCity = city.trim()
        return if (cleanCity.isEmpty()) address.trim()
        else "${address.trim()}, $cleanCity, Россия"
    }
}

private suspend fun geocodePhotoStops(
    context: android.content.Context,
    addresses: List<String>,
    city: String
): Pair<List<LocatedPhotoStop>, List<String>> = withContext(Dispatchers.IO) {
    val geocoder = Geocoder(context, Locale("ru", "RU"))
    val found = mutableListOf<LocatedPhotoStop>()
    val failed = mutableListOf<String>()
    for (address in addresses) {
        try {
            val query = PhotoRouteGeocodingQuery.build(address, city)
            val result = geocoder.getFromLocationName(query, 3)
                ?.minByOrNull { candidate ->
                    // Prefer a result whose locality/sub-admin area matches the selected city.
                    val locality = listOfNotNull(candidate.locality, candidate.subAdminArea, candidate.adminArea)
                        .joinToString(" ").lowercase(Locale("ru", "RU"))
                    if (city.isNotBlank() && locality.contains(city.trim().lowercase(Locale("ru", "RU")))) 0 else 1
                }
            if (result == null) failed += address
            else found += LocatedPhotoStop(address, result.latitude, result.longitude)
        } catch (_: Exception) {
            failed += address
        }
    }
    found to failed
}

internal object PhotoRouteOrdering {
    fun farthestStopIndex(origin: PhotoRouteCoordinate, stops: List<PhotoRouteCoordinate>): Int {
        require(stops.isNotEmpty()) { "Нет точек доставки" }
        fun distanceKm(point: PhotoRouteCoordinate): Double {
            val radius = 6371.0
            val lat1 = Math.toRadians(origin.latitude)
            val lat2 = Math.toRadians(point.latitude)
            val dLat = lat2 - lat1
            val dLon = Math.toRadians(point.longitude - origin.longitude)
            val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(dLon / 2).let { it * it }
            return 2 * radius * kotlin.math.asin(kotlin.math.sqrt(a))
        }
        return stops.indices.maxBy { distanceKm(stops[it]) }
    }
}

private suspend fun optimizeStopsOnly(stops: List<LocatedPhotoStop>): List<LocatedPhotoStop> = withContext(Dispatchers.IO) {
    require(stops.size >= 2) { "Для оптимизации нужны минимум две точки" }
    val coordinates = stops.joinToString(";") { "${it.longitude},${it.latitude}" }
    val endpoint = "https://router.project-osrm.org/trip/v1/driving/$coordinates?source=any&destination=any&roundtrip=false&overview=false"
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 30000
    try {
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        check(json.optString("code") == "Ok") { "Сервис маршрутизации не смог построить порядок точек" }
        val waypoints = json.getJSONArray("waypoints")
        val ordered = (0 until waypoints.length())
            .sortedBy { waypoints.getJSONObject(it).getInt("waypoint_index") }
            .map { stops[it] }
        check(ordered.size == stops.size && ordered.toSet().size == stops.size) {
            "Оптимизатор вернул неполный список точек"
        }
        ordered
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun RouteCameraCaptureScreen(
    onPhotoCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var message by remember { mutableStateOf("Сфотографируйте лист целиком, затем добавьте следующий кадр при необходимости.") }
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    try {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture
                        )
                        imageCapture = capture
                    } catch (error: Exception) {
                        message = "Камера недоступна: ${error.message}"
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )
        Text(message, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(12.dp))
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Готово") }
            Button(
                onClick = {
                    val capture = imageCapture
                    if (capture == null) {
                        message = "Камера ещё запускается"
                    } else {
                        val file = File(context.cacheDir, "route_photo_${System.currentTimeMillis()}.jpg")
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()
                        capture.takePicture(options, ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                    onPhotoCaptured(Uri.fromFile(file))
                                    message = "Фото сохранено. Можно сделать следующий снимок."
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    message = "Не удалось сохранить фото: ${exception.message}"
                                }
                            })
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Снять фото") }
        }
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
    var routeCity by remember { mutableStateOf("Калуга") }
    var addresses by remember { mutableStateOf(listOf<String>()) }
    var located by remember { mutableStateOf(listOf<LocatedPhotoStop>()) }
    var routeOrigin by remember { mutableStateOf<LocatedPhotoStop?>(null) }
    var status by remember { mutableStateOf("Сфотографируйте маршрутный лист. Русский текст распознаётся на устройстве после загрузки языковой модели.") }
    var busy by remember { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }
    var capturedUris by remember { mutableStateOf(listOf<Uri>()) }
    fun processPhotoUris(uris: List<Uri>) {
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            status = "Распознаю ${uris.size} фотографий. Тексты будут объединены в один маршрут."
            try {
                val recognizedTexts = mutableListOf<String>()
                uris.forEachIndexed { index, uri ->
                    recognizedTexts += runRussianOcr(context, uri) { message ->
                        status = "Фото ${index + 1} из ${uris.size}: $message"
                    }
                }
                rawText = recognizedTexts.filter { it.isNotBlank() }.joinToString("\n\n--- Следующая фотография ---\n\n")
                addresses = PhotoRouteTextParser.extractCandidates(rawText, routeCity)
                located = emptyList()
                routeOrigin = null
                status = if (addresses.isEmpty()) {
                    "Распознано фотографий: ${uris.size}, но адреса не выделены автоматически. Исправьте текст или добавьте адреса вручную."
                } else {
                    "Распознано фотографий: ${uris.size}. Найдено строк-кандидатов: ${addresses.size}. Проверьте общий список перед построением маршрута."
                }
            } catch (error: Exception) {
                status = "Ошибка OCR: ${error.message ?: "неизвестная ошибка"}"
            } finally { busy = false }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        processPhotoUris(uris)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraOpen = true
        else Toast.makeText(context, "Для съёмки маршрутника нужно разрешение камеры", Toast.LENGTH_LONG).show()
    }
    if (cameraOpen) {
        RouteCameraCaptureScreen(
            onPhotoCaptured = { uri -> capturedUris = capturedUris + uri },
            onClose = { cameraOpen = false }
        )
        return
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Сфотографировать")
            }
            OutlinedButton(onClick = { photoPicker.launch("image/*") }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Из галереи")
            }
        }
        if (capturedUris.isNotEmpty()) {
            Text("Снимков с камеры: ${capturedUris.size}", color = TextLight, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { processPhotoUris(capturedUris); capturedUris = emptyList() },
                enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Распознать снятые фотографии")
            }
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
        OutlinedTextField(
            value = routeCity,
            onValueChange = { routeCity = it },
            label = { Text("Город маршрута") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
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
                        val (resolved, failed) = geocodePhotoStops(context, clean, routeCity)
                        located = resolved
                        if (failed.isNotEmpty()) {
                            status = "Не удалось определить адреса: ${failed.joinToString("; ")}. Исправьте их и повторите."
                        } else if (resolved.size < 2) {
                            status = "Найдено меньше двух координат. Проверьте адреса."
                        } else {
                            // Сначала только проверка и просмотр адресов. Оптимизация запускается отдельно после проверки водителем.
                            routeOrigin = null
                            located = resolved
                            val duplicateGroups = resolved.groupBy {
                                "${"%.5f".format(Locale.US, it.latitude)},${"%.5f".format(Locale.US, it.longitude)}"
                            }.values.filter { it.size > 1 }
                            status = if (duplicateGroups.isEmpty()) {
                                "Найдено точек: ${resolved.size}. Проверьте каждую в Яндекс Картах и при необходимости исправьте адреса."
                            } else {
                                "Найдено точек: ${resolved.size}. ${duplicateGroups.sumOf { it.size - 1 }} точек получили одинаковые координаты. Проверьте их вручную."
                            }
                        }
                    } catch (error: Exception) {
                        status = "Не удалось построить маршрут: ${error.message ?: "ошибка сети"}"
                    } finally { busy = false }
                }
            }, enabled = !busy && addresses.count { it.isNotBlank() } >= 2, modifier = Modifier.weight(1f)) {
                Text(if (busy) "Подождите…" else "Найти точки", maxLines = 1, softWrap = false, fontSize = 12.sp)
            }
        }
        if (located.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Предварительный список: ${located.size} точек", color = TextLight, style = MaterialTheme.typography.titleMedium)
            Text(
                "Проверьте каждую точку в Яндекс Картах. Порядок пока сохранён как в маршрутном листе, оптимизация не запускалась.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        status = "Оптимизирую порядок только после проверки точек…"
                        try {
                            located = optimizeStopsOnly(located)
                            status = "Порядок оптимизирован по дорожной сети. Все ${located.size} точек сохранены."
                        } catch (error: Exception) {
                            status = "Не удалось оптимизировать: ${error.message ?: "ошибка сети"}"
                        } finally { busy = false }
                    }
                },
                enabled = !busy && located.size >= 2,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Оптимизация…" else "Оптимизировать", maxLines = 1, softWrap = false, fontSize = 13.sp) }
            Spacer(Modifier.height(8.dp))
            val navigationPoints = located
            val navigationCoordinates = navigationPoints.map { PhotoRouteCoordinate(it.latitude, it.longitude) }
            val yandexSegments = PhotoRouteNavigationUrls.yandexSegments(navigationCoordinates)
            if (yandexSegments.size == 1) {
                Button(
                    onClick = { openExternalMap(context, PhotoRouteNavigationUrls.yandex(yandexSegments.first())) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Открыть маршрут в Яндекс Картах") }
            } else {
                Text(
                    "В Яндексе лимит 20 точек. Маршрут разбит на ${yandexSegments.size} участка с общей точкой перехода.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                yandexSegments.forEachIndexed { index, segment ->
                    Button(
                        onClick = { openExternalMap(context, PhotoRouteNavigationUrls.yandex(segment)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Открыть участок ${index + 1}/${yandexSegments.size} (${segment.size} точек)")
                    }
                }
            }
            Button(
                onClick = {
                    runCatching { PhotoRouteNavigationUrls.google(navigationCoordinates) }
                        .onSuccess { openExternalMap(context, it) }
                        .onFailure {
                            android.widget.Toast.makeText(context, it.message ?: "Не удалось открыть Google Карты", android.widget.Toast.LENGTH_LONG).show()
                        }
                },
                enabled = located.size <= 11,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Открыть маршрут в Google Картах") }
            if (navigationCoordinates.size > 11) {
                Text(
                    "В Google Картах ограничено число промежуточных точек. Маршрут в Яндексе разбивается на участки по 20 точек.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Порядок можно менять вручную. Стрелки перемещают остановку выше или ниже; навигатор получит именно этот порядок.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                located.forEachIndexed { index, stop ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgDark.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Точка ${index + 1}", color = TextLight, style = MaterialTheme.typography.labelLarge)
                            Text(stop.address, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = {
                                val query = "${stop.latitude},${stop.longitude}"
                                openExternalMap(context, "https://yandex.ru/maps/?text=" + java.net.URLEncoder.encode(query, "UTF-8"))
                            }) { Text("Проверить в Яндекс Картах") }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(
                                enabled = index > 0,
                                onClick = {
                                    located = located.toMutableList().also { list ->
                                        val item = list.removeAt(index)
                                        list.add(index - 1, item)
                                    }
                                    status = "Порядок изменён вручную. Экспортируй маршрут заново в навигатор."
                                }
                            ) { Text("↑ Выше") }
                            TextButton(
                                enabled = index < located.lastIndex,
                                onClick = {
                                    located = located.toMutableList().also { list ->
                                        val item = list.removeAt(index)
                                        list.add(index + 1, item)
                                    }
                                    status = "Порядок изменён вручную. Экспортируй маршрут заново в навигатор."
                                }
                            ) { Text("↓ Ниже") }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    val items = located.mapIndexed { index, stop ->
                        val metadata = PhotoRouteTextParser.extractMetadata(rawText, stop.address)
                        RouteItem(
                            barcode = "PHOTO-${index + 1}",
                            storeName = metadata.storeName.ifBlank { "Точка ${index + 1}" },
                            address = stop.address,
                            itemName = "Доставка по маршрутному листу",
                            totalBoxes = 0,
                            phone = metadata.phone,
                            note = metadata.comment.ifBlank { "Импортировано из фотографии" },
                            latitude = stop.latitude,
                            longitude = stop.longitude,
                            ownerName = metadata.ownerName,
                            workingHours = metadata.workingHours,
                            cashNote = metadata.cashNote,
                            extraComment = metadata.comment
                        )
                    }
                    onApplyRoute(items)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Также сохранить маршрут в DigiDrop") }
        }
    }
}
