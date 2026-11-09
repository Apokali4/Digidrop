package com.example.qrhelper

private val invalidBarcodeLabels = setOf(
    "штрихкод", "barcode", "код", "кодтовара", "qr", "qrкод", "нет", "н/д", "null", "-"
)

internal fun isUsableInvoiceBarcode(raw: String): Boolean {
    val value = raw.trim()
    if (value.length !in 3..128) return false
    if (value.lowercase().replace("ё", "е") in invalidBarcodeLabels) return false
    return value.any(Char::isLetterOrDigit) && value.any { it.isDigit() || it.isLetter() }
}

internal fun parseInvoiceQuantity(raw: String): Int? {
    if (raw.isBlank()) return 1
    val normalized = raw.trim()
        .replace("\u00a0", "")
        .replace(" ", "")
        .replace(',', '.')
    val value = normalized.toDoubleOrNull() ?: return null
    if (!value.isFinite() || value <= 0.0 || value > 100_000.0) return null
    if (value % 1.0 != 0.0) return null
    return value.toInt()
}

internal fun parseCoordinatePair(latitudeRaw: String, longitudeRaw: String): Pair<Double, Double>? {
    if (latitudeRaw.isBlank() || longitudeRaw.isBlank()) return null
    val latitude = latitudeRaw.trim().replace(',', '.').toDoubleOrNull() ?: return null
    val longitude = longitudeRaw.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return latitude to longitude
}
