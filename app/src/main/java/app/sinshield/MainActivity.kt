package app.sinshield

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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.sinshield.ui.theme.SwitzerHeavyFontFamily
import app.sinshield.ui.theme.SinSheldTheme
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val ScreenBlue = Color(0xFFE5EFFF)
private val CardWhite = Color(0xFFF9FBFF)
private val Ink = Color(0xFF082D48)
private val MutedInk = Color(0xFF52606C)
private val BrightBlue = Color(0xFF087CF0)
private val ActiveGreen = Color(0xFF00D782)
private val RequiredRed = Color(0xFFB3261E)
private val Divider = Color(0xFFD2D7DE)
private val PermissionRowCornerRadius = 0.dp
private val PreviewFocusPadding = 22.dp
private val PreviewFocusPulse = 4.dp
private val PreviewExplanationGap = 14.dp
private val PreviewFirstRowTop = 32.dp
private val PreviewViewportMargin = 14.dp
private const val PREVIEW_UI_SETTLE_MS = 650L
private const val PREVIEW_RESUME_SETTLE_MS = 1_200L
private const val PREVIEW_SCROLL_ANIMATION_MS = 450
private const val PREVIEW_SCRIM_REVEAL_MS = 900
private const val PREVIEW_EXPLANATION_REVEAL_MS = 420

private enum class AppUpdateGateState {
    CHECKING,
    CURRENT,
    REQUIRED
}

class MainActivity : ComponentActivity() {
    private lateinit var appUpdateManager: AppUpdateManager
    private var appUpdateGateState by mutableStateOf(AppUpdateGateState.CHECKING)
    private var firstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        enableEdgeToEdge()
        ProtectionHealthMonitor.start(this)
        ProtectionHealthMonitor.restoreExpectedProtection(this)
        setContent {
            SinSheldTheme {
                CurrentAppContent()
            }
        }
        checkForRequiredUpdate()
    }

    override fun onResume() {
        super.onResume()
        if (!::appUpdateManager.isInitialized) return
        if (firstResume) {
            firstResume = false
            return
        }
        checkForRequiredUpdate()
    }

    @Composable
    private fun CurrentAppContent() {
        var showLoadingScreen by remember { mutableStateOf(true) }
        var appContentReady by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize()) {
            Scaffold(containerColor = ScreenBlue) { padding ->
                Box(Modifier.fillMaxSize()) {
                    MainScreen(
                        modifier = Modifier.padding(padding),
                        guideOfferCanStart =
                            !showLoadingScreen && appUpdateGateState == AppUpdateGateState.CURRENT,
                        guideUiBlocked = appUpdateGateState != AppUpdateGateState.CURRENT,
                        onReady = { appContentReady = true },
                        onPreviewRequiredUpdate = {
                            appUpdateGateState = AppUpdateGateState.REQUIRED
                        }
                    )
                    if (appUpdateGateState == AppUpdateGateState.REQUIRED) {
                        RequiredAppUpdateScreen(
                            modifier = Modifier.padding(padding),
                            onUpdate = ::openPlayStoreListing,
                            onClose = ::finishAndRemoveTask
                        )
                    }
                }
            }
            if (showLoadingScreen) {
                SinShieldLoadingScreen(
                    readyToFinish =
                        appContentReady && appUpdateGateState != AppUpdateGateState.CHECKING,
                    onFinished = { showLoadingScreen = false }
                )
            }
        }
    }

    private fun checkForRequiredUpdate() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                when (appUpdateInfo.updateAvailability()) {
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
                    UpdateAvailability.UPDATE_AVAILABLE -> {
                        appUpdateGateState = AppUpdateGateState.REQUIRED
                    }

                    else -> appUpdateGateState = AppUpdateGateState.CURRENT
                }
            }
            .addOnFailureListener {
                // A temporary Play/network failure must not permanently lock users out.
                if (appUpdateGateState == AppUpdateGateState.CHECKING) {
                    appUpdateGateState = AppUpdateGateState.CURRENT
                }
            }
    }

    private fun openPlayStoreListing() {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")
        ).setPackage("com.android.vending")
        runCatching { startActivity(marketIntent) }
            .onFailure {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    )
                )
            }
    }
}

@Composable
private fun RequiredAppUpdateScreen(
    modifier: Modifier = Modifier,
    onUpdate: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.78f))
    ) {
        RobotGuideMessage(
            title = "You’ve gotta update SinShield",
            description = "A newer version is ready. To keep using SinShield, update the app " +
                "through Google Play.",
            emphasizedPhrases = listOf("newer version", "update the app"),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, top = 18.dp, end = 12.dp),
            animateEntrance = true
        ) {
            PreviewPrimaryButton("Update now", onUpdate)
            PreviewSecondaryButton("Close app", onClose)
        }
    }
}

private data class SettingDetails(val title: String, val description: String)
private data class PreviewPermissionContent(
    val title: String,
    val description: String,
    val emphasizedPhrases: List<String> = emptyList()
)

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
    val disclosureRes: Int? = null,
    val instructions: List<SetupStep>
)

private data class SetupStep(val text: String, val boldPhrases: List<String>)

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    guideOfferCanStart: Boolean = true,
    guideUiBlocked: Boolean = false,
    onReady: () -> Unit = {},
    onPreviewRequiredUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        // Keep the launch screen above the app until this content has reached a rendered frame.
        withFrameNanos { }
        onReady()
    }
    var protectionLevel by remember { mutableStateOf(ProtectionPreferences.protectionLevel(context)) }
    var detection by remember { mutableStateOf(ProtectionPreferences.detectionSettings(context)) }
    var customTuning by remember { mutableStateOf(ProtectionPreferences.hasCustomTuning(context)) }
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
    var debugPhotoDumps by remember { mutableStateOf(DebugSettings.photoDumps(context)) }
    var debugOverlayFeedback by remember { mutableStateOf(DebugSettings.overlayFeedback(context)) }
    var debugLastShutdown by remember { mutableStateOf(DebugSettings.lastShutdown(context)) }
    var streakSnapshot by remember {
        mutableStateOf(
            StreakTracker.updateProtection(
                context,
                ProtectionHealthMonitor.isProtectionOperational(context)
            )
        )
    }
    var showStreakDetails by remember { mutableStateOf(false) }
    var showPreviewFinishedCard by remember { mutableStateOf(false) }
    var previewStage by remember { mutableStateOf(ProtectionPreviewRepository.stage(context)) }
    var showPreviewOffer by remember { mutableStateOf(false) }
    var previewUiAllowed by remember { mutableStateOf(false) }

    LaunchedEffect(showStreakDetails) {
        while (showStreakDetails) {
            streakSnapshot = StreakTracker.updateProtection(
                context,
                ProtectionHealthMonitor.isProtectionOperational(context)
            )
            delay(30_000L)
        }
    }

    LaunchedEffect(guideOfferCanStart) {
        if (!guideOfferCanStart) return@LaunchedEffect
        val savedStage = ProtectionPreviewRepository.stage(context)
        if (savedStage == ProtectionPreviewStage.IDLE) delay(PREVIEW_OFFER_DELAY_MS)
        previewStage = ProtectionPreviewRepository.stage(context)
        previewUiAllowed = true
        showPreviewOffer = ProtectionPreviewRepository.shouldOffer(context)
    }

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
                streakSnapshot = StreakTracker.updateProtection(
                    context,
                    ProtectionHealthMonitor.isProtectionOperational(context)
                )
                if (previewStage == ProtectionPreviewStage.VPN && vpn) {
                    ProtectionPreviewRepository.moveTo(context, ProtectionPreviewStage.READY)
                }
                previewStage = ProtectionPreviewRepository.stage(context)
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
                protectionLevel = ProtectionPreferences.protectionLevel(context)
                customTuning = ProtectionPreferences.hasCustomTuning(context)
                ProtectionHealthMonitor.checkNow(context)
                streakSnapshot = StreakTracker.updateProtection(
                    context,
                    ProtectionHealthMonitor.isProtectionOperational(context)
                )
                previewStage = advancePreviewPermissionStage(
                    context = context,
                    current = ProtectionPreviewRepository.stage(context),
                    accessibility = accessibility,
                    overlay = overlay,
                    vpn = vpn
                )
            }
        }
        val stateFilter = IntentFilter(AdultContentVpnService.ACTION_STATE_CHANGED)
        stateFilter.addAction(ProtectionPreviewRepository.ACTION_STATE_CHANGED)
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

    if (previewUiAllowed && !guideUiBlocked) when (previewStage) {
        ProtectionPreviewStage.ACCESSIBILITY,
        ProtectionPreviewStage.OVERLAY,
        ProtectionPreviewStage.BATTERY,
        ProtectionPreviewStage.VPN,
        ProtectionPreviewStage.READY,
        ProtectionPreviewStage.TOUR_INTRO,
        ProtectionPreviewStage.TOUR_STREAK,
        ProtectionPreviewStage.TOUR_PROTECTION_MODE,
        ProtectionPreviewStage.TOUR_ADVANCED_TUNING,
        ProtectionPreviewStage.TOUR_DOMAIN_BLOCK_LIST,
        ProtectionPreviewStage.TOUR_OPTIONAL_PERMISSIONS -> Unit
        ProtectionPreviewStage.WAITING_SITE_BLOCK,
        ProtectionPreviewStage.SITE_EXPLANATION,
        ProtectionPreviewStage.WAITING_CARS_SEARCH,
        ProtectionPreviewStage.WAITING_IMAGES,
        ProtectionPreviewStage.IMAGE_EXPLANATION -> {
            PreviewInProgressScreen(
                modifier = modifier,
                stage = previewStage,
                onResume = { openPreviewBrowser(context) },
                onCancel = {
                    ProtectionPreviewRepository.maybeLater(context)
                    previewStage = ProtectionPreviewStage.IDLE
                }
            )
            return
        }
        ProtectionPreviewStage.IDLE -> Unit
    }

    if (showStreakDetails) {
        BackHandler { showStreakDetails = false }
        StreakDetailsScreen(
            modifier = modifier,
            startedAtMillis = streakSnapshot.startedAtMillis,
            onBack = { showStreakDetails = false }
        )
        return
    }

    details?.let { value ->
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(value.title, color = Ink) },
            text = { Text(value.description, color = MutedInk) },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Got it") } }
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
                SetupGuide.ACCESSIBILITY -> openAccessibilitySettings(context)
                SetupGuide.OVERLAY -> openOverlaySettings(context)
                SetupGuide.BATTERY -> batterySettingsLauncher.launch(batterySettingsIntent(context))
                SetupGuide.DEVICE_BACKGROUND -> {
                    deviceBackgroundPolicy?.let { policy ->
                        openDeviceBackgroundSettings(context, policy, deviceBackgroundLauncher::launch)
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

    val permissionPreviewActive = previewUiAllowed && !guideUiBlocked && (
        previewStage == ProtectionPreviewStage.ACCESSIBILITY ||
            previewStage == ProtectionPreviewStage.OVERLAY ||
            previewStage == ProtectionPreviewStage.VPN
        )
    val settingsTourActive =
        previewUiAllowed && !guideUiBlocked && previewStage in settingsTourStages
    val guidedPreviewActiveRaw = permissionPreviewActive || settingsTourActive
    val mainDensity = LocalDensity.current
    val mainScrollState = rememberScrollState()
    var previewTarget by remember { mutableStateOf<Pair<ProtectionPreviewStage, Rect>?>(null) }
    val previewRangeStarts = remember { mutableStateMapOf<ProtectionPreviewStage, Rect>() }
    val previewRangeEnds = remember { mutableStateMapOf<ProtectionPreviewStage, Rect>() }
    var previewRootPosition by remember { mutableStateOf(Offset.Zero) }
    var previewRootSize by remember { mutableStateOf(IntSize.Zero) }
    var previewExplanationHeight by remember {
        mutableStateOf<Pair<ProtectionPreviewStage, Int>?>(null)
    }
    var preparingPreviewStage by remember { mutableStateOf<ProtectionPreviewStage?>(null) }
    var scrolledPreviewStage by remember { mutableStateOf<ProtectionPreviewStage?>(null) }
    var robotAnchor by remember {
        mutableStateOf<Pair<ProtectionPreviewStage, Offset>?>(null)
    }
    var arrivedRobotStage by remember { mutableStateOf<ProtectionPreviewStage?>(null) }

    val reportPreviewRangeStart = { stage: ProtectionPreviewStage, bounds: Rect ->
        previewRangeStarts[stage] = bounds
        previewRangeEnds[stage]?.let { end ->
            previewTarget = stage to Rect(
                left = minOf(bounds.left, end.left),
                top = bounds.top,
                right = maxOf(bounds.right, end.right),
                bottom = end.bottom
            )
        }
        Unit
    }
    val reportPreviewRangeEnd = { stage: ProtectionPreviewStage, bounds: Rect ->
        previewRangeEnds[stage] = bounds
        previewRangeStarts[stage]?.let { start ->
            previewTarget = stage to Rect(
                left = minOf(start.left, bounds.left),
                top = start.top,
                right = maxOf(start.right, bounds.right),
                bottom = bounds.bottom
            )
        }
        Unit
    }

    val advanceSettingsTour = {
        val next = nextSettingsTourStage(previewStage)
        if (next == null) {
            showPreviewFinishedCard = true
        } else {
            ProtectionPreviewRepository.moveTo(context, next)
            previewStage = next
        }
    }

    val finishPreview = {
        showPreviewFinishedCard = false
        ProtectionPreviewRepository.complete(context)
        previewStage = ProtectionPreviewStage.IDLE
    }

    val openCurrentPreviewPermission = {
        when (previewStage) {
            ProtectionPreviewStage.ACCESSIBILITY -> openAccessibilitySettings(context)
            ProtectionPreviewStage.OVERLAY -> openOverlaySettings(context)
            ProtectionPreviewStage.VPN -> {
                VpnService.prepare(context)?.let(vpnLauncher::launch)
                    ?: AdultContentVpnService.start(context)
            }
            else -> Unit
        }
    }

    // Accessibility does not use the explanation height to calculate its destination. Keeping
    // that changing measurement out of this effect's keys prevents it from cancelling an active
    // scroll and leaving preparingPreviewStage permanently claimed.
    val previewExplanationMeasurementKey = if (
        previewStage == ProtectionPreviewStage.ACCESSIBILITY
    ) {
        null
    } else {
        previewExplanationHeight
    }
    LaunchedEffect(
        previewStage,
        previewTarget?.first,
        previewExplanationMeasurementKey,
        previewRootSize,
        mainScrollState.maxValue,
        guidedPreviewActiveRaw
    ) {
        if (!guidedPreviewActiveRaw) {
            preparingPreviewStage = null
            scrolledPreviewStage = null
            previewTarget = null
            previewExplanationHeight = null
            return@LaunchedEffect
        }
        val target = previewTarget
        val explanationHeightPx = previewExplanationHeight
            ?.takeIf { it.first == previewStage }
            ?.second
        // The first permission step only needs the row bounds. Requiring the hidden explanation
        // card to measure before scrolling can deadlock this transition on a fresh composition.
        if (
            previewStage != ProtectionPreviewStage.ACCESSIBILITY &&
            explanationHeightPx == null
        ) {
            return@LaunchedEffect
        }
        if (
            target?.first == previewStage &&
            previewRootSize.height > 0 &&
            preparingPreviewStage != previewStage &&
            scrolledPreviewStage != previewStage
        ) {
            val targetTop = target.second.top - previewRootPosition.y
            val targetBottom = target.second.bottom - previewRootPosition.y
            val previousScroll = mainScrollState.value
            val accessibilityDestination = if (
                previewStage == ProtectionPreviewStage.ACCESSIBILITY
            ) {
                accessibilityPreviewScrollDestination(
                    currentScroll = previousScroll,
                    maxScroll = mainScrollState.maxValue,
                    targetTop = targetTop,
                    targetBottom = targetBottom,
                    viewportHeight = previewRootSize.height.toFloat(),
                    desiredTop = with(mainDensity) { PreviewFirstRowTop.toPx() },
                    viewportMargin = with(mainDensity) {
                        (PreviewViewportMargin + PreviewFocusPadding + PreviewFocusPulse).toPx()
                    }
                )
            } else {
                null
            }
            val destination = if (previewStage == ProtectionPreviewStage.ACCESSIBILITY) {
                accessibilityDestination ?: previousScroll
            } else {
                val panelGap = with(mainDensity) {
                    (PreviewFocusPadding + PreviewExplanationGap).toPx()
                }
                val bottomMargin = with(mainDensity) { PreviewViewportMargin.toPx() }
                val requiredBottom = targetBottom + panelGap + checkNotNull(explanationHeightPx)
                val availableBottom = previewRootSize.height - bottomMargin
                val overflow = (requiredBottom - availableBottom).coerceAtLeast(0f)
                previousScroll + overflow.roundToInt()
            }.coerceIn(0, mainScrollState.maxValue)

            // A scroll is its own cue, so it starts the instant the target is known — no
            // artificial wait. Only when there's nothing to scroll to (already in view) do
            // we hold for a beat first, since an instant highlight on an unchanged screen
            // would otherwise appear the moment the screen is shown.
            val stageBeingPrepared = previewStage
            preparingPreviewStage = stageBeingPrepared
            try {
                if (
                    (stageBeingPrepared == ProtectionPreviewStage.ACCESSIBILITY &&
                        accessibilityDestination == null) ||
                    destination == previousScroll
                ) {
                    delay(PREVIEW_RESUME_SETTLE_MS)
                } else {
                    mainScrollState.animateScrollTo(
                        destination,
                        animationSpec = tween(
                            PREVIEW_SCROLL_ANIMATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                // Wait for the row's onGloballyPositioned callback to publish its final viewport
                // coordinates. The overlay must use that measurement rather than adjusting the old
                // rectangle by the requested scroll amount: the animation can be clamped or rounded.
                withFrameNanos { }
                scrolledPreviewStage = stageBeingPrepared
                showPreviewOffer = false
            } finally {
                // Layout measurements are effect keys for later tour stages. If one changes during
                // a suspension, Compose cancels this run before restarting it; always release the
                // claim so the replacement run can finish the transition.
                if (preparingPreviewStage == stageBeingPrepared) {
                    preparingPreviewStage = null
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                previewRootPosition = it.positionInRoot()
                previewRootSize = it.size
            }
    ) {
        Column(
            Modifier.fillMaxSize().background(ScreenBlue).verticalScroll(mainScrollState)
                .padding(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        SinSheldWordmark()
        Spacer(Modifier.height(18.dp))

        StreakCard(
            snapshot = streakSnapshot,
            onOpen = {
                streakSnapshot = StreakTracker.startIfEligible(
                    context,
                    ProtectionHealthMonitor.isProtectionOperational(context)
                )
                showStreakDetails = true
            },
            modifier = previewRowModifier(
                previewStage.takeIf { settingsTourActive },
                ProtectionPreviewStage.TOUR_STREAK,
                onBounds = { stage, bounds -> previewTarget = stage to bounds }
            )
        )
        Spacer(Modifier.height(18.dp))

        SettingsCard(
            "Protection mode",
            R.drawable.ic_settings,
            containerModifier = previewRowModifier(
                previewStage.takeIf { settingsTourActive },
                ProtectionPreviewStage.TOUR_PROTECTION_MODE,
                onBounds = reportPreviewRangeStart
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        previewRowModifier(
                            previewStage.takeIf { settingsTourActive },
                            ProtectionPreviewStage.TOUR_PROTECTION_MODE,
                            onBounds = reportPreviewRangeEnd
                        )
                    )
            ) {
                Text(
                    "Choose how sensitive SinShield should be.",
                    color = MutedInk,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 14.dp)
                )
                ProtectionModeControl(
                    selected = protectionLevel,
                    custom = customTuning,
                    onSelected = { level ->
                        protectionLevel = level
                        ProtectionPreferences.setProtectionLevel(context, level)
                        detection = ProtectionPreferences.detectionSettings(context)
                        customTuning = false
                    },
                    modifier = Modifier.padding(top = 14.dp)
                )
                Text(
                    if (customTuning) "Your personal detection settings" else protectionLevel.description,
                    color = MutedInk,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { tuning = !tuning },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrightBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        previewRowModifier(
                            previewStage.takeIf { settingsTourActive },
                            ProtectionPreviewStage.TOUR_ADVANCED_TUNING,
                            onBounds = { stage, bounds -> previewTarget = stage to bounds }
                        )
                    )
            ) {
                Text(if (tuning) "Hide advanced tuning" else "Advanced detection tuning")
            }

            InstantExpandable(visible = tuning) {
                Spacer(Modifier.height(16.dp))
                AppDivider()
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
                    customTuning = true
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
                    customTuning = true
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
                    customTuning = true
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
                    customTuning = true
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
                    customTuning = true
                }
                AppDivider()
                SettingsRow("Verifier approval", "Require the second model for decisive hits", detection.requireVerifierForStrongExplicit, R.drawable.ic_verified_user, {
                    detection = detection.copy(requireVerifierForStrongExplicit = it)
                    ProtectionPreferences.setRequireVerifierForStrongExplicit(context, it)
                    customTuning = ProtectionPreferences.hasCustomTuning(context)
                }) {
                    details = SettingDetails("Verifier approval", "Requiring the independent second model reduces false alarms, but may also let explicit content through if verification cannot run.")
                }
                TextButton(
                    onClick = {
                        ProtectionPreferences.resetDetectionTuningToRecommended(context)
                        protectionLevel = ProtectionLevel.BALANCED
                        detection = ProtectionPreferences.detectionSettings(context)
                        customTuning = false
                    },
                    enabled = customTuning ||
                        detection.thresholds != ProtectionLevel.BALANCED.thresholds ||
                        !detection.requireVerifierForStrongExplicit,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Return to balanced settings") }
            }
        }

        Spacer(Modifier.height(18.dp))
        RequiredPermissionsCard(
            accessibility = accessibility,
            overlay = overlay,
            vpnRunning = vpn,
            alwaysOnVpn = alwaysOnVpn,
            previewStage = previewStage.takeIf { permissionPreviewActive },
            onPreviewTargetBounds = { stage, bounds -> previewTarget = stage to bounds },
            onAccessibility = {
                if (previewStage == ProtectionPreviewStage.ACCESSIBILITY) {
                    openCurrentPreviewPermission()
                } else if (accessibility) openAccessibilitySettings(context)
                else setupGuide = SetupGuide.ACCESSIBILITY
            },
            onOverlay = {
                if (previewStage == ProtectionPreviewStage.OVERLAY) {
                    openCurrentPreviewPermission()
                } else if (overlay) openOverlaySettings(context)
                else setupGuide = SetupGuide.OVERLAY
            },
            onWebsiteProtection = { enable ->
                if (previewStage == ProtectionPreviewStage.VPN) {
                    openCurrentPreviewPermission()
                } else if (enable) {
                    VpnService.prepare(context)?.let(vpnLauncher::launch)
                        ?: AdultContentVpnService.start(context)
                } else if (alwaysOnVpn) {
                    openVpnSettings(context)
                } else {
                    AdultContentVpnService.stop(context)
                }
            },
            onDetails = { details = it }
        )

        val reliabilityContent: @Composable () -> Unit = {
            ReliabilityCenterCard(
                deviceBackgroundPolicy = deviceBackgroundPolicy,
                deviceBackgroundReady = deviceBackgroundReady,
                battery = battery,
                manufacturerBatteryConfirmed = manufacturerBatteryConfirmed,
                vpnRunning = vpn,
                vpnExpected = ProtectionHealthMonitor.isVpnExpected(context),
                alwaysOnVpn = alwaysOnVpn,
                notifications = notifications,
                previewStage = previewStage.takeIf { settingsTourActive },
                onPreviewTargetBounds = { stage, bounds -> previewTarget = stage to bounds },
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
                            openDeviceBackgroundSettings(context, policy, deviceBackgroundLauncher::launch)
                        }
                    } else setupGuide = SetupGuide.DEVICE_BACKGROUND
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
        }

        Spacer(Modifier.height(18.dp))
        SettingsCard(
            "General",
            R.drawable.ic_tune,
            containerModifier = previewRowModifier(
                previewStage.takeIf { settingsTourActive },
                ProtectionPreviewStage.TOUR_DOMAIN_BLOCK_LIST,
                onBounds = reportPreviewRangeStart
            )
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            previewRowModifier(
                                previewStage.takeIf { settingsTourActive },
                                ProtectionPreviewStage.TOUR_DOMAIN_BLOCK_LIST,
                                onBounds = reportPreviewRangeEnd
                            )
                        )
                        .padding(top = 14.dp)
                ) {
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
            }
        }

        Spacer(Modifier.height(18.dp))
        reliabilityContent()

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
                    "Save captured screens, media-region maps, and model crops to the app's Pictures folder",
                    debugPhotoDumps,
                    R.drawable.ic_visibility_off,
                    {
                        debugPhotoDumps = it
                        DebugSettings.setPhotoDumps(context, it)
                    }
                ) {
                    details = SettingDetails(
                        "Photo dumps",
                        "Writes captured browser screens, classifier inputs, verifier inputs, and visual or accessibility media-region maps. Image encoding runs during analysis and can make scans substantially slower."
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
                AppDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onPreviewRequiredUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = BrightBlue),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Preview required update", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            ProtectionPreviewRepository.restartForTesting(context)
                            previewStage = ProtectionPreviewStage.IDLE
                            previewUiAllowed = true
                            showPreviewOffer = true
                        },
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Run setup guide again", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegalLink(
                text = stringResource(R.string.privacy_policy_link),
                onClick = { openPrivacyPolicy(context) }
            )
            Text("·", color = MutedInk, fontSize = 14.sp)
            LegalLink(
                text = stringResource(R.string.terms_and_conditions_link),
                onClick = { openTermsAndConditions(context) }
            )
        }
        Spacer(Modifier.height(36.dp))
        }

        if (
            guidedPreviewActiveRaw &&
            !showPreviewFinishedCard &&
            previewTarget?.first == previewStage &&
            previewExplanationHeight?.first != previewStage
        ) {
            previewPermissionContent(previewStage, deviceBackgroundPolicy)?.let { content ->
                PermissionExplanationCard(
                    content = content,
                    onCancel = {},
                    cancelEnabled = false,
                    onNext = if (settingsTourActive) ({}) else null,
                    nextLabel = "Next",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .alpha(0f)
                        .onSizeChanged { previewExplanationHeight = previewStage to it.height }
                )
            }
        }

        previewTarget
            ?.takeIf {
                guidedPreviewActiveRaw &&
                    !showPreviewFinishedCard &&
                    it.first == previewStage &&
                    scrolledPreviewStage == previewStage &&
                    (robotAnchor == null || arrivedRobotStage == previewStage)
            }
            ?.let { (_, bounds) ->
                PermissionPreviewOverlay(
                    stage = previewStage,
                    deviceBackgroundPolicy = deviceBackgroundPolicy,
                    targetBounds = Rect(
                        left = bounds.left - previewRootPosition.x,
                        top = bounds.top - previewRootPosition.y,
                        right = bounds.right - previewRootPosition.x,
                        bottom = bounds.bottom - previewRootPosition.y
                    ),
                    onTargetClick = if (settingsTourActive) {
                        advanceSettingsTour
                    } else {
                        openCurrentPreviewPermission
                    },
                    onOutsideClick = advanceSettingsTour.takeIf { settingsTourActive },
                    onNext = advanceSettingsTour.takeIf { settingsTourActive },
                    nextLabel = "Next",
                    animateRobotEntrance = robotAnchor == null,
                    onRobotPositionChanged = { position ->
                        robotAnchor = previewStage to position
                        arrivedRobotStage = previewStage
                    },
                    onCancel = {
                        ProtectionPreviewRepository.maybeLater(context)
                        previewStage = ProtectionPreviewStage.IDLE
                    }
                )
            }

        val previousRobot = robotAnchor?.takeIf { it.first != previewStage }
        val nextRobotPosition = if (
            previousRobot != null &&
            guidedPreviewActiveRaw &&
            scrolledPreviewStage == previewStage
        ) {
            val target = previewTarget?.takeIf { it.first == previewStage }?.second
            val explanationHeight = previewExplanationHeight
                ?.takeIf { it.first == previewStage }
                ?.second
            if (target != null && explanationHeight != null && previewRootSize.height > 0) {
                val bottomPadding = with(mainDensity) {
                    when (previewStage) {
                        ProtectionPreviewStage.TOUR_PROTECTION_MODE,
                        ProtectionPreviewStage.TOUR_ADVANCED_TUNING -> 4.dp.toPx()
                        else -> PreviewFocusPadding.toPx()
                    }
                }
                val desiredTop = target.bottom - previewRootPosition.y + bottomPadding +
                    with(mainDensity) { PreviewExplanationGap.toPx() }
                val viewportMargin = with(mainDensity) { PreviewViewportMargin.toPx() }
                val maximumTop = (previewRootSize.height - viewportMargin - explanationHeight)
                    .coerceAtLeast(viewportMargin)
                Offset(
                    x = previewRootSize.width - with(mainDensity) { 80.dp.toPx() },
                    y = desiredTop.coerceIn(viewportMargin, maximumTop)
                )
            } else {
                null
            }
        } else {
            null
        }

        if (
            previousRobot != null &&
            guidedPreviewActiveRaw &&
            !showPreviewFinishedCard
        ) {
            MovingRobotGuideAvatar(
                movementKey = previewStage,
                start = previousRobot.second - previewRootPosition,
                target = nextRobotPosition,
                onArrived = { localPosition ->
                    robotAnchor = previewStage to (localPosition + previewRootPosition)
                    arrivedRobotStage = previewStage
                }
            )
        }

        if (previewUiAllowed && !guideUiBlocked && showPreviewOffer) {
            PreviewOfferScreen(
                modifier = Modifier.matchParentSize(),
                onRobotPositionChanged = { position ->
                    robotAnchor = ProtectionPreviewStage.IDLE to position
                },
                onStart = {
                    details = null
                    setupGuide = null
                    ProtectionPreviewRepository.start(context)
                    val nextStage = advancePreviewPermissionStage(
                        context,
                        ProtectionPreviewStage.ACCESSIBILITY,
                        accessibility,
                        overlay,
                        vpn
                    )
                    previewStage = nextStage
                    showPreviewOffer = false
                },
                onLater = {
                    ProtectionPreviewRepository.maybeLater(context)
                    showPreviewOffer = false
                },
                onDecline = {
                    ProtectionPreviewRepository.decline(context)
                    showPreviewOffer = false
                }
            )
        }

        if (
            previewUiAllowed &&
            !guideUiBlocked &&
            previewStage == ProtectionPreviewStage.READY
        ) {
            PreviewReadyOverlay(
                modifier = Modifier.matchParentSize(),
                onContinue = {
                    ProtectionPreviewRepository.moveTo(
                        context,
                        ProtectionPreviewStage.WAITING_SITE_BLOCK
                    )
                    previewStage = ProtectionPreviewStage.WAITING_SITE_BLOCK
                    openPreviewBrowser(context)
                },
                onCancel = {
                    ProtectionPreviewRepository.maybeLater(context)
                    previewStage = ProtectionPreviewStage.IDLE
                }
            )
        }

        if (
            previewUiAllowed &&
            !guideUiBlocked &&
            previewStage == ProtectionPreviewStage.TOUR_INTRO
        ) {
            PreviewUiTourIntroOverlay(
                modifier = Modifier.matchParentSize(),
                onRobotPositionChanged = { position ->
                    robotAnchor = ProtectionPreviewStage.TOUR_INTRO to position
                    arrivedRobotStage = ProtectionPreviewStage.TOUR_INTRO
                },
                onContinue = {
                    ProtectionPreviewRepository.moveTo(
                        context,
                        ProtectionPreviewStage.TOUR_STREAK
                    )
                    previewStage = ProtectionPreviewStage.TOUR_STREAK
                },
                onCancel = {
                    ProtectionPreviewRepository.maybeLater(context)
                    previewStage = ProtectionPreviewStage.IDLE
                }
            )
        }

        if (!guideUiBlocked && showPreviewFinishedCard) {
            PreviewFinishedOverlay(
                modifier = Modifier.matchParentSize(),
                onDone = finishPreview
            )
        }
    }
}

@Composable
private fun PreviewFinishedOverlay(modifier: Modifier, onDone: () -> Unit) {
    val reveal = remember { Animatable(0f) }
    var messageVisible by remember { mutableStateOf(false) }
    val messageScrim by animateFloatAsState(
        targetValue = if (messageVisible) 1f else 0f,
        animationSpec = tween(260),
        label = "finished guide scrim"
    )
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, animationSpec = tween(PREVIEW_SCRIM_REVEAL_MS, easing = FastOutSlowInEasing))
    }
    Box(
        modifier.background(Ink.copy(alpha = 0.84f * reveal.value * messageScrim))
    ) {
        RobotGuideMessage(
            title = "You’re all set",
            description = "That’s the end of the setup guide. You can close everything now—you " +
                "don’t need to keep SinShield open.\n\n" +
                "• Your protection will stay on in the background.\n" +
                "• Adult websites stay blocked in your browser.\n" +
                "• Sexual content can be covered while you use X and Instagram.\n\n" +
                "Return anytime to check your streak or adjust your protection settings.",
            emphasizedPhrases = listOf(
                "Your protection will stay on",
                "Adult websites",
                "Sexual content",
                "check your streak"
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                .alpha(reveal.value),
            onMessageVisibilityChanged = { messageVisible = it }
        ) {
            PreviewPrimaryButton("Finish", onDone)
        }
    }
}

@Composable
private fun PreviewOfferScreen(
    modifier: Modifier,
    onRobotPositionChanged: (Offset) -> Unit,
    onStart: () -> Unit,
    onLater: () -> Unit,
    onDecline: () -> Unit
) {
    var messageVisible by remember { mutableStateOf(false) }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (messageVisible) 0.78f else 0f,
        animationSpec = tween(260),
        label = "guide offer scrim"
    )
    Box(modifier.background(Ink.copy(alpha = scrimAlpha))) {
        RobotGuideMessage(
        title = "Hi! I’m Shieldbot.",
        description = "I’ll help you get comfortable with SinShield.\n\n" +
            "• Set up the protection settings you need.\n" +
            "• See how SinShield actually works.\n" +
            "• Learn how to adjust your protection.\n\n" +
            "You can drag me anytime to move me wherever you want. Want me to show you around?",
        emphasizedPhrases = listOf(
            "protection settings",
            "how SinShield actually works",
            "adjust your protection",
            "drag me anytime"
        ),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, top = 18.dp, end = 12.dp),
        animateEntrance = true,
        onRobotPositionChanged = onRobotPositionChanged,
        onMessageVisibilityChanged = { messageVisible = it }
        ) {
            PreviewPrimaryButton("Start setup guide", onStart)
            PreviewPrimaryButton("Maybe later", onLater)
            PreviewSecondaryButton("Don’t show this again", onDecline)
        }
    }
}

@Composable
private fun PermissionPreviewOverlay(
    stage: ProtectionPreviewStage,
    deviceBackgroundPolicy: DeviceBackgroundPolicy?,
    targetBounds: Rect,
    onTargetClick: () -> Unit,
    onOutsideClick: (() -> Unit)?,
    onNext: (() -> Unit)?,
    nextLabel: String,
    animateRobotEntrance: Boolean,
    onRobotPositionChanged: (Offset) -> Unit,
    onCancel: () -> Unit
) {
    val content = previewPermissionContent(stage, deviceBackgroundPolicy) ?: return
    val density = LocalDensity.current
    val scrimReveal = remember(stage) { Animatable(0f) }
    val explanationReveal = remember(stage) { Animatable(0f) }
    var previewInteractionReady by remember(stage) { mutableStateOf(false) }
    var robotMessageVisible by remember(stage) { mutableStateOf(false) }
    val robotMessageScrim by animateFloatAsState(
        targetValue = if (robotMessageVisible) 1f else 0f,
        animationSpec = tween(260),
        label = "highlight guide scrim"
    )
    LaunchedEffect(stage) {
        // Let the unchanged screen settle first, then introduce the focus deliberately. The
        // explanation waits until the dimming has completed so the user's eye gets one cue at a
        // time instead of seeing the next step flash in immediately.
        delay(PREVIEW_UI_SETTLE_MS)
        scrimReveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(PREVIEW_SCRIM_REVEAL_MS, easing = FastOutSlowInEasing)
        )
        explanationReveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(PREVIEW_EXPLANATION_REVEAL_MS, easing = FastOutSlowInEasing)
        )
        previewInteractionReady = true
    }
    val horizontalFocusPadding = with(density) { PreviewFocusPadding.toPx() }
    val topFocusPadding = with(density) {
        when (stage) {
            ProtectionPreviewStage.TOUR_ADVANCED_TUNING -> 4.dp.toPx()
            else -> PreviewFocusPadding.toPx()
        }
    }
    val bottomFocusPadding = with(density) {
        when (stage) {
            ProtectionPreviewStage.TOUR_PROTECTION_MODE,
            ProtectionPreviewStage.TOUR_ADVANCED_TUNING -> 4.dp.toPx()
            else -> PreviewFocusPadding.toPx()
        }
    }
    val hole = Rect(
        left = targetBounds.left - horizontalFocusPadding,
        top = targetBounds.top - topFocusPadding,
        right = targetBounds.right + horizontalFocusPadding,
        bottom = targetBounds.bottom + bottomFocusPadding
    )
    val highlightTransition = rememberInfiniteTransition(label = "permission highlight")
    val highlightExpansion by highlightTransition.animateFloat(
        initialValue = 0f,
        targetValue = with(density) {
            when (stage) {
                ProtectionPreviewStage.TOUR_PROTECTION_MODE,
                ProtectionPreviewStage.TOUR_ADVANCED_TUNING -> 0.dp.toPx()
                else -> PreviewFocusPulse.toPx()
            }
        },
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "permission highlight expansion"
    )
    val highlightAlpha by highlightTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "permission highlight alpha"
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val left = hole.left.coerceIn(0f, viewportWidthPx)
        val top = hole.top.coerceIn(0f, viewportHeightPx)
        val right = hole.right.coerceIn(0f, viewportWidthPx)
        val bottom = hole.bottom.coerceIn(0f, viewportHeightPx)
        // While the target hasn't scrolled into the viewport yet (or has scrolled past it),
        // the clamped hole collapses to a sliver pinned at an edge. Drawing that sliver reads
        // as a stuck/broken highlight, so skip the cutout and frame entirely until there's a
        // real hole to show.
        val holeVisible = bottom - top > 1f && right - left > 1f
        Canvas(
            Modifier.fillMaxSize()
        ) {
            val messageVisibility = robotMessageScrim
            val scrim = Ink.copy(alpha = 0.84f * scrimReveal.value * messageVisibility)

            if (!holeVisible) {
                drawRect(scrim, size = size)
            } else {
                drawRect(scrim, size = androidx.compose.ui.geometry.Size(size.width, top))
                drawRect(
                    scrim,
                    topLeft = Offset(0f, bottom),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height - bottom)
                )
                drawRect(
                    scrim,
                    topLeft = Offset(0f, top),
                    size = androidx.compose.ui.geometry.Size(left, bottom - top)
                )
                drawRect(
                    scrim,
                    topLeft = Offset(right, top),
                    size = androidx.compose.ui.geometry.Size(size.width - right, bottom - top)
                )

                drawRoundRect(
                    color = BrightBlue.copy(
                        alpha = highlightAlpha * 0.16f * scrimReveal.value * messageVisibility
                    ),
                    topLeft = Offset(left - highlightExpansion, top - highlightExpansion),
                    size = androidx.compose.ui.geometry.Size(
                        right - left + highlightExpansion * 2,
                        bottom - top + highlightExpansion * 2
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius.Zero,
                    style = Stroke(width = 8.dp.toPx())
                )
                drawRoundRect(
                    color = BrightBlue.copy(
                        alpha = highlightAlpha * scrimReveal.value * messageVisibility
                    ),
                    topLeft = Offset(left - highlightExpansion, top - highlightExpansion),
                    size = androidx.compose.ui.geometry.Size(
                        right - left + highlightExpansion * 2,
                        bottom - top + highlightExpansion * 2
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius.Zero,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        fun Modifier.previewTap(onTap: () -> Unit): Modifier =
            pointerInput(stage, onTap) { detectTapGestures { onTap() } }

        if (previewInteractionReady && onOutsideClick != null) {
            val leftDp = with(density) { left.toDp() }
            val topDp = with(density) { top.toDp() }
            val rightDp = with(density) { right.toDp() }
            val bottomDp = with(density) { bottom.toDp() }
            val middleHeight = with(density) { (bottom - top).toDp() }
            Box(
                Modifier.fillMaxWidth().height(topDp).previewTap(onOutsideClick)
            )
            Box(
                Modifier.fillMaxWidth().offset(y = bottomDp)
                    .height((maxHeight - bottomDp).coerceAtLeast(0.dp))
                    .previewTap(onOutsideClick)
            )
            Box(
                Modifier.offset(y = topDp).width(leftDp).height(middleHeight)
                    .previewTap(onOutsideClick)
            )
            Box(
                Modifier.offset(x = rightDp, y = topDp)
                    .width((maxWidth - rightDp).coerceAtLeast(0.dp))
                    .height(middleHeight)
                    .previewTap(onOutsideClick)
            )
        } else if (previewInteractionReady) {
            Box(
                Modifier
                    .offset(x = with(density) { left.toDp() }, y = with(density) { top.toDp() })
                    .size(
                        width = with(density) { (right - left).toDp() },
                        height = with(density) { (bottom - top).toDp() }
                    )
                    .previewTap(onTargetClick)
            )
        }

        val explanationTopPx = bottom + with(density) { PreviewExplanationGap.toPx() }
        val viewportMarginPx = with(density) { PreviewViewportMargin.roundToPx() }
        Layout(
            content = {
                PermissionExplanationCard(
                    content = content,
                    onCancel = onCancel,
                    onNext = onNext,
                    nextLabel = nextLabel,
                    animateRobotEntrance = animateRobotEntrance,
                    messageRevealDelayMs = when {
                        stage == ProtectionPreviewStage.TOUR_OPTIONAL_PERMISSIONS ->
                            FINAL_UI_TOUR_MESSAGE_DELAY_MS
                        stage in settingsTourStages -> UI_TOUR_MESSAGE_DELAY_MS
                        else -> ROBOT_MESSAGE_DELAY_MS
                    },
                    onRobotPositionChanged = onRobotPositionChanged,
                    onMessageVisibilityChanged = { robotMessageVisible = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { measurables, constraints ->
            val maximumCardHeight =
                (constraints.maxHeight - viewportMarginPx * 2).coerceAtLeast(0)
            val card = measurables.single().measure(
                constraints.copy(minWidth = 0, minHeight = 0, maxHeight = maximumCardHeight)
            )
            val maximumTop =
                (constraints.maxHeight - viewportMarginPx - card.height)
                    .coerceAtLeast(viewportMarginPx)
            val cardTop = explanationTopPx.roundToInt().coerceIn(
                viewportMarginPx,
                maximumTop
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                card.placeRelative(0, cardTop)
            }
        }
    }
}

private fun previewPermissionContent(
    stage: ProtectionPreviewStage,
    deviceBackgroundPolicy: DeviceBackgroundPolicy?
): PreviewPermissionContent? = when (stage) {
        ProtectionPreviewStage.ACCESSIBILITY -> PreviewPermissionContent(
            "Turn on Accessibility",
            "This setting lets SinShield notice screen changes and check for unsafe content.\n\n" +
                "• Tap the highlighted Accessibility permission row.\n" +
                "• Open Downloaded apps, then choose SinShield.\n" +
                "• Turn on Use SinShield and tap Allow.\n" +
                "• Return to SinShield when you’re done.",
            listOf(
                "Accessibility permission",
                "Downloaded apps",
                "Use SinShield",
                "Allow",
                "Return to SinShield"
            )
        )
        ProtectionPreviewStage.OVERLAY -> PreviewPermissionContent(
            "Allow display over other apps",
            "This lets SinShield cover unsafe content when it appears.\n\n" +
                "• Tap the highlighted row.\n" +
                "• Turn on Allow display over other apps for SinShield.\n" +
                "• Return to SinShield when you’re done.",
            listOf("Allow display over other apps", "Return to SinShield")
        )
        ProtectionPreviewStage.BATTERY -> PreviewPermissionContent(
            "Allow unrestricted background use",
            if (deviceBackgroundPolicy == null) {
                "Android may stop SinShield to save battery.\n\n" +
                    "• Tap the highlighted row.\n" +
                    "• Allow SinShield to keep running in the background.\n\n" +
                    "This helps protection stay on while you use other apps."
            } else {
                "${deviceBackgroundPolicy.displayName} may stop SinShield to save battery.\n\n" +
                    "• Tap the highlighted row.\n" +
                    "• Choose unrestricted battery or background use.\n\n" +
                    "This helps protection stay on while you use other apps."
            },
            listOf(
                "highlighted row",
                "running in the background",
                "unrestricted battery or background use",
                "protection stays on"
            )
        )
        ProtectionPreviewStage.VPN -> PreviewPermissionContent(
            "Turn on website protection",
            "Website protection blocks known adult websites on your phone.\n\n" +
                "• Tap the highlighted row.\n" +
                "• When Android asks to set up a VPN connection, tap OK or Allow.\n\n" +
                "SinShield does not send your browsing activity to a server.",
            listOf(
                "blocks known adult websites",
                "VPN connection",
                "OK or Allow",
                "does not send your browsing activity to a server"
            )
        )
        ProtectionPreviewStage.TOUR_STREAK -> PreviewPermissionContent(
            "Your protection streak",
            "Your streak counts the days you keep all protection layers active. Keep them on " +
                "each day to grow your streak. Tap this card anytime to check your progress.",
            listOf(
                "all protection layers active",
                "grow your streak",
                "check your progress"
            )
        )
        ProtectionPreviewStage.TOUR_PROTECTION_MODE -> PreviewPermissionContent(
            "Choose your protection mode",
            "Choose the balance that feels right for you.\n\n" +
                "• Balanced is the best choice for most people.\n" +
                "• Strict blocks more, but may sometimes block a safe image.\n" +
                "• Relaxed reduces false blocks, but may miss more unsafe images.\n\n" +
                "You can change this at any time.",
            listOf("Balanced", "Strict", "Relaxed", "change this at any time")
        )
        ProtectionPreviewStage.TOUR_ADVANCED_TUNING -> PreviewPermissionContent(
            "Advanced settings",
            "These controls change how readily SinShield blocks an image.\n\n" +
                "You do not need to change them—the recommended settings are already selected.",
            listOf("You do not need to change them", "recommended settings")
        )
        ProtectionPreviewStage.TOUR_DOMAIN_BLOCK_LIST -> PreviewPermissionContent(
            "Choose websites to block",
            "Make website protection fit your needs.\n\n" +
                "• Add the address of any website you want SinShield to block.\n" +
                "• Remove a website from this list whenever you want.",
            listOf("Add the address", "Remove a website")
        )
        ProtectionPreviewStage.TOUR_OPTIONAL_PERMISSIONS -> PreviewPermissionContent(
            "Help protection stay active",
            "These settings are optional, but they can make protection more reliable.\n\n" +
                "• Help SinShield start again after your phone restarts.\n" +
                "• Let SinShield warn you if protection stops.",
            listOf("optional", "start again", "warn you if protection stops")
        )
        else -> null
    }

@Composable
private fun PermissionExplanationCard(
    content: PreviewPermissionContent,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancelEnabled: Boolean = true,
    onNext: (() -> Unit)? = null,
    nextLabel: String = "Next",
    animateRobotEntrance: Boolean = true,
    messageRevealDelayMs: Long = ROBOT_MESSAGE_DELAY_MS,
    onRobotPositionChanged: (Offset) -> Unit = {},
    onMessageVisibilityChanged: (Boolean) -> Unit = {}
) {
    RobotGuideMessage(
        title = content.title,
        description = content.description,
        emphasizedPhrases = content.emphasizedPhrases,
        modifier = modifier,
        animateEntrance = animateRobotEntrance,
        showMessageImmediately = !cancelEnabled,
        messageRevealDelayMs = messageRevealDelayMs,
        onRobotPositionChanged = onRobotPositionChanged,
        onMessageVisibilityChanged = onMessageVisibilityChanged
    ) {
        onNext?.let {
            Button(
                onClick = it,
                colors = ButtonDefaults.buttonColors(containerColor = BrightBlue),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp).padding(top = 2.dp)
            ) {
                Text(nextLabel, fontWeight = FontWeight.Bold)
            }
        }
        TextButton(
            onClick = onCancel,
            enabled = cancelEnabled,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
        ) {
            Text("End setup guide", color = MutedInk)
        }
    }
}

@Composable
private fun PreviewReadyOverlay(
    modifier: Modifier,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    var messageVisible by remember { mutableStateOf(false) }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (messageVisible) 0.78f else 0f,
        animationSpec = tween(260),
        label = "ready guide scrim"
    )
    Box(modifier.background(Ink.copy(alpha = scrimAlpha))) {
        RobotGuideMessage(
            title = "The required settings are ready",
            description = "Nice work—the required settings are ready.\n\n" +
                "Next, I’ll meet you in your browser and show you:\n\n" +
                "• Website protection in action.\n" +
                "• Screen protection in action.",
            emphasizedPhrases = listOf("Website protection", "Screen protection"),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp),
            onMessageVisibilityChanged = { messageVisible = it }
        ) {
            PreviewPrimaryButton("Continue", onContinue)
            PreviewSecondaryButton("End setup guide", onCancel)
        }
    }
}

@Composable
private fun PreviewUiTourIntroOverlay(
    modifier: Modifier,
    onRobotPositionChanged: (Offset) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    var messageVisible by remember { mutableStateOf(false) }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (messageVisible) 0.78f else 0f,
        animationSpec = tween(260),
        label = "UI tour intro scrim"
    )
    Box(modifier.background(Ink.copy(alpha = scrimAlpha))) {
        RobotGuideMessage(
            title = "Now let’s look inside SinShield",
            description = "You’ve seen website protection and screen protection in action.\n\n" +
                "Next, I’ll show you around SinShield itself:\n\n" +
                "• Your protection streak\n" +
                "• Protection modes and advanced controls\n" +
                "• Your website block list\n" +
                "• Optional reliability settings",
            emphasizedPhrases = listOf(
                "Your protection streak",
                "Protection modes",
                "advanced controls",
                "website block list",
                "Optional reliability settings"
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp),
            onRobotPositionChanged = onRobotPositionChanged,
            onMessageVisibilityChanged = { messageVisible = it }
        ) {
            PreviewPrimaryButton("Show me around", onContinue)
            PreviewSecondaryButton("End setup guide", onCancel)
        }
    }
}

@Composable
private fun PreviewInProgressScreen(
    modifier: Modifier,
    stage: ProtectionPreviewStage,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val (description, emphasizedPhrases) = when (stage) {
        ProtectionPreviewStage.WAITING_SITE_BLOCK ->
            "Return to the browser and tap the SinShield bubble to copy the test website." to
                listOf("Return to the browser", "SinShield bubble", "copy the test website")
        ProtectionPreviewStage.SITE_EXPLANATION ->
            "The website demonstration is waiting in your browser." to
                listOf("waiting in your browser")
        ProtectionPreviewStage.WAITING_CARS_SEARCH ->
            "Return to Google and search for “cars”." to
                listOf("Return to Google", "search for “cars”")
        ProtectionPreviewStage.WAITING_IMAGES ->
            "Return to the cars results and select Images." to
                listOf("cars results", "Images")
        else -> "The protection demonstration is waiting in your browser." to
            listOf("waiting in your browser")
    }
    PreviewFullscreenCard(
        modifier,
        "Your setup guide is in the browser",
        description,
        emphasizedPhrases
    ) {
        PreviewPrimaryButton("Return to browser", onResume)
        PreviewSecondaryButton("End setup guide", onCancel)
    }
}

@Composable
private fun PreviewFullscreenCard(
    modifier: Modifier,
    title: String,
    description: String,
    emphasizedPhrases: List<String> = emptyList(),
    actions: @Composable ColumnScope.() -> Unit
) {
    var messageVisible by remember(title) { mutableStateOf(false) }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (messageVisible) 0.78f else 0f,
        animationSpec = tween(260),
        label = "fullscreen guide scrim"
    )
    Box(
        modifier
            .fillMaxSize()
            .background(ScreenBlue)
            .background(Ink.copy(alpha = scrimAlpha))
    ) {
        RobotGuideMessage(
            title = title,
            description = description,
            emphasizedPhrases = emphasizedPhrases,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp),
            onMessageVisibilityChanged = { messageVisible = it },
            content = actions
        )
    }
}

@Composable
private fun RobotGuideMessage(
    title: String,
    description: String,
    emphasizedPhrases: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    animateEntrance: Boolean = true,
    showMessageImmediately: Boolean = false,
    messageRevealDelayMs: Long = ROBOT_MESSAGE_DELAY_MS,
    onRobotPositionChanged: (Offset) -> Unit = {},
    onMessageVisibilityChanged: (Boolean) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {}
) {
    var dragOffset by remember(title) { mutableStateOf(Offset.Zero) }
    var bubblePosition by remember(title) { mutableStateOf(Offset.Zero) }
    var bubbleSize by remember(title) { mutableStateOf(IntSize.Zero) }
    var bubbleContainerSize by remember(title) { mutableStateOf(IntSize.Zero) }
    var messageVisible by remember(title) { mutableStateOf(showMessageImmediately) }
    LaunchedEffect(messageVisible) {
        onMessageVisibilityChanged(messageVisible)
    }
    val entrance = remember(title) { Animatable(if (animateEntrance) 420f else 0f) }
    LaunchedEffect(title) {
        // Position reporting changes animateEntrance as soon as the robot is laid out. That must
        // not restart this effect: doing so cancels animateTo and strands the message off-screen
        // at its partially completed horizontal offset.
        if (entrance.value != 0f) {
            entrance.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        }
        if (!showMessageImmediately) {
            delay(messageRevealDelayMs)
            messageVisible = true
        }
    }
    val dragHandle = Modifier.pointerInput(title) {
        detectDragGestures { change, amount ->
            change.consume()
            val maximumX =
                (bubbleContainerSize.width - bubblePosition.x - bubbleSize.width).coerceAtLeast(0f)
            val maximumY =
                (bubbleContainerSize.height - bubblePosition.y - bubbleSize.height).coerceAtLeast(0f)
            dragOffset += Offset(
                x = amount.x.coerceIn(-bubblePosition.x, maximumX),
                y = amount.y.coerceIn(-bubblePosition.y, maximumY)
            )
        }
    }

    BoxWithConstraints(modifier) {
        val messageWidth = (maxWidth - 76.dp).coerceAtMost(280.dp).coerceAtLeast(200.dp)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = (dragOffset.x + entrance.value).roundToInt(),
                        y = dragOffset.y.roundToInt()
                    )
                }
                .onGloballyPositioned {
                    bubblePosition = it.positionInRoot()
                    bubbleSize = it.size
                    bubbleContainerSize = it.findRootCoordinates().size
                },
            verticalAlignment = Alignment.Top
        ) {
            AnimatedVisibility(
                visible = messageVisible,
                enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(tween(260)),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(tween(180))
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Card(
                        modifier = Modifier.width(messageWidth),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = BorderStroke(2.dp, BrightBlue),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(18.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                title,
                                color = Ink,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                highlightedText(
                                    description,
                                    emphasizedPhrases,
                                    emphasisColor = BrightBlue,
                                    emphasisWeight = FontWeight.SemiBold,
                                    useHeavyFont = false
                                ),
                                color = MutedInk,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 9.dp, bottom = 8.dp)
                            )
                            content()
                        }
                    }
                    Box(
                        Modifier
                            .padding(top = 24.dp)
                            .offset(x = (-4).dp)
                            .size(10.dp)
                            .rotate(45f)
                            .background(CardWhite)
                    )
                }
            }
            RobotGuideAvatar(
                size = 58.dp,
                modifier = dragHandle
                    .offset(x = (-2).dp)
                    .onGloballyPositioned {
                        onRobotPositionChanged(it.positionInRoot())
                    }
                    .clickable(role = Role.Button) { messageVisible = !messageVisible }
                    .semantics {
                        contentDescription = if (messageVisible) {
                            "Hide SinShield setup guide message"
                        } else {
                            "Show SinShield setup guide message"
                        }
                    }
            )
        }
    }
}

@Composable
private fun MovingRobotGuideAvatar(
    movementKey: Any?,
    start: Offset,
    target: Offset?,
    onArrived: (Offset) -> Unit
) {
    val position = remember(movementKey) {
        Animatable(start, Offset.VectorConverter)
    }
    LaunchedEffect(movementKey, target) {
        val destination = target ?: return@LaunchedEffect
        position.animateTo(
            destination,
            animationSpec = tween(420, easing = FastOutSlowInEasing)
        )
        onArrived(destination)
    }
    RobotGuideAvatar(
        size = 58.dp,
        modifier = Modifier.offset {
            IntOffset(position.value.x.roundToInt(), position.value.y.roundToInt())
        }
    )
}

@Composable
private fun DraggableRobotGuideAvatar(
    appearanceKey: Any?,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember(appearanceKey) { mutableStateOf(Offset.Zero) }
    var avatarPosition by remember(appearanceKey) { mutableStateOf(Offset.Zero) }
    var avatarSize by remember(appearanceKey) { mutableStateOf(IntSize.Zero) }
    var containerSize by remember(appearanceKey) { mutableStateOf(IntSize.Zero) }
    val entrance = remember(appearanceKey) { Animatable(120f) }
    LaunchedEffect(appearanceKey) {
        entrance.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
    }
    RobotGuideAvatar(
        size = 58.dp,
        modifier = modifier
            .offset {
                IntOffset(
                    x = (dragOffset.x + entrance.value).roundToInt(),
                    y = dragOffset.y.roundToInt()
                )
            }
            .onGloballyPositioned {
                avatarPosition = it.positionInParent()
                avatarSize = it.size
                containerSize = it.parentLayoutCoordinates?.size ?: IntSize.Zero
            }
            .pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    val maximumX =
                        (containerSize.width - avatarPosition.x - avatarSize.width).coerceAtLeast(0f)
                    val maximumY =
                        (containerSize.height - avatarPosition.y - avatarSize.height).coerceAtLeast(0f)
                    dragOffset += Offset(
                        x = amount.x.coerceIn(-avatarPosition.x, maximumX),
                        y = amount.y.coerceIn(-avatarPosition.y, maximumY)
                    )
                }
            }
    )
}

@Composable
private fun RobotGuideAvatar(
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "SinShield setup guide" },
        shape = RoundedCornerShape(20.dp),
        color = CardWhite,
        border = BorderStroke(2.dp, BrightBlue),
        shadowElevation = 8.dp
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_setup_guide_robot),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.padding(size * 0.15f)
        )
    }
}

@Composable
private fun PreviewPrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = BrightBlue),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp)
    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PreviewSecondaryButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, BrightBlue),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp)
    ) { Text(label, color = BrightBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    Spacer(Modifier.height(8.dp))
}

private fun advancePreviewPermissionStage(
    context: Context,
    current: ProtectionPreviewStage,
    accessibility: Boolean,
    overlay: Boolean,
    vpn: Boolean
): ProtectionPreviewStage {
    var stage = current
    if (stage == ProtectionPreviewStage.ACCESSIBILITY && accessibility) {
        stage = ProtectionPreviewStage.OVERLAY
    }
    if (stage == ProtectionPreviewStage.OVERLAY && overlay) {
        stage = ProtectionPreviewStage.VPN
    }
    // BATTERY was part of the required flow in older builds. Skip that persisted legacy stage.
    if (stage == ProtectionPreviewStage.BATTERY) stage = ProtectionPreviewStage.VPN
    if (stage == ProtectionPreviewStage.VPN && vpn) {
        stage = ProtectionPreviewStage.READY
    }
    if (stage != current) ProtectionPreviewRepository.moveTo(context, stage)
    return stage
}

private val settingsTourStages = setOf(
    ProtectionPreviewStage.TOUR_STREAK,
    ProtectionPreviewStage.TOUR_PROTECTION_MODE,
    ProtectionPreviewStage.TOUR_ADVANCED_TUNING,
    ProtectionPreviewStage.TOUR_DOMAIN_BLOCK_LIST,
    ProtectionPreviewStage.TOUR_OPTIONAL_PERMISSIONS
)

private fun nextSettingsTourStage(
    current: ProtectionPreviewStage
): ProtectionPreviewStage? = when (current) {
    ProtectionPreviewStage.TOUR_STREAK ->
        ProtectionPreviewStage.TOUR_PROTECTION_MODE
    ProtectionPreviewStage.TOUR_PROTECTION_MODE ->
        ProtectionPreviewStage.TOUR_ADVANCED_TUNING
    ProtectionPreviewStage.TOUR_ADVANCED_TUNING ->
        ProtectionPreviewStage.TOUR_DOMAIN_BLOCK_LIST
    ProtectionPreviewStage.TOUR_DOMAIN_BLOCK_LIST ->
        ProtectionPreviewStage.TOUR_OPTIONAL_PERMISSIONS
    ProtectionPreviewStage.TOUR_OPTIONAL_PERMISSIONS -> null
    else -> null
}

private fun openPreviewBrowser(context: Context) {
    runCatching {
        val intent = checkNotNull(newPreviewBrowserTabIntent(context)) {
            "No default browser is configured"
        }
        ProtectionPreviewRepository.setBrowserPackage(
            context,
            intent.component?.packageName
        )
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "No browser is available", Toast.LENGTH_LONG).show()
    }
}

private const val PREVIEW_OFFER_DELAY_MS = 5_000L
private const val ROBOT_MESSAGE_DELAY_MS = 1_500L
private const val UI_TOUR_MESSAGE_DELAY_MS = 1_000L
private const val FINAL_UI_TOUR_MESSAGE_DELAY_MS = 500L

private fun openPrivacyPolicy(context: Context) {
    openLegalPage(context, context.getString(R.string.privacy_policy_url))
}

private fun openTermsAndConditions(context: Context) {
    openLegalPage(context, context.getString(R.string.terms_and_conditions_url))
}

private fun openLegalPage(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun LegalLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MutedInk,
        fontSize = 14.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    )
}

@Composable
private fun ReliabilityCenterCard(
    deviceBackgroundPolicy: DeviceBackgroundPolicy?,
    deviceBackgroundReady: Boolean,
    battery: Boolean,
    manufacturerBatteryConfirmed: Boolean,
    vpnRunning: Boolean,
    vpnExpected: Boolean,
    alwaysOnVpn: Boolean,
    notifications: Boolean,
    previewStage: ProtectionPreviewStage?,
    onPreviewTargetBounds: (ProtectionPreviewStage, Rect) -> Unit,
    onBattery: () -> Unit,
    onDeviceBackground: () -> Unit,
    onAlwaysOnVpn: () -> Unit,
    onNotifications: () -> Unit,
    onRepair: () -> Unit,
    onDetails: (SettingDetails) -> Unit
) {
    SettingsCard(
        "Optional & reliability",
        R.drawable.ic_restart,
        headerModifier = previewRowModifier(
            previewStage,
            ProtectionPreviewStage.TOUR_OPTIONAL_PERMISSIONS,
            onPreviewTargetBounds
        )
    ) {
        Text(
            "Optional controls improve background reliability and status alerts.",
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
                    "Optional, but recommended. Allow unrestricted battery use to improve " +
                        "screen and website protection reliability in the background."
                )
            )
        }
        AppDivider()
        deviceBackgroundPolicy?.let { policy ->
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
            AppDivider()
        }
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
            AppBackButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
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
                content.disclosureRes?.let { disclosureRes ->
                    Surface(
                        color = BrightBlue.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Text(
                            stringResource(disclosureRes),
                            color = MutedInk,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
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

private fun highlightedStep(step: SetupStep) = highlightedText(step.text, step.boldPhrases)

private fun highlightedText(
    text: String,
    boldPhrases: List<String>,
    emphasisColor: Color = Ink,
    emphasisWeight: FontWeight = FontWeight.Black,
    useHeavyFont: Boolean = true
) = buildAnnotatedString {
    var cursor = 0
    while (cursor < text.length) {
        val match = boldPhrases
            .mapNotNull { phrase ->
                text.indexOf(phrase, cursor).takeIf { it >= 0 }?.let { it to phrase }
            }
            .minByOrNull { it.first }
        if (match == null) {
            append(text.substring(cursor))
            break
        }
        append(text.substring(cursor, match.first))
        withStyle(
            SpanStyle(
                fontWeight = emphasisWeight,
                fontFamily = if (useHeavyFont) SwitzerHeavyFontFamily else null,
                color = emphasisColor
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
        disclosureRes = R.string.accessibility_disclosure_body,
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
private fun StreakCard(
    snapshot: StreakSnapshot,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(snapshot.startedAtMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val today = LocalDate.now()
    val weekStart = today.minusDays((today.dayOfWeek.value % 7).toLong())
    val week = (0L..6L).map(weekStart::plusDays)
    val days = snapshot.completedDays(nowMillis)
    val title = if (snapshot.isStarted) {
        "$days ${if (days == 1L) "day" else "days"} under protection"
    } else {
        "Start your streak"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        // The preview highlight tracks this inner, padded column rather than the card's
        // own edge-to-edge bounds, so its focus ring gets the same left/right margin as
        // every other highlighted row instead of being clipped flush to the screen edge.
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp).then(modifier)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_streak),
                    contentDescription = null,
                    tint = BrightBlue,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    color = Ink,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Canvas(Modifier.size(24.dp)) {
                    val stroke = 2.dp.toPx()
                    drawLine(Ink, Offset(size.width * .38f, size.height * .25f), Offset(size.width * .68f, size.height * .5f), stroke)
                    drawLine(Ink, Offset(size.width * .68f, size.height * .5f), Offset(size.width * .38f, size.height * .75f), stroke)
                }
            }
            AppDivider()
            Text(
                if (snapshot.isStarted) "Keep every layer of protection active."
                else "Build your protection one day at a time.",
                color = MutedInk,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 15.dp, bottom = 17.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { date ->
                    StreakDay(
                        date = date,
                        today = today,
                        protected = date in snapshot.protectedDates
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakDay(date: LocalDate, today: LocalDate, protected: Boolean) {
    val isToday = date == today
    val isFuture = date.isAfter(today)
    val background = when {
        isToday -> BrightBlue
        isFuture -> Color.Transparent
        protected -> Color(0xFFD5E4F5)
        else -> Color(0xFFE7EAEE)
    }
    val border = when {
        isToday -> null
        isFuture -> BorderStroke(1.dp, Ink.copy(alpha = .70f))
        else -> null
    }
    val textColor = when {
        isToday -> Color.White
        protected -> Ink
        else -> MutedInk
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(38.dp)) {
        Surface(
            color = background,
            shape = RoundedCornerShape(50),
            border = border,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(date.dayOfMonth.toString(), color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
            color = if (isToday) BrightBlue else MutedInk,
            fontSize = 12.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}

@Composable
private fun StreakDetailsScreen(
    modifier: Modifier = Modifier,
    startedAtMillis: Long?,
    onBack: () -> Unit
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val elapsedSeconds = ((startedAtMillis?.let { nowMillis - it } ?: 0L).coerceAtLeast(0L)) / 1_000L
    val days = elapsedSeconds / 86_400L
    val hours = (elapsedSeconds / 3_600L) % 24L
    val minutes = (elapsedSeconds / 60L) % 60L
    val seconds = elapsedSeconds % 60L

    Column(
        modifier.fillMaxSize().background(ScreenBlue).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth()) {
            AppBackButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
            SinSheldWordmark(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(18.dp))
        SettingsCard("Your streak", R.drawable.ic_streak) {
            Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp)) {
                TimerValue(days, "Days", Modifier.weight(1f))
                TimerValue(hours, "Hours", Modifier.weight(1f))
                TimerValue(minutes, "Mins", Modifier.weight(1f))
                TimerValue(seconds, "Secs", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(18.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_key),
                        contentDescription = null,
                        tint = BrightBlue,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Keys to success",
                        color = Ink,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                AppDivider()
                Spacer(Modifier.height(18.dp))
                SuccessKey(
                    "Keep today achievable",
                    "Focus on today. Small, repeatable choices build the streak."
                )
                SuccessKey(
                    "Stay mindful",
                    "Treat today like any other day. There is no need to overthink it."
                )
                SuccessKey(
                    "Let progress take time",
                    "The first days may feel harder. Consistency makes them easier."
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AppBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .semantics {
                contentDescription = "Back"
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 2.dp.toPx()
            drawLine(
                Ink,
                Offset(size.width * .72f, size.height * .18f),
                Offset(size.width * .28f, size.height * .5f),
                stroke
            )
            drawLine(
                Ink,
                Offset(size.width * .28f, size.height * .5f),
                Offset(size.width * .72f, size.height * .82f),
                stroke
            )
        }
    }
}

@Composable
private fun TimerValue(value: Long, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString().padStart(2, '0'),
            color = BrightBlue,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(label, color = MutedInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SuccessKey(title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text("•", color = BrightBlue, fontSize = 20.sp, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                color = MutedInk,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE5EFFF, widthDp = 412)
@Composable
private fun StreakCardPreview() {
    SinSheldTheme {
        Box(Modifier.background(ScreenBlue).padding(20.dp)) {
            StreakCard(
                snapshot = StreakSnapshot(
                    startedAtMillis = System.currentTimeMillis() - 3L * StreakSnapshot.DAY_MILLIS,
                    protectedDates = setOf(LocalDate.now().minusDays(1), LocalDate.now())
                ),
                onOpen = {}
            )
        }
    }
}

@Composable
private fun RequiredPermissionsCard(
    accessibility: Boolean,
    overlay: Boolean,
    vpnRunning: Boolean,
    alwaysOnVpn: Boolean,
    previewStage: ProtectionPreviewStage?,
    onPreviewTargetBounds: (ProtectionPreviewStage, Rect) -> Unit,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onWebsiteProtection: (Boolean) -> Unit,
    onDetails: (SettingDetails) -> Unit
) {
    SettingsCard("Required", R.drawable.ic_verified_user) {
        PermissionRow(
            "Accessibility permission",
            "Allows on-device screen protection to work",
            R.drawable.ic_accessibility,
            accessibility,
            previewRowModifier(
                previewStage,
                ProtectionPreviewStage.ACCESSIBILITY,
                onPreviewTargetBounds
            ),
            onAccessibility
        ) {
            onDetails(SettingDetails("Accessibility permission", "Required. Android only lets you enable or disable an Accessibility service yourself in system settings."))
        }
        AppDivider()
        PermissionRow(
            "Overlay permission",
            "Allows SinSheld to show a blocking screen",
            R.drawable.ic_layers,
            overlay,
            previewRowModifier(
                previewStage,
                ProtectionPreviewStage.OVERLAY,
                onPreviewTargetBounds
            ),
            onOverlay
        ) {
            onDetails(SettingDetails("Overlay permission", "Required. Without Display over other apps access, SinSheld can detect unsafe content but cannot cover it."))
        }
        AppDivider()
        PermissionRow(
            "Website protection",
            when {
                vpnRunning && alwaysOnVpn -> "Active · Always-on recovery managed by Android"
                vpnRunning -> "Active · adult-content domains blocked locally"
                else -> "Use a local DNS-only VPN to block adult-content websites"
            },
            R.drawable.ic_vpn_lock,
            vpnRunning,
            previewRowModifier(
                previewStage,
                ProtectionPreviewStage.VPN,
                onPreviewTargetBounds
            ),
            { onWebsiteProtection(!vpnRunning) }
        ) {
            onDetails(
                SettingDetails(
                    "Website protection",
                    "Required. SinShield uses a local split-tunnel VPN for DNS filtering and does not decrypt web traffic. Android allows only one VPN at a time."
                )
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    summary: String,
    iconRes: Int,
    enabled: Boolean,
    rowModifier: Modifier,
    onToggle: () -> Unit,
    onDetails: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PermissionRowCornerRadius))
            .padding(vertical = 15.dp)
            .then(rowModifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

private fun previewRowModifier(
    currentStage: ProtectionPreviewStage?,
    rowStage: ProtectionPreviewStage,
    onBounds: (ProtectionPreviewStage, Rect) -> Unit
): Modifier = if (currentStage == rowStage) {
    Modifier.onGloballyPositioned { coordinates ->
        // boundsInRoot() clips children outside a scroll viewport to an empty rectangle. The
        // un-clipped position is required so an off-screen permission row can be moved to the top.
        val position = coordinates.positionInRoot()
        onBounds(
            rowStage,
            Rect(
                left = position.x,
                top = position.y,
                right = position.x + coordinates.size.width,
                bottom = position.y + coordinates.size.height
            )
        )
    }
} else {
    Modifier
}

@Composable
private fun SettingsCard(
    title: String,
    iconRes: Int,
    headerModifier: Modifier = Modifier,
    containerModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(horizontal = 22.dp, vertical = 20.dp)
                .then(containerModifier)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).then(headerModifier)
            ) {
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
        AppSwitch(checked, onChecked = onChecked)
    }
}

@Composable
private fun AppSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChecked: (Boolean) -> Unit
) = Switch(
    checked = checked, onCheckedChange = onChecked,
    modifier = modifier,
    colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White, checkedTrackColor = ActiveGreen,
        uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFB8B9BC),
        uncheckedBorderColor = Color.Transparent
    )
)

@Composable
private fun InstantExpandable(
    visible: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val collapsedSemantics = if (visible) Modifier else Modifier.clearAndSetSemantics { }
    Column(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .then(collapsedSemantics)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                layout(placeable.width, if (visible) placeable.height else 0) {
                    if (visible) placeable.placeRelative(0, 0)
                }
            },
        content = content
    )
}

@Composable
private fun ProtectionModeControl(
    selected: ProtectionLevel,
    custom: Boolean,
    onSelected: (ProtectionLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    val presetLevels = listOf(
        ProtectionLevel.RELAXED,
        ProtectionLevel.BALANCED,
        ProtectionLevel.STRICT
    )
    val segments: List<ProtectionLevel?> = if (custom) presetLevels + null else presetLevels
    val selectedIndex = if (custom) {
        segments.lastIndex
    } else {
        segments.indexOf(selected).coerceAtLeast(0)
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFFDCE8F8), RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / segments.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(durationMillis = 220),
            label = "protection mode indicator"
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .background(CardWhite, RoundedCornerShape(12.dp))
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            segments.forEachIndexed { index, level ->
                val isSelected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            onClick = { level?.let(onSelected) },
                            role = Role.RadioButton
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        level?.displayName ?: "Custom",
                        color = if (isSelected) BrightBlue else MutedInk,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

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

private fun openDeviceBackgroundSettings(
    context: Context,
    policy: DeviceBackgroundPolicy,
    launch: (Intent) -> Unit
) {
    if (!DeviceBackgroundPolicy.launchSettings(context, policy, launch)) {
        Toast.makeText(context, "Device settings are unavailable", Toast.LENGTH_LONG).show()
    }
}

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
