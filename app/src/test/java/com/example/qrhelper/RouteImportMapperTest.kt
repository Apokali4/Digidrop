package com.example.qrhelper

import com.example.qrhelper.data.DeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteImportMapperTest {
    @Test fun marksPointCompletedWhenAllExpectedBoxesWereScannedWithoutManualConfirmation() {
        val item = RouteItem("a", "Магазин", "Адрес", "Товар", 3, "", scannedBoxes = 3)
        assertEquals(DeliveryStatus.COMPLETED, RouteImportMapper.map(listOf(item)).single().status)
    }

    @Test fun discrepancyOverridesManualCompletionInStoredPointStatus() {
        val item = RouteItem(
            "a", "Магазин", "Адрес", "Товар", 3, "",
            isDelivered = true, scannedBoxes = 2, discrepancyQty = 1, discrepancyReason = "НЕДОВОЗ"
        )
        assertEquals(DeliveryStatus.HAS_DISCREPANCY, RouteImportMapper.map(listOf(item)).single().status)
    }

    @Test fun groupsMultipleInvoiceRowsAtTheSameStoreAndAddress() {
        val items = listOf(
            RouteItem("4601234567890", "Магазин", "Калуга, ул. Кирова, 1", "Молоко", 4, "8800"),
            RouteItem("4601234567891", "Магазин", "Калуга, ул. Кирова, 1", "Хлеб", 2, "8800")
        )
        val points = RouteImportMapper.map(items)
        assertEquals(1, points.size)
        assertEquals(2, points.single().cargo.size)
        assertEquals(6, points.single().cargo.sumOf { it.expectedQty })
    }

    @Test fun groupsDifferentInvoiceSuppliersIntoOnePhysicalStorePoint() {
        val items = listOf(
            RouteItem("1708", "Балтика ПК ООО", "г. Калуга, ул. Врубовая, д. 2, Бристоль", "Пиво", 4, ""),
            RouteItem("1646", "Альфа Рязань ООО", "Калуга, улица Врубовая, 2", "Вода", 3, ""),
            RouteItem("2835", "Кока-Кола", "Калуга, ул. Врубовая, дом 2", "Напитки", 5, "")
        )
        val points = RouteImportMapper.map(items)
        assertEquals(1, points.size)
        assertEquals(3, points.single().cargo.size)
        assertEquals(12, points.single().cargo.sumOf { it.expectedQty })
    }

    @Test fun derivesPointStatusFromImportedProgress() {
        val items = listOf(
            RouteItem("a", "Ожидает", "Адрес 1", "Товар", 3, ""),
            RouteItem("b", "В работе", "Адрес 2", "Товар", 3, "", scannedBoxes = 1),
            RouteItem("c", "Сдано", "Адрес 3", "Товар", 3, "", isDelivered = true)
        )
        assertEquals(
            listOf(DeliveryStatus.PENDING, DeliveryStatus.IN_PROGRESS, DeliveryStatus.COMPLETED),
            RouteImportMapper.map(items).map { it.status }
        )
    }
    @Test fun marksMultiInvoicePointInProgressUntilEveryLineIsDelivered() {
        val items = listOf(
            RouteItem("a", "Магазин", "Адрес", "Товар 1", 2, "", isDelivered = true),
            RouteItem("b", "Магазин", "Адрес", "Товар 2", 3, "")
        )
        assertEquals(DeliveryStatus.IN_PROGRESS, RouteImportMapper.map(items).single().status)
    }
    @Test fun preservesCoordinatesAndDeliveryInstructionsForRoomRestore() {
        val item = RouteItem(
            barcode = "4601234567890",
            storeName = "Склад",
            address = "Калуга, ул. Ленина, 1",
            itemName = "Коробка",
            totalBoxes = 3,
            phone = "+70000000000",
            note = "Въезд со двора",
            latitude = 54.51,
            longitude = 36.26,
            ownerName = "Иванов",
            workingHours = "09:00-18:00",
            cashNote = "Забрать документы",
            extraComment = "Позвонить заранее"
        )
        val point = RouteImportMapper.map(listOf(item)).single()
        assertEquals(54.51, point.latitude!!, 0.0001)
        assertEquals(36.26, point.longitude!!, 0.0001)
        val cargo = point.cargo.single()
        assertEquals("Въезд со двора", cargo.note)
        assertEquals("Иванов", cargo.ownerName)
        assertEquals("09:00-18:00", cargo.workingHours)
        assertEquals("Забрать документы", cargo.cashNote)
        assertEquals("Позвонить заранее", cargo.extraComment)
    }

    @Test fun discrepancyReasonAndQuantityProduceProblemStatus() {
        val item = RouteItem(
            barcode = "4601234567890",
            storeName = "Магазин",
            address = "Адрес",
            itemName = "Товар",
            totalBoxes = 5,
            phone = "",
            scannedBoxes = 3,
            discrepancyQty = 2,
            discrepancyReason = "НЕДОВОЗ"
        )
        val point = RouteImportMapper.map(listOf(item)).single()
        assertEquals(DeliveryStatus.HAS_DISCREPANCY, point.status)
        assertEquals(2, point.cargo.single().discrepancyQty)
        assertEquals("НЕДОВОЗ", point.cargo.single().discrepancyReason?.name)
    }

}
