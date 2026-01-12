package com.example.temperatureoverlay.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.temperatureoverlay.R
import com.example.temperatureoverlay.data.TemperatureRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    private var overlayView: View? = null

    private var rootView: View? = null
    private var tempText: TextView? = null
    private var closeBtn: TextView? = null

    private var layoutParams: WindowManager.LayoutParams? = null
    private var isCollapsed: Boolean = false

    private lateinit var repo: TemperatureRepository

    override fun onCreate() {
        super.onCreate()

        // REQUIRED: become a foreground service quickly after starting.
        startForeground(
            OverlayNotification.NOTIFICATION_ID,
            OverlayNotification.build(this)
        )

        repo = TemperatureRepository(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_temperature, null)

        // Get references
        rootView = overlayView?.findViewById(R.id.overlay_root)
        tempText = overlayView?.findViewById(R.id.overlay_temp_text)
        closeBtn = overlayView?.findViewById(R.id.overlay_close)

        // Overlay window params
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

        layoutParams = params
        windowManager?.addView(overlayView, params)

        // Touch handling ONLY on the root to avoid children stealing touches.
        // This makes drag reliable 100% of the time.
        rootView?.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var downTime = 0L
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val lp = layoutParams ?: return true

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x
                        startY = lp.y
                        touchX = event.rawX
                        touchY = event.rawY
                        downTime = SystemClock.elapsedRealtime()
                        moved = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()

                        // Consider it a drag once the finger moves a bit.
                        if (!moved && (abs(dx) + abs(dy) > 8)) moved = true

                        lp.x = startX + dx
                        lp.y = startY + dy
                        windowManager?.updateViewLayout(overlayView, lp)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        val elapsed = SystemClock.elapsedRealtime() - downTime

                        // If it wasn't really a drag, treat as a tap.
                        val isTap = !moved && elapsed < 250

                        if (isTap) {
                            // Decide if the tap hit the close button.
                            if (hitTest(closeBtn, event.rawX, event.rawY)) {
                                stopSelf() // closes overlay + stops notification
                            } else {
                                toggleCollapsed()
                            }
                        }

                        return true
                    }

                    MotionEvent.ACTION_CANCEL -> return true
                }

                return true
            }
        })

        // Periodic updates: only update text when expanded.
        serviceScope.launch {
            while (isActive) {
                val tempC = repo.readBatteryTemperatureC()
                if (!isCollapsed) {
                    tempText?.text = formatTemp(tempC)
                }
                delay(5_000)
            }
        }
    }

    private fun toggleCollapsed() {
        isCollapsed = !isCollapsed
        if (isCollapsed) {
            tempText?.text = "•"
        } else {
            // Force immediate refresh on expand
            tempText?.text = formatTemp(repo.readBatteryTemperatureC())
        }
    }

    private fun hitTest(view: View?, rawX: Float, rawY: Float): Boolean {
        if (view == null) return false
        val rect = Rect()
        view.getGlobalVisibleRect(rect)
        return rect.contains(rawX.toInt(), rawY.toInt())
    }

    private fun formatTemp(tempC: Float?): String =
        if (tempC == null) "--.- °C" else "${"%.1f".format(tempC)} °C"

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        overlayView?.let { view -> windowManager?.removeView(view) }
        overlayView = null
        rootView = null
        tempText = null
        closeBtn = null
        windowManager = null
        layoutParams = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
