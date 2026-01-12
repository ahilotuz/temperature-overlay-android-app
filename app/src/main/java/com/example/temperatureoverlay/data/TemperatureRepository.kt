package com.example.temperatureoverlay.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlin.math.roundToInt

class TemperatureRepository(
    private val appContext: Context
) {
    fun readBatteryTemperatureC(): Float? {
        val intent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null

        val tempTenths = intent.getIntExtra("temperature", Int.MIN_VALUE)
        if (tempTenths == Int.MIN_VALUE) return null

        return tempTenths / 10f
    }

    fun formatTemperature(tempC: Float?): String {
        return if (tempC == null) {
            "Temperature: unavailable"
        } else {
            val rounded = (tempC * 10).roundToInt() / 10f
            "Temperature: $rounded °C"
        }
    }
}
