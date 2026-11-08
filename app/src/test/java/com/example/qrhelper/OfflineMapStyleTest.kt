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
        assertTrue(style.contains("maxzoom"))
        assertTrue(style.contains("© OpenStreetMap contributors"))
        assertTrue("Map labels must use bundled offline glyphs", style.contains("asset://fonts/{fontstack}/{range}.pbf"))
        assertTrue("Road labels must be present", style.contains("transportation_name"))
        assertTrue("Place labels must be present", style.contains("\"source-layer\":\"place\""))
        assertFalse("Vector tile sources must not declare raster-dem encoding", style.contains("\\"encoding\\""))
    }
}
