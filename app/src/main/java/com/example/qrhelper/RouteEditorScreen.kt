package com.example.qrhelper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RouteEditorScreen(
    routeItems: List<RouteItem>,
    onBack: () -> Unit,
    onSave: (List<RouteItem>) -> Unit
) {
    var groups by remember(routeItems) { mutableStateOf(groupRouteItems(routeItems)) }
    var editingGroup by remember { mutableStateOf<List<RouteItem>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deletingGroup by remember { mutableStateOf<List<RouteItem>?>(null) }

    var storeName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var workingHours by remember { mutableStateOf("") }
    var timeSlot by remember { mutableStateOf("") }
    var cashNote by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var extraComment by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var validationError by remember { mutableStateOf("") }

    fun openEditor(group: List<RouteItem>?) {
        editingGroup = group
        adding = group == null
        val first = group?.firstOrNull()
        storeName = first?.storeName.orEmpty()
        address = first?.address.orEmpty()
        phone = first?.phone.orEmpty()
        ownerName = first?.ownerName.orEmpty()
        workingHours = first?.workingHours.orEmpty()
        timeSlot = ""
        cashNote = first?.cashNote.orEmpty()
        note = first?.note.orEmpty()
        extraComment = first?.extraComment.orEmpty()
        barcode = ""
        itemName = ""
        quantity = "1"
        validationError = ""
    }

    val palette = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(palette.background).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                onClick = onBack, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) { Text("← НАЗАД", Modifier.padding(16.dp), color = palette.onSurface) }
            Card(
                onClick = { onSave(groups.flatten()) }, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
            ) { Text("СОХРАНИТЬ", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onPrimary) }
        }
        Text("Редактор маршрута", color = palette.onSurface, fontSize = 24.sp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
        ) {
            Text("ТОЧЕК: ${groups.size}   •   СТРОК ГРУЗА: ${groups.sumOf { it.size }}",
                Modifier.padding(14.dp), color = palette.onSurfaceVariant)
        }
        Card(
            onClick = { openEditor(null) }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.primary),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
        ) {
            Text("+ ДОБАВИТЬ ТОЧКУ", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onPrimary)
        }
        if (groups.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant)) {
                Text("Маршрут пуст. Добавь первую точку.", Modifier.padding(16.dp), color = palette.onSurfaceVariant)
            }
        } else {
            val listState = rememberLazyListState()
            var draggedIndex by remember { mutableStateOf<Int?>(null) }
            var dragDelta by remember { mutableStateOf(0f) }
            Text("Зажми плитку и перетащи для изменения порядка.", color = palette.onSurfaceVariant, fontSize = 12.sp)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(groups, key = { _, group -> group.first().storeName + "|" + group.first().address }) { index, group ->
                    val first = group.first()
                    val currentIndex by rememberUpdatedState(index)
                    Card(
                        Modifier.fillMaxWidth().pointerInput(first.storeName, first.address) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedIndex = currentIndex; dragDelta = 0f },
                                onDragEnd = { draggedIndex = null; dragDelta = 0f },
                                onDragCancel = { draggedIndex = null; dragDelta = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val from = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                    dragDelta += amount.y
                                    val source = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from }
                                        ?: return@detectDragGesturesAfterLongPress
                                    val center = source.offset + source.size / 2 + dragDelta
                                    val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                        info.index != from && center >= info.offset && center <= info.offset + info.size
                                    }
                                    if (target != null) {
                                        groups = groupRouteItems(moveRoutePointGroup(groups.flatten(), from, target.index))
                                        draggedIndex = target.index
                                        dragDelta = 0f
                                    }
                                }
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surface),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, palette.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ТОЧКА ${index + 1}  •  ⠿ ПЕРЕТАЩИТЬ", color = palette.primary, fontSize = 12.sp)
                            Text(first.storeName.ifBlank { "Магазин не указан" }, color = palette.onSurface, fontSize = 19.sp)
                            Text(first.address, color = palette.onSurfaceVariant, fontSize = 14.sp)
                            if (first.workingHours.isNotBlank()) {
                                Text("График магазина: ${first.workingHours}", color = palette.onSurfaceVariant, fontSize = 12.sp)
                            }
                            val deliveryWindows = group.map { it.timeSlot }.filter(String::isNotBlank).distinct()
                            if (deliveryWindows.isNotEmpty()) {
                                Text("Окна доставки: ${deliveryWindows.joinToString(" • ")}", color = palette.primary, fontSize = 12.sp)
                            }
                            Text("${group.size} строк груза • мест: ${group.sumOf { it.totalBoxes }} • сканов: ${group.sumOf { it.scannedBoxes }}",
                                color = palette.onSurfaceVariant, fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Card(
                                    onClick = { openEditor(group) }, modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = palette.secondaryContainer),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
                                ) { Text("ИЗМЕНИТЬ", Modifier.padding(12.dp), color = palette.primary) }
                                Card(
                                    onClick = { deletingGroup = group }, modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
                                ) { Text("УДАЛИТЬ", Modifier.padding(12.dp), color = palette.onSurface) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding || editingGroup != null) {
        AlertDialog(
            onDismissRequest = { adding = false; editingGroup = null },
            title = { Text(if (adding) "Новая точка" else "Данные точки") },
            text = {
                Column(
                    Modifier.fillMaxWidth().height(480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    OutlinedTextField(storeName, { storeName = it }, label = { Text("Магазин / получатель") }, singleLine = true)
                    OutlinedTextField(address, { address = it }, label = { Text("Адрес") })
                    OutlinedTextField(phone, { phone = it }, label = { Text("Телефон") }, singleLine = true)
                    OutlinedTextField(ownerName, { ownerName = it }, label = { Text("ИП / контактное лицо") }, singleLine = true)
                    OutlinedTextField(workingHours, { workingHours = it }, label = { Text("График работы магазина") }, singleLine = true)
                    if (adding) {
                        OutlinedTextField(timeSlot, { timeSlot = it }, label = { Text("Окно доставки") }, singleLine = true)
                    } else {
                        val existingWindows = editingGroup.orEmpty().mapIndexedNotNull { index, item ->
                            item.timeSlot.takeIf(String::isNotBlank)?.let { "Строка ${index + 1}: $it" }
                        }
                        if (existingWindows.isNotEmpty()) {
                            Text(
                                "Окна доставки по отдельным строкам груза:\n${existingWindows.joinToString("\n")}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    OutlinedTextField(cashNote, { cashNote = it }, label = { Text("Наличные / документы") })
                    OutlinedTextField(note, { note = it }, label = { Text("Инструкция к заезду") })
                    OutlinedTextField(extraComment, { extraComment = it }, label = { Text("Дополнительный комментарий") })
                    if (adding) {
                        Text("Груз для новой точки", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        OutlinedTextField(barcode, { barcode = it }, label = { Text("Штрихкод (необязательно)") }, singleLine = true)
                        OutlinedTextField(itemName, { itemName = it }, label = { Text("Название товара") }, singleLine = true)
                        OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(6) }, label = { Text("Количество мест") }, singleLine = true)
                    }
                    if (validationError.isNotBlank()) {
                        Text(validationError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (storeName.isBlank() || address.isBlank()) {
                        validationError = "Укажи название точки и адрес"
                    } else if (adding && (itemName.isBlank() || quantity.toIntOrNull() == null || quantity.toIntOrNull()!! < 0)) {
                        validationError = "Укажи товар и корректное количество мест"
                    } else {
                        val current = groups.flatten()
                        if (adding) {
                            val first = RouteItem(
                                barcode = barcode.trim().ifBlank { "MANUAL-${System.currentTimeMillis()}" },
                                storeName = storeName.trim(),
                                address = address.trim(),
                                itemName = itemName.trim(),
                                totalBoxes = quantity.toInt(),
                                phone = phone.trim(),
                                note = note.trim(),
                                ownerName = ownerName.trim(),
                                workingHours = workingHours.trim(),
                                timeSlot = timeSlot.trim(),
                                cashNote = cashNote.trim(),
                                extraComment = extraComment.trim()
                            )
                            groups = groupRouteItems(current + first)
                        } else {
                            val old = editingGroup!!.first()
                            val coordinatesUnchanged = old.address.trim() == address.trim()
                            groups = groupRouteItems(
                                updateRoutePointDetails(
                                    current,
                                    old.storeName,
                                    old.address,
                                    RoutePointDetails(
                                        storeName = storeName,
                                        address = address,
                                        phone = phone,
                                        ownerName = ownerName,
                                        workingHours = workingHours,
                                        cashNote = cashNote,
                                        note = note,
                                        extraComment = extraComment,
                                        latitude = if (coordinatesUnchanged) old.latitude else null,
                                        longitude = if (coordinatesUnchanged) old.longitude else null
                                    )
                                )
                            )
                        }
                        adding = false
                        editingGroup = null
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { adding = false; editingGroup = null }) { Text("Отмена") }
            }
        )
    }

    deletingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text("Удалить точку?") },
            text = { Text("Точка «${group.first().storeName}» и все её строки груза будут удалены из текущего маршрута.") },
            confirmButton = {
                TextButton(onClick = {
                    groups = groupRouteItems(removeRoutePointGroup(groups.flatten(), group.first().storeName, group.first().address))
                    deletingGroup = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deletingGroup = null }) { Text("Отмена") } }
        )
    }
}
