package com.apokali4.watchfacemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val StudioGreen = Color(0xFF9BBC0F)
private val StudioBg = Color(0xFF090D08)
private val StudioPanel = Color(0xFF182016)

@Composable
fun PipBoyStudioApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pipboy_studio", 0) }
    var design by remember { mutableStateOf(WatchfaceDesignStore.load(prefs)) }
    var selected by remember { mutableStateOf(FaceElementId.TIME) }
    var page by remember { mutableStateOf(0) }
    val selectedStyle = design.elements.getValue(selected)

    MaterialTheme {
        Surface(color = StudioBg) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("PIP-BOY STUDIO", color = StudioGreen, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("Редактор циферблатов Wear OS", color = Color.LightGray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { page = 0 }, colors = ButtonDefaults.buttonColors(containerColor = if (page == 0) StudioGreen else StudioPanel, contentColor = if (page == 0) Color.Black else StudioGreen)) { Text("РЕДАКТОР") }
                    Button(onClick = { page = 1 }, colors = ButtonDefaults.buttonColors(containerColor = if (page == 1) StudioGreen else StudioPanel, contentColor = if (page == 1) Color.Black else StudioGreen)) { Text("УСТАНОВКА") }
                }
                if (page == 0) {
                    Text("ПРЕДПРОСМОТР · ПЕРЕТАСКИВАЙ ЭЛЕМЕНТЫ", color = StudioGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    WatchfacePreview(
                        design = design,
                        selected = selected,
                        onSelect = { selected = it },
                        onMove = { id, x, y -> design = design.updateElement(id, design.elements.getValue(id).copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f))) }
                    )
                    Text("ЭЛЕМЕНТЫ", color = StudioGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(FaceElementId.TIME, FaceElementId.DATE, FaceElementId.VAULT_BOY).forEach { id ->
                            ElementChip(id, selected == id) { selected = id }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(FaceElementId.WEATHER, FaceElementId.HEART_RATE, FaceElementId.STEPS).forEach { id ->
                            ElementChip(id, selected == id) { selected = id }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(FaceElementId.CALORIES, FaceElementId.WATCH_BATTERY, FaceElementId.PHONE_BATTERY).forEach { id ->
                            ElementChip(id, selected == id) { selected = id }
                        }
                    }
                    Column(Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("НАСТРОЙКА: ${selected.title.uppercase()}", color = StudioGreen, fontWeight = FontWeight.Bold)
                        Text("Размер: ${(selectedStyle.scale * 100).roundToInt()}%", color = Color.White, fontSize = 12.sp)
                        Slider(value = selectedStyle.scale, onValueChange = { v -> design = design.updateElement(selected, selectedStyle.copy(scale = v)) }, valueRange = 0.5f..2.5f)
                        Text("Горизонталь: ${(selectedStyle.x * 100).roundToInt()}%", color = Color.White, fontSize = 12.sp)
                        Slider(value = selectedStyle.x, onValueChange = { v -> design = design.updateElement(selected, selectedStyle.copy(x = v)) })
                        Text("Вертикаль: ${(selectedStyle.y * 100).roundToInt()}%", color = Color.White, fontSize = 12.sp)
                        Slider(value = selectedStyle.y, onValueChange = { v -> design = design.updateElement(selected, selectedStyle.copy(y = v)) })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Показывать элемент", color = Color.White, modifier = Modifier.weight(1f))
                            Switch(checked = selectedStyle.visible, onCheckedChange = { v -> design = design.updateElement(selected, selectedStyle.copy(visible = v)) })
                        }
                    }
                    Text("ЦВЕТОВАЯ ТЕМА", color = StudioGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PipBoyTheme.entries.forEach { theme ->
                            Button(onClick = { design = design.withTheme(theme) }, colors = ButtonDefaults.buttonColors(containerColor = Color(theme.colorArgb), contentColor = Color.Black)) {
                                Text(theme.name)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { design = WatchfaceDesign.default(); WatchfaceDesignStore.save(prefs, design) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = StudioPanel, contentColor = StudioGreen)) { Text("СБРОСИТЬ") }
                        Button(onClick = { WatchfaceDesignStore.save(prefs, design) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = StudioGreen, contentColor = Color.Black)) { Text("СОХРАНИТЬ") }
                    }
                    Text("Настройки сохраняются в профиле телефона. Передача макета в сам WFF-циферблат подключается следующим этапом.", color = Color.LightGray, fontSize = 11.sp)
                } else {
                    WatchfaceVaultScreen()
                }
            }
        }
    }
}

@Composable
private fun ElementChip(id: FaceElementId, active: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = if (active) StudioGreen else StudioPanel, contentColor = if (active) Color.Black else Color.White), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
        Text(id.title, fontSize = 10.sp)
    }
}

@Composable
private fun WatchfacePreview(
    design: WatchfaceDesign,
    selected: FaceElementId,
    onSelect: (FaceElementId) -> Unit,
    onMove: (FaceElementId, Float, Float) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(28.dp))
            .background(Color.Black)
            .border(2.dp, Color(design.theme.colorArgb), RoundedCornerShape(28.dp))
            .pointerInput(selected, design.elements) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val current = design.elements.getValue(selected)
                    onMove(selected, current.x + drag.x / size.width, current.y + drag.y / size.height)
                }
            }
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        Text("PIP-BOY 3000", color = Color(design.theme.colorArgb), fontSize = 12.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
        design.elements.forEach { (id, style) ->
            if (style.visible) {
                val label = when (id) {
                    FaceElementId.TIME -> "20:22"
                    FaceElementId.DATE -> "16 NOV 2026"
                    FaceElementId.WEATHER -> "+8° ОБЛАЧНО"
                    FaceElementId.HEART_RATE -> "♥ 72"
                    FaceElementId.STEPS -> "STEPS 6420"
                    FaceElementId.CALORIES -> "KCAL 380"
                    FaceElementId.WATCH_BATTERY -> "WATCH 88%"
                    FaceElementId.PHONE_BATTERY -> "PHONE 44%"
                    FaceElementId.VAULT_BOY -> "VAULT BOY"
                }
                Text(
                    label,
                    color = Color(style.colorArgb),
                    fontSize = (if (id == FaceElementId.TIME) 27f else 11f * style.scale).sp,
                    fontWeight = if (id == FaceElementId.TIME || id == FaceElementId.VAULT_BOY) FontWeight.Black else FontWeight.Medium,
                    modifier = Modifier.offset { IntOffset((style.x * w).roundToInt() - 36, (style.y * h).roundToInt() - 12) }
                        .background(if (id == selected) Color(0x5539FF14) else Color.Transparent, RoundedCornerShape(4.dp))
                        .border(if (id == selected) 1.dp else 0.dp, if (id == selected) StudioGreen else Color.Transparent)
                        .padding(3.dp)
                        .pointerInput(id) { detectDragGestures(onDragStart = { onSelect(id) }) { change, drag -> change.consume(); onMove(id, style.x + drag.x / w, style.y + drag.y / h) } }
                )
            }
        }
    }
}
