package com.example.qrhelper

internal data class ProductCatalogRecord(
    val barcode: String,
    val productName: String
)

internal fun mapProductCatalog(items: List<RouteItem>): List<ProductCatalogRecord> =
    items.asSequence()
        .filter { it.barcode.isNotBlank() && it.itemName.isNotBlank() }
        .groupBy { it.barcode.trim() }
        .map { (barcode, rows) ->
            ProductCatalogRecord(barcode, rows.last().itemName.trim())
        }
        .sortedBy { it.productName.lowercase() }
