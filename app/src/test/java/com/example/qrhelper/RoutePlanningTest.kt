package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutePlanningTest {
    @Test
    fun rebuildKeepsScanCompletedPointsAheadOfRemainingStops() {
        val completed = RouteItem(
            "done", "Сдано по сканам", "A", "Товар", 1, "",
            latitude = 0.0, longitude = 0.0, scannedBoxes = 1
        )
        val farther = RouteItem(
            "far", "Дальняя точка", "B", "Товар", 1, "",
            latitude = 0.0, longitude = 2.0
        )
        val nearer = RouteItem(
            "near", "Ближняя точка", "C", "Товар", 1, "",
            latitude = 0.0, longitude = 1.0
        )

        val result = rebuildRemainingRoute(listOf(completed, farther, nearer))

        assertEquals(
            listOf("Сдано по сканам", "Ближняя точка", "Дальняя точка"),
            result.map { it.storeName }
        )
    }

    @Test
    fun nextPendingPointSkipsPointsCompletedByScans() {
        val completed = RouteItem("done", "Сдано", "A", "Товар", 2, "", scannedBoxes = 2)
        val pending = RouteItem("next", "Следующая", "B", "Товар", 2, "")

        assertEquals("Следующая", nextPendingPoint(listOf(completed, pending))?.first()?.storeName)
    }

    @Test
    fun nextPendingPointReturnsNullWhenEveryPointIsComplete() {
        val completed = RouteItem("done", "Сдано", "A", "Товар", 2, "", scannedBoxes = 2)

        assertNull(nextPendingPoint(listOf(completed)))
    }

    @Test
    fun identifiesPackageAssignedToAnotherDeliveryPointWithoutBlockingIt() {
        val current = RouteItem("a", "Магнит", "Калуга, ул. Ленина, 1", "Товар", 2, "")
        val same = current.copy(barcode = "b")
        val other = current.copy(storeName = "Бристоль", address = "Калуга, ул. Кирова, 2")
        assertEquals(false, isDifferentDeliveryPoint(same, current))
        assertEquals(true, isDifferentDeliveryPoint(other, current))
        assertEquals(false, isDifferentDeliveryPoint(other, null))
    }

    @Test
    fun manualOrderIsPreservedForNavigation() {
        val first = RouteItem("1", "Дальняя", "A", "Товар", 1, "", latitude = 0.0, longitude = 2.0)
        val second = RouteItem("2", "Ближняя", "B", "Товар", 1, "", latitude = 0.0, longitude = 1.0)
        assertEquals(listOf("Дальняя", "Ближняя"),
            routeForNavigation(listOf(first, second), manualOrder = true).map { it.storeName })
    }

    @Test
    fun untouchedRouteUsesOptimizerBeforeNavigation() {
        val completed = RouteItem("done", "Сдано", "A", "Товар", 1, "", latitude = 0.0, longitude = 0.0, scannedBoxes = 1)
        val farther = RouteItem("far", "Дальняя", "B", "Товар", 1, "", latitude = 0.0, longitude = 2.0)
        val nearer = RouteItem("near", "Ближняя", "C", "Товар", 1, "", latitude = 0.0, longitude = 1.0)
        assertEquals(listOf("Сдано", "Ближняя", "Дальняя"),
            routeForNavigation(listOf(completed, farther, nearer), manualOrder = false).map { it.storeName })
    }

}


class NavigationSegmentProgressTest {
    @Test fun advancesToNextSegmentAndWrapsAfterLast() {
        assertEquals(1, nextNavigationSegmentIndex(0, 3))
        assertEquals(2, nextNavigationSegmentIndex(1, 3))
        assertEquals(0, nextNavigationSegmentIndex(2, 3))
    }

    @Test fun emptyRouteHasNoNextSegment() {
        assertEquals(0, nextNavigationSegmentIndex(0, 0))
    }
}
