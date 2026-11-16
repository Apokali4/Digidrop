package com.apokali4.pipboy

import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

class PhoneWeatherComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val weather = getSharedPreferences(
            PhoneBatteryListenerService.WEATHER_PREFERENCES,
            Context.MODE_PRIVATE
        ).getString(PhoneBatteryListenerService.KEY_WEATHER_TEXT, null) ?: "--°"
        return shortText(weather)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return shortText("+18° ЯСНО")
    }

    private fun shortText(value: String): ShortTextComplicationData {
        val text = PlainComplicationText.Builder(value).build()
        val description = PlainComplicationText.Builder("Погода в Калуге: $value").build()
        return ShortTextComplicationData.Builder(
            text = text,
            contentDescription = description
        ).build()
    }
}
