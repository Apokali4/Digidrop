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

    @Test fun rejectsUnexpectedArchivePathsBeforeParsingJson() {
        val bytes = archive("../outside.txt" to byteArrayOf(1))
        try {
            BackupManager.readBackup(ByteArrayInputStream(bytes), File("build/test-backup"))
            fail("Ожидалось отклонение неизвестного пути")
        } catch (expected: IllegalArgumentException) {
            // Expected: archive entries are allow-listed.
        }
    }

    @Test fun rejectsDuplicateArchiveEntries() {
        val bytes = archive(
            "backup.json" to "{}".toByteArray(),
            "backup.json" to "{}".toByteArray()
        )
        try {
            BackupManager.readBackup(ByteArrayInputStream(bytes), File("build/test-backup"))
            fail("Ожидалось отклонение дублирующейся записи")
        } catch (expected: IllegalArgumentException) {
            // Expected: duplicate entries are not accepted.
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
