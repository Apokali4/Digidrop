package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceImportMergeTest {
    @Test fun identifiesSameInvoiceWithoutResettingItsProgress() {
        val current = listOf(
            RouteItem("4601234567890", "Магазин", "Адрес", "Молоко", 5, "", scannedBoxes = 3)
        )
        val result = mergeInvoiceImport(current, current.map { it.copy(scannedBoxes = 0) })
        assertTrue(result.isDuplicate)
        assertEquals(3, result.items.single().scannedBoxes)
    }

    @Test fun carriesProgressAndEvidenceForRowsThatRemainInReplacementInvoice() {
        val current = listOf(
            RouteItem(
                "4601234567890", "Магазин", "Адрес", "Молоко", 5, "",
                scannedBoxes = 3, discrepancyQty = 1, discrepancyReason = "НЕДОВОЗ",
                photoDocUri = "file:///photo.jpg", signaturePath = "file:///sign.png"
            )
        )
        val incoming = listOf(
            current.single().copy(scannedBoxes = 0, discrepancyQty = 0, photoDocUri = "", signaturePath = "")
        )
        val result = mergeInvoiceImport(current, incoming)
        assertFalse(result.isDuplicate)
        assertEquals(3, result.items.single().scannedBoxes)
        assertEquals(1, result.items.single().discrepancyQty)
        assertEquals("file:///photo.jpg", result.items.single().photoDocUri)
        assertEquals("file:///sign.png", result.items.single().signaturePath)
    }

    @Test fun doesNotCarryProgressToDifferentCargoAtSameStore() {
        val current = listOf(
            RouteItem("4601234567890", "Магазин", "Адрес", "Молоко", 5, "", scannedBoxes = 3)
        )
        val incoming = listOf(
            RouteItem("4601234567891", "Магазин", "Адрес", "Хлеб", 5, "")
        )
        assertEquals(0, mergeInvoiceImport(current, incoming).items.single().scannedBoxes)
    }
    @Test fun preservesProgressForRepeatedIdenticalCargoRowsInTheirOriginalOrder() {
        val current = listOf(
            RouteItem("same", "Магазин", "Адрес", "Молоко", 2, "", scannedBoxes = 1),
            RouteItem("same", "Магазин", "Адрес", "Молоко", 4, "", scannedBoxes = 3)
        )
        val incoming = listOf(
            current[0].copy(scannedBoxes = 0),
            current[1].copy(scannedBoxes = 0)
        )
        val result = mergeInvoiceImport(current, incoming)
        assertEquals(listOf(1, 3), result.items.map { it.scannedBoxes })
    }

    @Test fun treatsReorderedInvoiceRowsAsDuplicate() {
        val current = listOf(
            RouteItem("a", "Магазин", "Адрес", "Молоко", 2, ""),
            RouteItem("b", "Магазин", "Адрес", "Хлеб", 4, "")
        )
        val result = mergeInvoiceImport(current, current.reversed())
        assertTrue(result.isDuplicate)
        assertEquals(current, result.items)
    }

    @Test fun carriesProgressAndEvidenceWhenContactPhoneChanges() {
        val current = listOf(
            RouteItem(
                "4601234567890", "Магазин", "Адрес", "Молоко", 5, "+7 900 111-22-33",
                scannedBoxes = 2, discrepancyQty = 1,
                photoDocUri = "file:///old-photo.jpg",
                signaturePath = "file:///old-sign.png"
            )
        )
        val incoming = listOf(
            current.single().copy(
                phone = "+7 900 999-88-77",
                scannedBoxes = 0,
                discrepancyQty = 0,
                photoDocUri = "",
                signaturePath = ""
            )
        )

        val merged = mergeInvoiceImport(current, incoming).items.single()

        assertEquals("+7 900 999-88-77", merged.phone)
        assertEquals(2, merged.scannedBoxes)
        assertEquals(1, merged.discrepancyQty)
        assertEquals("file:///old-photo.jpg", merged.photoDocUri)
        assertEquals("file:///old-sign.png", merged.signaturePath)
    }

    @Test fun preservesPreviouslyScannedPackageCodesWhenReplacementDoesNotSpecifyCodes() {
        assertEquals(
            setOf("4601234567890", "4601234567891"),
            scanCodesForRouteReplacement(
                existing = setOf("4601234567890", "4601234567891"),
                requested = null
            )
        )
    }

    @Test fun explicitEmptyScanCodeSetResetsPreviousCodes() {
        assertTrue(
            scanCodesForRouteReplacement(
                existing = setOf("4601234567890"),
                requested = emptySet()
            ).isEmpty()
        )
    }

}
