package com.example.qrhelper

import com.example.qrhelper.domain.DeliveryPointStatus
import com.example.qrhelper.domain.RouteProgress

internal fun deliveryPointStatus(items: List<RouteItem>): DeliveryPointStatus {
    if (items.isEmpty()) return DeliveryPointStatus.PENDING
    if (items.any { it.discrepancyQty.coerceAtLeast(0) > 0 }) return DeliveryPointStatus.PROBLEM
    if (items.all { it.isDelivered }) return DeliveryPointStatus.DELIVERED
    val expected = items.sumOf { it.totalBoxes.coerceAtLeast(0) }
    val scanned = items.sumOf { it.scannedBoxes.coerceAtLeast(0) }
    return RouteProgress.status(expected, scanned, 0)
}

internal fun remainingBoxes(item: RouteItem): Int =
    (item.totalBoxes.coerceAtLeast(0) - item.scannedBoxes.coerceAtLeast(0) - item.discrepancyQty.coerceAtLeast(0)).coerceAtLeast(0)


internal fun deliveryItemStatusLabel(item: RouteItem): String = when {
    item.discrepancyQty.coerceAtLeast(0) > 0 -> "Расхождение"
    item.isDelivered -> "Доставлено"
    item.scannedBoxes.coerceAtLeast(0) > 0 -> "В работе"
    else -> "Ожидает"
}


internal data class RouteReportSummary(
    val pointCount: Int,
    val deliveredPoints: Int,
    val problemPoints: Int,
    val inProgressPoints: Int,
    val pendingPoints: Int,
    val expectedBoxes: Int,
    val scannedBoxes: Int,
    val discrepancyBoxes: Int,
    val remainingBoxes: Int,
    val rowCount: Int
)

internal fun routeReportSummary(items: List<RouteItem>): RouteReportSummary {
    val points = items.groupBy { it.storeName.trim() to it.address.trim() }.values
    val delivered = points.count { deliveryPointStatus(it) == DeliveryPointStatus.DELIVERED }
    val problem = points.count { deliveryPointStatus(it) == DeliveryPointStatus.PROBLEM }
    val inProgress = points.count { deliveryPointStatus(it) == DeliveryPointStatus.IN_PROGRESS }
    val expected = items.sumOf { it.totalBoxes.coerceAtLeast(0) }
    val scanned = items.sumOf { it.scannedBoxes.coerceAtLeast(0) }
    val discrepancies = items.sumOf { it.discrepancyQty.coerceAtLeast(0) }
    return RouteReportSummary(
        pointCount = points.size,
        deliveredPoints = delivered,
        problemPoints = problem,
        inProgressPoints = inProgress,
        pendingPoints = (points.size - delivered - problem - inProgress).coerceAtLeast(0),
        expectedBoxes = expected,
        scannedBoxes = scanned,
        discrepancyBoxes = discrepancies,
        remainingBoxes = (expected - scanned - discrepancies).coerceAtLeast(0),
        rowCount = items.size
    )
}
