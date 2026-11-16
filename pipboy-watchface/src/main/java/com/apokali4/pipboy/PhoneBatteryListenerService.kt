package com.apokali4.pipboy

import android.content.ComponentName
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

class PhoneBatteryListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val item = event.dataItem
            if (item.uri.path != PHONE_BATTERY_PATH) return@forEach

            val data = DataMapItem.fromDataItem(item).dataMap
            val percent = data.getInt(KEY_PERCENT, -1)
            if (percent !in 0..100) return@forEach

            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putInt(KEY_PERCENT, percent)
                .putLong(KEY_UPDATED_AT, data.getLong(KEY_UPDATED_AT, 0L))
                .apply()

            ComplicationDataSourceUpdateRequester.create(
                this,
                ComponentName(this, PhoneBatteryComplicationService::class.java)
            ).requestUpdateAll()
        }
    }

    companion object {
        const val PHONE_BATTERY_PATH = "/pipboy/phone_battery"
        const val PREFERENCES = "pipboy_phone_battery"
        const val KEY_PERCENT = "percent"
        const val KEY_UPDATED_AT = "updated_at"
    }
}
