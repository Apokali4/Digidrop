package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RouteEditorLogicTest {
    private fun item(
        store: String,
        address: String,
        barcode: String,
        name: String,
        scanned: Int = 0
    ) = RouteItem(
        barcode = barcode,
        storeName = store,
        address = address,
        itemName = name,
        totalBoxes = 5,
        phone = "+70000000000",
        scannedBoxes = scanned
    )

    @Test fun groupsRowsByTrimmedStoreAndAddressWithoutLosingCargoRows() {
        val rows = listOf(
            item(" Магазин ", "Адрес 1", "a", "Молоко"),
            item("Магазин", "Адрес 1 ", "b", "Хлеб"),
            item("Магазин Б", "Адрес 2", "c", "Вода")
        )
        val groups = groupRouteItems(rows)
        assertEquals(2, groups.size)
        assertEquals(listOf("a", "b"), groups.first().map { it.barcode })
        assertEquals(listOf("c"), groups.last().map { it.barcode })
    }

    @Test fun movesWholePointGroupAndPreservesRowsInsideEachPoint() {
        val rows = listOf(
            item("A", "1", "a1", "Товар 1"),
            item("A", "1", "a2", "Товар 2"),
            item("B", "2", "b1", "Товар 3"),
            item("C", "3", "c1", "Товар 4")
        )
        val moved = moveRoutePointGroup(rows, 0, 2)
        assertEquals(listOf("B", "C", "A", "A"), moved.map { it.storeName })
        assertEquals(listOf("a1", "a2"), moved.takeLast(2).map { it.barcode })
        assertEquals(rows, moveRoutePointGroup(rows, -1, 2))
    }

    @Test fun updatesPointMetadataAcrossCargoRowsWithoutChangingDeliveryProgress() {
        val rows = listOf(
            item("Старый магазин", "Старый адрес", "a", "Молоко", scanned = 2),
            item("Старый магазин", "Старый адрес", "b", "Хлеб", scanned = 1),
            item("Другой", "Адрес 2", "c", "Вода")
        )
        val updated = updateRoutePointDetails(
            rows,
            "Старый магазин",
            "Старый адрес",
            RoutePointDetails(
                storeName = "Новый магазин",
                address = "Новый адрес",
                phone = " +7999 ",
                ownerName = " ИП Иванов ",
                workingHours = "09:00-18:00",
                cashNote = "Забрать документы",
                note = "Въезд со двора",
                extraComment = "Позвонить заранее",
                latitude = 54.5,
                longitude = 36.2
            )
        )
        assertEquals(listOf("Новый магазин", "Новый магазин", "Другой"), updated.map { it.storeName })
        assertEquals(listOf(2, 1, 0), updated.map { it.scannedBoxes })
        assertEquals(listOf("a", "b", "c"), updated.map { it.barcode })
        assertEquals("+7999", updated.first().phone)
        assertEquals("ИП Иванов", updated.lastOrNull { it.barcode == "b" }?.ownerName)
        assertEquals(54.5, updated.first().latitude!!, 0.0001)
    }

    @Test fun removesOnlyTheSelectedPointAndAllItsCargoRows() {
        val rows = listOf(
            item("A", "1", "a1", "Товар 1"),
            item("A", "1", "a2", "Товар 2"),
            item("B", "2", "b1", "Товар 3")
        )
        assertEquals(listOf("b1"), removeRoutePointGroup(rows, "A", "1").map { it.barcode })
    }
}
