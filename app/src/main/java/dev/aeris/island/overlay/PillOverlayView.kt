package dev.aeris.island.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context.INPUT_METHOD_SERVICE
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import android.text.InputType
import android.graphics.Color
import android.view.MotionEvent
import dev.aeris.island.AerisPrefs
import dev.aeris.island.media.MediaBridge
import dev.aeris.island.media.MediaSnapshot
import dev.aeris.island.notifications.NotificationSnapshot
import dev.aeris.island.notifications.NotificationStore

class PillOverlayView(context: Context, private val callbacks: Callbacks) : LinearLayout(context) {
    interface Callbacks {
        fun onReplyRequested()
        fun onReplyFinished()
        fun onToggleExpanded()
    }

    private val title = TextView(context)
    private val subtitle = TextView(context)
    private val art = ImageView(context)
    private val visualizer = VisualizerStrip(context)
    private val progress = ProgressStrip(context)
    private val notificationLine = TextView(context)
    private val controls = LinearLayout(context)
    private val replyArea = LinearLayout(context)
    private val replyInput = EditText(context)
    private var expanded = false
    private var currentMedia: MediaSnapshot? = null
    private var currentNotification: NotificationSnapshot? = null
    private var hideSensitive = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        background = pillBackground()
        elevation = dp(12).toFloat()
        clipToOutline = true
        setPadding(dp(12), dp(7), dp(12), dp(7))

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        art.layoutParams = LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(9) }
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.background = roundedBackground(0xFF202832.toInt(), dp(8).toFloat())
        header.addView(art)

        val textColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        title.textSize = 11.5f
        title.setTextColor(Color.WHITE)
        title.maxLines = 1
        subtitle.textSize = 9f
        subtitle.setTextColor(0xFFA7B0BC.toInt())
        subtitle.maxLines = 1
        textColumn.addView(title)
        textColumn.addView(subtitle)
        header.addView(textColumn)

        visualizer.layoutParams = LayoutParams(dp(30), dp(22))
        header.addView(visualizer)
        addView(header)

        progress.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(3)).apply {
            topMargin = dp(8)
            bottomMargin = dp(1)
        }
        progress.visibility = GONE
        addView(progress)

        notificationLine.textSize = 10f
        notificationLine.setTextColor(0xFFCBD3DC.toInt())
        notificationLine.maxLines = 2
        notificationLine.visibility = GONE
        notificationLine.setPadding(0, dp(8), 0, dp(3))
        addView(notificationLine)

        controls.orientation = HORIZONTAL
        controls.gravity = Gravity.CENTER
        controls.visibility = GONE
        controls.setPadding(0, dp(7), 0, dp(2))
        addView(controls)

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
        replyArea.addView(actionText("SEND") {
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
        visualizerSpeed: Int
    ) {
        this.currentMedia = media
        this.currentNotification = notification
        this.expanded = expanded
        this.hideSensitive = hideSensitive
        background = pillBackground(cornerRadius)
        visualizer.speed = (visualizerSpeed.coerceIn(40, 180) / 100f) * 0.22f
        val hasMedia = media != null
        title.text = when {
            hasMedia -> media!!.title
            notification != null -> "AERIS  /  ACTIVITY"
            else -> "AERIS  /  READY"
        }
        subtitle.text = when {
            hasMedia -> if (showTrackState) listOf(media!!.artist, if (media.playing) "Playing" else "Paused").joinToString("  ·  ") else media!!.artist
            notification != null -> if (showSourceApp) notification.packageName.substringAfterLast('.') else "Notification received"
            else -> "Tap to expand"
        }
        val sourcePackage = media?.packageName ?: notification?.packageName
        if (showArtwork && media?.artwork != null) {
            art.visibility = VISIBLE
            art.setImageBitmap(media.artwork)
        } else if (showArtwork && sourcePackage != null) {
            art.visibility = VISIBLE
            try { art.setImageDrawable(context.packageManager.getApplicationIcon(sourcePackage)) }
            catch (_: Exception) { art.setImageDrawable(null) }
        } else {
            art.visibility = GONE
            art.setImageDrawable(null)
        }
        visualizer.visibility = if (showVisualizer && media?.playing == true) VISIBLE else GONE
        visualizer.isActive = media?.playing == true
        val duration = media?.durationMs ?: 0L
        progress.fraction = if (duration > 0L) (media?.positionMs ?: 0L).toFloat() / duration.toFloat() else 0f
        progress.visibility = if (expanded && duration > 0L) VISIBLE else GONE

        val notificationText = notification?.let {
            if (hideSensitive) "New notification from ${it.packageName.substringAfterLast('.')}"
            else listOf(it.title, it.text).filter { value -> value.isNotBlank() }.joinToString(" — ")
        }.orEmpty()
        notificationLine.text = notificationText
        notificationLine.visibility = if (expanded && showNotificationText && notificationText.isNotBlank()) VISIBLE else GONE

        controls.removeAllViews()
        if (expanded && showMediaControls && media != null) {
            controls.visibility = VISIBLE
            controls.addView(actionText("PREV") { MediaBridge.previous(context) })
            controls.addView(actionText(if (media.playing) "PAUSE" else "PLAY") { MediaBridge.toggle(context) })
            controls.addView(actionText("NEXT") { MediaBridge.next(context) })
            if (showReply && notification?.canReply == true) {
                controls.addView(actionText("REPLY") { callbacks.onReplyRequested() })
            }
        } else if (expanded && showReply && notification?.canReply == true) {
            controls.visibility = VISIBLE
            controls.addView(actionText("REPLY") { callbacks.onReplyRequested() })
        } else {
            controls.visibility = GONE
        }

        if (!expanded) {
            replyArea.visibility = GONE
        }
        val height = if (expanded) dp(98) else dp(28)
        val padding = if (expanded) dp(12) else dp(5)
        setPadding(dp(12), padding, dp(12), padding)
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

    private fun actionText(label: String, action: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 9.5f
        letterSpacing = 0.06f
        setTextColor(0xFF76E4F6.toInt())
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(7), dp(10), dp(7))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun pillBackground(radius: Int = 40) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius.coerceIn(18, 52)).toFloat()
        setColor(0xFF05070B.toInt())
        setStroke(dp(1), 0xFF242B34.toInt())
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private class ProgressStrip(context: Context) : View(context) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF27313B.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(2.4f)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF69DFF2.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(2.4f)
    }
    var fraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val y = height / 2f
        val end = width.toFloat()
        canvas.drawLine(0f, y, end, y, backgroundPaint)
        canvas.drawLine(0f, y, end * fraction, y, progressPaint)
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}

private class VisualizerStrip(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF69DFF2.toInt()
        strokeWidth = dp(2.1f)
        strokeCap = Paint.Cap.ROUND
    }
    var isActive = false
        set(value) {
            field = value
            invalidate()
        }
    private var phase = 0f
    var speed = 0.22f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = 6
        val gap = width.toFloat() / (count + 1)
        for (i in 0 until count) {
            val wave = if (isActive) kotlin.math.abs(kotlin.math.sin(phase + i * 0.72f)) else 0.18f
            val h = height * (0.22f + wave * 0.55f)
            val x = gap * (i + 1)
            canvas.drawLine(x, (height - h) / 2f, x, (height + h) / 2f, paint)
        }
        if (isActive) {
            phase += speed
            postInvalidateDelayed(110)
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
