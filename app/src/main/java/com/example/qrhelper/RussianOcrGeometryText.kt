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
        val orderedRows = rows.sortedBy { row -> row.minOf { it.top + it.height / 2 } }
        val headerIndex = orderedRows.indexOfFirst { row ->
            val headerText = row.joinToString(" ") { it.text.lowercase(java.util.Locale("ru", "RU")) }
                .replace('ё', 'е')
            Regex("""(?iu)(?:адрес|телефон|короб|мест|наклад|инкассац|клиент|организац|контакт|примеч|коммент|код|номер|№)""")
                .findAll(headerText).count() >= 2
        }

        if (headerIndex >= 0) {
            val headerWords = orderedRows[headerIndex].sortedBy { it.left }
            val headerGroups = mutableListOf<MutableList<RussianOcrWord>>()
            for (word in headerWords) {
                val previousGroup = headerGroups.lastOrNull()
                val previousWord = previousGroup?.lastOrNull()
                val gap = if (previousWord == null) Int.MAX_VALUE
                    else word.left - (previousWord.left + previousWord.width)
                if (previousGroup != null && gap < columnGap) previousGroup += word
                else headerGroups += mutableListOf(word)
            }
            val anchors = headerGroups.map { group -> group.minOf { it.left } }
            if (anchors.size >= 2) {
                return orderedRows.mapIndexed { index, row ->
                    if (index == headerIndex) {
                        headerGroups.joinToString("\t") { group ->
                            group.sortedBy { it.left }.joinToString(" ") { it.text.trim() }
                        }
                    } else if (index < headerIndex) {
                        renderByGaps(row, columnGap)
                    } else {
                        val cells = MutableList(anchors.size) { mutableListOf<String>() }
                        row.forEach { word ->
                            val column = anchors.indices.minByOrNull { column ->
                                kotlin.math.abs(word.left - anchors[column])
                            } ?: 0
                            cells[column] += word.text.trim()
                        }
                        cells.joinToString("\t") { it.joinToString(" ") }
                    }
                }.filter(String::isNotBlank).joinToString("\n").trim()
            }
        }

        return orderedRows.joinToString("\n") { renderByGaps(it, columnGap) }.trim()
    }

    private fun renderByGaps(row: List<RussianOcrWord>, columnGap: Float): String {
        val ordered = row.sortedBy { it.left }
        return buildString {
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
}
