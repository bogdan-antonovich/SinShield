package com.example.sinshield

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.sinshield.ui.theme.SinShieldTheme
import kotlin.math.roundToInt

private val ScreenBlue = Color(0xFFE5EFFF)
private val CardWhite = Color(0xFFF9FBFF)
private val Ink = Color(0xFF082D48)
private val MutedInk = Color(0xFF52606C)
private val BrightBlue = Color(0xFF087CF0)
private val ActiveGreen = Color(0xFF00D782)
private val RequiredRed = Color(0xFFB3261E)
private val Divider = Color(0xFFD2D7DE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ProtectionHealthMonitor.start(this)
        setContent {
            SinShieldTheme {
                Scaffold(containerColor = ScreenBlue) { padding ->
                    MainScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

private data class SettingDetails(val title: String, val description: String)

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var strict by remember { mutableStateOf(ProtectionPreferences.strictMode(context)) }
    var level by remember { mutableStateOf(ProtectionPreferences.protectionLevel(context)) }
    var detection by remember { mutableStateOf(ProtectionPreferences.detectionSettings(context)) }
    var custom by remember { mutableStateOf(ProtectionPreferences.hasCustomThresholds(context)) }
    var tuning by remember { mutableStateOf(false) }
    var accessibility by remember { mutableStateOf(ProtectionHealthMonitor.isAccessibilityEnabled(context)) }
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var battery by remember { mutableStateOf(ProtectionHealthMonitor.isBatteryOptimizationDisabled(context)) }
    val deviceBackgroundPolicy = remember { DeviceBackgroundPolicy.current() }
    var deviceBackgroundReady by remember {
        mutableStateOf(
            deviceBackgroundPolicy == null ||
                DeviceBackgroundPolicy.hasVisitedSettings(context, deviceBackgroundPolicy)
        )
    }
    var notifications by remember { mutableStateOf(canPostNotifications(context)) }
    var vpn by remember { mutableStateOf(AdultContentVpnService.isRunning) }
    var domains by remember { mutableStateOf("${AdultDomainListRepository.storedDomainCount(context)} domains ready") }
    var details by remember { mutableStateOf<SettingDetails?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifications = it || canPostNotifications(context)
        if (notifications) ProtectionHealthMonitor.checkNow(context)
    }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) AdultContentVpnService.start(context)
        vpn = AdultContentVpnService.isRunning
    }
    val deviceBackgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        deviceBackgroundPolicy?.let { policy ->
            DeviceBackgroundPolicy.markSettingsVisited(context, policy)
            deviceBackgroundReady = true
            ProtectionHealthMonitor.checkNow(context)
        }
    }

    DisposableEffect(context, lifecycleOwner) {
        var active = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                vpn = intent?.getBooleanExtra(AdultContentVpnService.EXTRA_RUNNING, AdultContentVpnService.isRunning)
                    ?: AdultContentVpnService.isRunning
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibility = ProtectionHealthMonitor.isAccessibilityEnabled(context)
                overlay = Settings.canDrawOverlays(context)
                battery = ProtectionHealthMonitor.isBatteryOptimizationDisabled(context)
                deviceBackgroundReady = deviceBackgroundPolicy == null ||
                    DeviceBackgroundPolicy.hasVisitedSettings(context, deviceBackgroundPolicy)
                notifications = canPostNotifications(context)
                vpn = AdultContentVpnService.isRunning
                level = ProtectionPreferences.protectionLevel(context)
                detection = ProtectionPreferences.detectionSettings(context)
                custom = ProtectionPreferences.hasCustomThresholds(context)
                ProtectionHealthMonitor.checkNow(context)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(AdultContentVpnService.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleOwner.lifecycle.addObserver(observer)
        AdultDomainListRepository.refreshIfStale(context) { result ->
            if (!active) return@refreshIfStale
            result.matcher?.let {
                domains = "${it.size} domains ready"
                AdultContentVpnService.reloadList(context)
            } ?: run { domains = "Using saved list · update unavailable" }
        }
        onDispose {
            active = false
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }

    details?.let { value ->
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(value.title, color = Ink) },
            text = { Text(value.description, color = MutedInk) },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Got it") } }
        )
    }

    Column(
        modifier.fillMaxSize().background(ScreenBlue).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Advanced settings", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        Spacer(Modifier.height(18.dp))

        RequiredPermissionsCard(
            accessibility, overlay, battery, deviceBackgroundPolicy, deviceBackgroundReady,
            { openAccessibilitySettings(context) },
            { openOverlaySettings(context) },
            { requestBatteryExemption(context) },
            {
                deviceBackgroundPolicy?.let { policy ->
                    deviceBackgroundLauncher.launch(
                        DeviceBackgroundPolicy.settingsIntent(context, policy)
                    )
                }
            },
            { details = it }
        )

        Spacer(Modifier.height(18.dp))
        SettingsCard("Strict Mode", R.drawable.ic_settings) {
            SettingsRow(
                "Screen content blocker",
                if (accessibility && overlay) "Active · scans supported apps on-device"
                else "Requires Accessibility and overlay permissions",
                accessibility && overlay,
                R.drawable.ic_visibility_off,
                { openAccessibilitySettings(context) }
            ) {
                details = SettingDetails("Screen content blocker", "KillLust uses Android Accessibility events and on-device screenshots to detect unsafe content. The overlay permission lets it cover detected content. Both permissions are required and can only be changed in Android settings.")
            }
            AppDivider()
            SettingsRow("Block suggestive content", "Also covers semi-nude or sexually suggestive content", strict, R.drawable.ic_blur_on, {
                strict = it
                ProtectionPreferences.setStrictMode(context, it)
                detection = detection.copy(blockSuggestive = it)
            }) {
                details = SettingDetails("Block suggestive content", "Adds the model's broader suggestive category to explicit-content blocking. It catches more content, but may also cover swimwear, fitness, or fashion images.")
            }
            AppDivider()
            SettingsRow(
                "Website protection",
                if (vpn) "Active · adult-content domains blocked locally" else "Uses a local VPN to block adult-content websites",
                vpn,
                R.drawable.ic_vpn_lock,
                { enable ->
                    if (enable) {
                        VpnService.prepare(context)?.let(vpnLauncher::launch) ?: AdultContentVpnService.start(context)
                    } else AdultContentVpnService.stop(context)
                }
            ) {
                details = SettingDetails("Website protection", "KillLust uses a local split-tunnel VPN for DNS filtering. It does not decrypt web traffic. Android allows only one VPN at a time, and VPN approval can only be granted by you.")
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsCard("General", R.drawable.ic_tune) {
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Text("Protection level", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(level.displayName + if (custom) " · Custom" else "", color = BrightBlue, fontWeight = FontWeight.Bold)
                Text(level.description, color = MutedInk, fontSize = 14.sp)
                Slider(
                    value = level.ordinal.toFloat(),
                    onValueChange = { raw ->
                        val selected = ProtectionLevel.entries[raw.roundToInt().coerceIn(0, ProtectionLevel.entries.lastIndex)]
                        if (selected != level || custom) {
                            level = selected
                            ProtectionPreferences.setProtectionLevel(context, selected)
                            detection = ProtectionPreferences.detectionSettings(context)
                            custom = false
                        }
                    },
                    valueRange = 0f..ProtectionLevel.entries.lastIndex.toFloat(),
                    steps = ProtectionLevel.entries.size - 2
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ProtectionLevel.entries.forEach { Text(it.displayName, color = Ink, fontSize = 11.sp) }
                }
            }
            AppDivider()
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Text("Domain block list", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(domains, color = MutedInk, fontSize = 14.sp)
                OutlinedButton(
                    onClick = {
                        domains = "Updating domain list…"
                        AdultDomainListRepository.refresh(context) { result ->
                            result.matcher?.let {
                                domains = "${it.size} domains ready"
                                AdultContentVpnService.reloadList(context)
                            } ?: run { domains = "Update failed · saved list still active" }
                        }
                    },
                    border = BorderStroke(1.dp, Ink),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) { Text("Update domain list", color = Ink) }
            }
            AppDivider()
            SettingsRow(
                "Protection alerts",
                if (notifications) "Enabled · alerts when setup needs attention" else "Allow permission and stopped-service alerts",
                notifications,
                R.drawable.ic_notifications_active,
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openNotificationSettings(context)
                }
            ) {
                details = SettingDetails("Protection alerts", "KillLust periodically checks for missing required permissions and a stopped screen-protection service. Android must allow notifications before these warnings can appear.")
            }
        }

        Spacer(Modifier.height(18.dp))
        OutlinedButton(
            onClick = { tuning = !tuning },
            shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, Ink),
            modifier = Modifier.fillMaxWidth(0.75f)
        ) { Text(if (tuning) "Hide advanced tuning" else "Advanced detection tuning", color = Ink) }

        if (tuning) {
            Spacer(Modifier.height(18.dp))
            SettingsCard("Detection tuning", R.drawable.ic_tune) {
                Text("Lower values catch more content; higher values reduce false alarms.", color = MutedInk,
                    fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                ThresholdSlider("Explicit block threshold", detection.thresholds.explicit, 0.10f..0.99f) {
                    val value = detection.thresholds.copy(explicit = it).normalized()
                    detection = detection.copy(thresholds = value); custom = true
                    ProtectionPreferences.setCustomThresholds(context, value)
                }
                ThresholdSlider("Suggestive block threshold", detection.thresholds.semiNude, 0.10f..0.99f) {
                    val value = detection.thresholds.copy(semiNude = it).normalized()
                    detection = detection.copy(thresholds = value); custom = true
                    ProtectionPreferences.setCustomThresholds(context, value)
                }
                ThresholdSlider("Explicit candidate threshold", detection.thresholds.suspiciousExplicit, 0.05f..detection.thresholds.explicit) {
                    val value = detection.thresholds.copy(suspiciousExplicit = it).normalized()
                    detection = detection.copy(thresholds = value); custom = true
                    ProtectionPreferences.setCustomThresholds(context, value)
                }
                ThresholdSlider("Suggestive candidate threshold", detection.thresholds.suspiciousSemiNude, 0.05f..detection.thresholds.semiNude) {
                    val value = detection.thresholds.copy(suspiciousSemiNude = it).normalized()
                    detection = detection.copy(thresholds = value); custom = true
                    ProtectionPreferences.setCustomThresholds(context, value)
                }
                ThresholdSlider("Second-model threshold", detection.thresholds.verifier, 0.50f..0.99f) {
                    val value = detection.thresholds.copy(verifier = it).normalized()
                    detection = detection.copy(thresholds = value); custom = true
                    ProtectionPreferences.setCustomThresholds(context, value)
                }
                AppDivider()
                SettingsRow("Verifier approval", "Require the second model for decisive hits", detection.requireVerifierForStrongExplicit, R.drawable.ic_verified_user, {
                    detection = detection.copy(requireVerifierForStrongExplicit = it)
                    ProtectionPreferences.setRequireVerifierForStrongExplicit(context, it)
                }) {
                    details = SettingDetails("Verifier approval", "Requiring the independent second model reduces false alarms, but may also let explicit content through if verification cannot run.")
                }
                TextButton(
                    onClick = {
                        ProtectionPreferences.resetCustomThresholds(context)
                        detection = ProtectionPreferences.detectionSettings(context); custom = false
                    }, enabled = custom, modifier = Modifier.fillMaxWidth()
                ) { Text("Reset to ${level.displayName}") }
            }
        }
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun RequiredPermissionsCard(
    accessibility: Boolean, overlay: Boolean, battery: Boolean,
    deviceBackgroundPolicy: DeviceBackgroundPolicy?, deviceBackgroundReady: Boolean,
    onAccessibility: () -> Unit, onOverlay: () -> Unit, onBattery: () -> Unit,
    onDeviceBackground: () -> Unit,
    onDetails: (SettingDetails) -> Unit
) {
    val ready = accessibility && overlay && battery && deviceBackgroundReady
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(82.dp).border(4.dp, BrightBlue, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                Text("S", color = BrightBlue, fontSize = 34.sp, fontWeight = FontWeight.Black)
            }
            Text(
                if (ready) "Required Permissions Enabled" else "Please Enable Required Permissions",
                color = Ink, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                if (ready) "KillLust is ready to protect your screen." else "All listed settings are REQUIRED for reliable screen protection.",
                color = MutedInk, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
            )
            PermissionRow("Accessibility permission", "Allows on-device screen protection to work", R.drawable.ic_accessibility, accessibility, onAccessibility) {
                onDetails(SettingDetails("Accessibility permission", "Required. Android only lets you enable or disable an Accessibility service yourself in system settings."))
            }
            AppDivider()
            PermissionRow("Overlay permission", "Allows KillLust to show a blocking screen", R.drawable.ic_layers, overlay, onOverlay) {
                onDetails(SettingDetails("Overlay permission", "Required. Without Display over other apps access, KillLust can detect unsafe content but cannot cover it."))
            }
            AppDivider()
            PermissionRow("Unrestricted battery use", "Helps protection keep working in background", R.drawable.ic_battery, battery, onBattery) {
                onDetails(SettingDetails("Unrestricted battery use", "Required for reliable background protection, especially on phones with aggressive battery management."))
            }
            deviceBackgroundPolicy?.let { policy ->
                AppDivider()
                PermissionRow(
                    "${policy.displayName} background startup",
                    "Open the device settings and allow KillLust to start automatically",
                    R.drawable.ic_restart,
                    deviceBackgroundReady,
                    onDeviceBackground
                ) {
                    onDetails(
                        SettingDetails(
                            "${policy.displayName} background startup",
                            "This device may stop background protection. Open the device settings and enable Autostart or background activity for KillLust. Android does not let KillLust read this manufacturer setting, so the row is confirmed after you return."
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    summary: String,
    iconRes: Int,
    enabled: Boolean,
    onToggle: () -> Unit,
    onDetails: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable(onClick = onDetails).padding(end = 10.dp)) {
            Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (!enabled) {
                Surface(
                    color = RequiredRed.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        "REQUIRED",
                        color = RequiredRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            Text(summary, color = MutedInk, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
        }
        AppSwitch(enabled) { onToggle() }
    }
}

@Composable
private fun SettingsCard(title: String, iconRes: Int, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = BrightBlue,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(title, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            }
            AppDivider()
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    summary: String,
    checked: Boolean,
    iconRes: Int,
    onChecked: (Boolean) -> Unit,
    onDetails: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable(onClick = onDetails).padding(end = 12.dp)) {
            Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(summary, color = MutedInk, fontSize = 14.sp, lineHeight = 18.sp)
        }
        AppSwitch(checked, onChecked)
    }
}

@Composable
private fun AppSwitch(checked: Boolean, onChecked: (Boolean) -> Unit) = Switch(
    checked = checked, onCheckedChange = onChecked,
    colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White, checkedTrackColor = ActiveGreen,
        uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFB8B9BC),
        uncheckedBorderColor = Color.Transparent
    )
)

@Composable
private fun ThresholdSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
        Text("${(value * 100).roundToInt()}%", color = BrightBlue, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = { onChange((it * 100).roundToInt() / 100f) },
        valueRange = range,
        steps = ((range.endInclusive - range.start) * 100).roundToInt().minus(1).coerceAtLeast(0)
    )
}

@Composable
private fun AppDivider() = HorizontalDivider(color = Divider, thickness = 1.dp)

private fun canPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun openAccessibilitySettings(context: Context) {
    val component = ComponentName(context, ShieldAccessibilityService::class.java)
    val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, component)
    runCatching { context.startActivity(intent) }.onFailure { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }.onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
}

private fun requestBatteryExemption(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }.onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}
