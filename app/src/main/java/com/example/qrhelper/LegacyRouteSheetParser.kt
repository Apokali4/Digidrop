package com.example.qrhelper

/**
 * Parser for the older dispatch route sheet:
 * № | Код | Клиент | Адрес доставки | Комментарий.
 *
 * These documents do not contain a cargo quantity column. Every printed row
 * represents one delivery cargo line, so its default quantity is one.
 */
internal object LegacyRouteSheetParser {
    private val addressHint = Regex(
        """(?iu)(?:г\.?\s*калуга|калуга|ул\.?|улица|проспект|пр\.?|переулок|пер\.?|шоссе|наб\.?|набережная)"""
    )

    fun parse(text: String): List<RouteItem>? {
        val lines = text.removePrefix("\uFEFF").lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return null

        val rows = lines.map(::splitColumns)
        val headerIndex = rows.indexOfFirst { row ->
            val normalized = row.map(::normalize)
            normalized.any { it == "код" } &&
                normalized.any { it == "клиент" } &&
                normalized.any { it == "адресдоставки" || it == "адрес" }
        }

        val header = if (headerIndex >= 0) rows[headerIndex].map(::normalize) else emptyList()
        val numberColumn = if (headerIndex >= 0) header.indexOfFirst { it == "№" || it == "номер" }.let { if (it < 0) 0 else it } else 0
        val codeColumn = if (headerIndex >= 0) header.indexOf("код") else 1
        val clientColumn = if (headerIndex >= 0) header.indexOf("клиент") else 2
        val addressColumn = if (headerIndex >= 0) header.indexOfFirst { it == "адресдоставки" || it == "адрес" } else 3
        val commentColumn = if (headerIndex >= 0) header.indexOf("комментарий") else 4

        val parsed = mutableListOf<RouteItem>()
        val start = if (headerIndex >= 0) headerIndex + 1 else 0
        for (row in rows.drop(start)) {
            val number = row.getOrNull(numberColumn).orEmpty().filter(Char::isDigit)
            val code = row.getOrNull(codeColumn).orEmpty().trim()
            val client = row.getOrNull(clientColumn).orEmpty().trim()
            val address = row.getOrNull(addressColumn).orEmpty().trim()
            val comment = row.getOrNull(commentColumn).orEmpty().trim()

            if (number.isBlank() || number.length > 3) continue
            if (code.length !in 2..8 || !code.all(Char::isDigit)) continue
            if (client.length < 3 || address.length < 8 || !addressHint.containsMatchIn(address)) continue

            parsed += RouteItem(
                barcode = code,
                storeName = client,
                address = address,
                itemName = "Доставка по маршрутному листу",
                totalBoxes = 1,
                phone = "",
                note = comment.ifBlank { "Доставка по маршрутному листу" }
            )
        }
        return parsed.takeIf { it.isNotEmpty() }
    }

    private fun splitColumns(line: String): List<String> =
        if (line.contains('\t')) line.split('\t').map { it.trim().trim('"') }
        else line.split(Regex("\\s{2,}")).map { it.trim().trim('"') }

    private fun normalize(value: String): String =
        value.trim().lowercase().replace("ё", "е")
            .replace(Regex("[\\s_№.-]+"), "")
}
