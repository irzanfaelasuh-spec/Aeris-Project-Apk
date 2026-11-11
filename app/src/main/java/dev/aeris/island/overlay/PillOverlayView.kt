package dev.aeris.island.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.SystemClock
import android.text.TextUtils
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context.INPUT_METHOD_SERVICE
import android.widget.EditText
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

class PillOverlayView(context: Context, private val callbacks: Callbacks) : LinearLayout(context) {
    interface Callbacks {
        fun toggleExpanded()
        fun browseNotifications(direction: Int)
        fun dismissNotification()
        fun replyRequested()
        fun replyFinished()
        fun lyricsExpandedChanged(expanded: Boolean)
    }

    private val handler = android.os.Handler(context.mainLooper)
    private val header = LinearLayout(context)
    private val artwork = ImageView(context)
    private val labels = LinearLayout(context)
    private val title = TextView(context)
    private val artist = TextView(context)
    private val visualizer = VisualizerStrip(context)
    private val lyricsBox = LinearLayout(context)
    private val previousLyric = lyricText(11f, 0xFF9DA3AF.toInt())
    private val currentLyric = lyricText(18f, Color.WHITE)
    private val nextLyric = lyricText(11f, 0xFF9DA3AF.toInt())
    private val noLyrics = lyricText(23f, 0xFFE7DDFB.toInt())
    private val progressRow = LinearLayout(context)
    private val elapsed = labelText(10f, 0xFFE8E6EC.toInt())
    private val remaining = labelText(10f, 0xFFB3AFB8.toInt())
    private val seek = SeekBar(context)
    private val actions = LinearLayout(context)
    private val notificationBody = TextView(context)
    private val replyRow = LinearLayout(context)
    private val replyInput = EditText(context)

    private var media: MediaSnapshot? = null
    private var notification: NotificationSnapshot? = null
    private var expanded = false
    private var lyricsExpanded = false
    private var screenRecording = false
    private var historyIndex = 0
    private var historyCount = 0
    private var tint = 0xFF272D3A.toInt()
    private var tintKey = ""
    private var artworkKey = ""
    private var lyricKey = ""
    private var seeking = false
    private var lastTapAt = 0L
    private var touchDownX = 0f

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        clipToOutline = true
        elevation = dp(22).toFloat()
        setPadding(dp(14), dp(5), dp(14), dp(5))
        background = pillBackground(tint, 42)

        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.layoutParams = LayoutParams(MATCH_PARENT, dp(38))

        artwork.scaleType = ImageView.ScaleType.CENTER_CROP
        artwork.layoutParams = LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(10) }
        artwork.background = roundedBackground(0xFF2D313B, 100f)
        artwork.clipToOutline = true
        header.addView(artwork)

        labels.orientation = VERTICAL
        labels.gravity = Gravity.CENTER_VERTICAL
        labels.layoutParams = LayoutParams(0, MATCH_PARENT, 1f)
        title.textSize = 12f
        title.setTextColor(Color.WHITE)
        title.typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        artist.textSize = 9.5f
        artist.setTextColor(0xFFB8B7BF.toInt())
        artist.maxLines = 1
        artist.ellipsize = TextUtils.TruncateAt.END
        labels.addView(title)
        labels.addView(artist)
        header.addView(labels)

        visualizer.layoutParams = LayoutParams(dp(31), dp(22)).apply { marginStart = dp(6) }
        header.addView(visualizer)
        addView(header)

        header.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { touchDownX = event.x; true }
                MotionEvent.ACTION_UP -> {
                    val delta = event.x - touchDownX
                    if (abs(delta) > dp(42) && historyCount > 1) {
                        callbacks.browseNotifications(if (delta < 0f) 1 else -1)
                    } else {
                        performIslandTap()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }

        lyricsBox.orientation = VERTICAL
        lyricsBox.gravity = Gravity.CENTER
        lyricsBox.setPadding(dp(4), dp(12), dp(4), dp(6))
        previousLyric.gravity = Gravity.CENTER
        currentLyric.gravity = Gravity.CENTER
        currentLyric.maxLines = 3
        nextLyric.gravity = Gravity.CENTER
        noLyrics.gravity = Gravity.CENTER
        lyricsBox.addView(previousLyric, LayoutParams(MATCH_PARENT, dp(22)))
        lyricsBox.addView(currentLyric, LayoutParams(MATCH_PARENT, dp(62)))
        lyricsBox.addView(nextLyric, LayoutParams(MATCH_PARENT, dp(22)))
        lyricsBox.addView(noLyrics, LayoutParams(MATCH_PARENT, dp(54)))
        lyricsBox.visibility = GONE
        addView(lyricsBox)

        progressRow.orientation = HORIZONTAL
        progressRow.gravity = Gravity.CENTER_VERTICAL
        progressRow.addView(elapsed)
        progressRow.addView(View(context), LayoutParams(0, 1, 1f))
        progressRow.addView(remaining)
        progressRow.visibility = GONE
        addView(progressRow)

        seek.max = 1000
        seek.progressTintList = android.content.res.ColorStateList.valueOf(0xFFF5F2F7.toInt())
        seek.thumbTintList = android.content.res.ColorStateList.valueOf(0xFFF5F2F7.toInt())
        seek.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x667B7C85)
        seek.layoutParams = LayoutParams(MATCH_PARENT, dp(24))
        seek.visibility = GONE
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) elapsed.text = formatTime((media?.durationMs ?: 0L) * progress / 1000L)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { seeking = true }
            override fun onStopTrackingTouch(bar: SeekBar?) {
                seeking = false
                media?.let { MediaBridge.seek(context, it.durationMs * (bar?.progress ?: 0) / 1000L) }
            }
        })
        addView(seek)

        actions.orientation = HORIZONTAL
        actions.gravity = Gravity.CENTER
        actions.setPadding(dp(1), dp(6), dp(1), dp(4))
        actions.visibility = GONE
        addView(actions, LayoutParams(MATCH_PARENT, dp(47)))

        notificationBody.textSize = 11f
        notificationBody.setTextColor(0xFFF1EEF5.toInt())
        notificationBody.maxLines = 3
        notificationBody.ellipsize = TextUtils.TruncateAt.END
        notificationBody.setPadding(dp(5), dp(9), dp(5), dp(5))
        notificationBody.visibility = GONE
        addView(notificationBody)

        replyRow.orientation = HORIZONTAL
        replyRow.gravity = Gravity.CENTER_VERTICAL
        replyRow.setPadding(0, dp(8), 0, dp(2))
        replyRow.visibility = GONE
        replyInput.hint = "Reply…"
        replyInput.setHintTextColor(0xFFAAA6B0.toInt())
        replyInput.setTextColor(Color.WHITE)
        replyInput.textSize = 13f
        replyInput.setSingleLine(true)
        replyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        replyInput.background = roundedBackground(0x442B303B, 16f)
        replyInput.setPadding(dp(12), dp(10), dp(12), dp(10))
        replyInput.layoutParams = LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
        replyRow.addView(replyInput)
        replyRow.addView(iconButton("↗", true) {
            val current = notification ?: return@iconButton
            if (NotificationStore.sendReply(context, current.key, replyInput.text?.toString().orEmpty())) {
                replyInput.setText("")
                (context.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.hideSoftInputFromWindow(replyInput.windowToken, 0)
                callbacks.replyFinished()
            }
        })
        addView(replyRow)

        setOnClickListener { performIslandTap() }
    }

    fun update(media: MediaSnapshot?, notification: NotificationSnapshot?, isExpanded: Boolean,
               index: Int, count: Int, recording: Boolean) {
        this.media = media
        this.notification = notification
        expanded = isExpanded
        historyIndex = index
        historyCount = count
        screenRecording = recording
        val active = media != null || notification != null || recording
        val newTintKey = "${media?.packageName}|${media?.title}|${media?.artwork?.generationId}|${notification?.packageName}|$recording"
        if (newTintKey != tintKey) {
            tint = when {
                media?.artwork != null -> sampleArtworkTint(media.artwork)
                notification != null -> appTint(notification.packageName)
                recording -> 0xFF8B252B.toInt()
                else -> 0xFF272D3A.toInt()
            }
            tintKey = newTintKey
        }
        background = pillBackground(tint, if (expanded) 36 else 42)
        setPadding(dp(14), dp(if (expanded) 13 else 5), dp(14), dp(if (expanded) 12 else 5))

        header.layoutParams = LayoutParams(MATCH_PARENT, dp(if (expanded) 62 else 38))
        val artSize = dp(if (expanded) 58 else 30)
        artwork.layoutParams = LayoutParams(artSize, artSize).apply { marginEnd = dp(11) }
        artwork.background = roundedBackground(0xFF2D313B, if (expanded) 15f else 100f)
        labels.visibility = if (active) VISIBLE else GONE
        title.text = when {
            media != null -> media.title
            notification != null -> notification.title
            recording -> "Screen recording"
            else -> ""
        }
        artist.text = when {
            media != null -> "${media.artist}  ·  ${if (media.playing) "Playing" else "Paused"}"
            notification != null -> notification.packageName.substringAfterLast('.')
            recording -> "● REC  ·  AERIS capture indicator"
            else -> ""
        }
        title.textSize = if (expanded) 15f else 12f
        artist.textSize = if (expanded) 11f else 9.5f

        val newArtKey = "${media?.packageName}|${media?.title}|${media?.artwork?.generationId}|${notification?.packageName}"
        if (newArtKey != artworkKey) {
            when {
                media?.artwork != null -> artwork.setImageBitmap(media.artwork)
                notification != null -> try { artwork.setImageDrawable(context.packageManager.getApplicationIcon(notification.packageName)) }
                    catch (_: Exception) { artwork.setImageDrawable(null) }
                else -> artwork.setImageDrawable(null)
            }
            artworkKey = newArtKey
        }

        visualizer.visibility = VISIBLE
        visualizer.active = media?.playing == true
        visualizer.tint = if (recording) 0xFFFF6068.toInt() else 0xFFF1D9C5.toInt()
        if (!active) {
            title.text = ""
            artist.text = ""
            visualizer.active = false
        }

        val duration = media?.durationMs ?: 0L
        val position = media?.positionMs ?: 0L
        if (!seeking) {
            seek.progress = if (duration > 0) ((position.toDouble() / duration) * 1000).toInt().coerceIn(0, 1000) else 0
            elapsed.text = formatTime(position)
        }
        remaining.text = if (duration > position) "−${formatTime(duration - position)}" else formatTime(duration)
        progressRow.visibility = if (expanded && media != null && duration > 0) VISIBLE else GONE
        seek.visibility = if (expanded && media?.canSeek == true && duration > 0) VISIBLE else GONE

        val lyricLines = LyricsEngine.parse(media?.lyrics)
        val frame = LyricsEngine.frame(lyricLines, position)
        lyricsBox.visibility = if (expanded && lyricsExpanded && media != null) VISIBLE else GONE
        previousLyric.visibility = if (frame != null) VISIBLE else GONE
        currentLyric.visibility = if (frame != null) VISIBLE else GONE
        nextLyric.visibility = if (frame != null) VISIBLE else GONE
        noLyrics.visibility = if (frame == null) VISIBLE else GONE
        if (frame != null) {
            previousLyric.text = frame.previous
            currentLyric.text = styledLyric(frame)
            nextLyric.text = frame.next
            val signature = "${frame.current}|${frame.activeWordIndex}"
            if (signature != lyricKey) {
                currentLyric.alpha = .55f
                currentLyric.animate().alpha(1f).setDuration(220L).start()
                lyricKey = signature
            }
        } else noLyrics.text = "♫"

        val body = notification?.let {
            if (AerisPrefs.bool(context, "hide_sensitive", false)) "New notification · preview hidden"
            else listOf(it.title, it.text).filter(String::isNotBlank).joinToString(" — ")
        }.orEmpty()
        val appIcon = notification?.let { try { context.packageManager.getApplicationIcon(it.packageName) } catch (_: Exception) { null } }
        appIcon?.setBounds(0, 0, dp(19), dp(19))
        notificationBody.setCompoundDrawables(appIcon, null, null, null)
        notificationBody.compoundDrawablePadding = dp(8)
        val page = if (historyCount > 1) "   ·   ${historyIndex + 1}/$historyCount" else ""
        notificationBody.text = body + page
        notificationBody.visibility = if (expanded && notification != null) VISIBLE else GONE

        actions.removeAllViews()
        actions.visibility = if (expanded) VISIBLE else GONE
        if (expanded && media != null) {
            actions.addView(iconButton("↶", false, media.canSeek) { MediaBridge.seek(context, (position - 10_000L).coerceAtLeast(0L)) })
            actions.addView(iconButton("◂◂", false, media.canPrevious) { MediaBridge.previous(context) })
            actions.addView(iconButton(if (media.playing) "Ⅱ" else "▶", true, media.canToggle) { MediaBridge.toggle(context) })
            actions.addView(iconButton("▸▸", false, media.canNext) { MediaBridge.next(context) })
            actions.addView(iconButton("♫", false) {
                lyricsExpanded = !lyricsExpanded
                callbacks.lyricsExpandedChanged(lyricsExpanded)
            })
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            actions.addView(iconButton("−", false) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0) })
            actions.addView(iconButton("+", false) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0) })
        }
        if (expanded && notification != null) {
            if (notification.canReply) actions.addView(iconButton("↗", false) { callbacks.replyRequested() })
            actions.addView(iconButton("×", false) { callbacks.dismissNotification() })
        }
        if (!expanded) {
            actions.visibility = GONE
            replyRow.visibility = GONE
        }
    }

    fun showReplyEditor() {
        replyRow.visibility = VISIBLE
        replyInput.requestFocus()
        (context.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.showSoftInput(replyInput, InputMethodManager.SHOW_IMPLICIT)
    }
    fun hideReplyEditor() { replyRow.visibility = GONE; replyInput.clearFocus() }


    private fun performIslandTap() {
        val now = SystemClock.uptimeMillis()
        if (lastTapAt != 0L && now - lastTapAt < 290L) {
            handler.removeCallbacksAndMessages(null)
            lastTapAt = 0L
            performShortcut(AerisPrefs.string(context, "double_tap_action", "play_pause"))
        } else {
            lastTapAt = now
            handler.postDelayed({
                if (lastTapAt == now) {
                    lastTapAt = 0L
                    performShortcut(AerisPrefs.string(context, "single_tap_action", "expand"))
                }
            }, 295L)
        }
    }

    private fun performShortcut(action: String) {
        when (action) {
            "expand" -> callbacks.toggleExpanded()
            "play_pause" -> MediaBridge.toggle(context)
            "next" -> MediaBridge.next(context)
            "previous" -> MediaBridge.previous(context)
            "volume_up" -> (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            "volume_down" -> (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            "lyrics" -> { lyricsExpanded = !lyricsExpanded; callbacks.lyricsExpandedChanged(lyricsExpanded) }
            "dismiss" -> callbacks.dismissNotification()
            else -> callbacks.toggleExpanded()
        }
    }

    private fun iconButton(symbol: String, primary: Boolean, enabled: Boolean = true, action: () -> Unit): View = TextView(context).apply {
        text = symbol
        textSize = if (primary) 19f else 16f
        gravity = Gravity.CENTER
        setTextColor(if (primary) 0xFF11131A.toInt() else Color.WHITE)
        background = roundedBackground(if (primary) 0xFFECE8F1.toInt() else 0x33212632, 16f)
        alpha = if (enabled) 1f else .35f
        isEnabled = enabled
        isClickable = enabled
        setPadding(dp(8), dp(5), dp(8), dp(5))
        layoutParams = LayoutParams(0, dp(37), 1f).apply { marginStart = dp(2); marginEnd = dp(2) }
        setOnClickListener { if (enabled) action() }
    }

    private fun styledLyric(frame: dev.aeris.island.media.LyricsFrame): CharSequence {
        if (frame.words.isEmpty()) return frame.current
        val content = frame.words.joinToString(" ") { it.text }
        val styled = SpannableString(content)
        var cursor = 0
        frame.words.forEachIndexed { index, word ->
            val start = cursor
            val end = (start + word.text.length).coerceAtMost(content.length)
            val color = if (index <= frame.activeWordIndex) Color.WHITE else 0xFF8C929F.toInt()
            if (end > start) {
                styled.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (index == frame.activeWordIndex) {
                    styled.setSpan(BackgroundColorSpan(0x334DC9E8), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    styled.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            cursor = end + 1
        }
        return styled
    }

    private fun lyricText(size: Float, color: Int) = TextView(context).apply {
        textSize = size; setTextColor(color); maxLines = 2; ellipsize = TextUtils.TruncateAt.END; includeFontPadding = false
    }
    private fun labelText(size: Float, color: Int) = TextView(context).apply { textSize = size; setTextColor(color); includeFontPadding = false }

    private fun pillBackground(color: Int, radius: Int) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (AerisPrefs.bool(context, "liquid_glass", false)) intArrayOf(blend(color, 0xFFCBD8EF.toInt(), .32f), blend(color, 0xFF171B27.toInt(), .42f), 0xE9090C13.toInt())
        else intArrayOf(blend(color, 0xFF090A0E.toInt(), .40f), 0xFF090A0E.toInt())
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), if (AerisPrefs.bool(context, "liquid_glass", false)) 0x66E0E9F8 else 0x553C4049)
    }

    private fun sampleArtworkTint(bitmap: Bitmap): Int {
        if (bitmap.isRecycled || bitmap.width < 1 || bitmap.height < 1) return 0xFF343845.toInt()
        return try {
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            val sx = (bitmap.width / 10).coerceAtLeast(1); val sy = (bitmap.height / 10).coerceAtLeast(1)
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    val c = bitmap.getPixel(x, y); r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++; x += sx
                }
                y += sy
            }
            if (n == 0L) 0xFF343845.toInt() else blend(Color.rgb((r/n).toInt(), (g/n).toInt(), (b/n).toInt()), 0xFF10131A.toInt(), .48f)
        } catch (_: Exception) { 0xFF343845.toInt() }
    }
    private fun appTint(pkg: String) = when {
        pkg.contains("whatsapp", true) -> 0xFF0B6949.toInt()
        pkg.contains("youtube", true) -> 0xFF9A2025.toInt()
        pkg.contains("instagram", true) -> 0xFF85406F.toInt()
        pkg.contains("telegram", true) -> 0xFF1A6086.toInt()
        pkg.contains("discord", true) -> 0xFF424B9A.toInt()
        pkg.contains("messenger", true) -> 0xFF2859B4.toInt()
        else -> 0xFF293342.toInt()
    }
    private fun blend(a: Int, b: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        return Color.rgb((Color.red(a)*(1-f)+Color.red(b)*f).toInt(), (Color.green(a)*(1-f)+Color.green(b)*f).toInt(), (Color.blue(a)*(1-f)+Color.blue(b)*f).toInt())
    }
    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius.toInt()).toFloat(); setColor(color) }
    private fun formatTime(ms: Long): String { val s = ms.coerceAtLeast(0L) / 1000L; return String.format(Locale.getDefault(), "%02d:%02d", s / 60, s % 60) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private class VisualizerStrip(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.2f * resources.displayMetrics.density; strokeCap = Paint.Cap.ROUND; color = 0xFFF1D9C5.toInt() }
        var active = false
            set(value) { field = value; invalidate() }
        var tint: Int = 0xFFF1D9C5.toInt()
            set(value) { field = value; paint.color = value; invalidate() }
        private var phase = 0.0
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val gap = width.toFloat() / 7f
            for (i in 0 until 6) {
                val wave = if (active) abs(sin(phase + i * .72)).toFloat() else .10f
                val h = height * (.22f + wave * .56f)
                val x = gap * (i + 1)
                canvas.drawLine(x, (height - h) / 2f, x, (height + h) / 2f, paint)
            }
            if (active && isAttachedToWindow) { phase += .30; postInvalidateDelayed(55L) }
        }
    }
}
