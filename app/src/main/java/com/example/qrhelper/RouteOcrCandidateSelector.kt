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

    fun sanitizeStoreNames(items: List<RouteItem>): List<RouteItem> =
        items.map { item ->
            item.copy(storeName = PhotoRouteTextParser.cleanDetectedStoreName(item.storeName))
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

/** Convert the column-aware photo parser's delivery record without losing cargo fields. */
internal fun PhotoRouteDeliveryRow.toRouteItem(index: Int): RouteItem {
    val sourceText = rawCells.joinToString(" ")
    val metadataStore = PhotoRouteTextParser.extractMetadata(sourceText, address).storeName
    val store = PhotoRouteTextParser.cleanDetectedStoreName(organization)
        .ifBlank { PhotoRouteTextParser.cleanDetectedStoreName(metadataStore) }
    val quantity = placeCount.takeIf { it > 0 } ?: packageCount.takeIf { it > 0 } ?: 1
    val codeDescription = customerCode.trim().takeIf(String::isNotBlank)
        ?.let { " • код $it" }.orEmpty()
    val extra = buildList {
        invoiceNumber.trim().takeIf(String::isNotBlank)?.let { add("Накладная: $it") }
        extraColumns.filter(String::isNotBlank).forEach(::add)
    }.distinct().joinToString("; ")
    return RouteItem(
        barcode = "",
        storeName = store,
        address = address,
        itemName = "Доставка по маршрутному списку$codeDescription",
        totalBoxes = quantity,
        phone = phone,
        note = comment.ifBlank { "Доставка по маршрутному списку" },
        ownerName = contactPerson,
        workingHours = workingHours,
        timeSlot = timeSlot,
        cashNote = cashCollection,
        extraComment = extra
    )
}
