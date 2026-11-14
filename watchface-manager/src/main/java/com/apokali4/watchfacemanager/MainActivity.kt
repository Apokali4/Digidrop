package com.apokali4.watchfacemanager

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.RemoteActivityHelper
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private val PipGreen = Color(0xFF9BBC0F)
private val PipDark = Color(0xFF10150D)
private val PipPanel = Color(0xFF20291A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WatchfaceVaultScreen() }
    }
}

@Composable
private fun WatchfaceVaultScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Подключи часы к телефону через Wear OS.") }
    var installingId by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = PipDark) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("WATCHFACE VAULT", color = PipGreen, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("Коллекция циферблатов для Wear OS", color = Color(0xFFB8C5A8), fontSize = 14.sp)

                WatchfaceCatalog.items.forEach { entry ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(PipPanel, RoundedCornerShape(22.dp))
                            .border(1.dp, PipGreen.copy(alpha = .7f), RoundedCornerShape(22.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (entry.id == "pip-boy-3000") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("PIP-BOY 3000", color = PipGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("08:42", color = PipGreen, fontSize = 50.sp, fontWeight = FontWeight.Black)
                                    Text("СТАТУС СИСТЕМЫ: ОК", color = PipGreen, fontSize = 10.sp)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(entry.subtitle, color = Color(0xFFB8C5A8), fontSize = 12.sp)
                            }
                            Text("ДЛЯ ЧАСОВ", color = PipGreen, fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    installingId = entry.id
                                    status = "Ищу часы и передаю ${entry.title}…"
                                    status = runCatching { installWatchface(context, entry) }
                                        .fold(
                                            onSuccess = { "Файл отправлен. Подтверди установку на экране часов." },
                                            onFailure = { "Не получилось: ${it.message ?: "проверь подключение часов"}" }
                                        )
                                    installingId = null
                                }
                            },
                            enabled = installingId == null,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PipGreen, contentColor = PipDark)
                        ) {
                            Text(
                                if (installingId == entry.id) "ПЕРЕДАЮ…" else "УСТАНОВИТЬ НА ЧАСЫ",
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Text(status, color = Color(0xFFB8C5A8), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("НОВЫЕ МОДЕЛИ БУДУТ ДОБАВЛЯТЬСЯ В ЭТОТ КАТАЛОГ", color = PipGreen.copy(alpha = .7f), fontSize = 10.sp)
            }
        }
    }
}

private suspend fun installWatchface(
    context: android.content.Context,
    entry: WatchfaceEntry
) {
    val nodes = Wearable.getNodeClient(context).connectedNodes.await()
    check(nodes.isNotEmpty()) { "часы не найдены. Проверь, что они сопряжены и подключены." }

    val apkBytes = withContext(Dispatchers.IO) {
        context.assets.open(entry.assetPath).use { it.readBytes() }
    }
    check(apkBytes.isNotEmpty()) { "APK ${entry.title} не найден внутри каталога." }

    val request = PutDataMapRequest.create("/watchfaces/install").apply {
        dataMap.putAsset("apk", Asset.createFromBytes(apkBytes))
        dataMap.putString("packageName", entry.packageName)
        dataMap.putLong("sentAt", System.currentTimeMillis())
    }.asPutDataRequest().setUrgent()

    Wearable.getDataClient(context).putDataItem(request).await()

    val launchIntent = Intent().setComponent(
        ComponentName(entry.packageName, "${entry.packageName}.InstallReceiverActivity")
    )
    val helper = RemoteActivityHelper(context)
    var launched = false
    var lastError: Exception? = null
    for (node in nodes) {
        try {
            helper.startRemoteActivity(launchIntent, node.id).await()
            launched = true
            break
        } catch (error: Exception) {
            lastError = error
        }
    }
    check(launched) { lastError?.message ?: "не удалось открыть установщик на часах" }
}
