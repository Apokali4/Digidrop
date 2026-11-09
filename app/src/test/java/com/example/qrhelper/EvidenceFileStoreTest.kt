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
    @Test fun deletesOnlyFilesDirectlyInsideEvidenceDirectory() {
        val directory = Files.createTempDirectory("digidrop-evidence-cleanup").toFile()
        val outside = File(directory.parentFile, "digidrop-outside-${System.nanoTime()}.jpg")
        try {
            val owned = File(directory, "photo_old.jpg").apply { writeBytes(byteArrayOf(1)) }
            outside.writeBytes(byteArrayOf(2))

            assertTrue(EvidenceFileStore.deleteOwned(directory, owned.absolutePath))
            assertFalse(owned.exists())
            assertFalse(EvidenceFileStore.deleteOwned(directory, outside.absolutePath))
            assertFalse(EvidenceFileStore.deleteOwned(directory, File(directory, "../${outside.name}").path))
            assertTrue(outside.exists())
        } finally {
            directory.deleteRecursively()
            outside.delete()
        }
    }

    @Test fun refusesToOverwriteAnExistingEvidenceFile() {
        val directory = Files.createTempDirectory("digidrop-evidence-no-overwrite").toFile()
        try {
            val existing = File(directory, "photo_same.jpg").apply { writeBytes(byteArrayOf(7)) }
            try {
                EvidenceFileStore.copy(directory, existing.name, ByteArrayInputStream(byteArrayOf(8)))
                throw AssertionError("Expected an existing evidence file to be protected")
            } catch (expected: IllegalStateException) {
                assertArrayEquals(byteArrayOf(7), existing.readBytes())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun startupCleanupKeepsReferencedEvidenceAndRemovesOnlyOrphansAndTemps() {
        val directory = Files.createTempDirectory("digidrop-evidence-reconcile").toFile()
        try {
            val referenced = File(directory, "photo_kept.jpg").apply { writeBytes(byteArrayOf(1)) }
            val orphan = File(directory, "signature_orphan.png").apply { writeBytes(byteArrayOf(2)) }
            val temporary = File(directory, ".photo_interrupted.jpg.tmp").apply { writeBytes(byteArrayOf(3)) }
            val unrelated = File(directory, "notes.txt").apply { writeBytes(byteArrayOf(4)) }

            val removed = EvidenceFileStore.cleanupUnreferenced(
                directory,
                setOf(referenced.absolutePath, "/some/archived/evidence.jpg")
            )

            assertTrue(removed >= 2)
            assertTrue(referenced.exists())
            assertFalse(orphan.exists())
            assertFalse(temporary.exists())
            assertTrue(unrelated.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun cleanupDoesNotFollowNestedDirectoriesOrDeleteReferencedFiles() {
        val directory = Files.createTempDirectory("digidrop-evidence-nested").toFile()
        try {
            val nested = File(directory, "nested").apply { mkdirs() }
            val nestedFile = File(nested, "photo_nested.jpg").apply { writeBytes(byteArrayOf(1)) }
            val orphan = File(directory, "photo_orphan.jpg").apply { writeBytes(byteArrayOf(2)) }

            EvidenceFileStore.cleanupUnreferenced(directory, emptySet())

            assertTrue(nestedFile.exists())
            assertFalse(orphan.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

}
