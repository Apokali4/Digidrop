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

    /**
     * Compare the header-based table parse with the independent free-text parse.
     * A partially recognized table must not hide stops that the text parser found.
     */
    fun selectPhotoRouteAddresses(
        tableRows: List<PhotoRouteDeliveryRow>,
        recognizedText: String,
        city: String
    ): List<String> {
        val tableCandidate = tableRows.mapIndexed { index, row ->
            row.toRouteItem(index + 1)
        }
        val freeTextAddresses = PhotoRouteTextParser.extractNumberedRouteAddressRows(
            recognizedText,
            city
        )
        val occurrences = mutableMapOf<String, Int>()
        val freeTextCandidate = freeTextAddresses.mapIndexed { index, address ->
            val key = PhotoRouteTextParser.canonicalAddressKey(address)
            val occurrence = occurrences.getOrDefault(key, 0)
            occurrences[key] = occurrence + 1
            RouteItem(
                barcode = "",
                storeName = PhotoRouteTextParser.extractMetadataForOccurrence(
                    recognizedText,
                    address,
                    occurrence
                ).storeName,
                address = address,
                itemName = "",
                totalBoxes = 0,
                phone = ""
            )
        }
        return select(listOf(tableCandidate, freeTextCandidate))
            .map { it.address }
    }

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
        // A single uncertain row must not make us discard an otherwise complete
        // route. Penalize multiple uncertain rows progressively: this preserves a
        // 27th delivery for manual address review, while rejecting a much larger
        // OCR candidate polluted by many fragments or false street matches.
        val uncertaintyPenalty = invalidAddresses * (invalidAddresses - 1).coerceAtLeast(0) * 10
        return validAddresses * 1_000 -
            uncertaintyPenalty +
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
    // The "Клиент/Организация" column often contains the supplier or legal
    // counterparty, not the shop at the delivery address. Never present it as
    // the store name. If OCR did not capture a name beside the address, leave
    // it for manual review instead of confidently showing the wrong company.
    return PhotoRouteTextParser.cleanDetectedStoreName(storeName)
        .ifBlank { PhotoRouteTextParser.cleanDetectedStoreName(fromAddressRow) }
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
