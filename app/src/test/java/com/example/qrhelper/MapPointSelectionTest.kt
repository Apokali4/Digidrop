package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapPointSelectionTest {
    @Test fun mapsOneBasedMarkerNumbersToZeroBasedRouteIndices() {
        assertEquals(0, mapPointIndexFromMarkerNumber(1, 4))
        assertEquals(3, mapPointIndexFromMarkerNumber(4, 4))
    }

    @Test fun rejectsMissingOutOfRangeAndInvalidMarkerNumbers() {
        assertNull(mapPointIndexFromMarkerNumber(null, 4))
        assertNull(mapPointIndexFromMarkerNumber(0, 4))
        assertNull(mapPointIndexFromMarkerNumber(5, 4))
        assertNull(mapPointIndexFromMarkerNumber(1, 0))
    }
}
