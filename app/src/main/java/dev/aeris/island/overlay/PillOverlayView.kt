package dev.aeris.island.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context.INPUT_METHOD_SERVICE
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.text.InputType
import dev.aeris.island.AerisPrefs
import dev.aeris.island.media.LyricsEngine
import dev.aeris.island.media.MediaBridge
import dev.aeris.island.media.MediaSnapshot
import dev.aeris.island.notifications.NotificationSnapshot
import dev.aeris.island.notifications.NotificationStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

/** Polished, adaptive Dynamic Island surface. All notification text remains in memory only. */
class PillOverlayView(context: Context, private val callbacks: Callbacks) : LinearLayout(context) {
    interface Callbacks {
        fun onReplyRequested(notificationKey: String)
        fun onReplyFinished()
        fun onDismissNotification(notificationKey: String)
        fun onToggleExpanded()
        fun onGestureAction(action: String)
        fun onOutsideTouch()
        fun onNotificationHistoryChanged(index: Int)
        fun onContentSizeChanged()
    }

    private val header = LinearLayout(context)
    private val art = ImageView(context)
    private val textColumn = LinearLayout(context)
    private val title = TextView(context)
    private val subtitle = TextView(context)
    private val visualizer = VisualizerStrip(context)
    private val idleMark = View(context)
    private val idleMessage = TextView(context)
    private val musicPanel = LinearLayout(context)
    private val timeRow = LinearLayout(context)
    private val elapsedText = TextView(context)
    private val durationText = TextView(context)
    private val seekBar = SeekBar(context)
    private val controls = LinearLayout(context)
    private val lyricsBlock = LinearLayout(context)
    private val previousLyric = lyricText(11f, 0xFF9DA9B6.toInt())
    private val activeLyric = lyricText(15f, Color.WHITE)
    private val nextLyric = lyricText(11f, 0xFF9DA9B6.toInt())
    private val lyricUnavailable = lyricText(11f, 0xFFDCE5ED.toInt()).apply { gravity = Gravity.CENTER }
    private val notificationCard = LinearLayout(context)
    private val notificationHeader = LinearLayout(context)
    private val notificationIcon = ImageView(context)
    private val notificationTextColumn = LinearLayout(context)
    private val notificationTitle = TextView(context)
    private val notificationBody = TextView(context)
    private val notificationNavigation = LinearLayout(context)
    private val notificationPosition = TextView(context)
    private val replyArea = LinearLayout(context)
    private val replyInput = EditText(context)
    private val emptyState = TextView(context)

    private var currentMedia: MediaSnapshot? = null
    private var notifications: List<NotificationSnapshot> = emptyList()
    private var selectedNotification: NotificationSnapshot? = null
    private var selectedIndex = 0
    private var expanded = false
    private var isSeeking = false
    private var lyricsExpanded = false
    private var liquidGlass = false
    private var lastLyricsKey = ""
    private var lastLyricSignature = ""
    private var cachedLyricsRaw: String? = null
    private var cachedLyricsLines = emptyList<dev.aeris.island.media.LyricLine>()
    private var lastArtworkKey = ""
    private var lastNotificationKey = ""
    private var currentAccent = 0xFF79C9DA.toInt()
    private var lastSheenMode = false
    private var lastSheenAccent = 0
    private var gestureStartX = 0f

    val isLyricsExpanded: Boolean get() = lyricsExpanded

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(12), dp(5), dp(12), dp(7))
        background = pillBackground(currentAccent, false)
        elevation = dp(18).toFloat()
        clipToOutline = true
        isClickable = true
        isFocusable = true

        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(34))
        header.isClickable = true
        header.isLongClickable = true

        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.clipToOutline = true
        art.layoutParams = LayoutParams(dp(29), dp(29)).apply { marginEnd = dp(10) }
        header.addView(art)

        textColumn.orientation = VERTICAL
        textColumn.gravity = Gravity.CENTER_VERTICAL
        textColumn.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        title.setTextColor(Color.WHITE)
        title.textSize = 15f
        title.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        subtitle.setTextColor(0xFFB6BEC8.toInt())
        subtitle.textSize = 12f
        subtitle.maxLines = 1
        subtitle.ellipsize = TextUtils.TruncateAt.END
        textColumn.addView(title)
        textColumn.addView(subtitle)
        header.addView(textColumn)

        visualizer.layoutParams = LayoutParams(dp(31), dp(25)).apply { marginStart = dp(7) }
        header.addView(visualizer)
        idleMark.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF9BA8B6.toInt())
        }
        idleMark.layoutParams = LayoutParams(dp(5), dp(5)).apply { marginStart = dp(5) }
        idleMark.visibility = GONE
        header.addView(idleMark)
        addView(header)

        idleMessage.text = "AERIS  ·  READY"
        idleMessage.textSize = 10f
        idleMessage.setTextColor(0xFF8793A1.toInt())
        idleMessage.letterSpacing = 0.08f
        idleMessage.gravity = Gravity.CENTER
        idleMessage.setPadding(0, dp(13), 0, dp(10))
        idleMessage.visibility = GONE
        addView(idleMessage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        musicPanel.orientation = VERTICAL
        musicPanel.gravity = Gravity.CENTER_HORIZONTAL
        musicPanel.setPadding(0, dp(8), 0, 0)
        addView(musicPanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        timeRow.orientation = HORIZONTAL
        timeRow.gravity = Gravity.CENTER_VERTICAL
        elapsedText.textSize = 10f
        elapsedText.setTextColor(0xFFE0E5EB.toInt())
        durationText.textSize = 10f
        durationText.setTextColor(0xFFA8B1BC.toInt())
        timeRow.addView(elapsedText)
        timeRow.addView(View(context), LayoutParams(0, 1, 1f))
        timeRow.addView(durationText)
        musicPanel.addView(timeRow)

        seekBar.max = 1000
        seekBar.progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        seekBar.thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        seekBar.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x665F6874)
        seekBar.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(25))
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) elapsedText.text = formatTime((currentMedia?.durationMs ?: 0L) * progress / 1000L)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { isSeeking = true }
            override fun onStopTrackingTouch(bar: SeekBar?) {
                isSeeking = false
                val duration = currentMedia?.durationMs ?: 0L
                if (duration > 0L) MediaBridge.seekTo(context, duration * (bar?.progress ?: 0) / 1000L)
            }
        })
        musicPanel.addView(seekBar)

        controls.orientation = HORIZONTAL
        controls.gravity = Gravity.CENTER
        controls.setPadding(0, dp(4), 0, dp(2))
        musicPanel.addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))

        lyricsBlock.orientation = VERTICAL
        lyricsBlock.gravity = Gravity.CENTER
        lyricsBlock.background = roundedBackground(0x30101822, dp(17).toFloat())
        lyricsBlock.setPadding(dp(10), dp(8), dp(10), dp(8))
        previousLyric.gravity = Gravity.CENTER
        activeLyric.gravity = Gravity.CENTER
        activeLyric.maxLines = 2
        activeLyric.ellipsize = TextUtils.TruncateAt.END
        nextLyric.gravity = Gravity.CENTER
        lyricsBlock.addView(previousLyric, LayoutParams(LayoutParams.MATCH_PARENT, dp(18)))
        lyricsBlock.addView(activeLyric, LayoutParams(LayoutParams.MATCH_PARENT, dp(34)))
        lyricsBlock.addView(nextLyric, LayoutParams(LayoutParams.MATCH_PARENT, dp(18)))
        lyricsBlock.addView(lyricUnavailable, LayoutParams(LayoutParams.MATCH_PARENT, dp(34)))
        lyricsBlock.visibility = GONE
        musicPanel.addView(lyricsBlock, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(7)
        })

        notificationCard.orientation = VERTICAL
        notificationCard.setPadding(dp(10), dp(10), dp(10), dp(9))
        notificationCard.background = roundedBackground(0xFF153026.toInt(), dp(19).toFloat())
        notificationCard.visibility = GONE
        notificationHeader.orientation = HORIZONTAL
        notificationHeader.gravity = Gravity.CENTER_VERTICAL
        notificationIcon.scaleType = ImageView.ScaleType.CENTER_CROP
        notificationIcon.clipToOutline = true
        notificationIcon.background = roundedBackground(0xFF222A32.toInt(), dp(11).toFloat())
        notificationHeader.addView(notificationIcon, LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(9) })
        notificationTextColumn.orientation = VERTICAL
        notificationTextColumn.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        notificationTitle.setTextColor(Color.WHITE)
        notificationTitle.textSize = 12f
        notificationTitle.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        notificationTitle.maxLines = 1
        notificationTitle.ellipsize = TextUtils.TruncateAt.END
        notificationBody.setTextColor(0xFFE1E7E4.toInt())
        notificationBody.textSize = 11f
        notificationBody.maxLines = 2
        notificationBody.ellipsize = TextUtils.TruncateAt.END
        notificationTextColumn.addView(notificationTitle)
        notificationTextColumn.addView(notificationBody)
        notificationHeader.addView(notificationTextColumn)
        notificationCard.addView(notificationHeader)

        notificationNavigation.orientation = HORIZONTAL
        notificationNavigation.gravity = Gravity.CENTER_VERTICAL
        notificationNavigation.setPadding(0, dp(7), 0, 0)
        notificationPosition.textSize = 9f
        notificationPosition.setTextColor(0xFFC7D2CC.toInt())
        notificationPosition.gravity = Gravity.CENTER
        notificationNavigation.addView(glyphButton("‹", "Previous notification") {
            callbacks.onNotificationHistoryChanged((selectedIndex + 1).coerceAtMost((notifications.size - 1).coerceAtLeast(0)))
        })
        notificationNavigation.addView(notificationPosition, LayoutParams(0, dp(34), 1f))
        notificationNavigation.addView(glyphButton("›", "Next notification") {
            callbacks.onNotificationHistoryChanged((selectedIndex - 1).coerceAtLeast(0))
        })
        notificationNavigation.addView(glyphButton("↗", "Reply to notification") {
            selectedNotification?.takeIf { it.canReply }?.let { callbacks.onReplyRequested(it.key) }
        })
        notificationNavigation.addView(glyphButton("×", "Dismiss notification") {
            selectedNotification?.let { callbacks.onDismissNotification(it.key) }
        })
        notificationCard.addView(notificationNavigation)
        addView(notificationCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(9)
        })

        replyArea.orientation = HORIZONTAL
        replyArea.gravity = Gravity.CENTER_VERTICAL
        replyArea.visibility = GONE
        replyArea.setPadding(0, dp(8), 0, 0)
        replyInput.hint = "Message…"
        replyInput.setHintTextColor(0xFF9AA6B2.toInt())
        replyInput.setTextColor(Color.WHITE)
        replyInput.textSize = 13f
        replyInput.setSingleLine(true)
        replyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        replyInput.background = roundedBackground(0xCC111923.toInt(), dp(14).toFloat())
        replyInput.setPadding(dp(12), dp(9), dp(12), dp(9))
        replyInput.layoutParams = LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
        replyArea.addView(replyInput)
        replyArea.addView(glyphButton("↑", "Send reply", true) {
            val targetKey = selectedNotification?.key
            val sent = NotificationStore.sendReply(context, replyInput.text?.toString().orEmpty(), targetKey)
            if (sent) {
                replyInput.setText("")
                (context.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.hideSoftInputFromWindow(replyInput.windowToken, 0)
                callbacks.onReplyFinished()
            }
        })
        addView(replyArea)

        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                runShortcut("shortcut_tap", "expand")
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                runShortcut("shortcut_double", "play_pause")
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                runShortcut("shortcut_long", "next_track")
            }
        }
        val gestureDetector = GestureDetector(context, gestureListener)
        header.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                callbacks.onOutsideTouch()
                true
            } else gestureDetector.onTouchEvent(event)
        }

        val notificationSwipe = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureStartX = event.rawX
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val delta = event.rawX - gestureStartX
                    if (abs(delta) > dp(34)) {
                        val next = if (delta < 0f) selectedIndex + 1 else selectedIndex - 1
                        callbacks.onNotificationHistoryChanged(next.coerceIn(0, (notifications.size - 1).coerceAtLeast(0)))
                    }
                    true
                }
                else -> true
            }
        }
        notificationTextColumn.setOnTouchListener(notificationSwipe)
        notificationIcon.setOnTouchListener(notificationSwipe)
    }

    private fun runShortcut(key: String, fallback: String) {
        if (key == "shortcut_tap" && !AerisPrefs.bool(context, "tap_to_expand", true)) return
        val action = AerisPrefs.string(context, key, fallback)
        if (action != "none") callbacks.onGestureAction(action)
    }

    fun updateContent(
        media: MediaSnapshot?,
        recentNotifications: List<NotificationSnapshot>,
        selectedNotificationIndex: Int,
        expanded: Boolean,
        hideSensitive: Boolean,
        showArtwork: Boolean,
        showVisualizer: Boolean,
        showMediaControls: Boolean,
        showNotificationText: Boolean,
        showReply: Boolean,
        showTrackState: Boolean,
        showSourceApp: Boolean,
        cornerRadius: Int,
        visualizerSpeed: Int,
        showLyrics: Boolean,
        useLiquidGlass: Boolean
    ) {
        currentMedia = media
        notifications = recentNotifications
        selectedIndex = selectedNotificationIndex.coerceIn(0, (notifications.size - 1).coerceAtLeast(0))
        selectedNotification = notifications.getOrNull(selectedIndex)
        this.expanded = expanded
        liquidGlass = useLiquidGlass

        val mediaAccent = media?.artwork?.let(::sampleAccent) ?: 0xFF79C9DA.toInt()
        currentAccent = mediaAccent
        background = pillBackground(mediaAccent, liquidGlass, cornerRadius)
        if (lastSheenMode != liquidGlass || lastSheenAccent != mediaAccent) {
            foreground = if (liquidGlass) GlassSheenDrawable(mediaAccent) else null
            lastSheenMode = liquidGlass
            lastSheenAccent = mediaAccent
        }
        visualizer.speed = (visualizerSpeed.coerceIn(40, 180) / 100f) * 0.24f
        visualizer.isActive = showVisualizer && media?.playing == true && !AerisPrefs.bool(context, "reduce_motion", false)

        val hasMedia = media != null
        val hasNotification = selectedNotification != null
        val showNotificationContent = expanded && hasNotification
        val hasActivity = hasMedia || showNotificationContent
        val showCollapsedArtwork = !expanded && hasMedia && showArtwork
        val showExpandedArtwork = expanded && hasMedia && showArtwork
        art.visibility = if (showCollapsedArtwork || showExpandedArtwork || (!hasMedia && showNotificationContent && showArtwork)) VISIBLE else GONE
        art.layoutParams = LayoutParams(
            dp(if (expanded && hasMedia) 58 else if (expanded && hasNotification && !hasMedia) 42 else 29),
            dp(if (expanded && hasMedia) 58 else if (expanded && hasNotification && !hasMedia) 42 else 29)
        ).apply { marginEnd = dp(10) }
        art.background = imageShape(if (expanded) dp(15).toFloat() else 1000f)
        art.clipToOutline = true
        val artworkKey = when {
            hasMedia -> "media|${media!!.packageName}|${media.title}|${media.artwork?.generationId ?: 0}"
            showNotificationContent -> "notification|${selectedNotification!!.packageName}"
            else -> ""
        }
        if (artworkKey != lastArtworkKey) {
            if (hasMedia && media!!.artwork != null) art.setImageBitmap(media.artwork)
            else if (hasMedia) setAppIcon(media!!.packageName, art)
            else if (hasNotification) setAppIcon(selectedNotification!!.packageName, art)
            else art.setImageDrawable(null)
            lastArtworkKey = artworkKey
        }

        textColumn.visibility = if (hasActivity) VISIBLE else GONE
        idleMark.visibility = if (!hasActivity) VISIBLE else GONE
        visualizer.visibility = if (showVisualizer) VISIBLE else GONE
        title.text = when {
            hasMedia -> media!!.title
            showNotificationContent -> if (hideSensitive) "New notification" else selectedNotification!!.title.ifBlank { appName(selectedNotification!!.packageName) }
            else -> ""
        }
        subtitle.text = when {
            hasMedia -> if (showTrackState) "${media!!.artist}  ·  ${if (media.playing) "Playing" else "Paused"}" else media!!.artist
            showNotificationContent && showSourceApp -> appName(selectedNotification!!.packageName)
            showNotificationContent -> "Notification"
            else -> ""
        }
        header.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(if (expanded) 58 else 34))

        idleMessage.visibility = if (expanded && !hasActivity) VISIBLE else GONE
        idleMessage.text = if (expanded && !hasActivity) "AERIS  ·  READY" else ""
        musicPanel.visibility = if (expanded && hasMedia) VISIBLE else GONE
        if (hasMedia) {
            val duration = media!!.durationMs
            val position = media.positionMs
            if (!isSeeking) {
                seekBar.progress = if (duration > 0L) ((position.toDouble() / duration.toDouble()) * 1000.0).toInt().coerceIn(0, 1000) else 0
                elapsedText.text = formatTime(position)
            }
            durationText.text = formatTime(duration)
            timeRow.visibility = if (duration > 0L) VISIBLE else GONE
            seekBar.visibility = if (media.canSeek && duration > 0L) VISIBLE else GONE
            rebuildControls(media, showMediaControls, showLyrics)
            val raw = if (showLyrics) media.lyrics else null
            if (raw != cachedLyricsRaw) {
                cachedLyricsRaw = raw
                cachedLyricsLines = LyricsEngine.parse(raw)
            }
            val lyricFrame = LyricsEngine.frame(cachedLyricsLines, position)
            val hasPlain = cachedLyricsLines.isEmpty() && !raw.isNullOrBlank()
            val hasLyricsData = lyricFrame != null || hasPlain
            lyricsBlock.visibility = if (expanded && lyricsExpanded && showLyrics) VISIBLE else GONE
            previousLyric.visibility = if (lyricFrame != null) VISIBLE else GONE
            activeLyric.visibility = if (lyricFrame != null) VISIBLE else GONE
            nextLyric.visibility = if (lyricFrame != null) VISIBLE else GONE
            lyricUnavailable.visibility = if (!hasLyricsData) VISIBLE else GONE
            val signature = "${media.packageName}|${media.title}|${lyricFrame?.current}|${lyricFrame?.activeWordIndex}|$hasPlain"
            if (signature != lastLyricsKey || lyricFrame?.current != lastLyricSignature) {
                previousLyric.text = lyricFrame?.previous.orEmpty()
                nextLyric.text = lyricFrame?.next.orEmpty()
                activeLyric.text = makeLyricSpan(lyricFrame)
                lyricUnavailable.text = "♫   Lyrics aren't exposed by this player"
                activeLyric.alpha = 0.65f
                activeLyric.animate().alpha(1f).setDuration(210L).start()
                lastLyricsKey = signature
                lastLyricSignature = lyricFrame?.current.orEmpty()
            }
            if (hasPlain) {
                activeLyric.text = raw.orEmpty()
                activeLyric.maxLines = 3
            } else activeLyric.maxLines = 2
        } else {
            lyricsBlock.visibility = GONE
            lyricsExpanded = false
            timeRow.visibility = GONE
            seekBar.visibility = GONE
            controls.removeAllViews()
        }

        val notification = selectedNotification
        notificationCard.visibility = if (expanded && notification != null && showNotificationText) VISIBLE else GONE
        replyArea.visibility = if (expanded && notification != null && notification.canReply && showReply && replyArea.tag == "editing") VISIBLE else GONE
        if (notification != null) {
            val key = notification.key
            if (key != lastNotificationKey) {
                val accent = notificationAccent(notification.packageName)
                notificationCard.background = notificationBackground(accent)
                setAppIcon(notification.packageName, notificationIcon)
                notificationIcon.contentDescription = appName(notification.packageName)
                replyArea.tag = if (replyArea.tag == "editing" && notification.canReply) "editing" else ""
                lastNotificationKey = key
            }
            notificationTitle.text = if (hideSensitive) "New notification" else notification.title.ifBlank { appName(notification.packageName) }
            notificationBody.text = if (hideSensitive) "Message preview hidden" else notification.text.ifBlank { appName(notification.packageName) }
            notificationPosition.text = if (notifications.size > 1) "${selectedIndex + 1}  /  ${notifications.size}" else "NEW MESSAGE"
        } else {
            lastNotificationKey = ""
            replyArea.visibility = GONE
            replyArea.tag = ""
        }

        if (!expanded) {
            replyArea.visibility = GONE
            lyricsExpanded = false
        }
        val compactTop = dp(if (expanded) 11 else 5)
        setPadding(dp(12), compactTop, dp(12), dp(if (expanded) 12 else 7))
    }

    private fun rebuildControls(media: MediaSnapshot, showControls: Boolean, showLyrics: Boolean) {
        controls.removeAllViews()
        if (!showControls) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        controls.addView(glyphButton("⏮", "Previous", media.canPrevious) { MediaBridge.previous(context) }, weightedParams())
        controls.addView(glyphButton(if (media.playing) "Ⅱ" else "▶", if (media.playing) "Pause" else "Play", media.canToggle, true) { MediaBridge.toggle(context) }, weightedParams())
        controls.addView(glyphButton("⏭", "Next", media.canNext) { MediaBridge.next(context) }, weightedParams())
        controls.addView(glyphButton("−", "Volume down") {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
        }, weightedParams())
        controls.addView(glyphButton("+", "Volume up") {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
        }, weightedParams())
        if (showLyrics) {
            controls.addView(glyphButton(if (lyricsExpanded) "♫" else "♪", "Toggle lyrics", true) {
                lyricsExpanded = !lyricsExpanded
                lyricsBlock.visibility = if (lyricsExpanded) VISIBLE else GONE
                lyricsBlock.alpha = 0.5f
                lyricsBlock.animate().alpha(1f).setDuration(220L).start()
                callbacks.onContentSizeChanged()
            }, weightedParams())
        }
    }

    private fun weightedParams() = LayoutParams(0, dp(43), 1f).apply {
        marginStart = dp(2)
        marginEnd = dp(2)
    }

    fun showReplyEditor() {
        replyArea.tag = "editing"
        replyArea.visibility = VISIBLE
        replyInput.requestFocus()
        (context.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.showSoftInput(replyInput, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideReplyEditor() {
        replyArea.tag = ""
        replyArea.visibility = GONE
        replyInput.clearFocus()
    }

    private fun makeLyricSpan(frame: dev.aeris.island.media.LyricsFrame?): CharSequence {
        if (frame == null || frame.words.isEmpty()) return frame?.current.orEmpty()
        val content = frame.words.joinToString(" ") { it.text }
        val spannable = SpannableString(content)
        var cursor = 0
        frame.words.forEachIndexed { index, word ->
            val start = cursor
            val end = (start + word.text.length).coerceAtMost(content.length)
            spannable.setSpan(ForegroundColorSpan(if (index <= frame.activeWordIndex) Color.WHITE else 0xFF9BA8B6.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (index == frame.activeWordIndex && end > start) {
                spannable.setSpan(BackgroundColorSpan(0x5566D7EE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            cursor = end + 1
        }
        return spannable
    }

    private fun glyphButton(glyph: String, description: String, enabled: Boolean = true, primary: Boolean = false, action: () -> Unit): View {
        val frame = FrameLayout(context).apply {
            background = roundedBackground(
                if (primary) 0xFFF3F7FA.toInt() else if (liquidGlass) 0x36FFFFFF else 0xFF1A222C.toInt(),
                dp(if (primary) 17 else 13).toFloat()
            )
            alpha = if (enabled) 1f else 0.36f
            isEnabled = enabled
            isClickable = enabled
            contentDescription = description
            setOnClickListener { if (enabled) action() }
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        val label = TextView(context).apply {
            text = glyph
            textSize = if (primary) 19f else 17f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(if (primary) 0xFF071018.toInt() else Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        frame.addView(label, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        return frame
    }

    private fun lyricText(size: Float, color: Int) = TextView(context).apply {
        textSize = size
        setTextColor(color)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    private fun pillBackground(accent: Int, glass: Boolean, radius: Int = 42) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (glass) intArrayOf(
            withAlpha(0xFF2A303A.toInt(), 0xB8),
            withAlpha(blend(accent, 0xFF10141B.toInt(), 0.38f), 0xC8)
        ) else intArrayOf(0xFF05070B.toInt(), blend(accent, 0xFF10151C.toInt(), 0.42f))
    ).apply {
        cornerRadius = dp(radius.coerceIn(18, 54)).toFloat()
        setStroke(dp(1), if (glass) 0xBFFFFFFF.toInt() else 0xFF29313C.toInt())
    }

    private fun notificationBackground(accent: Int) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(blend(accent, 0xFF101820.toInt(), 0.66f), blend(accent, 0xFF0C1118.toInt(), 0.48f))
    ).apply {
        cornerRadius = dp(19).toFloat()
        setStroke(dp(1), 0x55FFFFFF)
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
        setStroke(dp(1), 0x25FFFFFF)
    }

    private fun imageShape(radius: Float) = GradientDrawable().apply {
        shape = if (radius > 500f) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        if (radius <= 500f) cornerRadius = radius
        setColor(0xFF202832.toInt())
    }

    private fun sampleAccent(bitmap: Bitmap): Int {
        return try {
            val pixel = bitmap.getPixel((bitmap.width / 2).coerceIn(0, bitmap.width - 1), (bitmap.height / 2).coerceIn(0, bitmap.height - 1))
            val r = Color.red(pixel).coerceIn(28, 230)
            val g = Color.green(pixel).coerceIn(28, 230)
            val b = Color.blue(pixel).coerceIn(28, 230)
            blend(Color.rgb(r, g, b), 0xFF0C1118.toInt(), 0.25f)
        } catch (_: Exception) { 0xFF79C9DA.toInt() }
    }

    private fun notificationAccent(packageName: String): Int = when {
        packageName.contains("whatsapp", true) -> 0xFF20B86A.toInt()
        packageName.contains("instagram", true) -> 0xFFB84F9A.toInt()
        packageName.contains("youtube", true) -> 0xFFE53935.toInt()
        packageName.contains("telegram", true) -> 0xFF2A9DDB.toInt()
        packageName.contains("messenger", true) -> 0xFF3978E8.toInt()
        packageName.contains("discord", true) -> 0xFF6875E8.toInt()
        else -> 0xFF537D92.toInt()
    }

    private fun blend(foreground: Int, background: Int, amount: Float): Int {
        val f = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(foreground) * f + Color.red(background) * (1f - f)).toInt(),
            (Color.green(foreground) * f + Color.green(background) * (1f - f)).toInt(),
            (Color.blue(foreground) * f + Color.blue(background) * (1f - f)).toInt()
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255) and 0xFF) shl 24)

    private fun setAppIcon(packageName: String, target: ImageView) {
        try { target.setImageDrawable(context.packageManager.getApplicationIcon(packageName)) }
        catch (_: Exception) { target.setImageDrawable(null) }
    }

    private fun appName(packageName: String): String = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) { packageName.substringAfterLast('.') }

    private fun formatTime(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val seconds = ms / 1000L
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private class VisualizerStrip(context: Context) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE1E8EE.toInt()
        strokeWidth = dp(2f)
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    var isActive = false
        set(value) { field = value; invalidate() }
    var speed = 0.22f
    private var phase = 0f

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val count = 5
        val gap = width.toFloat() / (count + 1)
        for (i in 0 until count) {
            val wave = if (isActive) abs(sin(phase + i * 0.78f)).toFloat() else 0.035f
            val h = if (isActive) height * (0.18f + wave * 0.62f) else dp(2f)
            val x = gap * (i + 1)
            canvas.drawLine(x, (height - h) / 2f, x, (height + h) / 2f, paint)
        }
        if (isActive && visibility == VISIBLE && isAttachedToWindow) {
            phase += speed
            postInvalidateDelayed(58L)
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}


/** Subtle optical highlights layered above the translucent surface in Liquid Glass mode. */
private class GlassSheenDrawable(private val accent: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()
    private val rect = RectF()
    private var multiplier = 255
    private var filter: ColorFilter? = null

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        shape.reset()
        shape.addRoundRect(rect, rect.height() * 0.48f, rect.height() * 0.48f, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(shape)

        paint.style = Paint.Style.FILL
        paint.alpha = (0x54 * multiplier / 255).coerceIn(0, 255)
        paint.shader = LinearGradient(
            0f, rect.top, 0f, rect.top + rect.height() * 0.58f,
            intArrayOf(0x72FFFFFF, 0x20FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        paint.colorFilter = filter
        canvas.drawRect(rect, paint)

        paint.shader = RadialGradient(
            rect.left + rect.width() * 0.24f,
            rect.top + rect.height() * 0.18f,
            rect.width() * 0.62f,
            intArrayOf(withAlpha(Color.WHITE, 0x40), withAlpha(accent, 0x16), 0x00000000),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (rect.height() * 0.012f).coerceAtLeast(1f)
        paint.color = withAlpha(Color.WHITE, 0x58 * multiplier / 255)
        paint.colorFilter = filter
        canvas.drawRoundRect(rect.left + 1f, rect.top + 1f, rect.right - 1f, rect.bottom - 1f,
            rect.height() * 0.48f, rect.height() * 0.48f, paint)

        paint.strokeWidth = (rect.height() * 0.018f).coerceAtLeast(1f)
        paint.color = withAlpha(Color.WHITE, 0x42 * multiplier / 255)
        val bubbleY = rect.top + rect.height() * 0.20f
        canvas.drawCircle(rect.left + rect.width() * 0.76f, bubbleY, rect.height() * 0.085f, paint)
        canvas.drawCircle(rect.left + rect.width() * 0.84f, bubbleY + rect.height() * 0.035f, rect.height() * 0.035f, paint)

        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.colorFilter = null
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { multiplier = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255) and 0xFF) shl 24)

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
