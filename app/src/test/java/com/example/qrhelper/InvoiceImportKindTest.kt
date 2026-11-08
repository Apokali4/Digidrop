package com.example.qrhelper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceImportKindTest {
    @Test fun recognizesImagesSelectedFromTheUploadPicker() {
        assertTrue(InvoiceImportKind.isImage("image/jpeg", "content://photos/1"))
        assertTrue(InvoiceImportKind.isImage("image/png", "content://photos/2"))
        assertTrue(InvoiceImportKind.isImage("", "content://docs/route-photo.webp"))
    }

    @Test fun keepsInvoiceDocumentsOutOfThePhotoOcrFlow() {
        assertFalse(InvoiceImportKind.isImage("application/pdf", "content://docs/route.pdf"))
        assertFalse(InvoiceImportKind.isImage("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "content://docs/route.xlsx"))
        assertFalse(InvoiceImportKind.isImage("text/csv", "content://docs/route.csv"))
    }
}
