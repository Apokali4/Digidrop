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
    
    @Test fun generatesUpToFourDistinctAlternativesWithDifferentStartingPoints() {
        val stops = listOf(
            RouteStop("a", 0.0, 0.0, false),
            RouteStop("b", 0.0, 1.0, false),
            RouteStop("c", 1.0, 1.0, false),
            RouteStop("d", 1.0, 0.0, false),
            RouteStop("e", 0.5, 0.5, false)
        )
        val alternatives = RouteOrderOptimizer.alternatives(stops)
        assertEquals(4, alternatives.size)
        assertEquals(listOf("a", "b", "c", "d"), alternatives.map { it.first().id })
        assertEquals(4, alternatives.map { it.map(RouteStop::id) }.distinct().size)
    }

    @Test fun alternativesKeepCompletedStopsAtTheFront() {
        val stops = listOf(
            RouteStop("done", 0.0, 0.0, true),
            RouteStop("a", 0.0, 1.0, false),
            RouteStop("b", 1.0, 1.0, false),
            RouteStop("c", 1.0, 0.0, false)
        )
        assertEquals(
            listOf("done"),
            RouteOrderOptimizer.alternatives(stops).first().take(1).map { it.id }
        )
        assertEquals(4, RouteOrderOptimizer.alternatives(stops).size)
    }
}
