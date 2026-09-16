package com.example.sinshield

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.View
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Owns the screenshot→classify→verdict pipeline that is the heart of the shield: it schedules and
 * takes the capture, delegates analysis to an app-specific [AppAnalysisFlow], and turns the result
 * into overlays. The scan state machine ([ScanScheduler]), adaptive interval, confirmation state,
 * and idle model warm-up live here; model sequencing lives in independently changeable app flows.
 *
 * Like [OverlayManager] and [RecoveryController] this cannot be Android-free: capture is an
 * AccessibilityService method and inference reads real bitmaps, so [service] supplies the screenshot
 * APIs and Context, [overlays] is the surface a verdict draws on, and [appBlockActions] are the block
 * buttons handed to it. Foreground identity, event timestamps, monitored-package membership, the
 * close-in-progress flag, and the localized-overlay cleanup are all owned elsewhere and injected
 * through [host]; the two node reads the pipeline shares with the rest of the service arrive as the
 * [collectMediaRegions]/[collectScreenSignals] lambdas. Every member is touched only from the main
 * thread except [analyzeFrame] and model workers, which run off-main and hop back via [handler].
 */
internal class FrameScanner(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val overlays: OverlayManager,
    private val appBlockActions: OverlayManager.AppBlockActions,
    private val collectAccessibilityContext: (ShieldedApp?) -> ScanAccessibilityContext,
    private val host: Host
) {
    /**
     * The foreground identity and cleanup owned by the service's event loop that the scan pipeline
     * has to read. Foreground identity is updated by the event loop with an event fallback, so the
     * scanner reads it here rather than tracking a copy that could drift out of sync.
     */
    interface Host {
        fun syncForegroundFromRoot(): Boolean
        val foregroundPackage: String?
        val foregroundWindowId: Int
        val lastRelevantEventUptimeAt: Long
        val lastRelevantEventElapsedAt: Long
        fun isMonitored(packageName: String?): Boolean
        val closingAppInProgress: Boolean
        fun clearLocalizedOverlays(packageName: String, windowId: Int)
    }

    // Model construction is intentionally confined to inferenceExecutor. Creating several native
    // interpreters from AccessibilityService.onCreate() can stall the service main thread, and
    // retaining them while no protected app is visible wastes hundreds of megabytes on some devices.
    @Volatile private var analysisFlows: AppAnalysisFlowRegistry? = null
    private val modelDebugWriter =
        if (GlobalDebugMode.ENABLED) {
            ModelAnalysisDebugWriter(service.applicationContext)
        } else {
            null
        }
    private val captureExecutor = newBackgroundSingleThreadExecutor("SinSheld-Capture")
    private val inferenceExecutor = newBackgroundSingleThreadExecutor("SinSheld-Inference")

    private var stableSafeFrames = 0
    private var lastFrameHash: Long? = null
    private var lastSafeFrameHash: Long? = null
    private var confirmation: ConfirmationState? = null
    private var dismissedIncident: IncidentId? = null
    private var pendingFeedback: PendingFeedback? = null
    private var lastDetectionSettings: DetectionSettings? = null
    private var scheduledModelRelease: Runnable? = null
    private var postRecoverySafePackage: String? = null
    private val overlayReleaseGate = OverlayReleaseGate(REQUIRED_SAFE_RELEASE_ANALYSES)
    private val scanBurstPolicy = ScanBurstPolicy()

    // The scan state machine (single-flight, generations, debounce, adaptive interval) is driven
    // only from the main thread. onBeginScan is guarded so the injected start action never runs
    // below API R, which the plain method reference cannot express itself.
    private val scanScheduler = ScanScheduler(
        scheduler = object : ScanScheduler.Scheduler {
            override fun schedule(delayMs: Long, action: () -> Unit): Any {
                val runnable = Runnable { action() }
                handler.postDelayed(runnable, delayMs)
                return runnable
            }

            override fun cancel(handle: Any) {
                handler.removeCallbacks(handle as Runnable)
            }
        },
        now = SystemClock::elapsedRealtime,
        canScan = {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                host.isMonitored(host.foregroundPackage) &&
                !overlays.isRecoveryCooldownActive &&
                (overlays.appOverlay == null ||
                    postRecoverySafePackage == host.foregroundPackage)
        },
        onBeginScan = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) beginScan()
        }
    )

    /** True while a capture/inference flight is outstanding; the event loop reads it to coalesce. */
    val inFlight: Boolean get() = scanScheduler.inFlight

    fun requestScan(delayMs: Long) = scanScheduler.requestScan(delayMs)

    fun requestPostRecoveryScan(packageName: String, delayMs: Long) {
        postRecoverySafePackage = packageName
        requestScan(delayMs)
    }

    fun cancelScheduledScan() = scanScheduler.cancelScheduledScan()

    fun markFramePending() = scanScheduler.markFramePending()

    fun invalidateInFlightResults() = scanScheduler.invalidateInFlightResults()

    /** Drops any pending confirmation streak when the foreground stops being scannable. */
    fun clearConfirmation() {
        confirmation = null
    }

    fun dismissIncident(incident: IncidentId) {
        dismissedIncident = incident
        if (pendingFeedback?.incident == incident) pendingFeedback = null
        Log.i(TAG, "User dismissed incident hash=${incident.frameHash.toULong().toString(16)}")
    }

    fun recordFeedback(
        incident: IncidentId,
        disturbing: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        val feedback = pendingFeedback?.takeIf { it.incident == incident }
        if (disturbing) {
            if (feedback != null) pendingFeedback = null
            Log.i(TAG, "User confirmed incident as disturbing")
            onComplete(true)
            return
        }

        dismissedIncident = incident
        pendingFeedback = null
        if (feedback == null) {
            Log.w(TAG, "False-positive feedback had no matching captured frame")
            onComplete(false)
            return
        }
        // Apply the user's correction before the asynchronous evidence write finishes so another
        // scan of the same visible frame cannot recreate the block in the meantime.
        FeedbackRepository.rememberFalsePositive(
            service,
            feedback.evidence.packageName,
            feedback.evidence.frameHash
        )
        inferenceExecutor.execute {
            val saved = runCatching {
                FeedbackRepository.saveFalsePositive(service, feedback.evidence)
            }
                .onSuccess { saved -> Log.i(TAG, "False-positive feedback saved at ${saved.path}") }
                .onFailure { error -> Log.e(TAG, "Could not save false-positive feedback", error) }
                .isSuccess
            handler.post { onComplete(saved) }
        }
    }

    /** The initial scan requested when the service connects to an already-foreground shielded app. */
    fun scheduleConnectedScan() = requestScan(EVENT_DEBOUNCE_MS)

    /**
     * A relevant event for a current, monitored app. An event during inference never starts a second
     * flight; one bit records that the latest composited frame still needs checking when the flight
     * finishes. Otherwise a fresh scan is scheduled after the debounce.
     */
    fun onMonitoredEvent() {
        cancelScheduledModelRelease()
        scanBurstPolicy.noteEvent()
        if (scanScheduler.inFlight) {
            scanScheduler.markFramePending()
            return
        }
        scanScheduler.resetToActiveInterval()
        stableSafeFrames = 0
        requestScan(EVENT_DEBOUNCE_MS)
    }

    fun resetAdaptiveState(keepLastHash: Boolean = false) {
        scanScheduler.resetToActiveInterval()
        stableSafeFrames = 0
        confirmation = null
        lastSafeFrameHash = null
        if (!keepLastHash) lastFrameHash = null
    }

    /** Releases native model state after the user has stayed outside protected apps for a while. */
    fun scheduleModelRelease() {
        if (scheduledModelRelease != null || analysisFlows == null) return
        val release = Runnable {
            scheduledModelRelease = null
            if (host.isMonitored(host.foregroundPackage) || scanScheduler.inFlight) return@Runnable
            inferenceExecutor.execute {
                analysisFlows?.close()
                analysisFlows = null
                Log.i(TAG, "Detection models released while protection is idle")
            }
        }
        scheduledModelRelease = release
        handler.postDelayed(release, MODEL_IDLE_RELEASE_DELAY_MS)
    }

    fun cancelScheduledModelRelease() {
        scheduledModelRelease?.let(handler::removeCallbacks)
        scheduledModelRelease = null
    }

    /** Drops native ML state when Android reports memory pressure; the next scan reloads it. */
    fun releaseModelsForMemoryPressure() {
        cancelScheduledModelRelease()
        inferenceExecutor.execute {
            analysisFlows?.close()
            analysisFlows = null
            Log.w(TAG, "Detection models released because Android reported memory pressure")
        }
    }

    /** Closes the detection models and stops the inference thread on service teardown. */
    fun shutdown() {
        cancelScheduledModelRelease()
        inferenceExecutor.execute {
            analysisFlows?.close()
            analysisFlows = null
        }
        inferenceExecutor.shutdown()
        captureExecutor.shutdownNow()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun beginScan() {
        if (scanScheduler.inFlight) return
        if (overlays.isRecoveryCooldownActive) return
        // Foreground identity is maintained by accessibility events. A root lookup here can block
        // the service main thread for several seconds if the foreground app is unresponsive.
        val pkg = host.foregroundPackage ?: return
        if (!host.isMonitored(pkg)) return

        val protectionLevel = ProtectionPreferences.protectionLevel(service)
        val detectionSettings = ProtectionPreferences.detectionSettings(service)
        if (detectionSettings != lastDetectionSettings) {
            lastDetectionSettings = detectionSettings
            lastSafeFrameHash = null
            confirmation = null
            Log.i(
                TAG,
                "Detection settings changed: level=${protectionLevel.displayName} " +
                    "settings=$detectionSettings"
            )
        }
        val generation = scanScheduler.startFlightOrDefer() ?: return
        val shieldedApp = ShieldedApp.forPackage(pkg)
        val allowLateSafeResult = postRecoverySafePackage == pkg &&
            overlays.appOverlay?.app?.packageName == pkg
        if (!allowLateSafeResult && postRecoverySafePackage != null) {
            postRecoverySafePackage = null
        }
        val windowId = host.foregroundWindowId
        val eventTimestamp = host.lastRelevantEventUptimeAt
        // AccessibilityNodeInfo getters are synchronous Binder calls into the foreground app. A
        // slow/ANR-ing feed can hold one for seconds, so collect the tree snapshot on a background
        // worker. Blocking the AccessibilityService main looper makes HyperOS force-stop the whole
        // package and consequently turns off both Accessibility and VPN.
        captureExecutor.execute {
            val accessibility = runCatching { collectAccessibilityContext(shieldedApp) }
                .onFailure { Log.w(TAG, "Could not collect accessibility scan context", it) }
                .getOrDefault(ScanAccessibilityContext.EMPTY)
            handler.post {
                if (host.foregroundPackage != pkg || windowChanged(windowId, host.foregroundWindowId)) {
                    finishFailedScan("Foreground changed while preparing scan")
                    return@post
                }
                val context = ScanContext(
                    generation = generation,
                    packageName = pkg,
                    windowId = windowId,
                    eventTimestamp = eventTimestamp,
                    mediaRegions = accessibility.mediaRegions,
                    screenMode = shieldedApp?.screenMode(accessibility.screenSignals)
                        ?: ShieldedScreenMode.UNKNOWN,
                    screenSignals = accessibility.screenSignals,
                    // Every pass which can contribute to removing an app shield must run the complete
                    // model pipeline; a repeated frame must not be accepted through the safe-hash cache.
                    knownSafeFrameHash = if (allowLateSafeResult) null else lastSafeFrameHash,
                    protectionLevel = protectionLevel,
                    detectionSettings = detectionSettings,
                    allowLateSafeResult = allowLateSafeResult
                )
                requestPreparedScreenshot(context)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestPreparedScreenshot(context: ScanContext) {
        val hiddenForLegacyCapture = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overlays.hideForLegacyCapture()
        } else {
            emptyList()
        }
        val capture = Runnable { requestScreenshot(context, hiddenForLegacyCapture) }
        if (hiddenForLegacyCapture.isEmpty()) {
            capture.run()
        } else {
            // Legacy display capture includes accessibility overlays. Wait one render frame after
            // hiding them, then restore immediately when capture completes.
            handler.postDelayed(capture, LEGACY_OVERLAY_HIDE_FRAME_MS)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestScreenshot(
        context: ScanContext,
        hiddenForLegacyCapture: List<View>,
        forceDisplayCapture: Boolean = false
    ) {
        try {
            val useWindowCapture = !forceDisplayCapture &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                context.windowId != UNKNOWN_WINDOW_ID
            val callback = screenshotCallback(context, hiddenForLegacyCapture, useWindowCapture)
            if (useWindowCapture) {
                // Window capture excludes SinSheld's accessibility covers, allowing a covered
                // image to be re-evaluated and a genuinely safe feed to remove stale covers.
                service.takeScreenshotOfWindow(
                    context.windowId,
                    captureExecutor,
                    callback
                )
            } else {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    captureExecutor,
                    callback
                )
            }
        } catch (error: Throwable) {
            overlays.restoreLegacyCaptureOverlays(hiddenForLegacyCapture)
            Log.e(TAG, "takeScreenshot failed", error)
            finishFailedScan("Screenshot request threw")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun screenshotCallback(
        context: ScanContext,
        hiddenForLegacyCapture: List<View>,
        usedWindowCapture: Boolean
    ) = object : AccessibilityService.TakeScreenshotCallback {
        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
            handler.post { overlays.restoreLegacyCaptureOverlays(hiddenForLegacyCapture) }
            val screenshotTimestamp = result.timestamp
            val hardwareBuffer = result.hardwareBuffer
            var hardwareBitmap: Bitmap? = null
            try {
                hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                val bitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                if (bitmap == null) {
                    handler.post { finishFailedScan("Screenshot buffer could not be copied") }
                    return
                }
                inferenceExecutor.execute {
                    analyzeFrame(context, screenshotTimestamp, bitmap)
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to read screenshot buffer", error)
                handler.post { finishFailedScan("Screenshot buffer failed") }
            } finally {
                hardwareBitmap?.recycle()
                hardwareBuffer.close()
            }
        }

        override fun onFailure(errorCode: Int) {
            handler.post {
                if (usedWindowCapture &&
                    errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR
                ) {
                    // Some HyperOS builds advertise window capture but intermittently reject a
                    // valid accessibility window. A display capture keeps protection alive; there
                    // is no SinShield overlay in the ordinary scanning state to contaminate it.
                    Log.w(TAG, "Window screenshot failed; falling back to display capture")
                    requestScreenshot(
                        context,
                        hiddenForLegacyCapture,
                        forceDisplayCapture = true
                    )
                    return@post
                }
                overlays.restoreLegacyCaptureOverlays(hiddenForLegacyCapture)
                Log.w(TAG, "Screenshot failed: ${screenshotErrorName(errorCode)}")
                if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                    scanScheduler.clampToStable()
                }
                finishFailedScan(
                    "Screenshot error $errorCode",
                    preserveRateLimitBackoff =
                        errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
                )
            }
        }
    }

    private fun analyzeFrame(context: ScanContext, screenshotTimestamp: Long, bitmap: Bitmap) {
        val analysisStartedAt = SystemClock.elapsedRealtime()
        try {
            val frameHash = FrameHasher.averageHash(bitmap)
            val unchangedKnownSafeFrame = frameHash == context.knownSafeFrameHash
            val rememberedFalsePositive = FeedbackRepository.isKnownFalsePositive(
                service,
                context.packageName,
                frameHash
            )
            val forceFullAnalysis = context.allowLateSafeResult
            val analysis = if (!forceFullAnalysis &&
                (unchangedKnownSafeFrame || rememberedFalsePositive)
            ) {
                FrameAnalysis.safe(
                    frameHash,
                    deduplicated = true,
                    bitmap = bitmap,
                    thresholds = context.detectionSettings.thresholds,
                    requireVerifierForStrongExplicit =
                        context.detectionSettings.requireVerifierForStrongExplicit,
                    falsePositiveSuppressed = rememberedFalsePositive
                )
            } else {
                getOrCreateAnalysisFlows().analyze(
                    AppAnalysisInput(
                        packageName = context.packageName,
                        frameHash = frameHash,
                        bitmap = bitmap,
                        settings = context.detectionSettings,
                        debugSession = modelDebugWriter?.beginFrame(frameHash),
                        accessibilityMediaRegions = context.mediaRegions,
                        screenMode = context.screenMode,
                        screenSignals = context.screenSignals
                    )
                )
            }
            val evidenceReadyAnalysis = if (analysis.verdict == ContentVerdict.SAFE) {
                analysis
            } else {
                analysis.copy(frameJpeg = encodeFeedbackFrame(bitmap))
            }
            val durationMs = SystemClock.elapsedRealtime() - analysisStartedAt
            handler.post {
                completeScan(context, screenshotTimestamp, evidenceReadyAnalysis, durationMs)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Frame inference failed", error)
            handler.post { finishFailedScan("Inference error") }
        } finally {
            bitmap.recycle()
        }
    }

    private fun getOrCreateAnalysisFlows(): AppAnalysisFlowRegistry =
        analysisFlows ?: AppAnalysisFlowRegistry(service.applicationContext).also {
            analysisFlows = it
            Log.i(TAG, "Detection models loaded on the inference thread")
        }

    private fun completeScan(
        context: ScanContext,
        screenshotTimestamp: Long,
        analysis: FrameAnalysis,
        durationMs: Long
    ) {
        val supersededByNewerScan = scanScheduler.recordCompletion(context.generation)

        val eventArrivedAfterCapture = host.lastRelevantEventUptimeAt > screenshotTimestamp
        val contextStale = supersededByNewerScan ||
            host.foregroundPackage != context.packageName ||
            windowChanged(context.windowId, host.foregroundWindowId)
        // Instagram emits content-change events while media remains visually unchanged. Inference
        // takes over a second on typical devices, so rejecting every result after any such event
        // starves the blocker indefinitely. Ordinary late SAFE results are retried; the one scan
        // explicitly requested after a recovery action may clear the matching app's shield. Late
        // unsafe results remain conservatively accepted while package and window stay current.
        val stale = ScanFreshnessPolicy.shouldDiscard(
            contextStale,
            eventArrivedAfterCapture,
            analysis.verdict,
            context.allowLateSafeResult
        )

        val diagnostics =
            "scan=${context.generation} pkg=${context.packageName} window=${context.windowId} " +
                "eventTs=${context.eventTimestamp} screenshotTs=$screenshotTimestamp " +
                "latestEventTs=${host.lastRelevantEventUptimeAt} " +
                "hash=${analysis.frameHash.toULong().toString(16)} " +
                "protectionLevel=${context.protectionLevel.name} " +
                "thresholds=${analysis.thresholds} " +
                "requireVerifierForStrongExplicit=" +
                "${analysis.requireVerifierForStrongExplicit} " +
                "candidate=${analysis.candidateVerdict} verdict=${analysis.verdict} " +
                "verifierNsfw=${analysis.verification?.nsfwScore ?: "not_run"} " +
                "causes=${analysis.causes.joinToString()} boxes=${analysis.localized.boxes.size} " +
                "regions=${formatRegionSources(analysis.localized.regionScores)} " +
                "frame=${analysis.frameWidth}x${analysis.frameHeight} " +
                "metrics=${service.resources.displayMetrics.widthPixels}x" +
                "${service.resources.displayMetrics.heightPixels} " +
                "verifyBox=${analysis.verifierBox?.let { formatVerifierBox(it) } ?: "none"} " +
                "allRegions=[${formatRegionScores(analysis.localized.regionScores)}] " +
                "screenScores=[${formatWholeScreenScores(analysis.wholeScreenScores)}] " +
                "screenExplicit=${analysis.wholeScreenExplicitScore} " +
                "screenSexy=${analysis.wholeScreenSemiNudeScore} " +
                "regionExplicit=${analysis.localized.explicitScore} " +
                "regionSexy=${analysis.localized.semiNudeScore} " +
                "inferenceMs=$durationMs stale=$stale eventAfterCapture=$eventArrivedAfterCapture " +
                "postRecovery=${context.allowLateSafeResult} " +
                "dedup=${analysis.deduplicated} " +
                "feedbackSuppressed=${analysis.falsePositiveSuppressed}"
        Log.i(TAG, diagnostics)

        scanScheduler.clearInFlight()
        if (stale) {
            scanScheduler.markFramePending()
            scheduleAfterCompletion(forceFast = true)
            return
        }

        val previousHash = lastFrameHash
        val contentChangedSignificantly = previousHash != null &&
            FrameHasher.hammingDistance(previousHash, analysis.frameHash) >= SIGNIFICANT_HASH_DISTANCE
        lastFrameHash = analysis.frameHash
        if (contentChangedSignificantly) resetAdaptiveState(keepLastHash = true)

        val timing = ScanTiming(context.eventTimestamp, screenshotTimestamp, durationMs)
        when (analysis.verdict) {
            ContentVerdict.SAFE -> {
                if (ShieldedApp.forPackage(context.packageName) != null && host.closingAppInProgress) {
                    Log.d(TAG, "Safe frame reached during close; keeping shield until task removal")
                    postRecoverySafePackage = null
                    overlayReleaseGate.reset()
                } else if (overlays.appOverlay?.app?.packageName == context.packageName) {
                    val canRelease = overlayReleaseGate.recordSafeAnalysis()
                    if (!canRelease) {
                        lastSafeFrameHash = analysis.frameHash
                        noteSafeFrame(contentChangedSignificantly)
                        Log.i(
                            TAG,
                            "Keeping full-screen shield after clean analysis " +
                                "${overlayReleaseGate.consecutiveSafeAnalyses}/" +
                                "$REQUIRED_SAFE_RELEASE_ANALYSES"
                        )
                        requestPostRecoveryScan(context.packageName, CONFIRMATION_INTERVAL_MS)
                        return
                    }
                    Log.i(TAG, "Releasing full-screen shield after three clean analyses")
                    postRecoverySafePackage = null
                    overlayReleaseGate.reset()
                    host.clearLocalizedOverlays(context.packageName, context.windowId)
                } else {
                    postRecoverySafePackage = null
                    overlayReleaseGate.reset()
                    host.clearLocalizedOverlays(context.packageName, context.windowId)
                }
                confirmation = null
                lastSafeFrameHash = analysis.frameHash
                noteSafeFrame(contentChangedSignificantly)
                scheduleAfterCompletion()
            }
            ContentVerdict.SUSPICIOUS -> {
                postRecoverySafePackage = null
                overlayReleaseGate.reset()
                handleSuspicious(context, analysis, diagnostics, timing)
            }
            ContentVerdict.EXPLICIT,
            ContentVerdict.SEMI_NUDE -> {
                postRecoverySafePackage = null
                overlayReleaseGate.reset()
                handleFinalUnsafe(context, analysis, diagnostics = diagnostics, timing = timing)
            }
        }
    }

    private fun handleSuspicious(
        context: ScanContext,
        analysis: FrameAnalysis,
        diagnostics: String,
        timing: ScanTiming
    ) {
        val suspectedFinalVerdict = analysis.suspectedFinalVerdict
        val prior = confirmation
        val sameCandidate = prior != null &&
            prior.packageName == context.packageName &&
            !windowChanged(prior.windowId, context.windowId) &&
            FrameHasher.hammingDistance(prior.frameHash, analysis.frameHash) < SIGNIFICANT_HASH_DISTANCE &&
            prior.suspectedVerdict == suspectedFinalVerdict

        val confirmations = if (sameCandidate) prior!!.confirmations + 1 else 0
        val requiredConfirmations = if (
            context.packageName == ShieldedApp.REDDIT.packageName &&
            analysis.candidateVerdict == ContentVerdict.EXPLICIT
        ) {
            REDDIT_STRONG_EXPLICIT_CONFIRMATIONS
        } else {
            MAX_CONFIRMATION_CAPTURES
        }
        if (confirmations >= requiredConfirmations) {
            confirmation = null
            handleFinalUnsafe(context, analysis, suspectedFinalVerdict, diagnostics, timing)
            return
        }

        confirmation = ConfirmationState(
            packageName = context.packageName,
            windowId = context.windowId,
            frameHash = analysis.frameHash,
            suspectedVerdict = suspectedFinalVerdict,
            confirmations = confirmations
        )
        showProvisionalShield(context, analysis, suspectedFinalVerdict, diagnostics, timing)
        scanScheduler.resetToActiveInterval()
        Log.i(
            TAG,
            "Suspicious result; quick confirmation scheduled"
        )
        requestScan(CONFIRMATION_INTERVAL_MS)
    }

    /**
     * Covers a suspicious frame while its one follow-up confirmation is running. A SAFE follow-up
     * removes this provisional shield through the normal safe-frame path; a matching result upgrades
     * it to a final incident with feedback evidence. This keeps confirmation from extending the time
     * disturbing pixels remain visible.
     */
    private fun showProvisionalShield(
        context: ScanContext,
        analysis: FrameAnalysis,
        verdict: ContentVerdict,
        diagnostics: String,
        timing: ScanTiming
    ) {
        if (verdict == ContentVerdict.SEMI_NUDE &&
            !context.detectionSettings.blockSuggestive
        ) {
            return
        }
        if (!isCurrent(context)) return

        val incident = IncidentId(context.packageName, context.windowId, analysis.frameHash)
        if (dismissedIncident.matchesVisibleFrame(incident)) return
        val shieldedApp = ShieldedApp.forPackage(context.packageName)
        if (shieldedApp != null) {
            val screenSignals = context.screenSignals
            val mode = shieldedApp.screenMode(screenSignals)
            if (mode != ShieldedScreenMode.CREATION) {
                preparePendingFeedback(context, analysis, incident, verdict, diagnostics)
                overlays.showAppBlockingOverlay(
                    shieldedApp,
                    incident,
                    verdict,
                    mode,
                    appBlockActions
                )
                logBanTiming(context, verdict, timing, provisional = true, mode = mode)
            }
        } else {
            overlays.showLocalizedBlockingOverlays(
                incident,
                context.mediaRegions,
                analysis.localized.boxes,
                verdict
            )
            logBanTiming(context, verdict, timing, provisional = true)
        }
    }

    private fun handleFinalUnsafe(
        context: ScanContext,
        analysis: FrameAnalysis,
        verdict: ContentVerdict = analysis.verdict,
        diagnostics: String,
        timing: ScanTiming
    ) {
        if (verdict == ContentVerdict.SEMI_NUDE &&
            !context.detectionSettings.blockSuggestive
        ) {
            Log.i(TAG, "Suggestive result allowed because strict mode is off")
            lastSafeFrameHash = analysis.frameHash
            noteSafeFrame(contentChangedSignificantly = false)
            scheduleAfterCompletion()
            return
        }

        if (!isCurrent(context)) {
            scanScheduler.markFramePending()
            scheduleAfterCompletion(forceFast = true)
            return
        }

        val incident = IncidentId(context.packageName, context.windowId, analysis.frameHash)
        if (dismissedIncident.matchesVisibleFrame(incident)) {
            Log.d(TAG, "Keeping user-dismissed incident hidden")
            scheduleAfterCompletion()
            return
        }
        val shieldedApp = ShieldedApp.forPackage(context.packageName)
        if (shieldedApp != null) {
            val screenSignals = context.screenSignals
            val mode = shieldedApp.screenMode(screenSignals)
            if (mode == ShieldedScreenMode.CREATION) {
                Log.i(
                    TAG,
                    "Unsafe result ignored on ${shieldedApp.displayName} creation screen; " +
                        "evidence=${shieldedApp.creationEvidence(screenSignals).joinToString()}"
                )
                confirmation = null
                // Do not bless the user's draft as globally safe: the same pixels may later be
                // posted into a monitored feed without changing the app window.
                lastSafeFrameHash = null
                noteSafeFrame(contentChangedSignificantly = false)
            } else {
                appBlockActions.onRecoveryVerificationBlocked(shieldedApp)
                preparePendingFeedback(context, analysis, incident, verdict, diagnostics)
                overlays.showAppBlockingOverlay(shieldedApp, incident, verdict, mode, appBlockActions)
                logBanTiming(context, verdict, timing, provisional = false, mode = mode)
            }
        } else {
            overlays.showLocalizedBlockingOverlays(
                incident,
                context.mediaRegions,
                analysis.localized.boxes,
                verdict
            )
            logBanTiming(context, verdict, timing, provisional = false)
        }
        scheduleAfterCompletion()
    }

    private fun preparePendingFeedback(
        context: ScanContext,
        analysis: FrameAnalysis,
        incident: IncidentId,
        verdict: ContentVerdict,
        diagnostics: String
    ) {
        analysis.frameJpeg?.let { jpeg ->
            pendingFeedback = PendingFeedback(
                incident,
                FeedbackEvidence(
                    capturedAtEpochMillis = System.currentTimeMillis(),
                    packageName = context.packageName,
                    windowId = context.windowId,
                    frameHash = analysis.frameHash,
                    verdict = verdict.name,
                    protectionLevel = context.protectionLevel.name,
                    diagnostics = diagnostics,
                    imageJpeg = jpeg
                )
            )
        }
    }

    /**
     * One release-safe line per shown block, measuring the visible→blocked latency so the pipeline
     * can be timed on a production build without the debuggable-only image dumps. Every point stamp
     * is uptime millis — the same domain as eventTs/screenshotTs — so the parts sum to the total:
     * `eventToCapture` (debounce + cooldown + capture queueing) + `captureToBlock` (screenshot
     * delivery + inference + main-thread hop + overlay inflation) = `total`. `inferenceMs` is the
     * model-only slice inside captureToBlock. A `provisional` line is a cover shown before its
     * confirmation scan; the matching `provisional=false` line follows when the block is finalized.
     */
    private fun logBanTiming(
        context: ScanContext,
        verdict: ContentVerdict,
        timing: ScanTiming,
        provisional: Boolean,
        mode: ShieldedScreenMode? = null
    ) {
        val blockShownAt = SystemClock.uptimeMillis()
        val eventToCaptureMs = timing.screenshotTimestamp - timing.eventTimestamp
        val captureToBlockMs = blockShownAt - timing.screenshotTimestamp
        val totalMs = blockShownAt - timing.eventTimestamp
        Log.i(
            TAG,
            "BAN pkg=${context.packageName} verdict=$verdict " +
                (mode?.let { "mode=$it " } ?: "") +
                (if (provisional) "provisional=true " else "") +
                "eventToCaptureMs=$eventToCaptureMs inferenceMs=${timing.inferenceMs} " +
                "captureToBlockMs=$captureToBlockMs totalMs=$totalMs"
        )
    }

    private fun encodeFeedbackFrame(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, FEEDBACK_JPEG_QUALITY, output)
        return output.toByteArray()
    }

    private fun isCurrent(context: ScanContext): Boolean {
        return host.foregroundPackage == context.packageName &&
            !windowChanged(context.windowId, host.foregroundWindowId)
    }

    private fun noteSafeFrame(contentChangedSignificantly: Boolean) {
        val recentlyChanging =
            SystemClock.elapsedRealtime() - host.lastRelevantEventElapsedAt < ACTIVE_EVENT_WINDOW_MS
        if (contentChangedSignificantly || recentlyChanging) {
            stableSafeFrames = 0
            scanScheduler.resetToActiveInterval()
            return
        }
        stableSafeFrames++
        if (stableSafeFrames >= SAFE_FRAMES_BEFORE_BACKOFF) {
            scanScheduler.backOff()
        }
    }

    private fun scheduleAfterCompletion(forceFast: Boolean = false) {
        // Arm the completion-based gap even when the finite follow-up burst is already exhausted;
        // a new event must not restart heavy analysis immediately after the previous one returned.
        scanScheduler.enforceCompletionRest(forceFast)
        if (!host.isMonitored(host.foregroundPackage)) {
            Log.d(
                TAG,
                "Next scan not scheduled; foreground=${host.foregroundPackage} " +
                    "window=${host.foregroundWindowId}"
            )
            return
        }
        if (!scanBurstPolicy.shouldSchedule(forceFast, scanScheduler.framePending)) {
            Log.d(TAG, "Scan burst complete; waiting for the next accessibility event")
            return
        }
        scanScheduler.scheduleAfterCompletion(forceFast)
    }

    private fun finishFailedScan(reason: String, preserveRateLimitBackoff: Boolean = false) {
        Log.d(TAG, "$reason; scanner will retry")
        scanScheduler.clearInFlight()
        if (preserveRateLimitBackoff) {
            scanScheduler.clearFramePending()
            requestScan(scanScheduler.currentIntervalMs)
        } else {
            scheduleAfterCompletion(forceFast = scanScheduler.framePending)
        }
    }

    private fun windowChanged(first: Int, second: Int): Boolean =
        first != UNKNOWN_WINDOW_ID && second != UNKNOWN_WINDOW_ID && first != second

    private fun screenshotErrorName(code: Int): String = when (code) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "INTERNAL_ERROR"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "NO_ACCESSIBILITY_ACCESS"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "INTERVAL_TIME_SHORT"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "INVALID_DISPLAY"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "SECURE_WINDOW"
        else -> "UNKNOWN($code)"
    }

    companion object {
        private const val TAG = "SinSheld"
        private const val UNKNOWN_WINDOW_ID = -1
        private const val MODEL_IDLE_RELEASE_DELAY_MS = 60_000L
        private const val EVENT_DEBOUNCE_MS = 80L
        // ScanScheduler still enforces Android's capture cooldown. Adding another fixed wait here
        // only leaves suspicious pixels visible longer on devices whose first inference was slow.
        private const val CONFIRMATION_INTERVAL_MS = 0L
        private const val LEGACY_OVERLAY_HIDE_FRAME_MS = 20L
        private const val ACTIVE_EVENT_WINDOW_MS = 700L
        private const val SAFE_FRAMES_BEFORE_BACKOFF = 3
        // One matching follow-up still rejects one-frame glitches without making the user wait for
        // three more full inference passes before the shield appears.
        private const val MAX_CONFIRMATION_CAPTURES = 1
        private const val REDDIT_STRONG_EXPLICIT_CONFIRMATIONS = 1
        private const val REQUIRED_SAFE_RELEASE_ANALYSES = 3
        private const val SIGNIFICANT_HASH_DISTANCE = 14
        private const val FEEDBACK_JPEG_QUALITY = 90
    }
}

private data class ScanContext(
    val generation: Long,
    val packageName: String,
    val windowId: Int,
    val eventTimestamp: Long,
    val mediaRegions: List<DetectionRegion>,
    val screenMode: ShieldedScreenMode,
    val screenSignals: ScreenSignals,
    val knownSafeFrameHash: Long?,
    val protectionLevel: ProtectionLevel,
    val detectionSettings: DetectionSettings,
    val allowLateSafeResult: Boolean
)

internal data class ScanAccessibilityContext(
    val mediaRegions: List<DetectionRegion>,
    val screenSignals: ScreenSignals
) {
    companion object {
        val EMPTY = ScanAccessibilityContext(emptyList(), ScreenSignals.EMPTY)
    }
}

/** The three uptime-millis stamps [logBanTiming] needs, carried from completion to the show site. */
private data class ScanTiming(
    val eventTimestamp: Long,
    val screenshotTimestamp: Long,
    val inferenceMs: Long
)

private data class PendingFeedback(
    val incident: IncidentId,
    val evidence: FeedbackEvidence
)

private fun IncidentId?.matchesVisibleFrame(other: IncidentId): Boolean =
    this != null &&
        packageName == other.packageName &&
        FrameHasher.hammingDistance(frameHash, other.frameHash) < 14

private data class ConfirmationState(
    val packageName: String,
    val windowId: Int,
    val frameHash: Long,
    val suspectedVerdict: ContentVerdict,
    val confirmations: Int
)

/**
 * The box that actually produced [verdict]. Selecting the globally highest-scoring box instead
 * routinely picks the wrong region, because a Sexy hit outscores the Porn hit behind an EXPLICIT
 * candidate, and the verifier is then asked to confirm porn while looking somewhere else.
 * Null means no localized box supports the verdict, so the whole screen is verified instead.
 */
internal fun List<DetectionBox>.bestSupporting(verdict: ContentVerdict): DetectionBox? {
    val relevant = relevantDetectorClasses(verdict)
    return filter { it.detectorClass in relevant }.maxByOrNull(DetectionBox::score)
}

internal data class LocalizedDetection(
    val boxes: List<DetectionBox>,
    val explicitScore: Float,
    val semiNudeScore: Float,
    // Every region and what it scored, not just the winner, so a region sitting somewhere other
    // than the on-screen photo is visible in the log.
    val regionScores: List<RegionScore> = emptyList()
) {
    companion object {
        val EMPTY = LocalizedDetection(emptyList(), 0f, 0f)
    }
}

internal data class RegionScore(
    val region: DetectionRegion,
    val explicit: Float,
    val semiNude: Float
)

private fun round(value: Float): String = "${(value * 1000).roundToInt() / 1000f}"

/** How many OpenCV regions contributed to the localized result. */
private fun formatRegionSources(scores: List<RegionScore>): String = scores
    .groupingBy { it.region.source }
    .eachCount()
    .entries
    .joinToString("+") { "${it.key}:${it.value}" }
    .ifEmpty { "none" }

/** Every analyzed region and its scores, so a misplaced region is visible without a screenshot. */
private fun formatRegionScores(scores: List<RegionScore>): String = scores.joinToString(",") {
    "${round(it.region.left)},${round(it.region.top)}-" +
        "${round(it.region.right)},${round(it.region.bottom)}" +
        "/e${round(it.explicit)}/s${round(it.semiNude)}"
}

private fun formatWholeScreenScores(scores: FloatArray): String {
    if (scores.size < 5) return "unavailable"
    return "drawings=${round(scores[0])},hentai=${round(scores[1])}," +
        "neutral=${round(scores[2])},porn=${round(scores[3])},sexy=${round(scores[4])}"
}

/**
 * Renders the box the verifier was pointed at, including its share of the screen. A large area
 * means the subject is a small part of the 384x384 verifier input and its score may be diluted.
 * NsfwVerifier uses these exact bounds when creating the verifier input.
 */
internal fun formatVerifierBox(box: DetectionBox): String {
    val area = (box.right - box.left) * (box.bottom - box.top)
    return "${box.detectorClass}@${box.left},${box.top}-${box.right},${box.bottom}/area=$area"
}

internal data class FrameAnalysis(
    val frameHash: Long,
    val stageOne: StageOneResult,
    val localized: LocalizedDetection,
    val wholeScreenScores: FloatArray,
    val wholeScreenExplicitScore: Float,
    val wholeScreenSemiNudeScore: Float,
    val verification: VerificationResult?,
    val verifierBox: DetectionBox? = null,
    // Node bounds are normalized against displayMetrics, but crops are taken from the screenshot.
    // If the two disagree every accessibility-derived box is offset and the models see the wrong
    // pixels, so both sizes are logged until this is ruled out.
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val deduplicated: Boolean,
    val falsePositiveSuppressed: Boolean = false,
    val thresholds: DetectionThresholds = DetectionThresholds(),
    val requireVerifierForStrongExplicit: Boolean = true,
    val frameJpeg: ByteArray? = null
) {
    private val candidatePolicy: StageOneResult
        get() = ContentPolicy.combine(
            stageOne,
            localized.explicitScore,
            localized.semiNudeScore,
            thresholds
        )

    val candidateVerdict: ContentVerdict
        get() = candidatePolicy.verdict
    val verdict: ContentVerdict
        get() = VerifierPolicy.finalVerdict(
            candidatePolicy,
            verification?.nsfwScore,
            thresholds.verifier,
            requireVerifierForStrongExplicit
        )
    val suspectedFinalVerdict: ContentVerdict
        get() = candidatePolicy.suspectedFinalVerdict
    val causes: List<String>
        get() = stageOne.causes + localized.boxes.map { it.detectorClass }.distinct()

    companion object {
        fun safe(
            frameHash: Long,
            deduplicated: Boolean,
            bitmap: Bitmap? = null,
            thresholds: DetectionThresholds = DetectionThresholds(),
            requireVerifierForStrongExplicit: Boolean = true,
            falsePositiveSuppressed: Boolean = false
        ) = FrameAnalysis(
            frameHash = frameHash,
            stageOne = StageOneResult(ContentVerdict.SAFE, ContentVerdict.SAFE, emptyList()),
            localized = LocalizedDetection.EMPTY,
            wholeScreenScores = floatArrayOf(),
            wholeScreenExplicitScore = 0f,
            wholeScreenSemiNudeScore = 0f,
            verification = null,
            verifierBox = null,
            frameWidth = bitmap?.width ?: 0,
            frameHeight = bitmap?.height ?: 0,
            deduplicated = deduplicated,
            falsePositiveSuppressed = falsePositiveSuppressed,
            thresholds = thresholds,
            requireVerifierForStrongExplicit = requireVerifierForStrongExplicit
        )
    }
}

private object FrameHasher {
    fun averageHash(bitmap: Bitmap): Long {
        // Difference hash retains edges from a post even when most of the screen is white UI.
        // The previous average hash collapsed many visibly different X frames to all-one bits,
        // which incorrectly reused an earlier SAFE result.
        val small = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        val pixels = IntArray(72)
        small.getPixels(pixels, 0, 9, 0, 0, 9, 8)
        if (small !== bitmap) small.recycle()
        var hash = 0L
        var bit = 0
        for (row in 0 until 8) {
            for (column in 0 until 8) {
                val left = luminance(pixels[row * 9 + column])
                val right = luminance(pixels[row * 9 + column + 1])
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    private fun luminance(color: Int): Int =
        (((color shr 16) and 0xff) * 299 +
            ((color shr 8) and 0xff) * 587 +
            (color and 0xff) * 114) / 1_000

    fun hammingDistance(first: Long, second: Long): Int =
        java.lang.Long.bitCount(first xor second)
}
