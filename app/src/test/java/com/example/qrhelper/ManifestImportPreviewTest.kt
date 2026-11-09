package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class ManifestImportPreviewTest {
    @Test
    fun keepsRowsFromDifferentFilesInSeparateDocumentsEvenForSameStore() {
        val first = RouteItem("111", "Один магазин", "Адрес", "Молоко", 3, "")
        val second = RouteItem("222", "Один магазин", "Адрес", "Хлеб", 5, "")

        val preview = prepareManifestImportPreview(
            listOf(
                ImportedManifestDocument("Поставщик А.xlsx", listOf(first)),
                ImportedManifestDocument("Поставщик Б.pdf", listOf(second))
            )
        )

        assertEquals(2, preview.totalDocuments)
        assertEquals(2, preview.totalRows)
        assertEquals(listOf("Поставщик А.xlsx", "Поставщик Б.pdf"), preview.documents.map { it.sourceName })
        assertEquals(listOf(first), preview.documents[0].rows)
        assertEquals(listOf(second), preview.documents[1].rows)
    }

    @Test
    fun keepsAnEmptyDocumentVisibleAndAssignsNameWhenSourceNameIsBlank() {
        val preview = prepareManifestImportPreview(
            listOf(ImportedManifestDocument("  ", emptyList()))
        )

        assertEquals(1, preview.totalDocuments)
        assertEquals(0, preview.totalRows)
        assertEquals("Документ 1", preview.documents.single().sourceName)
        assertEquals(emptyList<RouteItem>(), preview.documents.single().rows)
    }

    @Test
    fun doesNotMutateTheSourceRowsCollection() {
        val rows = mutableListOf(RouteItem("111", "Магазин", "Адрес", "Товар", 1, ""))
        val preview = prepareManifestImportPreview(
            listOf(ImportedManifestDocument("Накладная.csv", rows))
        )

        rows.clear()

        assertEquals(1, preview.documents.single().rows.size)
    }
}
