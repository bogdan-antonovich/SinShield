package com.example.sinshield

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.sinshield.ui.theme.SinShieldTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SinShieldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

private data class SettingDetails(
    val title: String,
    val description: String
)

private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
    "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var strictMode by remember { mutableStateOf(ProtectionPreferences.strictMode(context)) }
    var protectionLevel by remember {
        mutableStateOf(ProtectionPreferences.protectionLevel(context))
    }
    var detectionSettings by remember {
        mutableStateOf(ProtectionPreferences.detectionSettings(context))
    }
    var customThresholds by remember {
        mutableStateOf(ProtectionPreferences.hasCustomThresholds(context))
    }
    var advancedTuningExpanded by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var overlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryOptimizationDisabled by remember {
        mutableStateOf(isBatteryOptimizationDisabled(context))
    }
    var vpnEnabled by remember { mutableStateOf(AdultContentVpnService.isRunning) }
    var domainListStatus by remember {
        mutableStateOf("${AdultDomainListRepository.storedDomainCount(context)} domains ready")
    }
    var shownDetails by remember { mutableStateOf<SettingDetails?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AdultContentVpnService.start(context)
        }
        vpnEnabled = AdultContentVpnService.isRunning
    }

    DisposableEffect(context, lifecycleOwner) {
        var active = true
        val stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                vpnEnabled = intent?.getBooleanExtra(
                    AdultContentVpnService.EXTRA_RUNNING,
                    AdultContentVpnService.isRunning
                ) ?: AdultContentVpnService.isRunning
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = isAccessibilityEnabled(context)
                overlayEnabled = Settings.canDrawOverlays(context)
                batteryOptimizationDisabled = isBatteryOptimizationDisabled(context)
                vpnEnabled = AdultContentVpnService.isRunning
                protectionLevel = ProtectionPreferences.protectionLevel(context)
                detectionSettings = ProtectionPreferences.detectionSettings(context)
                customThresholds = ProtectionPreferences.hasCustomThresholds(context)
            }
        }
        ContextCompat.registerReceiver(
            context,
            stateReceiver,
            IntentFilter(AdultContentVpnService.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        AdultDomainListRepository.refreshIfStale(context) { result ->
            if (!active) return@refreshIfStale
            result.matcher?.let {
                domainListStatus = "${it.size} domains ready"
                AdultContentVpnService.reloadList(context)
            } ?: run {
                domainListStatus = "Using the saved list · update unavailable"
            }
        }
        onDispose {
            active = false
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            context.unregisterReceiver(stateReceiver)
        }
    }

    shownDetails?.let { details ->
        AlertDialog(
            onDismissRequest = { shownDetails = null },
            title = { Text(details.title) },
            text = { Text(details.description) },
            confirmButton = {
                TextButton(onClick = { shownDetails = null }) {
                    Text("Got it")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "SinShield", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Protection settings",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Enable the protections you want. Tap a setting's description to learn more.",
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        ProtectionSettingRow(
            title = "Accessibility protection",
            summary = "Scans supported apps on-device for explicit content",
            checked = accessibilityEnabled,
            onCheckedChange = { openAccessibilitySettings(context) },
            onDescriptionClick = {
                shownDetails = SettingDetails(
                    title = "Accessibility protection",
                    description = "SinShield uses Android's Accessibility Service to notice when supported social apps and browsers change, take on-device screenshots, and classify visible media. Screenshots are processed on your device. The separate Blocking overlay permission is also required for SinShield to cover detected content. Android requires you to approve or remove Accessibility access yourself."
                )
            }
        )
        HorizontalDivider()
        ProtectionSettingRow(
            title = "Blocking overlay",
            summary = if (overlayEnabled) {
                "Allowed · SinShield can cover unsafe content"
            } else {
                "Allow SinShield to display blocking screens over other apps"
            },
            checked = overlayEnabled,
            onCheckedChange = { openOverlaySettings(context) },
            onDescriptionClick = {
                shownDetails = SettingDetails(
                    title = "Blocking overlay",
                    description = "SinShield needs Android's Display over other apps permission to place an opaque warning above explicit content and prevent interaction with it. This permission is separate from Accessibility protection and must be enabled manually in system settings. If it is off, SinShield may detect unsafe content but cannot cover it with a blocking screen."
                )
            }
        )
        HorizontalDivider()
        ProtectionSettingRow(
            title = "Website protection",
            summary = if (vpnEnabled) {
                "Active · adult-content domains are blocked locally"
            } else {
                "Uses a local VPN to block adult-content websites"
            },
            checked = vpnEnabled,
            onCheckedChange = { enable ->
                if (enable) {
                    val permissionIntent = VpnService.prepare(context)
                    if (permissionIntent == null) {
                        AdultContentVpnService.start(context)
                    } else {
                        vpnPermissionLauncher.launch(permissionIntent)
                    }
                } else {
                    AdultContentVpnService.stop(context)
                }
            },
            onDescriptionClick = {
                shownDetails = SettingDetails(
                    title = "Website protection",
                    description = "SinShield creates a local, split-tunnel VPN that handles standard DNS requests and returns a blocked response for known adult-content domains. It does not decrypt or inspect your web traffic. Android allows only one VPN at a time, so turning this on may replace another VPN. Apps or browsers using encrypted DNS may bypass standard DNS filtering."
                )
            }
        )
        HorizontalDivider()
        ProtectionSettingRow(
            title = "Disable battery optimization",
            summary = if (batteryOptimizationDisabled) {
                "Enabled · SinShield has unrestricted background use"
            } else {
                "Helps protection remain active in the background"
            },
            checked = batteryOptimizationDisabled,
            onCheckedChange = { enable ->
                if (enable) {
                    requestBatteryOptimizationExemption(context)
                } else {
                    openBatteryOptimizationSettings(context)
                }
            },
            onDescriptionClick = {
                shownDetails = SettingDetails(
                    title = "Disable battery optimization",
                    description = "Android and some phone manufacturers may limit or stop apps that run for a long time in the background. Allowing unrestricted battery use can make SinShield's Accessibility and website protection more reliable, especially on devices with aggressive power saving. It can increase battery usage. The switch reports Android's current setting; removing the exemption must be confirmed in system settings."
                )
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Domain block list",
            modifier = Modifier.fillMaxWidth(),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(text = domainListStatus, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                domainListStatus = "Updating domain list…"
                AdultDomainListRepository.refresh(context) { result ->
                    result.matcher?.let {
                        domainListStatus = "${it.size} domains ready"
                        AdultContentVpnService.reloadList(context)
                    } ?: run {
                        domainListStatus = "Update failed · the saved list is still active"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Update domain list")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Protection level",
            modifier = Modifier.fillMaxWidth(),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = protectionLevel.displayName + if (customThresholds) " · Custom" else "",
            modifier = Modifier.fillMaxWidth(),
            fontWeight = FontWeight.Bold
        )
        Text(text = protectionLevel.description, modifier = Modifier.fillMaxWidth())
        Text(
            text = "Explicit ${(detectionSettings.thresholds.explicit * 100).roundToInt()}% · " +
                "Suggestive ${(detectionSettings.thresholds.semiNude * 100).roundToInt()}% · " +
                "Second model ${(detectionSettings.thresholds.verifier * 100).roundToInt()}%",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 13.sp
        )
        Slider(
            value = protectionLevel.ordinal.toFloat(),
            onValueChange = { value ->
                val selected = ProtectionLevel.entries[value.roundToInt().coerceIn(
                    0,
                    ProtectionLevel.entries.lastIndex
                )]
                if (selected != protectionLevel || customThresholds) {
                    protectionLevel = selected
                    ProtectionPreferences.setProtectionLevel(context, selected)
                    detectionSettings = ProtectionPreferences.detectionSettings(context)
                    customThresholds = false
                }
            },
            valueRange = 0f..ProtectionLevel.entries.lastIndex.toFloat(),
            steps = ProtectionLevel.entries.size - 2,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ProtectionLevel.entries.forEach { level ->
                Text(text = level.displayName, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        ProtectionSettingRow(
            title = "Block suggestive content",
            summary = "Covers the model's Sexy category as well as Porn and Hentai",
            checked = strictMode,
            onCheckedChange = {
                strictMode = it
                ProtectionPreferences.setStrictMode(context, it)
                detectionSettings = detectionSettings.copy(blockSuggestive = it)
            },
            onDescriptionClick = {
                shownDetails = SettingDetails(
                    title = "Block suggestive content",
                    description = "The first model separates Porn and Hentai from a broader Sexy category. Turn this on if sexual or semi-nude material should also be blocked. It catches more content, but may also cover swimwear, fitness, or fashion images."
                )
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { advancedTuningExpanded = !advancedTuningExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (advancedTuningExpanded) "Hide detection tuning" else "Advanced detection tuning")
        }

        if (advancedTuningExpanded) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Changes apply to the running scanner automatically. Lower thresholds catch more content; higher thresholds reduce false alarms.",
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF5F6368)
            )

            DetectionThresholdSlider(
                title = "Explicit block threshold",
                description = "Porn or Hentai score that counts as decisive",
                value = detectionSettings.thresholds.explicit,
                valueRange = 0.10f..0.99f,
                onValueChange = { value ->
                    val updated = detectionSettings.thresholds.copy(explicit = value).normalized()
                    detectionSettings = detectionSettings.copy(thresholds = updated)
                    customThresholds = true
                    ProtectionPreferences.setCustomThresholds(context, updated)
                }
            )
            DetectionThresholdSlider(
                title = "Suggestive block threshold",
                description = "Sexy score used when suggestive blocking is on",
                value = detectionSettings.thresholds.semiNude,
                valueRange = 0.10f..0.99f,
                onValueChange = { value ->
                    val updated = detectionSettings.thresholds.copy(semiNude = value).normalized()
                    detectionSettings = detectionSettings.copy(thresholds = updated)
                    customThresholds = true
                    ProtectionPreferences.setCustomThresholds(context, updated)
                }
            )
            DetectionThresholdSlider(
                title = "Explicit candidate threshold",
                description = "Lower-confidence Porn or Hentai sent to the second model",
                value = detectionSettings.thresholds.suspiciousExplicit,
                valueRange = 0.05f..detectionSettings.thresholds.explicit,
                onValueChange = { value ->
                    val updated = detectionSettings.thresholds
                        .copy(suspiciousExplicit = value)
                        .normalized()
                    detectionSettings = detectionSettings.copy(thresholds = updated)
                    customThresholds = true
                    ProtectionPreferences.setCustomThresholds(context, updated)
                }
            )
            DetectionThresholdSlider(
                title = "Suggestive candidate threshold",
                description = "Lower-confidence Sexy content sent to the second model",
                value = detectionSettings.thresholds.suspiciousSemiNude,
                valueRange = 0.05f..detectionSettings.thresholds.semiNude,
                onValueChange = { value ->
                    val updated = detectionSettings.thresholds
                        .copy(suspiciousSemiNude = value)
                        .normalized()
                    detectionSettings = detectionSettings.copy(thresholds = updated)
                    customThresholds = true
                    ProtectionPreferences.setCustomThresholds(context, updated)
                }
            )
            DetectionThresholdSlider(
                title = "Second-model threshold",
                description = "Confidence required to confirm borderline content",
                value = detectionSettings.thresholds.verifier,
                valueRange = 0.50f..0.99f,
                onValueChange = { value ->
                    val updated = detectionSettings.thresholds.copy(verifier = value).normalized()
                    detectionSettings = detectionSettings.copy(thresholds = updated)
                    customThresholds = true
                    ProtectionPreferences.setCustomThresholds(context, updated)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            ProtectionSettingRow(
                title = "Verifier must approve decisive hits",
                summary = if (detectionSettings.requireVerifierForStrongExplicit) {
                    "Fewer false alarms, but explicit content may be missed"
                } else {
                    "Off · decisive Porn/Hentai scores block immediately"
                },
                checked = detectionSettings.requireVerifierForStrongExplicit,
                onCheckedChange = { required ->
                    detectionSettings = detectionSettings.copy(
                        requireVerifierForStrongExplicit = required
                    )
                    ProtectionPreferences.setRequireVerifierForStrongExplicit(context, required)
                },
                onDescriptionClick = {
                    shownDetails = SettingDetails(
                        title = "Verifier must approve decisive hits",
                        description = "Borderline detections always require the independent second model. When this option is on, even a decisive Porn or Hentai result is discarded if the second model disagrees or cannot run. That reduces false alarms but was the reason strong explicit detections could become safe."
                    )
                }
            )

            TextButton(
                onClick = {
                    ProtectionPreferences.resetCustomThresholds(context)
                    detectionSettings = ProtectionPreferences.detectionSettings(context)
                    customThresholds = false
                },
                enabled = customThresholds,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset thresholds to ${protectionLevel.displayName}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "On supported social apps and browsers, unsafe content opens a protected warning with actions to move past it, return to a safer screen, or leave the app.",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DetectionThresholdSlider(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontWeight = FontWeight.Bold)
        Text("${(value * 100).roundToInt()}%", fontWeight = FontWeight.Bold)
    }
    Text(
        description,
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF5F6368),
        fontSize = 13.sp
    )
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = { onValueChange((it * 100).roundToInt() / 100f) },
        valueRange = valueRange,
        steps = ((valueRange.endInclusive - valueRange.start) * 100).roundToInt()
            .minus(1)
            .coerceAtLeast(0),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ProtectionSettingRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDescriptionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onDescriptionClick)
                .padding(end = 16.dp)
        ) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(text = summary, color = Color(0xFF5F6368))
            Text(
                text = "Tap for details",
                color = Color(0xFF3567A8),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val expectedComponent = ComponentName(context, ShieldAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    return enabledServices
        .split(':')
        .mapNotNull(ComponentName::unflattenFromString)
        .any { it == expectedComponent }
}

private fun isBatteryOptimizationDisabled(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)

private fun openAccessibilitySettings(context: Context) {
    val component = ComponentName(context, ShieldAccessibilityService::class.java)
    val detailsIntent = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS).apply {
        putExtra(Intent.EXTRA_COMPONENT_NAME, component)
    }
    runCatching { context.startActivity(detailsIntent) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
}

private fun openOverlaySettings(context: Context) {
    val detailsIntent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    runCatching { context.startActivity(detailsIntent) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
}

private fun requestBatteryOptimizationExemption(context: Context) {
    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    runCatching { context.startActivity(requestIntent) }
        .onFailure { openBatteryOptimizationSettings(context) }
}

private fun openBatteryOptimizationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}
