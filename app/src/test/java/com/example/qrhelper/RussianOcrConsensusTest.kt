package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class RussianOcrConsensusTest {
    @Test fun repeatedAgreementRaisesCandidateWithSharedRouteRows() {
        val weak = "1 Магазин Север, Калуга, ул. Кирова, д. 8\n2 Магазин Юг, Калуга, ул. Победы, д. 2"
        val agreed = "1 Магазин Север, Калуга, ул. Кирова, д. 8\n2 Магазин Юг, Калуга, ул. Победы, д. 2"
        val noisy = "1 Магазин Севеp, Калуга, ул. Кирова, д. 8\n2 Магазин Юг, Калуга, ул. Победы, д. 2\nслужебная строка"

        val ranked = RussianOcrConsensus.rank(listOf(noisy, weak, agreed))

        assertEquals(weak, ranked.first())
    }

    @Test fun consensusKeepsUniqueRowsInCandidateSet() {
        val first = "1 Север, Калуга, ул. Кирова, д. 8"
        val second = "2 Юг, Калуга, ул. Победы, д. 2"

        val ranked = RussianOcrConsensus.rank(listOf(first, second))

        assertEquals(2, ranked.size)
        assertEquals(setOf(first, second), ranked.toSet())
    }

    @Test fun blankCandidatesAreIgnored() {
        assertEquals(listOf("1 Север, Калуга, ул. Кирова, д. 8"),
            RussianOcrConsensus.rank(listOf("", "  ", "1 Север, Калуга, ул. Кирова, д. 8")))
    }
}
