package com.example.qrhelper

import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.material3.FloatingActionButton
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
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.json.JSONArray
import org.json.JSONObject
import com.example.qrhelper.domain.DeliveryPointSnapshot
import com.example.qrhelper.domain.DeliveryPointStatus
import com.example.qrhelper.domain.PointFilter
import com.example.qrhelper.domain.RouteProgress

private fun createNumberedPointMarker(number: Int, fillColor: Int): Bitmap {
    val size = 88
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val radius = size * 0.36f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 31f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawCircle(center, center, radius, fill)
    canvas.drawCircle(center, center, radius, outline)
    val baseline = center - (text.ascent() + text.descent()) / 2f
    canvas.drawText(number.toString(), center, baseline, text)
    return bitmap
}

@Composable
fun OfflineMapScreen(
    routeItems: List<RouteItem>,
    refreshKey: Int,
    onBack: () -> Unit,
    onImportMap: () -> Unit,
    onGeocodeMissing: () -> Unit,
    onOpenScanner: () -> Unit,
    onSelectPoint: (List<RouteItem>) -> Unit
) {
    val context = LocalContext.current
    val pointGroups = remember(routeItems) { routeItems.groupBy { PhotoRouteTextParser.canonicalAddressKey(it.address) }.values.map { it.toList() } }
    var selectedFilter by remember { mutableStateOf(PointFilter.ALL) }
    val pointSnapshots = remember(pointGroups) {
        pointGroups.mapIndexed { index, group ->
            val expected = group.sumOf { it.totalBoxes.coerceAtLeast(0) }
            val scanned = group.sumOf { it.scannedBoxes.coerceAtLeast(0) }
            val discrepancies = group.sumOf { it.discrepancyQty.coerceAtLeast(0) }
            val status = deliveryPointStatus(group)
            DeliveryPointSnapshot(
                id = PhotoRouteTextParser.canonicalAddressKey(group.first().address),
                sortOrder = index,
                status = status,
                expectedUnits = expected,
                scannedUnits = scanned,
                discrepancyUnits = discrepancies
            )
        }
    }
    val visibleIds = remember(pointSnapshots, selectedFilter) {
        RouteProgress.filter(pointSnapshots, selectedFilter).map { it.id }.toSet()
    }
    val visiblePointGroups = pointGroups.filter { group ->
        PhotoRouteTextParser.canonicalAddressKey(group.first().address) in visibleIds
    }
    val mappedGroups = visiblePointGroups.filter { group -> group.firstOrNull()?.let { it.latitude != null && it.longitude != null } == true }
    val lifecycleOwner = LocalLifecycleOwner.current
    var liveMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedPointId by remember { mutableStateOf<String?>(null) }
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
            TextButton(onClick = onBack) { Text("← Назад", color = MintAccent) }
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
            val missingCoordinates = visiblePointGroups.size - mappedCount
            Text(
                "Точек на карте: $mappedCount • без координат: $missingCoordinates",
                color = if (missingCoordinates > 0) EmeraldPrimary else TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            if (missingCoordinates > 0) {
                TextButton(onClick = onGeocodeMissing) {
                    Text("Определить координаты недостающих точек (нужен интернет)")
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    PointFilter.ALL to "Все",
                    PointFilter.ACTIVE to "Активные",
                    PointFilter.PROBLEMS to "Проблемы",
                    PointFilter.DELIVERED to "Сданные"
                ).forEach { (filter, label) ->
                    TextButton(onClick = { selectedFilter = filter }) {
                        Text(
                            label,
                            color = if (selectedFilter == filter) EmeraldPrimary else TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
            key(refreshKey, visiblePointGroups, activeMapFile.absolutePath) {
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
                            liveMap = map
                            val source = OfflineMapStore.mapLibreSource(activeMapFile)
                            val pointFeatures = JSONArray()
                            val routeCoordinates = JSONArray()
                            mappedGroups.forEach { group ->
                                val item = group.first()
                                val coordinates = JSONArray().put(item.longitude).put(item.latitude)
                                routeCoordinates.put(coordinates)
                                val markerStatus = when (deliveryPointStatus(group)) {
                                    DeliveryPointStatus.PROBLEM -> "Проблема" to "#356B43"
                                    DeliveryPointStatus.DELIVERED -> "Сдано" to "#278747"
                                    DeliveryPointStatus.IN_PROGRESS -> "В работе" to "#4D9B62"
                                    DeliveryPointStatus.PENDING -> "Ожидает" to "#79A96F"
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
                            val styleJson = buildOfflineMapStyleJson(
                                source = source,
                                routeGeoJson = routeGeoJson.toString(),
                                lineGeoJson = lineGeoJson.toString()
                            )
                            map.setStyle(Style.Builder().fromJson(styleJson)) {
                                val mappedCoordinates = mappedGroups.mapNotNull { group ->
                                    val point = group.first()
                                    if (point.latitude != null && point.longitude != null) LatLng(point.latitude!!, point.longitude!!) else null
                                }
                                if (mappedCoordinates.size >= 2) {
                                    val bounds = LatLngBounds.Builder().also { builder ->
                                        mappedCoordinates.forEach { coordinate -> builder.include(coordinate) }
                                    }.build()
                                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64))
                                } else {
                                    map.cameraPosition = CameraPosition.Builder()
                                        .target(mappedCoordinates.firstOrNull() ?: LatLng(54.5138, 36.2612))
                                        .zoom(12.0)
                                        .build()
                                }
                                mappedGroups.forEach { group ->
                                    val item = group.first()
                                    val markerNumber = pointGroups.indexOf(group) + 1
                                    val markerColor = when (deliveryPointStatus(group)) {
                                        DeliveryPointStatus.PROBLEM -> 0xFF1F6F39.toInt()
                                        DeliveryPointStatus.DELIVERED -> 0xFF278747.toInt()
                                        DeliveryPointStatus.IN_PROGRESS -> 0xFF4D9B62.toInt()
                                        DeliveryPointStatus.PENDING -> 0xFF79A96F.toInt()
                                    }
                                    val icon = IconFactory.getInstance(context)
                                        .fromBitmap(createNumberedPointMarker(markerNumber, markerColor))
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(item.latitude!!, item.longitude!!))
                                            .title(item.storeName)
                                            .snippet(markerNumber.toString())
                                            .icon(icon)
                                    )
                                }
                                map.setOnMarkerClickListener { marker ->
                                    val markerNumber = marker.snippet.toIntOrNull()
                                    val pointIndex = mapPointIndexFromMarkerNumber(markerNumber, pointGroups.size)
                                    val selectedGroup = pointIndex?.let { pointGroups.getOrNull(it) }
                                    if (selectedGroup != null) {
                                        onSelectPoint(selectedGroup)
                                        true
                                    } else false
                                }
                            }
                            map.addOnMapClickListener { tapped ->
                                val screenPoint = map.projection.toScreenLocation(tapped)
                                val feature = map.queryRenderedFeatures(screenPoint, "route-points-layer").firstOrNull()
                                val markerNumber = feature?.getNumberProperty("number")?.toInt()
                                val pointIndex = mapPointIndexFromMarkerNumber(markerNumber, pointGroups.size)
                                val selectedGroup = pointIndex?.let { pointGroups.getOrNull(it) }
                                if (selectedGroup != null) {
                                    onSelectPoint(selectedGroup)
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                        mapView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            FloatingActionButton(
                onClick = onOpenScanner,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = EmeraldPrimary,
                contentColor = Color.White
            ) {
                Text("⌗", fontSize = 25.sp)
            }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(112.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(visiblePointGroups) { index, group ->
                    val first = group.first()
                    val pointStatus = deliveryPointStatus(group)
                    val status = when (pointStatus) {
                        DeliveryPointStatus.PROBLEM -> "Проблема"
                        DeliveryPointStatus.DELIVERED -> "Сдано"
                        DeliveryPointStatus.IN_PROGRESS -> "В работе"
                        DeliveryPointStatus.PENDING -> "Ожидает"
                    }
                    val tint = when (pointStatus) {
                        DeliveryPointStatus.PROBLEM -> DigiDropPalette.primaryDark
                        DeliveryPointStatus.DELIVERED -> DigiDropPalette.primary
                        DeliveryPointStatus.IN_PROGRESS -> Color(0xFF4D9B62)
                        DeliveryPointStatus.PENDING -> Color(0xFF79A96F)
                    }
                    Card(
                        Modifier.width(220.dp).clickable {
                            selectedPointId = PhotoRouteTextParser.canonicalAddressKey(first.address)
                            val lat = first.latitude
                            val lon = first.longitude
                            if (lat != null && lon != null) {
                                liveMap?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15.0),
                                    650
                                )
                            } else {
                                onSelectPoint(group)
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = if (selectedPointId == PhotoRouteTextParser.canonicalAddressKey(first.address)) Color(0xFFE5F0E4) else CardSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${pointGroups.indexOf(group) + 1}. ${first.storeName}", color = TextLight, fontSize = 12.sp, maxLines = 1)
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
