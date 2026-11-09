package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class InvoiceZipLimitsTest {
    @Test fun readsEntryWithinExpandedLimit() {
        val bytes = ByteArray(32) { it.toByte() }
        assertEquals(bytes.toList(), readBoundedInvoiceZipEntry(ByteArrayInputStream(bytes), 32).toList())
    }

    @Test fun rejectsEntryThatExpandsBeyondLimit() {
        val bytes = ByteArray(33) { 1 }
        assertThrows(IllegalArgumentException::class.java) {
            readBoundedInvoiceZipEntry(ByteArrayInputStream(bytes), 32)
        }
    }

    @Test fun acceptsExactLimitWithoutReadingPastIt() {
        val bytes = ByteArray(16) { 2 }
        assertEquals(16, readBoundedInvoiceZipEntry(ByteArrayInputStream(bytes), 16).size)
    }
    @Test fun accumulatesExpandedXmlAcrossWorkbookEntries() {
        assertEquals(48L, nextXlsxXmlTotal(32L, 16))
    }

    @Test fun rejectsWorkbookWhoseCombinedXmlExceedsBudget() {
        assertThrows(IllegalArgumentException::class.java) {
            nextXlsxXmlTotal(MAX_XLSX_TOTAL_XML_BYTES - 8L, 9)
        }
    }

}
