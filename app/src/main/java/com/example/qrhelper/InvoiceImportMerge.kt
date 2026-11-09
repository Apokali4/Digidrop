package com.example.qrhelper

internal data class InvoiceImportMergeResult(
    val items: List<RouteItem>,
    val isDuplicate: Boolean
)

private fun RouteItem.invoiceIdentity(): String =
    // Phone is mutable delivery metadata, not cargo identity. A corrected
    // contact number must not detach already scanned cargo or its evidence.
    listOf(barcode, storeName, address, itemName)
        .joinToString("|") { it.trim().lowercase().replace("ё", "е") }

private fun List<RouteItem>.invoiceSignature(): List<String> =
    map { item -> "${item.invoiceIdentity()}|${item.totalBoxes}" }.sorted()

internal fun mergeInvoiceImport(
    current: List<RouteItem>,
    incoming: List<RouteItem>
): InvoiceImportMergeResult {
    if (incoming.isEmpty()) return InvoiceImportMergeResult(current, isDuplicate = false)

    if (current.isNotEmpty() && current.invoiceSignature() == incoming.invoiceSignature()) {
        return InvoiceImportMergeResult(current, isDuplicate = true)
    }

    // A barcode can legitimately occur on multiple invoice rows. Keep each
    // previous row in a queue instead of silently retaining only the last one.
    val previousByIdentity = current.groupBy { it.invoiceIdentity() }
        .mapValues { (_, rows) -> ArrayDeque(rows) }
    val merged = incoming.map { fresh ->
        val previous = previousByIdentity[fresh.invoiceIdentity()]?.removeFirstOrNull()
            ?: return@map fresh
        fresh.copy(
            isDelivered = previous.isDelivered,
            scannedBoxes = previous.scannedBoxes,
            discrepancyQty = previous.discrepancyQty,
            discrepancyReason = previous.discrepancyReason,
            photoDocUri = previous.photoDocUri,
            signaturePath = previous.signaturePath
        )
    }
    return InvoiceImportMergeResult(merged, isDuplicate = false)
}
