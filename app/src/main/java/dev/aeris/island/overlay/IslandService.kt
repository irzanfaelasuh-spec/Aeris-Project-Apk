package dev.aeris.island.overlay

import android.animation.ValueAnimator
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.PathInterpolator
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
import dev.aeris.island.notifications.AerisNotificationListener

class IslandService : Service(), PillOverlayView.Callbacks {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var islandView: PillOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var resizeAnimator: ValueAnimator? = null
    private var lastMediaKey = ""
    private var lastNotificationSignature = ""
    private var expandedUntil = 0L
    private var focusForReply = false
    private var notificationIndex = 0
    private var introAnimationUntil = 0L

    private val refreshTask = object : Runnable {
        override fun run() {
            refreshIsland()
            val delay = if (AerisPrefs.bool(this@IslandService, "low_power", false)) 1250L else REFRESH_MS
            handler.postDelayed(this, delay)
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
        if (!Settings.canDrawOverlays(this) || !AerisPrefs.islandEnabled(this)) {
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
            .setContentText("Your Dynamic Island controls are ready.")
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
            view.alpha = 0f
            view.translationY = -dp(10).toFloat()
            view.scaleX = 0.96f
            view.scaleY = 0.96f
            windowManager.addView(view, params)
            islandView = view
            layoutParams = params
            introAnimationUntil = SystemClock.uptimeMillis() + 430L
            view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(430L).setInterpolator(PathInterpolator(0.18f, 0.78f, 0.22f, 1f)).start()
        } catch (_: Exception) {
            islandView = null
            layoutParams = null
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val screenWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val collapsedWidth = AerisPrefs.int(this, "island_width", 212)
        val maxWidthDp = (screenWidthDp - 20).coerceAtLeast(140)
        val width = dp(minOf(collapsedWidth, maxWidthDp))
        val height = dp(AerisPrefs.int(this, "island_height", 44).coerceIn(38, 56))
        val y = dp(AerisPrefs.int(this, "island_y", 5).coerceIn(0, 34))
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            this.y = y
            windowAnimations = android.R.style.Animation_Toast
        }
    }

    private fun refreshIsland() {
        if (!Settings.canDrawOverlays(this) || !AerisPrefs.islandEnabled(this)) {
            stopSelf()
            return
        }
        if (islandView == null) attachIsland()
        val view = islandView ?: return

        val media = if (AerisPrefs.bool(this, "music_enabled", true)) MediaBridge.current(this) else null
        val notificationEnabled = AerisPrefs.bool(this, "notifications_enabled", true)
        val recent = if (notificationEnabled) NotificationStore.recent() else emptyList()
        if (notificationIndex >= recent.size) notificationIndex = 0
        val latestNotification = recent.firstOrNull()
        val selected = recent.getOrNull(notificationIndex)

        val mediaKey = media?.let { "${it.packageName}|${it.title}|${it.artist}|${it.playing}" }.orEmpty()
        if (mediaKey.isNotBlank() && mediaKey != lastMediaKey && AerisPrefs.bool(this, "auto_expand_music", true)) {
            requestTimedExpansion(isNotification = false)
        }
        lastMediaKey = mediaKey

        val notificationSignature = latestNotification?.let { "${it.key}|${it.receivedAt}" }.orEmpty()
        if (notificationSignature.isNotBlank() && notificationSignature != lastNotificationSignature &&
            AerisPrefs.bool(this, "auto_expand_notifications", true)) {
            notificationIndex = 0
            requestTimedExpansion(isNotification = true)
        }
        lastNotificationSignature = notificationSignature

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape && !AerisPrefs.bool(this, "landscape_enabled", true)) {
            view.visibility = android.view.View.GONE
            return
        }
        view.visibility = android.view.View.VISIBLE

        val idle = media == null && selected == null
        val baseOpacity = AerisPrefs.int(this, "island_opacity", 100).coerceIn(68, 100) / 100f
        if (SystemClock.uptimeMillis() >= introAnimationUntil) {
            view.alpha = if (idle && AerisPrefs.bool(this, "fade_when_idle", false)) minOf(baseOpacity, 0.82f) else baseOpacity
        }
        val showExpanded = focusForReply || System.currentTimeMillis() < expandedUntil
        view.updateContent(
            media = media,
            recentNotifications = recent,
            selectedNotificationIndex = notificationIndex,
            expanded = showExpanded,
            hideSensitive = AerisPrefs.bool(this, "hide_sensitive", false),
            showArtwork = AerisPrefs.bool(this, "show_artwork", true),
            showVisualizer = AerisPrefs.bool(this, "visualizer_enabled", true),
            showMediaControls = AerisPrefs.bool(this, "media_controls", true),
            showNotificationText = AerisPrefs.bool(this, "notification_preview", true),
            showReply = AerisPrefs.bool(this, "quick_reply", true),
            showTrackState = AerisPrefs.bool(this, "show_track_state", true),
            showSourceApp = AerisPrefs.bool(this, "show_source_app", true),
            cornerRadius = AerisPrefs.int(this, "corner_radius", 42),
            visualizerSpeed = AerisPrefs.int(this, "animation_speed", 100),
            showLyrics = AerisPrefs.bool(this, "lyrics_enabled", true),
            useLiquidGlass = AerisPrefs.bool(this, "liquid_glass", true)
        )
        updateLayoutForCurrentMode(showExpanded, media, selected, view.isLyricsExpanded)
    }

    private fun requestTimedExpansion(isNotification: Boolean) {
        if (!AerisPrefs.bool(this, "auto_collapse", true)) {
            expandedUntil = Long.MAX_VALUE
            return
        }
        val seconds = if (isNotification) {
            AerisPrefs.int(this, "notification_preview_seconds", 2).coerceIn(2, 8)
        } else {
            AerisPrefs.int(this, "expand_seconds", 2).coerceIn(2, 8)
        }
        expandedUntil = System.currentTimeMillis() + seconds * 1000L
        animateOpen()
    }

    private fun updateLayoutForCurrentMode(
        expanded: Boolean,
        media: MediaSnapshot?,
        notification: NotificationSnapshot?,
        showLyrics: Boolean
    ) {
        val view = islandView ?: return
        val current = layoutParams ?: return
        val screenWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val screenHeightDp = (resources.displayMetrics.heightPixels / resources.displayMetrics.density).toInt()
        val maxWidthDp = (screenWidthDp - 20).coerceAtLeast(140)
        val requestedWidth = if (expanded) AerisPrefs.int(this, "expanded_width", 340) else AerisPrefs.int(this, "island_width", 212)
        val targetWidth = dp(minOf(requestedWidth, maxWidthDp).coerceAtLeast(140))
        val baseHeight = when {
            !expanded -> AerisPrefs.int(this, "island_height", 44).coerceIn(38, 56)
            media != null && notification != null -> if (showLyrics) 362 else 302
            media != null -> if (showLyrics) 300 else 242
            notification != null -> 184
            else -> 92
        }
        val keyboardExtra = if (focusForReply) 54 else 0
        val safeHeight = (screenHeightDp - 80).coerceAtLeast(180)
        val targetHeight = dp((baseHeight + keyboardExtra).coerceAtMost((safeHeight * 0.74f).toInt()))
        updateBlurForMode(current, expanded)
        try { windowManager.updateViewLayout(view, current) } catch (_: Exception) { }
        if (current.width == targetWidth && current.height == targetHeight) return

        resizeAnimator?.cancel()
        val startWidth = current.width
        val startHeight = current.height
        resizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (AerisPrefs.bool(this@IslandService, "reduce_motion", false)) 150L else 330L
            interpolator = PathInterpolator(0.2f, 0.82f, 0.2f, 1f)
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                current.width = (startWidth + (targetWidth - startWidth) * fraction).toInt()
                current.height = (startHeight + (targetHeight - startHeight) * fraction).toInt()
                try { windowManager.updateViewLayout(view, current) } catch (_: Exception) { }
            }
            start()
        }
    }

    private fun updateBlurForMode(params: WindowManager.LayoutParams, expanded: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val wantGlassBlur = expanded && AerisPrefs.bool(this, "liquid_glass", true)
        try {
            if (wantGlassBlur && windowManager.isCrossWindowBlurEnabled) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.setBlurBehindRadius(dp(18))
                params.dimAmount = 0.08f
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                params.setBlurBehindRadius(0)
                params.dimAmount = 0f
            }
        } catch (_: Exception) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            params.dimAmount = 0f
        }
    }

    private fun animateOpen() {
        islandView?.let { view ->
            view.animate().cancel()
            view.scaleX = 0.97f
            view.scaleY = 0.97f
            view.alpha = 0.9f
            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(280L)
                .setInterpolator(PathInterpolator(0.18f, 0.78f, 0.22f, 1f)).start()
        }
    }

    private fun animateClose() {
        islandView?.let { view ->
            view.animate().cancel()
            view.animate().scaleX(0.985f).scaleY(0.985f).alpha(0.92f).setDuration(150L)
                .setInterpolator(PathInterpolator(0.3f, 0f, 0.7f, 1f)).withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160L).start()
                }.start()
        }
    }

    override fun onReplyRequested(notificationKey: String) {
        focusForReply = true
        val params = layoutParams ?: return
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        try {
            islandView?.let { view ->
                updateLayoutForCurrentMode(true, MediaBridge.current(this), NotificationStore.recent().firstOrNull { it.key == notificationKey }, view.isLyricsExpanded)
                view.showReplyEditor()
            }
        } catch (_: Exception) { }
    }

    override fun onDismissNotification(notificationKey: String) {
        // Dismiss from Android's notification shade, but retain the in-memory
        // snapshot so it remains available in AERIS notification history.
        if (AerisNotificationListener.dismissNotification(notificationKey)) {
            notificationIndex = 0
            refreshIsland()
        }
    }

    override fun onReplyFinished() {
        focusForReply = false
        val params = layoutParams ?: return
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        islandView?.hideReplyEditor()
        refreshIsland()
    }

    override fun onToggleExpanded() {
        val now = System.currentTimeMillis()
        val isCurrentlyExpanded = focusForReply || now < expandedUntil
        if (isCurrentlyExpanded) {
            expandedUntil = 0L
            focusForReply = false
            islandView?.hideReplyEditor()
            layoutParams?.let { it.flags = it.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE }
            animateClose()
        } else {
            val mediaActive = MediaBridge.current(this) != null
            val notificationActive = NotificationStore.latest != null
            expandedUntil = if (mediaActive || notificationActive) Long.MAX_VALUE else now + 1000L
            animateOpen()
        }
        refreshIsland()
    }

    override fun onGestureAction(action: String) {
        when (action) {
            "expand" -> onToggleExpanded()
            "play_pause" -> MediaBridge.toggle(this)
            "next_track" -> MediaBridge.next(this)
            "previous_track" -> MediaBridge.previous(this)
            "volume_up" -> (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            "volume_down" -> (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            "open_app" -> try {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            } catch (_: Exception) { }
            "none" -> Unit
            else -> onToggleExpanded()
        }
        refreshIsland()
    }

    override fun onOutsideTouch() {
        if (focusForReply) return
        val isExpanded = System.currentTimeMillis() < expandedUntil
        if (isExpanded && expandedUntil != Long.MAX_VALUE) {
            expandedUntil = 0L
            animateClose()
            refreshIsland()
        } else if (expandedUntil == Long.MAX_VALUE) {
            expandedUntil = 0L
            animateClose()
            refreshIsland()
        }
    }

    override fun onNotificationHistoryChanged(index: Int) {
        val count = NotificationStore.count()
        notificationIndex = index.coerceIn(0, (count - 1).coerceAtLeast(0))
        refreshIsland()
    }

    override fun onContentSizeChanged() {
        refreshIsland()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        resizeAnimator?.cancel()
        try { islandView?.let { windowManager.removeView(it) } } catch (_: Exception) { }
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
        private const val REFRESH_MS = 620L
    }
}
