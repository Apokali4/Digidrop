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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        cashNote = first?.cashNote.orEmpty()
        note = first?.note.orEmpty()
        extraComment = first?.extraComment.orEmpty()
        barcode = ""
        itemName = ""
        quantity = "1"
        validationError = ""
    }

    Column(
        Modifier.fillMaxSize().background(BgDark).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← Назад", color = MintAccent) }
            Button(onClick = { onSave(groups.flatten()) }) { Text("Сохранить маршрут") }
        }
        Text("Редактор маршрута", color = TextLight, fontSize = 24.sp)
        Text(
            "Точек: ${groups.size} • строк груза: ${groups.sumOf { it.size }}. Изменения применятся после сохранения.",
            color = TextMuted,
            fontSize = 12.sp
        )
        Button(onClick = { openEditor(null) }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Добавить точку")
        }
        if (groups.isEmpty()) {
            Text("Маршрут пуст. Добавь первую точку или вернись к импорту накладной.", color = TextMuted)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(groups, key = { _, group -> group.first().storeName + "|" + group.first().address }) { index, group ->
                    val first = group.first()
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${index + 1}. ${first.storeName}", color = TextLight, fontSize = 16.sp)
                            Text(first.address, color = TextMuted, fontSize = 13.sp)
                            Text("${group.size} строк • мест: ${group.sumOf { it.totalBoxes }} • сканов: ${group.sumOf { it.scannedBoxes }}",
                                color = MintAccent, fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { openEditor(group) }) { Text("Изменить") }
                                TextButton(onClick = { deletingGroup = group }) { Text("Удалить") }
                                Spacer(Modifier.weight(1f))
                                TextButton(enabled = index > 0, onClick = {
                                    groups = groupRouteItems(moveRoutePointGroup(groups.flatten(), index, index - 1))
                                }) { Text("↑") }
                                TextButton(enabled = index < groups.lastIndex, onClick = {
                                    groups = groupRouteItems(moveRoutePointGroup(groups.flatten(), index, index + 1))
                                }) { Text("↓") }
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
                    OutlinedTextField(workingHours, { workingHours = it }, label = { Text("График работы") }, singleLine = true)
                    OutlinedTextField(cashNote, { cashNote = it }, label = { Text("Наличные / документы") })
                    OutlinedTextField(note, { note = it }, label = { Text("Инструкция к заезду") })
                    OutlinedTextField(extraComment, { extraComment = it }, label = { Text("Дополнительный комментарий") })
                    if (adding) {
                        Text("Груз для новой точки", color = MintAccent, fontSize = 13.sp)
                        OutlinedTextField(barcode, { barcode = it }, label = { Text("Штрихкод (необязательно)") }, singleLine = true)
                        OutlinedTextField(itemName, { itemName = it }, label = { Text("Название товара") }, singleLine = true)
                        OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(6) }, label = { Text("Количество мест") }, singleLine = true)
                    }
                    if (validationError.isNotBlank()) {
                        Text(validationError, color = androidx.compose.ui.graphics.Color(0xFFB3261E), fontSize = 12.sp)
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
