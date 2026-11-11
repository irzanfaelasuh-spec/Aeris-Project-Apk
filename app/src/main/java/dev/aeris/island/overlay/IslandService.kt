package dev.aeris.island.overlay

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.aeris.island.AerisPrefs
import dev.aeris.island.MainActivity
import dev.aeris.island.R
import dev.aeris.island.media.MediaBridge
import dev.aeris.island.notifications.AerisNotificationListener
import dev.aeris.island.notifications.NotificationSnapshot
import dev.aeris.island.notifications.NotificationStore
import java.util.function.Consumer

class IslandService : Service(), PillOverlayView.Callbacks {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var island: PillOverlayView? = null
    private var params: WindowManager.LayoutParams? = null
    private var animator: ValueAnimator? = null
    private var expandedUntil = 0L
    private var pinnedExpanded = false
    private var lyricsExpanded = false
    private var historyIndex = 0
    private var lastMediaSignature = ""
    private var lastNotificationSignature = ""
    private var selectedNotificationKey: String? = null
    private var recordingVisible = false
    private var recordingCallback: Consumer<Int>? = null

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, if (AerisPrefs.bool(this@IslandService, "low_power", false)) 1400L else 450L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannel()
        registerRecordingCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        AerisPrefs.setBool(this, "service_active", true)
        attachIsland()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "AERIS status", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun startAsForeground() {
        val openApp = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle("AERIS is active")
            .setContentText("Media and notification island is running.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun attachIsland() {
        if (island != null || !Settings.canDrawOverlays(this)) return
        val view = PillOverlayView(this, this)
        val lp = buildParams()
        try {
            view.alpha = 0f
            view.translationY = -dp(7).toFloat()
            view.scaleX = .96f
            view.scaleY = .96f
            windowManager.addView(view, lp)
            island = view
            params = lp
            view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(420L).setInterpolator(DecelerateInterpolator()).start()
        } catch (_: Exception) {
            island = null
            params = null
        }
    }

    private fun buildParams(): WindowManager.LayoutParams {
        val screenWidth = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val width = minOf(AerisPrefs.int(this, "collapsed_width", 190), (screenWidth - 22).coerceAtLeast(145))
        return WindowManager.LayoutParams(
            dp(width), dp(AerisPrefs.int(this, "collapsed_height", 46)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(AerisPrefs.int(this@IslandService, "top_offset", 10))
        }
    }

    private fun refresh() {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        if (island == null) attachIsland()
        val view = island ?: return
        val media = if (AerisPrefs.bool(this, "music_enabled", true)) MediaBridge.current(this) else null
        val history = if (AerisPrefs.bool(this, "notifications_enabled", true)) NotificationStore.recent() else emptyList()
        val newest = history.firstOrNull()
        val mediaSignature = media?.let { "${it.packageName}|${it.title}|${it.artist}|${it.playing}" }.orEmpty()
        if (mediaSignature.isNotBlank() && mediaSignature != lastMediaSignature) {
            if (AerisPrefs.bool(this, "auto_expand_music", true)) peek(AerisPrefs.int(this, "expand_seconds", 8).coerceIn(2, 30))
            lastMediaSignature = mediaSignature
        }
        val notificationSignature = newest?.let { "${it.key}|${it.receivedAt}" }.orEmpty()
        if (notificationSignature.isNotBlank() && notificationSignature != lastNotificationSignature) {
            lastNotificationSignature = notificationSignature
            historyIndex = 0
            if (AerisPrefs.bool(this, "auto_expand_notifications", true)) {
                peek(AerisPrefs.int(this, "notification_peek_seconds", 2).coerceIn(2, 8))
            }
        }
        historyIndex = historyIndex.coerceIn(0, (history.size - 1).coerceAtLeast(0))
        val notification: NotificationSnapshot? = history.getOrNull(historyIndex)
        selectedNotificationKey = notification?.key

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape && !AerisPrefs.bool(this, "landscape_enabled", true)) {
            view.visibility = android.view.View.GONE
            return
        }
        view.visibility = android.view.View.VISIBLE
        val expanded = pinnedExpanded || System.currentTimeMillis() < expandedUntil
        view.update(media, notification, expanded, historyIndex, history.size, recordingVisible)

        val screenWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val hasActivity = media != null || notification != null || recordingVisible
        val desiredWidth = when {
            !expanded -> AerisPrefs.int(this, "collapsed_width", 190)
            !hasActivity -> 236
            else -> AerisPrefs.int(this, "expanded_width", 350)
        }
        val width = dp(desiredWidth.coerceAtMost((screenWidthDp - 20).coerceAtLeast(145)))
        val desiredHeight = if (!expanded) AerisPrefs.int(this, "collapsed_height", 46) else when {
            !hasActivity -> 88
            media != null && lyricsExpanded && notification != null -> 440
            media != null && lyricsExpanded -> 395
            media != null && notification != null -> 350
            media != null -> 285
            notification != null -> 195
            else -> 120
        }
        val screenHeightDp = (resources.displayMetrics.heightPixels / resources.displayMetrics.density).toInt()
        val height = dp(desiredHeight.coerceAtMost((screenHeightDp - 72).coerceAtLeast(170)))
        updateBlur(expanded)
        animateTo(width, height)
    }

    private fun peek(seconds: Int) {
        pinnedExpanded = false
        expandedUntil = System.currentTimeMillis() + seconds.coerceIn(1, 30) * 1000L
    }

    private fun updateBlur(expanded: Boolean) {
        val lp = params ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            val enabled = expanded && AerisPrefs.bool(this, "liquid_glass", false) &&
                try { windowManager.isCrossWindowBlurEnabled } catch (_: Exception) { false }
            lp.flags = if (enabled) lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                else lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            try { lp.setBlurBehindRadius(if (enabled) dp(20) else 0) } catch (_: Exception) { }
            lp.dimAmount = if (enabled) .06f else 0f
        }
    }

    private fun animateTo(width: Int, height: Int) {
        val lp = params ?: return
        val view = island ?: return
        if (lp.width == width && lp.height == height) {
            try { windowManager.updateViewLayout(view, lp) } catch (_: Exception) { }
            return
        }
        animator?.cancel()
        val fromWidth = lp.width
        val fromHeight = lp.height
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (AerisPrefs.bool(this@IslandService, "reduce_motion", false)) 170L else 390L
            interpolator = DecelerateInterpolator(1.7f)
            addUpdateListener { value ->
                val fraction = value.animatedValue as Float
                lp.width = (fromWidth + (width - fromWidth) * fraction).toInt()
                lp.height = (fromHeight + (height - fromHeight) * fraction).toInt()
                try { windowManager.updateViewLayout(view, lp) } catch (_: Exception) { }
            }
            start()
        }
    }

    override fun toggleExpanded() {
        val idle = MediaBridge.current(this) == null && NotificationStore.latest == null && !recordingVisible
        when {
            pinnedExpanded -> { pinnedExpanded = false; expandedUntil = 0L }
            System.currentTimeMillis() < expandedUntil -> { expandedUntil = 0L; pinnedExpanded = false }
            idle -> peek(1)
            else -> { pinnedExpanded = true; expandedUntil = 0L }
        }
        refresh()
    }

    override fun browseNotifications(direction: Int) {
        val count = NotificationStore.recent().size
        if (count < 2) return
        historyIndex = (historyIndex + direction).mod(count)
        pinnedExpanded = true
        expandedUntil = 0L
        refresh()
    }

    override fun dismissNotification() {
        val key = selectedNotificationKey ?: return
        if (AerisNotificationListener.dismiss(key)) NotificationStore.remove(key)
        historyIndex = 0
        refresh()
    }

    override fun replyRequested() {
        val lp = params ?: return
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        try {
            island?.let { view ->
                windowManager.updateViewLayout(view, lp)
                animateTo(lp.width, dp(310))
                view.showReplyEditor()
            }
        } catch (_: Exception) { }
    }

    override fun replyFinished() {
        val lp = params ?: return
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        island?.hideReplyEditor()
        try { island?.let { windowManager.updateViewLayout(it, lp) } } catch (_: Exception) { }
        refresh()
    }

    override fun lyricsExpandedChanged(expanded: Boolean) {
        lyricsExpanded = expanded
        refresh()
    }

    private fun registerRecordingCallback() {
        if (Build.VERSION.SDK_INT < 35) return
        try {
            val callback = Consumer<Int> { state ->
                recordingVisible = state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE
                refresh()
            }
            recordingCallback = callback
            val current = windowManager.addScreenRecordingCallback(mainExecutor, callback)
            recordingVisible = current == WindowManager.SCREEN_RECORDING_STATE_VISIBLE
        } catch (_: Exception) { recordingVisible = false }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = island ?: return
        val next = buildParams()
        try { params = next; windowManager.updateViewLayout(view, next); refresh() } catch (_: Exception) { }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        animator?.cancel()
        if (Build.VERSION.SDK_INT >= 35) {
            try { recordingCallback?.let { windowManager.removeScreenRecordingCallback(it) } } catch (_: Exception) { }
        }
        try { island?.let { windowManager.removeView(it) } } catch (_: Exception) { }
        island = null
        params = null
        AerisPrefs.setBool(this, "service_active", false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "dev.aeris.island.action.START"
        const val ACTION_STOP = "dev.aeris.island.action.STOP"
        private const val CHANNEL_ID = "aeris_v3_status"
        private const val NOTIFICATION_ID = 4310
    }
}
