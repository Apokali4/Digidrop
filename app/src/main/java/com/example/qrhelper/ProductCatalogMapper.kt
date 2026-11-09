package com.example.qrhelper

internal data class ProductCatalogRecord(
    val barcode: String,
    val productName: String
)

internal fun mapProductCatalog(items: List<RouteItem>): List<ProductCatalogRecord> =
    items.asSequence()
        .mapNotNull { item ->
            val barcode = item.barcode.trim()
            val productName = item.itemName.trim()
            if (barcode.isBlank() || productName.isBlank()) null
            else barcode to productName
        }
        .groupBy(
            keySelector = { (barcode, _) -> barcode },
            valueTransform = { (_, productName) -> productName }
        )
        .map { (barcode, names) ->
            ProductCatalogRecord(barcode, names.last())
        }
        .sortedBy { it.productName.lowercase() }
