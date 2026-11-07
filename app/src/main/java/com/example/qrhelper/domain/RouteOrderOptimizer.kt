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

object RouteOrderOptimizer {
    fun optimize(stops: List<RouteStop>): List<RouteStop> {
        if (stops.size < 3) return stops
        val completed = stops.filter { it.completed }
        val pending = stops.filterNot { it.completed }
        if (pending.size < 2 || pending.any { !it.hasValidCoordinates() }) return stops

        val remaining = pending.toMutableList()
        val orderedPending = mutableListOf<RouteStop>()
        val lastCompleted = completed.lastOrNull()
        var current = lastCompleted?.takeIf { it.hasValidCoordinates() }

        if (current == null) {
            current = remaining.removeAt(0)
            orderedPending.add(current)
        }

        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { distanceMeters(current!!, it) } ?: break
            orderedPending.add(next)
            remaining.remove(next)
            current = next
        }
        return completed + orderedPending
    }

    private fun RouteStop.hasValidCoordinates(): Boolean =
        latitude != null && longitude != null &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    private fun distanceMeters(a: RouteStop, b: RouteStop): Double {
        val lat1 = Math.toRadians(a.latitude!!)
        val lat2 = Math.toRadians(b.latitude!!)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(b.longitude!! - a.longitude!!)
        val haversine = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return 6_371_000.0 * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    }
}
