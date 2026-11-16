package com.apokali4.watchfacemanager

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class PhoneBatterySyncWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val battery = applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return Result.retry()

        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return Result.retry()

        val percent = (level * 100 / scale).coerceIn(0, 100)
        val batteryRequest = PutDataMapRequest.create(PHONE_BATTERY_PATH).apply {
            dataMap.putInt(KEY_PERCENT, percent)
            dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        return try {
            Wearable.getDataClient(applicationContext).putDataItem(batteryRequest).await()
            // Weather is independent: a temporary forecast outage must not block battery sync.
            runCatching { syncWeather() }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun syncWeather() {
        val connection = URL(WEATHER_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(response).getJSONObject("current")
            val temperature = current.getDouble("temperature_2m").roundToInt()
            val code = current.getInt("weather_code")
            val condition = when (code) {
                0 -> "ЯСНО"
                1, 2 -> "ПЕРЕМЕННО"
                3 -> "ОБЛАЧНО"
                45, 48 -> "ТУМАН"
                51, 53, 55, 56, 57 -> "МОРОСЬ"
                61, 63, 65, 66, 67 -> "ДОЖДЬ"
                71, 73, 75, 77, 85, 86 -> "СНЕГ"
                80, 81, 82 -> "ЛИВЕНЬ"
                95, 96, 99 -> "ГРОЗА"
                else -> "ПОГОДА"
            }
            val temperatureText = if (temperature > 0) "+$temperature°" else "$temperature°"
            val weatherRequest = PutDataMapRequest.create(WEATHER_PATH).apply {
                dataMap.putString(KEY_WEATHER_TEXT, "$temperatureText $condition")
                dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(applicationContext).putDataItem(weatherRequest).await()
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val PHONE_BATTERY_PATH = "/pipboy/phone_battery"
        const val WEATHER_PATH = "/pipboy/weather"
        const val KEY_PERCENT = "percent"
        const val KEY_WEATHER_TEXT = "weather_text"
        const val KEY_UPDATED_AT = "updated_at"
        private const val WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=54.5293&longitude=36.2754&current=temperature_2m,weather_code&timezone=auto"
    }
}

object PhoneBatterySync {
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val periodic = PeriodicWorkRequestBuilder<PhoneBatterySyncWorker>(
            15, TimeUnit.MINUTES
        ).build()
        workManager.enqueueUniquePeriodicWork(
            "pipboy-phone-data-periodic",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
        val initial = OneTimeWorkRequestBuilder<PhoneBatterySyncWorker>().build()
        workManager.enqueueUniqueWork(
            "pipboy-phone-data-initial",
            ExistingWorkPolicy.KEEP,
            initial
        )
    }
}
