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
    val comment: String = "",
    val customerCode: String = "",
    val pointNumber: String = "",
    val contactPerson: String = "",
    val packageCount: Int = 0,
    val placeCount: Int = 0,
    val invoiceNumber: String = "",
    val rawColumns: List<String> = emptyList()
)

internal data class PhotoRouteDeliveryRow(
    val pointNumber: String = "",
    val customerCode: String = "",
    val organization: String = "",
    val address: String = "",
    val phone: String = "",
    val packageCount: Int = 0,
    val placeCount: Int = 0,
    val invoiceNumber: String = "",
    val contactPerson: String = "",
    val cashCollection: String = "",
    val comment: String = "",
    val rawCells: List<String> = emptyList(),
    val numericReviewFields: List<String> = emptyList()
)

/**
 * Reads the column layout from the photographed sheet itself. The first page's
 * header remains active on continuation pages where OCR did not capture it.
 * Rows are records, not stops: duplicate addresses are deliberately retained.
 */
internal object PhotoRouteTableParser {
    private enum class Column { POINT, CODE, ORGANIZATION, ADDRESS, PHONE, CONTACT, PACKAGES, PLACES, INVOICE, CASH, COMMENT }
    private val aliases = mapOf(
        Column.POINT to listOf(Regex("""(?iu)^(?:№|номер|п/п|пункт|точка)$""")),
        Column.CODE to listOf(Regex("""(?iu)^(?:код|код\s+клиента|код\s+торговой\s+точки)$""")),
        Column.ORGANIZATION to listOf(Regex("""(?iu)^(?:клиент|организация|получатель|торговая\s+точка|магазин|наименование)$""")),
        Column.ADDRESS to listOf(Regex("""(?iu)^(?:адрес|адрес\s+доставки|место\s+доставки|адрес\s+точки)$""")),
        Column.PHONE to listOf(Regex("""(?iu)^(?:телефон|тел\.?|мобильный|номер\s+телефона)$""")),
        Column.CONTACT to listOf(Regex("""(?iu)^(?:контакт|контактное\s+лицо|представитель)$""")),
        Column.PACKAGES to listOf(Regex("""(?iu)^(?:коробки|короб|кор\.?|упаковки|упак\.?|количество\s+коробок)$""")),
        Column.PLACES to listOf(Regex("""(?iu)^(?:места|мест|количество\s+мест|кол-во\s+мест)$""")),
        Column.INVOICE to listOf(Regex("""(?iu)^(?:накладная|номер\s+накладной|документ|№\s*накладной)$""")),
        Column.CASH to listOf(Regex("""(?iu)^(?:инкассация|сбор\s+денег|забрать\s+деньги|сумма\s+к\s+сбору|оплата)$""")),
        Column.COMMENT to listOf(Regex("""(?iu)^(?:комментарий|коммент\.?|примечание|примечания)$"""))
    )

    fun parse(text: String, city: String = ""): List<PhotoRouteDeliveryRow> {
        var schema: Map<Column, Int>? = null
        val rows = mutableListOf<PhotoRouteDeliveryRow>()
        text.lineSequence().forEach { source ->
            val line = source.trim()
            if (line.isBlank() || line.startsWith("---")) return@forEach
            val cells = splitCells(line)
            val detected = detectHeader(cells)
            if (detected != null) {
                schema = detected
                return@forEach
            }
            val columns = schema ?: return@forEach
            val addressCell = cells.getOrNull(columns[Column.ADDRESS] ?: -1).orEmpty()
            val address = PhotoRouteTextParser.extractCandidates(addressCell, city).firstOrNull()
                ?: PhotoRouteTextParser.extractCandidates(cells.joinToString(" "), city).firstOrNull()
                ?: ""
            val organization = cells.getOrNull(columns[Column.ORGANIZATION] ?: -1).orEmpty()
                .takeIf { it.isNotBlank() && !it.contains(Regex("""(?iu)итого|всего|маршрут""")) }.orEmpty()
            val pointNumber = cells.getOrNull(columns[Column.POINT] ?: -1).orEmpty()
            val customerCode = cells.getOrNull(columns[Column.CODE] ?: -1).orEmpty()
            val firstCell = cells.firstOrNull().orEmpty()
                .lowercase(Locale("ru", "RU"))
                .replace('ё', 'е')
                .trim()
                .trim(':', '.', '#', '-', ' ')
            if (firstCell == "итого" || firstCell == "всего" ||
                firstCell.startsWith("итого ") || firstCell.startsWith("всего ") ||
                firstCell.startsWith("общее итого") || firstCell.startsWith("общий итог")) {
                return@forEach
            }
            // Never discard an identifiable delivery just because OCR failed on its address.
            // Keep its raw cells so the driver can correct the address during review.
            if (address.isBlank() && pointNumber.isBlank() && customerCode.isBlank() && organization.isBlank()) {
                return@forEach
            }
            val comment = cells.getOrNull(columns[Column.COMMENT] ?: -1).orEmpty()
            val phone = cells.getOrNull(columns[Column.PHONE] ?: -1).orEmpty()
                .takeIf { Regex("""(?<!\d)(?:\+7|8)?(?:[\s()\-]*\d){10}(?!\d)""").containsMatchIn(it) }.orEmpty()
            fun rawNumber(column: Column): String = cells.getOrNull(columns[column] ?: -1).orEmpty().trim()
            fun number(column: Column): Int {
                val raw = rawNumber(column)
                if (raw.isBlank()) return 0
                val normalized = raw.lowercase(Locale("ru", "RU")).replace('ё', 'е')
                if (!Regex("""^\d{1,4}(?:\s*(?:шт\.?|кор\.?|места?))?$""").matches(normalized)) return 0
                return Regex("""^\d+""").find(normalized)?.value?.toIntOrNull() ?: 0
            }
            val numericReviewFields = buildList {
                listOf(Column.PACKAGES to "Коробки", Column.PLACES to "Места").forEach { (column, label) ->
                    val raw = rawNumber(column)
                    if (raw.isNotBlank() && number(column) == 0) add("$label: $raw")
                }
            }
            rows += PhotoRouteDeliveryRow(
                pointNumber = pointNumber,
                customerCode = customerCode,
                organization = organization,
                address = address,
                phone = phone,
                packageCount = number(Column.PACKAGES),
                placeCount = number(Column.PLACES),
                invoiceNumber = cells.getOrNull(columns[Column.INVOICE] ?: -1).orEmpty(),
                contactPerson = cells.getOrNull(columns[Column.CONTACT] ?: -1).orEmpty(),
                cashCollection = cells.getOrNull(columns[Column.CASH] ?: -1).orEmpty(),
                comment = comment,
                rawCells = cells,
                numericReviewFields = numericReviewFields
            )
        }
        return rows
    }

    private fun detectHeader(cells: List<String>): Map<Column, Int>? {
        val found = mutableMapOf<Column, Int>()
        cells.forEachIndexed { index, raw ->
            val value = raw.trim().trim(':', '.', '#').replace(Regex("""\s+"""), " ")
            aliases.entries.firstOrNull { (_, patterns) -> patterns.any { it.matches(value) } }
                ?.key?.let { found.putIfAbsent(it, index) }
        }
        return found.takeIf { Column.ADDRESS in it && it.size >= 2 }
    }

    private fun splitCells(line: String): List<String> {
        val delimiter = when {
            '\t' in line -> "\\t"
            '|' in line -> "\\|"
            ';' in line -> ";"
            Regex("""\s{2,}""").containsMatchIn(line) -> """\s{2,}"""
            else -> return listOf(line)
        }
        return line.split(Regex(delimiter)).map(String::trim)
    }
}

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
        """(?iu)(?<![А-Яа-яЁёA-Za-z])(улица|ул\.?|проспект|пр\.?|переулок|пер\.?|шоссе|ш\.?|набережная|наб\.?|площадь|пл\.?|б-р|бул\.?)(?![А-Яа-яЁёA-Za-z])"""
    )
    private val houseAfterStreet = Regex(
        """(?iu)(?:[,\s-]*(?:д\.?|дом|вх\.?|в)\s*№?\s*[-–—]?)?(\d{1,4}(?:\s*[А-Яа-яA-Za-z])?(?:(?:[/\-]|к)\d{1,4}[А-Яа-яA-Za-z]?)?)(?=\s*(?:[,;.]|корп|крп|строен|стр|лит|кв|$))"""
    )
    private val metadataHint = Regex("""(?iu)(маршрут|накладная|телефон|тел\.?|диспетчер|итого|всего|водитель|экспедитор|задание|отгрузк|документ|рейс)""")
    // Preserve корпус/строение/литера so separate buildings never collapse into one stop.
    private val buildingSuffixPattern = Regex(
        """(?iu)\s*[,;]?\s*(?:корп(?:ус)?\.?|крп\.?|строен(?:ие)?\.?|стр\.?|лит(?:ера)?\.?)\s*№?\s*[0-9а-яёa-z-]+"""
    )
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

    private fun normalizedBrandText(value: String): String =
        value.lowercase(Locale("ru", "RU"))
            .replace('ё', 'е')
            .filter { it.isLetterOrDigit() }

    private fun editDistance(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                val substitution = previous[j] + if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    substitution
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun brandFromLine(line: String): String? {
        brandPatterns.firstOrNull { (pattern, _) -> pattern.containsMatchIn(line) }?.let { return it.second }
        val words = line.lowercase(Locale("ru", "RU"))
            .replace('ё', 'е')
            .split(Regex("""[^а-яa-z0-9]+"""))
            .filter { it.isNotBlank() }
        for ((_, brand) in brandPatterns) {
            val targetWords = brand.lowercase(Locale("ru", "RU"))
                .replace('ё', 'е')
                .split(Regex("""[^а-яa-z0-9]+"""))
                .filter { it.isNotBlank() }
            val target = targetWords.joinToString("")
            val maxDistance = if (target.length >= 8) 2 else 1
            for (start in words.indices) {
                for (count in 1..minOf(targetWords.size + 1, words.size - start)) {
                    val candidate = words.subList(start, start + count).joinToString("")
                    if (candidate.length >= target.length - maxDistance &&
                        candidate.length <= target.length + maxDistance &&
                        editDistance(candidate, target) <= maxDistance
                    ) return brand
                }
            }
        }
        return null
    }

    private fun isPlausibleStoreSuffix(value: String): Boolean {
        val candidate = value.trim(' ', ',', ';', '|', '.', ':', '-', '—')
        if (candidate.isBlank() || candidate.length > 48) return false
        if (Regex("""(?iu)^(?:крп|кри|корп\.?|корпус|к\.?|стр\.?|строение|д\.?|дом|кв\.?|квартира)\.?(?=\s|$|№|-|\d)""")
                .containsMatchIn(candidate)) return false
        if (Regex("""(?<!\d)(?:\+7|8)?(?:[\s()\-]*\d){8,}(?!\d)""").containsMatchIn(candidate)) return false
        if (streetHint.containsMatchIn(candidate) || metadataHint.containsMatchIn(candidate)) return false
        return candidate.count(Char::isLetter) >= 3
    }

    internal fun cleanStoreName(candidate: String): String {
        var cleaned = candidate.trim()
            .replace(Regex("""(?iu)(?:^|\s+)(?:квартира|кв\.?|подъезд|офис|помещение)\b.*$"""), "")
            .replace(Regex("""(?iu)\s+(?:x[0-9a-z]{2,}|[a-z]{2,}[-_]\d{6,}|\d{8,})\b.*$"""), "")
            .trim(' ', ',', ';', '|', '.', ':', '-', '—')
        val words = cleaned.split(Regex("""\s+""")).toMutableList()
        while (words.size > 1 && words.last().length <= 2 && words.last().any { it.isLetter() } &&
            words.last().none { it.isUpperCase() }
        ) words.removeAt(words.lastIndex)
        cleaned = words.joinToString(" ").trim()
        if (cleaned.isBlank() || cleaned.length > 48) return ""
        if (Regex("""(?iu)^(?:точка|магазин|объект)\s*#?\s*\d+$""").matches(cleaned)) return ""
        if (Regex("""(?iu)^[а-яёa-z]{3,}\s*[:№-]\s*\d{1,4}$""").matches(cleaned)) return ""
        if (Regex("""(?iu)^(?:букпр|бупкр|крп|кри|корп|корпус|стр|строение|лит)\b""").containsMatchIn(cleaned)) return ""
        if (cleaned.count { it.isLetter() } < 3) return ""
        return cleaned
    }

    private fun cleanAddressFragment(line: String, city: String): String? {
        val street = streetHint.find(line) ?: return null
        val house = houseAfterStreet.find(line, street.range.last + 1) ?: return null
        val cityMatch = if (city.isNotBlank()) {
            val cityName = city.trim().replace(Regex("""(?iu)^г\.?\s*"""), "")
            Regex("(?iu)(?:\\bг\\.?\\s*)?${Regex.escape(cityName)}").find(line)
                ?.takeIf { it.range.first <= street.range.first }
        } else {
            Regex("""(?iu)\bг\.?\s*[А-ЯЁ][А-Яа-яЁё-]+""").find(line)
                ?.takeIf { it.range.first <= street.range.first }
        }
        val start = cityMatch?.range?.first ?: street.range.first
        val buildingSuffix = buildingSuffixPattern.find(line.substring(house.range.last + 1))?.value.orEmpty()
        return (line.substring(start, house.range.last + 1) + buildingSuffix)
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
        val buildingSuffix = buildingSuffixPattern.find(address.substring(house.range.last + 1))
            ?.value.orEmpty()
            .lowercase(Locale("ru", "RU"))
            .replace(Regex("""(?iu)^(?:\s*[,;]?\s*)?(?:корп(?:ус)?\.?|крп\.?|строен(?:ие)?\.?|стр\.?|лит(?:ера)?\.?)\s*№?\s*"""), "")
            .replace(Regex("""[^\p{L}\p{N}]"""), "")
        return "$streetName#$houseNumber#$buildingSuffix"
    }

    fun extractCandidates(text: String, city: String = ""): List<String> =
        extractAddressRows(text, city).distinctBy(::canonicalAddressKey)

    /** Keeps repeated address rows: two customers can share one building. */
    internal fun extractRouteAddressRows(text: String, city: String = ""): List<String> =
        extractAddressRows(text, city)

    // [build-apk] Validate strict route-row extraction and geocoder matching.
    /**
     * Photo route import is intentionally stricter than generic address extraction:
     * a delivery address must belong to a numbered table row with a customer code.
     * Header/footer text and random street mentions are never promoted to stops.
     */
    internal fun extractNumberedRouteAddressRows(text: String, city: String = ""): List<String> {
        val lines = text.lineSequence()
            .map { it.replace(Regex("""^[\s•*№\-–—]+"""), "").trim() }
            .filter { it.isNotBlank() }
            .toList()
        // Row numbers and customer codes are optional in real delivery sheets.
        val rowStart = Regex("""(?iu)^\s*(?:№\s*)?(\d{1,3})[.)]?\s+(?:(\d{2,8})\s+)?(.+)$""")
        val numberedIndexes = lines.indices.filter { rowStart.containsMatchIn(lines[it]) }
        // If some rows have numbers, treat address lines after the first numbered
        // row as continuation rows whose number was lost by OCR. A lone address
        // above the table is commonly the dispatch/header address, not a stop.
        val firstNumbered = numberedIndexes.firstOrNull()
        val lastNumbered = numberedIndexes.lastOrNull()
        val result = mutableListOf<Pair<Int, String>>()
        lines.forEachIndexed { index, line ->
            val numbered = rowStart.find(line)
            val lineStreet = streetHint.find(line)
            val prefix = lineStreet?.let { line.substring(0, it.range.first) }.orEmpty()
            val citylessPrefix = prefix
                .replace(Regex("""(?iu)\bг\.?\s*${Regex.escape(city)}\b"""), "")
                .replace(Regex("""(?iu)\b${Regex.escape(city)}\b"""), "")
            val hasStoreNameOnRow = citylessPrefix.count(Char::isLetter) >= 3
            val previousLine = lines.getOrNull(index - 1).orEmpty()
            val previousLineLooksLikeStore = previousLine.count(Char::isLetter) >= 3 &&
                !metadataHint.containsMatchIn(previousLine) &&
                !streetHint.containsMatchIn(previousLine)
            val isRecoverableUnnumberedRow = numbered == null && lineStreet != null &&
                (firstNumbered == null || (index > firstNumbered && index < (lastNumbered ?: firstNumbered)) || hasStoreNameOnRow || previousLineLooksLikeStore)
            if (numbered == null && !isRecoverableUnnumberedRow) return@forEachIndexed

            var candidate = numbered?.groupValues?.get(3) ?: line
            val street = streetHint.find(candidate) ?: return@forEachIndexed
            if (houseAfterStreet.find(candidate, street.range.last + 1) == null) {
                val next = lines.getOrNull(index + 1).orEmpty()
                if (next.isNotBlank() && !rowStart.containsMatchIn(next) &&
                    !streetHint.containsMatchIn(next) &&
                    Regex("""(?<!\d)\d{1,4}(?!\d)""").containsMatchIn(next)
                ) candidate = "$candidate $next"
            }
            val address = cleanAddressFragment(candidate, city) ?: return@forEachIndexed
            val addressStreet = streetHint.find(address) ?: return@forEachIndexed
            if (houseAfterStreet.find(address, addressStreet.range.last + 1) == null) return@forEachIndexed
            result += index to address
        }
        return result.sortedBy { it.first }.map { it.second }
    }

    private fun extractAddressRows(text: String, city: String): List<String> {
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
    }

    fun extractMetadata(text: String, address: String): PhotoStopMetadata =
        extractMetadataForOccurrence(text, address, 0)

    internal fun extractMetadataForOccurrence(
        text: String,
        address: String,
        occurrence: Int
    ): PhotoStopMetadata {
        val normalizedText = text.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalizedText.lineSequence().map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }.toList()
        val targetKey = canonicalAddressKey(address)
        val matchingIndices = lines.indices.filter { lineIndex ->
            cleanAddressFragment(lines[lineIndex], "")?.let(::canonicalAddressKey) == targetKey
        }
        val index = matchingIndices.getOrNull(occurrence) ?: lines.indexOfFirst { line ->
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

        val legalPattern = Regex("""(?iu)\b(ИП|ООО|АО|ПАО|ОАО|ЗАО|ЧУП|ГУП|МУП|ТОО|Тов\.?|товарищество)\b""")
        val companyLine = block.withIndex()
            .filter { (lineIndex, line) ->
                blockStart + lineIndex <= index &&
                    legalPattern.containsMatchIn(line) &&
                    !line.startsWith("ИП", ignoreCase = true)
            }
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
        val cleanedSuffix = cleanStoreName(afterHouse)
        val suffixBrand = cleanedSuffix.takeIf {
            isPlausibleStoreSuffix(it) && !legalPattern.containsMatchIn(it) &&
                !Regex("""[^А-Яа-яЁёA-Za-z0-9 .&()/-]""").containsMatchIn(it)
        }
        val brandInSuffix = suffixBrand?.let { suffix ->
            val recognizedBrand = brandFromLine(suffix)
            if (recognizedBrand != null) {
                val trailingNumber = Regex("""\b\d{1,3}\s*$""").find(suffix)?.value?.trim().orEmpty()
                if (trailingNumber.isNotBlank()) "$recognizedBrand $trailingNumber" else recognizedBrand
            } else cleanStoreName(suffix)
        }
        val brandNearby = block.withIndex()
            .filter { (lineIndex, _) -> blockStart + lineIndex < index }
            .mapNotNull { (lineIndex, line) ->
                brandFromLine(line)?.let { kotlin.math.abs(blockStart + lineIndex - index) to it }
            }.minByOrNull { it.first }?.second
        val store = brandInSuffix ?: cleanStoreName(brandNearby ?: companyLine)
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
        val displayName = store.ifBlank {
            owner.takeIf { it.startsWith("ИП", ignoreCase = true) }.orEmpty()
        }
        return PhotoStopMetadata(displayName, owner, phone, hours.ifBlank { defaultHours }, cash, comment)
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
    val metadata: PhotoStopMetadata = PhotoStopMetadata(),
    val additionalDeliveries: List<PhotoStopMetadata> = emptyList()
) {
    val deliveries: List<PhotoStopMetadata>
        get() = listOf(metadata) + additionalDeliveries
}

/** Groups delivery rows by physical address while preserving every supplier row. */
internal fun groupPhotoRouteAddresses(addresses: List<String>): List<List<String>> =
    addresses.filter(String::isNotBlank)
        .groupBy { PhotoRouteTextParser.canonicalAddressKey(it) }
        .values.toList()

data class PhotoRouteCoordinate(val latitude: Double, val longitude: Double)

internal object PhotoRouteNavigationUrls {
    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun validate(stops: List<PhotoRouteCoordinate>) {
        require(stops.size >= 2) { "Для навигации нужны минимум две точки" }
    }

    const val YANDEX_MAX_STOPS = 20

    fun yandexSegments(stops: List<PhotoRouteCoordinate>): List<List<PhotoRouteCoordinate>> {
        validate(stops)
        val firstSegmentLimit = YANDEX_MAX_STOPS - 1 // Первый участок: текущая позиция водителя тоже занимает одну из 20 точек Яндекса.
        if (stops.size <= firstSegmentLimit) return listOf(stops)
        val segments = mutableListOf<List<PhotoRouteCoordinate>>()
        var start = 0
        var limit = firstSegmentLimit
        while (start < stops.lastIndex) {
            val endExclusive = minOf(start + limit, stops.size)
            segments += stops.subList(start, endExclusive).toList()
            if (endExclusive == stops.size) break
            // Последняя точка участка становится стартом следующего участка.
            start = endExclusive - 1
            limit = YANDEX_MAX_STOPS
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

    internal fun yandexNavigatorUrl(stops: List<PhotoRouteCoordinate>, startsFromPreviousStop: Boolean = false): String {
        require(stops.isNotEmpty()) { "Нет точки назначения" }
        require(stops.size <= YANDEX_MAX_STOPS) {
            "Яндекс Навигатор принимает не больше 20 точек за один участок"
        }
        val query = mutableListOf<Pair<String, String>>()
        val destinations = if (startsFromPreviousStop) {
            query += "lat_from" to stops.first().latitude.toString()
            query += "lon_from" to stops.first().longitude.toString()
            stops.drop(1)
        } else stops
        require(destinations.isNotEmpty()) { "Нет точки назначения" }
        destinations.dropLast(1).forEachIndexed { index, point ->
            query += "lat_via_$index" to point.latitude.toString()
            query += "lon_via_$index" to point.longitude.toString()
        }
        val destination = destinations.last()
        query += "lat_to" to destination.latitude.toString()
        query += "lon_to" to destination.longitude.toString()
        return "yandexnavi://build_route_on_map?" +
            query.joinToString("&") { (key, value) -> "$key=$value" }
    }

    fun yandexNavigator(stops: List<PhotoRouteCoordinate>, startsFromPreviousStop: Boolean = false): Uri =
        Uri.parse(yandexNavigatorUrl(stops, startsFromPreviousStop))


}

internal fun openYandexNavigator(
    context: android.content.Context,
    stops: List<PhotoRouteCoordinate>,
    startsFromPreviousStop: Boolean = false
) {
    try {
        val intent = Intent(
            Intent.ACTION_VIEW,
            PhotoRouteNavigationUrls.yandexNavigator(stops, startsFromPreviousStop)
        ).setPackage("ru.yandex.yandexnavi")
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Яндекс Навигатор не установлен. Установите его и повторите.", Toast.LENGTH_LONG).show()
    } catch (error: Exception) {
        Toast.makeText(context, error.message ?: "Не удалось открыть маршрут в Яндекс Навигаторе", Toast.LENGTH_LONG).show()
    }
}

internal object PhotoRouteGeocodingQuery {
    fun build(address: String, city: String): String {
        val cleanCity = city.trim()
        if (cleanCity.isEmpty()) return address.trim()
        return if (cleanCity.equals("Калуга", ignoreCase = true))
            "${address.trim()}, Калужская область, Россия"
        else "${address.trim()}, $cleanCity, Россия"
    }

    // Калуга в настройке маршрута означает всю Калужскую область, а не радиус вокруг города.
    fun isWithinSelectedCity(latitude: Double, longitude: Double, city: String): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun isInSelectedRegion(adminArea: String?, city: String): Boolean {
        if (!city.trim().equals("Калуга", ignoreCase = true)) return true
        val region = adminArea.orEmpty().lowercase(Locale("ru", "RU")).replace('ё', 'е')
        // Если геокодер не вернул административный регион, запрос уже ограничен областью.
        return region.isBlank() || region.contains("калуж")
    }

    /**
     * A geocoder result is usable only when it confirms BOTH the requested street
     * and house. Geocoder.getFromLocationName is fuzzy and may otherwise return
     * a nearby, unrelated address (which used to create phantom route stops).
     */
    // [build-apk] Rebuild after strict route/geocoder test correction.
    fun matchesRequestedAddress(
        requestedAddress: String,
        candidateStreet: String?,
        candidateHouse: String?,
        candidateAddressLine: String? = null
    ): Boolean {
        fun normalizeStreet(value: String): String = value.lowercase(Locale("ru", "RU"))
            .replace('ё', 'е')
            .replace(Regex("""(?iu)^\s*(?:улица|ул\.?|проспект|пр\.?|переулок|пер\.?|шоссе|ш\.?|набережная|наб\.?|площадь|пл\.?|б-р|бул\.?)\s*"""), "")
            .replace(Regex("""[^\p{L}\p{N}]"""), "")

        fun houseFrom(value: String): String {
            val street = streetPatternForGeocoding.find(value) ?: return ""
            val tail = value.substring(street.range.last + 1)
            return Regex("""(?iu)(?:д\.?|дом)?\s*№?\s*(\d{1,4}(?:\s*[а-яёa-z])?(?:(?:[/\-]|к)\d{1,4}[а-яёa-z]?)?)""")
                .find(tail)?.groupValues?.getOrNull(1)
                ?.lowercase(Locale("ru", "RU"))
                ?.replace(Regex("""[^\p{L}\p{N}]"""), "").orEmpty()
        }

        val requestedStreetMatch = streetPatternForGeocoding.find(requestedAddress) ?: return false
        val requestedTail = requestedAddress.substring(requestedStreetMatch.range.last + 1)
        val requestedHouse = Regex("""(?iu)(?:д\.?|дом)?\s*№?\s*(\d{1,4}(?:\s*[а-яёa-z])?(?:(?:[/\-]|к)\d{1,4}[а-яёa-z]?)?)""")
            .find(requestedTail)?.groupValues?.getOrNull(1)
            ?.lowercase(Locale("ru", "RU"))
            ?.replace(Regex("""[^\p{L}\p{N}]"""), "").orEmpty()
        if (requestedHouse.isBlank()) return false
        val requestedStreet = normalizeStreet(
            requestedTail.substring(0, Regex("""(?iu)(?:д\.?|дом)?\s*№?\s*\d""").find(requestedTail)?.range?.first
                ?: return false
            )
        )
        val resolvedStreet = candidateStreet?.takeIf { it.isNotBlank() }?.let(::normalizeStreet)
            ?: candidateAddressLine?.let { line ->
                val match = streetPatternForGeocoding.find(line) ?: return@let ""
                val tail = line.substring(match.range.last + 1)
                val houseStart = Regex("""(?iu)(?:д\.?|дом)?\s*№?\s*\d""").find(tail)?.range?.first
                    ?: return@let ""
                normalizeStreet(tail.substring(0, houseStart))
            }.orEmpty()
        val resolvedHouse = candidateHouse?.takeIf { it.isNotBlank() }
            ?.let { it.lowercase(Locale("ru", "RU")).replace(Regex("""[^\p{L}\p{N}]"""), "") }
            ?: candidateAddressLine?.let(::houseFrom).orEmpty()
        return requestedStreet.isNotBlank() && resolvedStreet == requestedStreet &&
            resolvedHouse.isNotBlank() && resolvedHouse == requestedHouse
    }

    private val streetPatternForGeocoding = Regex(
        """(?iu)(?<![А-Яа-яЁёA-Za-z])(улица|ул\.?|проспект|пр\.?|переулок|пер\.?|шоссе|ш\.?|набережная|наб\.?|площадь|пл\.?|б-р|бул\.?)(?![А-Яа-яЁёA-Za-z])"""
    )
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
            val result = geocoder.getFromLocationName(query, 8)
                ?.filter { candidate ->
                    PhotoRouteGeocodingQuery.isWithinSelectedCity(candidate.latitude, candidate.longitude, city) &&
                        PhotoRouteGeocodingQuery.isInSelectedRegion(candidate.adminArea, city) &&
                        PhotoRouteGeocodingQuery.matchesRequestedAddress(
                            requestedAddress = address,
                            candidateStreet = candidate.thoroughfare,
                            candidateHouse = candidate.subThoroughfare,
                            candidateAddressLine = runCatching { candidate.getAddressLine(0) }.getOrNull()
                        )
                }
                ?.minByOrNull { candidate ->
                    val locality = listOfNotNull(candidate.locality, candidate.subAdminArea)
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
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var captureInProgress by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Наведите камеру на лист целиком. Для длинного маршрутника сделайте несколько кадров.") }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageCapture = null
        }
    }

    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    try {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                            .build()
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture
                        )
                        cameraProvider = provider
                        imageCapture = capture
                        message = "Камера готова. Нажмите «Снять фото»."
                    } catch (error: Exception) {
                        imageCapture = null
                        message = "Камера недоступна: ${error.localizedMessage ?: error.javaClass.simpleName}"
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )
        Text(
            message,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onClose,
                enabled = !captureInProgress,
                modifier = Modifier.weight(1f)
            ) { Text("Готово") }
            Button(
                onClick = {
                    val capture = imageCapture
                    if (capture == null) {
                        message = "Камера ещё запускается. Подождите появления сообщения «Камера готова»."
                    } else if (!captureInProgress) {
                        captureInProgress = true
                        message = "Снимаю фотографию…"
                        try {
                            val photoDirectory = File(context.filesDir, "route-manifest-photos")
                            if (!photoDirectory.exists() && !photoDirectory.mkdirs()) {
                                error("Не удалось создать папку для фотографий")
                            }
                            val file = File(photoDirectory, "route_photo_${System.currentTimeMillis()}.jpg")
                            val options = ImageCapture.OutputFileOptions.Builder(file).build()
                            capture.takePicture(
                                options,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                        captureInProgress = false
                                        if (file.isFile && file.length() > 0L) {
                                            message = "Фото сохранено. Можно снять следующую страницу."
                                            onPhotoCaptured(Uri.fromFile(file))
                                        } else {
                                            message = "Камера завершила съёмку, но файл пустой. Повторите снимок."
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        captureInProgress = false
                                        message = "Не удалось снять фото: ${exception.localizedMessage ?: exception.imageCaptureError}"
                                    }
                                }
                            )
                        } catch (error: Exception) {
                            captureInProgress = false
                            message = "Ошибка камеры: ${error.localizedMessage ?: error.javaClass.simpleName}"
                        }
                    }
                },
                enabled = imageCapture != null && !captureInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (captureInProgress) "Снимаю…" else if (imageCapture == null) "Запуск камеры…" else "Снять фото")
            }
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
    val palette = MaterialTheme.colorScheme
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
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
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

internal fun shouldResetPhotoRouteSession(initialAction: String?): Boolean = initialAction == "new"

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
    val palette = MaterialTheme.colorScheme
    val plannerPrefs = remember {
        context.getSharedPreferences("digitdrop_photo_route_session", android.content.Context.MODE_PRIVATE)
    }
    fun readStringList(key: String): List<String> = runCatching {
        val array = org.json.JSONArray(plannerPrefs.getString(key, "[]").orEmpty())
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())

    suspend fun copyPhotoIntoRouteStorage(source: Uri): Uri = withContext(Dispatchers.IO) {
        val directory = java.io.File(context.filesDir, "route-manifest-photos").apply { mkdirs() }
        val extension = when (context.contentResolver.getType(source)?.lowercase()) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ".jpg"
        }
        val target = java.io.File(directory, "manifest_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}$extension")
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Не удалось сохранить фотографию маршрутного листа")
        Uri.fromFile(target)
    }
    fun readLocatedStops(): List<LocatedPhotoStop> = runCatching {
        val array = org.json.JSONArray(plannerPrefs.getString("located_stops", "[]").orEmpty())
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val metadata = item.optJSONObject("metadata") ?: org.json.JSONObject()
            val address = item.optString("address")
            if (address.isBlank()) return@mapNotNull null
            LocatedPhotoStop(
                address = address,
                latitude = item.optDouble("latitude", Double.NaN),
                longitude = item.optDouble("longitude", Double.NaN),
                metadata = PhotoStopMetadata(
                    storeName = metadata.optString("storeName"),
                    ownerName = metadata.optString("ownerName"),
                    phone = metadata.optString("phone"),
                    workingHours = metadata.optString("workingHours"),
                    cashNote = metadata.optString("cashNote"),
                    comment = metadata.optString("comment"),
                    customerCode = metadata.optString("customerCode"),
                    pointNumber = metadata.optString("pointNumber"),
                    contactPerson = metadata.optString("contactPerson"),
                    packageCount = metadata.optInt("packageCount"),
                    placeCount = metadata.optInt("placeCount"),
                    invoiceNumber = metadata.optString("invoiceNumber"),
                    rawColumns = metadata.optJSONArray("rawColumns")?.let { array ->
                        (0 until array.length()).map { array.optString(it) }
                    }.orEmpty()
                ),
                additionalDeliveries = item.optJSONArray("additionalDeliveries")?.let { deliveries ->
                    (0 until deliveries.length()).mapNotNull { deliveryIndex ->
                        deliveries.optJSONObject(deliveryIndex)?.let { delivery ->
                            PhotoStopMetadata(
                                storeName = delivery.optString("storeName"),
                                ownerName = delivery.optString("ownerName"),
                                phone = delivery.optString("phone"),
                                workingHours = delivery.optString("workingHours"),
                                cashNote = delivery.optString("cashNote"),
                                comment = delivery.optString("comment"),
                                customerCode = delivery.optString("customerCode"),
                                pointNumber = delivery.optString("pointNumber"),
                                contactPerson = delivery.optString("contactPerson"),
                                packageCount = delivery.optInt("packageCount"),
                                placeCount = delivery.optInt("placeCount"),
                                invoiceNumber = delivery.optString("invoiceNumber"),
                                rawColumns = delivery.optJSONArray("rawColumns")?.let { array ->
                                    (0 until array.length()).map { array.optString(it) }
                                }.orEmpty()
                            )
                        }
                    }
                }.orEmpty()
            ).takeIf { it.latitude.isFinite() && it.longitude.isFinite() }
        }
    }.getOrDefault(emptyList())
    var rawText by remember { mutableStateOf(plannerPrefs.getString("raw_text", "").orEmpty()) }
    var routeHeader by remember {
        mutableStateOf(
            PhotoRouteHeader(
                driver = plannerPrefs.getString("header_driver", "").orEmpty(),
                vehicle = plannerPrefs.getString("header_vehicle", "").orEmpty(),
                vehicleNumber = plannerPrefs.getString("header_vehicle_number", "").orEmpty(),
                routeNumber = plannerPrefs.getString("header_route_number", "").orEmpty()
            )
        )
    }
    var routeCity by remember { mutableStateOf(plannerPrefs.getString("route_city", "Калуга").orEmpty()) }
    var addresses by remember { mutableStateOf(readStringList("addresses")) }
    var located by remember { mutableStateOf(readLocatedStops()) }
    val rowsNeedingAddressReview = remember(rawText, routeCity) {
        PhotoRouteTableParser.parse(rawText, routeCity).filter { it.address.isBlank() }
    }
    var routeOrigin by remember { mutableStateOf<LocatedPhotoStop?>(null) }
    var status by remember { mutableStateOf("Сфотографируйте маршрутный лист. Русский текст распознаётся на устройстве.") }
    var busy by remember { mutableStateOf(false) }
    var showRawText by remember { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }
    var capturedUris by remember {
        mutableStateOf(readStringList("captured_uris").mapNotNull { runCatching { Uri.parse(it) }.getOrNull() })
    }
    LaunchedEffect(rawText, routeHeader, routeCity, addresses, located, capturedUris) {
        val addressJson = org.json.JSONArray().apply { addresses.forEach(::put) }
        val photoJson = org.json.JSONArray().apply { capturedUris.forEach { put(it.toString()) } }
        val stopJson = org.json.JSONArray().apply {
            located.forEach { stop ->
                put(org.json.JSONObject().apply {
                    put("address", stop.address)
                    put("latitude", stop.latitude)
                    put("longitude", stop.longitude)
                    put("metadata", org.json.JSONObject().apply {
                        put("storeName", stop.metadata.storeName)
                        put("ownerName", stop.metadata.ownerName)
                        put("phone", stop.metadata.phone)
                        put("workingHours", stop.metadata.workingHours)
                        put("cashNote", stop.metadata.cashNote)
                        put("comment", stop.metadata.comment)
                        put("customerCode", stop.metadata.customerCode)
                        put("pointNumber", stop.metadata.pointNumber)
                        put("contactPerson", stop.metadata.contactPerson)
                        put("packageCount", stop.metadata.packageCount)
                        put("placeCount", stop.metadata.placeCount)
                        put("invoiceNumber", stop.metadata.invoiceNumber)
                        put("rawColumns", org.json.JSONArray(stop.metadata.rawColumns))
                    })
                    put("additionalDeliveries", org.json.JSONArray().apply {
                        stop.additionalDeliveries.forEach { delivery ->
                            put(org.json.JSONObject().apply {
                                put("storeName", delivery.storeName)
                                put("ownerName", delivery.ownerName)
                                put("phone", delivery.phone)
                                put("workingHours", delivery.workingHours)
                                put("cashNote", delivery.cashNote)
                                put("comment", delivery.comment)
                                put("customerCode", delivery.customerCode)
                                put("pointNumber", delivery.pointNumber)
                                put("contactPerson", delivery.contactPerson)
                                put("packageCount", delivery.packageCount)
                                put("placeCount", delivery.placeCount)
                                put("invoiceNumber", delivery.invoiceNumber)
                                put("rawColumns", org.json.JSONArray(delivery.rawColumns))
                            })
                        }
                    })
                })
            }
        }
        plannerPrefs.edit()
            .putString("raw_text", rawText)
            .putString("header_driver", routeHeader.driver)
            .putString("header_vehicle", routeHeader.vehicle)
            .putString("header_vehicle_number", routeHeader.vehicleNumber)
            .putString("header_route_number", routeHeader.routeNumber)
            .putString("route_city", routeCity)
            .putString("addresses", addressJson.toString())
            .putString("located_stops", stopJson.toString())
            .putString("captured_uris", photoJson.toString())
            .apply()
    }
    LaunchedEffect(initialAction) {
        if (shouldResetPhotoRouteSession(initialAction)) {
            plannerPrefs.edit().clear().apply()
            rawText = ""
            routeHeader = PhotoRouteHeader()
            addresses = emptyList()
            located = emptyList()
            capturedUris = emptyList()
            routeCity = "Калуга"
        }
    }

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
                val tableRows = PhotoRouteTableParser.parse(rawText, routeCity)
                val rowsWithoutAddress = tableRows.count { it.address.isBlank() }
                addresses = if (tableRows.isNotEmpty()) tableRows.mapNotNull { it.address.takeIf(String::isNotBlank) }
                    else PhotoRouteTextParser.extractNumberedRouteAddressRows(rawText, routeCity)
                if (addresses.isEmpty()) {
                    status = "Текст распознан, но адреса не выделены. Проверьте распознанный текст."
                } else {
                    val addressGroups = groupPhotoRouteAddresses(addresses)
                    status = "Распознано фотографий ${uris.size}. Объединяю ${addresses.size} записей в ${addressGroups.size} адресных остановок…"
                    val (resolved, failed) = geocodePhotoStops(context, addressGroups.map { it.first() }, routeCity)
                    located = resolved.map { stop ->
                        val group = addressGroups.firstOrNull {
                            PhotoRouteTextParser.canonicalAddressKey(it.first()) == PhotoRouteTextParser.canonicalAddressKey(stop.address)
                        }.orEmpty()
                        val deliveries = group.mapIndexed { occurrence, address ->
                            val tableDelivery = tableRows.filter {
                                PhotoRouteTextParser.canonicalAddressKey(it.address) ==
                                    PhotoRouteTextParser.canonicalAddressKey(address)
                            }.getOrNull(occurrence)
                            if (tableDelivery != null) PhotoStopMetadata(
                                storeName = tableDelivery.organization,
                                ownerName = tableDelivery.organization,
                                phone = tableDelivery.phone,
                                comment = tableDelivery.comment,
                                customerCode = tableDelivery.customerCode,
                                pointNumber = tableDelivery.pointNumber,
                                contactPerson = tableDelivery.contactPerson,
                                cashNote = tableDelivery.cashCollection,
                                packageCount = tableDelivery.packageCount,
                                placeCount = tableDelivery.placeCount,
                                invoiceNumber = tableDelivery.invoiceNumber,
                                rawColumns = tableDelivery.rawCells
                            ) else PhotoRouteTextParser.extractMetadataForOccurrence(rawText, address, occurrence)
                        }
                        stop.copy(
                            metadata = deliveries.firstOrNull() ?: PhotoStopMetadata(),
                            additionalDeliveries = deliveries.drop(1)
                        )
                    }
                    val addressReview = if (rowsWithoutAddress > 0) {
                        " Не распознаны адреса в строках: $rowsWithoutAddress. Они сохранены в исходном тексте для проверки."
                    } else ""
                    status = when {
                        located.isEmpty() -> "Адресов: ${addresses.size}, но координаты не определились. Проверьте интернет и адреса.$addressReview"
                        failed.isNotEmpty() -> "Подготовлено ${located.size} остановок из ${addresses.size} записей. Не найдены координаты: ${failed.joinToString("; ")}.$addressReview"
                        else -> "Готово: ${located.size} уникальных остановок из ${addresses.size} записей доставки.$addressReview"
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
        if (uris.isNotEmpty()) scope.launch {
            runCatching { uris.map { copyPhotoIntoRouteStorage(it) } }
                .onSuccess { saved ->
                    capturedUris = capturedUris + saved
                    status = "Фото сохранены в памяти DigiDrop: ${capturedUris.size}. Их можно распознать позже."
                }
                .onFailure { error -> status = "Не удалось сохранить фото: ${error.message}" }
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
            status = "Фотография получена. Сохраняю копию…"
            runCatching { copyPhotoIntoRouteStorage(uri) }
                .onSuccess { saved ->
                    capturedUris = (capturedUris + saved).distinctBy(Uri::toString)
                    status = "Фото сохранено локально. Распознаю только новый снимок…"
                    // A newly captured page must not trigger OCR again for every
                    // page already added to this route session.
                    processPhotoUris(listOf(saved), onFinished = onInitialImageConsumed)
                }
                .onFailure { error ->
                    status = "Не удалось сохранить фото: ${error.message}"
                    onInitialImageConsumed()
                }
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
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
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
        PhotoPlannerTile("Выбрать фотографии из галереи", "Можно отметить сразу несколько снимков маршрутного листа", enabled = !busy) {
            galleryPhotosLauncher.launch("image/*")
        }
        if (capturedUris.isNotEmpty()) {
            PhotoPlannerTile("Распознать все фотографии", "В очереди снимков: ${capturedUris.size}", enabled = !busy, primary = true) {
                processPhotoUris(capturedUris) {
                    capturedUris = emptyList()
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ФОТО В ОЧЕРЕДИ", color = palette.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    capturedUris.forEachIndexed { index, uri ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}. ${uri.lastPathSegment?.substringAfterLast('/') ?: "Снимок"}",
                                modifier = Modifier.weight(1f),
                                color = palette.onSurface,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    capturedUris = capturedUris.filterIndexed { photoIndex, _ -> photoIndex != index }
                                }
                            ) { Text("Удалить") }
                        }
                    }
                }
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
                val rows = PhotoRouteTableParser.parse(rawText, routeCity)
                addresses = if (rows.isNotEmpty()) rows.map { it.address }
                    else PhotoRouteTextParser.extractNumberedRouteAddressRows(rawText, routeCity)
                located = emptyList()
                routeOrigin = null
                status = "Найдено адресов: ${addresses.size}."
            }
        }
        OutlinedTextField(routeCity, { routeCity = it }, label = { Text("Город маршрута") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)

        if (rowsNeedingAddressReview.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.outline)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("СТРОКИ БЕЗ РАСПОЗНАННОГО АДРЕСА: ${rowsNeedingAddressReview.size}",
                        color = palette.error, style = MaterialTheme.typography.titleSmall)
                    Text("Они не попадут в навигацию, пока адрес не будет исправлен. Исходные данные сохранены.",
                        color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    rowsNeedingAddressReview.forEachIndexed { index, row ->
                        Text(
                            listOf(row.pointNumber, row.customerCode, row.organization, row.rawCells.joinToString(" | "))
                                .filter(String::isNotBlank).distinct().joinToString(" • "),
                            color = palette.onSurface,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (index != rowsNeedingAddressReview.lastIndex) {
                            HorizontalDivider(color = palette.outline.copy(alpha = .5f))
                        }
                    }
                }
            }
        }

        if (located.isEmpty()) {
            Text("АДРЕСА ДЛЯ МАРШРУТА: ${addresses.size}", color = palette.onSurface, style = MaterialTheme.typography.titleMedium)
            addresses.forEachIndexed { index, address ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
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
                            val addressGroups = groupPhotoRouteAddresses(addresses)
                            val (resolved, failed) = geocodePhotoStops(context, addressGroups.map { it.first() }, routeCity)
                            located = resolved.map { stop ->
                                val group = addressGroups.firstOrNull {
                                    PhotoRouteTextParser.canonicalAddressKey(it.first()) == PhotoRouteTextParser.canonicalAddressKey(stop.address)
                                }.orEmpty()
                                val deliveries = group.mapIndexed { occurrence, address ->
                                    PhotoRouteTextParser.extractMetadataForOccurrence(rawText, address, occurrence)
                                }
                                stop.copy(
                                    metadata = deliveries.firstOrNull() ?: PhotoStopMetadata(),
                                    additionalDeliveries = deliveries.drop(1)
                                )
                            }
                            status = if (failed.isEmpty()) "Координаты найдены для ${located.size} уникальных остановок из ${addresses.size} записей."
                                else "Координаты найдены для ${located.size} остановок. Не найдены: ${failed.joinToString("; ")}."
                        } finally { busy = false }
                    }
                }
            }
        }

        if (located.isNotEmpty()) {
            Text("УНИКАЛЬНЫХ ОСТАНОВОК: ${located.size}", color = palette.primary,
                style = MaterialTheme.typography.titleMedium)
            Text("Записей доставки внутри остановок: ${located.sumOf { it.deliveries.size }}", color = palette.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            Text("Совпадающие адреса объединены. Все доставки и комментарии сохранены внутри остановок.",
                color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            PhotoPlannerTile("Показать список точек", "Дальше все действия будут на одном экране маршрута",
                enabled = !busy, primary = true) {
                val items = located.flatMapIndexed { index, stop ->
                    stop.deliveries.mapIndexed { deliveryIndex, delivery ->
                        RouteItem(
                            barcode = "",
                            storeName = delivery.storeName.ifBlank { "Точка ${index + 1}" },
                            address = stop.address,
                            itemName = buildString {
                                append(delivery.ownerName.ifBlank { "Доставка по маршрутному листу" })
                                if (delivery.customerCode.isNotBlank()) append(" • код ${delivery.customerCode}")
                                if (delivery.contactPerson.isNotBlank()) append(" • контакт: ${delivery.contactPerson}")
                                if (delivery.invoiceNumber.isNotBlank()) append(" • накладная ${delivery.invoiceNumber}")
                                if (delivery.placeCount > 0) append(" • мест: ${delivery.placeCount}")
                            },
                            totalBoxes = delivery.packageCount,
                            phone = delivery.phone,
                            note = listOf(delivery.comment, delivery.cashNote).filter(String::isNotBlank).joinToString(" • ")
                                .ifBlank { "Импортировано из маршрутного листа" },
                            latitude = stop.latitude,
                            longitude = stop.longitude,
                            ownerName = delivery.ownerName,
                            workingHours = delivery.workingHours,
                            cashNote = delivery.cashNote,
                            extraComment = delivery.comment
                        )
                    }
                }
                onApplyRoute(items, routeHeader)
            }
        }
    }
}
