package com.example.qrhelper

import com.example.qrhelper.domain.RouteStop
import com.example.qrhelper.domain.RouteOrderOptimizer
import com.example.qrhelper.domain.RouteDistanceMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reorders physical delivery addresses, preserving every delivery row at each stop. */
internal fun rebuildRemainingRoute(items: List<RouteItem>, roadDistances: RouteDistanceMatrix? = null): List<RouteItem> {
    val groups = items.groupBy { PhotoRouteTextParser.canonicalAddressKey(it.address) }
    if (groups.size < 3) return items

    val stops = groups.map { (addressKey, group) ->
        val first = group.first()
        RouteStop(
            id = addressKey,
            latitude = first.latitude,
            longitude = first.longitude,
            completed = deliveryPointStatus(group) == com.example.qrhelper.domain.DeliveryPointStatus.DELIVERED
        )
    }
    val optimized = RouteOrderOptimizer.optimize(stops, roadDistances)
    val byId = groups
    return optimized.flatMap { stop -> byId[stop.id].orEmpty() }
}


/**
 * Uses OSRM road-network distances for the explicit Optimize action.
 * If the device is offline or the routing service is unavailable, the existing
 * deterministic geodesic optimizer remains the safe fallback.
 */
internal suspend fun rebuildRemainingRouteWithRoadNetwork(items: List<RouteItem>): Pair<List<RouteItem>, Boolean> =
    withContext(Dispatchers.IO) {
        val groups = items.groupBy { PhotoRouteTextParser.canonicalAddressKey(it.address) }
        if (groups.size < 3) return@withContext items to false
        val stops = groups.map { (addressKey, group) ->
            val first = group.first()
            RouteStop(
                id = addressKey,
                latitude = first.latitude,
                longitude = first.longitude,
                completed = deliveryPointStatus(group) == com.example.qrhelper.domain.DeliveryPointStatus.DELIVERED
            )
        }
        val pending = stops.filterNot { it.completed }
        if (pending.size < 2 || pending.any {
                it.latitude == null || it.longitude == null ||
                    !it.latitude.isFinite() || !it.longitude.isFinite() ||
                    it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0
            }) return@withContext rebuildRemainingRoute(items) to false

        val matrixStops = stops.filter { stop ->
            val latitude = stop.latitude
            val longitude = stop.longitude
            latitude != null && longitude != null &&
                latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
        }
        val roadDistances = runCatching { RoadDistanceMatrixLoader.fetch(matrixStops) }.getOrNull()
        val optimized = rebuildRemainingRoute(items, roadDistances)
        optimized to (roadDistances != null)
    }


/** Returns the first physical address that still has an undelivered row. */
internal fun nextPendingPoint(items: List<RouteItem>): List<RouteItem>? =
    items.groupBy { PhotoRouteTextParser.canonicalAddressKey(it.address) }.values.firstOrNull { group ->
        deliveryPointStatus(group) != com.example.qrhelper.domain.DeliveryPointStatus.DELIVERED
    }

/** True when a package barcode resolves to a destination other than the current delivery point. */
internal fun isDifferentDeliveryPoint(scanned: RouteItem, current: RouteItem?): Boolean =
    current != null && (scanned.storeName.trim().equals(current.storeName.trim(), ignoreCase = true).not() ||
        PhotoRouteTextParser.canonicalAddressKey(scanned.address) !=
        PhotoRouteTextParser.canonicalAddressKey(current.address))

/** Advances through Yandex route segments and wraps only after the final segment. */
internal fun nextNavigationSegmentIndex(currentIndex: Int, segmentCount: Int): Int =
    if (segmentCount <= 0) 0 else (currentIndex + 1).mod(segmentCount)

/** Manual order is authoritative; otherwise prepare the route with the optimizer. */
internal fun routeForNavigation(items: List<RouteItem>, manualOrder: Boolean): List<RouteItem> =
    if (manualOrder) items else rebuildRemainingRoute(items)
