package com.apokali4.pipboy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class InstallReceiverActivity : ComponentActivity() {
    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        message = TextView(this).apply {
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
        var apkAsset: Asset? = null
        for (attempt in 0 until 20) {
            val items = client.getDataItems(Uri.parse("wear://*/watchfaces/install")).await()
            try {
                val item = items.firstOrNull()
                if (item != null) {
                    apkAsset = DataMapItem.fromDataItem(item).dataMap.getAsset("apk")
                }
            } finally {
                items.release()
            }
            if (apkAsset != null) break
            delay(500)
        }
        val asset = checkNotNull(apkAsset) { "Телефон ещё не передал файл. Нажми установку ещё раз." }
        val descriptor = client.getFdForAsset(asset).await()
        val apkFile = File(cacheDir, "pipboy-watchface.apk")
        FileOutputStream(apkFile).use { output ->
            FileInputStream(descriptor.fileDescriptor).use { input -> input.copyTo(output) }
        }
        descriptor.close()

        if (!packageManager.canRequestPackageInstalls()) {
            message.text = "РАЗРЕШИ УСТАНОВКУ\n\nВключи разрешение для этого приложения. Затем вернись сюда и повтори отправку с телефона."
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(settingsIntent)
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.files", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(installIntent)
    }
}
