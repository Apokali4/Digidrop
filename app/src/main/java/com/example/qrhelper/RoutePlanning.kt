package com.example.qrhelper

import com.example.qrhelper.domain.RouteStop
import com.example.qrhelper.domain.RouteOrderOptimizer

/** Reorders physical delivery addresses, preserving every delivery row at each stop. */
internal fun rebuildRemainingRoute(items: List<RouteItem>): List<RouteItem> {
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
    val optimized = RouteOrderOptimizer.optimize(stops)
    val byId = groups
    return optimized.flatMap { stop -> byId[stop.id].orEmpty() }
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
