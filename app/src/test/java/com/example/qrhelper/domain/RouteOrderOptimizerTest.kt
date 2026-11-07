package com.example.qrhelper.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteOrderOptimizerTest {
    @Test fun preservesCompletedPrefixAndStartsFromLastCompletedPoint() {
        val stops = listOf(
            RouteStop("done", 0.0, 0.0, completed = true),
            RouteStop("far", 10.0, 0.0, completed = false),
            RouteStop("near", 1.0, 0.0, completed = false),
            RouteStop("middle", 2.0, 0.0, completed = false)
        )
        assertEquals(
            listOf("done", "near", "middle", "far"),
            RouteOrderOptimizer.optimize(stops).map { it.id }
        )
    }

    @Test fun startsAtFirstPendingPointWhenNothingIsCompleted() {
        val stops = listOf(
            RouteStop("first", 0.0, 0.0, completed = false),
            RouteStop("far", 10.0, 0.0, completed = false),
            RouteStop("near", 1.0, 0.0, completed = false)
        )
        assertEquals(
            listOf("first", "near", "far"),
            RouteOrderOptimizer.optimize(stops).map { it.id }
        )
    }

    @Test fun leavesOriginalOrderWhenAnyPendingPointHasNoCoordinates() {
        val stops = listOf(
            RouteStop("a", 0.0, 0.0, completed = false),
            RouteStop("b", null, null, completed = false),
            RouteStop("c", 1.0, 0.0, completed = false)
        )
        assertEquals(stops, RouteOrderOptimizer.optimize(stops))
    }

    @Test fun doesNotMoveCompletedStopsBehindPendingStops() {
        val stops = listOf(
            RouteStop("done-a", 0.0, 0.0, completed = true),
            RouteStop("done-b", 1.0, 0.0, completed = true),
            RouteStop("pending", 2.0, 0.0, completed = false)
        )
        assertEquals(stops, RouteOrderOptimizer.optimize(stops))
    }
}
