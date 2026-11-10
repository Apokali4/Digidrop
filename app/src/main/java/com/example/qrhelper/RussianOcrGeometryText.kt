package com.example.qrhelper

internal data class RussianOcrWord(
    val text: String,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val confidence: Float
)

/**
 * Rebuilds OCR lines from Tesseract word boxes. Large horizontal gaps become
 * cell separators, so the route-table parser can retain columns when plain
 * OCR text has flattened the photographed table.
 */
internal object RussianOcrGeometryText {
    fun reconstruct(words: List<RussianOcrWord>): String {
        val valid = words.asSequence()
            .filter { it.text.isNotBlank() && it.width > 0 && it.height > 0 }
            .sortedWith(compareBy<RussianOcrWord> { it.top + it.height / 2 }.thenBy { it.left })
            .toList()
        if (valid.isEmpty()) return ""

        val typicalHeight = valid.map { it.height }.sorted()[valid.size / 2].coerceAtLeast(1)
        val rowTolerance = (typicalHeight * 0.72f).coerceAtLeast(6f)
        val rows = mutableListOf<MutableList<RussianOcrWord>>()

        for (word in valid) {
            val centerY = word.top + word.height / 2f
            val row = rows.minByOrNull { candidate ->
                kotlin.math.abs(candidate.map { it.top + it.height / 2f }.average().toFloat() - centerY)
            }
            if (row != null) {
                val rowCenter = row.map { it.top + it.height / 2f }.average().toFloat()
                if (kotlin.math.abs(rowCenter - centerY) <= rowTolerance) {
                    row += word
                    continue
                }
            }
            rows += mutableListOf(word)
        }

        val typicalWidth = valid.map { it.width }.sorted()[valid.size / 2].coerceAtLeast(1)
        val columnGap = maxOf(typicalHeight * 1.8f, typicalWidth * 1.35f)
        return rows.sortedBy { row -> row.minOf { it.top + it.height / 2 } }
            .joinToString("\n") { row ->
                val ordered = row.sortedBy { it.left }
                buildString {
                    ordered.forEachIndexed { index, word ->
                        if (index > 0) {
                            val previous = ordered[index - 1]
                            val gap = word.left - (previous.left + previous.width)
                            append(if (gap >= columnGap) '\t' else ' ')
                        }
                        append(word.text.trim())
                    }
                }
            }
            .trim()
    }
}
