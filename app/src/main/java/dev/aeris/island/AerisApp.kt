package dev.aeris.island

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AerisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AERIS active overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when the user has enabled the AERIS overlay."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "aeris_overlay_service"
    }
}
