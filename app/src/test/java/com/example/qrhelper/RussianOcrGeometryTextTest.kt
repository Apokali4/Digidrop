package com.example.qrhelper

import org.junit.Assert.assertTrue
import org.junit.Test

class RussianOcrGeometryTextTest {
    @Test fun keepsWordsNearColumnBoundaryInTheCellWhereTheirCentersBelong() {
        val words = listOf(
            RussianOcrWord("№", 10, 10, 10, 10, 99f),
            RussianOcrWord("Код", 100, 10, 25, 10, 99f),
            RussianOcrWord("Клиент", 200, 10, 50, 10, 99f),
            RussianOcrWord("Адрес", 400, 10, 45, 10, 99f),
            RussianOcrWord("1", 10, 40, 8, 10, 99f),
            RussianOcrWord("1708", 100, 40, 30, 10, 99f),
            RussianOcrWord("Балтика", 200, 40, 55, 10, 99f),
            RussianOcrWord("ПК", 265, 40, 18, 10, 99f),
            RussianOcrWord("ООО", 310, 40, 20, 10, 99f),
            RussianOcrWord("г.", 400, 40, 12, 10, 99f),
            RussianOcrWord("Кирова", 425, 40, 50, 10, 99f),
            RussianOcrWord("12", 490, 40, 12, 10, 99f)
        )

        val reconstructed = RussianOcrGeometryText.reconstruct(words)
        val dataRow = reconstructed.lines().last()

        assertTrue("Client words must stay in the client cell: $dataRow",
            dataRow.split('\t').getOrNull(2).orEmpty().contains("ООО"))
        assertTrue("Address must remain in its own cell: $dataRow",
            dataRow.split('\t').getOrNull(3).orEmpty().contains("Кирова"))
    }

    @Test fun reconstructsEveryDeliveryRowInOriginalVerticalOrder() {
        val words = listOf(
            RussianOcrWord("№", 10, 10, 10, 10, 99f),
            RussianOcrWord("Клиент", 160, 10, 50, 10, 99f),
            RussianOcrWord("Адрес", 360, 10, 45, 10, 99f),
            RussianOcrWord("1", 10, 40, 8, 10, 99f),
            RussianOcrWord("Север", 160, 40, 35, 10, 99f),
            RussianOcrWord("Кирова", 360, 40, 50, 10, 99f),
            RussianOcrWord("2", 10, 70, 8, 10, 99f),
            RussianOcrWord("Юг", 160, 70, 20, 10, 99f),
            RussianOcrWord("Ленина", 360, 70, 50, 10, 99f)
        )

        val rows = RussianOcrGeometryText.reconstruct(words).lines()

        assertTrue(rows.size >= 3)
        assertTrue(rows[1].contains("Север"))
        assertTrue(rows[2].contains("Юг"))
    }
}
