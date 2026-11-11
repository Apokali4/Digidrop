package com.example.qrhelper

/**
 * Ranks local OCR passes by agreement without deleting complementary text.
 * Exact normalized route lines supported by several passes receive a bonus;
 * one-off route lines remain in the candidate so the merger can recover them.
 */
internal object RussianOcrConsensus {
    fun rank(candidates: List<String>): List<String> {
        val passes = candidates.map(String::trim).filter(String::isNotBlank)
        val texts = passes.distinct()
        if (texts.size < 2) return texts

        val routeLineCounts = mutableMapOf<String, Int>()
        passes.forEach { text ->
            val seenInPass = mutableSetOf<String>()
            text.lineSequence().forEach { line ->
                if (PhotoRouteTextParser.extractRouteAddressRows(line).isEmpty()) return@forEach
                val key = normalizeLine(line)
                if (key.isNotBlank() && seenInPass.add(key)) {
                    routeLineCounts[key] = (routeLineCounts[key] ?: 0) + 1
                }
            }
        }

        return texts.sortedWith(
            compareByDescending<String> { text ->
                val routeLines = text.lineSequence()
                    .filter { PhotoRouteTextParser.extractRouteAddressRows(it).isNotEmpty() }
                    .map(::normalizeLine)
                    .filter(String::isNotBlank)
                    .distinct()
                    .toList()
                val agreement = routeLines.sumOf { key ->
                    ((routeLineCounts[key] ?: 1) - 1) * 100
                }
                val unsupported = routeLines.count { (routeLineCounts[it] ?: 0) == 1 }
                RussianOcrTextScoring.score(text) + agreement - unsupported * 12
            }.thenByDescending(RussianOcrTextScoring::score)
        )
    }

    private fun normalizeLine(value: String): String =
        value.lowercase(java.util.Locale("ru", "RU"))
            .replace('ё', 'е')
            .filter(Char::isLetterOrDigit)
}
