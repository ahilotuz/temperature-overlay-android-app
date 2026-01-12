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
 * - Periodically reads battery temperature (system data) and updates overlay UI.
 * - User-visible via a persistent notification (required for foreground services).
 * - Stops immediately when user disables the overlay (close button / Stop action).
 *
 * Foreground service type: dataSync
 *
 * NOTE:
 * This service draws a system overlay (TYPE_APPLICATION_OVERLAY). The user must grant
 * "Display over other apps" permission (SYSTEM_ALERT_WINDOW).
 */
class OverlayService : Service() {

    // Service-scoped coroutine lifecycle:
    // - SupervisorJob prevents one failure from cancelling all children.
    // - Main.immediate is appropriate because UI updates (TextView) must happen on the main thread.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // WindowManager is the system API that can attach/detach/update overlay Views.
    private var windowManager: WindowManager? = null

    // The inflated overlay layout attached to the WindowManager.
    private var overlayView: View? = null

    // Child views inside the overlay layout.
    // rootView is where we attach the touch listener to avoid child view interception.
    private var rootView: View? = null
    private var tempText: TextView? = null
    private var closeBtn: TextView? = null

    // Last-used layout params so we can update x/y during drag.
    private var layoutParams: WindowManager.LayoutParams? = null

    // Collapsed state: when true, show a minimal dot and pause visual updates.
    private var isCollapsed: Boolean = false

    // Repository that reads battery temperature via ACTION_BATTERY_CHANGED (sticky broadcast).
    private lateinit var repo: TemperatureRepository

    override fun onCreate() {
        super.onCreate()

        // REQUIRED: A foreground service must call startForeground shortly after starting,
        // otherwise Android may stop it (especially on newer versions).
        startForeground(
            OverlayNotification.NOTIFICATION_ID,
            OverlayNotification.build(this)
        )

        // Initialize dependencies.
        repo = TemperatureRepository(applicationContext)

        // Get the system WindowManager service to add an overlay view.
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate the overlay layout file (overlay_temperature.xml).
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_temperature, null)

        // Find view references from the inflated layout.
        rootView = overlayView?.findViewById(R.id.overlay_root)
        tempText = overlayView?.findViewById(R.id.overlay_temp_text)
        closeBtn = overlayView?.findViewById(R.id.overlay_close)

        // Configure overlay window params:
        // - TYPE_APPLICATION_OVERLAY: required for overlays on Android O+.
        // - FLAG_NOT_FOCUSABLE: overlay won't steal focus/keyboard from other apps.
        // - TRANSLUCENT: supports alpha/transparent backgrounds.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Start near the top-left with a bit of offset.
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 120
        }

        // Store params so we can update x/y while dragging.
        layoutParams = params

        // Attach the overlay to the system window.
        windowManager?.addView(overlayView, params)

        // Touch handling ONLY on the root view to avoid child views (TextViews) "stealing" touches.
        // This is the key to reliable drag behavior across devices.
        rootView?.setOnTouchListener(object : View.OnTouchListener {
            // Starting overlay position when the finger goes down.
            private var startX = 0
            private var startY = 0

            // Starting finger position (raw screen coordinates).
            private var touchX = 0f
            private var touchY = 0f

            // Used to differentiate tap vs drag.
            private var downTime = 0L
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                // If we lost params, consume the event to avoid crashes.
                val lp = layoutParams ?: return true

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Record the starting state for this gesture.
                        startX = lp.x
                        startY = lp.y
                        touchX = event.rawX
                        touchY = event.rawY
                        downTime = SystemClock.elapsedRealtime()
                        moved = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // Convert finger movement into window movement.
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()

                        // Once movement passes a small threshold, consider it a drag.
                        if (!moved && (abs(dx) + abs(dy) > 8)) moved = true

                        // Update overlay position and apply it via WindowManager.
                        lp.x = startX + dx
                        lp.y = startY + dy
                        windowManager?.updateViewLayout(overlayView, lp)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        // Decide whether this gesture is a "tap" (short + minimal movement).
                        val elapsed = SystemClock.elapsedRealtime() - downTime
                        val isTap = !moved && elapsed < 250

                        if (isTap) {
                            // If the tap landed on the close button -> stop service.
                            // Otherwise -> toggle collapsed/expanded state.
                            if (hitTest(closeBtn, event.rawX, event.rawY)) {
                                stopSelf() // closes overlay + removes notification in onDestroy()
                            } else {
                                toggleCollapsed()
                            }
                        }

                        return true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        // Gesture cancelled by the system; we just consume it.
                        return true
                    }
                }

                // Always consume events so the gesture stays consistent.
                return true
            }
        })

        // Periodic updates:
        // - Poll temperature every 5 seconds.
        // - Only update the displayed text while expanded (not collapsed).
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

    /**
     * Toggle collapsed state.
     * - Collapsed: show a minimal dot "•" and stop visual updates.
     * - Expanded: show the temperature and refresh immediately.
     */
    private fun toggleCollapsed() {
        isCollapsed = !isCollapsed
        if (isCollapsed) {
            tempText?.text = "•"
        } else {
            // Force immediate refresh on expand so user sees the latest value instantly.
            tempText?.text = formatTemp(repo.readBatteryTemperatureC())
        }
    }

    /**
     * Simple hit test: checks if the raw touch coordinate falls inside the given view.
     * Used to detect taps on the close button even though we handle touch on the root view.
     */
    private fun hitTest(view: View?, rawX: Float, rawY: Float): Boolean {
        if (view == null) return false
        val rect = Rect()
        view.getGlobalVisibleRect(rect)
        return rect.contains(rawX.toInt(), rawY.toInt())
    }

    /**
     * Formats the temperature for the overlay.
     * Null means the device didn't provide a battery temperature reading.
     */
    private fun formatTemp(tempC: Float?): String =
        if (tempC == null) "--.- °C" else "${"%.1f".format(tempC)} °C"

    override fun onDestroy() {
        super.onDestroy()

        // Cancel all coroutines to stop periodic updates and prevent leaks.
        serviceScope.cancel()

        // Remove overlay view from the window to avoid a WindowManager leak.
        overlayView?.let { view -> windowManager?.removeView(view) }

        // Clear references (helps avoid memory leaks).
        overlayView = null
        rootView = null
        tempText = null
        closeBtn = null
        windowManager = null
        layoutParams = null
    }

    // This is a started service, not a bound service.
    override fun onBind(intent: Intent?): IBinder? = null
}
