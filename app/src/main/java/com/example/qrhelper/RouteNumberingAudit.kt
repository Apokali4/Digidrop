package com.example.qrhelper

/**
 * Detects missing printed point numbers between rows that OCR did recognize.
 * It never fabricates a delivery row; it only gives the driver a completeness warning.
 */
internal object RouteNumberingAudit {
    fun missingPointNumbers(rows: List<PhotoRouteDeliveryRow>): List<Int> {
        val recognized = rows.mapNotNull { row ->
            row.pointNumber.trim().toIntOrNull()?.takeIf { it in 1..999 }
        }.distinct().sorted()
        if (recognized.size < 2) return emptyList()

        val present = recognized.toHashSet()
        return (recognized.first()..recognized.last()).filterNot(present::contains)
    }
}
