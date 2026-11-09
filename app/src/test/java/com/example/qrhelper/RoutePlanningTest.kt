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

}
