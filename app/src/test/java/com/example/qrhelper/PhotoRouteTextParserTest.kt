package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoRouteTextParserTest {
    @Test fun extractsRussianStreetAndHouseAddressLines() {
        val text = """
            Маршрут № 18
            ООО Ромашка
            г. Калуга, ул. Кирова, д. 12
            Телефон 8 900 000 00 00
            проспект Ленина, 45к2
        """.trimIndent()
        assertEquals(
            listOf("г. Калуга, ул. Кирова, д. 12", "проспект Ленина, 45к2"),
            PhotoRouteTextParser.extractCandidates(text)
        )
    }

    @Test fun removesDuplicateAddressLines() {
        val text = "ул. Ленина, 10\nУЛ. ЛЕНИНА 10\nул. Победы, 2"
        assertEquals(2, PhotoRouteTextParser.extractCandidates(text).size)
    }

    @Test fun ignoresLinesWithoutAddressSignals() {
        assertEquals(emptyList<String>(), PhotoRouteTextParser.extractCandidates("Маршрут № 5\nИванов И.И.\n8 900 000 00 00"))
    }
    @Test fun downsamplesLargePhotosToSafeOcrSize() {
        assertEquals(1, PhotoOcrImageSizing.sampleSize(1600, 1200))
        assertEquals(2, PhotoOcrImageSizing.sampleSize(4800, 3200))
        assertEquals(4, PhotoOcrImageSizing.sampleSize(9600, 6400))
    }
}
