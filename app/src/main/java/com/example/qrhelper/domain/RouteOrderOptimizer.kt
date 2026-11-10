package com.example.qrhelper.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteStop(
    val id: String,
    val latitude: Double?,
    val longitude: Double?,
    val completed: Boolean
)

/**
 * Directed road distances in metres. Key order is (origin stop id, destination stop id).
 * Missing or invalid pairs deliberately fall back to geodesic distance.
 */
data class RouteDistanceMatrix(val meters: Map<Pair<String, String>, Double>) {
    fun distance(from: RouteStop, to: RouteStop): Double? =
        meters[from.id to to.id]?.takeIf { it.isFinite() && it >= 0.0 }
}

object RouteOrderOptimizer {
    /** Generates deterministic alternatives while preserving the completed prefix. */
    fun alternatives(stops: List<RouteStop>, limit: Int = 4): List<List<RouteStop>> {
        if (limit <= 0 || stops.size < 3) return emptyList()
        val completed = stops.filter { it.completed }
        val pending = stops.filterNot { it.completed }
        if (pending.size < 2 || pending.any { !it.hasValidCoordinates() }) return emptyList()
        return pending.take(limit).map { start ->
            completed + improveTwoOpt(nearestNeighbor(pending, start), start)
        }.distinctBy { route -> route.map(RouteStop::id) }
    }

    /**
     * Keeps the driver's current route start deterministic: the first pending stop
     * remains the start unless a completed stop immediately precedes the pending route.
     * Reorders only the remaining stops and improves the greedy route with 2-opt.
     */
    fun optimize(stops: List<RouteStop>, roadDistances: RouteDistanceMatrix? = null): List<RouteStop> {
        if (stops.size < 3) return stops
        val completed = stops.filter { it.completed }
        val pending = stops.filterNot { it.completed }
        if (pending.size < 2 || pending.any { !it.hasValidCoordinates() }) return stops
        val lastCompleted = completed.lastOrNull()?.takeIf { it.hasValidCoordinates() }
        val start = lastCompleted?.let { origin ->
            pending.minByOrNull { distanceMeters(origin, it, roadDistances) }
        } ?: pending.first()
        return completed + improveTwoOpt(nearestNeighbor(pending, start, roadDistances), start, roadDistances)
    }

    private fun nearestNeighbor(points: List<RouteStop>, start: RouteStop, roadDistances: RouteDistanceMatrix?): List<RouteStop> {
        val remaining = points.filterNot { it.id == start.id }.toMutableList()
        val ordered = mutableListOf(start)
        var current = start
        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { distanceMeters(current, it, roadDistances) } ?: break
            ordered += next
            remaining.remove(next)
            current = next
        }
        return ordered
    }

    /** Reverses route subsections while they shorten total straight-line travel distance. */
    private fun improveTwoOpt(route: List<RouteStop>, fixedStart: RouteStop, roadDistances: RouteDistanceMatrix?): List<RouteStop> {
        if (route.size < 4) return route
        val result = route.toMutableList()
        var improved = true
        var passes = 0
        while (improved && passes++ < 8) {
            improved = false
            for (i in 1 until result.size - 1) {
                for (k in i + 1 until result.size) {
                    val a = result[i - 1]
                    val b = result[i]
                    val c = result[k]
                    val d = result.getOrNull(k + 1)
                    // Road distances are directed. Compare the full route cost after
                    // reversal instead of applying the symmetric 2-opt shortcut.
                    val before = routeDistance(result, roadDistances)
                    val candidate = result.toMutableList().apply { subList(i, k + 1).reverse() }
                    val after = routeDistance(candidate, roadDistances)
                    if (after + 1.0 < before) {
                        result.subList(i, k + 1).reverse()
                        improved = true
                    }
                }
            }
        }
        check(result.first().id == fixedStart.id)
        return result
    }

    private fun RouteStop.hasValidCoordinates(): Boolean =
        latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    private fun routeDistance(route: List<RouteStop>, roadDistances: RouteDistanceMatrix?): Double =
        route.zipWithNext().sumOf { (from, to) -> distanceMeters(from, to, roadDistances) }

    private fun distanceMeters(
        a: RouteStop,
        b: RouteStop,
        roadDistances: RouteDistanceMatrix?
    ): Double = roadDistances?.distance(a, b) ?: geodesicDistanceMeters(a, b)

    private fun geodesicDistanceMeters(a: RouteStop, b: RouteStop): Double {
        val lat1 = Math.toRadians(a.latitude!!)
        val lat2 = Math.toRadians(b.latitude!!)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(b.longitude!! - a.longitude!!)
        val haversine = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return 6_371_000.0 * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    }
}
