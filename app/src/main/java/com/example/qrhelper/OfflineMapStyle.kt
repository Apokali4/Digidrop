package com.example.qrhelper

/**
 * Offline MapLibre style for the bundled Kaluga PMTiles vector archive.
 * Keep geometry layers independent from delivery overlays so the route remains readable.
 */
internal fun buildOfflineMapStyleJson(
    source: String,
    routeGeoJson: String,
    lineGeoJson: String
): String = """
    {
      "version": 8,
      "name": "DigiDrop Industrial Offline",
      "sources": {
        "regional": {
          "type": "vector",
          "url": "$source",
          "encoding": "mvt",
          "minzoom": 0,
          "maxzoom": 15,
          "attribution": "© OpenStreetMap contributors"
        },
        "route-points": {"type":"geojson","data":$routeGeoJson},
        "route-line": {"type":"geojson","data":$lineGeoJson}
      },
      "layers": [
        {"id":"background","type":"background","paint":{"background-color":"#17211D"}},
        {"id":"landcover","type":"fill","source":"regional","source-layer":"landcover","paint":{"fill-color":"#263B2E","fill-opacity":0.85}},
        {"id":"landuse","type":"fill","source":"regional","source-layer":"landuse","paint":{"fill-color":"#2C3930","fill-opacity":0.72}},
        {"id":"water","type":"fill","source":"regional","source-layer":"water","paint":{"fill-color":"#244553"}},
        {"id":"building","type":"fill","source":"regional","source-layer":"building","minzoom":12,"paint":{"fill-color":"#46534B","fill-outline-color":"#59665E","fill-opacity":0.9}},
        {"id":"road-casing","type":"line","source":"regional","source-layer":"transportation","paint":{"line-color":"#111A16","line-width":["interpolate",["linear"],["zoom"],7,1.1,14,6],"line-opacity":0.9}},
        {"id":"roads","type":"line","source":"regional","source-layer":"transportation","paint":{"line-color":"#84948A","line-width":["interpolate",["linear"],["zoom"],7,0.45,14,3.2],"line-opacity":0.95}},
        {"id":"route-line-layer","type":"line","source":"route-line","paint":{"line-color":"#70E36F","line-width":5,"line-opacity":0.98,"line-cap":"round","line-join":"round"}},
        {"id":"route-points-layer","type":"circle","source":"route-points","paint":{"circle-radius":["interpolate",["linear"],["zoom"],8,6,14,11],"circle-color":["get","color"],"circle-stroke-color":"#F4F7F4","circle-stroke-width":2.5}}
      ]
    }
""".trimIndent()
