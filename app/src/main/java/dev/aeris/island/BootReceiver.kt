package dev.aeris.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.aeris.island.overlay.IslandService

/** Auto-start is opt-in and still depends on the device's overlay and background rules. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AerisPrefs.bool(context, "auto_start", false)) return
        if (!Settings.canDrawOverlays(context)) return

        try {
            val serviceIntent = Intent(context, IslandService::class.java).apply {
                action = IslandService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Some Android/OEM builds block background startup. User can start it manually.
        }
    }
}
