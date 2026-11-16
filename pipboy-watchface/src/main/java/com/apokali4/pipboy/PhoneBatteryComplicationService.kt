package com.apokali4.pipboy

import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

class PhoneBatteryComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val percent = getSharedPreferences(
            PhoneBatteryListenerService.PREFERENCES,
            Context.MODE_PRIVATE
        ).getInt(PhoneBatteryListenerService.KEY_PERCENT, -1)
        val value = if (percent in 0..100) "$percent%" else "--%"
        return shortText(value)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return shortText("87%")
    }

    private fun shortText(value: String): ShortTextComplicationData {
        val text = PlainComplicationText.Builder(value).build()
        val description = PlainComplicationText.Builder("Заряд телефона: $value").build()
        return ShortTextComplicationData.Builder(
            text = text,
            contentDescription = description
        ).build()
    }
}
