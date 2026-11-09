package com.example.qrhelper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ProductCatalogScreen(
    products: List<ProductEntity>,
    onBack: () -> Unit,
    onSearch: (String) -> Unit
) {
    val palette = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(palette.background).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("← НАЗАД", modifier = Modifier.padding(vertical = 8.dp), color = palette.primary)
        Text("Каталог товаров", color = palette.onBackground, fontSize = 25.sp)
        Text("Локальная база штрихкодов из загруженных накладных",
            color = palette.onSurfaceVariant, fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products) { product ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.outline)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(product.productName, color = palette.onSurface, fontSize = 16.sp)
                        Text(product.barcode, color = palette.primary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
