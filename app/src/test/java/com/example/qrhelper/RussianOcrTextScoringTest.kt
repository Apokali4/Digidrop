package com.example.qrhelper

import org.junit.Assert.assertTrue
import org.junit.Test

class RussianOcrTextScoringTest {
    @Test fun russianStreetAbbreviationsIncreaseRouteTextScore() {
        val plain = RussianOcrTextScoring.score("Обычный текст без адресов")
        val route = RussianOcrTextScoring.score("г. Калуга, ул. Кирова, д. 12")
        assertTrue(route > plain)
    }

    @Test fun numberedDeliveryRowsIncreaseRouteTextScore() {
        val plain = RussianOcrTextScoring.score("Балтика ПК ООО Калуга")
        val route = RussianOcrTextScoring.score("1 1708 Балтика ПК ООО г. Калуга, ул. Кирова, д. 12")
        assertTrue(route > plain)
    }

    @Test fun blankOcrTextGetsLowestScore() {
        assertTrue(RussianOcrTextScoring.score("   ") < 0)
    }
}
