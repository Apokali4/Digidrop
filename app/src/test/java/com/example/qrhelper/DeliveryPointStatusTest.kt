package com.example.qrhelper

import com.example.qrhelper.domain.DeliveryPointStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryPointStatusTest {
    @Test fun marksPointDeliveredWhenAllExpectedBoxesWereScanned() {
        val items = listOf(
            RouteItem("a", "Store", "Address", "Item A", 2, "", scannedBoxes = 2),
            RouteItem("b", "Store", "Address", "Item B", 3, "", scannedBoxes = 3)
        )
        assertEquals(DeliveryPointStatus.DELIVERED, deliveryPointStatus(items))
    }

    @Test fun discrepancyTakesPriorityOverScannedOrManualDeliveredState() {
        val items = listOf(
            RouteItem("a", "Store", "Address", "Item A", 2, "", isDelivered = true, scannedBoxes = 2),
            RouteItem("b", "Store", "Address", "Item B", 3, "", scannedBoxes = 2, discrepancyQty = 1, discrepancyReason = "НЕДОВОЗ")
        )
        assertEquals(DeliveryPointStatus.PROBLEM, deliveryPointStatus(items))
    }

    @Test fun keepsManualCompletionForZeroQuantityRows() {
        val items = listOf(RouteItem("a", "Store", "Address", "Service", 0, "", isDelivered = true))
        assertEquals(DeliveryPointStatus.DELIVERED, deliveryPointStatus(items))
    }

    @Test fun emptyPointIsPending() {
        assertEquals(DeliveryPointStatus.PENDING, deliveryPointStatus(emptyList()))
    }
}
