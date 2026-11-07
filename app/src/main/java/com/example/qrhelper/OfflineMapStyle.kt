package com.example.qrhelper

/**
 * Creates the MapLibre style for the bundled Kaluga PMTiles archive and delivery overlays.
 * Keep this pure so malformed style JSON can be covered by local unit tests.
 */
internal fun buildOfflineMapStyleJson(
    source: String,
    routeGeoJson: String,
    lineGeoJson: String
): String = """
    {
      "version": 8,
      "name": "DigiDrop Offline",
      "sources": {
        "regional": {"type":"vector","url":"$source","minzoom":0,"maxzoom":15,"attribution":"© OpenStreetMap contributors"},
        "route-points": {"type":"geojson","data":$routeGeoJson},
        "route-line": {"type":"geojson","data":$lineGeoJson}
      },
      "layers": [
        {"id":"background","type":"background","paint":{"background-color":"#eee9df"}},
        {"id":"water","type":"fill","source":"regional","source-layer":"water","paint":{"fill-color":"#a9cbd5"}},
        {"id":"landcover","type":"fill","source":"regional","source-layer":"landcover","paint":{"fill-color":"#dce5d0","fill-opacity":0.55}},
        {"id":"building","type":"fill","source":"regional","source-layer":"building","paint":{"fill-color":"#d2c8b9","fill-outline-color":"#b8aa98"}},
        {"id":"road-casing","type":"line","source":"regional","source-layer":"transportation","paint":{"line-color":"#c8bba8","line-width":["interpolate",["linear"],["zoom"],8,0.8,14,5],"line-opacity":0.65}},
        {"id":"roads","type":"line","source":"regional","source-layer":"transportation","paint":{"line-color":"#fffdf8","line-width":["interpolate",["linear"],["zoom"],8,0.5,14,4]}},
        {"id":"route-line-layer","type":"line","source":"route-line","paint":{"line-color":"#176B54","line-width":4,"line-opacity":0.9}},
        {"id":"route-points-layer","type":"circle","source":"route-points","paint":{"circle-radius":8,"circle-color":["get","color"],"circle-stroke-color":"#ffffff","circle-stroke-width":2}}
      ]
    }
""".trimIndent()
