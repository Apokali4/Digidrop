package com.example.qrhelper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class EvidenceFileStoreTest {
    @Test fun writesEvidenceToFinalFileOnlyAfterSuccessfulCompletion() {
        val directory = Files.createTempDirectory("digidrop-evidence").toFile()
        try {
            val result = EvidenceFileStore.copy(directory, "photo_test.jpg", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
            assertTrue(result.isFile)
            assertArrayEquals(byteArrayOf(1, 2, 3), result.readBytes())
            assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun removesTemporaryFileWhenWriterFails() {
        val directory = Files.createTempDirectory("digidrop-evidence-failure").toFile()
        try {
            try {
                EvidenceFileStore.write(directory, "signature_test.png") { output ->
                    output.write(byteArrayOf(1, 2, 3))
                    error("simulated interruption")
                }
            } catch (expected: IllegalStateException) {
                // Expected: partial evidence must not be published as a final file.
            }
            assertFalse(File(directory, "signature_test.png").exists())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsEvidenceLargerThanTwelveMegabytes() {
        val directory = Files.createTempDirectory("digidrop-evidence-limit").toFile()
        try {
            val oversized = ByteArray(MAX_EVIDENCE_FILE_BYTES.toInt() + 1)
            try {
                EvidenceFileStore.copy(directory, "photo_large.jpg", ByteArrayInputStream(oversized))
                throw AssertionError("Expected oversized evidence to be rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(directory.listFiles().orEmpty().isEmpty())
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
