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
      "glyphs": "asset://fonts/{fontstack}/{range}.pbf",
      "sources": {
        "regional": {
          "type": "vector",
          "url": "$source",
          "minzoom": 0,
          "maxzoom": 15,
          "attribution": "© OpenStreetMap contributors"
        },
        "route-points": {"type":"geojson","data":$routeGeoJson},
        "route-line": {"type":"geojson","data":$lineGeoJson}
      },
      "layers": [
        {"id":"background","type":"background","paint":{"background-color":"#F3F2EA"}},
        {"id":"landcover","type":"fill","source":"regional","source-layer":"landcover","paint":{"fill-color":"#DDE8D7","fill-opacity":0.85}},
        {"id":"landuse","type":"fill","source":"regional","source-layer":"landuse","paint":{"fill-color":"#E9EDE3","fill-opacity":0.72}},
        {"id":"water","type":"fill","source":"regional","source-layer":"water","paint":{"fill-color":"#CFE2D4"}},
        {"id":"building","type":"fill","source":"regional","source-layer":"building","minzoom":12,"paint":{"fill-color":"#FFFFFF","fill-outline-color":"#C8D4C8","fill-opacity":0.9}},
        {"id":"road-casing","type":"line","source":"regional","source-layer":"transportation","paint":{"line-color":"#AABBAA","line-width":["interpolate",["linear"],["zoom"],7,1.1,14,6],"line-opacity":0.9}},
        {"id":"roads","type":"line","source":"regional","source-layer":"transportation","paint":{"line-color":"#879B88","line-width":["interpolate",["linear"],["zoom"],7,0.45,14,3.2],"line-opacity":0.95}},
        {"id":"road-labels","type":"symbol","source":"regional","source-layer":"transportation_name","layout":{"symbol-placement":"line","text-field":["coalesce",["get","name:ru"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":["interpolate",["linear"],["zoom"],10,9,15,13],"text-max-angle":35},"paint":{"text-color":"#425647","text-halo-color":"#F3F2EA","text-halo-width":1.4}},
        {"id":"place-labels","type":"symbol","source":"regional","source-layer":"place","layout":{"text-field":["coalesce",["get","name:ru"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":["interpolate",["linear"],["zoom"],5,10,10,14,14,18],"text-max-width":9},"paint":{"text-color":"#26352C","text-halo-color":"#1A2420","text-halo-width":2}},
        {"id":"poi-labels","type":"symbol","source":"regional","source-layer":"poi","minzoom":13,"layout":{"text-field":["coalesce",["get","name:ru"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":11,"text-max-width":8},"paint":{"text-color":"#D5DED8","text-halo-color":"#1A2420","text-halo-width":1.5}},
        {"id":"water-labels","type":"symbol","source":"regional","source-layer":"water_name","minzoom":10,"layout":{"symbol-placement":"line","text-field":["coalesce",["get","name:ru"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":11},"paint":{"text-color":"#527A5B","text-halo-color":"#1A2420","text-halo-width":1.4}},
        {"id":"route-line-layer","type":"line","source":"route-line","paint":{"line-color":"#278747","line-width":5,"line-opacity":0.98,"line-cap":"round","line-join":"round"}},
        {"id":"route-points-layer","type":"circle","source":"route-points","paint":{"circle-radius":["interpolate",["linear"],["zoom"],8,6,14,11],"circle-color":["get","color"],"circle-stroke-color":"#FFFFFF","circle-stroke-width":2.5}}
      ]
    }
""".trimIndent()
