package com.example.qrhelper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import java.io.ByteArrayInputStream
import org.junit.Test

class InvoiceImportKindTest {
    @Test fun readsSmallImportFilesCompletely() {
        val source = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(source, ByteArrayInputStream(source).readInvoiceBytes())
    }

    @Test fun rejectsEmptyImportFiles() {
        try {
            ByteArrayInputStream(byteArrayOf()).readInvoiceBytes()
            fail("Пустой файл должен быть отклонён")
        } catch (expected: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test fun recognizesImagesSelectedFromTheUploadPicker() {
        assertTrue(InvoiceImportKind.isImage("image/jpeg", "content://photos/1"))
        assertTrue(InvoiceImportKind.isImage("image/png", "content://photos/2"))
        assertTrue(InvoiceImportKind.isImage("", "content://docs/route-photo.webp"))
    }

    @Test fun detectsAdditionalOfficeAndStructuredFormats() {
        assertTrue(InvoiceImportKind.detect("", "content://docs/old.xls") == InvoiceDocumentFormat.XLS)
        assertTrue(InvoiceImportKind.detect("", "content://docs/scan.docx") == InvoiceDocumentFormat.DOCX)
        assertTrue(InvoiceImportKind.detect("", "content://docs/data.xml") == InvoiceDocumentFormat.XML)
        assertTrue(InvoiceImportKind.detect("", "content://docs/data.json") == InvoiceDocumentFormat.JSON)
        assertTrue(InvoiceImportKind.detect("", "content://docs/page.html") == InvoiceDocumentFormat.HTML)
        assertTrue(InvoiceImportKind.detect("", "content://docs/list.tsv") == InvoiceDocumentFormat.TSV)
        assertTrue(InvoiceImportKind.detect("", "content://docs/photo.tiff") == InvoiceDocumentFormat.IMAGE)
    }

    @Test fun keepsInvoiceDocumentsOutOfThePhotoOcrFlow() {
        assertFalse(InvoiceImportKind.isImage("application/pdf", "content://docs/route.pdf"))
        assertFalse(InvoiceImportKind.isImage("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "content://docs/route.xlsx"))
        assertFalse(InvoiceImportKind.isImage("text/csv", "content://docs/route.csv"))
    }
}
