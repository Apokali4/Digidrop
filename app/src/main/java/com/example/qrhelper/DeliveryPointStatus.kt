package com.example.qrhelper

import com.example.qrhelper.domain.DeliveryPointStatus
import com.example.qrhelper.domain.RouteProgress

internal fun deliveryPointStatus(items: List<RouteItem>): DeliveryPointStatus {
    if (items.isEmpty()) return DeliveryPointStatus.PENDING
    if (items.any { it.discrepancyQty > 0 }) return DeliveryPointStatus.PROBLEM
    if (items.all { it.isDelivered }) return DeliveryPointStatus.DELIVERED
    val expected = items.sumOf { it.totalBoxes.coerceAtLeast(0) }
    val scanned = items.sumOf { it.scannedBoxes.coerceAtLeast(0) }
    return RouteProgress.status(expected, scanned, 0)
}

internal fun remainingBoxes(item: RouteItem): Int =
    (item.totalBoxes - item.scannedBoxes - item.discrepancyQty).coerceAtLeast(0)
