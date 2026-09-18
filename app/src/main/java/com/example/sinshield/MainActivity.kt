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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.sinshield.ui.theme.SwitzerHeavyFontFamily
import com.example.sinshield.ui.theme.SinSheldTheme
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
        ProtectionHealthMonitor.restoreExpectedProtection(this)
        setContent {
            SinSheldTheme {
                Scaffold(containerColor = ScreenBlue) { padding ->
                    MainScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

private data class SettingDetails(val title: String, val description: String)

private enum class SetupGuide {
    ACCESSIBILITY,
    OVERLAY,
    BATTERY,
    DEVICE_BACKGROUND,
    ALWAYS_ON_VPN,
    NOTIFICATIONS
}

private data class SetupGuideContent(
    val rowTitle: String,
    val rowSummary: String,
    val iconRes: Int,
    val required: Boolean = false,
    val instructions: List<SetupStep>
)

private data class SetupStep(val text: String, val boldPhrases: List<String>)

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var strict by remember { mutableStateOf(ProtectionPreferences.strictMode(context)) }
    var detection by remember { mutableStateOf(ProtectionPreferences.detectionSettings(context)) }
    var tuning by remember { mutableStateOf(false) }
    var accessibility by remember { mutableStateOf(ProtectionHealthMonitor.isAccessibilityEnabled(context)) }
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var battery by remember { mutableStateOf(ProtectionHealthMonitor.isBatteryOptimizationDisabled(context)) }
    val deviceBackgroundPolicy = remember { DeviceBackgroundPolicy.current() }
    var manufacturerBatteryConfirmed by remember {
        mutableStateOf(
            deviceBackgroundPolicy?.let {
                DeviceBackgroundPolicy.hasConfirmedNoRestrictions(context, it)
            } ?: false
        )
    }
    var deviceBackgroundReady by remember {
        mutableStateOf(
            deviceBackgroundPolicy == null ||
                DeviceBackgroundPolicy.hasVisitedSettings(context, deviceBackgroundPolicy)
        )
    }
    var notifications by remember { mutableStateOf(canPostNotifications(context)) }
    var vpn by remember { mutableStateOf(AdultContentVpnService.isRunning) }
    var alwaysOnVpn by remember {
        mutableStateOf(ProtectionHealthMonitor.wasVpnAlwaysOnObserved(context))
    }
    var exitReport by remember {
        mutableStateOf(
            if (GlobalDebugMode.ENABLED) ProcessExitDiagnostics.latest(context) else null
        )
    }
    var domains by remember { mutableStateOf(formatDomainCount(AdultDomainListRepository.storedDomainCount(context))) }
    var customDomains by remember { mutableStateOf(AdultDomainListRepository.customDomains(context)) }
    var domainInput by remember { mutableStateOf("") }
    var domainError by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<SettingDetails?>(null) }
    var setupGuide by remember { mutableStateOf<SetupGuide?>(null) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var debugPhotoDumps by remember { mutableStateOf(DebugSettings.photoDumps(context)) }
    var debugOverlayFeedback by remember { mutableStateOf(DebugSettings.overlayFeedback(context)) }
    var debugOverlayDismiss by remember { mutableStateOf(DebugSettings.overlayDismiss(context)) }
    var debugLastShutdown by remember { mutableStateOf(DebugSettings.lastShutdown(context)) }

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
    val batterySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        battery = ProtectionHealthMonitor.isBatteryOptimizationDisabled(context)
        deviceBackgroundPolicy?.let { policy ->
            // Xiaomi/HyperOS does not expose its “No restrictions” value through an Android API.
            // Returning from the exact setting page is the confirmation the app can retain.
            DeviceBackgroundPolicy.confirmNoRestrictions(context, policy)
            manufacturerBatteryConfirmed = true
        }
        ProtectionHealthMonitor.checkNow(context)
    }

    DisposableEffect(context, lifecycleOwner) {
        var active = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                vpn = intent?.getBooleanExtra(AdultContentVpnService.EXTRA_RUNNING, AdultContentVpnService.isRunning)
                    ?: AdultContentVpnService.isRunning
                alwaysOnVpn = intent?.getBooleanExtra(
                    AdultContentVpnService.EXTRA_ALWAYS_ON,
                    ProtectionHealthMonitor.wasVpnAlwaysOnObserved(context)
                ) ?: ProtectionHealthMonitor.wasVpnAlwaysOnObserved(context)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibility = ProtectionHealthMonitor.isAccessibilityEnabled(context)
                overlay = Settings.canDrawOverlays(context)
                battery = ProtectionHealthMonitor.isBatteryOptimizationDisabled(context)
                manufacturerBatteryConfirmed = deviceBackgroundPolicy?.let {
                    DeviceBackgroundPolicy.hasConfirmedNoRestrictions(context, it)
                } ?: false
                deviceBackgroundReady = deviceBackgroundPolicy == null ||
                    DeviceBackgroundPolicy.hasVisitedSettings(context, deviceBackgroundPolicy)
                notifications = canPostNotifications(context)
                vpn = AdultContentVpnService.isRunning
                AdultContentVpnService.refreshStatus(context)
                alwaysOnVpn = ProtectionHealthMonitor.wasVpnAlwaysOnObserved(context)
                if (GlobalDebugMode.ENABLED) {
                    exitReport = ProcessExitDiagnostics.latest(context)
                }
                detection = ProtectionPreferences.detectionSettings(context)
                ProtectionHealthMonitor.checkNow(context)
            }
        }
        val stateFilter = IntentFilter(AdultContentVpnService.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            stateFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        lifecycleOwner.lifecycle.addObserver(observer)
        AdultDomainListRepository.refreshIfStale(context) { result ->
            if (!active) return@refreshIfStale
            result.matcher?.let {
                domains = formatDomainCount(it.size)
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

    if (showAccessibilityDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            title = { Text(stringResource(R.string.accessibility_disclosure_title), color = Ink) },
            text = { Text(stringResource(R.string.accessibility_disclosure_body), color = MutedInk) },
            confirmButton = {
                TextButton(onClick = {
                    showAccessibilityDisclosure = false
                    openAccessibilitySettings(context)
                }) { Text(stringResource(R.string.accessibility_disclosure_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDisclosure = false }) {
                    Text(stringResource(R.string.accessibility_disclosure_cancel))
                }
            }
        )
    }

    setupGuide?.let { guide ->
        val enabled = when (guide) {
            SetupGuide.ACCESSIBILITY -> accessibility
            SetupGuide.OVERLAY -> overlay
            SetupGuide.BATTERY -> battery || manufacturerBatteryConfirmed
            SetupGuide.DEVICE_BACKGROUND -> deviceBackgroundReady
            SetupGuide.ALWAYS_ON_VPN -> alwaysOnVpn
            SetupGuide.NOTIFICATIONS -> notifications
        }
        val openSettings = {
            when (guide) {
                SetupGuide.ACCESSIBILITY ->
                    if (accessibility) openAccessibilitySettings(context)
                    else showAccessibilityDisclosure = true
                SetupGuide.OVERLAY -> openOverlaySettings(context)
                SetupGuide.BATTERY -> batterySettingsLauncher.launch(batterySettingsIntent(context))
                SetupGuide.DEVICE_BACKGROUND -> {
                    deviceBackgroundPolicy?.let { policy ->
                        deviceBackgroundLauncher.launch(
                            DeviceBackgroundPolicy.settingsIntent(context, policy)
                        )
                    }
                    Unit
                }
                SetupGuide.ALWAYS_ON_VPN -> openVpnSettings(context)
                SetupGuide.NOTIFICATIONS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                }
            }
        }
        SetupGuideScreen(
            modifier = modifier,
            content = setupGuideContent(guide, deviceBackgroundPolicy),
            enabled = enabled,
            onBack = { setupGuide = null },
            onOpenSettings = openSettings
        )
        return
    }

    Column(
        modifier.fillMaxSize().background(ScreenBlue).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SinSheldWordmark()
        Spacer(Modifier.height(18.dp))

        RequiredPermissionsCard(
            accessibility, overlay,
            {
                if (accessibility) openAccessibilitySettings(context)
                else setupGuide = SetupGuide.ACCESSIBILITY
            },
            {
                if (overlay) openOverlaySettings(context)
                else setupGuide = SetupGuide.OVERLAY
            },
            { details = it }
        )

        Spacer(Modifier.height(18.dp))
        ReliabilityCenterCard(
            battery = battery,
            manufacturerBatteryConfirmed = manufacturerBatteryConfirmed,
            deviceBackgroundPolicy = deviceBackgroundPolicy,
            deviceBackgroundReady = deviceBackgroundReady,
            vpnRunning = vpn,
            vpnExpected = ProtectionHealthMonitor.isVpnExpected(context),
            alwaysOnVpn = alwaysOnVpn,
            notifications = notifications,
            onBattery = {
                if (battery || manufacturerBatteryConfirmed) {
                    batterySettingsLauncher.launch(batterySettingsIntent(context))
                } else {
                    setupGuide = SetupGuide.BATTERY
                }
            },
            onDeviceBackground = {
                if (deviceBackgroundReady) {
                    deviceBackgroundPolicy?.let { policy ->
                        deviceBackgroundLauncher.launch(DeviceBackgroundPolicy.settingsIntent(context, policy))
                    }
                } else setupGuide = SetupGuide.DEVICE_BACKGROUND
            },
            onWebsiteProtection = { enable ->
                if (enable) {
                    VpnService.prepare(context)?.let(vpnLauncher::launch)
                        ?: AdultContentVpnService.start(context)
                } else if (alwaysOnVpn) {
                    openVpnSettings(context)
                } else {
                    AdultContentVpnService.stop(context)
                }
            },
            onAlwaysOnVpn = {
                if (alwaysOnVpn) openVpnSettings(context)
                else setupGuide = SetupGuide.ALWAYS_ON_VPN
            },
            onNotifications = {
                if (notifications) openNotificationSettings(context)
                else setupGuide = SetupGuide.NOTIFICATIONS
            },
            onRepair = {
                val missing = ProtectionHealthMonitor.missingRequirements(context)
                val vpnRestartRequested = missing.isEmpty() &&
                    ProtectionHealthMonitor.isVpnExpected(context) &&
                    !AdultContentVpnService.isRunning &&
                    VpnService.prepare(context) == null
                ProtectionHealthMonitor.checkNow(context)
                ProtectionHealthMonitor.restoreExpectedProtection(context)
                vpn = AdultContentVpnService.isRunning
                val feedback = when {
                    missing.isNotEmpty() ->
                        "Check complete · Needs attention: ${missing.joinToString()}"
                    vpnRestartRequested ->
                        "Repair requested · restarting website protection"
                    else ->
                        "Check complete · protection is ready"
                }
                Toast.makeText(context, feedback, Toast.LENGTH_LONG).show()
            },
            onDetails = { details = it }
        )

        Spacer(Modifier.height(18.dp))
        SettingsCard("Strict Mode", R.drawable.ic_settings) {
            SettingsRow("Block suggestive content", "Also covers semi-nude or sexually suggestive content", strict, R.drawable.ic_blur_on, {
                strict = it
                ProtectionPreferences.setStrictMode(context, it)
                detection = detection.copy(blockSuggestive = it)
            }) {
                details = SettingDetails("Block suggestive content", "Adds the model's broader suggestive category to explicit-content blocking. It catches more content, but may also cover swimwear, fitness, or fashion images.")
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsCard("General", R.drawable.ic_tune) {
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Text("Domain block list", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(domains, color = MutedInk, fontSize = 14.sp)
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = {
                        domainInput = it
                        domainError = null
                    },
                    label = { Text("Domain to block") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    isError = domainError != null,
                    supportingText = domainError?.let { message -> { Text(message) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                Button(
                    onClick = {
                        when (AdultDomainListRepository.addCustomDomain(context, domainInput)) {
                            AddCustomDomainResult.ADDED -> {
                                domainInput = ""
                                domainError = null
                                customDomains = AdultDomainListRepository.customDomains(context)
                                domains = formatDomainCount(
                                    AdultDomainListRepository.storedDomainCount(context)
                                )
                                AdultContentVpnService.reloadList(context)
                            }
                            AddCustomDomainResult.INVALID ->
                                domainError = "Enter a valid domain, such as example.com."
                            AddCustomDomainResult.ALREADY_BLOCKED ->
                                domainError = "This domain is already covered by the block list."
                        }
                    },
                    enabled = domainInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrightBlue)
                ) {
                    Text("Add domain")
                }
                if (customDomains.isNotEmpty()) {
                    Text(
                        "Your domains",
                        color = Ink,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                    customDomains.forEach { domain ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(domain, color = MutedInk, modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    AdultDomainListRepository.removeCustomDomain(context, domain)
                                    customDomains = AdultDomainListRepository.customDomains(context)
                                    domains = formatDomainCount(
                                        AdultDomainListRepository.storedDomainCount(context)
                                    )
                                    AdultContentVpnService.reloadList(context)
                                }
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        domains = "Updating domain list…"
                        AdultDomainListRepository.refresh(context) { result ->
                            result.matcher?.let {
                                domains = formatDomainCount(it.size)
                                AdultContentVpnService.reloadList(context)
                            } ?: run { domains = "Update failed · saved list still active" }
                        }
                    },
                    border = BorderStroke(1.dp, Ink),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) { Text("Refresh built-in list", color = Ink) }
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
                ThresholdSlider(
                    "Explicit block threshold",
                    "How certain the primary model must be before explicit content blocks immediately. For example, 40% blocks a Porn or Hentai score of 40% or higher; raising it reduces false alarms.",
                    detection.thresholds.explicit,
                    0.10f..0.99f
                ) {
                    val value = detection.thresholds.copy(explicit = it).normalized()
                    detection = detection.copy(thresholds = value)
                    ProtectionPreferences.setCustomThresholds(context, value)
                }
                ThresholdSlider(
                    "Suggestive block threshold",
                    "How certain the primary model must be before suggestive content is accepted as a strong detection. For example, 50% accepts a Sexy score of 50% or higher; blocking it still depends on Strict Mode.",
                    detection.thresholds.semiNude,
                    0.10f..0.99f
                ) {
                    val value = detection.thresholds.copy(semiNude = it).normalized()
                    detection = detection.copy(thresholds = value)
                    ProtectionPreferences.setCustomThresholds(context, value)
                }
                ThresholdSlider(
                    "Explicit candidate threshold",
                    "The lower score that marks possible explicit content for confirmation instead of treating it as safe. For example, 25% sends a 30% Porn score through the confirmation path rather than blocking immediately.",
                    detection.thresholds.suspiciousExplicit,
                    0.05f..detection.thresholds.explicit
                ) {
                    val value = detection.thresholds.copy(suspiciousExplicit = it).normalized()
                    detection = detection.copy(thresholds = value)
                    ProtectionPreferences.setCustomThresholds(context, value)
                }
                ThresholdSlider(
                    "Suggestive candidate threshold",
                    "The lower score that marks possible suggestive content for confirmation. For example, 20% keeps a 30% Sexy score under review; lowering it catches weaker signals but does more verification work.",
                    detection.thresholds.suspiciousSemiNude,
                    0.05f..detection.thresholds.semiNude
                ) {
                    val value = detection.thresholds.copy(suspiciousSemiNude = it).normalized()
                    detection = detection.copy(thresholds = value)
                    ProtectionPreferences.setCustomThresholds(context, value)
                }
                ThresholdSlider(
                    "Second-model threshold",
                    "How strongly the independent verifier must agree before a candidate can block. For example, 50% accepts the verifier's basic NSFW decision; 80% requires a much clearer result.",
                    detection.thresholds.verifier,
                    0.50f..0.99f
                ) {
                    val value = detection.thresholds.copy(verifier = it).normalized()
                    detection = detection.copy(thresholds = value)
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
                        ProtectionPreferences.resetDetectionTuningToRecommended(context)
                        detection = ProtectionPreferences.detectionSettings(context)
                    },
                    enabled = detection.thresholds != ProtectionLevel.RECOMMENDED.thresholds ||
                        !detection.requireVerifierForStrongExplicit,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Return to recommended settings") }
            }
        }

        if (GlobalDebugMode.ENABLED) {
            Spacer(Modifier.height(18.dp))
            SettingsCard("Debug", R.drawable.ic_tune) {
                Text(
                    "Developer tools enabled through GlobalDebugMode in code.",
                    color = MutedInk,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                SettingsRow(
                    "Photo dumps",
                    "Save model crops and OpenCV diagnostics to the app's Pictures folder",
                    debugPhotoDumps,
                    R.drawable.ic_visibility_off,
                    {
                        debugPhotoDumps = it
                        DebugSettings.setPhotoDumps(context, it)
                    }
                ) {
                    details = SettingDetails(
                        "Photo dumps",
                        "Writes classifier inputs, verifier inputs, region maps, and OpenCV diagnostics. Image encoding runs during analysis and can make scans substantially slower."
                    )
                }
                AppDivider()
                SettingsRow(
                    "Overlay feedback",
                    "Show the detection feedback question on blocking overlays",
                    debugOverlayFeedback,
                    R.drawable.ic_verified_user,
                    {
                        debugOverlayFeedback = it
                        DebugSettings.setOverlayFeedback(context, it)
                    }
                ) {
                    details = SettingDetails(
                        "Overlay feedback",
                        "Adds Yes and No feedback controls to new full-screen blocking overlays. False-positive reports include the captured evidence."
                    )
                }
                AppDivider()
                SettingsRow(
                    "Overlay close button",
                    "Show a debug-only button that immediately dismisses a blocking overlay",
                    debugOverlayDismiss,
                    R.drawable.ic_layers,
                    {
                        debugOverlayDismiss = it
                        DebugSettings.setOverlayDismiss(context, it)
                    }
                ) {
                    details = SettingDetails(
                        "Overlay close button",
                        "Shows the debug dismiss button on new full-screen blocking overlays, bypassing the normal recovery choices."
                    )
                }
                AppDivider()
                SettingsRow(
                    "Last protection stop",
                    "Show Android's previous-process shutdown diagnostics",
                    debugLastShutdown,
                    R.drawable.ic_restart,
                    {
                        debugLastShutdown = it
                        DebugSettings.setLastShutdown(context, it)
                    }
                ) {
                    details = SettingDetails(
                        "Last protection stop",
                        "Shows the reason, time, and memory information Android recorded for the previous SinShield process. This diagnostic stays hidden unless both global debug mode and this switch are on."
                    )
                }
                if (debugLastShutdown) {
                    exitReport?.let { report ->
                        AppDivider()
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                details = SettingDetails(report.headline, report.detail)
                            }.padding(vertical = 15.dp)
                        ) {
                            Text(
                                report.headline,
                                color = if (report.needsAttention) RequiredRed else Ink,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                report.detail,
                                color = MutedInk,
                                fontSize = 14.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.privacy_policy_link),
            color = MutedInk,
            fontSize = 14.sp,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable { openPrivacyPolicy(context) }
                .padding(8.dp)
        )
        Spacer(Modifier.height(36.dp))
    }
}

private fun openPrivacyPolicy(context: Context) {
    val url = context.getString(R.string.privacy_policy_url)
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun ReliabilityCenterCard(
    battery: Boolean,
    manufacturerBatteryConfirmed: Boolean,
    deviceBackgroundPolicy: DeviceBackgroundPolicy?,
    deviceBackgroundReady: Boolean,
    vpnRunning: Boolean,
    vpnExpected: Boolean,
    alwaysOnVpn: Boolean,
    notifications: Boolean,
    onBattery: () -> Unit,
    onDeviceBackground: () -> Unit,
    onWebsiteProtection: (Boolean) -> Unit,
    onAlwaysOnVpn: () -> Unit,
    onNotifications: () -> Unit,
    onRepair: () -> Unit,
    onDetails: (SettingDetails) -> Unit
) {
    SettingsCard("Optional & reliability", R.drawable.ic_restart) {
        Text(
            "Optional controls improve background reliability, website blocking, and status alerts.",
            color = MutedInk,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        SettingsRow(
            if (deviceBackgroundPolicy != null) "Unrestricted battery use"
            else "Android battery exemption",
            if (deviceBackgroundPolicy != null) {
                if (battery || manufacturerBatteryConfirmed) {
                    "Confirmed · ${deviceBackgroundPolicy.displayName} Battery saver is unrestricted"
                } else {
                    "Allow unrestricted background activity"
                }
            } else if (battery) {
                "Verified by Android · exempt from Doze restrictions"
            } else {
                "Allow unrestricted background activity"
            },
            battery || manufacturerBatteryConfirmed,
            R.drawable.ic_battery,
            { onBattery() }
        ) {
            onDetails(
                SettingDetails(
                    "Background battery use",
                    "Optional, but recommended. Allowing unrestricted battery use helps Android keep screen and website protection available in the background."
                )
            )
        }
        deviceBackgroundPolicy?.let { policy ->
            AppDivider()
            SettingsRow(
                "${policy.displayName} background startup",
                "Allow SinShield to start automatically after reboot or process death",
                deviceBackgroundReady,
                R.drawable.ic_restart,
                { onDeviceBackground() }
            ) {
                onDetails(
                    SettingDetails(
                        "${policy.displayName} background startup",
                        "Optional, but recommended. Enable Autostart or background activity in the device settings. Android does not expose this manufacturer setting, so SinShield remembers that you visited the page."
                    )
                )
            }
        }
        AppDivider()
        SettingsRow(
            "Website protection",
            when {
                vpnRunning && alwaysOnVpn -> "Active · Always-on recovery managed by Android"
                vpnRunning -> "Active · adult-content domains blocked locally"
                else -> "Use a local DNS-only VPN to block adult-content websites"
            },
            vpnRunning,
            R.drawable.ic_vpn_lock,
            onWebsiteProtection
        ) {
            onDetails(
                SettingDetails(
                    "Website protection",
                    "Optional. SinShield uses a local split-tunnel VPN for DNS filtering and does not decrypt web traffic. Android allows only one VPN at a time."
                )
            )
        }
        AppDivider()
        SettingsRow(
            "Always-on VPN",
            if (alwaysOnVpn) "Confirmed by Android · restored after reboot and ordinary process death"
            else "Open Android VPN settings and enable Always-on VPN for SinShield",
            alwaysOnVpn,
            R.drawable.ic_vpn_lock,
            { onAlwaysOnVpn() }
        ) {
            onDetails(
                SettingDetails(
                    "Always-on VPN",
                    "Android reserves this switch for you, but SinShield takes you directly to it. " +
                        "Enable Always-on VPN for automatic startup after reboot and ordinary process death. " +
                            "Android deliberately suppresses every component after a force-stop, so no app can self-recover from that state. " +
                            "Leave “Block connections without VPN” off because SinShield routes DNS only."
                )
            )
        }
        AppDivider()
        SettingsRow(
            "Failure notifications",
            if (notifications) "Allowed · SinShield can report missing or stopped protection"
            else "Notifications are blocked, so failure warnings cannot appear",
            notifications,
            R.drawable.ic_notifications_active,
            { onNotifications() }
        ) {
            onDetails(
                SettingDetails(
                    "Failure notifications",
                    "Keep both the Protection alerts and active-protection notification channels enabled. " +
                        "An abruptly killed process cannot notify at the instant it dies, so the watchdog reports it when Android next wakes SinShield."
                )
            )
        }
        AppDivider()
        Button(
            onClick = onRepair,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrightBlue)
        ) {
            Icon(painterResource(R.drawable.ic_restart), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (vpnExpected && !vpnRunning) "Repair protection now" else "Check protection now")
        }
    }
}

@Composable
private fun SetupGuideScreen(
    modifier: Modifier,
    content: SetupGuideContent,
    enabled: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        modifier.fillMaxSize().background(ScreenBlue).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(
                    "← Back",
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            SinSheldWordmark(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(18.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(content.iconRes),
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(if (content.required) 28.dp else 30.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            content.rowTitle,
                            color = Ink,
                            fontSize = if (content.required) 17.sp else 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (content.required && !enabled) {
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
                        Text(
                            content.rowSummary,
                            color = MutedInk,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    AppSwitch(enabled) { onOpenSettings() }
                }
                content.instructions.forEachIndexed { index, instruction ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "${index + 1}.",
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = SwitzerHeavyFontFamily
                        )
                        Text(
                            highlightedStep(instruction),
                            color = MutedInk,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun highlightedStep(step: SetupStep) = buildAnnotatedString {
    var cursor = 0
    while (cursor < step.text.length) {
        val match = step.boldPhrases
            .mapNotNull { phrase ->
                step.text.indexOf(phrase, cursor).takeIf { it >= 0 }?.let { it to phrase }
            }
            .minByOrNull { it.first }
        if (match == null) {
            append(step.text.substring(cursor))
            break
        }
        append(step.text.substring(cursor, match.first))
        withStyle(
            SpanStyle(
                fontWeight = FontWeight.Black,
                fontFamily = SwitzerHeavyFontFamily,
                color = Ink
            )
        ) {
            append(match.second)
        }
        cursor = match.first + match.second.length
    }
}

private fun setupStep(text: String, vararg boldPhrases: String) =
    SetupStep(text, boldPhrases.toList())

private fun setupGuideContent(
    guide: SetupGuide,
    deviceBackgroundPolicy: DeviceBackgroundPolicy?
): SetupGuideContent = when (guide) {
    SetupGuide.ACCESSIBILITY -> SetupGuideContent(
        rowTitle = "Accessibility permission",
        rowSummary = "Allows on-device screen protection to work",
        iconRes = R.drawable.ic_accessibility,
        required = true,
        instructions = listOf(
            setupStep("Tap the switch above. Android will open Accessibility settings.", "switch above", "Accessibility settings"),
            setupStep("If you see a list, open Downloaded apps or Installed services, then tap SinShield.", "Downloaded apps", "Installed services", "SinShield"),
            setupStep("Turn on Use SinShield. When Android shows a warning, tap Allow.", "Use SinShield", "Allow"),
            setupStep("Return to SinShield. The switch above should now be on.", "Return to SinShield", "on")
        )
    )
    SetupGuide.OVERLAY -> SetupGuideContent(
        rowTitle = "Overlay permission",
        rowSummary = "Allows SinShield to show a blocking screen",
        iconRes = R.drawable.ic_layers,
        required = true,
        instructions = listOf(
            setupStep("Tap the switch above. Android will open Display over other apps.", "switch above", "Display over other apps"),
            setupStep("If you see a list of apps, tap SinShield.", "SinShield"),
            setupStep(
                "Turn on Allow display over other apps or Appear on top, then return to SinShield.",
                "Allow display over other apps",
                "Appear on top",
                "return to SinShield"
            )
        )
    )
    SetupGuide.BATTERY -> SetupGuideContent(
        rowTitle = if (deviceBackgroundPolicy != null) "Unrestricted battery use"
            else "Android battery exemption",
        rowSummary = "Allow unrestricted background activity",
        iconRes = R.drawable.ic_battery,
        instructions = batteryInstructions(deviceBackgroundPolicy)
    )
    SetupGuide.DEVICE_BACKGROUND -> SetupGuideContent(
        rowTitle = "${deviceBackgroundPolicy?.displayName ?: "Android"} background startup",
        rowSummary = "Allow SinShield to start automatically after reboot or process death",
        iconRes = R.drawable.ic_restart,
        instructions = backgroundStartupInstructions(deviceBackgroundPolicy)
    )
    SetupGuide.ALWAYS_ON_VPN -> SetupGuideContent(
        rowTitle = "Always-on VPN",
        rowSummary = "Open Android VPN settings and enable Always-on VPN for SinShield",
        iconRes = R.drawable.ic_vpn_lock,
        instructions = listOf(
            setupStep("Make sure Website protection is already on in SinShield's main screen.", "Website protection", "on"),
            setupStep("Tap the switch above. Android will open VPN settings.", "switch above", "VPN settings"),
            setupStep("Find SinShield and tap the gear or settings icon beside it.", "SinShield", "gear or settings icon"),
            setupStep("Turn on Always-on VPN. Keep Block connections without VPN turned off.", "Always-on VPN", "Block connections without VPN", "off")
        )
    )
    SetupGuide.NOTIFICATIONS -> SetupGuideContent(
        rowTitle = "Failure notifications",
        rowSummary = "Notifications are blocked, so failure warnings cannot appear",
        iconRes = R.drawable.ic_notifications_active,
        instructions = listOf(
            setupStep("Tap the switch above.", "switch above"),
            setupStep("When Android asks for permission, tap Allow.", "Allow"),
            setupStep("If notification settings open, turn on Allow notifications and Protection alerts.", "Allow notifications", "Protection alerts"),
            setupStep("Return to SinShield. The switch above should now be on.", "Return to SinShield", "on")
        )
    )
}

private fun batteryInstructions(policy: DeviceBackgroundPolicy?): List<SetupStep> {
    val settingName = when (policy) {
        DeviceBackgroundPolicy.XIAOMI -> "Battery saver, then choose No restrictions"
        DeviceBackgroundPolicy.SAMSUNG -> "Battery, then choose Unrestricted"
        DeviceBackgroundPolicy.HUAWEI -> "Battery or App launch, then allow manual background management"
        DeviceBackgroundPolicy.OPPO -> "Battery usage, then allow background activity"
        DeviceBackgroundPolicy.VIVO -> "Battery, then allow high background power usage"
        null -> "Battery usage, then choose Unrestricted or Not optimized"
    }
    return listOf(
        setupStep("Tap the switch above. Android will open SinShield's battery settings or show a confirmation.", "switch above", "SinShield", "battery settings"),
        setupStep("If Android shows a confirmation, tap Allow.", "Allow"),
        setupStep("If battery settings open, find $settingName.", settingName),
        setupStep("Select that option, then return to SinShield.", "Select that option", "return to SinShield")
    )
}

private fun backgroundStartupInstructions(policy: DeviceBackgroundPolicy?): List<SetupStep> =
    when (policy) {
        DeviceBackgroundPolicy.XIAOMI -> listOf(
            setupStep("Tap the switch above to open Xiaomi's Autostart screen.", "switch above", "Autostart"),
            setupStep("Find SinShield in the app list.", "SinShield"),
            setupStep("Turn on the switch beside SinShield, then return to the app.", "Turn on", "SinShield", "return to the app")
        )
        DeviceBackgroundPolicy.HUAWEI -> listOf(
            setupStep("Tap the switch above to open Huawei's App launch screen.", "switch above", "App launch"),
            setupStep("Find SinShield and turn off Manage automatically.", "SinShield", "Manage automatically"),
            setupStep("Turn on Auto-launch, Secondary launch, and Run in background, then tap OK.", "Auto-launch", "Secondary launch", "Run in background", "OK")
        )
        DeviceBackgroundPolicy.OPPO -> listOf(
            setupStep("Tap the switch above to open Oppo's startup settings.", "switch above", "startup settings"),
            setupStep("Find SinShield, then turn on Auto launch or Allow background activity.", "SinShield", "Auto launch", "Allow background activity"),
            setupStep("Return to SinShield when the option is enabled.", "Return to SinShield", "enabled")
        )
        DeviceBackgroundPolicy.VIVO -> listOf(
            setupStep("Tap the switch above to open Vivo's background-startup settings.", "switch above", "background-startup settings"),
            setupStep("Find SinShield and turn on Autostart or Background startup.", "SinShield", "Autostart", "Background startup"),
            setupStep("Return to SinShield when the option is enabled.", "Return to SinShield", "enabled")
        )
        DeviceBackgroundPolicy.SAMSUNG, null -> listOf(
            setupStep("Tap the switch above to open SinShield's app settings.", "switch above", "SinShield", "app settings"),
            setupStep("Tap Battery, then select Unrestricted.", "Battery", "Unrestricted"),
            setupStep("Return to SinShield when Unrestricted is selected.", "Return to SinShield", "Unrestricted")
        )
    }

@Composable
private fun SinSheldWordmark(modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Ink)) { append("Sin") }
            withStyle(SpanStyle(color = BrightBlue)) { append("Shield.") }
        },
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.4).sp,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun RequiredPermissionsCard(
    accessibility: Boolean,
    overlay: Boolean,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onDetails: (SettingDetails) -> Unit
) {
    SettingsCard("Required", R.drawable.ic_verified_user) {
        PermissionRow("Accessibility permission", "Allows on-device screen protection to work", R.drawable.ic_accessibility, accessibility, onAccessibility) {
            onDetails(SettingDetails("Accessibility permission", "Required. Android only lets you enable or disable an Accessibility service yourself in system settings."))
        }
        AppDivider()
        PermissionRow("Overlay permission", "Allows SinSheld to show a blocking screen", R.drawable.ic_layers, overlay, onOverlay) {
            onDetails(SettingDetails("Overlay permission", "Required. Without Display over other apps access, SinSheld can detect unsafe content but cannot cover it."))
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
private fun ThresholdSlider(
    title: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
        Text("${(value * 100).roundToInt()}%", color = BrightBlue, fontWeight = FontWeight.Bold)
    }
    Text(
        description,
        color = MutedInk,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(top = 3.dp)
    )
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

internal fun formatDomainCount(count: Int): String =
    if (count >= 1_000) "${count / 1_000}k+ domains ready" else "$count domains ready"

private fun openAccessibilitySettings(context: Context) {
    val component = ComponentName(context, ShieldAccessibilityService::class.java)
    val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, component)
    runCatching { context.startActivity(intent) }.onFailure { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }.onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
}

private fun batterySettingsIntent(context: Context): Intent {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )
    return direct.takeIf { it.resolveActivity(context.packageManager) != null }
        ?: Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

private fun openVpnSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
}
