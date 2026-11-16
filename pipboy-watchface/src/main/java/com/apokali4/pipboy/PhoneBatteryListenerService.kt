package com.apokali4.pipboy

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class PhoneBatteryListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val item = event.dataItem
            val data = DataMapItem.fromDataItem(item).dataMap

            when (item.uri.path) {
                PHONE_BATTERY_PATH -> {
                    val percent = data.getInt(KEY_PERCENT, -1)
                    if (percent !in 0..100) return@forEach
                    getSharedPreferences(BATTERY_PREFERENCES, Context.MODE_PRIVATE).edit()
                        .putInt(KEY_PERCENT, percent)
                        .putLong(KEY_UPDATED_AT, if (data.containsKey(KEY_UPDATED_AT)) data.getLong(KEY_UPDATED_AT) else 0L)
                        .apply()
                    requestUpdate(PhoneBatteryComplicationService::class.java)
                }
                WEATHER_PATH -> {
                    val weather = data.getString(KEY_WEATHER_TEXT).orEmpty()
                    if (weather.isBlank()) return@forEach
                    getSharedPreferences(WEATHER_PREFERENCES, Context.MODE_PRIVATE).edit()
                        .putString(KEY_WEATHER_TEXT, weather)
                        .putLong(KEY_UPDATED_AT, if (data.containsKey(KEY_UPDATED_AT)) data.getLong(KEY_UPDATED_AT) else 0L)
                        .apply()
                    requestUpdate(PhoneWeatherComplicationService::class.java)
                }
            }
        }
    }

    private fun requestUpdate(service: Class<*>) {
        ComplicationDataSourceUpdateRequester.create(
            this,
            ComponentName(this, service)
        ).requestUpdateAll()
    }

    companion object {
        const val PHONE_BATTERY_PATH = "/pipboy/phone_battery"
        const val WEATHER_PATH = "/pipboy/weather"
        const val BATTERY_PREFERENCES = "pipboy_phone_battery"
        const val WEATHER_PREFERENCES = "pipboy_weather"
        const val KEY_PERCENT = "percent"
        const val KEY_WEATHER_TEXT = "weather_text"
        const val KEY_UPDATED_AT = "updated_at"
    }
}
