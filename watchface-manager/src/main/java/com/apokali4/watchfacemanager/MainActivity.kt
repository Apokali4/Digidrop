package com.apokali4.watchfacemanager

import android.content.Context
import android.os.Bundle
import android.util.Base64
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbPrivateKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val PipGreen = Color(0xFF9BBC0F)
private val PipDark = Color(0xFF10150D)
private val PipPanel = Color(0xFF20291A)
private val Muted = Color(0xFFB8C5A8)
private const val PREFS = "watchface_vault_adb"
private const val KEY_HOST = "host"
private const val KEY_PORT = "port"
private const val KEY_PRIVATE_KEY = "private_key"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WatchfaceVaultScreen() }
    }
}

@Composable
private fun WatchfaceVaultScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(prefs.getString(KEY_HOST, "") ?: "") }
    var pairingPort by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf(prefs.getInt(KEY_PORT, 0).takeIf { it > 0 }?.toString() ?: "") }
    var pairingCode by remember { mutableStateOf("") }
    var paired by remember { mutableStateOf(prefs.getString(KEY_HOST, null) != null && prefs.getInt(KEY_PORT, 0) > 0) }
    var status by remember { mutableStateOf(if (paired) "Часы сохранены. Можно устанавливать циферблаты." else "Первичная настройка: включи «Беспроводную отладку» на часах.") }
    var busy by remember { mutableStateOf(false) }
    var installingId by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = PipDark) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("WATCHFACE VAULT", color = PipGreen, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("Циферблаты для Wear OS · Xiaomi Watch 2", color = Muted, fontSize = 13.sp)

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(PipPanel, RoundedCornerShape(20.dp))
                        .border(1.dp, PipGreen.copy(alpha = .65f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ПОДКЛЮЧЕНИЕ К ЧАСАМ", color = PipGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(if (paired) "СОХРАНЕНО" else "НЕ НАСТРОЕНО", color = if (paired) PipGreen else Muted, fontSize = 10.sp)
                    }
                    OutlinedTextField(
                        value = host, onValueChange = { host = it; paired = false },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("IP часов, например 192.168.1.50") },
                        colors = fieldColors()
                    )
                    OutlinedTextField(
                        value = connectPort, onValueChange = { connectPort = it; paired = false },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Порт отладки (Connect)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors()
                    )
                    if (!paired) {
                        OutlinedTextField(
                            value = pairingPort, onValueChange = { pairingPort = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("Порт сопряжения (Pair)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors()
                        )
                        OutlinedTextField(
                            value = pairingCode, onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("Код сопряжения из часов") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = fieldColors()
                        )
                        Text("На часах: Настройки → Система → Для разработчиков → Беспроводная отладка → Сопряжение с помощью кода. Телефон и часы должны быть в одной Wi‑Fi сети.", color = Muted, fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                status = "Сопрягаюсь с часами…"
                                status = runCatching {
                                    val normalizedHost = AdbEndpoint.normalizeHost(host)
                                    val pairPort = AdbEndpoint.parsePort(pairingPort)
                                    val adbPort = AdbEndpoint.parsePort(connectPort)
                                    require(pairingCode.length == 6) { "Код сопряжения должен состоять из 6 цифр." }
                                    withContext(Dispatchers.IO) {
                                        configureAdbIdentity(context)
                                        Kadb.pair(normalizedHost, pairPort, pairingCode)
                                        Kadb.create(normalizedHost, adbPort).use { adb ->
                                            check(adb.shell("echo watchface-vault").allOutput.contains("watchface-vault")) {
                                                "Часы ответили неожиданно."
                                            }
                                        }
                                    }
                                    prefs.edit().putString(KEY_HOST, normalizedHost).putInt(KEY_PORT, adbPort).apply()
                                    paired = true
                                    pairingCode = ""
                                    "Часы сопряжены и сохранены. Теперь установка в один тап."
                                }.getOrElse { "Не удалось подключиться: ${it.message ?: "проверь IP, порты и код"}" }
                                busy = false
                            }
                        },
                        enabled = !busy && !paired,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PipGreen, contentColor = PipDark)
                    ) { Text(if (busy) "СОПРЯЖЕНИЕ…" else "СОПРЯЧЬ ОДИН РАЗ", fontWeight = FontWeight.Black) }
                    if (paired) {
                        Button(
                            onClick = {
                                prefs.edit().remove(KEY_HOST).remove(KEY_PORT).apply()
                                paired = false
                                pairingPort = ""
                                pairingCode = ""
                                status = "Подключение сброшено. Выполни сопряжение заново."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF394331), contentColor = Muted)
                        ) { Text("СМЕНИТЬ / ПЕРЕПОДКЛЮЧИТЬ ЧАСЫ") }
                    }
                }

                WatchfaceCatalog.items.forEach { entry ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(PipPanel, RoundedCornerShape(22.dp))
                            .border(1.dp, PipGreen.copy(alpha = .7f), RoundedCornerShape(22.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (entry.id == "pip-boy-3000") {
                            Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("PIP-BOY 3000", color = PipGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("08:42", color = PipGreen, fontSize = 50.sp, fontWeight = FontWeight.Black)
                                    Text("СТАТУС СИСТЕМЫ: ОК", color = PipGreen, fontSize = 10.sp)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(entry.subtitle, color = Muted, fontSize = 12.sp)
                            }
                            Text("WEAR OS", color = PipGreen, fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    installingId = entry.id
                                    status = "Устанавливаю ${entry.title} на часы…"
                                    status = runCatching {
                                        val savedHost = prefs.getString(KEY_HOST, null) ?: error("Сначала сопряги часы.")
                                        val savedPort = prefs.getInt(KEY_PORT, 0).takeIf { it > 0 } ?: error("Не найден порт подключения.")
                                        installWatchface(context, savedHost, savedPort, entry)
                                        "${entry.title} успешно установлен. Теперь выбери его в списке циферблатов на часах."
                                    }.getOrElse { "Установка не удалась: ${it.message ?: "проверь соединение часов"}" }
                                    installingId = null
                                    busy = false
                                }
                            },
                            enabled = paired && !busy,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PipGreen, contentColor = PipDark, disabledContainerColor = Color(0xFF46513A))
                        ) {
                            Text(if (installingId == entry.id) "УСТАНАВЛИВАЮ…" else "УСТАНОВИТЬ НА ЧАСЫ", fontWeight = FontWeight.Black)
                        }
                    }
                }
                Text(status, color = Muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("НОВЫЕ ЦИФЕРБЛАТЫ БУДУТ ПОЯВЛЯТЬСЯ В КАТАЛОГЕ", color = PipGreen.copy(alpha = .7f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Muted,
    focusedBorderColor = PipGreen,
    unfocusedBorderColor = Muted.copy(alpha = .5f),
    focusedLabelColor = PipGreen,
    unfocusedLabelColor = Muted
)

private fun configureAdbIdentity(context: Context) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    KadbCert.configure(object : KadbPrivateKeyStore {
        override fun readPrivateKeyPem(): ByteArray? =
            prefs.getString(KEY_PRIVATE_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

        override fun writePrivateKeyPemAtomic(privateKeyPem: ByteArray) {
            check(prefs.edit().putString(KEY_PRIVATE_KEY, Base64.encodeToString(privateKeyPem, Base64.NO_WRAP)).commit()) {
                "Не удалось сохранить ключ ADB."
            }
        }

        override fun clear() {
            prefs.edit().remove(KEY_PRIVATE_KEY).commit()
        }
    })
    KadbCert.ensureReady()
}

private suspend fun installWatchface(
    context: Context,
    host: String,
    port: Int,
    entry: WatchfaceEntry
) = withContext(Dispatchers.IO) {
    configureAdbIdentity(context)
    val apkFile = File(context.cacheDir, "${entry.id}.apk")
    try {
        context.assets.open(entry.assetPath).use { input ->
            apkFile.outputStream().use { output -> input.copyTo(output) }
        }
        check(apkFile.length() > 0L) { "APK ${entry.title} пустой или не попал в каталог." }
        Kadb.create(host, port).use { adb -> adb.install(apkFile) }
    } finally {
        apkFile.delete()
    }
}
