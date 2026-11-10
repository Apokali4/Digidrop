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
        assertEquals(3, RouteOrderOptimizer.alternatives(stops).size)
    }
    @Test fun usesDirectedRoadDistancesInsteadOfStraightLineWhenProvided() {
        val stops = listOf(
            RouteStop("start", 0.0, 0.0, false),
            RouteStop("nearByAir", 0.0, 0.1, false),
            RouteStop("roadFast", 1.0, 1.0, false)
        )
        val roads = RouteDistanceMatrix(
            mapOf(
                ("start" to "nearByAir") to 9000.0,
                ("start" to "roadFast") to 1000.0,
                ("roadFast" to "nearByAir") to 1000.0,
                ("nearByAir" to "roadFast") to 12000.0
            )
        )

        assertEquals(
            listOf("start", "roadFast", "nearByAir"),
            RouteOrderOptimizer.optimize(stops, roads).map { it.id }
        )
    }

    @Test fun choosesFirstStopByRoadDistanceFromLastCompletedPoint() {
        val stops = listOf(
            RouteStop("done", 0.0, 0.0, true),
            RouteStop("nearByAir", 0.0, 0.01, false),
            RouteStop("roadFast", 1.0, 1.0, false)
        )
        val roads = RouteDistanceMatrix(
            mapOf(
                ("done" to "nearByAir") to 8000.0,
                ("done" to "roadFast") to 1000.0,
                ("roadFast" to "nearByAir") to 1000.0
            )
        )

        assertEquals(
            listOf("done", "roadFast", "nearByAir"),
            RouteOrderOptimizer.optimize(stops, roads).map { it.id }
        )
    }

}
