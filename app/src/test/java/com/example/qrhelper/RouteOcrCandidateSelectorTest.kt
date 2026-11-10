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
        assertTrue(!PhotoRouteTextParser.isPlausibleRouteAddress("Калуга, ул. ло Щедрина, 71"))
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
}
