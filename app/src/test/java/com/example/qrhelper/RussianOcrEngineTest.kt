package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class RussianOcrEngineTest {
    @Test fun installsOnlyCompleteBoundedModelAtomically() {
        val directory = Files.createTempDirectory("digidrop-ocr-model").toFile()
        try {
            val target = File(directory, "rus.traineddata")
            val valid = ByteArray(1_000_000) { 7 }
            installOcrModel(ByteArrayInputStream(valid), target)
            assertEquals(valid.size.toLong(), target.length())
            assertTrue(target.readBytes().all { it == 7.toByte() })
            assertFalse(File(directory, "rus.traineddata.download").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsTruncatedModelAndKeepsPreviousInstalledModel() {
        val directory = Files.createTempDirectory("digidrop-ocr-model-invalid").toFile()
        try {
            val target = File(directory, "rus.traineddata").apply { writeBytes(byteArrayOf(9, 8, 7)) }
            runCatching { installOcrModel(ByteArrayInputStream(ByteArray(128)), target) }
            assertTrue(target.readBytes().contentEquals(byteArrayOf(9, 8, 7)))
        } finally {
            directory.deleteRecursively()
        }
    }
    @Test fun mergesRecoveredRouteRowsInOriginalNumberOrder() {
        val firstPass = """
            1 Магазин Север, Калуга, ул. Кирова, д. 8
            3 Магазин Юг, Калуга, ул. Победы, д. 2
        """.trimIndent()
        val stripPass = """
            1 Магазин Север, Калуга, ул. Кирова, д. 8
            2 Пятёрочка, Калуга, ул. Ленина, д. 14
            3 Магазин Юг, Калуга, ул. Победы, д. 2
        """.trimIndent()

        val merged = RussianOcrCandidateMerger.merge(listOf(firstPass, stripPass))
        val numbers = merged.lineSequence().mapNotNull {
            Regex("""^\\s*(\\d{1,3})[.)]?\\s+""").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }.toList()

        assertEquals(listOf(1, 2, 3), numbers)
    }

    @Test fun mergingComplementaryPassesDoesNotDuplicateAlreadyRecognizedRows() {
        val firstPass = """
            1 Магазин Север, Калуга, ул. Кирова, д. 8
            2 Пятёрочка, Калуга, ул. Ленина, д. 14
        """.trimIndent()
        val stripPass = """
            1 Магазин Север, Калуга, ул. Кирова, д. 8
            2 Пятёрочка, Калуга, ул. Ленина, д. 14
        """.trimIndent()

        val merged = RussianOcrCandidateMerger.merge(listOf(firstPass, stripPass))

        assertEquals(2, merged.lineSequence().count {
            PhotoRouteTextParser.extractRouteAddressRows(it).isNotEmpty()
        })
    }

}
