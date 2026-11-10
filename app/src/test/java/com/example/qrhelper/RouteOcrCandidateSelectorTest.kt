package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteOcrCandidateSelectorTest {
    private fun routeItem(index: Int, address: String, store: String = "Бристоль") = RouteItem(
        barcode = "",
        storeName = store,
        address = address,
        itemName = "Доставка по маршрутному списку",
        totalBoxes = 1,
        phone = ""
    )

    @Test
    fun cleanCandidateBeatsLongerCandidateWithOcrAddressGarbage() {
        val clean = (1..80).map { index ->
            routeItem(index, "г. Калуга, ул. Кирова, д. $index")
        }
        val noisy = (1..100).map { index ->
            routeItem(index, "г. Калуга, ул. Кирова, д. $index")
        } + (1..21).map { index ->
            routeItem(100 + index, "г. Калуга, ул. , д. $index", "и эоноезаз) ООО внесба ефчиу \"9791")
        }

        val selected = RouteOcrCandidateSelector.select(listOf(noisy, clean))

        assertEquals(80, selected.size)
        assertTrue(RouteOcrCandidateSelector.invalidAddressRows(selected).isEmpty())
    }

    @Test
    fun candidateSelectionDoesNotCollapseDuplicateDeliveriesAtOneAddress() {
        val duplicateRows = listOf(
            routeItem(1, "Калуга, ул. Болдина, д. 9", "Красное и Белое"),
            routeItem(2, "г. Калуга, улица Болдина 9", "Бристоль")
        )

        val selected = RouteOcrCandidateSelector.select(listOf(duplicateRows))

        assertEquals(2, selected.size)
        assertEquals(
            PhotoRouteTextParser.canonicalAddressKey(selected[0].address),
            PhotoRouteTextParser.canonicalAddressKey(selected[1].address)
        )
    }

    @Test
    fun addressQualityRejectsFragmentsAndAcceptsReadableStreetHousePairs() {
        assertTrue(PhotoRouteTextParser.isPlausibleRouteAddress("Калуга, ул. Постовалова, 24"))
        assertTrue(PhotoRouteTextParser.isPlausibleRouteAddress("Калуга, б-р Солнечный, 4"))
        assertTrue(PhotoRouteTextParser.isPlausibleRouteAddress("Калуга, ул. ло Щедрина, 71"))
        assertTrue(!PhotoRouteTextParser.isPlausibleRouteAddress("ульская, 7475"))
        assertTrue(!PhotoRouteTextParser.isPlausibleRouteAddress("Калуга, ул. , 186"))
    }

    @Test
    fun noisyStoreTextIsNotPresentedAsARecognizedShop() {
        assertEquals("ООО Ромашка", PhotoRouteTextParser.cleanDetectedStoreName("ООО Ромашка"))
        assertEquals("Красное и Белое", PhotoRouteTextParser.cleanDetectedStoreName("Красное и Белое"))
        assertEquals("", PhotoRouteTextParser.cleanDetectedStoreName("и эоноезаз) ООО внесба ефчиу \"9791"))
    }

    @Test
    fun geocoderTriesConservativeStreetPrefixCorrectionWithoutRelaxingHouseMatch() {
        val original = "Калуга, ул. ло Щедрина, 71"
        assertEquals(
            listOf(original, "Калуга, ул. Щедрина, 71"),
            PhotoRouteGeocodingQuery.addressVariants(original)
        )
        assertTrue(
            PhotoRouteGeocodingQuery.matchesRequestedAddress(
                requestedAddress = original,
                candidateStreet = "улица Щедрина",
                candidateHouse = "71"
            )
        )
        assertTrue(
            !PhotoRouteGeocodingQuery.matchesRequestedAddress(
                requestedAddress = original,
                candidateStreet = "улица Щедрина",
                candidateHouse = "17"
            )
        )
    }

    @Test
    fun navigationCanOpenOneConfirmedCoordinateWhenOtherAddressesAreUnresolved() {
        val point = PhotoRouteCoordinate(54.51, 36.25)
        assertEquals(listOf(listOf(point)), PhotoRouteNavigationUrls.yandexSegments(listOf(point)))
    }

    @Test
    fun largerCandidateWithOneUncertainRowIsNotSilentlyReducedToTinyPartialRoute() {
        val partial = (1..11).map { index ->
            routeItem(index, "Калуга, ул. Победы, $index")
        } + listOf(routeItem(12, "Калуга, ул. , 186"))
        val tiny = listOf(routeItem(1, "Калуга, ул. Ленина, 10"))

        val selected = RouteOcrCandidateSelector.select(listOf(tiny, partial))

        assertEquals(partial.size, selected.size)
        assertEquals(1, RouteOcrCandidateSelector.invalidAddressRows(selected).size)
    }

    @Test
    fun keepsOneAdditionalDeliveryRowWhenOnlyItsAddressNeedsReview() {
        val cleanCandidate = (1..26).map { index ->
            routeItem(index, "Калуга, ул. Кирова, $index")
        }
        val completeCandidate = cleanCandidate + routeItem(
            27,
            "Калуга, ул. , 27",
            "Проверить название"
        )

        val selected = RouteOcrCandidateSelector.select(listOf(cleanCandidate, completeCandidate))

        assertEquals(27, selected.size)
        assertEquals(1, RouteOcrCandidateSelector.invalidAddressRows(selected).size)
        assertEquals("Проверить название", selected.last().storeName)
    }

    @Test
    fun smallCleanCandidateDoesNotHideThirteenUnresolvedRowsInLargerParse() {
        val small = (1..6).map { routeItem(it, "Калуга, ул. Кирова, $it") }
        val larger = small + (7..19).map {
            routeItem(it, "Калуга, ул. , $it", "Проверить адрес $it")
        }
        val selected = RouteOcrCandidateSelector.select(listOf(small, larger))

        assertEquals(6, selected.size)
        assertEquals(13, RouteOcrCandidateSelector.unresolvedRowsFromLargerCandidate(
            listOf(small, larger),
            selected
        ).size)
    }

    @Test
    fun canonicalGroupingRepairsKnownShortOcrPrefixVariant() {
        assertEquals(
            PhotoRouteTextParser.canonicalAddressKey("Калуга, ул. Щедрина, 71"),
            PhotoRouteTextParser.canonicalAddressKey("Калуга, ул. ло Щедрина, 71")
        )
    }

    @Test
    fun genericLocationLabelsAndStreetTextAreNotPresentedAsStoreNames() {
        assertEquals("", PhotoRouteTextParser.cleanDetectedStoreName("Точка без названия"))
        assertEquals("", PhotoRouteTextParser.cleanDetectedStoreName("пом.105"))
        assertEquals("", PhotoRouteTextParser.cleanDetectedStoreName("ул. Победы, 24"))
        assertEquals("Красное и Белое", PhotoRouteTextParser.cleanDetectedStoreName("Красное и Белое"))
    }

    @Test
    fun barcodeAndDeliveryInstructionsAreNotShownAsStoreNames() {
        assertEquals("", PhotoRouteTextParser.cleanDetectedStoreName("234636125 доставка до 11"))
        assertEquals("Кафе кция тез", PhotoRouteTextParser.cleanDetectedStoreName("Кафе кция тез"))
        assertEquals("БРИСТО", PhotoRouteTextParser.cleanDetectedStoreName("БРИСТО"))
    }

    @Test
    fun sanitizedMissingStoreNamesAreMarkedForReview() {
        val cleaned = RouteOcrCandidateSelector.sanitizeStoreNames(
            listOf(routeItem(1, "Калуга, ул. Кирова, 8", "пом.105"))
        )
        assertEquals("Проверить название", cleaned.single().storeName)
    }

    @Test
    fun routePhotoUsesStoreBrandFromAddressInsteadOfSupplierCompanyName() {
        val text = listOf(
            "№\tКод\tКлиент\tАдрес доставки",
            "1\t1708\tБалтика ПК ООО\tг. Калуга, ул. Врубовая, д. 2, Бристоль",
            "2\t1646\tАльфа Рязань ООО\tг. Калуга, ул. Болдина, д. 9, Красное и Белое"
        ).joinToString("\n")

        val items = PhotoRouteTableParser.parse(text, "Калуга").mapIndexed { index, row ->
            row.toRouteItem(index + 1)
        }

        assertEquals(listOf("Бристоль", "Красное и Белое"), items.map { it.storeName })
        assertEquals(
            listOf("г. Калуга, ул. Врубовая, д. 2", "г. Калуга, ул. Болдина, д. 9"),
            items.map { it.address }
        )
        assertTrue(items.all {
            it.barcode.isBlank() && it.itemName.isBlank() && it.totalBoxes == 0 &&
                it.phone.isBlank() && it.note.isBlank() && it.workingHours.isBlank()
        })
    }


    @Test
    fun unstructuredOcrCandidateRecoversStopsMissingFromPartialTableParse() {
        val partialTable = listOf(
            routeItem(1, "г. Калуга, ул. Кирова, д. 8")
        )
        val unstructured = listOf(
            routeItem(1, "г. Калуга, ул. Кирова, д. 8"),
            routeItem(2, "г. Калуга, ул. Ленина, д. 14"),
            routeItem(3, "г. Калуга, ул. Победы, д. 2")
        )

        val selected = RouteOcrCandidateSelector.select(listOf(partialTable, unstructured))

        assertEquals(3, selected.size)
        assertEquals(
            listOf("г. Калуга, ул. Кирова, д. 8", "г. Калуга, ул. Ленина, д. 14", "г. Калуга, ул. Победы, д. 2"),
            selected.map { it.address }
        )
    }


    @Test
    fun photoAddressSelectionUsesFreeTextWhenTableParserMissesMostStops() {
        val tableRows = listOf(
            PhotoRouteDeliveryRow(
                pointNumber = "1",
                organization = "Магазин Север",
                address = "г. Калуга, ул. Кирова, д. 8"
            )
        )
        val recognizedText = """
            1 Магазин Север г. Калуга, ул. Кирова, д. 8
            2 Пятёрочка г. Калуга, ул. Ленина, д. 14
            3 Магазин Юг г. Калуга, ул. Победы, д. 2
        """.trimIndent()

        val selected = RouteOcrCandidateSelector.selectPhotoRouteAddresses(
            tableRows,
            recognizedText,
            "Калуга"
        )

        assertEquals(3, selected.size)
        assertEquals("г. Калуга, ул. Победы, д. 2", selected.last())
    }

}