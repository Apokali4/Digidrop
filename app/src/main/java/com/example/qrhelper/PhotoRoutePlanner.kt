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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
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

data class PhotoRouteHeader(
    val driver: String = "",
    val vehicle: String = "",
    val vehicleNumber: String = "",
    val routeNumber: String = ""
)

internal object PhotoRouteHeaderParser {
    private val platePattern = Regex("""(?iu)([АВЕКМНОРСТУХABEKMHOPCTYX]\s*\d{3}\s*[АВЕКМНОРСТУХABEKMHOPCTYX]{2}\s*\d\s*\d\s*\d?)""")

    fun parse(text: String): PhotoRouteHeader {
        val lines = text.lineSequence()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }
            .toList()

        fun value(pattern: Regex): String = lines.firstNotNullOfOrNull { line ->
            pattern.find(line)?.groupValues?.getOrNull(1)?.trim()
                ?.trim(':', '-', '—', '№', '.', ' ')
                ?.takeIf { it.isNotBlank() }
        }.orEmpty()

        val driver = value(Regex("""(?iu)(?:водитель|экспедитор)\s*[:№-]?\s*(.+)$"""))
            .replace(Regex("""(?iu)\s+(?:экспедитор|водитель)\s*:.*$"""), "")
            .trim()

        var vehicle = ""
        var vehicleNumber = ""
        val vehicleLine = lines.firstOrNull {
            Regex("""(?iu)(?:транспортное\s+средство|автомобиль|машина|марка\s+авто)""").containsMatchIn(it)
        }
        if (vehicleLine != null) {
            val remainder = vehicleLine.replaceFirst(
                Regex("""(?iu)^.*?(?:транспортное\s+средство|автомобиль|машина|марка\s+авто)\s*[:№-]?\s*"""), ""
            ).trim()
            val normalizedRemainder = remainder.replace(Regex("""(?<=\d)\s+(?=\d)"""), "")
            vehicleNumber = platePattern.find(normalizedRemainder)?.groupValues?.getOrNull(1)
                ?.replace(Regex("""\s+"""), "")?.uppercase(Locale("ru", "RU")).orEmpty()
            vehicle = normalizedRemainder.replace(platePattern, "")
                .replace(Regex("""(?iu)\s*№\s*"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim(' ', ',', ';', '-', '—')
        }

        if (vehicleNumber.isBlank()) {
            val rawPlate = value(Regex("""(?iu)(?:гос(?:ударственный)?\.?\s*номер|г\.?\s*н\.?|номер\s+автомобиля)\s*[:№-]?\s*([А-ЯЁA-Z0-9 -]{5,})"""))
            vehicleNumber = (platePattern.find(rawPlate)?.groupValues?.getOrNull(1) ?: rawPlate)
                .replace(Regex("""\s+"""), "").uppercase(Locale("ru", "RU"))
        }

        val routeNumberFromLabel = value(
            Regex("""(?iu)(?:номер\s+маршрута|маршрут|рейс|задание\s+на\s+отгрузку)\s*№?\s*[:№-]?\s*([А-ЯЁA-Z0-9/-]+)""")
        )
        val routeNumber = routeNumberFromLabel.ifBlank {
            Regex("""(?iu)(?<![А-ЯЁA-Z0-9])([А-ЯЁA-Z])\s*([0-9][0-9\s]{7,}[0-9])(?![0-9])""")
                .find(text)?.let { it.groupValues[1] + it.groupValues[2].replace(Regex("""\s+"""), "") }.orEmpty()
        }
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
        Regex("""(?iu)красное\s+и(?:\s+бел(?:ое|\w*))?""") to "Красное и Белое",
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

    internal fun canonicalAddressKey(address: String): String {
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
        val addressIndices = lines.indices.filter { lineIndex -> cleanAddressFragment(lines[lineIndex], "") != null }
        val previousAddress = addressIndices.lastOrNull { it < index } ?: -1
        val nextAddress = addressIndices.firstOrNull { it > index } ?: lines.size
        val blockStart = if (index >= 0) maxOf(previousAddress + 1, index - 3) else 0
        val blockEnd = if (index >= 0) minOf(nextAddress, index + 7) else lines.size
        val block = if (index >= 0) lines.subList(blockStart, blockEnd) else lines
        val anchor = lines.getOrNull(index).orEmpty()
        val houseMatch = houseAfterStreet.find(anchor, (streetHint.find(anchor)?.range?.last ?: -1) + 1)
        val afterHouse = houseMatch?.let { anchor.substring(it.range.last + 1) }
            ?.trim(' ', ',', ';', '|', '.', ':', '-', '—').orEmpty()

        val legalPattern = Regex("""(?iu)\b(ИП|ООО|АО|ПАО|ОАО|ЗАО|ЧУП|УП|ГУП|МУП|ТОО|Тов\.?|товарищество)\b""")
        val companyLine = block.withIndex()
            .filter { (_, line) -> legalPattern.containsMatchIn(line) && !line.startsWith("ИП", ignoreCase = true) }
            .minByOrNull { (lineIndex, _) -> kotlin.math.abs(blockStart + lineIndex - index) }?.value.orEmpty()
        val owner = block.withIndex()
            .filter { (_, line) -> line.startsWith("ИП", ignoreCase = true) && legalPattern.containsMatchIn(line) }
            .minByOrNull { (lineIndex, _) -> kotlin.math.abs(blockStart + lineIndex - index) }?.value ?: companyLine
        val phone = block.withIndex()
            .mapNotNull { (lineIndex, line) -> Regex("""(?iu)(?<!\d)(?:\+7|8)?(?:[\s()\-]*\d){10}(?!\d)""").find(line)?.value?.let { kotlin.math.abs(blockStart + lineIndex - index) to it } }
            .minByOrNull { it.first }?.second.orEmpty().trim()

        val timePatterns = listOf(
            Regex("""(?iu)\d{1,2}[:.]\d{2}\s*(?:[-–—]|до)\s*\d{1,2}[:.]\d{2}"""),
            Regex("""(?iu)до\s*\d{1,2}[:.]\d{2}"""),
            Regex("""(?iu)(?:с|после)\s*\d{1,2}[:.]\d{2}""")
        )
        val hours = block.withIndex().mapNotNull { (lineIndex, line) ->
            timePatterns.firstNotNullOfOrNull { it.find(line)?.value }?.let { kotlin.math.abs(blockStart + lineIndex - index) to it }
        }.minByOrNull { it.first }?.second.orEmpty()

        val cash = block.withIndex()
            .filter { (_, line) -> Regex("""(?iu)(налич|деньг|забрат|оплат|инкассац|касс|документ)""").containsMatchIn(line) }
            .minByOrNull { (lineIndex, _) -> kotlin.math.abs(blockStart + lineIndex - index) }?.value.orEmpty()
        val suffixBrand = afterHouse.takeIf {
            it.isNotBlank() && !legalPattern.containsMatchIn(it) &&
                it.length <= 48 && !Regex("""[^А-Яа-яЁёA-Za-z0-9 .&()/-]""").containsMatchIn(it)
        }
        val brandInSuffix = suffixBrand?.let { suffix ->
            brandPatterns.firstOrNull { (pattern, _) -> pattern.containsMatchIn(suffix) }?.second
        }
        val brandNearby = block.withIndex().mapNotNull { (lineIndex, line) ->
            brandPatterns.firstOrNull { (pattern, _) -> pattern.containsMatchIn(line) }?.second
                ?.let { kotlin.math.abs(blockStart + lineIndex - index) to it }
        }.minByOrNull { it.first }?.second
        val store = brandInSuffix ?: suffixBrand ?: brandNearby ?: companyLine
        val instructionPattern = Regex("""(?iu)(достав|до\s*\d|после\s*\d|открыва|режим|при[её]м|разгруз|подъезд|въезд|позвон|ворот|забрать|передать|инструкц|коммент)""")
        val comment = block.withIndex().filter { (lineIndex, line) ->
            val absoluteIndex = blockStart + lineIndex
            absoluteIndex > index && absoluteIndex < nextAddress && line != cash &&
                !(hours.isNotBlank() && line.contains(hours, ignoreCase = true)) &&
                (instructionPattern.containsMatchIn(line) ||
                    line.contains("с ", ignoreCase = true) && Regex("""\d{1,2}[:.]\d{2}""").containsMatchIn(line))
        }.map { it.value }.distinct().joinToString("; ")
        val defaultHours = when (store) {
            "Красное и Белое" -> "09:00–22:00"
            "Бристоль" -> "08:00–22:00"
            else -> ""
        }
        return PhotoStopMetadata(store, owner, phone, hours.ifBlank { defaultHours }, cash, comment)
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
    val palette = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .background(palette.surfaceVariant, RoundedCornerShape(14.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(title, color = palette.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = title != "Комментарий",
            minLines = if (title == "Комментарий") 2 else 1,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.primary,
                unfocusedBorderColor = palette.outline
            )
        )
    }
}

@Composable
private fun PhotoPlannerTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    val palette = com.example.qrhelper.ui.theme.DigiDropPalette
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (primary) palette.primary else palette.surface,
            disabledContainerColor = palette.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (primary) palette.primary else palette.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = if (primary) androidx.compose.ui.graphics.Color.White else palette.onSurface,
                style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) Text(subtitle,
                color = if (primary) androidx.compose.ui.graphics.Color.White.copy(alpha = .88f) else palette.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
    }
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
    val palette = com.example.qrhelper.ui.theme.DigiDropPalette
    var rawText by remember { mutableStateOf("") }
    var routeHeader by remember { mutableStateOf(PhotoRouteHeader()) }
    var routeCity by remember { mutableStateOf("Калуга") }
    var addresses by remember { mutableStateOf(listOf<String>()) }
    var located by remember { mutableStateOf(listOf<LocatedPhotoStop>()) }
    var routeOrigin by remember { mutableStateOf<LocatedPhotoStop?>(null) }
    var status by remember { mutableStateOf("Сфотографируйте маршрутный лист. Русский текст распознаётся на устройстве.") }
    var busy by remember { mutableStateOf(false) }
    var showRawText by remember { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }
    var capturedUris by remember { mutableStateOf(listOf<Uri>()) }

    fun updateStopMetadata(index: Int, transform: (PhotoStopMetadata) -> PhotoStopMetadata) {
        if (index !in located.indices) return
        located = located.toMutableList().also { list -> list[index] = list[index].copy(metadata = transform(list[index].metadata)) }
    }
    fun processPhotoUris(uris: List<Uri>, onFinished: (() -> Unit)? = null) {
        if (uris.isEmpty()) {
            status = "Фотография не передана. Выберите снимок ещё раз."
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
                    recognizedTexts += runRussianOcr(context, uri) { message -> status = "Фото ${index + 1}: $message" }
                }
                rawText = recognizedTexts.filter { it.isNotBlank() }.joinToString("\n\n--- Следующая фотография ---\n\n")
                check(rawText.isNotBlank()) { "OCR не распознал текст. Попробуйте снимок крупнее и без бликов." }
                routeHeader = PhotoRouteHeaderParser.parse(rawText)
                addresses = PhotoRouteTextParser.extractCandidates(rawText, routeCity)
                if (addresses.isEmpty()) {
                    status = "Текст распознан, но адреса не выделены. Проверьте распознанный текст."
                } else {
                    status = "Распознано фотографий ${uris.size}. Определяю координаты точек…"
                    val (resolved, failed) = geocodePhotoStops(context, addresses, routeCity)
                    located = resolved.map { it.copy(metadata = PhotoRouteTextParser.extractMetadata(rawText, it.address)) }
                    status = when {
                        located.isEmpty() -> "Адресов: ${addresses.size}, но координаты не определились. Проверьте интернет и адреса."
                        failed.isNotEmpty() -> "Добавлено ${located.size} точек. Не найдены координаты: ${failed.joinToString("; ")}."
                        else -> "Готово: ${located.size} точек распознаны и собраны в маршрут."
                    }
                }
            } catch (error: Exception) {
                status = "Не удалось обработать фото: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                busy = false
                onFinished?.invoke()
            }
        }
    }

    val galleryPhotosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            capturedUris = capturedUris + uris
            status = "Добавлено фотографий: ${capturedUris.size}. Можно выбрать ещё или запустить общее распознавание."
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
            status = "Фотография получена. Запускаю распознавание…"
            processPhotoUris(listOf(uri), onFinished = onInitialImageConsumed)
        }
    }
    if (cameraOpen) {
        RouteCameraCaptureScreen(
            onPhotoCaptured = {
                uri ->
                capturedUris = capturedUris + uri
                status = "Фото добавлено в пакет. Снимите следующий лист или нажмите «Готово»."
            },
            onClose = { cameraOpen = false }
        )
        return
    }

    Column(
        Modifier.fillMaxSize().background(palette.background)
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PhotoPlannerTile("← Назад", "Вернуться на главный экран", onClick = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Маршрут по фотографии", color = palette.onSurface, style = MaterialTheme.typography.headlineSmall)
            Text("Импортируй лист, проверь каждую точку и выбери навигацию.", color = palette.onSurfaceVariant)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.outline)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (busy) "ОБРАБОТКА" else "СТАТУС", color = palette.primary, style = MaterialTheme.typography.labelLarge)
                Text(status, color = palette.onSurface, style = MaterialTheme.typography.bodyMedium)
                if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = palette.primary)
            }
        }
        PhotoPlannerTile("Сфотографировать маршрутный лист", "Камера • можно добавить несколько снимков", enabled = !busy, primary = true) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        PhotoPlannerTile("Выбрать фотографии из галереи", "Можно отметить сразу несколько снимков накладной", enabled = !busy) {
            galleryPhotosLauncher.launch("image/*")
        }
        if (capturedUris.isNotEmpty()) {
            PhotoPlannerTile("Распознать все фотографии", "В очереди снимков: ${capturedUris.size}", enabled = !busy, primary = true) {
                processPhotoUris(capturedUris)
                capturedUris = emptyList()
            }
        }

        if (rawText.isNotBlank()) {
            Text("ДАННЫЕ РЕЙСА", color = palette.primary, style = MaterialTheme.typography.titleMedium)
            listOf(
                "Номер маршрута / рейса" to routeHeader.routeNumber,
                "Водитель" to routeHeader.driver,
                "Транспортное средство" to routeHeader.vehicle,
                "Государственный номер" to routeHeader.vehicleNumber
            ).forEach { (label, value) ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { updated ->
                        routeHeader = when (label) {
                            "Номер маршрута / рейса" -> routeHeader.copy(routeNumber = updated)
                            "Водитель" -> routeHeader.copy(driver = updated)
                            "Транспортное средство" -> routeHeader.copy(vehicle = updated)
                            else -> routeHeader.copy(vehicleNumber = updated)
                        }
                    },
                    label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
        }

        PhotoPlannerTile(
            if (showRawText) "Скрыть распознанный текст" else "Проверить распознанный текст",
            "Ручная проверка OCR и повторное выделение адресов"
        ) { showRawText = !showRawText }
        if (showRawText) {
            OutlinedTextField(rawText, { rawText = it }, label = { Text("Распознанный текст") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 220.dp), minLines = 4)
            PhotoPlannerTile("Повторно выделить адреса", "Перестроить список по исправленному тексту") {
                addresses = PhotoRouteTextParser.extractCandidates(rawText, routeCity)
                located = emptyList()
                routeOrigin = null
                status = "Найдено адресов: ${addresses.size}."
            }
        }
        OutlinedTextField(routeCity, { routeCity = it }, label = { Text("Город маршрута") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)

        if (located.isEmpty()) {
            Text("АДРЕСА ДЛЯ МАРШРУТА: ${addresses.size}", color = palette.onSurface, style = MaterialTheme.typography.titleMedium)
            addresses.forEachIndexed { index, address ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.outline)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ТОЧКА ${index + 1}", color = palette.primary, style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(address, { value -> addresses = addresses.toMutableList().also { it[index] = value } },
                            label = { Text("Адрес") }, modifier = Modifier.fillMaxWidth())
                        PhotoPlannerTile("Удалить адрес", "Убрать эту строку из распознавания") {
                            addresses = addresses.filterIndexed { i, _ -> i != index }
                        }
                    }
                }
            }
            PhotoPlannerTile("+ Добавить адрес вручную", "Создать дополнительную точку") { addresses = addresses + "" }
            if (addresses.isNotEmpty()) {
                PhotoPlannerTile("Определить координаты точек", "Проверить адреса и подготовить карту", enabled = !busy, primary = true) {
                    scope.launch {
                        busy = true
                        try {
                            val (resolved, failed) = geocodePhotoStops(context, addresses, routeCity)
                            located = resolved.map { it.copy(metadata = PhotoRouteTextParser.extractMetadata(rawText, it.address)) }
                            status = if (failed.isEmpty()) "Координаты найдены для всех ${located.size} точек."
                                else "Координаты найдены для ${located.size}. Не найдены: ${failed.joinToString("; ")}."
                        } finally { busy = false }
                    }
                }
            }
        }

        if (located.isNotEmpty()) {
            Text("МАРШРУТ • ${located.size} ТОЧЕК", color = palette.onSurface, style = MaterialTheme.typography.titleLarge)
            Text("Зажми плитку и перетащи её, чтобы изменить порядок. Навигатор получит именно эту последовательность.",
                color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            PhotoPlannerTile("Оптимизировать по дорожной сети", "Если дорожный сервис недоступен, будет честно показана локальная оценка.",
                enabled = !busy && located.size >= 2, primary = true) {
                scope.launch {
                    busy = true
                    status = "Оптимизирую маршрут…"
                    try {
                        val result = optimizeStopsOnly(located)
                        located = result.stops
                        status = if (result.usedRoadNetwork) "Порядок рассчитан по дорожной сети."
                            else "Дорожный сервис недоступен. Порядок оценён по расстоянию по прямой, проверь перед выездом."
                    } catch (error: Exception) {
                        status = "Оптимизация не выполнена: ${error.message ?: "ошибка"}"
                    } finally { busy = false }
                }
            }

            located.forEachIndexed { index, stop ->
                var dragDistance by remember(stop.address) { mutableStateOf(0f) }
                Card(
                    modifier = Modifier.fillMaxWidth().pointerInput(stop.address, located.size) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { dragDistance = 0f },
                            onDragCancel = { dragDistance = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragDistance += amount.y
                                if (dragDistance > 72f && index < located.lastIndex) {
                                    located = located.toMutableList().also { list ->
                                        val item = list.removeAt(index); list.add(index + 1, item)
                                    }
                                    dragDistance = 0f
                                } else if (dragDistance < -72f && index > 0) {
                                    located = located.toMutableList().also { list ->
                                        val item = list.removeAt(index); list.add(index - 1, item)
                                    }
                                    dragDistance = 0f
                                }
                            }
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, palette.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("ТОЧКА ${index + 1}", color = palette.primary, style = MaterialTheme.typography.titleMedium)
                            Text("⠿ ПЕРЕТАЩИТЬ", color = palette.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(stop.metadata.storeName.ifBlank { "Название магазина не распознано" },
                            color = palette.onSurface, style = MaterialTheme.typography.titleLarge)
                        Text(stop.address, color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        PhotoPlannerTile("Проверить адрес в Яндекс Картах", "Откроется найденная координата") {
                            val query = "${stop.latitude},${stop.longitude}"
                            openExternalMap(context, "https://yandex.ru/maps/?text=" + java.net.URLEncoder.encode(query, "UTF-8"))
                        }
                        PhotoMetadataField("Магазин", stop.metadata.storeName) { value -> updateStopMetadata(index) { it.copy(storeName = value) } }
                        PhotoMetadataField("Время / условие доставки", stop.metadata.workingHours) { value -> updateStopMetadata(index) { it.copy(workingHours = value) } }
                        PhotoMetadataField("Телефон", stop.metadata.phone) { value -> updateStopMetadata(index) { it.copy(phone = value) } }
                        PhotoMetadataField("Комментарий", stop.metadata.comment) { value -> updateStopMetadata(index) { it.copy(comment = value) } }
                        PhotoPlannerTile("Удалить точку", "Убрать адрес из текущего маршрута") {
                            located = located.filterIndexed { i, _ -> i != index }
                            status = "Точка удалена."
                        }
                    }
                }
            }

            Text("НАВИГАЦИЯ", color = palette.onSurface, style = MaterialTheme.typography.titleLarge)
            val coords = located.map { PhotoRouteCoordinate(it.latitude, it.longitude) }
            val segments = if (coords.size >= 2) PhotoRouteNavigationUrls.yandexSegments(coords) else emptyList()
            if (segments.isEmpty()) {
                Text("Для Яндекс Навигатора нужны минимум две точки с координатами.", color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else if (segments.size == 1) {
                PhotoPlannerTile("Яндекс Навигатор", "Открыть все ${coords.size} точек в заданном порядке") {
                    openExternalMap(context, PhotoRouteNavigationUrls.yandex(segments.first()))
                }
            } else {
                segments.forEachIndexed { index, segment ->
                    PhotoPlannerTile("Яндекс • участок ${index + 1} из ${segments.size}",
                        "${segment.size} точек • соседние участки имеют общую переходную точку") {
                        openExternalMap(context, PhotoRouteNavigationUrls.yandex(segment))
                    }
                }
            }
            PhotoPlannerTile("Сохранить маршрут в DigiDrop", "Передать точки в рабочий список рейса", primary = true) {
                val items = located.mapIndexed { index, stop ->
                    RouteItem(
                        barcode = "PHOTO-${index + 1}",
                        storeName = stop.metadata.storeName.ifBlank { "Точка ${index + 1}" },
                        address = stop.address,
                        itemName = "Доставка по маршрутному листу",
                        totalBoxes = 0,
                        phone = stop.metadata.phone,
                        note = stop.metadata.comment.ifBlank { "Импортировано из фотографии" },
                        latitude = stop.latitude,
                        longitude = stop.longitude,
                        ownerName = stop.metadata.ownerName,
                        workingHours = stop.metadata.workingHours,
                        cashNote = stop.metadata.cashNote,
                        extraComment = stop.metadata.comment
                    )
                }
                onApplyRoute(items, routeHeader)
            }
        }
    }
}
