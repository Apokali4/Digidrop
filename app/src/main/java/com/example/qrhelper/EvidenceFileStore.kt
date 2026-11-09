package com.example.qrhelper

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

internal const val MAX_EVIDENCE_FILE_BYTES = 12L * 1024L * 1024L

internal object EvidenceFileStore {
    fun write(directory: File, fileName: String, writer: (FileOutputStream) -> Unit): File {
        require(fileName.matches(Regex("[a-zA-Z0-9_-]+\\.(jpg|jpeg|png)"))) {
            "Недопустимое имя файла доказательства"
        }
        check(directory.exists() || directory.mkdirs()) { "Не удалось создать папку доказательств" }
        val target = File(directory, fileName)
        val temporary = File(directory, ".$fileName.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                writer(output)
                output.flush()
                output.fd.sync()
            }
            check(temporary.length() in 1..MAX_EVIDENCE_FILE_BYTES) {
                "Файл доказательства пустой или превышает 12 МБ"
            }
            check(temporary.renameTo(target)) { "Не удалось завершить сохранение файла доказательства" }
            return target
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    fun copy(directory: File, fileName: String, input: InputStream): File =
        write(directory, fileName) { output ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_EVIDENCE_FILE_BYTES) {
                    "Файл доказательства превышает 12 МБ"
                }
                output.write(buffer, 0, count)
            }
        }
}
