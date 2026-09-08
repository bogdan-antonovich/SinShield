package com.example.sinshield

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import java.util.ArrayDeque

/**
 * Owns everything that happens once a full-screen block is up: the Return-to-feed / Scroll-past /
 * Close-app recovery actions, the Home-feed navigation and Recents-dismissal machinery, the
 * shielded gesture dispatch, and the browser site-block exits. The two navigation flags
 * [navigationActionInProgress] and [closingAppInProgress] are recovery state and live here; the
 * scanner reads them through the read-only accessors so a Return/Close transition keeps the shield.
 *
 * Like [OverlayManager] this cannot be Android-free — recovery drives the AccessibilityService's
 * global actions, gesture dispatch, and node tree. [service] supplies those, [overlays] is the
 * window surface recovery manipulates, and the couplings back to the scanner (foreground identity,
 * scan scheduling, adaptive-state reset) are injected through [host] and the two node-read lambdas.
 * The dependency therefore only ever points from recovery into the service, never the reverse.
 * Every member is touched only from the main thread, exactly as before.
 */
internal class RecoveryController(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val overlays: OverlayManager,
    private val rootForPackage: (String) -> AccessibilityNodeInfo?,
    private val collectScreenSignals: (ShieldedApp) -> ScreenSignals,
    private val host: Host
) {
    /**
     * The scanner-owned state and operations recovery has to coordinate with. Foreground identity
     * is shared with the scan loop — which updates it with an event fallback — so recovery reads it
     * here rather than tracking its own copy that could drift out of sync.
     */
    interface Host {
        fun syncForegroundFromRoot(): Boolean
        val foregroundPackage: String?
        val foregroundWindowId: Int
        fun resetAdaptiveState()
        fun requestScan(delayMs: Long)
        fun requestPostRecoveryScan(packageName: String, delayMs: Long)
        fun invalidateInFlightScanResults()
        val scanInFlight: Boolean
        fun markFramePending()
    }

    /** A recovery action is deliberately recreating the app's task; keep the shield above it. */
    var navigationActionInProgress = false
        private set

    /** The running recovery action ends in closing the app; hold the shield through task removal. */
    var closingAppInProgress = false
        private set

    /** Resets the navigation flags this class owns, then has OverlayManager remove the window. */
    fun clearAppBlock() {
        navigationActionInProgress = false
        closingAppInProgress = false
        overlays.removeAppBlockingOverlay()
    }

    fun returnToAppFeed(app: ShieldedApp) {
        navigationActionInProgress = true
        closingAppInProgress = false
        overlays.appOverlay?.view?.let { view ->
            setActionButtonsEnabled(view, false)
            view.findViewById<TextView>(R.id.blocked_explanation)
                .setText(R.string.returning_to_feed)
        }
        navigateToVisibleHomeFeed(app) { reachedFeed ->
            Log.i(TAG, "Return to feed completed; app=${app.packageName} homeAction=$reachedFeed")
            if (reachedFeed) {
                // A scan captured before navigation may still finish after the shield is removed.
                // Put a generation barrier in front of it so old unsafe pixels cannot recreate
                // the overlay on the newly opened feed.
                host.invalidateInFlightScanResults()
                clearAppBlock()
                host.resetAdaptiveState()
                if (host.foregroundPackage == app.packageName) {
                    host.requestScan(POST_ACTION_SCAN_DELAY_MS)
                }
            } else {
                showPrimaryActionFailure(R.string.unable_to_return_to_feed)
            }
        }
    }

    fun performPrimaryRecoveryAction(app: ShieldedApp) {
        when (overlays.appOverlay?.mode) {
            ShieldedScreenMode.STORY -> skipCurrentStory()
            ShieldedScreenMode.DIRECT_MESSAGE -> leaveCurrentSurface(
                R.string.returning_to_chat,
                "Back-to-chat"
            )
            ShieldedScreenMode.LIVE -> leaveCurrentSurface(
                R.string.leaving_live,
                "Leave-live"
            )
            else -> scrollPastContent(app)
        }
    }

    private fun scrollPastContent(app: ShieldedApp) {
        val overlay = overlays.appOverlay ?: return
        setActionButtonsEnabled(overlay.view, false)
        overlay.view.findViewById<TextView>(R.id.blocked_explanation)
            .setText(R.string.scrolling_past_content)

        // Instagram profiles contain a horizontal posts/reels/tagged pager whose accessibility
        // actions can masquerade as vertical scrolling. Acting on it can switch tabs or land on
        // the account navigation instead of moving the profile. A centered swipe reproduces the
        // user's vertical gesture without starting near Instagram's bottom-right Profile tab.
        if (app === ShieldedApp.INSTAGRAM &&
            overlay.mode == ShieldedScreenMode.PROFILE_OR_POST
        ) {
            dispatchShieldedScrollGesture(
                xPercent = INSTAGRAM_PROFILE_SCROLL_X_PERCENT,
                startYPercent = INSTAGRAM_PROFILE_SCROLL_START_Y_PERCENT,
                endYPercent = INSTAGRAM_PROFILE_SCROLL_END_Y_PERCENT
            )
            return
        }

        val verticalTarget = findVerticalScrollTarget(app)
        if (verticalTarget != null &&
            verticalTarget.node.performAction(verticalTarget.actionId)
        ) {
            Log.i(
                TAG,
                "Scroll-past used vertical node action=${verticalTarget.actionName} " +
                    "class=${verticalTarget.className} bounds=${verticalTarget.bounds}"
            )
            scheduleActionVerification()
            return
        }

        // Generic ACTION_SCROLL_FORWARD is deliberately not used: on an X profile it advances
        // the horizontal Posts/Replies/Reposts/Videos pager. The gesture fallback waits until
        // WindowManager has actually applied FLAG_NOT_TOUCHABLE to the still-visible shield.
        dispatchShieldedScrollGesture()
    }

    /** Advances exactly one Story/Highlight while the opaque shield remains visible. */
    private fun skipCurrentStory() {
        val overlay = overlays.appOverlay ?: return
        navigationActionInProgress = true
        closingAppInProgress = false
        setActionButtonsEnabled(overlay.view, false)
        overlay.view.findViewById<TextView>(R.id.blocked_explanation)
            .setText(R.string.skipping_story)
        overlays.setAppOverlayTouchable(false)
        handler.postDelayed(
            {
                if (overlays.appOverlay == null) return@postDelayed
                val width = service.resources.displayMetrics.widthPixels.toFloat()
                val height = service.resources.displayMetrics.heightPixels.toFloat()
                val path = Path().apply {
                    moveTo(width * STORY_SKIP_X_PERCENT, height * STORY_SKIP_Y_PERCENT)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(path, 0L, STORY_SKIP_TAP_DURATION_MS)
                    )
                    .build()
                val accepted = service.dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            overlays.setAppOverlayTouchable(true)
                            Log.i(
                                TAG,
                                "Skipped current ${overlays.appOverlay?.app?.displayName ?: "social"} " +
                                    "story through protected tap"
                            )
                            scheduleActionVerification()
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            overlays.setAppOverlayTouchable(true)
                            showPrimaryActionFailure(R.string.unable_to_skip_story)
                        }
                    },
                    handler
                )
                if (!accepted) {
                    overlays.setAppOverlayTouchable(true)
                    showPrimaryActionFailure(R.string.unable_to_skip_story)
                }
            },
            OVERLAY_PASSTHROUGH_SETTLE_MS
        )
    }

    private fun leaveCurrentSurface(message: Int, actionName: String) {
        val overlay = overlays.appOverlay ?: return
        navigationActionInProgress = true
        closingAppInProgress = false
        setActionButtonsEnabled(overlay.view, false)
        overlay.view.findViewById<TextView>(R.id.blocked_explanation).setText(message)
        if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            Log.i(TAG, "$actionName performed with Back")
            scheduleActionVerification(BACK_SETTLE_DELAY_MS)
        } else {
            showPrimaryActionFailure(R.string.unable_to_leave_surface)
        }
    }

    private fun dispatchShieldedScrollGesture(
        xPercent: Float = GESTURE_X_PERCENT,
        startYPercent: Float = GESTURE_START_Y_PERCENT,
        endYPercent: Float = GESTURE_END_Y_PERCENT
    ) {
        overlays.appOverlay ?: return
        overlays.setAppOverlayTouchable(false)
        handler.postDelayed(
            {
                if (overlays.appOverlay == null) return@postDelayed
                val width = service.resources.displayMetrics.widthPixels.toFloat()
                val height = service.resources.displayMetrics.heightPixels.toFloat()
                val path = Path().apply {
                    moveTo(width * xPercent, height * startYPercent)
                    lineTo(width * xPercent, height * endYPercent)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(path, 0L, SCROLL_GESTURE_DURATION_MS)
                    )
                    .build()
                val accepted = service.dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            overlays.setAppOverlayTouchable(true)
                            Log.i(TAG, "Scroll-past performed through settled shielded gesture")
                            scheduleActionVerification()
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            overlays.setAppOverlayTouchable(true)
                            showScrollFailure()
                        }
                    },
                    handler
                )
                if (!accepted) {
                    overlays.setAppOverlayTouchable(true)
                    showScrollFailure()
                }
            },
            OVERLAY_PASSTHROUGH_SETTLE_MS
        )
    }

    private fun findVerticalScrollTarget(app: ShieldedApp): VerticalScrollTarget? {
        val root = rootForPackage(app.packageName) ?: return null
        val screenWidth = service.resources.displayMetrics.widthPixels
        val screenHeight = service.resources.displayMetrics.heightPixels
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val candidates = mutableListOf<VerticalScrollTarget>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_ACCESSIBILITY_NODES) {
            val node = queue.removeFirst()
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
            if (!node.isVisibleToUser || !node.isScrollable) continue

            val actionIds = node.actionList.mapTo(mutableSetOf()) { it.id }
            val scrollDown = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
            val pageDown = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN.id
            } else {
                Int.MIN_VALUE
            }
            val scrollLeft = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
            val scrollRight = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
            val className = node.className?.toString().orEmpty()
            val normalizedClass = className.lowercase()
            val horizontalPager = normalizedClass.contains("viewpager") ||
                normalizedClass.contains("horizontalscroll") ||
                scrollLeft in actionIds || scrollRight in actionIds
            val verticalContainer = normalizedClass.contains("recyclerview") ||
                normalizedClass.contains("listview") ||
                (normalizedClass.contains("scrollview") && !horizontalPager)
            // Directional actions are tried before the class-name heuristic on purpose. Instagram's
            // Reels viewer is a *vertical* ViewPager2, which the horizontalPager name check flags
            // by mistake; because it advertises SCROLL_DOWN it is still matched here first.
            val action = when {
                scrollDown in actionIds -> scrollDown to "SCROLL_DOWN"
                pageDown in actionIds -> pageDown to "PAGE_DOWN"
                verticalContainer && !horizontalPager &&
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD in actionIds ->
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD to "SCROLL_FORWARD_VERTICAL"
                else -> null
            } ?: continue

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.intersect(0, 0, screenWidth, screenHeight)) continue
            val area = bounds.width().toLong() * bounds.height()
            val priority = when (action.second) {
                "SCROLL_DOWN" -> 3
                "PAGE_DOWN" -> 2
                else -> 1
            }
            candidates += VerticalScrollTarget(
                node = node,
                actionId = action.first,
                actionName = action.second,
                className = className,
                bounds = Rect(bounds),
                priority = priority,
                visibleArea = area
            )
        }
        return candidates.maxWithOrNull(
            compareBy<VerticalScrollTarget> { it.priority }.thenBy { it.visibleArea }
        )
    }

    fun closeApp(app: ShieldedApp) {
        navigationActionInProgress = true
        closingAppInProgress = true
        overlays.appOverlay?.view?.let { view ->
            setActionButtonsEnabled(view, false)
            view.findViewById<TextView>(R.id.blocked_explanation).text =
                service.getString(R.string.closing_and_resetting_app, app.displayName)
        }
        navigateToVisibleHomeFeed(app) { reachedFeed ->
            Log.i(TAG, "${app.displayName} reset before close; homeAction=$reachedFeed")
            handler.postDelayed({
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                handler.postDelayed(
                    {
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                        handler.postDelayed(
                            { dismissTaskFromRecents(app) },
                            RECENTS_OPEN_DELAY_MS
                        )
                    },
                    HOME_SETTLE_DELAY_MS
                )
            }, POST_HOME_CLICK_DELAY_MS)
        }
    }

    private fun navigateToVisibleHomeFeed(app: ShieldedApp, onComplete: (Boolean) -> Unit) {
        navigateToVisibleHomeFeed(
            app,
            backsRemaining = MAX_BACKS_TO_FIND_HOME,
            taskRestartAvailable = app.restartAtLauncherFallback,
            onComplete = onComplete
        )
    }

    private fun navigateToVisibleHomeFeed(
        app: ShieldedApp,
        backsRemaining: Int,
        taskRestartAvailable: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        host.syncForegroundFromRoot()
        val startingWindowId = host.foregroundWindowId
        if (clickVisibleHomeTab(app)) {
            handler.postDelayed(
                {
                    host.syncForegroundFromRoot()
                    val resultingMode = app.screenMode(collectScreenSignals(app))
                    val reachedFeed = NavigationRecoveryPolicy.reachedHomeFeed(
                        foregroundPackage = host.foregroundPackage,
                        appPackage = app.packageName,
                        resultingMode = resultingMode,
                        taskRestarted = false,
                        homeTabClicked = true,
                        homeTabClickIsConclusive = app.homeTabClickIsConclusive,
                        startingWindowId = startingWindowId,
                        resultingWindowId = host.foregroundWindowId
                    )
                    Log.i(
                        TAG,
                        "Home navigation settled; app=${app.packageName} " +
                            "window=$startingWindowId->${host.foregroundWindowId} " +
                            "mode=$resultingMode reached=$reachedFeed"
                    )
                    if (reachedFeed) {
                        onComplete(true)
                    } else if (taskRestartAvailable || backsRemaining <= 0) {
                        finishHomeNavigationOrRestart(
                            app,
                            taskRestartAvailable,
                            onComplete
                        )
                    } else {
                        navigateBackTowardHome(
                            app,
                            backsRemaining,
                            taskRestartAvailable,
                            onComplete
                        )
                    }
                },
                POST_HOME_CLICK_DELAY_MS
            )
            return
        }
        if (taskRestartAvailable) {
            finishHomeNavigationOrRestart(app, taskRestartAvailable, onComplete)
            return
        }
        navigateBackTowardHome(app, backsRemaining, taskRestartAvailable, onComplete)
    }

    private fun navigateBackTowardHome(
        app: ShieldedApp,
        backsRemaining: Int,
        taskRestartAvailable: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        if (backsRemaining <= 0 ||
            !service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        ) {
            finishHomeNavigationOrRestart(app, taskRestartAvailable, onComplete)
            return
        }
        handler.postDelayed(
            {
                navigateToVisibleHomeFeed(
                    app,
                    backsRemaining - 1,
                    taskRestartAvailable,
                    onComplete
                )
            },
            BACK_SETTLE_DELAY_MS
        )
    }

    private fun finishHomeNavigationOrRestart(
        app: ShieldedApp,
        taskRestartAvailable: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        if (!taskRestartAvailable) {
            onComplete(false)
            return
        }
        restartAppAtLauncher(app, onComplete)
    }

    /**
     * Some app routes do not expose a reliable Home target in their accessibility tree.
     * Restarting only that app's task is the deterministic fallback: it clears the internal route
     * stack and opens the launcher destination while the opaque shield remains visible.
     */
    private fun restartAppAtLauncher(app: ShieldedApp, onComplete: (Boolean) -> Unit) {
        val component = service.packageManager.getLaunchIntentForPackage(app.packageName)?.component
        if (component == null) {
            Log.w(TAG, "No launcher component found for ${app.packageName}")
            onComplete(false)
            return
        }
        try {
            service.startActivity(Intent.makeRestartActivityTask(component))
            Log.i(TAG, "Restarted ${app.displayName} task at its launcher as Home fallback")
        } catch (error: Throwable) {
            Log.e(TAG, "Could not restart ${app.displayName} at its launcher", error)
            onComplete(false)
            return
        }
        handler.postDelayed(
            {
                host.syncForegroundFromRoot()
                val resultingMode = app.screenMode(collectScreenSignals(app))
                val reachedFeed = NavigationRecoveryPolicy.reachedHomeFeed(
                    foregroundPackage = host.foregroundPackage,
                    appPackage = app.packageName,
                    resultingMode = resultingMode,
                    taskRestarted = true
                )
                Log.i(
                    TAG,
                    "${app.displayName} launcher fallback settled; " +
                        "foreground=${host.foregroundPackage} mode=$resultingMode reached=$reachedFeed"
                )
                onComplete(reachedFeed)
            },
            TASK_RESTART_SETTLE_MS
        )
    }

    private fun clickVisibleHomeTab(app: ShieldedApp): Boolean {
        val root = rootForPackage(app.packageName) ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_ACCESSIBILITY_NODES) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser &&
                app.isHomeTab(nodeLabels(node), node.viewIdResourceName.orEmpty().lowercase()) &&
                performClickOnNodeOrParent(node)
            ) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                Log.i(
                    TAG,
                    "Clicked Home target; app=${app.packageName} labels=${nodeLabels(node)} " +
                        "viewId=${node.viewIdResourceName.orEmpty()} bounds=$bounds"
                )
                return true
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return false
    }

    private fun nodeLabels(node: AccessibilityNodeInfo): List<String> =
        listOf(node.text, node.contentDescription)
            .mapNotNull { it?.toString()?.trim()?.lowercase() }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(MAX_ACTION_PARENT_DEPTH) {
            val candidate = current ?: return false
            val exposesClickAction = candidate.actionList.any {
                it.id == AccessibilityNodeInfo.ACTION_CLICK
            }
            if ((candidate.isClickable || exposesClickAction) &&
                candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return true
            }
            current = candidate.parent
        }
        return false
    }

    private fun dismissTaskFromRecents(app: ShieldedApp) {
        val root = service.rootInActiveWindow
        val labelNode = findNode(root) { app.isRecentsCard(nodeLabels(it)) }
        if (labelNode == null) {
            Log.w(
                TAG,
                "${app.displayName} task was not exposed in Recents; " +
                    "finishing close after feed reset"
            )
            finishClosingApp()
            return
        }

        val dismissable = findActionableAncestor(
            labelNode,
            AccessibilityNodeInfo.ACTION_DISMISS
        )
        if (dismissable?.performAction(AccessibilityNodeInfo.ACTION_DISMISS) == true) {
            Log.i(TAG, "Dismissed ${app.displayName} task from Recents through ACTION_DISMISS")
            handler.postDelayed(::finishClosingApp, RECENTS_DISMISS_SETTLE_DELAY_MS)
            return
        }

        val cardBounds = findRecentCardBounds(labelNode)
        if (cardBounds == null) {
            Log.w(TAG, "${app.displayName} Recents card had no dismiss action or usable bounds")
            finishClosingApp()
            return
        }
        swipeRecentCardAway(cardBounds)
    }

    private fun findNode(
        root: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        root ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_ACCESSIBILITY_NODES) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser && predicate(node)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return null
    }

    private fun findActionableAncestor(
        node: AccessibilityNodeInfo,
        action: Int
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(MAX_ACTION_PARENT_DEPTH) {
            val candidate = current ?: return null
            if (candidate.actionList.any { it.id == action }) return candidate
            current = candidate.parent
        }
        return null
    }

    private fun findRecentCardBounds(node: AccessibilityNodeInfo): Rect? {
        val screenWidth = service.resources.displayMetrics.widthPixels
        val screenHeight = service.resources.displayMetrics.heightPixels
        val screenArea = screenWidth.toLong() * screenHeight
        var current: AccessibilityNodeInfo? = node
        var best: Rect? = null
        repeat(MAX_ACTION_PARENT_DEPTH) {
            val candidate = current ?: return@repeat
            val bounds = Rect()
            candidate.getBoundsInScreen(bounds)
            val area = bounds.width().toLong() * bounds.height()
            if (bounds.width() >= screenWidth / 3 &&
                bounds.height() >= screenHeight / 4 &&
                area < screenArea * MAX_RECENT_CARD_AREA_PERCENT / 100L
            ) {
                if (best == null || area > best!!.width().toLong() * best!!.height()) {
                    best = bounds
                }
            }
            current = candidate.parent
        }
        return best
    }

    private fun swipeRecentCardAway(bounds: Rect) {
        overlays.setAppOverlayTouchable(false)
        val path = Path().apply {
            moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            lineTo(bounds.centerX().toFloat(), -bounds.height().toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(path, 0L, RECENTS_SWIPE_DURATION_MS)
            )
            .build()
        val accepted = service.dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "Swiped task away from Recents")
                    handler.postDelayed(::finishClosingApp, RECENTS_DISMISS_SETTLE_DELAY_MS)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Recents swipe was cancelled")
                    finishClosingApp()
                }
            },
            handler
        )
        if (!accepted) finishClosingApp()
    }

    private fun finishClosingApp() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        handler.postDelayed(::clearAppBlock, CLOSE_OVERLAY_GRACE_MS)
        Log.i(TAG, "Close sequence finished")
    }

    private fun scheduleActionVerification(
        delayMs: Long = POST_ACTION_SCAN_DELAY_MS
    ) {
        host.resetAdaptiveState()
        if (host.scanInFlight) host.markFramePending()
        handler.postDelayed(
            {
                val overlay = overlays.appOverlay ?: return@postDelayed
                setActionButtonsEnabled(overlay.view, true)
                overlay.view.findViewById<TextView>(R.id.blocked_explanation).apply {
                    text = ""
                    visibility = View.GONE
                }
                host.syncForegroundFromRoot()
                if (host.foregroundPackage == overlay.app.packageName) {
                    host.requestPostRecoveryScan(overlay.app.packageName, 0L)
                }
            },
            delayMs
        )
    }

    private fun showScrollFailure() {
        showPrimaryActionFailure(R.string.unable_to_scroll)
        val overlay = overlays.appOverlay ?: return
        Log.w(TAG, "No scrollable feed was available in ${overlay.app.packageName}")
    }

    private fun showPrimaryActionFailure(message: Int) {
        val overlay = overlays.appOverlay ?: return
        navigationActionInProgress = false
        closingAppInProgress = false
        setActionButtonsEnabled(overlay.view, true)
        overlay.view.findViewById<TextView>(R.id.blocked_explanation).text =
            service.getString(message, overlay.app.displayName)
    }

    private fun setActionButtonsEnabled(view: View, enabled: Boolean) {
        view.findViewById<Button>(R.id.return_to_feed).isEnabled = enabled
        view.findViewById<Button>(R.id.scroll_past_content).isEnabled = enabled
        view.findViewById<Button>(R.id.close_app).isEnabled = enabled
    }

    fun leaveSiteAndOpenSafePage() {
        val browserPackage = overlays.siteOverlay?.packageName ?: return
        overlays.clearSiteBlockingOverlay()
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        handler.postDelayed(
            {
                val explicitIntent = Intent(Intent.ACTION_VIEW, Uri.parse(SAFE_PAGE_URL))
                    .setPackage(browserPackage)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { service.startActivity(explicitIntent) }
                    .onFailure {
                        runCatching {
                            service.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(SAFE_PAGE_URL))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
            },
            SAFE_PAGE_OPEN_DELAY_MS
        )
    }

    /** The site block's Back button: retire the shield, then send a single system Back. */
    fun goBackFromSite() {
        overlays.clearSiteBlockingOverlay()
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    companion object {
        private const val TAG = "SinShield"
        private const val MAX_ACCESSIBILITY_NODES = 400
        private const val MAX_ACTION_PARENT_DEPTH = 8
        private const val MAX_BACKS_TO_FIND_HOME = 4
        private const val MAX_RECENT_CARD_AREA_PERCENT = 90
        private const val SCROLL_GESTURE_DURATION_MS = 420L
        private const val OVERLAY_PASSTHROUGH_SETTLE_MS = 100L
        private const val GESTURE_X_PERCENT = 0.88f
        private const val GESTURE_START_Y_PERCENT = 0.78f
        private const val GESTURE_END_Y_PERCENT = 0.18f
        private const val INSTAGRAM_PROFILE_SCROLL_X_PERCENT = 0.50f
        private const val INSTAGRAM_PROFILE_SCROLL_START_Y_PERCENT = 0.72f
        private const val INSTAGRAM_PROFILE_SCROLL_END_Y_PERCENT = 0.28f
        private const val STORY_SKIP_X_PERCENT = 0.84f
        private const val STORY_SKIP_Y_PERCENT = 0.48f
        private const val STORY_SKIP_TAP_DURATION_MS = 60L
        private const val POST_ACTION_SCAN_DELAY_MS = 450L
        private const val POST_HOME_CLICK_DELAY_MS = 500L
        private const val TASK_RESTART_SETTLE_MS = 1_200L
        private const val BACK_SETTLE_DELAY_MS = 550L
        private const val HOME_SETTLE_DELAY_MS = 350L
        private const val RECENTS_OPEN_DELAY_MS = 650L
        private const val RECENTS_DISMISS_SETTLE_DELAY_MS = 350L
        private const val RECENTS_SWIPE_DURATION_MS = 380L
        private const val CLOSE_OVERLAY_GRACE_MS = 250L
        private const val SAFE_PAGE_OPEN_DELAY_MS = 250L
        private const val SAFE_PAGE_URL = "https://www.google.com/"
    }
}

private data class VerticalScrollTarget(
    val node: AccessibilityNodeInfo,
    val actionId: Int,
    val actionName: String,
    val className: String,
    val bounds: Rect,
    val priority: Int,
    val visibleArea: Long
)
