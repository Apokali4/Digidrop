package com.example.qrhelper

internal data class InvoiceImportMergeResult(
    val items: List<RouteItem>,
    val isDuplicate: Boolean
)

private fun RouteItem.invoiceIdentity(): String =
    listOf(barcode, storeName, address, itemName, phone)
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

    val previousByIdentity = current.associateBy { it.invoiceIdentity() }
    val merged = incoming.map { fresh ->
        val previous = previousByIdentity[fresh.invoiceIdentity()] ?: return@map fresh
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
