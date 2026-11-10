package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteNumberingAuditTest {
    @Test
    fun reportsGapsBetweenRecognizedDeliveryNumbersWithoutInventingRows() {
        val rows = listOf("1", "2", "4", "7").map { number ->
            PhotoRouteDeliveryRow(pointNumber = number)
        }

        assertEquals(listOf(3, 5, 6), RouteNumberingAudit.missingPointNumbers(rows))
    }

    @Test
    fun ignoresBlankAndNonNumericPointLabels() {
        val rows = listOf(
            PhotoRouteDeliveryRow(pointNumber = "1"),
            PhotoRouteDeliveryRow(pointNumber = ""),
            PhotoRouteDeliveryRow(pointNumber = "Итого"),
            PhotoRouteDeliveryRow(pointNumber = "3")
        )

        assertEquals(listOf(2), RouteNumberingAudit.missingPointNumbers(rows))
    }

    @Test
    fun doesNotTreatContinuationPageStartingAtTwentyFourAsIncomplete() {
        val rows = listOf("24", "25", "26").map { PhotoRouteDeliveryRow(pointNumber = it) }

        assertEquals(emptyList<Int>(), RouteNumberingAudit.missingPointNumbers(rows))
    }
}
