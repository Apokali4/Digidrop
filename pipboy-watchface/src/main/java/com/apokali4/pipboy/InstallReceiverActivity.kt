package com.apokali4.pipboy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.content.FileProvider
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

class InstallReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = TextView(this).apply {
            text = "PIP-BOY INSTALLER\n\nПолучаю файл с телефона…"
            textSize = 18f
            setTextColor(android.graphics.Color.rgb(155, 188, 15))
            setBackgroundColor(android.graphics.Color.rgb(16, 21, 13))
            gravity = android.view.Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        setContentView(message)

        lifecycleScope.launch {
            runCatching { receiveAndLaunchInstaller() }
                .onFailure {
                    message.text = "НЕ УДАЛОСЬ ПОЛУЧИТЬ APK\n\n${it.message ?: "Повтори отправку с телефона"}"
                }
        }
    }

    private suspend fun receiveAndLaunchInstaller() {
        val client = Wearable.getDataClient(this)
        var apkAsset: com.google.android.gms.wearable.Asset? = null
        repeat(20) {
            val items = client.getDataItems(Uri.parse("wear://*/watchfaces/install")).await()
            val item = items.firstOrNull()
            if (item != null) {
                apkAsset = DataMapItem.fromDataItem(item).dataMap.getAsset("apk")
                item.release()
            }
            items.release()
            if (apkAsset != null) return@repeat
            delay(500)
        }
        val asset = checkNotNull(apkAsset) { "Телефон ещё не передал файл. Нажми установку ещё раз." }
        val descriptor = client.getFdForAsset(asset).await()
        val apkFile = File(cacheDir, "pipboy-watchface.apk")
        FileOutputStream(apkFile).use { output ->
            File(descriptor.fileDescriptor).inputStream().use { input -> input.copyTo(output) }
        }
        descriptor.close()

        val uri = FileProvider.getUriForFile(this, "$packageName.files", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(installIntent)
    }
}
