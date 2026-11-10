package com.example.qrhelper

/**
 * Imports dispatch/route lists, not cargo invoices.
 * A printed row is a delivery entry. Product barcodes and package quantities
 * are optional and are never required to create a route stop.
 */
internal object RouteSheetParser {
    private val headerAliases = mapOf(
        "number" to setOf("№", "номер", "номерстроки", "порядковыйномер", "n"),
        "code" to setOf("код", "кодклиента", "кодмагазина", "кодполучателя", "кодточки"),
        "client" to setOf("клиент", "магазин", "получатель", "наименованиеполучателя", "торговаяточка", "объект", "названиеточки"),
        "address" to setOf("адрес", "адресдоставки", "адресточки", "местодоставки", "адресполучателя"),
        "comment" to setOf("комментарий", "примечание", "дополнительно", "условиядоставки", "информация"),
        "hours" to setOf("времяработы", "режимработы", "графикработы", "часыработы"),
        "timeSlot" to setOf("времядоставки", "интервалдоставки", "окнодоставки", "временноокно", "таймслот")
    )

    private val addressHint = Regex(
        """(?iu)(?:\b(?:ул\.?|улица|проспект|пр\.?|переулок|пер\.?|шоссе|наб\.?|набережная|пл\.?|площадь|б-р|бул\.?|д\.?|дом)\b|\bг\.?\s*[А-ЯЁ])"""
    )

    fun parse(text: String): List<RouteItem> {
        val lines = text.removePrefix("\uFEFF").lineSequence()
            .map { line ->
                val withoutCarriageReturn = line.trimEnd('\r')
                // Preserve leading/trailing tabs: they represent empty table cells
                // on wrapped continuation rows.
                if (withoutCarriageReturn.contains('\t')) {
                    withoutCarriageReturn.trim(' ', '\uFEFF')
                } else {
                    withoutCarriageReturn.trim()
                }
            }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val delimiter = detectDelimiter(lines)
        val rows = lines.map { splitRow(it, delimiter) }
        val headerIndex = rows.indexOfFirst(::isRouteHeader)
        if (headerIndex >= 0) {
            val header = rows[headerIndex].map(::normalize)
            val columns = headerAliases.mapValues { (_, aliases) ->
                header.indexOfFirst { it in aliases }
            }
            return parseDelimitedRows(rows.drop(headerIndex + 1), columns)
        }

        val tsvRows = rows.filter { it.size >= 4 }
        val parsed = parseHeaderlessRows(tsvRows)
        if (parsed.isNotEmpty()) return parsed
        return parseOcrLines(lines)
    }

    fun parseOcrText(text: String, city: String = "Калуга"): List<RouteItem> {
        // A route sheet may contain addresses in its header/footer. Only numbered
        // customer rows are valid deliveries; never turn a stray header address into a stop.
        val addresses = PhotoRouteTextParser.extractNumberedRouteAddressRows(text, city)
        val occurrences = mutableMapOf<String, Int>()
        return addresses.mapIndexed { index, address ->
            val key = PhotoRouteTextParser.canonicalAddressKey(address)
            val occurrence = occurrences.getOrDefault(key, 0)
            occurrences[key] = occurrence + 1
            val metadata = PhotoRouteTextParser.extractMetadataForOccurrence(text, address, occurrence)
            RouteItem(
                barcode = "",
                storeName = PhotoRouteTextParser.cleanDetectedStoreName(metadata.storeName),
                address = address,
                itemName = "Доставка по маршрутному списку",
                totalBoxes = 1,
                phone = metadata.phone,
                note = metadata.comment.ifBlank { "Доставка по маршрутному списку" },
                ownerName = metadata.ownerName,
                workingHours = metadata.workingHours,
                timeSlot = metadata.timeSlot,
                cashNote = metadata.cashNote,
                extraComment = metadata.comment
            )
        }
    }

    fun parseSheets(sheets: List<List<List<String>>>): List<RouteItem> {
        if (sheets.isEmpty()) return emptyList()
        // Keep all sheets together so a repeated header or a continuation page
        // does not reset the column layout or discard rows.
        val combined = sheets.flatMap { it }
        val text = combined.joinToString("\n") { row ->
            row.joinToString("\t") { it.replace("\t", " ").replace("\n", " ") }
        }
        return parse(text)
    }

    private fun detectDelimiter(lines: List<String>): Char {
        if (lines.any { it.contains('\t') }) return '\t'
        if (lines.any { it.contains(';') }) return ';'
        if (lines.any { it.contains('|') }) return '|'
        if (lines.any { Regex("\\s{2,}").containsMatchIn(it) }) return ' '
        if (lines.any { it.contains(',') }) return ','
        return '\t'
    }

    private fun splitRow(line: String, delimiter: Char): List<String> {
        if (delimiter == ' ' && line.contains(Regex("\\s{2,}"))) {
            return line.split(Regex("\\s{2,}")).map(String::trim)
        }
        if (delimiter == '\t' && !line.contains('\t') && line.contains(Regex("\\s{2,}"))) {
            return line.split(Regex("\\s{2,}")).map(String::trim)
        }
        if (delimiter == ',') return splitQuotedLine(line, delimiter)
        if (delimiter == ';') return splitQuotedLine(line, delimiter)
        return line.split(delimiter).map { it.trim().trim('"') }
    }

    private fun splitQuotedLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> {
                    result += cell.toString().trim()
                    cell.setLength(0)
                }
                else -> cell.append(char)
            }
            index++
        }
        result += cell.toString().trim()
        return result
    }

    private fun isRouteHeader(row: List<String>): Boolean {
        val normalized = row.map(::normalize)
        val hasAddress = normalized.any { it in headerAliases.getValue("address") }
        val hasClient = normalized.any { it in headerAliases.getValue("client") }
        return hasAddress && hasClient
    }

    private fun parseDelimitedRows(
        rows: List<List<String>>,
        columns: Map<String, Int>
    ): List<RouteItem> {
        fun value(row: List<String>, key: String): String =
            row.getOrNull(columns[key] ?: -1).orEmpty().trim()

        val result = mutableListOf<RouteItem>()
        var last: RouteItem? = null
        for (row in rows) {
            if (isRouteHeader(row)) continue
            val number = value(row, "number")
            val code = value(row, "code")
            val client = value(row, "client")
            val address = value(row, "address")
            val comment = value(row, "comment")
            val workingHours = value(row, "hours")
            val timeSlot = value(row, "timeSlot")
            if (number.isBlank() && client.isBlank() && address.isBlank()) {
                if (last != null && (comment.isNotBlank() || row.any(String::isNotBlank))) {
                    val previous = result.removeAt(result.lastIndex)
                    val addition = row.filter(String::isNotBlank).joinToString(" ")
                    last = previous.copy(note = listOf(previous.note, addition).filter(String::isNotBlank).joinToString(" "))
                    result += last!!
                }
                continue
            }
            // A blank delivery number means this is a wrapped continuation row,
            // not a new stop. Preserve address fragments and metadata on the prior
            // delivery instead of manufacturing a phantom point.
            if ((columns["number"] ?: -1) >= 0 && number.isBlank() && last != null) {
                val previous = result.lastOrNull() ?: continue
                fun appendDistinct(base: String, addition: String): String {
                    val value = addition.trim()
                    if (value.isBlank() || base.contains(value, ignoreCase = true)) return base
                    return listOf(base, value).filter(String::isNotBlank).joinToString(" ")
                }
                val continuation = listOf(code, client, comment, workingHours, timeSlot)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(" • ")
                val updated = previous.copy(
                    address = appendDistinct(previous.address, address),
                    note = appendDistinct(previous.note, continuation),
                    extraComment = appendDistinct(previous.extraComment, continuation),
                    workingHours = appendDistinct(previous.workingHours, workingHours),
                    timeSlot = appendDistinct(previous.timeSlot, timeSlot)
                )
                result[result.lastIndex] = updated
                last = updated
                continue
            }
            if (address.isBlank()) {
                val previous = result.lastOrNull() ?: continue
                val continuation = listOf(code, client, comment)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(" • ")
                if (continuation.isNotBlank()) {
                    val updated = previous.copy(
                        note = listOf(previous.note, continuation).filter(String::isNotBlank).distinct().joinToString(" • "),
                        extraComment = listOf(previous.extraComment, continuation).filter(String::isNotBlank).distinct().joinToString(" • ")
                    )
                    result[result.lastIndex] = updated
                    last = updated
                }
                continue
            }
            if (number.isNotBlank() && number.filter(Char::isDigit).isBlank()) continue
            val item = routeItem(result.size + 1, code, client, address, comment).copy(
                workingHours = workingHours,
                timeSlot = timeSlot
            )
            result += item
            last = item
        }
        return result
    }

    private fun parseHeaderlessRows(rows: List<List<String>>): List<RouteItem> {
        val result = mutableListOf<RouteItem>()
        for (row in rows) {
            val number = row.getOrNull(0).orEmpty().filter(Char::isDigit)
            val code = row.getOrNull(1).orEmpty().trim()
            val client = row.getOrNull(2).orEmpty().trim()
            val address = row.getOrNull(3).orEmpty().trim()
            val comment = row.getOrNull(4).orEmpty().trim()
            if (number.isBlank() || number.length > 3 || code.isBlank() ||
                client.isBlank() || address.isBlank()) continue
            result += routeItem(result.size + 1, code, client, address, comment)
        }
        return result
    }

    private fun parseOcrLines(lines: List<String>): List<RouteItem> {
        val result = mutableListOf<RouteItem>()
        val rowStart = Regex("""^\s*(\d{1,3})\s+(\d{2,8})\s+(.+)$""")
        for (line in lines) {
            val match = rowStart.find(line) ?: continue
            val tail = match.groupValues[3]
            val addressMatch = addressHint.find(tail) ?: continue
            val client = tail.substring(0, addressMatch.range.first).trim(' ', '\t', '-', '|')
            val addressAndComment = tail.substring(addressMatch.range.first).trim()
            val commentBoundary = Regex("""(?iu)\s{2,}(?=(?:акция|забрать|доставить|региональное|безнал|налич|комментар))""")
                .find(addressAndComment)
            val address = if (commentBoundary != null) addressAndComment.substring(0, commentBoundary.range.first).trim()
                else addressAndComment
            val comment = commentBoundary?.let { addressAndComment.substring(it.range.first).trim() }.orEmpty()
            if (client.isBlank() || address.isBlank()) continue
            result += routeItem(result.size + 1, match.groupValues[2], client, address, comment)
        }
        return result
    }

    private fun routeItem(index: Int, code: String, client: String, address: String, comment: String) =
        RouteItem(
            barcode = "",
            storeName = client.ifBlank { "Точка $index" },
            address = address,
            itemName = if (code.isBlank()) "Доставка по маршрутному листу"
                else "Доставка по маршрутному листу • код $code",
            totalBoxes = 1,
            phone = "",
            note = comment.ifBlank { "Доставка по маршрутному листу" },
            extraComment = comment
        )

    private fun normalize(value: String): String =
        value.trim().lowercase().replace("ё", "е")
            // Keep the № symbol: it is a real route-table header alias.
            .replace(Regex("[\\s_.\\-]+"), "")
}
