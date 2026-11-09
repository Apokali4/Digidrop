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
}
