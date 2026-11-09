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


    @Test fun doesNotCompleteMultiRowPointWhenOneRowIsStillMissingEvenIfAnotherIsOverScanned() {
        val items = listOf(
            RouteItem("a", "Магазин", "Адрес", "Товар 1", 2, "", scannedBoxes = 4),
            RouteItem("b", "Магазин", "Адрес", "Товар 2", 3, "", scannedBoxes = 1)
        )
        assertEquals(DeliveryStatus.IN_PROGRESS, RouteImportMapper.map(items).single().status)
    }

    @Test fun doesNotTrustManualDeliveredFlagWhenCargoIsStillMissing() {
        val item = RouteItem(
            "a", "Магазин", "Адрес", "Товар", 5, "",
            isDelivered = true, scannedBoxes = 2
        )
        assertEquals(DeliveryStatus.IN_PROGRESS, RouteImportMapper.map(listOf(item)).single().status)
    }

    @Test fun doesNotAutoCompleteZeroQuantityRowWithoutManualConfirmation() {
        val item = RouteItem("a", "Магазин", "Адрес", "Услуга", 0, "")
        assertEquals(DeliveryStatus.PENDING, RouteImportMapper.map(listOf(item)).single().status)
    }

    @Test fun completesOnlyWhenEveryCargoRowIsAccounted() {
        val items = listOf(
            RouteItem("a", "Магазин", "Адрес", "Товар 1", 2, "", scannedBoxes = 2),
            RouteItem("b", "Магазин", "Адрес", "Товар 2", 3, "", scannedBoxes = 3)
        )
        assertEquals(DeliveryStatus.COMPLETED, RouteImportMapper.map(items).single().status)
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


    @Test fun keepsDifferentShopNamesAtTheSameAddressAsSeparateStops() {
        val items = listOf(
            RouteItem("a", "Красное и Белое", "Калуга, ул. Кирова, 1", "Пиво", 2, ""),
            RouteItem("b", "Продукты", "Калуга, ул. Кирова, 1", "Хлеб", 3, "")
        )
        val points = RouteImportMapper.map(items)
        assertEquals(2, points.size)
        assertEquals(listOf(2, 3), points.map { it.cargo.sumOf { cargo -> cargo.expectedQty } })
    }

    @Test fun normalizesMinorPunctuationDifferencesInTheSameStoreName() {
        val items = listOf(
            RouteItem("a", "Красное и Белое", "Калуга, ул. Кирова, 1", "Пиво", 2, ""),
            RouteItem("b", "Красное-Белое", "Калуга, ул. Кирова, 1", "Снэки", 3, "")
        )
        assertEquals(1, RouteImportMapper.map(items).size)
        assertEquals(5, RouteImportMapper.map(items).single().cargo.sumOf { it.expectedQty })
    }

    @Test fun usesShopBrandFromAddressWhenStoreColumnContainsOnlySupplierLegalName() {
        val items = listOf(
            RouteItem("a", "Балтика ПК ООО", "г. Калуга, ул. Врубовая, д. 2, Бристоль", "Пиво", 2, ""),
            RouteItem("b", "Альфа Рязань ООО", "Калуга, улица Врубовая, 2, БРИСТОЛЬ", "Вода", 3, "")
        )
        assertEquals(1, RouteImportMapper.map(items).size)
    }

    @Test fun fallsBackToLegalEntityWhenStoreNameIsMissing() {
        val items = listOf(
            RouteItem("a", "Точка без названия", "Калуга, ул. Кирова, 1", "Хлеб", 2, "89000000001", ownerName = "ИП Иванов"),
            RouteItem("b", "Точка без названия", "Калуга, ул. Кирова, 1", "Молоко", 3, "89000000002", ownerName = "ИП Петров")
        )
        assertEquals(2, RouteImportMapper.map(items).size)
    }

    @Test fun doesNotMergeUnidentifiedRecipientsOnlyBecauseAddressMatches() {
        val items = listOf(
            RouteItem("barcode-1", "Точка без названия", "Калуга, ул. Кирова, 1", "Хлеб", 2, ""),
            RouteItem("barcode-2", "Точка без названия", "Калуга, ул. Кирова, 1", "Молоко", 3, "")
        )
        assertEquals(2, RouteImportMapper.map(items).size)
    }

    @Test fun rejectsRouteReplacementWhenAnyImportedRowWouldBeSilentlyDropped() {
        val valid = RouteItem("a", "Магазин", "Адрес", "Товар", 2, "")
        val invalid = valid.copy(address = "   ")
        try {
            requireCompleteRouteImport(listOf(valid, invalid), RouteImportMapper.map(listOf(valid, invalid)))
            throw AssertionError("Expected incomplete route import to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("В накладной есть строки без адреса точки доставки", expected.message)
        }
    }

    @Test fun rejectsEmptyRouteReplacement() {
        try {
            requireCompleteRouteImport(emptyList(), emptyList())
            throw AssertionError("Expected empty route import to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Нельзя заменить активный рейс пустой накладной", expected.message)
        }
    }

    @Test fun acceptsRouteWhenEveryImportedRowHasAnAddress() {
        val items = listOf(
            RouteItem("a", "Магазин", "Адрес 1", "Товар 1", 2, ""),
            RouteItem("b", "Магазин", "Адрес 2", "Товар 2", 3, "")
        )
        requireCompleteRouteImport(items, RouteImportMapper.map(items))
    }

}
