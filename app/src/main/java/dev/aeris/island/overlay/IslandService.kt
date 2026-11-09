package dev.aeris.island.overlay

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.aeris.island.AerisApp
import dev.aeris.island.AerisPrefs
import dev.aeris.island.MainActivity
import dev.aeris.island.R
import dev.aeris.island.media.MediaBridge
import dev.aeris.island.media.MediaSnapshot
import dev.aeris.island.notifications.NotificationSnapshot
import dev.aeris.island.notifications.NotificationStore

class IslandService : Service(), PillOverlayView.Callbacks {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var islandView: PillOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var lastMediaKey = ""
    private var lastNotificationTime = 0L
    private var expandedUntil = 0L
    private var focusForReply = false

    private val refreshTask = object : Runnable {
        override fun run() {
            refreshIsland()
            val nextDelay = if (AerisPrefs.bool(this@IslandService, "low_power", false)) 1600L else REFRESH_MS
            handler.postDelayed(this, nextDelay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        (application as? AerisApp)?.ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> Unit
            else -> Unit
        }

        startAsForeground()
        AerisPrefs.setBool(this, "service_active", true)
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!AerisPrefs.islandEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        attachIsland()
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
        return START_STICKY
    }

    private fun startAsForeground() {
        val openApp = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, AerisApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle("AERIS is active")
            .setContentText("The interactive island overlay is running.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun attachIsland() {
        if (islandView != null || !Settings.canDrawOverlays(this)) return
        val view = PillOverlayView(this, this)
        val params = buildLayoutParams()
        try {
            windowManager.addView(view, params)
            islandView = view
            layoutParams = params
        } catch (_: Exception) {
            islandView = null
            layoutParams = null
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val screenWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val maxWidthDp = (screenWidthDp - 20).coerceAtLeast(140)
        val width = dp(minOf(AerisPrefs.int(this, "island_width", 188), maxWidthDp))
        val height = dp(AerisPrefs.int(this, "island_height", 43))
        val y = dp(AerisPrefs.int(this, "island_y", 8))
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            this.y = y
            windowAnimations = android.R.style.Animation_Toast
        }
    }

    private fun refreshIsland() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        if (!AerisPrefs.islandEnabled(this)) {
            stopSelf()
            return
        }
        if (islandView == null) attachIsland()
        val view = islandView ?: return

        val mediaEnabled = AerisPrefs.bool(this, "music_enabled", true)
        val notificationsEnabled = AerisPrefs.bool(this, "notifications_enabled", true)
        val media = if (mediaEnabled) MediaBridge.current(this) else null
        val notification = if (notificationsEnabled) NotificationStore.latest else null
        val mediaKey = media?.let { "${it.packageName}|${it.title}|${it.artist}|${it.playing}" }.orEmpty()
        if (mediaKey.isNotBlank() && mediaKey != lastMediaKey && AerisPrefs.bool(this, "auto_expand_music", true)) {
            requestTimedExpansion()
        }
        lastMediaKey = mediaKey

        if (notification != null && notification.receivedAt != lastNotificationTime &&
            AerisPrefs.bool(this, "auto_expand_notifications", true)) {
            lastNotificationTime = notification.receivedAt
            requestTimedExpansion()
        }

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape && !AerisPrefs.bool(this, "landscape_enabled", true)) {
            view.visibility = android.view.View.GONE
            return
        } else {
            view.visibility = android.view.View.VISIBLE
        }
        val idle = media == null && notification == null
        val baseOpacity = AerisPrefs.int(this, "island_opacity", 100).coerceIn(60, 100) / 100f
        view.alpha = if (idle && AerisPrefs.bool(this, "fade_when_idle", false)) minOf(baseOpacity, 0.72f) else baseOpacity
        val showExpanded = focusForReply || System.currentTimeMillis() < expandedUntil
        view.updateContent(
            media = media,
            notification = notification,
            expanded = showExpanded,
            hideSensitive = AerisPrefs.bool(this, "hide_sensitive", false),
            showArtwork = AerisPrefs.bool(this, "show_artwork", true),
            showVisualizer = AerisPrefs.bool(this, "visualizer_enabled", true) && !AerisPrefs.bool(this, "reduce_motion", false),
            showMediaControls = AerisPrefs.bool(this, "media_controls", true),
            showNotificationText = AerisPrefs.bool(this, "notification_preview", true),
            showReply = AerisPrefs.bool(this, "quick_reply", true),
            showTrackState = AerisPrefs.bool(this, "show_track_state", true),
            showSourceApp = AerisPrefs.bool(this, "show_source_app", false),
            cornerRadius = AerisPrefs.int(this, "corner_radius", 40),
            visualizerSpeed = AerisPrefs.int(this, "animation_speed", 100)
        )
        updateLayoutForCurrentMode(showExpanded)
    }

    private fun requestTimedExpansion() {
        if (!AerisPrefs.bool(this, "auto_collapse", true)) {
            expandedUntil = Long.MAX_VALUE
            return
        }
        val seconds = AerisPrefs.int(this, "expand_seconds", 10).coerceIn(3, 30)
        expandedUntil = System.currentTimeMillis() + seconds * 1000L
    }

    private fun updateLayoutForCurrentMode(expanded: Boolean) {
        val view = islandView ?: return
        val current = layoutParams ?: return
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val screenWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val maxWidthDp = (screenWidthDp - 20).coerceAtLeast(140)
        val requestedWidth = if (expanded) AerisPrefs.int(this, "expanded_width", 330)
            else AerisPrefs.int(this, "island_width", 188)
        val width = minOf(requestedWidth, maxWidthDp)
        val height = if (expanded) 122 else AerisPrefs.int(this, "island_height", 43)
        val newWidth = dp(width.coerceAtLeast(140))
        val resolvedHeight = if (focusForReply) 166 else height
        val newHeight = dp(resolvedHeight)
        if (current.width != newWidth || current.height != newHeight) {
            current.width = newWidth
            current.height = newHeight
            try { windowManager.updateViewLayout(view, current) } catch (_: Exception) { }
        }
    }

    override fun onReplyRequested() {
        focusForReply = true
        val params = layoutParams ?: return
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        try {
            islandView?.let {
                params.height = dp(166)
                windowManager.updateViewLayout(it, params)
                it.showReplyEditor()
            }
        } catch (_: Exception) { }
    }

    override fun onReplyFinished() {
        focusForReply = false
        val params = layoutParams ?: return
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try {
            islandView?.let { view ->
                view.hideReplyEditor()
                windowManager.updateViewLayout(view, params)
            }
        } catch (_: Exception) { }
    }

    override fun onToggleExpanded() {
        expandedUntil = if (System.currentTimeMillis() < expandedUntil) 0L
            else if (!AerisPrefs.bool(this, "auto_collapse", true)) Long.MAX_VALUE
            else System.currentTimeMillis() + AerisPrefs.int(this, "expand_seconds", 10).coerceIn(3, 30) * 1000L
        refreshIsland()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            islandView?.let { windowManager.removeView(it) }
        } catch (_: Exception) { }
        islandView = null
        layoutParams = null
        AerisPrefs.setBool(this, "service_active", false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = islandView ?: return
        val newParams = buildLayoutParams()
        try {
            layoutParams = newParams
            windowManager.updateViewLayout(view, newParams)
            refreshIsland()
        } catch (_: Exception) { }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "dev.aeris.island.action.START"
        const val ACTION_STOP = "dev.aeris.island.action.STOP"
        const val NOTIFICATION_ID = 4310
        private const val REFRESH_MS = 850L
    }
}
