package com.example.qrhelper

/**
 * Several independent parsers can read the same photographed route sheet.
 * Selecting the longest list rewards OCR hallucinations; this selector first
 * rewards complete, structurally valid street/house pairs and only then size.
 */
internal object RouteOcrCandidateSelector {
    fun select(candidates: List<List<RouteItem>>): List<RouteItem> =
        candidates.asSequence()
            .filter { it.isNotEmpty() }
            .maxByOrNull(::qualityScore)
            .orEmpty()

    fun invalidAddressRows(items: List<RouteItem>): List<RouteItem> =
        items.filterNot { PhotoRouteTextParser.isPlausibleRouteAddress(it.address) }

    fun unresolvedRowsFromLargerCandidate(
        candidates: List<List<RouteItem>>,
        selected: List<RouteItem>
    ): List<RouteItem> {
        if (selected.isEmpty()) return emptyList()
        val selectedValid = selected.count { PhotoRouteTextParser.isPlausibleRouteAddress(it.address) }
        val minimumExtraRows = maxOf(3, selected.size / 2)
        val largerCandidate = candidates
            .filter { it.size >= selected.size + minimumExtraRows }
            .filter { candidate ->
                candidate.count { PhotoRouteTextParser.isPlausibleRouteAddress(it.address) } <= selectedValid + 1
            }
            .maxByOrNull { it.size }
            ?: return emptyList()
        return invalidAddressRows(largerCandidate)
    }

    fun sanitizeStoreNames(items: List<RouteItem>): List<RouteItem> =
        items.map { item ->
            val cleaned = PhotoRouteTextParser.cleanDetectedStoreName(item.storeName)
            item.copy(storeName = cleaned.ifBlank { "Проверить название" })
        }

    private fun qualityScore(items: List<RouteItem>): Int {
        val validAddresses = items.count { PhotoRouteTextParser.isPlausibleRouteAddress(it.address) }
        val invalidAddresses = items.size - validAddresses
        val readableNames = items.count {
            PhotoRouteTextParser.cleanDetectedStoreName(it.storeName).isNotBlank()
        }
        val coverage = validAddresses.toDouble() / items.size
        // Prefer useful row coverage, but don't silently replace a mostly complete
        // result with a tiny fragment just because one row needs address review.
        // The common import gate rejects uncertain rows before saving the route.
        return validAddresses * 1_000 -
            invalidAddresses * 1_000 +
            (coverage * 1_000).toInt() +
            readableNames * 10 +
            items.size.coerceAtMost(10_000)
    }
}

/**
 * Prefer the actual shop name printed after the address (for example, "Бристоль")
 * over the supplier/legal-company name in the "Клиент" column.
 */
internal fun PhotoRouteDeliveryRow.detectStoreName(
    recognizedText: String = rawCells.joinToString(" "),
    occurrence: Int = 0
): String {
    val fromAddressRow = PhotoRouteTextParser.extractMetadataForOccurrence(
        recognizedText,
        address,
        occurrence
    ).storeName
    return PhotoRouteTextParser.cleanDetectedStoreName(fromAddressRow)
        .ifBlank { PhotoRouteTextParser.cleanDetectedStoreName(organization) }
}

/** Route-photo import deliberately creates only the two requested fields: name and address. */
internal fun PhotoRouteDeliveryRow.toRouteItem(index: Int): RouteItem {
    return RouteItem(
        barcode = "",
        storeName = detectStoreName(),
        address = address,
        itemName = "",
        totalBoxes = 0,
        phone = "",
        note = ""
    )
}
