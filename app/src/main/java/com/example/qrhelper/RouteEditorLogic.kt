package com.example.qrhelper

internal data class RoutePointDetails(
    val storeName: String,
    val address: String,
    val phone: String = "",
    val ownerName: String = "",
    val workingHours: String = "",
    val cashNote: String = "",
    val note: String = "",
    val extraComment: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)

internal fun groupRouteItems(items: List<RouteItem>): List<List<RouteItem>> =
    items.groupBy { PhotoRouteTextParser.canonicalAddressKey(it.address) }.values.map { it.toList() }

internal fun moveRoutePointGroup(items: List<RouteItem>, fromIndex: Int, toIndex: Int): List<RouteItem> {
    val groups = groupRouteItems(items).toMutableList()
    if (fromIndex !in groups.indices || toIndex !in groups.indices || fromIndex == toIndex) return items
    val moved = groups.removeAt(fromIndex)
    groups.add(toIndex, moved)
    return groups.flatten()
}

internal fun updateRoutePointDetails(
    items: List<RouteItem>,
    oldStoreName: String,
    oldAddress: String,
    details: RoutePointDetails
): List<RouteItem> = items.map { item ->
    if (PhotoRouteTextParser.canonicalAddressKey(item.address) == PhotoRouteTextParser.canonicalAddressKey(oldAddress)) {
        item.copy(
            storeName = details.storeName.trim(),
            address = details.address.trim(),
            phone = details.phone.trim(),
            ownerName = details.ownerName.trim(),
            workingHours = details.workingHours.trim(),
            cashNote = details.cashNote.trim(),
            note = details.note.trim(),
            extraComment = details.extraComment.trim(),
            latitude = details.latitude,
            longitude = details.longitude
        )
    } else item
}

internal fun removeRoutePointGroup(
    items: List<RouteItem>,
    storeName: String,
    address: String
): List<RouteItem> = items.filterNot {
    PhotoRouteTextParser.canonicalAddressKey(it.address) == PhotoRouteTextParser.canonicalAddressKey(address)
}
