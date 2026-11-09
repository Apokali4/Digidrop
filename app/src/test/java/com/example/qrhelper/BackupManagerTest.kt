package com.example.qrhelper

import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManagerTest {
    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    @Test fun refusesToExportMoreThanTenThousandRouteRows() {
        val snapshot = BackupSnapshot(
            routeItems = List(10_001) { RouteItem("", "", "Адрес", "Товар", 1, "") }
        )
        try {
            BackupManager.writeBackup(ByteArrayOutputStream(), snapshot, File("build/test-backup"))
            fail("Ожидалось ограничение числа строк")
        } catch (expected: IllegalArgumentException) {
            // Expected: export and import enforce the same route row limit.
        }
    }

    @Test fun rejectsUnexpectedArchivePathsBeforeParsingJson() {
        val bytes = archive("../outside.txt" to byteArrayOf(1))
        try {
            BackupManager.readBackup(ByteArrayInputStream(bytes), File("build/test-backup"))
            fail("Ожидалось отклонение неизвестного пути")
        } catch (expected: IllegalArgumentException) {
            // Expected: archive entries are allow-listed.
        }
    }

    @Test fun rejectsArchivesWithTooManyEntries() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            repeat(2049) { index ->
                zip.putNextEntry(ZipEntry("evidence/photo_$index"))
                zip.closeEntry()
            }
        }
        try {
            BackupManager.readBackup(ByteArrayInputStream(output.toByteArray()), File("build/test-backup"))
            fail("Ожидалось ограничение числа файлов")
        } catch (expected: IllegalArgumentException) {
            // Expected: entry count is bounded before JSON parsing.
        }
    }

    @Test fun rejectsArchivesWithoutBackupManifest() {
        val bytes = archive("evidence/photo_0" to byteArrayOf(1, 2, 3))
        try {
            BackupManager.readBackup(ByteArrayInputStream(bytes), File("build/test-backup"))
            fail("Ожидалось сообщение об отсутствии backup.json")
        } catch (expected: IllegalStateException) {
            // Expected: a backup must contain its manifest.
        }
    }
    @Test fun refusesToWriteBackupThatWouldExceedSupportedArchiveEntryCount() {
        try {
            requireBackupEntryCount(2049)
            fail("Ожидалось ограничение количества ZIP-записей")
        } catch (expected: IllegalArgumentException) {
            // Writer and reader must agree on the same archive entry ceiling.
        }
    }

    @Test fun acceptsBackupAtMaximumArchiveEntryCount() {
        requireBackupEntryCount(2048)
    }

    @Test fun cleanupRemovesOnlyTheDedicatedRestoredEvidenceDirectory() {
        val root = createTempDir(prefix = "backup-evidence-")
        try {
            val restored = File(root, "delivery-evidence/restored_123")
            check(restored.mkdirs())
            val evidence = File(restored, "photo_0").apply { writeBytes(byteArrayOf(1)) }
            val unrelated = File(root, "delivery-evidence/photo_keep.jpg").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(2))
            }
            val snapshot = BackupSnapshot(
                routeItems = listOf(RouteItem("", "", "Адрес", "Товар", 1, "", photoDocUri = evidence.absolutePath))
            )

            BackupManager.cleanupRestoredEvidence(snapshot, root)

            org.junit.Assert.assertFalse(restored.exists())
            org.junit.Assert.assertTrue(unrelated.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun cleanupIgnoresEvidenceOutsideRestoredDirectory() {
        val root = createTempDir(prefix = "backup-evidence-")
        try {
            val evidence = File(root, "delivery-evidence/photo_keep.jpg").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1))
            }
            val snapshot = BackupSnapshot(
                routeItems = listOf(RouteItem("", "", "Адрес", "Товар", 1, "", photoDocUri = evidence.absolutePath))
            )

            BackupManager.cleanupRestoredEvidence(snapshot, root)

            org.junit.Assert.assertTrue(evidence.exists())
        } finally {
            root.deleteRecursively()
        }
    }

}
