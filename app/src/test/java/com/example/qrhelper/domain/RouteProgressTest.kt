package com.example.qrhelper.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteProgressTest {
    private val points = listOf(
        DeliveryPointSnapshot("a", 0, DeliveryPointStatus.PENDING, 10, 0, 0),
        DeliveryPointSnapshot("b", 1, DeliveryPointStatus.IN_PROGRESS, 8, 3, 0),
        DeliveryPointSnapshot("c", 2, DeliveryPointStatus.DELIVERED, 4, 4, 0),
        DeliveryPointSnapshot("d", 3, DeliveryPointStatus.PROBLEM, 6, 4, 2)
    )

    @Test fun activeFilterHidesDeliveredAndKeepsPendingAndInProgress() {
        assertEquals(listOf("a", "b"), RouteProgress.filter(points, PointFilter.ACTIVE).map { it.id })
    }

    @Test fun problemFilterShowsOnlyPointsWithDiscrepancies() {
        assertEquals(listOf("d"), RouteProgress.filter(points, PointFilter.PROBLEMS).map { it.id })
    }

    @Test fun deliveredFilterShowsOnlyCompletedPoints() {
        assertEquals(listOf("c"), RouteProgress.filter(points, PointFilter.DELIVERED).map { it.id })
    }

    @Test fun reorderingMovesOnePointWithoutLosingOtherPointsOrTheirState() {
        val moved = RouteProgress.move(points, fromIndex = 0, toIndex = 2)
        assertEquals(listOf("b", "c", "a", "d"), moved.map { it.id })
        assertEquals(DeliveryPointStatus.PROBLEM, moved.last().status)
        assertEquals(points, RouteProgress.move(points, fromIndex = -1, toIndex = 2))
    }

    @Test fun statusIsDerivedFromScannedAndDiscrepancyCounts() {
        assertEquals(DeliveryPointStatus.PENDING, RouteProgress.status(5, 0, 0))
        assertEquals(DeliveryPointStatus.IN_PROGRESS, RouteProgress.status(5, 2, 0))
        assertEquals(DeliveryPointStatus.DELIVERED, RouteProgress.status(5, 5, 0))
        assertEquals(DeliveryPointStatus.PROBLEM, RouteProgress.status(5, 3, 2))
    }

    @Test fun summaryDoesNotCountDiscrepancyAsScannedDelivery() {
        assertEquals(
            RouteSummary(totalPoints = 4, deliveredPoints = 1, problemPoints = 1, expectedUnits = 28, scannedUnits = 11, discrepancyUnits = 2),
            RouteProgress.summary(points)
        )
    }
    @Test fun allFilterReturnsPointsInRouteOrder() {
        val shuffled = listOf(points[3], points[1], points[0], points[2])
        assertEquals(listOf("a", "b", "c", "d"), RouteProgress.filter(shuffled, PointFilter.ALL).map { it.id })
    }

    @Test fun statusClampsInvalidCountsInsteadOfProducingImpossibleProgress() {
        assertEquals(DeliveryPointStatus.PENDING, RouteProgress.status(-3, -1, -1))
        assertEquals(DeliveryPointStatus.DELIVERED, RouteProgress.status(5, 9, 0))
        assertEquals(DeliveryPointStatus.PROBLEM, RouteProgress.status(5, 9, 2))
    }

}
