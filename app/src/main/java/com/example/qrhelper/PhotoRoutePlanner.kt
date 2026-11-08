package com.example.qrhelper

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

internal data class PhotoRouteHeader(
    val driver: String = "",
    val vehicle: String = "",
    val vehicleNumber: String = "",
    val routeNumber: String = ""
)

internal object PhotoRouteHeaderParser {
    private val platePattern = Regex("""(?iu)([АВЕКМНОРСТУХABEKMHOPCTYX]\s*\d{3}\s*[АВЕКМНОРСТУХABEKMHOPCTYX]{2}\s*\d{2,3})""")

    fun parse(text: String): PhotoRouteHeader {
        val lines = text.lineSequence()
            .map { it.trim().replace(Regex("""\\s+"""), " ") }
            .filter { it.isNotBlank() }
            .toList()

        fun labeledValue(pattern: Regex): String = lines.firstNotNullOfOrNull { line ->
            pattern.find(line)?.groupValues?.getOrNull(1)?.trim()
                ?.trim(':', '-', '—', '№', '.', ' ')
                ?.takeIf { it.isNotBlank() }
        }.orEmpty()

        val driver = labeledValue(Regex("""(?iu)(?:водитель|экспедитор)\\s*[:№-]?\\s*(.+)$"""))
            .substringBefore(Regex("""(?iu)\\s+(?:экспедитор|водитель)\\s*:"""))
            .trim()

        var vehicle = ""
        var vehicleNumber = ""
        lines.firstOrNull { Regex("""(?iu)(?:транспортное\\s+средство|автомобиль|машина|марка\\s+авто)""").containsMatchIn(it) }
            ?.let { line ->
                val remainder = line.replaceFirst(
                    Regex("""(?iu)^.*?(?:транспортное\\s+средство|автомобиль|машина|марка\\s+авто)\\s*[:№-]?\\s*"""), ""
                ).trim()
                vehicleNumber = platePattern.find(remainder)?.groupValues?.getOrNull(1)
                    ?.replace(Regex("""\\s+"""), "")?.uppercase(Locale("ru", "RU")).orEmpty()
                vehicle = remainder
                    .replace(platePattern, "")
                    .replace(Regex("""(?iu)\\s*№\\s*"""), " ")
                    .replace(Regex("""\\s+"""), " ")
                    .trim(' ', ',', ';', '-', '—')
            }

        if (vehicleNumber.isBlank()) {
            vehicleNumber = labeledValue(Regex("""(?iu)(?:гос(?:ударственный)?\\.?\\s*номер|г\\.?\\s*н\\.?|номер\\s+автомобиля)\\s*[:№-]?\\s*([А-ЯЁA-Z0-9 -]{5,})"""))
                .let { platePattern.find(it)?.groupValues?.getOrNull(1) ?: it }
                .replace(Regex("""\\s+"""), "").uppercase(Locale("ru", "RU"))
        }

        val routeNumber = labeledValue(
            Regex("""(?iu)(?:номер\\s+маршрута|маршрут|рейс|задание\\s+на\\s+отгрузку)\\s*№?\\s*[:№-]?\\s*([А-ЯЁA-Z0-9/-]+)""")
        )
        return PhotoRouteHeader(driver, vehicle, vehicleNumber, routeNumber)
    }
}
internal object PhotoRouteTextParser {
    private val streetHint = Regex(
        """(?iu)(?<![А-Яа-яЁёA-Za-z])(ул\.?|улица|пр\.?|проспект|пер\.?|переулок|ш\.?|шоссе|наб\.?|набережная|пл\.?|площадь|б-р|бул\.?)(?![А-Яа-яЁёA-Za-z])"""
    )
    private val houseAfterStreet = Regex(
        """(?iu)(?:[,\s-]*(?:д\.?|дом|вх\.?|в)\s*№?\s*[-–—]?)?(\d{1,4}[А-Яа-яA-Za-z]?(?:(?:[/\-]|к)\d{1,4}[А-Яа-яA-Za-z]?)?)\b"""
    )
    private val metadataHint = Regex("""(?iu)(маршрут|накладная|телефон|тел\.?|диспетчер|итого|всего|водитель|экспедитор)""")
    private val brandPatterns = listOf(
        Regex("""(?iu)красное\s+и\s+белое""") to "Красное и Белое",
        Regex("""(?iu)бристоль""") to "Бристоль",
        Regex("""(?iu)пят[её]рочк""") to "Пятёрочка",
        Regex("""(?iu)магнит""") to "Магнит",
        Regex("""(?iu)дикси""") to "Дикси",
        Regex("""(?iu)лента""") to "Лента",
        Regex("""(?iu)перекр[её]сток""") to "Перекрёсток",
        Regex("""(?iu)чижик""") to "Чижик",
        Regex("""(?iu)светофор""") to "Светофор",
        Regex("""(?iu)вкусвилл""") to "ВкусВилл",
        Regex("""(?iu)балтика""") to "Балтика"
    )

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

    private fun canonicalAddressKey(address: String): String {
        val street = streetHint.find(address) ?: return address.lowercase(Locale("ru", "RU"))
            .replace(Regex("""[^\p{L}\p{N}]"""), "")
        val house = houseAfterStreet.find(address, street.range.last + 1)
        if (house == null) return address.lowercase(Locale("ru", "RU"))
            .replace(Regex("""[^\p{L}\p{N}]"""), "")
        val streetName = address.substring(street.range.last + 1, house.range.first)
            .lowercase(Locale("ru", "RU"))
            .replace(Regex("""(?iu)[,\s]*(?:д\.?|дом|вх\.?|в)\s*[-–—]?\s*$"""), "")
            .replace(Regex("""[\s.,;:|()_-]+"""), "")
        val houseNumber = house.groupValues[1].lowercase(Locale("ru", "RU"))
            .replace(Regex("""[^\p{L}\p{N}]"""), "")
        return "$streetName#$houseNumber"
    }

    fun extractCandidates(text: String, city: String = ""): List<String> {
        val lines = text.lineSequence()
            .map { it.replace(Regex("""^[\s•*№\-–—]+"""), "").trim() }
            .filter { it.isNotBlank() }
            .toList()
        val rows = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            rows += line
            val street = streetHint.find(line)
            val hasHouseOnSameLine = street != null && houseAfterStreet.find(line, street.range.last + 1) != null
            val next = lines.getOrNull(index + 1)
            if (street != null && !hasHouseOnSameLine && next != null &&
                Regex("""\d{1,4}""").containsMatchIn(next) &&
                !streetHint.containsMatchIn(next) && !metadataHint.containsMatchIn(next)
            ) {
                rows += "$line $next"
            }
        }
        return rows
            .filter { it.length >= 8 && !metadataHint.containsMatchIn(it) }
            .mapNotNull { cleanAddressFragment(it, city) }
            .distinctBy(::canonicalAddressKey)
    }

    fun extractMetadata(text: String, address: String): PhotoStopMetadata {
        val lines = text.lineSequence().map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }.toList()
        val targetKey = canonicalAddressKey(address)
        val index = lines.indexOfFirst { line ->
            cleanAddressFragment(line, "")?.let(::canonicalAddressKey) == targetKey
        }.takeIf { it >= 0 } ?: lines.indexOfFirst { line ->
            val street = streetHint.find(address)?.value.orEmpty()
            street.isNotBlank() && line.contains(street, ignoreCase = true)
        }
        val block = if (index >= 0) lines.subList(maxOf(0, index - 3), minOf(lines.size, index + 7)) else lines
        val anchor = lines.getOrNull(index).orEmpty()
        val houseMatch = houseAfterStreet.find(anchor, (streetHint.find(anchor)?.range?.last ?: -1) + 1)
        val afterHouse = houseMatch?.let { anchor.substring(it.range.last + 1) }
            ?.trim(' ', ',', ';', '|', '.', ':', '-', '—').orEmpty()

        val legalPattern = Regex("""(?iu)\b(ИП|ООО|АО|ПАО|ОАО|ЗАО|ЧУП|УП|ГУП|МУП|ТОО|Тов\.?|товарищество)\b""")
        val companyLine = block.firstOrNull { legalPattern.containsMatchIn(it) && !it.startsWith("ИП", ignoreCase = true) }.orEmpty()
        val owner = block.firstOrNull { it.startsWith("ИП", ignoreCase = true) && legalPattern.containsMatchIn(it) } ?: companyLine
        val phone = Regex("""(?iu)(?<!\d)(?:\+7|8)?(?:[\s()\-]*\d){10}(?!\d)""")
            .find(block.joinToString(" "))?.value.orEmpty().trim()

        val combined = block.joinToString(" ")
        val timeRange = Regex("""(?iu)\d{1,2}[:.]\d{2}\s*(?:[-–—]|до)\s*\d{1,2}[:.]\d{2}""").find(combined)?.value.orEmpty()
        val deadline = Regex("""(?iu)до\s*\d{1,2}[:.]\d{2}""").find(combined)?.value.orEmpty()
        val opening = Regex("""(?iu)(?:с|после)\s*\d{1,2}[:.]\d{2}""").find(combined)?.value.orEmpty()
        val hours = when {
            timeRange.isNotBlank() -> timeRange
            deadline.isNotBlank() -> deadline
            opening.isNotBlank() -> opening
            else -> ""
        }

        val cash = block.firstOrNull { Regex("""(?iu)(налич|деньг|забрат|оплат|инкассац|касс|документ)""").containsMatchIn(it) }.orEmpty()
        val suffixBrand = afterHouse.takeIf {
            it.isNotBlank() && !legalPattern.containsMatchIn(it) &&
                it.length <= 48 && !Regex("""[^А-Яа-яЁёA-Za-z0-9 .&()/-]""").containsMatchIn(it)
        }
        val brandInSuffix = suffixBrand?.let { suffix ->
            brandPatterns.firstOrNull { (pattern, _) -> pattern.containsMatchIn(suffix) }?.second
        }
        val brandNearby = block.firstNotNullOfOrNull { line ->
            brandPatterns.firstOrNull { (pattern, _) -> pattern.containsMatchIn(line) }?.second
        }
        val store = brandInSuffix ?: suffixBrand ?: brandNearby ?: companyLine
        val instructionPattern = Regex("""(?iu)(достав|до\s*\d|после\s*\d|открыва|режим|при[её]м|разгруз|подъезд|въезд|позвон|ворот|забрать|передать|инструкц|коммент)""")
        val comment = block.filter { line ->
            line != cash && !(hours.isNotBlank() && line.contains(hours, ignoreCase = true)) &&
                (instructionPattern.containsMatchIn(line) ||
                    line.contains("с ", ignoreCase = true) && Regex("""\d{1,2}[:.]\d{2}""").containsMatchIn(line))
        }.distinct().joinToString("; ")
        return PhotoStopMetadata(store, owner, phone, hours, cash, comment)
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
): String = RussianOcrEngine.recognizeUri(context, imageUri, onProgress)

private data class LocatedPhotoStop(
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val metadata: PhotoStopMetadata = PhotoStopMetadata()
)

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
    fun nearestNeighborOrder(points: List<PhotoRouteCoordinate>): List<Int> {
        if (points.size < 2) return points.indices.toList()
        val remaining = points.indices.drop(1).toMutableSet()
        val ordered = mutableListOf(0)
        while (remaining.isNotEmpty()) {
            val current = points[ordered.last()]
            val next = remaining.minByOrNull { index ->
                val candidate = points[index]
                val lat1 = Math.toRadians(current.latitude)
                val lat2 = Math.toRadians(candidate.latitude)
                val dLat = lat2 - lat1
                val dLon = Math.toRadians(candidate.longitude - current.longitude)
                val a = kotlin.math.sin(dLat / 2).let { it * it } +
                    kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                    kotlin.math.sin(dLon / 2).let { it * it }
                6_371_000.0 * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
            } ?: break
            ordered += next
            remaining.remove(next)
        }
        return ordered
    }

}

private data class PhotoRouteOptimizationResult(
    val stops: List<LocatedPhotoStop>,
    val usedRoadNetwork: Boolean
)

private suspend fun optimizeStopsOnly(stops: List<LocatedPhotoStop>): PhotoRouteOptimizationResult = withContext(Dispatchers.IO) {
    require(stops.size >= 2) { "Для оптимизации нужны минимум две точки" }
    try {
        val coordinates = stops.joinToString(";") { "${it.longitude},${it.latitude}" }
        val endpoint = "https://router.project-osrm.org/trip/v1/driving/$coordinates?source=any&destination=any&roundtrip=false&overview=false"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(code in 200..299) { "OSRM HTTP $code: ${body.take(300)}" }
            val json = JSONObject(body)
            check(json.optString("code") == "Ok") {
                "OSRM: ${json.optString("message", json.optString("code", "ошибка маршрута"))}"
            }
            val waypoints = json.getJSONArray("waypoints")
            val ordered = (0 until waypoints.length())
                .sortedBy { waypoints.getJSONObject(it).getInt("waypoint_index") }
                .map { stops[it] }
            check(ordered.size == stops.size && ordered.toSet().size == stops.size) {
                "Оптимизатор вернул неполный список точек"
            }
            PhotoRouteOptimizationResult(ordered, true)
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        val indexes = PhotoRouteOrdering.nearestNeighborOrder(
            stops.map { PhotoRouteCoordinate(it.latitude, it.longitude) }
        )
        PhotoRouteOptimizationResult(indexes.map(stops::get), false)
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
private fun PhotoMetadataField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val reviewLabel = if (value.isBlank()) "не распознано • заполнить" else "распознано • проверить"
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("$title • $reviewLabel", fontSize = 11.sp) },
        singleLine = true
    )
}

@Composable
fun PhotoRoutePlannerScreen(
    initialAction: String? = null,
    initialImageUri: Uri? = null,
    onInitialActionConsumed: () -> Unit = {},
    onInitialImageConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onApplyRoute: (List<RouteItem>, PhotoRouteHeader) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rawText by remember { mutableStateOf("") }
    var routeHeader by remember { mutableStateOf(PhotoRouteHeader()) }
    var routeCity by remember { mutableStateOf("Калуга") }
    var addresses by remember { mutableStateOf(listOf<String>()) }
    var located by remember { mutableStateOf(listOf<LocatedPhotoStop>()) }
    var routeOrigin by remember { mutableStateOf<LocatedPhotoStop?>(null) }
    var status by remember { mutableStateOf("Сфотографируйте маршрутный лист. Русский текст распознаётся на устройстве после загрузки языковой модели.") }
    var busy by remember { mutableStateOf(false) }
    var showRawText by remember { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }
    var capturedUris by remember { mutableStateOf(listOf<Uri>()) }
    fun updateStopMetadata(index: Int, transform: (PhotoStopMetadata) -> PhotoStopMetadata) {
        if (index !in located.indices) return
        val updated = located.toMutableList()
        updated[index] = updated[index].copy(metadata = transform(updated[index].metadata))
        located = updated
    }
    fun processPhotoUris(uris: List<Uri>, onFinished: (() -> Unit)? = null) {
        if (uris.isEmpty()) {
            status = "Фотография не передана в обработчик. Выберите снимок ещё раз."
            onFinished?.invoke()
            return
        }
        if (busy) {
            status = "Предыдущая фотография ещё обрабатывается. Дождитесь завершения."
            return
        }
        scope.launch {
            busy = true
            rawText = ""
            addresses = emptyList()
            located = emptyList()
            routeOrigin = null
            status = "Фото получено. Подготавливаю OCR…"
            try {
                val recognizedTexts = mutableListOf<String>()
                uris.forEachIndexed { index, uri ->
                    status = "Открываю фото ${index + 1} из ${uris.size}…"
                    recognizedTexts += runRussianOcr(context, uri) { message ->
                        status = "Фото ${index + 1} из ${uris.size}: $message"
                    }
                }
                rawText = recognizedTexts.filter { it.isNotBlank() }.joinToString("\n\n--- Следующая фотография ---\n\n")
                check(rawText.isNotBlank()) { "OCR не распознал текст на фотографии. Попробуйте снимок крупнее и без бликов." }
                routeHeader = PhotoRouteHeaderParser.parse(rawText)
                addresses = PhotoRouteTextParser.extractCandidates(rawText, routeCity)
                status = if (addresses.isEmpty()) {
                    "Текст распознан, но адреса автоматически не выделены. Проверьте текст ниже и добавьте адреса вручную."
                } else {
                    "Готово: распознано фотографий ${uris.size}, найдено адресов-кандидатов ${addresses.size}. Проверьте список."
                }
            } catch (error: Exception) {
                status = "Не удалось обработать фото: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                busy = false
                onFinished?.invoke()
            }
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraOpen = true
        else Toast.makeText(context, "Для съёмки маршрутника нужно разрешение камеры", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(initialAction) {
        if (initialAction == "camera") cameraPermission.launch(Manifest.permission.CAMERA)
        if (initialAction != null) onInitialActionConsumed()
    }
    LaunchedEffect(initialImageUri) {
        initialImageUri?.let { uri ->
            status = "Фотография получена из загрузчика. Запускаю распознавание…"
            processPhotoUris(listOf(uri), onFinished = onInitialImageConsumed)
        }
    }
    if (cameraOpen) {
        RouteCameraCaptureScreen(
            onPhotoCaptured = { uri ->
                capturedUris = capturedUris + uri
                processPhotoUris(listOf(uri))
            },
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
        Text(status, color = if (status.startsWith("Не удалось") || status.startsWith("Ошибка")) MaterialTheme.colorScheme.error else TextMuted,
            style = MaterialTheme.typography.bodySmall)
        if (busy) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Text("Обработка фотографии… не закрывайте экран", color = TextLight, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { cameraPermission.launch(Manifest.permission.CAMERA) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сфотографировать ещё") }
        if (capturedUris.isNotEmpty()) {
            Text("Снимков с камеры: ${capturedUris.size}", color = TextLight, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { processPhotoUris(capturedUris); capturedUris = emptyList() },
                enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Распознать снятые фотографии")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (rawText.isNotBlank()) {
            Text("ДАННЫЕ МАРШРУТНОГО ЛИСТА", color = TextLight, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = routeHeader.routeNumber, onValueChange = { routeHeader = routeHeader.copy(routeNumber = it) }, label = { Text("Номер маршрута / рейса") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = routeHeader.driver, onValueChange = { routeHeader = routeHeader.copy(driver = it) }, label = { Text("Водитель") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = routeHeader.vehicle, onValueChange = { routeHeader = routeHeader.copy(vehicle = it) }, label = { Text("Транспортное средство") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = routeHeader.vehicleNumber, onValueChange = { routeHeader = routeHeader.copy(vehicleNumber = it) }, label = { Text("Государственный номер") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = { showRawText = !showRawText }) {
            Text(if (showRawText) "Скрыть распознанный текст" else "Показать распознанный текст для ручной проверки")
        }
        if (showRawText) {
            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                label = { Text("Распознанный текст (можно исправить)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 220.dp),
                minLines = 4
            )
            OutlinedButton(onClick = {
                addresses = PhotoRouteTextParser.extractCandidates(rawText, routeCity)
                located = emptyList()
                routeOrigin = null
                status = "Кандидатов адресов: ${addresses.size}. Проверьте список ниже."
            }, modifier = Modifier.fillMaxWidth()) { Text("Повторно выделить адреса") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = routeCity,
            onValueChange = { routeCity = it },
            label = { Text("Город маршрута") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        if (located.isEmpty()) {
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
                            located = resolved.map { stop ->
                                stop.copy(metadata = PhotoRouteTextParser.extractMetadata(rawText, stop.address))
                            }
                            if (failed.isNotEmpty()) {
                                status = "Не удалось определить адреса: ${failed.joinToString("; ")}. Исправьте их и повторите."
                            } else if (resolved.size < 2) {
                                status = "Найдено меньше двух координат. Проверьте адреса."
                            } else {
                                // Сохраняем OCR-метаданные: раньше чистый resolved затирал их после геокодирования.
                                routeOrigin = null
                                located = resolved.map { stop ->
                                    stop.copy(metadata = PhotoRouteTextParser.extractMetadata(rawText, stop.address))
                                }
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
        }
        if (located.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Точки маршрута: ${located.size}", color = TextLight, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = {
                located = emptyList()
                status = "Список адресов открыт для добавления, удаления и исправления."
            }) { Text("Изменить список адресов") }
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
                            val result = optimizeStopsOnly(located)
                            located = result.stops
                            status = if (result.usedRoadNetwork) {
                                "Порядок оптимизирован по дорожной сети. Все ${located.size} точек сохранены."
                            } else {
                                "OSRM сейчас недоступен. Точки упорядочены локально по расстоянию по прямой; проверьте порядок перед выездом."
                            }
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
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Точка ${index + 1}", color = TextLight, style = MaterialTheme.typography.labelLarge)
                            Text(stop.address, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = {
                                val query = "${stop.latitude},${stop.longitude}"
                                openExternalMap(context, "https://yandex.ru/maps/?text=" + java.net.URLEncoder.encode(query, "UTF-8"))
                            }) { Text("Проверить в Яндекс Картах") }
                            PhotoMetadataField("Магазин", stop.metadata.storeName) { value ->
                                updateStopMetadata(index) { metadata -> metadata.copy(storeName = value) }
                            }
                            PhotoMetadataField("Время / условие доставки", stop.metadata.workingHours) { value ->
                                updateStopMetadata(index) { metadata -> metadata.copy(workingHours = value) }
                            }
                            PhotoMetadataField("Комментарий", stop.metadata.comment) { value ->
                                updateStopMetadata(index) { metadata -> metadata.copy(comment = value) }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = {
                                located = located.filterIndexed { i, _ -> i != index }
                                status = "Точка удалена из маршрута."
                            }) { Text("Убрать") }
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
                        val metadata = stop.metadata
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
                    onApplyRoute(items, routeHeader)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Также сохранить маршрут в DigiDrop") }
        }
    }
}
