package app.sinshield

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/**
 * Owns every window SinSheld draws and the collections that track them: the post-by-post
 * localized covers, the full-screen app block, and the browser site block. All WindowManager
 * add/remove/update calls, the layout params, view inflation and teardown, and scroll tracking
 * live here — nothing else in the app talks to WindowManager.
 *
 * Unlike [ScanScheduler] this cannot be Android-free: overlays are Views on WindowManager. The
 * seam it draws instead is responsibility. Recovery, gestures and navigation stay in the service
 * and reach the app overlay only through the read-only [appOverlay] handle and a few focused
 * operations; the two things this class cannot do for itself are injected — [collectMediaRegions]
 * to re-find media during scroll reconciliation, and the button-action callbacks passed when a
 * block is shown. Every member is touched only from the main thread, exactly as before.
 */
internal class OverlayManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val collectMediaRegions: () -> List<DetectionRegion>
) {
    /** What the full-screen block's buttons do. Implemented by the service's recovery logic. */
    interface AppBlockActions {
        fun onCooldownStarted()
        fun onReturnToFeed(app: ShieldedApp)
        fun onPrimaryRecovery(app: ShieldedApp)
        fun onCloseApp(app: ShieldedApp)
        fun onRecoveryVerificationBlocked(app: ShieldedApp)
        fun onDismiss(incident: IncidentId)
        fun onFeedback(
            incident: IncidentId,
            disturbing: Boolean,
            onComplete: (Boolean) -> Unit
        )
    }

    /** What the browser site block's buttons do. Implemented by the service. */
    interface SiteBlockActions {
        fun onOpenSafePage()
        fun onGoBack()
    }

    private val localizedOverlays = mutableListOf<LocalizedOverlay>()
    private var appBlockingOverlay: AppBlockingOverlay? = null
    private var cooldownView: View? = null
    private var cooldownGeneration = 0L
    private var siteBlockingOverlay: SiteBlockingOverlay? = null
    private val activeIncidents = mutableSetOf<IncidentId>()

    /** Read-only handle so recovery can inspect the current full-screen block without owning it. */
    val appOverlay: AppBlockingOverlay? get() = appBlockingOverlay

    /** Read-only handle so the event loop can reconcile the site block against the foreground. */
    val siteOverlay: SiteBlockingOverlay? get() = siteBlockingOverlay

    val hasLocalizedOverlays: Boolean get() = localizedOverlays.isNotEmpty()

    /** While true, the scanner leaves the covered screen alone and lets the user pause. */
    val isRecoveryCooldownActive: Boolean get() = cooldownView != null

    /** Blocking windows require the user-granted Display over other apps permission. */
    fun canShowBlockingOverlays(): Boolean = Settings.canDrawOverlays(context)

    /**
     * Below Android 14 the display capture includes accessibility overlays, so they are hidden for
     * one render frame while the screenshot is taken. Returns the views hidden, to be restored by
     * [restoreLegacyCaptureOverlays] the instant capture finishes.
     */
    fun hideForLegacyCapture(): List<View> = buildList {
        addAll(localizedOverlays.map(LocalizedOverlay::view))
        appBlockingOverlay?.view?.let(::add)
    }.onEach { it.visibility = View.INVISIBLE }

    fun restoreLegacyCaptureOverlays(views: List<View>) {
        views.forEach { view ->
            if (localizedOverlays.any { it.view === view } || appBlockingOverlay?.view === view) {
                view.visibility = View.VISIBLE
            }
        }
    }

    fun showAppBlockingOverlay(
        app: ShieldedApp,
        incident: IncidentId,
        verdict: ContentVerdict,
        mode: ShieldedScreenMode,
        actions: AppBlockActions
    ) {
        if (!canShowBlockingOverlays()) {
            Log.w(TAG, "Full-screen block suppressed; overlay permission is not granted")
            return
        }
        val existing = appBlockingOverlay
        // An overlay already covering a different app cannot be reused: its click handlers are
        // bound to that app's navigation, and reusing it would scroll or close the wrong one.
        if (existing != null && existing.app.packageName == app.packageName) {
            val incidentChanged = existing.incident != incident
            existing.incident = incident
            existing.mode = mode
            configureBlockingView(app, existing.view, verdict, mode, resetFeedback = incidentChanged)
            restartRecoveryCooldown(existing.view, actions)
            return
        }
        if (existing != null) removeAppBlockingOverlay()

        clearLocalizedOverlaysOnly()
        val view = View.inflate(context, R.layout.layout_app_screen_block, null)
        configureBlockingView(app, view, verdict, mode, resetFeedback = true)
        view.findViewById<Button>(R.id.return_to_feed).setOnClickListener {
            showRecoveryStatus(view)
            actions.onReturnToFeed(app)
        }
        view.findViewById<Button>(R.id.scroll_past_content).setOnClickListener {
            showRecoveryStatus(view)
            actions.onPrimaryRecovery(app)
        }
        view.findViewById<Button>(R.id.close_app).setOnClickListener {
            showRecoveryStatus(view)
            actions.onCloseApp(app)
        }
        if (GlobalDebugMode.ENABLED) {
            view.findViewById<View>(R.id.dismiss_overlay).setOnClickListener {
                currentIncident(view, incident)?.let(actions::onDismiss)
            }
            view.findViewById<Button>(R.id.feedback_yes).setOnClickListener {
                currentIncident(view, incident)?.let { active ->
                    actions.onFeedback(active, true) { }
                    showFeedbackThanks(view)
                }
            }
            view.findViewById<Button>(R.id.feedback_no).setOnClickListener {
                currentIncident(view, incident)?.let { active ->
                    actions.onFeedback(active, false) { saved ->
                        val message = if (saved) {
                            R.string.false_positive_saved
                        } else {
                            R.string.false_positive_save_failed
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        if (!addOverlay(view, appBlockingLayoutParams(touchable = true))) return
        appBlockingOverlay = AppBlockingOverlay(app, view, incident, mode)
        restartRecoveryCooldown(view, actions)
        Log.i(
            TAG,
            "Full-screen block shown; app=${app.packageName} verdict=$verdict " +
                "mode=$mode"
        )
    }

    private fun configureBlockingView(
        app: ShieldedApp,
        view: View,
        verdict: ContentVerdict,
        mode: ShieldedScreenMode,
        resetFeedback: Boolean
    ) {
        view.findViewById<TextView>(R.id.blocked_reason)
            .setText(R.string.slow_down_message)
        view.findViewById<Button>(R.id.close_app).text =
            context.getString(R.string.close_app, app.displayName)
        view.findViewById<Button>(R.id.scroll_past_content).text = context.getString(
            when (mode) {
                ShieldedScreenMode.STORY -> R.string.skip_story
                ShieldedScreenMode.DIRECT_MESSAGE -> R.string.back_to_chat
                ShieldedScreenMode.LIVE -> R.string.leave_live
                else -> R.string.scroll_past
            }
        )
        configureDebugControls(view, resetFeedback)
    }

    /** Keep normal recovery choices out of sight so the block creates a deliberate pause. */
    private fun hideRecoveryActions(view: View) {
        view.findViewById<View>(R.id.recovery_actions).visibility = View.INVISIBLE
        updateCooldownText(view, RECOVERY_ACTION_REST_SECONDS)
    }

    private fun restartRecoveryCooldown(
        view: View,
        actions: AppBlockActions
    ) {
        hideRecoveryActions(view)
        cooldownView = view
        val generation = ++cooldownGeneration
        val cooldownEndsAt = SystemClock.elapsedRealtime() + RECOVERY_ACTION_REST_MS
        actions.onCooldownStarted()

        fun tick() {
            if (appBlockingOverlay?.view !== view || generation != cooldownGeneration) return
            val remainingMs = cooldownEndsAt - SystemClock.elapsedRealtime()
            if (remainingMs <= 0L) {
                cooldownView = null
                view.findViewById<TextView>(R.id.cooldown_timer).visibility = View.INVISIBLE
                view.findViewById<View>(R.id.recovery_actions).visibility = View.VISIBLE
                return
            }

            val remainingSeconds = ((remainingMs + 999L) / 1_000L).toInt()
            updateCooldownText(view, remainingSeconds)
            view.postDelayed(::tick, minOf(1_000L, remainingMs))
        }

        tick()
    }

    private fun updateCooldownText(view: View, remainingSeconds: Int) {
        view.findViewById<TextView>(R.id.cooldown_timer).apply {
            text = context.resources.getQuantityString(
                R.plurals.recovery_available_in_seconds,
                remainingSeconds,
                remainingSeconds
            )
            visibility = View.VISIBLE
        }
    }

    private fun showRecoveryStatus(view: View) {
        view.findViewById<TextView>(R.id.blocked_explanation).visibility = View.VISIBLE
    }

    private fun configureDebugControls(view: View, resetFeedback: Boolean) {
        val dismissEnabled = DebugSettings.overlayDismiss(context)
        val feedbackEnabled = DebugSettings.overlayFeedback(context)
        view.findViewById<View>(R.id.dismiss_overlay).visibility =
            if (dismissEnabled) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.feedback_prompt).visibility =
            if (feedbackEnabled) View.VISIBLE else View.GONE
        if (feedbackEnabled) {
            if (resetFeedback) resetFeedbackQuestion(view)
        } else {
            view.findViewById<View>(R.id.feedback_buttons).visibility = View.GONE
            view.findViewById<TextView>(R.id.feedback_response).visibility = View.GONE
        }
    }

    private fun currentIncident(view: View, fallback: IncidentId): IncidentId? {
        val overlay = appBlockingOverlay
        return if (overlay == null || overlay.view === view) overlay?.incident ?: fallback else null
    }

    private fun resetFeedbackQuestion(view: View) {
        view.findViewById<TextView>(R.id.feedback_prompt).setText(R.string.disturbing_question)
        view.findViewById<View>(R.id.feedback_buttons).visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.feedback_response).visibility = View.GONE
    }

    private fun showFeedbackThanks(view: View) {
        view.findViewById<View>(R.id.feedback_buttons).visibility = View.GONE
        view.findViewById<TextView>(R.id.feedback_response).apply {
            setText(R.string.feedback_thanks)
            visibility = View.VISIBLE
        }
    }

    /** The explanation text for a full-screen block; also used by recovery to restore controls. */
    fun explanationFor(app: ShieldedApp, mode: ShieldedScreenMode): String = context.getString(
        when (mode) {
            ShieldedScreenMode.STORY -> R.string.story_blocked_explanation
            ShieldedScreenMode.PROFILE_OR_POST -> R.string.profile_post_blocked_explanation
            ShieldedScreenMode.REELS -> R.string.reels_blocked_explanation
            ShieldedScreenMode.EXPLORE -> R.string.explore_blocked_explanation
            ShieldedScreenMode.DIRECT_MESSAGE -> R.string.direct_message_blocked_explanation
            ShieldedScreenMode.LIVE -> R.string.live_blocked_explanation
            else -> R.string.feed_blocked_explanation
        },
        app.displayName
    )

    /** Toggles FLAG_NOT_TOUCHABLE on the full-screen block so a shielded gesture can pass through. */
    fun setAppOverlayTouchable(touchable: Boolean) {
        val view = appBlockingOverlay?.view ?: return
        runCatching {
            windowManager.updateViewLayout(view, appBlockingLayoutParams(touchable))
        }
    }

    /**
     * Removes the full-screen block if present. The service's navigation flags are its own concern
     * and are reset by the thin wrapper that calls this, so no recovery state leaks in here.
     */
    fun removeAppBlockingOverlay() {
        val overlay = appBlockingOverlay ?: return
        appBlockingOverlay = null
        if (cooldownView === overlay.view) cooldownView = null
        runCatching { windowManager.removeView(overlay.view) }
        Log.i(TAG, "Removed full-screen block for ${overlay.app.packageName}")
    }

    fun showSiteBlock(packageName: String, domain: String, actions: SiteBlockActions) {
        if (!canShowBlockingOverlays()) {
            Log.w(TAG, "Website block suppressed; overlay permission is not granted")
            return
        }
        val view = View.inflate(context, R.layout.layout_site_block, null)
        view.findViewById<Button>(R.id.open_safe_page).setOnClickListener {
            actions.onOpenSafePage()
        }
        view.findViewById<Button>(R.id.go_back).setOnClickListener {
            actions.onGoBack()
        }
        if (!addOverlay(view, appBlockingLayoutParams(touchable = true))) return
        siteBlockingOverlay = SiteBlockingOverlay(packageName, domain, view)
        Log.i(TAG, "Website block shown above $packageName")
    }

    fun clearSiteBlockingOverlay() {
        val overlay = siteBlockingOverlay ?: return
        siteBlockingOverlay = null
        runCatching { windowManager.removeView(overlay.view) }
        Log.i(TAG, "Removed website block for ${overlay.packageName}")
    }

    fun showLocalizedBlockingOverlays(
        incident: IncidentId,
        mediaRegions: List<DetectionRegion>,
        boxes: List<DetectionBox>,
        verdict: ContentVerdict
    ) {
        if (!canShowBlockingOverlays()) {
            Log.w(TAG, "Localized block suppressed; overlay permission is not granted")
            return
        }
        val relevantClasses = relevantDetectorClasses(verdict)
        val detectedBoxes = boxes
            .filter { it.detectorClass in relevantClasses }
            .sortedByDescending(DetectionBox::score)
        val fallbackBoxes = if (detectedBoxes.isEmpty()) {
            mediaRegions.map {
                DetectionBox(it.left, it.top, it.right, it.bottom, relevantClasses.first(), 1f)
            }
        } else {
            emptyList()
        }
        val selected = LocalizedBoxSelector.select(
            detectedBoxes,
            fallbackBoxes,
            MAX_SIMULTANEOUS_OVERLAYS
        )
        if (selected.isEmpty()) {
            Log.w(TAG, "Unsafe verdict had no localizable media region; no overlay added")
            return
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val retainedViews = mutableSetOf<View>()
        var added = 0
        for (box in selected) {
            val bounds = Rect(
                (box.left * screenWidth).toInt().coerceIn(0, screenWidth - 1),
                (box.top * screenHeight).toInt().coerceIn(0, screenHeight - 1),
                (box.right * screenWidth).toInt().coerceIn(1, screenWidth),
                (box.bottom * screenHeight).toInt().coerceIn(1, screenHeight)
            )
            if (bounds.width() <= 0 || bounds.height() <= 0) continue

            val existing = localizedOverlays.firstOrNull {
                it.incident.packageName == incident.packageName &&
                    !windowChanged(it.incident.windowId, incident.windowId) &&
                    rectIntersectionOverUnion(it.bounds, bounds) >= OVERLAY_MATCH_IOU
            }
            if (existing != null) {
                activeIncidents.remove(existing.incident)
                existing.incident = incident
                existing.bounds.set(bounds)
                existing.view.findViewById<TextView>(R.id.image_block_reason).text =
                    localizedReason(verdict)
                updateLocalizedOverlayLayout(existing)
                activeIncidents += incident
                retainedViews += existing.view
                continue
            }

            val view = View.inflate(context, R.layout.layout_image_block, null)
            view.findViewById<TextView>(R.id.image_block_reason).text =
                localizedReason(verdict)
            val overlay = LocalizedOverlay(view, bounds, incident)
            if (!addOverlay(view, localizedLayoutParams(bounds))) continue
            localizedOverlays += overlay
            activeIncidents += incident
            retainedViews += view
            added++
        }
        val obsolete = localizedOverlays.filter {
            it.incident.packageName == incident.packageName &&
                !windowChanged(it.incident.windowId, incident.windowId) &&
                it.view !in retainedViews
        }
        obsolete.forEach { overlay ->
            runCatching { windowManager.removeView(overlay.view) }
            localizedOverlays.remove(overlay)
        }
        activeIncidents.clear()
        activeIncidents += localizedOverlays.map(LocalizedOverlay::incident)
        Log.i(
            TAG,
            "Localized block pkg=${incident.packageName} window=${incident.windowId} " +
                "verdict=$verdict added=$added replaced=${obsolete.size} " +
                "active=${localizedOverlays.size}"
        )
    }

    private fun localizedReason(verdict: ContentVerdict): String =
        if (verdict == ContentVerdict.EXPLICIT) {
            context.getString(R.string.explicit_content_blocked)
        } else {
            context.getString(R.string.suggestive_content_blocked)
        }

    private fun updateLocalizedOverlayLayout(overlay: LocalizedOverlay) {
        runCatching {
            windowManager.updateViewLayout(overlay.view, localizedLayoutParams(overlay.bounds))
        }
    }

    fun moveLocalizedOverlaysForScroll(event: AccessibilityEvent) {
        if (localizedOverlays.isEmpty()) return
        val eventPackage = event.packageName?.toString() ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val deltaY = event.scrollDeltaY
        if (deltaY == 0) {
            reconcileLocalizedOverlaysWithMediaNodes(eventPackage)
            return
        }
        val screenHeight = context.resources.displayMetrics.heightPixels
        val iterator = localizedOverlays.iterator()
        var moved = 0
        while (iterator.hasNext()) {
            val overlay = iterator.next()
            if (overlay.incident.packageName != eventPackage) continue
            overlay.bounds.offset(0, -deltaY)
            if (overlay.bounds.bottom <= 0 || overlay.bounds.top >= screenHeight) {
                runCatching { windowManager.removeView(overlay.view) }
                iterator.remove()
            } else {
                updateLocalizedOverlayLayout(overlay)
                moved++
            }
        }
        activeIncidents.clear()
        activeIncidents += localizedOverlays.map(LocalizedOverlay::incident)
        Log.d(TAG, "Tracked localized blocks during scroll; deltaY=$deltaY active=$moved")
    }

    fun reconcileLocalizedOverlaysWithMediaNodes(eventPackage: String) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val candidates = collectMediaRegions().map {
            Rect(
                (it.left * screenWidth).toInt(),
                (it.top * screenHeight).toInt(),
                (it.right * screenWidth).toInt(),
                (it.bottom * screenHeight).toInt()
            )
        }.toMutableList()
        if (candidates.isEmpty()) return

        var updated = 0
        for (overlay in localizedOverlays.filter { it.incident.packageName == eventPackage }) {
            val best = candidates
                .filter { candidate ->
                    val widthRatio = candidate.width().toFloat() / overlay.bounds.width()
                    val heightRatio = candidate.height().toFloat() / overlay.bounds.height()
                    widthRatio in MEDIA_SIZE_MATCH_MIN..MEDIA_SIZE_MATCH_MAX &&
                        heightRatio in MEDIA_SIZE_MATCH_MIN..MEDIA_SIZE_MATCH_MAX
                }
                .minByOrNull { candidate ->
                    kotlin.math.abs(candidate.centerX() - overlay.bounds.centerX()) +
                        kotlin.math.abs(candidate.centerY() - overlay.bounds.centerY())
                }
                ?: continue
            overlay.bounds.set(best)
            candidates.remove(best)
            updateLocalizedOverlayLayout(overlay)
            updated++
        }
        if (updated > 0) {
            Log.d(TAG, "Reconciled $updated localized block(s) from media nodes")
        }
    }

    fun clearLocalizedOverlaysOnly() {
        val removed = localizedOverlays.size
        localizedOverlays.forEach { runCatching { windowManager.removeView(it.view) } }
        localizedOverlays.clear()
        activeIncidents.clear()
        if (removed > 0) Log.i(TAG, "Removed $removed localized block(s)")
    }

    /**
     * Removes only the localized covers matching a now-safe frame's package and window. The
     * decision to also retire the full-screen block stays with the service, which owns navigation
     * state; this method never touches the app overlay.
     */
    fun clearLocalizedOverlaysForPackage(packageName: String, windowId: Int) {
        val iterator = localizedOverlays.iterator()
        var removed = 0
        while (iterator.hasNext()) {
            val overlay = iterator.next()
            if (overlay.incident.packageName != packageName ||
                windowChanged(overlay.incident.windowId, windowId)
            ) {
                continue
            }
            runCatching { windowManager.removeView(overlay.view) }
            iterator.remove()
            removed++
        }
        activeIncidents.clear()
        activeIncidents += localizedOverlays.map(LocalizedOverlay::incident)
        if (removed > 0) Log.i(TAG, "Safe frame removed $removed localized block(s)")
    }

    private fun appBlockingLayoutParams(touchable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            // This window is owned by an AccessibilityService. Using the trusted accessibility
            // type keeps Xiaomi/Android 16 from reducing an untrusted application overlay's
            // opacity while FLAG_NOT_TOUCHABLE lets a protected gesture pass underneath it.
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1f
        }
    }

    private fun localizedLayoutParams(bounds: Rect) = WindowManager.LayoutParams(
        bounds.width(),
        bounds.height(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
    }

    private fun addOverlay(view: View, layoutParams: WindowManager.LayoutParams): Boolean =
        runCatching { windowManager.addView(view, layoutParams) }
            .onFailure { Log.e(TAG, "Could not show blocking overlay", it) }
            .isSuccess

    private fun windowChanged(first: Int, second: Int): Boolean =
        first != UNKNOWN_WINDOW_ID && second != UNKNOWN_WINDOW_ID && first != second

    companion object {
        private const val TAG = "SinSheld"
        private const val UNKNOWN_WINDOW_ID = -1
        private const val RECOVERY_ACTION_REST_SECONDS = 5
        private const val RECOVERY_ACTION_REST_MS = RECOVERY_ACTION_REST_SECONDS * 1_000L
        private const val MAX_SIMULTANEOUS_OVERLAYS = 3
        private const val OVERLAY_MATCH_IOU = 0.45f
        private const val MEDIA_SIZE_MATCH_MIN = 0.65f
        private const val MEDIA_SIZE_MATCH_MAX = 1.55f
    }
}
