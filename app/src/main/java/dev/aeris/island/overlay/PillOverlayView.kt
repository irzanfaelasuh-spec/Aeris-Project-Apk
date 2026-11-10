package dev.aeris.island.overlay

import android.content.Context
import android.media.AudioManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context.INPUT_METHOD_SERVICE
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.HorizontalScrollView
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

class PillOverlayView(context: Context, private val callbacks: Callbacks) : LinearLayout(context) {
    interface Callbacks {
        fun onReplyRequested()
        fun onReplyFinished()
        fun onDismissNotification()
        fun onToggleExpanded()
    }

    private val header = LinearLayout(context)
    private val art = ImageView(context)
    private val textColumn = LinearLayout(context)
    private val title = TextView(context)
    private val subtitle = TextView(context)
    private val visualizer = VisualizerStrip(context)
    private val idleEyes = IdleEyesView(context)
    private val lyricsBlock = LinearLayout(context)
    private val previousLyric = lyricText(10f, 0xFF858B96.toInt())
    private val activeLyric = lyricText(15f, Color.WHITE)
    private val nextLyric = lyricText(10f, 0xFF858B96.toInt())
    private val lyricUnavailable = lyricText(9f, 0xFF8C95A1.toInt()).apply { gravity = Gravity.CENTER }
    private val plainLyrics = lyricText(10f, 0xFFE2E6EC.toInt()).apply { maxLines = 3; gravity = Gravity.CENTER }
    private val timeRow = LinearLayout(context)
    private val elapsedText = TextView(context)
    private val durationText = TextView(context)
    private val seekBar = SeekBar(context)
    private val controlsViewport = HorizontalScrollView(context)
    private val controls = LinearLayout(context)
    private val notificationLine = TextView(context)
    private val replyArea = LinearLayout(context)
    private val replyInput = EditText(context)
    private var expanded = false
    private var currentMedia: MediaSnapshot? = null
    private var currentNotification: NotificationSnapshot? = null
    private var isSeeking = false
    private var lastLyricsKey = ""
    private var lastControlsKey = ""
    private var cachedLyricsRaw: String? = null
    private var cachedLyricsLines = emptyList<dev.aeris.island.media.LyricLine>()
    private var lastLyricFrameSignature = ""
    private var lastArtworkKey = ""

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        background = pillBackground()
        elevation = dp(15).toFloat()
        clipToOutline = true
        setPadding(dp(12), dp(5), dp(12), dp(5))

        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(34))

        art.layoutParams = LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(9) }
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.background = roundedBackground(0xFF202832.toInt(), dp(9).toFloat())
        art.clipToOutline = true
        header.addView(art)

        textColumn.orientation = VERTICAL
        textColumn.gravity = Gravity.CENTER_VERTICAL
        textColumn.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        title.textSize = 11.5f
        title.setTextColor(Color.WHITE)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        subtitle.textSize = 9f
        subtitle.setTextColor(0xFFA7B0BC.toInt())
        subtitle.maxLines = 1
        subtitle.ellipsize = android.text.TextUtils.TruncateAt.END
        textColumn.addView(title)
        textColumn.addView(subtitle)
        header.addView(textColumn)

        visualizer.layoutParams = LayoutParams(dp(28), dp(22)).apply { marginStart = dp(5) }
        header.addView(visualizer)

        idleEyes.layoutParams = LayoutParams(dp(44), dp(24)).apply { gravity = Gravity.CENTER }
        idleEyes.visibility = GONE
        header.addView(idleEyes)
        addView(header)

        lyricsBlock.orientation = VERTICAL
        lyricsBlock.gravity = Gravity.CENTER
        lyricsBlock.setPadding(dp(2), dp(6), dp(2), 0)
        previousLyric.gravity = Gravity.CENTER
        activeLyric.gravity = Gravity.CENTER
        activeLyric.maxLines = 2
        activeLyric.ellipsize = android.text.TextUtils.TruncateAt.END
        nextLyric.gravity = Gravity.CENTER
        lyricsBlock.addView(previousLyric, LayoutParams(LayoutParams.MATCH_PARENT, dp(16)))
        lyricsBlock.addView(activeLyric, LayoutParams(LayoutParams.MATCH_PARENT, dp(28)))
        lyricsBlock.addView(nextLyric, LayoutParams(LayoutParams.MATCH_PARENT, dp(16)))
        lyricsBlock.addView(plainLyrics, LayoutParams(LayoutParams.MATCH_PARENT, dp(43)))
        lyricsBlock.addView(lyricUnavailable, LayoutParams(LayoutParams.MATCH_PARENT, dp(22)))
        lyricsBlock.visibility = GONE
        addView(lyricsBlock)

        timeRow.orientation = HORIZONTAL
        timeRow.gravity = Gravity.CENTER_VERTICAL
        timeRow.setPadding(dp(1), dp(3), dp(1), 0)
        elapsedText.textSize = 9f
        elapsedText.setTextColor(0xFFDDE2E8.toInt())
        durationText.textSize = 9f
        durationText.setTextColor(0xFF8A929D.toInt())
        timeRow.addView(elapsedText)
        timeRow.addView(View(context), LayoutParams(0, 1, 1f))
        timeRow.addView(durationText)
        timeRow.visibility = GONE
        addView(timeRow)

        seekBar.max = 1000
        seekBar.progressTintList = android.content.res.ColorStateList.valueOf(0xFFF3F5F8.toInt())
        seekBar.thumbTintList = android.content.res.ColorStateList.valueOf(0xFFF3F5F8.toInt())
        seekBar.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF454B55.toInt())
        seekBar.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(23))
        seekBar.visibility = GONE
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
        addView(seekBar)

        controls.orientation = HORIZONTAL
        controls.gravity = Gravity.CENTER
        controls.setPadding(dp(2), dp(4), dp(2), dp(2))
        controlsViewport.isHorizontalScrollBarEnabled = false
        controlsViewport.overScrollMode = View.OVER_SCROLL_NEVER
        controlsViewport.fillViewport = true
        controlsViewport.visibility = GONE
        controlsViewport.addView(controls, ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)))
        addView(controlsViewport, LayoutParams(LayoutParams.MATCH_PARENT, dp(43)))

        notificationLine.textSize = 10f
        notificationLine.setTextColor(0xFFCDD3DC.toInt())
        notificationLine.maxLines = 2
        notificationLine.ellipsize = android.text.TextUtils.TruncateAt.END
        notificationLine.setPadding(dp(2), dp(7), dp(2), dp(2))
        notificationLine.visibility = GONE
        addView(notificationLine)

        replyArea.orientation = HORIZONTAL
        replyArea.gravity = Gravity.CENTER_VERTICAL
        replyArea.visibility = GONE
        replyArea.setPadding(0, dp(7), 0, 0)
        replyInput.hint = "Write a reply"
        replyInput.setHintTextColor(0xFF7F8996.toInt())
        replyInput.setTextColor(Color.WHITE)
        replyInput.textSize = 12f
        replyInput.setSingleLine(true)
        replyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        replyInput.background = roundedBackground(0xFF1A2029.toInt(), dp(13).toFloat())
        replyInput.setPadding(dp(10), dp(8), dp(10), dp(8))
        replyInput.layoutParams = LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(7) }
        replyArea.addView(replyInput)
        replyArea.addView(actionButton("SEND", "↗") {
            val sent = NotificationStore.sendReply(context, replyInput.text?.toString().orEmpty())
            if (sent) {
                replyInput.setText("")
                (context.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.hideSoftInputFromWindow(replyInput.windowToken, 0)
                callbacks.onReplyFinished()
            }
        })
        addView(replyArea)

        setOnClickListener {
            if (AerisPrefs.bool(context, "tap_to_expand", true)) {
                if (AerisPrefs.bool(context, "haptic_feedback", false)) performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                callbacks.onToggleExpanded()
            }
        }
    }

    fun updateContent(
        media: MediaSnapshot?,
        notification: NotificationSnapshot?,
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
        notificationCount: Int
    ) {
        currentMedia = media
        currentNotification = notification
        this.expanded = expanded
        background = pillBackground(cornerRadius)
        visualizer.speed = (visualizerSpeed.coerceIn(40, 180) / 100f) * 0.22f
        val hasActivity = media != null || notification != null
        idleEyes.visibility = if (!hasActivity) VISIBLE else GONE
        header.gravity = if (!hasActivity) Gravity.CENTER else Gravity.CENTER_VERTICAL
        textColumn.visibility = if (hasActivity) VISIBLE else GONE
        visualizer.visibility = if (showVisualizer && media?.playing == true && hasActivity) VISIBLE else GONE
        visualizer.isActive = media?.playing == true

        if (media != null) {
            title.text = media.title
            subtitle.text = if (showTrackState) {
                "${media.artist}  ·  ${if (media.playing) "Playing" else "Paused"}"
            } else media.artist
            if (showArtwork) {
                art.visibility = VISIBLE
                val artworkKey = "${media.packageName}|${media.title}|${media.artwork?.generationId ?: 0}"
                if (artworkKey != lastArtworkKey) {
                    if (media.artwork != null) art.setImageBitmap(media.artwork)
                    else try { art.setImageDrawable(context.packageManager.getApplicationIcon(media.packageName)) }
                    catch (_: Exception) { art.setImageDrawable(null) }
                    lastArtworkKey = artworkKey
                }
            } else {
                art.visibility = GONE
            }
        } else if (notification != null) {
            title.text = notification.title.ifBlank { notification.packageName.substringAfterLast('.') }
            subtitle.text = if (showSourceApp) notification.packageName.substringAfterLast('.') else "Notification"
            if (showArtwork) {
                art.visibility = VISIBLE
                val artworkKey = "${notification.packageName}|notification"
                if (artworkKey != lastArtworkKey) {
                    try { art.setImageDrawable(context.packageManager.getApplicationIcon(notification.packageName)) }
                    catch (_: Exception) { art.setImageDrawable(null) }
                    lastArtworkKey = artworkKey
                }
            } else {
                art.visibility = GONE
            }
        } else {
            title.text = ""
            subtitle.text = ""
            art.visibility = GONE
            art.setImageDrawable(null)
            lastArtworkKey = ""
        }

        val duration = media?.durationMs ?: 0L
        val position = media?.positionMs ?: 0L
        if (!isSeeking) {
            seekBar.progress = if (duration > 0L) ((position.toDouble() / duration.toDouble()) * 1000.0).toInt().coerceIn(0, 1000) else 0
            elapsedText.text = formatTime(position)
        }
        durationText.text = formatTime(duration)
        val canSeek = media?.canSeek == true && duration > 0L
        timeRow.visibility = if (expanded && media != null && duration > 0L) VISIBLE else GONE
        seekBar.visibility = if (expanded && canSeek) VISIBLE else GONE

        val rawLyrics = if (showLyrics) media?.lyrics else null
        if (rawLyrics != cachedLyricsRaw) {
            cachedLyricsRaw = rawLyrics
            cachedLyricsLines = LyricsEngine.parse(rawLyrics)
        }
        val lyricLines = cachedLyricsLines
        val lyricKey = "${media?.packageName}|${media?.title}|${rawLyrics?.hashCode()}"
        val lyricFrame = LyricsEngine.frame(lyricLines, position)
        val hasPlainLyrics = lyricLines.isEmpty() && !rawLyrics.isNullOrBlank()
        lyricsBlock.visibility = if (expanded && media != null && (lyricFrame != null || hasPlainLyrics || showLyrics)) VISIBLE else GONE
        previousLyric.visibility = if (lyricFrame != null) VISIBLE else GONE
        activeLyric.visibility = if (lyricFrame != null) VISIBLE else GONE
        nextLyric.visibility = if (lyricFrame != null) VISIBLE else GONE
        plainLyrics.visibility = if (hasPlainLyrics) VISIBLE else GONE
        lyricUnavailable.visibility = if (lyricFrame == null && !hasPlainLyrics && showLyrics && media != null) VISIBLE else GONE
        val lyricFrameSignature = lyricFrame?.let { "${it.current}|${it.activeWordIndex}" } ?: "plain:$hasPlainLyrics"
        if (lyricKey != lastLyricsKey || lyricFrameSignature != lastLyricFrameSignature || hasPlainLyrics) {
            previousLyric.text = lyricFrame?.previous.orEmpty()
            nextLyric.text = lyricFrame?.next.orEmpty()
            activeLyric.text = makeLyricSpan(lyricFrame)
            plainLyrics.text = rawLyrics.orEmpty()
            lyricUnavailable.text = "LYRICS NOT EXPOSED BY PLAYER"
            if (lyricFrameSignature != lastLyricFrameSignature && lyricFrame != null) {
                activeLyric.alpha = 0.45f
                activeLyric.animate().alpha(1f).setDuration(170L).start()
            }
            lastLyricsKey = lyricKey
            lastLyricFrameSignature = lyricFrameSignature
        }

        val notificationText = notification?.let {
            if (hideSensitive) "New notification from ${it.packageName.substringAfterLast('.')}"
            else listOf(it.title, it.text).filter { value -> value.isNotBlank() }.joinToString(" — ")
        }.orEmpty()
        notificationLine.text = when {
            notificationText.isBlank() -> ""
            notificationCount > 1 -> "$notificationText   ·   +${notificationCount - 1} more"
            else -> notificationText
        }
        notificationLine.visibility = if (expanded && showNotificationText && notificationText.isNotBlank()) VISIBLE else GONE

        val controlsKey = "${expanded}|${media?.packageName}|${media?.playing}|${media?.canToggle}|${media?.canPrevious}|${media?.canNext}|${media?.canSeek}|${media?.customActions?.joinToString { it.action }}|${showMediaControls}|${showReply}|${notification?.canReply}|${notification?.key}"
        if (controlsKey != lastControlsKey) {
            controls.removeAllViews()
            if (expanded && media != null && showMediaControls) {
                controlsViewport.visibility = VISIBLE
                controls.addView(actionButton("−10", "↶", media.canSeek) { MediaBridge.jumpBy(context, -10_000L) })
                controls.addView(actionButton("PREV", "|◂", media.canPrevious) { MediaBridge.previous(context) })
                controls.addView(actionButton(if (media.playing) "PAUSE" else "PLAY", if (media.playing) "Ⅱ" else "▶", media.canToggle, true) { MediaBridge.toggle(context) })
                controls.addView(actionButton("NEXT", "▸|", media.canNext) { MediaBridge.next(context) })
                controls.addView(actionButton("+10", "↷", media.canSeek) { MediaBridge.jumpBy(context, 10_000L) })
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                controls.addView(actionButton("VOL−", "−") { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0) })
                controls.addView(actionButton("VOL+", "+") { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0) })
                media.customActions.take(1).forEach { item ->
                    controls.addView(actionButton(item.name.take(8).uppercase(Locale.getDefault()), "⋯") {
                        MediaBridge.customAction(context, item.action)
                    })
                }
            } else if (expanded && showReply && notification?.canReply == true) {
                controlsViewport.visibility = VISIBLE
            } else {
                controlsViewport.visibility = GONE
            }
            if (expanded && showReply && notification?.canReply == true) {
                controls.addView(actionButton("REPLY", "↗") { callbacks.onReplyRequested() })
            }
            if (expanded && notification != null) {
                controlsViewport.visibility = VISIBLE
                controls.addView(actionButton("DISMISS", "×") { callbacks.onDismissNotification() })
            }
            lastControlsKey = controlsKey
        }
        if (!expanded) {
            replyArea.visibility = GONE
            controlsViewport.visibility = GONE
        }
        setPadding(dp(12), if (expanded) dp(10) else dp(5), dp(12), if (expanded) dp(9) else dp(5))
    }

    fun showReplyEditor() {
        replyArea.visibility = VISIBLE
        expanded = true
        replyInput.requestFocus()
        (context.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.showSoftInput(replyInput, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideReplyEditor() {
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
            val color = if (index <= frame.activeWordIndex) Color.WHITE else 0xFF777F8A.toInt()
            spannable.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (index == frame.activeWordIndex && end > start) {
                spannable.setSpan(BackgroundColorSpan(0x3349C9E8), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            cursor = end + 1
        }
        return spannable
    }

    private fun actionButton(label: String, symbol: String, enabled: Boolean = true, primary: Boolean = false, action: () -> Unit): View {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(if (primary) 0xFFF1F4F7.toInt() else 0xFF1C222B.toInt(), dp(13).toFloat())
            alpha = if (enabled) 1f else 0.35f
            isEnabled = enabled
            isClickable = enabled
            isFocusable = enabled
            setPadding(dp(if (primary) 13 else 9), dp(5), dp(if (primary) 13 else 9), dp(5))
            setOnClickListener { if (enabled) action() }
        }
        val glyph = TextView(context).apply {
            text = symbol
            textSize = if (primary) 16f else 13f
            gravity = Gravity.CENTER
            setTextColor(if (primary) 0xFF101318.toInt() else Color.WHITE)
            includeFontPadding = false
        }
        container.addView(glyph, LayoutParams(LayoutParams.WRAP_CONTENT, dp(17)))
        val caption = TextView(context).apply {
            text = label
            textSize = 7f
            gravity = Gravity.CENTER
            setTextColor(if (primary) 0xFF222831.toInt() else 0xFFB9C0C9.toInt())
            includeFontPadding = false
            letterSpacing = 0.02f
        }
        container.addView(caption, LayoutParams(LayoutParams.WRAP_CONTENT, dp(9)))
        val params = LayoutParams(LayoutParams.WRAP_CONTENT, dp(34)).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }
        container.layoutParams = params
        return container
    }

    private fun lyricText(size: Float, color: Int) = TextView(context).apply {
        textSize = size
        setTextColor(color)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    private fun pillBackground(radius: Int = 40) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius.coerceIn(18, 52)).toFloat()
        setColor(0xFF07090E.toInt())
        setStroke(dp(1), 0xFF252B34.toInt())
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val seconds = ms / 1000L
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private class IdleEyesView(context: Context) : View(context) {
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF3F6FA.toInt() }
    private val irisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF78DDF0.toInt() }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF071018.toInt() }
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val eyeRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = System.currentTimeMillis() / 1000.0
        val look = (sin(t * 0.72) * 0.34 + sin(t * 0.19) * 0.18).toFloat()
        val blink = abs(sin(t * 0.63 + 1.1)).let { if (it > 0.985) 0.12f else 1f }
        val eyeW = width * 0.27f
        val eyeH = height * 0.61f * blink
        val centerY = height / 2f
        val centers = listOf(width * 0.34f, width * 0.66f)
        centers.forEach { cx ->
            eyeRect.set(cx - eyeW / 2f, centerY - eyeH / 2f, cx + eyeW / 2f, centerY + eyeH / 2f)
            canvas.drawRoundRect(eyeRect, eyeH, eyeH, eyePaint)
            val irisR = eyeH * 0.35f
            val irisX = cx + look * eyeW * 0.45f
            canvas.drawCircle(irisX, centerY, irisR, irisPaint)
            canvas.drawCircle(irisX, centerY, irisR * 0.55f, pupilPaint)
            canvas.drawCircle(irisX - irisR * 0.22f, centerY - irisR * 0.25f, irisR * 0.16f, glintPaint)
        }
        if (visibility == VISIBLE && isAttachedToWindow) postInvalidateDelayed(48L)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) invalidate()
    }
}

private class VisualizerStrip(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF83DFF1.toInt()
        strokeWidth = dp(2.1f)
        strokeCap = Paint.Cap.ROUND
    }
    var isActive = false
        set(value) { field = value; invalidate() }
    private var phase = 0f
    var speed = 0.22f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = 6
        val gap = width.toFloat() / (count + 1)
        for (i in 0 until count) {
            val wave = if (isActive) abs(sin(phase + i * 0.72f)).toFloat() else 0.18f
            val h = height * (0.22f + wave * 0.55f)
            val x = gap * (i + 1)
            canvas.drawLine(x, (height - h) / 2f, x, (height + h) / 2f, paint)
        }
        if (isActive) {
            phase += speed
            postInvalidateDelayed(90L)
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
