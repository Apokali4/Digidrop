package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class PhotoRouteNavigationUrlsTest {
    private val stops = listOf(
        PhotoRouteCoordinate(55.7500, 37.6100),
        PhotoRouteCoordinate(55.7600, 37.6200),
        PhotoRouteCoordinate(55.7700, 37.6300),
        PhotoRouteCoordinate(55.7800, 37.6400)
    )

    @Test fun yandexUrlContainsEveryStopInOptimizedOrder() {
        val url = PhotoRouteNavigationUrls.yandex(stops)
        val decoded = URLDecoder.decode(url, StandardCharsets.UTF_8.name())
        assertTrue(decoded.contains("rtext=55.75,37.61~55.76,37.62~55.77,37.63~55.78,37.64"))
        assertTrue(decoded.contains("rtt=auto"))
    }

    @Test fun googleUrlUsesFirstAndLastAsEndpointsAndAllMiddleStopsAsWaypoints() {
        val url = PhotoRouteNavigationUrls.google(stops)
        val decoded = URLDecoder.decode(url, StandardCharsets.UTF_8.name())
        assertTrue(decoded.contains("origin=55.75,37.61"))
        assertTrue(decoded.contains("destination=55.78,37.64"))
        assertTrue(decoded.contains("waypoints=55.76,37.62|55.77,37.63"))
        assertTrue(decoded.contains("travelmode=driving"))
    }

    @Test fun bothProvidersRejectRoutesWithFewerThanTwoStops() {
        val error = runCatching { PhotoRouteNavigationUrls.yandex(stops.take(1)) }.exceptionOrNull()
        assertEquals("Для навигации нужны минимум две точки", error?.message)
    }
    @Test fun yandexSplitsLongRouteIntoTwentyStopSegmentsWithSharedBoundary() {
        val manyStops = (0..32).map { PhotoRouteCoordinate(55.0 + it / 1000.0, 37.0 + it / 1000.0) }
        val segments = PhotoRouteNavigationUrls.yandexSegments(manyStops)
        assertEquals(listOf(20, 14), segments.map { it.size })
        assertEquals(segments[0].last(), segments[1].first())
        assertEquals(manyStops.first(), segments.first().first())
        assertEquals(manyStops.last(), segments.last().last())
        segments.forEach { assertTrue(it.size <= PhotoRouteNavigationUrls.YANDEX_MAX_STOPS) }
    }

    @Test fun yandexKeepsShortRoutesAsSingleSegment() {
        assertEquals(1, PhotoRouteNavigationUrls.yandexSegments(stops).size)
    }

    @Test fun geocodingQueryAddsSelectedCityToAmbiguousStreetAddress() {
        assertEquals("Советская, 2, Калуга, Россия", PhotoRouteGeocodingQuery.build("Советская, 2", "Калуга"))
    }

    @Test fun geocodingQueryLeavesCityOptional() {
        assertEquals("Советская, 2", PhotoRouteGeocodingQuery.build("Советская, 2", " "))
    }

    @Test fun routeOptimizerChoosesFarthestDeliveryAsFinalStop() {
        val depot = PhotoRouteCoordinate(54.51, 36.25)
        val points = listOf(
            PhotoRouteCoordinate(54.52, 36.26),
            PhotoRouteCoordinate(54.70, 36.40),
            PhotoRouteCoordinate(54.55, 36.30)
        )
        assertEquals(1, PhotoRouteOrdering.farthestStopIndex(depot, points))
    }

}
