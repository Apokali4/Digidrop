package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductCatalogMapperTest {
    @Test fun createsOneCatalogEntryPerBarcodeAndKeepsLatestNonBlankName() {
        val products = mapProductCatalog(
            listOf(
                RouteItem("4601", "A", "Address", "Сок яблочный", 2, ""),
                RouteItem("4602", "A", "Address", "Вода", 1, ""),
                RouteItem("4601", "B", "Address", "Сок яблочный 1л", 3, ""),
                RouteItem("", "B", "Address", "Без штрихкода", 1, "")
            )
        )
        assertEquals(
            listOf(
                ProductCatalogRecord("4602", "Вода"),
                ProductCatalogRecord("4601", "Сок яблочный 1л")
            ),
            products
        )
    }

    @Test fun returnsEmptyCatalogForRowsWithoutBarcodeOrName() {
        assertEquals(emptyList<ProductCatalogRecord>(), mapProductCatalog(
            listOf(RouteItem(" ", "A", "Address", " ", 1, ""))
        ))
    }
}
