package com.example.qrhelper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapStyleTest {
    @Test
    fun generatedStyleIsValidJsonWithoutTrailingCommas() {
        val style = buildOfflineMapStyleJson(
            source = "pmtiles://file:///data/user/0/app/files/kaluga-region.pmtiles",
            routeGeoJson = """{"type":"FeatureCollection","features":[]}""",
            lineGeoJson = """{"type":"FeatureCollection","features":[]}"""
        )

        assertFalse(
            "MapLibre style JSON must not contain trailing commas",
            Regex(",\\s*[}\\]]").containsMatchIn(style)
        )
        assertTrue(style.contains("\"route-points-layer\""))
        assertTrue(style.contains("\"route-line-layer\""))
    }
}
