package com.example.temperatureoverlay.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import com.example.temperatureoverlay.R
import com.example.temperatureoverlay.data.TemperatureRepository
import kotlinx.coroutines.*

/**
 * Foreground Service justification (Android 14+):
 *
 * - Runs only while the user explicitly enables the temperature overlay.
 * - Periodically reads battery temperature (system data) and updates the overlay UI.
 * - User-visible via a persistent notification.
 * - Stops immediately when user disables the overlay.
 *
 * Foreground service type: dataSync
 */
class OverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null
    private var tempText: TextView? = null

    private lateinit var repo: TemperatureRepository

    override fun onCreate() {
        super.onCreate()

        // REQUIRED: foreground services must show a notification quickly.
        startForeground(
            OverlayNotification.NOTIFICATION_ID,
            OverlayNotification.build(this)
        )

        repo = TemperatureRepository(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_temperature, null)
        tempText = overlayView?.findViewById(R.id.overlay_temp_text)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 120
        }

        windowManager?.addView(overlayView, params)

        serviceScope.launch {
            while (isActive) {
                val tempC = repo.readBatteryTemperatureC()
                tempText?.text = if (tempC == null) "--.- °C" else "${"%.1f".format(tempC)} °C"
                delay(5_000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        overlayView?.let { view -> windowManager?.removeView(view) }
        overlayView = null
        tempText = null
        windowManager = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
