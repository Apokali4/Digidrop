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
}
