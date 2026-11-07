package com.example.qrhelper

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun OfflineMapScreen(
    routeItems: List<RouteItem>,
    refreshKey: Int,
    onBack: () -> Unit,
    onImportMap: () -> Unit,
    onGeocodeMissing: () -> Unit,
    onSelectPoint: (List<RouteItem>) -> Unit
) {
    val context = LocalContext.current
    val pointGroups = remember(routeItems) { routeItems.groupBy { it.storeName to it.address }.values.map { it.toList() } }
    val mappedGroups = pointGroups.filter { group -> group.firstOrNull()?.let { it.latitude != null && it.longitude != null } == true }
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapFile by remember { mutableStateOf(OfflineMapStore.installedMap(context)) }
    LaunchedEffect(refreshKey) {
        mapFile = OfflineMapStore.installBundledMap(context)
    }

    Column(
        Modifier.fillMaxSize().background(BgDark).padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Назад", color = TextLight) }
            Text("Карта Калужской области", color = TextLight, fontSize = 18.sp)
            TextButton(onClick = onImportMap) { Text("Импорт", color = EmeraldPrimary) }
        }

        if (mapFile == null) {
            Card(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Офлайн-карта ещё не установлена", color = TextLight, fontSize = 20.sp)
                    Text(
                        "Выберите файл kaluga-region.pmtiles. После импорта карта будет храниться в памяти приложения и открываться без мобильного интернета.",
                        color = TextMuted
                    )
                    Button(onClick = onImportMap) { Text("Выбрать файл PMTiles") }
                }
            }
        } else {
            val activeMapFile = mapFile!!
            Text(
                "Карта установлена • ${activeMapFile.length() / (1024 * 1024)} МБ",
                color = EmeraldPrimary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            val mappedCount = mappedGroups.size
            val missingCoordinates = pointGroups.size - mappedCount
            Text(
                "Точек на карте: $mappedCount • без координат: $missingCoordinates",
                color = if (missingCoordinates > 0) Color(0xFF9A5A18) else TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            if (missingCoordinates > 0) {
                TextButton(onClick = onGeocodeMissing) {
                    Text("Определить координаты недостающих точек (нужен интернет)")
                }
            }
            key(refreshKey, pointGroups, activeMapFile.absolutePath) {
                val mapView = androidx.compose.runtime.remember {
                    MapLibre.getInstance(context)
                    MapView(context).apply { onCreate(null) }
                }
                DisposableEffect(mapView, lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> mapView.onStart()
                            Lifecycle.Event.ON_RESUME -> mapView.onResume()
                            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                            Lifecycle.Event.ON_STOP -> mapView.onStop()
                            Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        mapView.onDestroy()
                    }
                }
                AndroidView(
                    factory = {
                        mapView.getMapAsync { map ->
                            val source = OfflineMapStore.mapLibreSource(activeMapFile)
                            val pointFeatures = JSONArray()
                            val routeCoordinates = JSONArray()
                            mappedGroups.forEach { group ->
                                val item = group.first()
                                val coordinates = JSONArray().put(item.longitude).put(item.latitude)
                                routeCoordinates.put(coordinates)
                                val markerStatus = when {
                                    group.any { it.discrepancyQty > 0 } -> "Проблема" to "#C62828"
                                    group.all { it.isDelivered } -> "Сдано" to "#27AE60"
                                    group.any { it.scannedBoxes > 0 || it.isDelivered } -> "В работе" to "#2878C8"
                                    else -> "Ожидает" to "#E5A000"
                                }
                                val properties = JSONObject()
                                    .put("number", pointGroups.indexOf(group) + 1)
                                    .put("name", item.storeName)
                                    .put("address", item.address)
                                    .put("status", markerStatus.first)
                                    .put("color", markerStatus.second)
                                pointFeatures.put(JSONObject().put("type", "Feature")
                                    .put("geometry", JSONObject().put("type", "Point").put("coordinates", coordinates))
                                    .put("properties", properties))
                            }
                            val routeGeoJson = JSONObject().put("type", "FeatureCollection").put("features", pointFeatures)
                            val lineFeatures = JSONArray()
                            if (routeCoordinates.length() >= 2) {
                                val lineFeature = JSONObject().put("type", "Feature")
                                    .put("geometry", JSONObject().put("type", "LineString").put("coordinates", routeCoordinates))
                                    .put("properties", JSONObject())
                                lineFeatures.put(lineFeature)
                            }
                            val lineGeoJson = JSONObject().put("type", "FeatureCollection").put("features", lineFeatures)
                            val styleJson = """
                                {
                                  "version": 8,
                                  "name": "DigiDrop Offline",
                                  "sources": {
                                    "regional": {"type":"vector","url":"$source","minzoom":0,"maxzoom":14},
                                    "route-points": {"type":"geojson","data":${routeGeoJson}},
                                    "route-line": {"type":"geojson","data":${lineGeoJson}}
                                  },
                                  "layers": [
                                    {"id":"background","type":"background","paint":{"background-color":"#eee9df"}},
                                    {"id":"water","type":"fill","source":"regional","source-layer":"water","paint":{"fill-color":"#a9cbd5"}},
                                    {"id":"landcover","type":"fill","source":"regional","source-layer":"landcover","paint":{"fill-color":"#dce5d0","fill-opacity":0.55}},
                                    {"id":"building","type":"fill","source":"regional","source-layer":"building","paint":{"fill-color":"#d2c8b9","fill-outline-color":"#b8aa98"}},
                                    {"id":"road-casing","type":"line","source":"regional","source-layer":"transportation","paint":{"line-color":"#c8bba8","line-width":["interpolate",["linear"],["zoom"],8,0.8,14,5],"line-opacity":0.65}},
                                    {"id":"roads","type":"line","source":"regional","source-layer":"transportation","paint":{"line-color":"#fffdf8","line-width":["interpolate",["linear"],["zoom"],8,0.5,14,4]}},
                                    {"id":"route-line-layer","type":"line","source":"route-line","paint":{"line-color":"#176B54","line-width":4,"line-opacity":0.9}},
                                    {"id":"route-points-layer","type":"circle","source":"route-points","paint":{"circle-radius":8,"circle-color":["get","color"],"circle-stroke-color":"#ffffff","circle-stroke-width":2}},
                                    {"id":"route-number-layer","type":"symbol","source":"route-points","layout":{"text-field":["to-string",["get","number"]],"text-size":11,"text-allow-overlap":true},"paint":{"text-color":"#ffffff"}}
                                  ]
                                }
                            """.trimIndent()
                            map.setStyle(Style.Builder().fromJson(styleJson)) {
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(54.5138, 36.2612))
                                    .zoom(10.0)
                                    .build()
                            }
                        }
                        mapView
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(92.dp).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pointGroups) { index, group ->
                    val first = group.first()
                    val status = when {
                        group.any { it.discrepancyQty > 0 } -> "Проблема"
                        group.all { it.isDelivered } -> "Сдано"
                        group.any { it.scannedBoxes > 0 || it.isDelivered } -> "В работе"
                        else -> "Ожидает"
                    }
                    val tint = when (status) {
                        "Проблема" -> Color(0xFFC62828)
                        "Сдано" -> Color(0xFF27AE60)
                        "В работе" -> Color(0xFF2878C8)
                        else -> Color(0xFFE5A000)
                    }
                    Card(
                        Modifier.width(190.dp).clickable { onSelectPoint(group) },
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${index + 1}. ${first.storeName}", color = TextLight, fontSize = 12.sp, maxLines = 1)
                            Text(first.address, color = TextMuted, fontSize = 10.sp, maxLines = 1)
                            Text(status, color = tint, fontSize = 11.sp)
                        }
                    }
                }
            }
            Text(
                "Данные карты: © OpenStreetMap contributors • лицензия ODbL",
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}
