package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoRouteUnnumberedEvidenceTest {
    @Test fun keepsUnnumberedAddressRowVisibleAsReviewEvidence() {
        val text = """
            №\tКлиент\tАдрес
            1\tСевер\tКалуга, ул. Кирова, д. 8
            \t\tКалуга, ул. Ленина, д. 14
        """.trimIndent().replace("\\t", "\t")
        val rows = PhotoRouteTableParser.parse(text, "Калуга")
        val evidence = rows.flatMap { it.extraColumns }.filter { it.startsWith("Строка без номера точки/клиента пропущена:") }.distinct()
        assertEquals(1, rows.size)
        assertEquals(listOf("Строка без номера точки/клиента пропущена: Калуга, ул. Ленина, д. 14"), evidence)
    }

    @Test fun doesNotTreatOrdinaryContinuationAsNewUnnumberedStop() {
        val text = """
            №\tКлиент\tАдрес
            1\tСевер\tКалуга, ул. Кирова, д. 8
            \t\tкорпус 2
        """.trimIndent().replace("\\t", "\t")
        val rows = PhotoRouteTableParser.parse(text, "Калуга")
        assertEquals(emptyList<String>(), PhotoRouteUnnumberedEvidence.collect(rows))
    }
}
