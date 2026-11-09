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
        "comment" to setOf("комментарий", "примечание", "дополнительно", "условиядоставки", "информация")
    )

    private val addressHint = Regex(
        """(?iu)(?:\b(?:ул\.?|улица|проспект|пр\.?|переулок|пер\.?|шоссе|наб\.?|набережная|пл\.?|площадь|б-р|бул\.?|д\.?|дом)\b|\bг\.?\s*[А-ЯЁ])"""
    )

    fun parse(text: String): List<RouteItem> {
        val lines = text.removePrefix("\uFEFF").lineSequence()
            .map { it.trimEnd('\r').trim() }
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
        val addresses = PhotoRouteTextParser.extractRouteAddressRows(text, city)
        val occurrences = mutableMapOf<String, Int>()
        return addresses.mapIndexed { index, address ->
            val key = PhotoRouteTextParser.canonicalAddressKey(address)
            val occurrence = occurrences.getOrDefault(key, 0)
            occurrences[key] = occurrence + 1
            val metadata = PhotoRouteTextParser.extractMetadataForOccurrence(text, address, occurrence)
            RouteItem(
                barcode = "",
                storeName = metadata.storeName.ifBlank { "Точка ${index + 1}" },
                address = address,
                itemName = "Доставка по маршрутному списку",
                totalBoxes = 1,
                phone = metadata.phone,
                note = metadata.comment.ifBlank { "Доставка по маршрутному списку" },
                ownerName = metadata.ownerName,
                workingHours = metadata.workingHours,
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
        if (delimiter == ',') return splitCsvLine(line, delimiter)
        if (delimiter == ';') return splitCsvLine(line, delimiter)
        return line.split(delimiter).map { it.trim().trim('"') }
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
            if (number.isBlank() && client.isBlank() && address.isBlank()) {
                if (last != null && (comment.isNotBlank() || row.any(String::isNotBlank))) {
                    val previous = result.removeAt(result.lastIndex)
                    val addition = row.filter(String::isNotBlank).joinToString(" ")
                    last = previous.copy(note = listOf(previous.note, addition).filter(String::isNotBlank).joinToString(" "))
                    result += last!!
                }
                continue
            }
            if (address.isBlank()) continue
            if (number.isNotBlank() && number.filter(Char::isDigit).isBlank()) continue
            val item = routeItem(result.size + 1, code, client, address, comment)
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
            .replace(Regex("[\\s_№.\\-]+"), "")
}
