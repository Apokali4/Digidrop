package com.example.qrhelper

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun OfflineMapScreen(
    refreshKey: Int,
    onBack: () -> Unit,
    onImportMap: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapFile = OfflineMapStore.installedMap(context)

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
            Text(
                "Карта установлена • ${mapFile.length() / (1024 * 1024)} МБ",
                color = EmeraldPrimary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            key(refreshKey, mapFile.absolutePath) {
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
                            val source = OfflineMapStore.mapLibreSource(mapFile)
                            val styleJson = """
                                {
                                  "version": 8,
                                  "name": "DigiDrop Offline",
                                  "sources": {
                                    "regional": {
                                      "type": "vector",
                                      "url": "$source",
                                      "minzoom": 0,
                                      "maxzoom": 14
                                    }
                                  },
                                  "layers": [
                                    {"id":"background","type":"background","paint":{"background-color":"#eee9df"}},
                                    {"id":"water","type":"fill","source":"regional","source-layer":"water","paint":{"fill-color":"#a9cbd5"}},
                                    {"id":"landcover","type":"fill","source":"regional","source-layer":"landcover","paint":{"fill-color":"#dce5d0","fill-opacity":0.55}},
                                    {"id":"building","type":"fill","source":"regional","source-layer":"building","paint":{"fill-color":"#d2c8b9","fill-outline-color":"#b8aa98"}},
                                    {"id":"roads","type":"line","source":"regional","source-layer":"transportation","paint":{"line-color":"#fffdf8","line-width":["interpolate",["linear"],["zoom"],8,0.5,14,4]}},
                                    {"id":"road-casing","type":"line","source":"regional","source-layer":"transportation","paint":{"line-color":"#c8bba8","line-width":["interpolate",["linear"],["zoom"],8,0.8,14,5],"line-opacity":0.65}}
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
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}
