package com.example.qrhelper.domain

enum class DeliveryPointStatus {
    PENDING,
    IN_PROGRESS,
    DELIVERED,
    PROBLEM
}

enum class PointFilter {
    ALL,
    ACTIVE,
    PROBLEMS,
    DELIVERED
}

data class DeliveryPointSnapshot(
    val id: String,
    val sortOrder: Int,
    val status: DeliveryPointStatus,
    val expectedUnits: Int,
    val scannedUnits: Int,
    val discrepancyUnits: Int
)

data class RouteSummary(
    val totalPoints: Int,
    val deliveredPoints: Int,
    val problemPoints: Int,
    val expectedUnits: Int,
    val scannedUnits: Int,
    val discrepancyUnits: Int
)

object RouteProgress {
    fun status(expected: Int, scanned: Int, discrepancy: Int): DeliveryPointStatus {
        val safeExpected = expected.coerceAtLeast(0)
        val safeScanned = scanned.coerceAtLeast(0).coerceAtMost(safeExpected)
        val safeDiscrepancy = discrepancy.coerceAtLeast(0)
        return when {
            safeDiscrepancy > 0 -> DeliveryPointStatus.PROBLEM
            safeExpected > 0 && safeScanned >= safeExpected -> DeliveryPointStatus.DELIVERED
            safeScanned > 0 -> DeliveryPointStatus.IN_PROGRESS
            else -> DeliveryPointStatus.PENDING
        }
    }

    fun filter(
        points: List<DeliveryPointSnapshot>,
        filter: PointFilter
    ): List<DeliveryPointSnapshot> {
        val ordered = points.sortedBy { it.sortOrder }
        return when (filter) {
            PointFilter.ALL -> ordered
            PointFilter.ACTIVE -> ordered.filter {
                it.status == DeliveryPointStatus.PENDING ||
                    it.status == DeliveryPointStatus.IN_PROGRESS
            }
            PointFilter.PROBLEMS -> ordered.filter { it.status == DeliveryPointStatus.PROBLEM }
            PointFilter.DELIVERED -> ordered.filter { it.status == DeliveryPointStatus.DELIVERED }
        }
    }

    fun move(
        points: List<DeliveryPointSnapshot>,
        fromIndex: Int,
        toIndex: Int
    ): List<DeliveryPointSnapshot> {
        if (fromIndex !in points.indices || toIndex !in points.indices || fromIndex == toIndex) {
            return points
        }
        val reordered = points.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        return reordered.mapIndexed { index, point -> point.copy(sortOrder = index) }
    }

    fun summary(points: List<DeliveryPointSnapshot>): RouteSummary = RouteSummary(
        totalPoints = points.size,
        deliveredPoints = points.count { it.status == DeliveryPointStatus.DELIVERED },
        problemPoints = points.count { it.status == DeliveryPointStatus.PROBLEM },
        expectedUnits = points.sumOf { it.expectedUnits.coerceAtLeast(0) },
        scannedUnits = points.sumOf { it.scannedUnits.coerceAtLeast(0) },
        discrepancyUnits = points.sumOf { it.discrepancyUnits.coerceAtLeast(0) }
    )
}
