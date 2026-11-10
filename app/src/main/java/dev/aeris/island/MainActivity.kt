package dev.aeris.island

import android.Manifest
import android.app.Activity
import android.os.BatteryManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import dev.aeris.island.media.MediaBridge
import dev.aeris.island.media.MediaSnapshot
import dev.aeris.island.media.LyricsEngine
import dev.aeris.island.notifications.AerisNotificationListener
import dev.aeris.island.notifications.NotificationSnapshot
import dev.aeris.island.notifications.NotificationStore
import dev.aeris.island.overlay.IslandService
import dev.aeris.island.shizuku.ShizukuBridge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val AerisBg = Color(0xFF07090D)
private val AerisSurface = Color(0xFF11161D)
private val AerisSurface2 = Color(0xFF171E27)
private val AerisLine = Color(0xFF29323D)
private val AerisText = Color(0xFFF3F6F9)
private val AerisMuted = Color(0xFF96A2B0)
private val AerisCyan = Color(0xFF62DDF3)
private val AerisGreen = Color(0xFF7DD6B0)
private val AerisAmber = Color(0xFFE9B86B)

private val AerisScheme = darkColorScheme(
    primary = AerisCyan,
    onPrimary = Color(0xFF001117),
    secondary = AerisGreen,
    background = AerisBg,
    surface = AerisSurface,
    onSurface = AerisText,
    onBackground = AerisText,
    outline = AerisLine
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.rgb(7, 9, 13)
        window.navigationBarColor = android.graphics.Color.rgb(7, 9, 13)
        setContent {
            MaterialTheme(colorScheme = AerisScheme) {
                AerisRoot(this)
            }
        }
    }
}

@Composable
private fun AerisRoot(activity: ComponentActivity) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf("splash") }
    var serviceRunning by remember { mutableStateOf(AerisPrefs.bool(context, "service_active", false)) }

    LaunchedEffect(Unit) {
        delay(2100)
        page = if (AerisPrefs.firstRunComplete(context)) "home" else "verify"
    }
    LaunchedEffect(Unit) {
        while (true) {
            serviceRunning = AerisPrefs.bool(context, "service_active", false)
            delay(900)
        }
    }

    Box(
        Modifier.fillMaxSize().background(AerisBg)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
    ) {
        AnimatedContent(targetState = page, label = "aeris-page") { target ->
            when (target) {
                "splash" -> SplashScreen()
                "verify" -> VerificationScreen(activity, onNext = {
                    AerisPrefs.markFirstRunComplete(context)
                    page = "home"
                })
                "boot" -> BootSequenceScreen(onFinish = { page = "home" })
                "home" -> AppShell("home", serviceRunning, onNavigate = { page = it }, onBack = { page = "home" }, onStart = { page = "boot" }, onStop = { stopAeris(context) })
                "music" -> AppShell("music", serviceRunning, onNavigate = { page = it }, onBack = { page = "home" })
                "notifications" -> AppShell("notifications", serviceRunning, onNavigate = { page = it }, onBack = { page = "home" })
                "tools" -> AppShell("tools", serviceRunning, onNavigate = { page = it }, onBack = { page = "home" })
                "settings" -> AppShell("settings", serviceRunning, onNavigate = { page = it }, onBack = { page = "home" })
                "tutorial" -> AppShell("tutorial", serviceRunning, onNavigate = { page = it }, onBack = { page = "settings" })
                "about" -> AppShell("about", serviceRunning, onNavigate = { page = it }, onBack = { page = "settings" })
                "permissions" -> VerificationScreen(activity, onNext = { page = "settings" })
                else -> AppShell("home", serviceRunning, onNavigate = { page = it }, onBack = { page = "home" }, onStart = { page = "boot" }, onStop = { stopAeris(context) })
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "splash")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5600, easing = FastOutSlowInEasing)),
        label = "ring"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse),
        label = "pulse"
    )
    var showVersion by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(260)
        showVersion = true
    }
    val versionOffset by animateFloatAsState(
        targetValue = if (showVersion) 0f else -24f,
        animationSpec = tween(820, easing = FastOutSlowInEasing),
        label = "v2-drop"
    )
    val versionAlpha by animateFloatAsState(
        targetValue = if (showVersion) 1f else 0f,
        animationSpec = tween(640),
        label = "v2-fade"
    )
    Column(
        Modifier.fillMaxSize().background(AerisBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(AerisCyan.copy(alpha = 0.075f), radius = size.minDimension * 0.46f)
                drawCircle(AerisLine, radius = size.minDimension * 0.42f, style = Stroke(1.dp.toPx()))
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, AerisCyan, Color.Transparent)),
                    startAngle = rotation,
                    sweepAngle = 112f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            AerisMark(Modifier.size(66.dp).scale(pulse))
        }
        Spacer(Modifier.height(22.dp))
        Text("AERIS", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 7.sp, color = AerisText)
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
            Text(
                "V2",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = AerisCyan.copy(alpha = versionAlpha),
                import androidx.compose.foundation.layout.offset
            )
        }
        Text("ANDROID DYNAMIC EXPERIENCE", fontSize = 9.sp, letterSpacing = 1.9.sp, color = AerisMuted)
        Spacer(Modifier.height(48.dp))
        Box(Modifier.width(154.dp).height(2.dp).clip(CircleShape).background(AerisLine)) {
            Box(Modifier.fillMaxWidth(0.5f).height(2.dp).clip(CircleShape).background(AerisCyan))
        }
        Spacer(Modifier.height(12.dp))
        Text("INITIALIZING EXPERIENCE", fontSize = 9.sp, letterSpacing = 1.7.sp, color = AerisMuted)
    }
}

@Composable
private fun AerisMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.105f)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.14f, h * 0.86f)
            lineTo(w * 0.43f, h * 0.16f)
            quadraticTo(w * 0.5f, h * 0.02f, w * 0.58f, h * 0.16f)
            lineTo(w * 0.88f, h * 0.86f)
        }
        drawPath(path, color = AerisText, style = stroke)
        drawLine(AerisCyan, Offset(w * 0.05f, h * 0.66f), Offset(w * 0.95f, h * 0.36f), strokeWidth = w * 0.085f)
    }
}

@Composable
private fun VerificationScreen(activity: ComponentActivity, onNext: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(850); refresh++ }
    }
    val overlayGranted = remember(refresh) { Settings.canDrawOverlays(context) }
    val listenerGranted = remember(refresh) { AerisNotificationListener.isEnabled(context) }
    val notificationGranted = remember(refresh) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val batteryExempt = remember(refresh) { Build.VERSION.SDK_INT < 23 || powerManager.isIgnoringBatteryOptimizations(context.packageName) }
    val shizukuState = remember(refresh) { ShizukuBridge.state() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Column(Modifier.fillMaxSize().background(AerisBg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 26.dp)) {
        Text("FIRST-TIME SETUP", color = AerisCyan, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Text("Verify your device", color = AerisText, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("AERIS only asks for access used by the overlay. Optional integrations can stay disabled.", color = AerisMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(22.dp))

        PermissionTile(
            title = "Display over other apps",
            description = "Required to show the pill above your launcher and games.",
            status = if (overlayGranted) "GRANTED" else "REQUIRED",
            granted = overlayGranted,
            onClick = { safeOpen(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
        )
        PermissionTile(
            title = "Notification access",
            description = "Reads notification titles locally and detects media sessions. No data is uploaded.",
            status = if (listenerGranted) "GRANTED" else "OPTIONAL / RECOMMENDED",
            granted = listenerGranted,
            onClick = { safeOpen(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        )
        PermissionTile(
            title = "AERIS notifications",
            description = "Allows Android to show the persistent service notification while the island is active.",
            status = if (notificationGranted) "GRANTED" else "NOT GRANTED",
            granted = notificationGranted,
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )
        PermissionTile(
            title = "Background battery settings",
            description = "Recommended for longer sessions. Some phone brands may still stop background apps.",
            status = if (batteryExempt) "UNRESTRICTED" else "DEFAULT MANAGEMENT",
            granted = batteryExempt,
            onClick = { safeOpen(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        )
        PermissionTile(
            title = "Shizuku connection",
            description = "Optional advanced bridge. AERIS's overlay and music features do not require it.",
            status = shizukuState.uppercase(Locale.getDefault()),
            granted = shizukuState.contains("authorized", true),
            onClick = { ShizukuBridge.requestOrOpen(activity) }
        )

        Spacer(Modifier.height(16.dp))
        Surface(color = AerisSurface2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Text("DEVICE-SPECIFIC NOTE", color = AerisAmber, fontSize = 10.sp, letterSpacing = 1.3.sp)
                Spacer(Modifier.height(6.dp))
                Text("Auto-start and background controls vary by manufacturer. AERIS cannot silently bypass those system controls; enable its auto-launch option manually if your phone provides one.", color = AerisMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AerisCyan, contentColor = Color(0xFF001117))
        ) { Text("NEXT  →", fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp) }
        Spacer(Modifier.height(10.dp))
        Text("You can reopen this checklist later in Settings.", color = AerisMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun PermissionTile(title: String, description: String, status: String, granted: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = AerisSurface),
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(if (granted) AerisGreen.copy(alpha = .12f) else AerisSurface2), contentAlignment = Alignment.Center) {
                Icon(if (granted) Icons.Filled.CheckCircle else Icons.Filled.Shield, null, tint = if (granted) AerisGreen else AerisMuted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = AerisText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(description, color = AerisMuted, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(status, color = if (granted) AerisGreen else AerisAmber, fontSize = 9.sp, letterSpacing = .8.sp, fontWeight = FontWeight.Bold)
            }
            Text("›", color = AerisMuted, fontSize = 24.sp)
        }
    }
}

@Composable
private fun AppShell(
    page: String,
    serviceRunning: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit = {},
    onStop: () -> Unit = {}
) {
    val title = when (page) {
        "home" -> "Overview"
        "music" -> "Music center"
        "notifications" -> "Notifications"
        "tools" -> "Quick tools"
        "settings" -> "Settings"
        "tutorial" -> "How to use AERIS"
        "about" -> "About AERIS"
        else -> "AERIS"
    }
    Column(Modifier.fillMaxSize().background(AerisBg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page == "tutorial" || page == "about") {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.ArrowBack, null, tint = AerisText) }
            } else {
                AerisMark(Modifier.size(26.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = AerisText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("AERIS  /  CONTROL LAYER", color = AerisMuted, fontSize = 9.sp, letterSpacing = 1.25.sp)
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (serviceRunning) AerisGreen else AerisLine))
            Spacer(Modifier.width(6.dp))
            Text(if (serviceRunning) "ACTIVE" else "IDLE", color = if (serviceRunning) AerisGreen else AerisMuted, fontSize = 9.sp, letterSpacing = 1.sp)
        }
        Divider(color = AerisLine.copy(alpha = .65f), thickness = 1.dp)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                "home" -> HomeScreen(serviceRunning, onStart, onStop, onNavigate)
                "music" -> MusicScreen()
                "notifications" -> NotificationsScreen()
                "tools" -> ToolsScreen()
                "settings" -> SettingsScreen(onNavigate)
                "tutorial" -> TutorialScreen()
                "about" -> AboutScreen()
            }
        }
        if (page !in listOf("tutorial", "about")) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF0C1016)).padding(horizontal = 7.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                NavItem("home", "Home", Icons.Filled.Home, page, onNavigate)
                NavItem("music", "Music", Icons.Filled.MusicNote, page, onNavigate)
                NavItem("notifications", "Alerts", Icons.Filled.Notifications, page, onNavigate)
                NavItem("tools", "Tools", Icons.Filled.Timer, page, onNavigate)
                NavItem("settings", "Settings", Icons.Filled.Settings, page, onNavigate)
            }
        }
    }
}

@Composable
private fun NavItem(route: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, current: String, onNavigate: (String) -> Unit) {
    val selected = route == current
    Column(
        Modifier.clip(RoundedCornerShape(13.dp)).clickable { onNavigate(route) }.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = if (selected) AerisCyan else AerisMuted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (selected) AerisCyan else AerisMuted, fontSize = 9.sp)
    }
}

private data class DeviceSnapshot(
    val battery: Int,
    val charging: Boolean,
    val batteryTemp: Float,
    val ramUsedPercent: Int,
    val storageFree: String,
    val network: String
)

private fun readDeviceSnapshot(context: Context): DeviceSnapshot {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val battery = if (scale > 0) (level * 100 / scale).coerceIn(0, 100) else 0
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memory = android.app.ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memory)
    val ramUsed = if (memory.totalMem > 0) (((memory.totalMem - memory.availMem) * 100) / memory.totalMem).toInt() else 0
    val stats = StatFs(Environment.getDataDirectory().path)
    val free = stats.availableBytes
    val storageFree = Formatter.formatShortFileSize(context, free)
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivity.activeNetwork
    val caps = if (network != null) connectivity.getNetworkCapabilities(network) else null
    val networkName = when {
        caps == null -> "Offline"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Connected"
    }
    return DeviceSnapshot(
        battery = battery,
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        batteryTemp = temp,
        ramUsedPercent = ramUsed,
        storageFree = storageFree,
        network = networkName
    )
}

@Composable
private fun HomeScreen(
    serviceRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(readDeviceSnapshot(context)) }
    var media by remember { mutableStateOf(MediaBridge.current(context)) }
    var clock by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = readDeviceSnapshot(context)
            media = MediaBridge.current(context)
            clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(1500)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("MONDAY  /  LIVE STATUS", color = AerisCyan, fontSize = 9.sp, letterSpacing = 1.8.sp)
                Text(clock, color = AerisText, fontSize = 34.sp, fontWeight = FontWeight.Light)
            }
            Text("ANDROID  ${Build.VERSION.RELEASE}", color = AerisMuted, fontSize = 9.sp, letterSpacing = .8.sp)
        }
        Spacer(Modifier.height(18.dp))
        DynamicIslandPreview(media, serviceRunning)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard(Modifier.weight(1f), "BATTERY", "${snapshot.battery}%", "${if (snapshot.charging) "Charging" else "On battery"}  ·  ${snapshot.batteryTemp}°C", Icons.Filled.BatteryFull)
            MetricCard(Modifier.weight(1f), "MEMORY", "${snapshot.ramUsedPercent}%", "RAM in use", Icons.Filled.Memory)
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard(Modifier.weight(1f), "STORAGE", snapshot.storageFree, "Available space", Icons.Filled.Storage)
            MetricCard(Modifier.weight(1f), "NETWORK", snapshot.network, if (snapshot.network == "Offline") "No connection" else "Connected", Icons.Filled.Wifi)
        }
        Spacer(Modifier.height(18.dp))
        Surface(color = AerisSurface, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("OVERLAY ENGINE", color = AerisMuted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    Text(if (serviceRunning) "RUNNING" else "STOPPED", color = if (serviceRunning) AerisGreen else AerisMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(if (serviceRunning) "Your island is available above other apps." else "Start AERIS to enable the floating island.", color = AerisText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(13.dp))
                Button(
                    onClick = { if (serviceRunning) onStop() else onStart() },
                    modifier = Modifier.fillMaxWidth().height(51.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (serviceRunning) Color(0xFF252C35) else AerisCyan, contentColor = if (serviceRunning) AerisText else Color(0xFF001117))
                ) {
                    Icon(if (serviceRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (serviceRunning) "STOP AERIS" else "START AERIS", fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("The service shows a persistent Android notification while active.", color = AerisMuted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickLink(Modifier.weight(1f), "Music center", "Media sessions", Icons.Filled.MusicNote) { onNavigate("music") }
            QuickLink(Modifier.weight(1f), "Gaming timer", "Session tools", Icons.Filled.Timer) { onNavigate("tools") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickLink(Modifier.weight(1f), "Tutorial", "Visual guide", Icons.Filled.HelpOutline) { onNavigate("tutorial") }
            QuickLink(Modifier.weight(1f), "About", "Version and limits", Icons.Filled.Info) { onNavigate("about") }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun DynamicIslandPreview(media: MediaSnapshot?, running: Boolean) {
    Surface(
        color = Color(0xFF030507),
        shape = RoundedCornerShape(30.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3038)),
        modifier = Modifier.fillMaxWidth().height(82.dp)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF1C2530)), contentAlignment = Alignment.Center) {
                if (media?.artwork != null) Image(media.artwork.asImageBitmap(), null, modifier = Modifier.fillMaxSize())
                else AerisMark(Modifier.size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(media?.title ?: if (running) "AERIS READY" else "ISLAND PREVIEW", color = AerisText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(media?.artist ?: "Tap START to activate", color = AerisMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val heights = listOf(8.dp, 15.dp, 11.dp, 18.dp, 9.dp)
                heights.forEachIndexed { index, height ->
                    Box(Modifier.width(2.dp).height(if (media?.playing == true) height else 4.dp).clip(CircleShape).background(AerisCyan.copy(alpha = if (media?.playing == true) .9f else .35f)))
                }
            }
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, label: String, value: String, caption: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(modifier, color = AerisSurface, shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine)) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = AerisMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = AerisMuted, fontSize = 8.sp, letterSpacing = 1.15.sp)
            }
            Spacer(Modifier.height(9.dp))
            Text(value, color = AerisText, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(caption, color = AerisMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun QuickLink(modifier: Modifier, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(modifier.clickable { onClick() }, color = AerisSurface, shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AerisCyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(title, color = AerisText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = AerisMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MusicScreen() {
    val context = LocalContext.current
    var media by remember { mutableStateOf(MediaBridge.current(context)) }
    var listenerEnabled by remember { mutableStateOf(AerisNotificationListener.isEnabled(context)) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            media = MediaBridge.current(context)
            listenerEnabled = AerisNotificationListener.isEnabled(context)
            if (!isDragging) {
                val duration = media?.durationMs ?: 0L
                seekValue = if (duration > 0L) (media!!.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
            }
            delay(700)
        }
    }
    val lyricLines = remember(media?.lyrics, media?.title) { LyricsEngine.parse(media?.lyrics) }
    val lyricFrame = LyricsEngine.frame(lyricLines, media?.positionMs ?: 0L)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("AERIS MUSIC ISLAND  /  V2", color = AerisCyan, fontSize = 9.sp, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(13.dp))
        Surface(color = AerisSurface, shape = RoundedCornerShape(26.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(152.dp).clip(RoundedCornerShape(22.dp)).background(AerisSurface2), contentAlignment = Alignment.Center) {
                    if (media?.artwork != null) Image(media!!.artwork!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize())
                    else AerisMark(Modifier.size(55.dp))
                }
                Spacer(Modifier.height(15.dp))
                Text(media?.title ?: "No active music", color = AerisText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(media?.artist ?: if (listenerEnabled) "Start music in Spotify or another player" else "Enable notification access below", color = AerisMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(((media?.durationMs ?: 0L) * seekValue).toLong()), color = AerisText, fontSize = 10.sp)
                    Text(formatTime(media?.durationMs ?: 0L), color = AerisMuted, fontSize = 10.sp)
                }
                Slider(
                    value = seekValue,
                    onValueChange = { isDragging = true; seekValue = it },
                    onValueChangeFinished = {
                        val duration = media?.durationMs ?: 0L
                        if (duration > 0L) MediaBridge.seekTo(context, (duration * seekValue).toLong())
                        isDragging = false
                    },
                    enabled = media?.canSeek == true && (media?.durationMs ?: 0L) > 0L,
                    valueRange = 0f..1f
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    CircleAction("−10", enabled = media?.canSeek == true) { MediaBridge.jumpBy(context, -10_000L) }
                    CircleAction("PREV", enabled = media?.canPrevious == true) { MediaBridge.previous(context) }
                    CircleAction(if (media?.playing == true) "PAUSE" else "PLAY", primary = true, enabled = media?.canToggle == true) { MediaBridge.toggle(context) }
                    CircleAction("NEXT", enabled = media?.canNext == true) { MediaBridge.next(context) }
                    CircleAction("+10", enabled = media?.canSeek == true) { MediaBridge.jumpBy(context, 10_000L) }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        media == null -> "NO ACTIVE SESSION"
                        media!!.playing -> "PLAYING  ·  ${media!!.packageName.substringAfterLast('.').uppercase(Locale.getDefault())}"
                        else -> "PAUSED"
                    },
                    color = if (media?.playing == true) AerisGreen else AerisMuted,
                    fontSize = 9.sp,
                    letterSpacing = 1.5.sp
                )
            }
        }
        Spacer(Modifier.height(13.dp))
        Surface(color = AerisSurface, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("LIVE LYRICS", color = AerisCyan, fontSize = 9.sp, letterSpacing = 1.4.sp)
                    Text(if (lyricFrame?.wordSynced == true) "WORD SYNC" else if (lyricFrame != null) "LINE SYNC" else "PLAYER SOURCE", color = AerisMuted, fontSize = 8.sp)
                }
                Spacer(Modifier.height(15.dp))
                if (lyricFrame != null) {
                    Text(lyricFrame.previous, color = AerisMuted.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(lyricFrame.current, color = AerisText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(lyricFrame.next, color = AerisMuted.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else {
                    if (!media?.lyrics.isNullOrBlank()) {
                        Text(media!!.lyrics!!, color = AerisText, fontSize = 11.sp, lineHeight = 18.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text("Lyrics appear here when the active music app exposes lyric text through its Android media session. Enhanced LRC enables word-by-word timing; standard LRC follows each line. AERIS never invents lyric timing.", color = AerisMuted, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        InfoPanel("Playback compatibility", "Previous, Next and seeking are enabled only when the active player advertises those MediaSession actions. Lyrics are read locally from the player metadata; AERIS does not scrape protected lyrics from streaming services.")
        Spacer(Modifier.height(10.dp))
        if (!listenerEnabled) {
            Button(onClick = { safeOpen(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AerisCyan, contentColor = Color(0xFF001117))) {
                Text("ENABLE NOTIFICATION ACCESS")
            }
        }
    }
}

@Composable
private fun CircleAction(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(if (primary) 58.dp else 44.dp).clip(CircleShape).alpha(if (enabled) 1f else 0.35f).clickable(enabled = enabled) { onClick() },
        shape = CircleShape,
        color = if (primary) AerisCyan else AerisSurface2,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (primary) AerisCyan else AerisLine)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (primary) Color(0xFF001117) else AerisText, fontSize = if (primary) 10.sp else 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NotificationsScreen() {
    val context = LocalContext.current
    var notification by remember { mutableStateOf(NotificationStore.latest) }
    var recentNotifications by remember { mutableStateOf(NotificationStore.recent()) }
    var listenerEnabled by remember { mutableStateOf(AerisNotificationListener.isEnabled(context)) }
    var replyText by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            notification = NotificationStore.latest
            recentNotifications = NotificationStore.recent()
            listenerEnabled = AerisNotificationListener.isEnabled(context)
            delay(850)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("LOCAL NOTIFICATION BRIDGE", color = AerisCyan, fontSize = 9.sp, letterSpacing = 1.7.sp)
        Spacer(Modifier.height(12.dp))
        if (!listenerEnabled) {
            InfoPanel("Access required", "Enable Notification access to let AERIS read notification previews and detect supported reply actions. The content is processed locally on this device.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { safeOpen(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AerisCyan, contentColor = Color(0xFF001117))) { Text("OPEN NOTIFICATION ACCESS") }
        } else if (notification == null) {
            InfoPanel("No recent notification", "When another app posts a notification, its latest title and message preview can appear here and in the island.")
        } else {
            val hide = AerisPrefs.bool(context, "hide_sensitive", false)
            Surface(color = AerisSurface, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(notification!!.packageName.substringAfterLast('.').uppercase(Locale.getDefault()), color = AerisCyan, fontSize = 9.sp, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(if (hide) "Hidden by privacy setting" else notification!!.title, color = AerisText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(if (hide) "Notification preview is hidden." else notification!!.text, color = AerisMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(13.dp))
                    if (notification!!.canReply && AerisPrefs.bool(context, "quick_reply", true) && !hide) {
                        Text("SOURCE APP SUPPORTS REPLY", color = AerisGreen, fontSize = 9.sp, letterSpacing = 1.1.sp)
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Write a reply", color = AerisMuted) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = AerisSurface2,
                                unfocusedContainerColor = AerisSurface2,
                                focusedTextColor = AerisText,
                                unfocusedTextColor = AerisText,
                                focusedIndicatorColor = AerisCyan,
                                unfocusedIndicatorColor = AerisLine
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val sent = NotificationStore.sendReply(context, replyText)
                            message = if (sent) "Reply action sent to the source app." else "The source app's reply action is no longer available."
                            if (sent) replyText = ""
                        }, enabled = replyText.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = AerisCyan, contentColor = Color(0xFF001117))) { Text("SEND REPLY") }
                        if (message.isNotBlank()) Text(message, color = AerisMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text("Quick reply is unavailable for this notification because the source app did not provide a valid RemoteInput action.", color = AerisMuted, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(15.dp))
        if (recentNotifications.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("RECENT ACTIVITY", color = AerisCyan, fontSize = 9.sp, letterSpacing = 1.5.sp)
                Text("${recentNotifications.size} / 8", color = AerisMuted, fontSize = 9.sp)
            }
            Spacer(Modifier.height(8.dp))
            recentNotifications.forEach { item ->
                Surface(color = AerisSurface, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.packageName.substringAfterLast('.').uppercase(Locale.getDefault()), color = AerisCyan, fontSize = 8.sp, letterSpacing = 1.1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(if (AerisPrefs.bool(context, "hide_sensitive", false)) "Hidden by privacy setting" else item.title, color = AerisText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        if (!AerisPrefs.bool(context, "hide_sensitive", false) && item.text.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(item.text, color = AerisMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        InfoPanel("Privacy", "AERIS does not upload notification content. The recent queue is kept in memory only and clears when Android ends the app process. Android controls notification access and can revoke it at any time.")
    }
}

@Composable
private fun ToolsScreen() {
    var elapsed by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var laps by remember { mutableIntStateOf(0) }
    var countdownMinutes by rememberSaveable { mutableIntStateOf(5) }
    var remaining by remember { mutableLongStateOf(countdownMinutes * 60_000L) }
    var timerRunning by remember { mutableStateOf(false) }
    var timerEnd by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        while (running) {
            elapsed = System.currentTimeMillis() - startedAt
            delay(50)
        }
    }
    LaunchedEffect(timerRunning) {
        while (timerRunning) {
            remaining = (timerEnd - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remaining <= 0L) timerRunning = false
            delay(100)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("TIME & SESSION TOOLS", color = AerisCyan, fontSize = 9.sp, letterSpacing = 1.7.sp)
        Spacer(Modifier.height(13.dp))
        Surface(color = AerisSurface, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("STOPWATCH", color = AerisMuted, fontSize = 9.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(8.dp))
                Text(formatTime(elapsed), color = AerisText, fontSize = 30.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (running) running = false else { startedAt = System.currentTimeMillis() - elapsed; running = true }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AerisCyan, contentColor = Color(0xFF001117))) { Text(if (running) "PAUSE" else "START") }
                    OutlinedButton(onClick = { running = false; elapsed = 0L; laps = 0 }) { Text("RESET", color = AerisText) }
                    OutlinedButton(onClick = { if (running) laps++ }, enabled = running) { Text("LAP ${laps + 1}", color = AerisText) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = AerisSurface, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("COUNTDOWN TIMER", color = AerisMuted, fontSize = 9.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(8.dp))
                Text(formatTime(remaining), color = AerisText, fontSize = 30.sp, fontWeight = FontWeight.Light)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { countdownMinutes = (countdownMinutes - 1).coerceAtLeast(1); remaining = countdownMinutes * 60_000L }, enabled = !timerRunning) { Text("− 1 MIN") }
                    Text("$countdownMinutes min", color = AerisMuted, fontSize = 11.sp)
                    TextButton(onClick = { countdownMinutes = (countdownMinutes + 1).coerceAtMost(180); remaining = countdownMinutes * 60_000L }, enabled = !timerRunning) { Text("+ 1 MIN") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (!timerRunning) { remaining = countdownMinutes * 60_000L; timerEnd = System.currentTimeMillis() + remaining; timerRunning = true } else timerRunning = false }, colors = ButtonDefaults.buttonColors(containerColor = AerisCyan, contentColor = Color(0xFF001117))) { Text(if (timerRunning) "PAUSE" else "START TIMER") }
                    OutlinedButton(onClick = { timerRunning = false; remaining = countdownMinutes * 60_000L }) { Text("RESET", color = AerisText) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        InfoPanel("Gaming session", "Use the stopwatch as a game-session timer. AERIS can keep the floating pill visible over compatible games when overlay access is enabled; protected or full-screen apps may block overlays.")
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private data class ToggleOption(val key: String, val title: String, val detail: String, val default: Boolean)

private val toggleOptions = listOf(
    ToggleOption("island_enabled", "Dynamic Island", "Master switch for the floating pill.", true),
    ToggleOption("music_enabled", "Music detection", "Read active Android media sessions.", true),
    ToggleOption("media_controls", "Media controls", "Show previous, play/pause, next and seek controls.", true),
    ToggleOption("lyrics_enabled", "Lyrics in Music Island", "Show lyrics exposed by the active media player.", true),
    ToggleOption("show_artwork", "Album artwork", "Show cover art when the player supplies it.", true),
    ToggleOption("visualizer_enabled", "Visualizer bars", "Animate a small playback indicator while music plays.", true),
    ToggleOption("auto_expand_music", "Expand on new music", "Temporarily expand when a new track starts.", true),
    ToggleOption("notifications_enabled", "Notification detection", "Listen for new notifications.", true),
    ToggleOption("notification_preview", "Notification preview", "Show title and message in expanded mode.", true),
    ToggleOption("auto_expand_notifications", "Expand on notification", "Temporarily expand when a notification arrives.", true),
    ToggleOption("quick_reply", "RemoteInput quick reply", "Show reply only when the source provides a real reply action.", true),
    ToggleOption("hide_sensitive", "Hide sensitive previews", "Replace message text with a privacy label.", false),
    ToggleOption("auto_start", "Auto-start after reboot", "Attempt to restore the overlay after device boot, subject to Android/OEM rules.", false),
    ToggleOption("landscape_enabled", "Show in landscape", "Keep the island visible when the screen rotates.", true),
    ToggleOption("reduce_motion", "Reduce motion", "Use a calmer visual presentation.", false),
    ToggleOption("low_power", "Low power refresh", "Reduce overlay refresh frequency to save battery.", false),
    ToggleOption("show_track_state", "Show playback state", "Show whether the track is playing or paused.", true),
    ToggleOption("show_source_app", "Show notification source", "Show the source app name under notification activity.", false),
    ToggleOption("tap_to_expand", "Tap to expand", "Allow tapping the pill to open or close its expanded view.", true),
    ToggleOption("auto_collapse", "Auto-collapse", "Collapse expanded mode after the configured delay.", true),
    ToggleOption("fade_when_idle", "Fade while idle", "Slightly reduce opacity when no media or notification is active.", false),
    ToggleOption("haptic_feedback", "Touch feedback", "Use a light haptic tick when the pill is tapped.", false)
)

@Composable
private fun SettingsScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("PERSONAL CONTROL", color = AerisCyan, fontSize = 9.sp, letterSpacing = 1.7.sp)
        Spacer(Modifier.height(12.dp))
        Surface(color = AerisSurface, shape = RoundedCornerShape(19.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Text("DISPLAY", color = AerisMuted, fontSize = 9.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(4.dp))
                SliderSetting("Collapsed width", "island_width", 150f, 280f, 188f, "dp")
                SliderSetting("Collapsed height", "island_height", 36f, 58f, 43f, "dp")
                SliderSetting("Top offset", "island_y", 0f, 60f, 8f, "dp")
                SliderSetting("Island opacity", "island_opacity", 60f, 100f, 100f, "%")
                SliderSetting("Corner radius", "corner_radius", 18f, 52f, 40f, "dp")
                SliderSetting("Expanded width", "expanded_width", 240f, 350f, 330f, "dp")
                SliderSetting("Animation speed", "animation_speed", 40f, 180f, 100f, "%")
                SliderSetting("Auto-collapse delay", "expand_seconds", 3f, 30f, 10f, "s")
            }
        }
        Spacer(Modifier.height(13.dp))
        Text("FEATURE SWITCHES", color = AerisMuted, fontSize = 9.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(7.dp))
        toggleOptions.forEach { option -> ToggleSettingRow(option) }
        Spacer(Modifier.height(13.dp))
        Surface(color = AerisSurface, shape = RoundedCornerShape(19.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Text("TOOLS & SUPPORT", color = AerisMuted, fontSize = 9.sp, letterSpacing = 1.4.sp)
                SupportRow("Permission checklist", "Review overlay, notifications and background settings") { onNavigate("permissions") }
                Divider(color = AerisLine)
                SupportRow("Visual tutorial", "See setup and game-mode examples") { onNavigate("tutorial") }
                Divider(color = AerisLine)
                SupportRow("About AERIS", "Version, privacy and platform limitations") { onNavigate("about") }
                Divider(color = AerisLine)
                SupportRow("Open Android notification settings", "Manage what can appear in the notification shade") { safeOpen(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ToggleSettingRow(option: ToggleOption) {
    val context = LocalContext.current
    var checked by remember(option.key) { mutableStateOf(AerisPrefs.bool(context, option.key, option.default)) }
    Surface(color = AerisSurface, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(option.title, color = AerisText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text(option.detail, color = AerisMuted, fontSize = 10.sp, lineHeight = 14.sp)
            }
            Switch(checked = checked, onCheckedChange = {
                checked = it
                AerisPrefs.setBool(context, option.key, it)
            })
        }
    }
}

@Composable
private fun SliderSetting(title: String, key: String, min: Float, max: Float, default: Float, unit: String) {
    val context = LocalContext.current
    var value by remember(key) { mutableFloatStateOf(AerisPrefs.int(context, key, default.toInt()).toFloat().coerceIn(min, max)) }
    Column(Modifier.fillMaxWidth().padding(top = 9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = AerisText, fontSize = 12.sp)
            Text("${value.toInt()} $unit", color = AerisCyan, fontSize = 10.sp)
        }
        Slider(value = value, onValueChange = { value = it }, onValueChangeFinished = { AerisPrefs.setInt(context, key, value.toInt()) }, valueRange = min..max)
    }
}

@Composable
private fun SupportRow(title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AerisText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(detail, color = AerisMuted, fontSize = 10.sp)
        }
        Text("›", color = AerisMuted, fontSize = 22.sp)
    }
}

@Composable
private fun TutorialScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        TutorialStep("01", "Grant display access", "Open Verification and enable Display over other apps. This is what allows the black pill to sit above a game or another app.", 0)
        TutorialStep("02", "Enable notification access", "Allow AERIS to read notification metadata. This also lets it discover Android media sessions from compatible music players.", 1)
        TutorialStep("03", "Press START", "Return to Overview and press START AERIS. The terminal-style boot sequence runs, then the overlay service starts with a persistent system notification.", 2)
        TutorialStep("04", "Tap the island", "Tap the pill to expand it. Music controls appear when a media session is available; the pill shrinks automatically after the configured number of seconds.", 3)
        TutorialStep("05", "Use it in landscape", "Rotate the phone. AERIS follows the display orientation and recalculates its width. Some secure or immersive apps may block third-party overlays.", 4)
        TutorialStep("06", "Reply safely", "The Reply control appears only when the notification contains a real Android RemoteInput action. If the source app does not offer one, AERIS will not imitate a send action.", 5)
    }
}

@Composable
private fun TutorialStep(number: String, title: String, detail: String, kind: Int) {
    Surface(color = AerisSurface, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(number, color = AerisCyan, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(9.dp))
                Text(title, color = AerisText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            when (kind) {
                0 -> PermissionDiagram()
                1 -> NotificationDiagram()
                2 -> BootDiagram()
                3 -> IslandDiagram()
                4 -> LandscapeDiagram()
                else -> ReplyDiagram()
            }
            Spacer(Modifier.height(10.dp))
            Text(detail, color = AerisMuted, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun PermissionDiagram() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF080B10)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(AerisSurface2), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Shield, null, tint = AerisCyan) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text("Display over other apps", color = AerisText, fontSize = 11.sp); Text("AERIS  ·  Allowed", color = AerisGreen, fontSize = 9.sp) }
        Text("ON", color = AerisGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NotificationDiagram() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF080B10)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Notifications, null, tint = AerisCyan, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(12.dp))
        Column { Text("Music app detected", color = AerisText, fontSize = 11.sp); Text("Track title  ·  Artist  ·  Playback state", color = AerisMuted, fontSize = 9.sp) }
    }
}

@Composable
private fun BootDiagram() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF05080B)).padding(12.dp)) {
        Text("AERIS CORE  /  BOOT", color = AerisCyan, fontSize = 9.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(5.dp))
        listOf("Loading overlay engine", "Binding media sessions", "Checking display layer", "Service ready").forEach { Text("› $it", color = AerisMuted, fontSize = 9.sp) }
    }
}

@Composable
private fun IslandDiagram() {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF080B10)).padding(16.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.width(220.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black).border(1.dp, AerisLine, RoundedCornerShape(30.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AerisMark(Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Column { Text("Track title", color = AerisText, fontSize = 9.sp); Text("Artist  ·  Playing", color = AerisMuted, fontSize = 8.sp) }
        }
    }
}

@Composable
private fun LandscapeDiagram() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF080B10)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(112.dp).height(62.dp).clip(RoundedCornerShape(9.dp)).border(1.dp, AerisLine, RoundedCornerShape(9.dp)), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.padding(top = 4.dp).width(46.dp).height(10.dp).clip(RoundedCornerShape(9.dp)).background(Color.Black).border(1.dp, AerisLine, RoundedCornerShape(9.dp)))
        }
        Spacer(Modifier.width(14.dp))
        Column { Text("Portrait  →  Landscape", color = AerisText, fontSize = 10.sp); Text("Island width adapts to the new display shape.", color = AerisMuted, fontSize = 9.sp) }
    }
}

@Composable
private fun ReplyDiagram() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF080B10)).padding(11.dp)) {
        Text("Notification supports reply", color = AerisGreen, fontSize = 9.sp)
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(9.dp)).background(AerisSurface2).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) { Text("Write a reply…", color = AerisMuted, fontSize = 9.sp) }
            Spacer(Modifier.width(7.dp))
            Box(Modifier.height(30.dp).clip(RoundedCornerShape(9.dp)).background(AerisCyan).padding(horizontal = 11.dp), contentAlignment = Alignment.Center) { Text("SEND", color = Color(0xFF001117), fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun AboutScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF101A24), Color(0xFF080A0E)))).padding(22.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AerisMark(Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("AERIS", color = AerisText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 5.sp)
                Text("SYSTEM INTERFACE", color = AerisMuted, fontSize = 9.sp, letterSpacing = 2.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        InfoPanel("Version", "AERIS 2.0.0  ·  Native Android project  ·  Minimum Android 8.0")
        Spacer(Modifier.height(9.dp))
        InfoPanel("Privacy", "Notification content and media metadata are handled locally. This project contains no analytics SDK, account system, ad SDK or remote notification relay.")
        Spacer(Modifier.height(9.dp))
        InfoPanel("Important platform limits", "Android does not guarantee that an overlay can appear over every secure/full-screen app. Media metadata depends on the player. Lyrics are not universally exposed. Device FPS, CPU temperature and some system controls are not available to ordinary third-party apps on every phone.")
        Spacer(Modifier.height(9.dp))
        InfoPanel("Quick reply", "AERIS only sends through the PendingIntent and RemoteInput action supplied by the original notification. It does not manufacture replies or bypass the source app.")
        Spacer(Modifier.height(9.dp))
        InfoPanel("Open-source components", "Jetpack Compose and AndroidX are licensed by their respective owners. Shizuku API is provided by RikkaApps; see its MIT license and the project dependency notices.")
    }
}

@Composable
private fun InfoPanel(title: String, detail: String) {
    Surface(color = AerisSurface, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title.uppercase(Locale.getDefault()), color = AerisCyan, fontSize = 9.sp, letterSpacing = 1.15.sp)
            Spacer(Modifier.height(6.dp))
            Text(detail, color = AerisMuted, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun BootSequenceScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val lines = listOf(
        "AERIS BOOTLOADER  /  v2.0.0",
        "Mounting local configuration... OK",
        "Initializing overlay engine... OK",
        "Checking notification bridge... READY",
        "Binding active media sessions... READY",
        "Preparing responsive display layer... OK",
        "Starting foreground service..."
    )
    var visibleLines by remember { mutableStateOf(listOf<String>()) }
    var elapsed by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf("INITIALIZING") }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        lines.forEachIndexed { index, line ->
            delay(420)
            visibleLines = visibleLines + line
            elapsed = ((System.currentTimeMillis() - start) / 1000).toInt()
        }
        delay(400)
        if (Settings.canDrawOverlays(context) && AerisPrefs.islandEnabled(context)) {
            try {
                val intent = Intent(context, IslandService::class.java).apply { action = IslandService.ACTION_START }
                ContextCompat.startForegroundService(context, intent)
                AerisPrefs.setBool(context, "service_active", true)
                result = "SERVICE READY"
            } catch (_: Exception) {
                result = "SERVICE BLOCKED BY ANDROID"
            }
        } else {
            result = "OVERLAY ACCESS REQUIRED"
        }
        delay(1000)
        onFinish()
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF05080B)).padding(20.dp), verticalArrangement = Arrangement.Center) {
        Text("AERIS  /  INITIALIZER", color = AerisCyan, fontSize = 10.sp, letterSpacing = 1.6.sp)
        Spacer(Modifier.height(16.dp))
        Surface(color = Color(0xFF090D12), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AerisLine), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("BOOT SEQUENCE", color = AerisMuted, fontSize = 9.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(12.dp))
                visibleLines.forEachIndexed { index, line ->
                    Text("${if (index == visibleLines.lastIndex) "›" else "  "} $line", color = if (index == visibleLines.lastIndex) AerisCyan else AerisMuted, fontSize = 10.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(16.dp))
                Divider(color = AerisLine)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ELAPSED  00:00:${elapsed.toString().padStart(2, '0')}", color = AerisMuted, fontSize = 9.sp)
                    Text(result, color = if (result == "SERVICE READY") AerisGreen else AerisAmber, fontSize = 9.sp, letterSpacing = .8.sp)
                }
            }
        }
        Spacer(Modifier.height(15.dp))
        Text("NO SYSTEM SETTINGS ARE CHANGED WITHOUT YOUR PERMISSION.", color = AerisMuted, fontSize = 8.sp, letterSpacing = .7.sp)
    }
}

private fun stopAeris(context: Context) {
    context.stopService(Intent(context, IslandService::class.java))
    AerisPrefs.setBool(context, "service_active", false)
}

private fun safeOpen(context: Context, intent: Intent) {
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        // Settings entry points vary across Android builds and OEM software.
    }
}
