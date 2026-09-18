package com.example.sinshield

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min

/**
 * Event-driven screen protection for apps whose media cannot be inspected through the
 * accessibility node tree. Normal content is never covered: accessibility events merely request
 * a coalesced screenshot, and only a current, final unsafe verdict creates the blocking window.
 */
class ShieldAccessibilityService : AccessibilityService() {

    private lateinit var overlays: OverlayManager
    private val handler = Handler(Looper.getMainLooper())
    private val healthHeartbeat = object : Runnable {
        override fun run() {
            ProtectionHealthMonitor.recordAccessibilityHeartbeat(this@ShieldAccessibilityService)
            handler.postDelayed(this, HEALTH_HEARTBEAT_INTERVAL_MS)
        }
    }

    private var foregroundPackage: String? = null
    private var foregroundWindowId: Int = UNKNOWN_WINDOW_ID
    // Scheduling uses elapsed realtime (which includes deep sleep), while Android timestamps both
    // accessibility events and screenshots in uptime milliseconds. Keep the clock domains separate
    // so a busy feed cannot make every completed screenshot look stale.
    private var lastRelevantEventElapsedAt = 0L
    private var lastRelevantEventUptimeAt = 0L
    private var contentEventsEnabled = false
    private var inputMethodPackage: String? = null
    private var eventMetricsStartedAt = SystemClock.elapsedRealtime()
    private var receivedEventCount = 0
    private var ignoredEventCount = 0
    private var maxEventTimeMs = 0L

    private lateinit var recovery: RecoveryController
    private lateinit var scanner: FrameScanner

    // The full-screen block's buttons drive recovery, which stays in this service; OverlayManager
    // only inflates the window and forwards clicks here.
    private val appBlockActions = object : OverlayManager.AppBlockActions {
        override fun onCooldownStarted() = scanner.cancelScheduledScan()

        override fun onReturnToFeed(app: ShieldedApp) = recovery.returnToAppFeed(app)
        override fun onPrimaryRecovery(app: ShieldedApp) = recovery.performPrimaryRecoveryAction(app)
        override fun onCloseApp(app: ShieldedApp) = recovery.closeApp(app)
        override fun onRecoveryVerificationBlocked(app: ShieldedApp) =
            recovery.onRecoveryVerificationBlocked(app)
        override fun onDismiss(incident: IncidentId) {
            scanner.dismissIncident(incident)
            recovery.clearAppBlock()
        }

        override fun onFeedback(
            incident: IncidentId,
            disturbing: Boolean,
            onComplete: (Boolean) -> Unit
        ) {
            scanner.recordFeedback(incident, disturbing, onComplete)
            if (!disturbing) recovery.clearAppBlock()
        }
    }

    private val siteBlockActions = object : OverlayManager.SiteBlockActions {
        override fun onOpenSafePage() = recovery.leaveSiteAndOpenSafePage()
        override fun onGoBack() = recovery.goBackFromSite()
    }

    // Browser packages remain available for DNS-site overlays, but do not enter screenshot
    // monitoring while app protection is intentionally limited to Instagram and X.
    private val monitoredPackages = socialPackages /* + browserPackages */

    private val blockedDomainReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val domain = intent
                ?.getStringExtra(AdultContentVpnService.EXTRA_DOMAIN)
                ?.takeIf(String::isNotBlank)
                ?: return
            showBlockedSiteOverlay(domain)
        }
    }

    override fun onCreate() {
        super.onCreate()
        inputMethodPackage = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )?.substringBefore('/')
        overlays = OverlayManager(
            context = this,
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager,
            collectMediaRegions = ::collectVisibleMediaRegions
        )
        scanner = FrameScanner(
            service = this,
            handler = handler,
            overlays = overlays,
            appBlockActions = appBlockActions,
            collectAccessibilityContext = ::collectScanAccessibilityContext,
            host = object : FrameScanner.Host {
                override fun syncForegroundFromRoot(): Boolean =
                    this@ShieldAccessibilityService.syncForegroundFromRoot()
                override val foregroundPackage: String?
                    get() = this@ShieldAccessibilityService.foregroundPackage
                override val foregroundWindowId: Int
                    get() = this@ShieldAccessibilityService.foregroundWindowId
                override val lastRelevantEventUptimeAt: Long
                    get() = this@ShieldAccessibilityService.lastRelevantEventUptimeAt
                override val lastRelevantEventElapsedAt: Long
                    get() = this@ShieldAccessibilityService.lastRelevantEventElapsedAt
                override fun isMonitored(packageName: String?): Boolean =
                    packageName in monitoredPackages
                override val closingAppInProgress: Boolean
                    get() = recovery.closingAppInProgress
                override fun clearLocalizedOverlays(packageName: String, windowId: Int) =
                    this@ShieldAccessibilityService.clearLocalizedOverlays(packageName, windowId)
            }
        )
        recovery = RecoveryController(
            service = this,
            handler = handler,
            overlays = overlays,
            rootForPackage = ::rootForPackage,
            collectScreenSignals = ::collectScreenSignals,
            host = object : RecoveryController.Host {
                override fun syncForegroundFromRoot(): Boolean =
                    this@ShieldAccessibilityService.syncForegroundFromRoot()
                override val foregroundPackage: String?
                    get() = this@ShieldAccessibilityService.foregroundPackage
                override val foregroundWindowId: Int
                    get() = this@ShieldAccessibilityService.foregroundWindowId
                override fun resetAdaptiveState() = scanner.resetAdaptiveState()
                override fun requestScan(delayMs: Long) = scanner.requestScan(delayMs)
                override fun requestPostRecoveryScan(packageName: String, delayMs: Long) =
                    scanner.requestPostRecoveryScan(packageName, delayMs)
                override fun invalidateInFlightScanResults() = scanner.invalidateInFlightResults()
                override val scanInFlight: Boolean get() = scanner.inFlight
                override fun markFramePending() = scanner.markFramePending()
            }
        )
        ContextCompat.registerReceiver(
            this,
            blockedDomainReceiver,
            IntentFilter(AdultContentVpnService.ACTION_ADULT_DOMAIN_BLOCKED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.i(TAG, "Service created; detection models will load on demand")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        broadcastConnectionState()
        updateEventSubscription(includeContentEvents = false)
        startAsForeground()
        handler.removeCallbacks(healthHeartbeat)
        healthHeartbeat.run()
        // Do not query rootInActiveWindow while Android is binding the service. On HyperOS a root
        // request can wait several seconds for an unresponsive foreground app, making this service
        // look hung too. The next accessibility event supplies the foreground package and starts
        // scanning without a cross-process round trip.
        Log.i(
            TAG,
            "Accessibility scanner connected; monitoring ${monitoredPackages.size} packages; " +
                "strict=${ProtectionPreferences.strictMode(this)}"
        )
    }

    private fun startAsForeground() {
        val channelId = "sinshield_active"
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(channelId, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("${getString(R.string.app_name)} active")
            .setContentText("Monitoring for explicit content")
            .setSmallIcon(R.drawable.sinshield_notification)
            .setColor(getColor(R.color.sinshield_primary))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType !in relevantEventTypes) return

        val startedAt = SystemClock.elapsedRealtime()
        receivedEventCount++
        try {
            handleAccessibilityEvent(event)
        } finally {
            maxEventTimeMs = max(maxEventTimeMs, SystemClock.elapsedRealtime() - startedAt)
            maybeLogEventMetrics()
        }
    }

    private fun handleAccessibilityEvent(event: AccessibilityEvent) {

        val eventPackage = event.packageName?.toString()
        val contentEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

        // Keyboard, System UI, and unrelated apps can generate content-change events many times per
        // second. They cannot affect a protected feed, so do not make a Binder root query for them.
        if (contentEvent && eventPackage !in monitoredPackages) {
            ignoredEventCount++
            return
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val previousPackage = foregroundPackage
        val previousWindow = foregroundWindowId

        // Never call rootInActiveWindow from the accessibility-event callback. The call crosses
        // into the foreground app and can block for Android's full interaction timeout when that
        // app is slow or ANR-ing. HyperOS then attributes the blocked callback to this service and
        // offers to force-stop it, which also clears the user's accessibility grant.
        //
        // TYPE_WINDOW_STATE_CHANGED normally identifies an app transition, but accessibility
        // overlays, System UI, and the keyboard create transient windows without replacing the app
        // underneath. Treating one of those windows as foreground immediately clears the shield we
        // just displayed. A real SinShield activity window remains a valid transition.
        val ownActivityWindow = eventPackage == packageName &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.className?.toString() == MainActivity::class.java.name
        val transientWindow = !ownActivityWindow && (
            eventPackage == packageName ||
                eventPackage == SYSTEM_UI_PACKAGE ||
                eventPackage == ANDROID_PACKAGE ||
                eventPackage == inputMethodPackage
            )
        val eventIdentifiesForeground = eventPackage != null && !transientWindow && (
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventPackage in monitoredPackages ||
                foregroundPackage == null
            )
        if (eventIdentifiesForeground) {
            foregroundPackage = eventPackage
            if (event.windowId != UNKNOWN_WINDOW_ID) foregroundWindowId = event.windowId
        }

        lastRelevantEventElapsedAt = max(lastRelevantEventElapsedAt, nowElapsed)
        lastRelevantEventUptimeAt = max(lastRelevantEventUptimeAt, max(nowUptime, event.eventTime))
        val contextChanged = previousPackage != foregroundPackage ||
            (previousWindow != UNKNOWN_WINDOW_ID &&
                foregroundWindowId != UNKNOWN_WINDOW_ID &&
                previousWindow != foregroundWindowId)

        if (contextChanged) {
            // Return/Close deliberately recreates the app's task. Keep the opaque shield above
            // that transition; otherwise the old route can flash while the app resets to Home.
            if (recovery.navigationActionInProgress && overlays.appOverlay != null) {
                overlays.clearLocalizedOverlaysOnly()
            } else {
                clearLocalizedOverlays()
            }
            scanner.resetAdaptiveState()
        }

        overlays.siteOverlay?.let { overlay ->
            if (foregroundPackage != overlay.packageName) {
                overlays.clearSiteBlockingOverlay()
            } else {
                cancelScheduledScan()
                scanner.clearConfirmation()
                return
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            overlays.moveLocalizedOverlaysForScroll(event)
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            overlays.hasLocalizedOverlays
        ) {
            eventPackage?.let(overlays::reconcileLocalizedOverlaysWithMediaNodes)
        }

        if (foregroundPackage !in monitoredPackages) {
            updateEventSubscription(includeContentEvents = false)
            if (contextChanged) {
                Log.i(
                    TAG,
                    "Monitoring paused; foreground=$foregroundPackage window=$foregroundWindowId"
                )
                scanner.scheduleModelRelease()
            }
            cancelScheduledScan()
            scanner.clearConfirmation()
            return
        }
        if (contextChanged) {
            Log.i(TAG, "Monitoring foreground=$foregroundPackage window=$foregroundWindowId")
        }
        updateEventSubscription(includeContentEvents = true)
        scanner.cancelScheduledModelRelease()
        scanner.onMonitoredEvent()
    }

    private fun updateEventSubscription(includeContentEvents: Boolean) {
        if (contentEventsEnabled == includeContentEvents && serviceInfo.notificationTimeout ==
            EVENT_NOTIFICATION_TIMEOUT_MS
        ) {
            return
        }
        contentEventsEnabled = includeContentEvents
        serviceInfo = serviceInfo.apply {
            eventTypes = if (includeContentEvents) ACTIVE_EVENT_TYPES else IDLE_EVENT_TYPES
            notificationTimeout = EVENT_NOTIFICATION_TIMEOUT_MS
            flags = flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        Log.i(TAG, "Accessibility event mode=${if (includeContentEvents) "active" else "idle"}")
    }

    private fun maybeLogEventMetrics() {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - eventMetricsStartedAt
        if (elapsed < EVENT_METRICS_INTERVAL_MS) return
        Log.i(
            TAG,
            "Accessibility event metrics: received=$receivedEventCount ignored=$ignoredEventCount " +
                "maxHandlerMs=$maxEventTimeMs intervalMs=$elapsed mode=" +
                if (contentEventsEnabled) "active" else "idle"
        )
        eventMetricsStartedAt = now
        receivedEventCount = 0
        ignoredEventCount = 0
        maxEventTimeMs = 0L
    }

    private fun cancelScheduledScan() = scanner.cancelScheduledScan()

    private fun syncForegroundFromRoot(): Boolean {
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: return false
        foregroundPackage = pkg
        foregroundWindowId = root.windowId
        return true
    }

    private fun collectVisibleMediaRegions(): List<DetectionRegion> {
        val root = rootInActiveWindow ?: return emptyList()
        val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val screenArea = screenWidth.toLong() * screenHeight
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val candidates = mutableListOf<DetectionRegion>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_ACCESSIBILITY_NODES) {
            val node = queue.removeFirst()
            visited++
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
            if (!node.isVisibleToUser) continue

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.intersect(0, 0, screenWidth, screenHeight)) continue
            val area = bounds.width().toLong() * bounds.height()
            if (area < screenArea * MIN_MEDIA_AREA_PERCENT / 100L ||
                area > screenArea * MAX_MEDIA_AREA_PERCENT / 100L ||
                bounds.width() < screenWidth * MIN_MEDIA_WIDTH_PERCENT / 100 ||
                bounds.height() < screenHeight * MIN_MEDIA_HEIGHT_PERCENT / 100
            ) {
                continue
            }

            val className = node.className?.toString().orEmpty().lowercase()
            val description = node.contentDescription?.toString().orEmpty().lowercase()
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            val likelyMedia = className.contains("imageview") ||
                className.contains("textureview") ||
                className.contains("surfaceview") ||
                MEDIA_HINTS.any { it in description || it in viewId } ||
                (node.childCount == 0 && description.isNotBlank())
            if (!likelyMedia) continue

            val region = DetectionRegion(
                bounds.left.toFloat() / screenWidth,
                bounds.top.toFloat() / screenHeight,
                bounds.right.toFloat() / screenWidth,
                bounds.bottom.toFloat() / screenHeight,
                source = DetectionRegionSource.ACCESSIBILITY
            )
            if (candidates.none { normalizedIntersectionOverUnion(it, region) >= DUPLICATE_REGION_IOU }) {
                candidates += region
            }
        }

        val resultLimit = if (root.packageName?.toString() == ShieldedApp.INSTAGRAM.packageName) {
            MAX_INSTAGRAM_MEDIA_REGIONS
        } else {
            MAX_MEDIA_REGIONS
        }
        return candidates
            .sortedByDescending(DetectionRegion::area)
            .take(resultLimit)
    }

    /**
     * Builds the per-scan node snapshot in one traversal. FrameScanner invokes this off-main because
     * every node getter can cross into the foreground app through Binder.
     */
    private fun collectScanAccessibilityContext(app: ShieldedApp?): ScanAccessibilityContext {
        val root = rootInActiveWindow ?: return ScanAccessibilityContext.EMPTY
        val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val screenArea = screenWidth.toLong() * screenHeight
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val candidates = mutableListOf<DetectionRegion>()
        val labels = mutableListOf<String>()
        val viewIds = mutableListOf<String>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited++ < MAX_ACCESSIBILITY_NODES) {
            val node = queue.removeFirst()
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
            if (!node.isVisibleToUser) continue

            if (app != null) {
                node.text?.toString()?.trim()?.lowercase()?.takeIf(String::isNotBlank)?.let(labels::add)
                node.contentDescription?.toString()?.trim()?.lowercase()
                    ?.takeIf(String::isNotBlank)?.let(labels::add)
                node.viewIdResourceName?.lowercase()?.let(viewIds::add)
            }

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.intersect(0, 0, screenWidth, screenHeight)) continue
            val area = bounds.width().toLong() * bounds.height()
            if (area < screenArea * MIN_MEDIA_AREA_PERCENT / 100L ||
                area > screenArea * MAX_MEDIA_AREA_PERCENT / 100L ||
                bounds.width() < screenWidth * MIN_MEDIA_WIDTH_PERCENT / 100 ||
                bounds.height() < screenHeight * MIN_MEDIA_HEIGHT_PERCENT / 100
            ) {
                continue
            }
            val className = node.className?.toString().orEmpty().lowercase()
            val description = node.contentDescription?.toString().orEmpty().lowercase()
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            val likelyMedia = className.contains("imageview") ||
                className.contains("textureview") ||
                className.contains("surfaceview") ||
                MEDIA_HINTS.any { it in description || it in viewId } ||
                (node.childCount == 0 && description.isNotBlank())
            if (!likelyMedia) continue

            val region = DetectionRegion(
                bounds.left.toFloat() / screenWidth,
                bounds.top.toFloat() / screenHeight,
                bounds.right.toFloat() / screenWidth,
                bounds.bottom.toFloat() / screenHeight,
                source = DetectionRegionSource.ACCESSIBILITY
            )
            if (candidates.none { normalizedIntersectionOverUnion(it, region) >= DUPLICATE_REGION_IOU }) {
                candidates += region
            }
        }

        val resultLimit = if (root.packageName?.toString() == ShieldedApp.INSTAGRAM.packageName) {
            MAX_INSTAGRAM_MEDIA_REGIONS
        } else {
            MAX_MEDIA_REGIONS
        }
        return ScanAccessibilityContext(
            mediaRegions = candidates.sortedByDescending(DetectionRegion::area).take(resultLimit),
            screenSignals = if (app == null) ScreenSignals.EMPTY else ScreenSignals(labels, viewIds)
        )
    }

    /** Shows a browser-specific shield only when a blocked DNS request came from the foreground. */
    private fun showBlockedSiteOverlay(domain: String) {
        val browserPackage = foregroundPackage?.takeIf { it in browserPackages } ?: run {
            Log.d(TAG, "Blocked DNS request had no foreground browser; overlay suppressed")
            return
        }
        val existing = overlays.siteOverlay
        if (existing?.packageName == browserPackage) {
            existing.domain = domain
            return
        }
        overlays.clearSiteBlockingOverlay()
        clearLocalizedOverlays()
        overlays.showSiteBlock(browserPackage, domain, siteBlockActions)
        cancelScheduledScan()
    }

    private fun collectScreenSignals(app: ShieldedApp): ScreenSignals {
        val root = rootForPackage(app.packageName) ?: return ScreenSignals.EMPTY
        val labels = mutableListOf<String>()
        val viewIds = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_ACCESSIBILITY_NODES) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser) {
                node.text?.toString()?.let { labels += it.trim().lowercase() }
                node.contentDescription?.toString()?.let { labels += it.trim().lowercase() }
                node.viewIdResourceName?.let { viewIds += it.lowercase() }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return ScreenSignals(labels, viewIds)
    }

    private fun rootForPackage(packageName: String): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { root ->
            if (root.packageName?.toString() == packageName) return root
        }
        return windows
            .asSequence()
            .sortedByDescending { it.layer }
            .mapNotNull { it.root }
            .firstOrNull { it.packageName?.toString() == packageName }
    }

    /** Clears both the localized covers and the full-screen block, resetting recovery state. */
    private fun clearLocalizedOverlays() {
        overlays.clearLocalizedOverlaysOnly()
        recovery.clearAppBlock()
    }

    /**
     * A now-safe frame retires this package's localized covers, and — when policy agrees the frame
     * belongs to the blocked app — the full-screen block too. The app-overlay decision (and its
     * navigation-flag reset) stays here; OverlayManager only removes the matching localized covers.
     */
    private fun clearLocalizedOverlays(packageName: String, windowId: Int) {
        overlays.appOverlay?.let { overlay ->
            // Full-screen shields protect the currently visible app frame. Facebook frequently
            // swaps accessibility window ids while returning to Feed, so a fresh SAFE result for
            // the same package must retire the old shield even if its incident used another id.
            if (NavigationRecoveryPolicy.shouldClearAppBlockOnSafeFrame(
                    blockedPackage = overlay.incident.packageName,
                    safeFramePackage = packageName
                )
            ) {
                recovery.clearAppBlock()
            }
        }
        overlays.clearLocalizedOverlaysForPackage(packageName, windowId)
    }

    private fun windowChanged(first: Int, second: Int): Boolean =
        first != UNKNOWN_WINDOW_ID && second != UNKNOWN_WINDOW_ID && first != second

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val actualPressure = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (actualPressure && ::scanner.isInitialized) {
            scanner.releaseModelsForMemoryPressure()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        broadcastConnectionState()
        cancelScheduledScan()
        scanner.cancelScheduledModelRelease()
        clearLocalizedOverlays()
        overlays.clearSiteBlockingOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        broadcastConnectionState()
        handler.removeCallbacks(healthHeartbeat)
        cancelScheduledScan()
        scanner.cancelScheduledModelRelease()
        clearLocalizedOverlays()
        overlays.clearSiteBlockingOverlay()
        runCatching { unregisterReceiver(blockedDomainReceiver) }
        scanner.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STATE_CHANGED = "com.example.sinshield.action.ACCESSIBILITY_STATE_CHANGED"
        const val EXTRA_CONNECTED = "connected"
        @Volatile var isConnected: Boolean = false
            private set
        private const val TAG = "SinSheld"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val ANDROID_PACKAGE = "android"
        private const val UNKNOWN_WINDOW_ID = -1
        private const val MAX_ACCESSIBILITY_NODES = 400
        private const val MAX_MEDIA_REGIONS = 4
        private const val MAX_INSTAGRAM_MEDIA_REGIONS = 18
        private const val HEALTH_HEARTBEAT_INTERVAL_MS = 5L * 60L * 1_000L
        private const val EVENT_NOTIFICATION_TIMEOUT_MS = 100L
        private const val EVENT_METRICS_INTERVAL_MS = 30_000L
        private const val MIN_MEDIA_AREA_PERCENT = 4
        private const val MAX_MEDIA_AREA_PERCENT = 90
        private const val MIN_MEDIA_WIDTH_PERCENT = 25
        private const val MIN_MEDIA_HEIGHT_PERCENT = 10
        private const val DUPLICATE_REGION_IOU = 0.85f

        private val MEDIA_HINTS = setOf(
            "image",
            "photo",
            "media",
            "video",
            "gif",
            "thumbnail",
            "reel",
            "story",
            "cover"
        )

        private val relevantEventTypes = setOf(
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
        )

        private const val IDLE_EVENT_TYPES =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
        private const val ACTIVE_EVENT_TYPES =
            IDLE_EVENT_TYPES or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

        // Apps with a ShieldedApp profile get the full-screen block; the rest are covered
        // post-by-post, so both kinds have to be monitored.
        private val socialPackages = setOf(
            ShieldedApp.INSTAGRAM.packageName,
            ShieldedApp.X.packageName,
            // Keep these entry points available for future support, but do not monitor them while
            // screen protection is intentionally limited to Instagram and X.
            // ShieldedApp.FACEBOOK.packageName,
            // ShieldedApp.FACEBOOK_LITE.packageName,
            // ShieldedApp.REDDIT.packageName,
            // "org.telegram.messenger",
        )

        private val browserPackages = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.sec.android.app.sbrowser",
            "com.opera.browser",
            "com.opera.mini.native"
        )
    }

    private fun broadcastConnectionState() {
        sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_CONNECTED, isConnected)
        )
    }
}

internal data class IncidentId(
    val packageName: String,
    val windowId: Int,
    val frameHash: Long
)

internal data class LocalizedOverlay(
    val view: View,
    val bounds: Rect,
    var incident: IncidentId
)

internal data class AppBlockingOverlay(
    val app: ShieldedApp,
    val view: View,
    var incident: IncidentId,
    var mode: ShieldedScreenMode
)

internal data class SiteBlockingOverlay(
    val packageName: String,
    var domain: String,
    val view: View
)

internal data class DetectionBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val detectorClass: String,
    val score: Float
)

/** The detector classes that can support a verdict. */
internal fun relevantDetectorClasses(verdict: ContentVerdict): Set<String> =
    if (verdict == ContentVerdict.EXPLICIT) setOf("Porn", "Hentai") else setOf("Sexy")

private fun detectionBoxIntersectionOverUnion(first: DetectionBox, second: DetectionBox): Float {
    val intersectionWidth = (min(first.right, second.right) - max(first.left, second.left))
        .coerceAtLeast(0f)
    val intersectionHeight = (min(first.bottom, second.bottom) - max(first.top, second.top))
        .coerceAtLeast(0f)
    val intersection = intersectionWidth * intersectionHeight
    val firstArea = (first.right - first.left) * (first.bottom - first.top)
    val secondArea = (second.right - second.left) * (second.bottom - second.top)
    val union = firstArea + secondArea - intersection
    return if (union > 0f) intersection / union else 0f
}

internal object LocalizedBoxSelector {
    private const val OVERLAY_DEDUP_IOU = 0.35f

    fun select(
        detected: List<DetectionBox>,
        fallback: List<DetectionBox>,
        maximum: Int
    ): List<DetectionBox> {
        val selected = mutableListOf<DetectionBox>()
        for (box in detected + fallback) {
            if (selected.none {
                    detectionBoxIntersectionOverUnion(it, box) >= OVERLAY_DEDUP_IOU
                }
            ) {
                selected += box
            }
            if (selected.size >= maximum) break
        }
        return selected
    }
}

internal fun rectIntersectionOverUnion(first: Rect, second: Rect): Float {
    val intersectionWidth = (min(first.right, second.right) - max(first.left, second.left))
        .coerceAtLeast(0)
    val intersectionHeight = (min(first.bottom, second.bottom) - max(first.top, second.top))
        .coerceAtLeast(0)
    val intersection = intersectionWidth.toLong() * intersectionHeight
    val union = first.width().toLong() * first.height() +
        second.width().toLong() * second.height() - intersection
    return if (union > 0L) intersection.toFloat() / union else 0f
}
