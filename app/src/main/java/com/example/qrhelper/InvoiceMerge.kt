package com.example.qrhelper

/**
 * Combines rows from multiple invoices while retaining one cargo line per product at each point.
 * Rows at different stores or with different product names remain separate.
 */
internal fun mergeInvoiceRows(existing: List<RouteItem>, incoming: List<RouteItem>): List<RouteItem> =
    (existing + incoming)
        .groupBy { listOf(it.storeName.trim(), it.address.trim(), it.barcode.trim(), it.itemName.trim()) }
        .values
        .map { group ->
            val first = group.first()
            first.copy(
                totalBoxes = group.sumOf { it.totalBoxes.coerceAtLeast(0) },
                scannedBoxes = group.sumOf { it.scannedBoxes.coerceAtLeast(0) },
                isDelivered = group.all { it.isDelivered },
                discrepancyQty = group.sumOf { it.discrepancyQty.coerceAtLeast(0) },
                discrepancyReason = group.mapNotNull { it.discrepancyReason }.lastOrNull(),
                phone = group.firstNotNullOfOrNull { it.phone.takeIf(String::isNotBlank) }.orEmpty(),
                note = group.map { it.note.trim() }.filter(String::isNotBlank).distinct().joinToString(" • "),
                latitude = group.firstNotNullOfOrNull { it.latitude },
                longitude = group.firstNotNullOfOrNull { it.longitude },
                ownerName = group.firstNotNullOfOrNull { it.ownerName.takeIf(String::isNotBlank) }.orEmpty(),
                workingHours = group.firstNotNullOfOrNull { it.workingHours.takeIf(String::isNotBlank) }.orEmpty(),
                cashNote = group.firstNotNullOfOrNull { it.cashNote.takeIf(String::isNotBlank) }.orEmpty(),
                extraComment = group.map { it.extraComment.trim() }.filter(String::isNotBlank).distinct().joinToString(" • "),
                photoDocUri = group.firstNotNullOfOrNull { it.photoDocUri.takeIf(String::isNotBlank) }.orEmpty(),
                signaturePath = group.firstNotNullOfOrNull { it.signaturePath.takeIf(String::isNotBlank) }.orEmpty()
            )
        }
