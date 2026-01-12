package com.example.temperatureoverlay.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.temperatureoverlay.R

object OverlayNotification {
    private const val CHANNEL_ID = "overlay_channel"
    private const val CHANNEL_NAME = "Temperature Overlay"
    const val NOTIFICATION_ID = 1001

    fun build(context: Context): Notification {
        ensureChannel(context)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Temperature overlay running")
            .setContentText("Overlay is active. Return to the app to stop it.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
}
