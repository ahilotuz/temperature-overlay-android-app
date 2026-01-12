package com.example.temperatureoverlay.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Small helper that centralizes:
 * - checking overlay permission
 * - starting/stopping the overlay service
 */
object OverlayController {

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun startOverlay(context: Context) {
        val intent = Intent(context, OverlayService::class.java)
        context.startService(intent)
    }

    fun stopOverlay(context: Context) {
        val intent = Intent(context, OverlayService::class.java)
        context.stopService(intent)
    }
}
