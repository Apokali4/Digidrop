package com.example.qrhelper

import com.example.qrhelper.domain.RouteStop
import com.example.qrhelper.domain.RouteOrderOptimizer

/** Reorders only pending delivery points; completed points remain at the front. */
internal fun rebuildRemainingRoute(items: List<RouteItem>): List<RouteItem> {
    val groups = items.groupBy { it.storeName to it.address }.values.map { it.toList() }
    if (groups.size < 3) return items

    val stops = groups.map { group ->
        val first = group.first()
        RouteStop(
            id = first.storeName + "|" + first.address,
            latitude = first.latitude,
            longitude = first.longitude,
            completed = deliveryPointStatus(group) == com.example.qrhelper.domain.DeliveryPointStatus.DELIVERED
        )
    }
    val optimized = RouteOrderOptimizer.optimize(stops)
    val byId = groups.associateBy { group -> group.first().storeName + "|" + group.first().address }
    return optimized.flatMap { stop -> byId[stop.id].orEmpty() }
}

/** Returns the first point that is not complete by either manual confirmation or scan totals. */
internal fun nextPendingPoint(items: List<RouteItem>): List<RouteItem>? =
    items.groupBy { it.storeName to it.address }.values.firstOrNull { group ->
        deliveryPointStatus(group) != com.example.qrhelper.domain.DeliveryPointStatus.DELIVERED
    }

/** True when a package barcode resolves to a destination other than the current delivery point. */
internal fun isDifferentDeliveryPoint(scanned: RouteItem, current: RouteItem?): Boolean =
    current != null && (scanned.storeName.trim().equals(current.storeName.trim(), ignoreCase = true).not() ||
        scanned.address.trim().equals(current.address.trim(), ignoreCase = true).not())


/** Advances through Yandex route segments and wraps only after the final segment. */
internal fun nextNavigationSegmentIndex(currentIndex: Int, segmentCount: Int): Int =
    if (segmentCount <= 0) 0 else (currentIndex + 1).mod(segmentCount)


/** Manual order is authoritative; otherwise prepare the route with the optimizer. */
internal fun routeForNavigation(items: List<RouteItem>, manualOrder: Boolean): List<RouteItem> =
    if (manualOrder) items else rebuildRemainingRoute(items)
