package dev.aeris.island.shizuku

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuBridge {
    const val REQUEST_CODE = 4311

    fun state(): String = try {
        if (!Shizuku.pingBinder()) "Shizuku is not running"
        else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) "Connected and authorized"
        else "Connected; AERIS permission not granted"
    } catch (_: Exception) {
        "Shizuku not detected"
    }

    fun requestOrOpen(activity: Activity) {
        try {
            if (!Shizuku.pingBinder()) {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                )
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Exception) {
            // The optional bridge never blocks the main overlay features.
        }
    }
}
