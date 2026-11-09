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

    @Test fun reportStatusPrioritizesDiscrepancy() {
        val item = RouteItem("a", "Store", "Address", "Item", 5, "", isDelivered = true, scannedBoxes = 5, discrepancyQty = 1)
        assertEquals("Расхождение", deliveryItemStatusLabel(item))
    }

    @Test fun reportStatusShowsDeliveredWhenManuallyCompleted() {
        val item = RouteItem("a", "Store", "Address", "Item", 0, "", isDelivered = true)
        assertEquals("Доставлено", deliveryItemStatusLabel(item))
    }

    @Test fun reportStatusShowsInProgressForPartialScans() {
        val item = RouteItem("a", "Store", "Address", "Item", 5, "", scannedBoxes = 2)
        assertEquals("В работе", deliveryItemStatusLabel(item))
    }

    @Test fun reportStatusShowsPendingWithoutProgress() {
        val item = RouteItem("a", "Store", "Address", "Item", 5, "")
        assertEquals("Ожидает", deliveryItemStatusLabel(item))
    }

    @Test fun emptyPointIsPending() {
        assertEquals(DeliveryPointStatus.PENDING, deliveryPointStatus(emptyList()))
    }

    @Test fun remainingBoxesNeverGoesBelowZero() {
        val item = RouteItem(
            barcode = "123",
            storeName = "Store",
            address = "Address",
            itemName = "Box",
            totalBoxes = 5,
            phone = "",
            scannedBoxes = 4,
            discrepancyQty = 2
        )
        assertEquals(0, remainingBoxes(item))
    }
}
