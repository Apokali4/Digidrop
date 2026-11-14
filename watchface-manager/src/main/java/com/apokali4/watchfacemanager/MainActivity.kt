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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    var status by remember { mutableStateOf("Часы должны быть подключены к телефону через Wear OS.") }
    var installing by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = PipDark) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("WATCHFACE VAULT", color = PipGreen, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("Коллекция циферблатов для Wear OS", color = Color(0xFFB8C5A8), fontSize = 14.sp)
                Box(
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                        .background(PipPanel, RoundedCornerShape(22.dp))
                        .border(1.dp, PipGreen.copy(alpha = .7f), RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PIP-BOY 3000", color = PipGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(18.dp))
                        Text("08:42", color = PipGreen, fontSize = 54.sp, fontWeight = FontWeight.Black)
                        Text("СТАТУС СИСТЕМЫ: ОК", color = PipGreen, fontSize = 11.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pip-Boy 3000", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Wear OS 4 · Xiaomi Watch 2", color = Color(0xFFB8C5A8), fontSize = 12.sp)
                    }
                    Text("v1.0", color = PipGreen, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        scope.launch {
                            installing = true
                            status = "Ищу подключённые часы…"
                            status = runCatching { installPipBoy(context) }
                                .fold(
                                    onSuccess = { "Файл отправлен на часы. Подтверди установку на экране часов." },
                                    onFailure = { "Не получилось: ${it.message ?: "проверь подключение часов"}" }
                                )
                            installing = false
                        }
                    },
                    enabled = !installing,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PipGreen, contentColor = PipDark)
                ) {
                    Text(if (installing) "ПЕРЕДАЮ…" else "УСТАНОВИТЬ НА ЧАСЫ", fontWeight = FontWeight.Black)
                }
                Text(status, color = Color(0xFFB8C5A8), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("НОВЫЕ ЦИФЕРБЛАТЫ БУДУТ ПОЯВЛЯТЬСЯ ЗДЕСЬ", color = PipGreen.copy(alpha = .7f), fontSize = 10.sp)
            }
        }
    }
}

private suspend fun installPipBoy(context: android.content.Context) {
    val nodes = Wearable.getNodeClient(context).connectedNodes.await()
    check(nodes.isNotEmpty()) { "часы не найдены. Проверь, что они сопряжены и подключены." }

    val apkBytes = withContext(Dispatchers.IO) {
        context.assets.open("watchfaces/pipboy-watchface.apk").use { it.readBytes() }
    }
    check(apkBytes.isNotEmpty()) { "APK циферблата не найден внутри каталога." }

    val request = PutDataMapRequest.create("/watchfaces/install").apply {
        dataMap.putAsset("apk", Asset.createFromBytes(apkBytes))
        dataMap.putString("packageName", "com.apokali4.pipboy")
        dataMap.putLong("sentAt", System.currentTimeMillis())
    }.asPutDataRequest().setUrgent()

    Wearable.getDataClient(context).putDataItem(request).await()

    val launchIntent = Intent().setComponent(
        ComponentName("com.apokali4.pipboy", "com.apokali4.pipboy.InstallReceiverActivity")
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
