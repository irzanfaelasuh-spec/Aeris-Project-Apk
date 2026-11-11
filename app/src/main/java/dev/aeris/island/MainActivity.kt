package dev.aeris.island

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.aeris.island.notifications.AerisNotificationListener
import dev.aeris.island.overlay.IslandService

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    private lateinit var statusLabel: TextView
    private lateinit var startButton: Button
    private lateinit var bootProgress: ProgressBar
    private lateinit var bootLabel: TextView
    private var bootStartedAt = 0L
    private var bootRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 10, 15)
        window.navigationBarColor = Color.rgb(8, 10, 15)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF080A0F.toInt()); isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(25), dp(19), dp(32))
        }
        scroll.addView(content)
        setContentView(scroll)

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        titleCol.addView(text("A E R I S", 10f, 0xFFC9B8F4.toInt(), true).apply { letterSpacing = .28f })
        titleCol.addView(text("Dynamic interface", 29f, Color.WHITE, true))
        titleCol.addView(text("VERSION 3.0  ·  LIQUID MOTION", 9f, 0xFF9DA4B2.toInt(), true).apply { letterSpacing = .12f })
        top.addView(titleCol)
        top.addView(TextView(this).apply {
            text = "A"; textSize = 23f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = gradient(0xFF4B416B.toInt(), 0xFF1A2230.toInt(), 18f)
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50))
        })
        content.addView(top)
        spacer(18)

        val hero = card()
        hero.addView(row {
            addView(column {
                addView(text("LIVE ISLAND", 9f, 0xFF9DA4B2.toInt(), true).apply { letterSpacing = .15f })
                statusLabel = text("Checking status…", 18f, Color.WHITE, true)
                addView(statusLabel)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@MainActivity).apply {
                text = "●"; textSize = 17f; setTextColor(0xFF7DD6B0.toInt())
            })
        })
        spacer(12, hero)
        hero.addView(islandPreview())
        spacer(13, hero)
        val infoRow = row {
            addView(metric("♫", "MUSIC"), LinearLayout.LayoutParams(0, -2, 1f))
            addView(metric("◉", "ALERTS"), LinearLayout.LayoutParams(0, -2, 1f))
            addView(metric("♪", "LYRICS"), LinearLayout.LayoutParams(0, -2, 1f))
        }
        hero.addView(infoRow)
        spacer(13, hero)
        startButton = Button(this).apply {
            text = "INITIALIZE AERIS"; isAllCaps = false; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF11131A.toInt()); background = gradient(0xFFC9B8F4.toInt(), 0xFFA9C8E8.toInt(), 18f)
            setOnClickListener { if (AerisPrefs.bool(this@MainActivity, "service_active", false)) stopIsland() else startBootSequence() }
        }
        hero.addView(startButton, LinearLayout.LayoutParams(-1, dp(49)))
        bootProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progress = 0; visibility = View.GONE
        }
        hero.addView(bootProgress, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(12) })
        bootLabel = text("AERIS CORE READY", 9f, 0xFFAAAEB8.toInt(), false).apply { visibility = View.GONE }
        hero.addView(bootLabel)
        content.addView(hero)

        section("PERMISSIONS")
        content.addView(permissionRow("Display over other apps", "Required to show the island above compatible apps.") {
            openSystemSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        content.addView(permissionRow("Notification access", "Used for notification history, media sessions and supported quick replies.") {
            openSystemSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        section("PERSONALIZATION")
        content.addView(settingRow("Change to LIQUID GLASS", "Translucent gradients, highlight borders and system blur when supported.", AerisPrefs.bool(this, "liquid_glass", false)) {
            AerisPrefs.setBool(this, "liquid_glass", it)
            buildScreen()
            refreshStatus()
        })
        addToggle("Music detection", "Read Android media sessions.", "music_enabled", true)
        addToggle("Online synced lyrics", "Optional: send track title, artist and duration to LRCLIB when the player does not expose lyrics. Off by default.", "online_lyrics", false)
        addToggle("Notification detection", "Show incoming notification cards and history.", "notifications_enabled", true)
        addToggle("Auto-expand music", "Open briefly when a new track starts.", "auto_expand_music", true)
        addToggle("Auto-expand notifications", "Show a compact alert peek for two seconds.", "auto_expand_notifications", true)
        addToggle("Hide sensitive previews", "Replace message text with a privacy label.", "hide_sensitive", false)
        addToggle("Landscape mode", "Recalculate size when the display rotates.", "landscape_enabled", true)
        addToggle("Reduce motion", "Shorter and calmer resize transitions.", "reduce_motion", false)
        addToggle("Low-power refresh", "Reduce the background UI refresh rate.", "low_power", false)

        section("GESTURE SHORTCUTS")
        content.addView(shortcutRow("Single tap", "single_tap_action", "expand"))
        content.addView(shortcutRow("Double tap", "double_tap_action", "play_pause"))

        section("TIMING & LAYOUT")
        content.addView(numberRow("Notification preview", "notification_peek_seconds", 2, 8, 2, "sec"))
        content.addView(numberRow("Auto-expand duration", "expand_seconds", 2, 30, 8, "sec"))
        content.addView(numberRow("Collapsed width", "collapsed_width", 150, 280, 190, "dp"))
        content.addView(numberRow("Expanded width", "expanded_width", 250, 370, 350, "dp"))
        content.addView(numberRow("Top offset", "top_offset", 0, 40, 10, "dp"))

        section("DESIGN DETAILS")
        val detail = card()
        detail.addView(text("Artwork-led surfaces", 13f, Color.WHITE, true))
        detail.addView(text("The album image is circular while compact and becomes a rounded square when expanded. Its sampled colors tint the lower background. Notification surfaces use an app-aware accent.", 11f, 0xFF9DA4B2.toInt(), false))
        spacer(9, detail)
        detail.addView(text("Lyrics have a dedicated panel so the track title, controls and text do not compete for the same vertical space. Missing lyrics show ♫.", 11f, 0xFF9DA4B2.toInt(), false))
        spacer(9, detail)
        detail.addView(text("Idle bars stay still. The visualizer animates only while an active media session is playing.", 11f, 0xFF9DA4B2.toInt(), false))
        spacer(9, detail)
        detail.addView(text("Notification history is memory-only. Swipe the expanded island left/right to browse. Quick reply only appears when the source app provides RemoteInput.", 11f, 0xFF9DA4B2.toInt(), false))
        content.addView(detail)
        refreshStatus()
    }

    private fun refreshStatus() {
        if (!::statusLabel.isInitialized) return
        val running = AerisPrefs.bool(this, "service_active", false)
        statusLabel.text = if (running) "Running smoothly" else "Ready to start"
        startButton.text = if (running) "STOP AERIS" else "INITIALIZE AERIS"
    }

    private fun startBootSequence() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this).setTitle("Overlay access needed")
                .setMessage("Allow AERIS to display over other apps before starting the island.")
                .setPositiveButton("Open settings") { _, _ -> openSystemSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                .setNegativeButton("Cancel", null).show()
            return
        }
        if (!AerisNotificationListener.isEnabled(this)) {
            AlertDialog.Builder(this).setTitle("Notification access needed")
                .setMessage("Enable AERIS notification access to connect media sessions and read incoming alerts.")
                .setPositiveButton("Open settings") { _, _ -> openSystemSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                .setNegativeButton("Cancel", null).show()
            return
        }
        bootProgress.progress = 0
        bootProgress.visibility = View.VISIBLE
        bootLabel.visibility = View.VISIBLE
        bootLabel.text = "AERIS BOOTLOADER  /  V3.0"
        startButton.isEnabled = false
        startButton.text = "INITIALIZING…"
        bootStartedAt = android.os.SystemClock.elapsedRealtime()
        val job = object : Runnable {
            override fun run() {
                val elapsed = android.os.SystemClock.elapsedRealtime() - bootStartedAt
                bootProgress.progress = ((elapsed.coerceAtMost(4000L) / 4f) * 1000).toInt()
                val stage = when {
                    elapsed < 900 -> "MOUNTING LOCAL CONFIGURATION"
                    elapsed < 1800 -> "BINDING MEDIA SESSION BRIDGE"
                    elapsed < 2800 -> "PREPARING NOTIFICATION HISTORY"
                    elapsed < 3800 -> "OPTIMIZING GLASS DISPLAY LAYER"
                    else -> "STARTING FOREGROUND SERVICE"
                }
                bootLabel.text = "› $stage"
                if (elapsed < 4000L) mainHandler.postDelayed(this, 40L) else {
                    try {
                        ContextCompat.startForegroundService(this@MainActivity,
                            Intent(this@MainActivity, IslandService::class.java).setAction(IslandService.ACTION_START))
                        AerisPrefs.setBool(this@MainActivity, "service_active", true)
                        bootLabel.text = "✓ SYSTEM READY"
                    } catch (_: Exception) {
                        bootLabel.text = "! ANDROID BLOCKED THE SERVICE"
                    }
                    startButton.isEnabled = true
                    refreshStatus()
                }
            }
        }
        bootRunnable = job
        mainHandler.post(job)
    }

    private fun stopIsland() {
        stopService(Intent(this, IslandService::class.java))
        AerisPrefs.setBool(this, "service_active", false)
        refreshStatus()
    }

    private fun addToggle(title: String, detail: String, key: String, default: Boolean) {
        val initial = AerisPrefs.bool(this, key, default)
        content.addView(settingRow(title, detail, initial) { AerisPrefs.setBool(this, key, it) })
    }

    private fun settingRow(title: String, detail: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val panel = card()
        val line = row {
            addView(column {
                addView(text(title, 12f, Color.WHITE, true))
                addView(text(detail, 10f, 0xFF9DA4B2.toInt(), false))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Switch(this@MainActivity).apply {
                isChecked = initial
                setOnCheckedChangeListener { _, checked -> onChange(checked) }
            })
        }
        panel.addView(line)
        return panel
    }

    private fun permissionRow(title: String, detail: String, action: () -> Unit): View {
        val panel = card()
        panel.addView(row {
            addView(column {
                addView(text(title, 12f, Color.WHITE, true))
                addView(text(detail, 10f, 0xFF9DA4B2.toInt(), false))
                val enabled = if (title.startsWith("Display")) Settings.canDrawOverlays(this@MainActivity) else AerisNotificationListener.isEnabled(this@MainActivity)
                addView(text(if (enabled) "ENABLED" else "NEEDS ACCESS", 8f, if (enabled) 0xFF7DD6B0.toInt() else 0xFFE9B86B.toInt(), true))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@MainActivity).apply {
                text = "OPEN"; textSize = 9f; setTextColor(0xFFC9B8F4.toInt()); setPadding(dp(8), dp(8), dp(4), dp(8)); setOnClickListener { action() }
            })
        })
        return panel
    }

    private fun shortcutRow(title: String, key: String, default: String): View {
        val panel = card()
        val actions = listOf(
            "expand" to "Expand / collapse", "play_pause" to "Play / pause", "next" to "Next track",
            "previous" to "Previous track", "volume_up" to "Volume up", "volume_down" to "Volume down",
            "lyrics" to "Toggle lyrics", "dismiss" to "Dismiss notification"
        )
        val current = AerisPrefs.string(this, key, default)
        panel.addView(row {
            addView(text(title, 12f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(actions.firstOrNull { it.first == current }?.second ?: "Expand / collapse", 10f, 0xFFC9B8F4.toInt(), true))
            addView(text("  ▾", 12f, 0xFF9DA4B2.toInt(), true))
        }.apply {
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle(title)
                    .setItems(actions.map { it.second }.toTypedArray()) { _, which ->
                        AerisPrefs.setString(this@MainActivity, key, actions[which].first)
                        rebuildAfterShortcut()
                    }.show()
            }
        })
        return panel
    }

    private fun rebuildAfterShortcut() {
        buildScreen()
        refreshStatus()
    }

    private fun numberRow(title: String, key: String, min: Int, max: Int, default: Int, unit: String): View {
        val panel = card()
        val value = AerisPrefs.int(this, key, default).coerceIn(min, max)
        panel.addView(row {
            addView(text(title, 11f, Color.WHITE, false), LinearLayout.LayoutParams(0, -2, 1f))
            addView(text("$value $unit", 10f, 0xFFC9B8F4.toInt(), true))
            addView(text("  −", 18f, Color.WHITE, true).apply { setPadding(dp(7), 0, dp(7), 0); setOnClickListener { AerisPrefs.setInt(this@MainActivity, key, (AerisPrefs.int(this@MainActivity, key, default) - 1).coerceIn(min, max)); rebuildAfterShortcut() } })
            addView(text("+", 18f, Color.WHITE, true).apply { setPadding(dp(7), 0, dp(2), 0); setOnClickListener { AerisPrefs.setInt(this@MainActivity, key, (AerisPrefs.int(this@MainActivity, key, default) + 1).coerceIn(min, max)); rebuildAfterShortcut() } })
        })
        return panel
    }

    private fun islandPreview(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = if (AerisPrefs.bool(this@MainActivity, "liquid_glass", false)) gradient(0xBB48516A.toInt(), 0xCC111722.toInt(), 30f) else gradient(0xFF26232B.toInt(), 0xFF0A0C11.toInt(), 30f)
        setPadding(dp(13), dp(10), dp(13), dp(10))
        addView(TextView(this@MainActivity).apply {
            text = "♫"; textSize = 17f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF7D719C.toInt()) }
            layoutParams = LinearLayout.LayoutParams(dp(31), dp(31)).apply { marginEnd = dp(10) }
        })
        addView(column {
            addView(text("Your music, at a glance", 11f, Color.WHITE, true))
            addView(text("Artwork · controls · live lyrics", 9f, 0xFFB8B4C0.toInt(), false))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(text("▮▮▮▮", 12f, if (AerisPrefs.bool(this@MainActivity, "liquid_glass", false)) 0xFFA9C8E8.toInt() else 0xFFC9B8F4.toInt(), true))
    }

    private fun metric(icon: String, label: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
        background = rounded(0xFF1C202B.toInt(), 14f); setPadding(dp(10), dp(8), dp(10), dp(8))
        addView(text(icon, 16f, 0xFFC9B8F4.toInt(), true))
        addView(text(label, 8f, 0xFF9DA4B2.toInt(), true))
    }

    private fun section(label: String) { spacer(18); content.addView(text(label, 9f, 0xFFC9B8F4.toInt(), true).apply { letterSpacing = .16f }) }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = if (AerisPrefs.bool(this@MainActivity, "liquid_glass", false)) {
            gradient(0xCC303849.toInt(), 0xCC111722.toInt(), 20f)
        } else rounded(0xFF141822.toInt(), 20f)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        val params = LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(7); layoutParams = params
    }
    private fun row(block: LinearLayout.() -> Unit) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; block() }
    private fun column(block: LinearLayout.() -> Unit) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; block() }
    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }
    private fun spacer(height: Int, parent: LinearLayout = content) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius.toInt()).toFloat(); setColor(color); setStroke(dp(1), 0x333D4658) }
    private fun gradient(start: Int, end: Int, radius: Float) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(start, end)).apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius.toInt()).toFloat(); setStroke(dp(1), 0x443C4352) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    private fun openSystemSettings(intent: Intent) {
        try { startActivity(intent) } catch (_: Exception) { }
    }

    override fun onDestroy() {
        bootRunnable?.let { mainHandler.removeCallbacks(it) }
        super.onDestroy()
    }
}
