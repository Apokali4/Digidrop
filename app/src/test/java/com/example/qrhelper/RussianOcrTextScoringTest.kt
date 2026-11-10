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

    @Test fun twoDeliveryRowsScoreHigherThanOneDeliveryRow() {
        val one = "1 1708 Балтика ООО г. Калуга, ул. Кирова, д. 12"
        val two = one + "\n2 1646 Альфа ООО г. Калуга, ул. Ленина, д. 8"
        assertTrue(RussianOcrTextScoring.score(two) > RussianOcrTextScoring.score(one))
    }

    @Test fun mergesComplementaryOcrPassesInsteadOfDiscardingRowsMissingFromTheBestPass() {
        val merged = RussianOcrPassPlanner.mergeCandidates(
            listOf(
                "1 ООО Север г. Калуга, ул. Кирова, д. 8\n3 ИП Иванов г. Калуга, ул. Победы, д. 2\n" + "Дополнительные реквизиты ".repeat(80),
                "1 ООО Север г. Калуга, ул. Кирова, д. 8\n2 Пятёрочка г. Калуга, ул. Ленина, д. 14\n3 ИП Иванов г. Калуга, ул. Победы, д. 2"
            )
        )

        assertTrue(merged.contains("2 Пятёрочка"))
        assertTrue(merged.contains("1 ООО Север"))
        assertTrue(merged.contains("3 ИП Иванов"))
    }

    @Test fun numberedRouteRowsWithoutCustomerCodesStillCountAsRouteEvidence() {
        val plain = RussianOcrTextScoring.score("Магазин Север г. Калуга, ул. Кирова, д. 8")
        val numbered = RussianOcrTextScoring.score("1 Магазин Север г. Калуга, ул. Кирова, д. 8")
        assertTrue(numbered > plain)
    }

    @Test fun lowConfidenceDocumentsTryAllQuarterTurnOrientations() {
        assertTrue(RussianOcrPassPlanner.candidateRotations(0, 0) == listOf(90, 180, 270))
        assertTrue(RussianOcrPassPlanner.candidateRotations(319, 36) == listOf(90, 180, 270))
    }

    @Test fun partialRouteRecognitionForcesRecoveryEvenWhenTextScoreIsHigh() {
        assertTrue(RussianOcrPassPlanner.candidateRotations(1700, 8) == listOf(90, 180, 270))
    }

    @Test fun completeRouteRecognitionSkipsExpensiveRotationAttempts() {
        assertTrue(RussianOcrPassPlanner.candidateRotations(2500, 24).isEmpty())
        assertTrue(RussianOcrPassPlanner.candidateRotations(5000, 36).isEmpty())
    }

    @Test fun countsRouteRowsFromNumberedLinesAndAddressEvidence() {
        val rows = (1..8).joinToString("\\n") { index ->
            "$index 1708 Магазин $index г. Калуга, ул. Болдина, д. $index"
        }
        assertTrue(RussianOcrTextScoring.routeRowCount(rows) >= 8)
    }


    @Test fun incompleteRowsAlsoTriggerAdaptiveThresholding() {
        assertTrue(RussianOcrPassPlanner.shouldTryAdaptive(1700, 8))
        assertTrue(!RussianOcrPassPlanner.shouldTryAdaptive(2500, 24))
    }

    @Test fun countsAddressOnlyRowsWhenTheDocumentHasNoPrintedRowNumbers() {
        val text = """
            Магазин Север г. Калуга, ул. Кирова, д. 8
            Пятёрочка г. Калуга, ул. Ленина, д. 14
            ИП Иванов г. Калуга, ул. Победы, д. 2
        """.trimIndent()
        assertTrue(RussianOcrTextScoring.routeRowCount(text) >= 3)
    }


    @Test fun ignoresSingleAddressNoiseWhenARealMultiRowPassExists() {
        val merged = RussianOcrPassPlanner.mergeCandidates(
            listOf(
                "1 Магазин Север г. Калуга, ул. Кирова, д. 8\n2 Пятёрочка г. Калуга, ул. Ленина, д. 14",
                "случайный текст г. Калуга, ул. Фальшивая, д. 999"
            )
        )
        assertTrue(!merged.contains("Фальшивая"))
        assertTrue(merged.contains("Кирова"))
        assertTrue(merged.contains("Ленина"))
    }

}
