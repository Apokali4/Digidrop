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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qrhelper.data.ProductEntity
import kotlinx.coroutines.delay

@Composable
fun ProductCatalogScreen(
    products: List<ProductEntity>,
    onBack: () -> Unit,
    onSearch: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(250)
        onSearch(query)
    }
    Column(
        Modifier.fillMaxSize().background(BgDark).padding(18.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Главная", color = MintAccent) }
        Text("Каталог товаров", color = TextLight, fontSize = 25.sp)
        Text("Локальная база штрихкодов из загруженных накладных", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Название товара или штрихкод") }
        )
        Spacer(Modifier.height(10.dp))
        Text("Найдено товаров: ${products.size}", color = TextMuted, fontSize = 12.sp)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(products, key = { it.barcode }) { product ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(product.productName, color = TextLight, fontSize = 15.sp)
                        Text(product.barcode, color = MintAccent, fontSize = 12.sp)
                    }
                }
            }
            if (products.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank()) "Каталог пока пуст. Импортируйте накладную со штрихкодами."
                        else "Совпадений не найдено.",
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
            }
        }
    }
}
