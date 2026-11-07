package com.example.qrhelper

internal fun mapPointIndexFromMarkerNumber(markerNumber: Int?, groupCount: Int): Int? {
    if (markerNumber == null || groupCount <= 0) return null
    return (markerNumber - 1).takeIf { it in 0 until groupCount }
}
