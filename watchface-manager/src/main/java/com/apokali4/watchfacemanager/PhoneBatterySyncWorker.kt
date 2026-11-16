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
import java.util.concurrent.TimeUnit

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
        val request = PutDataMapRequest.create(PHONE_BATTERY_PATH).apply {
            dataMap.putInt(KEY_PERCENT, percent)
            dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        return try {
            Wearable.getDataClient(applicationContext).putDataItem(request).await()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val PHONE_BATTERY_PATH = "/pipboy/phone_battery"
        const val KEY_PERCENT = "percent"
        const val KEY_UPDATED_AT = "updated_at"
    }
}

object PhoneBatterySync {
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val periodic = PeriodicWorkRequestBuilder<PhoneBatterySyncWorker>(
            15, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "pipboy-phone-battery-periodic",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )

        val initial = OneTimeWorkRequestBuilder<PhoneBatterySyncWorker>().build()
        workManager.enqueueUniqueWork(
            "pipboy-phone-battery-initial",
            ExistingWorkPolicy.KEEP,
            initial
        )
    }
}
