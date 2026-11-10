package com.example.qrhelper

/**
 * Splits tall route-sheet photos into overlapping horizontal OCR regions.
 * The overlap protects rows that cross a crop boundary or sit close to a page fold.
 */
internal object RussianOcrStripPlanner {
    fun ranges(height: Int, stripCount: Int = 3, overlapFraction: Float = 0.12f): List<IntRange> {
        require(height > 0) { "Высота фотографии должна быть больше нуля" }
        require(stripCount >= 1) { "Количество OCR-полос должно быть положительным" }
        require(overlapFraction in 0f..0.4f) { "Некорректное перекрытие OCR-полос" }
        if (height <= 900 || stripCount == 1) return listOf(0 until height)
        val segmentHeight = kotlin.math.ceil(height.toDouble() / stripCount).toInt()
        val overlap = (segmentHeight * overlapFraction).toInt()
        return (0 until stripCount).map { index ->
            val start = (index * segmentHeight - if (index == 0) 0 else overlap).coerceAtLeast(0)
            val endExclusive = ((index + 1) * segmentHeight + if (index == stripCount - 1) 0 else overlap)
                .coerceAtMost(height)
            start until endExclusive
        }.filter { !it.isEmpty() }
    }
}
