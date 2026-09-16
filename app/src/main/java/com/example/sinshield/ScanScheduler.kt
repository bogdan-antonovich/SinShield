package com.example.sinshield

import kotlin.math.max

/**
 * Owns the scan state machine that used to live inline in [ShieldAccessibilityService]: at most one
 * capture/inference flight at a time, the generation counter that lets a late result be discarded,
 * the trailing-scan debounce, and the adaptive interval that backs off while a feed stays safe.
 *
 * Every member is touched only from the main thread, exactly as before. The Android surface is
 * injected so the timing rules can be unit-tested without a device: [scheduler] wraps a Handler,
 * [now] wraps SystemClock.elapsedRealtime, [canScan] reports whether a monitored app is foreground
 * on a screenshot-capable OS, and [onBeginScan] starts a capture.
 */
internal class ScanScheduler(
    private val scheduler: Scheduler,
    private val now: () -> Long,
    private val canScan: () -> Boolean,
    private val onBeginScan: () -> Unit,
    private val activeIntervalMs: Long = ACTIVE_SCAN_INTERVAL_MS,
    private val stableIntervalMs: Long = STABLE_SCAN_INTERVAL_MS,
    private val backoffStepMs: Long = SCAN_BACKOFF_STEP_MS,
    private val postCompletionRestMs: Long = POST_COMPLETION_REST_MS,
    private val urgentCompletionRestMs: Long = URGENT_COMPLETION_REST_MS
) {
    /** Abstraction over Handler.postDelayed/removeCallbacks so the schedule can be faked in tests. */
    interface Scheduler {
        /** Runs [action] after [delayMs]; returns an opaque handle accepted by [cancel]. */
        fun schedule(delayMs: Long, action: () -> Unit): Any
        fun cancel(handle: Any)
    }

    private var scanInFlight = false
    private var latestFramePending = false
    private var nextGeneration = 0L
    private var latestCompletedGeneration = 0L
    private var lastCaptureAt = 0L
    private var nextScanNotBeforeAt = 0L
    private var scheduledScan: Any? = null
    private var scheduledScanAt = Long.MAX_VALUE
    private var intervalMs = activeIntervalMs

    val inFlight: Boolean get() = scanInFlight
    val framePending: Boolean get() = latestFramePending
    val currentIntervalMs: Long get() = intervalMs

    fun markFramePending() {
        latestFramePending = true
    }

    fun clearFramePending() {
        latestFramePending = false
    }

    fun clearInFlight() {
        scanInFlight = false
    }

    fun resetToActiveInterval() {
        intervalMs = activeIntervalMs
    }

    fun clampToStable() {
        intervalMs = stableIntervalMs
    }

    fun backOff() {
        intervalMs = minOf(stableIntervalMs, intervalMs + backoffStepMs)
    }

    /**
     * Requests a scan after [delayMs]. A no-op when nothing is scannable. While a scan is in flight
     * this only records that the latest frame still needs checking; the completing flight picks it
     * up. Rapid events during cooldown collapse into a single trailing callback, so a final frame is
     * still captured after a one-off scroll.
     */
    fun requestScan(delayMs: Long) {
        if (!canScan()) return
        if (scanInFlight) {
            latestFramePending = true
            return
        }

        val current = now()
        val cooldownEnd = max(lastCaptureAt + intervalMs, nextScanNotBeforeAt)
        val desiredAt = max(current + delayMs, cooldownEnd)

        // Keep the earliest trailing scan. Rapid events during cooldown collapse into this one
        // callback, so a final frame is still captured after a one-off scroll.
        if (scheduledScan != null && scheduledScanAt <= desiredAt) return
        cancelScheduledScan()
        val handle = scheduler.schedule(max(0L, desiredAt - current)) {
            scheduledScan = null
            scheduledScanAt = Long.MAX_VALUE
            onBeginScan()
        }
        scheduledScan = handle
        scheduledScanAt = desiredAt
    }

    fun cancelScheduledScan() {
        scheduledScan?.let(scheduler::cancel)
        scheduledScan = null
        scheduledScanAt = Long.MAX_VALUE
    }

    /**
     * Begins a new flight and returns its generation, or defers past the remaining cooldown and
     * returns null. The caller stamps the returned generation onto its ScanContext so a late result
     * can be matched against [recordCompletion].
     */
    fun startFlightOrDefer(): Long? {
        val current = now()
        val cooldownRemaining = max(lastCaptureAt + intervalMs, nextScanNotBeforeAt) - current
        if (cooldownRemaining > 0) {
            requestScan(cooldownRemaining)
            return null
        }
        scanInFlight = true
        latestFramePending = false
        lastCaptureAt = current
        return ++nextGeneration
    }

    /**
     * Records that [generation] finished and returns whether a newer scan has already completed —
     * i.e. this result is superseded and should be discarded.
     */
    fun recordCompletion(generation: Long): Boolean {
        latestCompletedGeneration = max(latestCompletedGeneration, generation)
        return generation < latestCompletedGeneration
    }

    /**
     * Schedules the next scan once one completes: a fast follow-up when [forceFast] or a frame is
     * pending, otherwise honoring the current adaptive interval. Callers must confirm a monitored
     * app is still foreground before calling.
     */
    fun scheduleAfterCompletion(forceFast: Boolean) {
        enforceCompletionRest(forceFast)
        if (forceFast || latestFramePending) {
            latestFramePending = false
            intervalMs = activeIntervalMs
            requestScan(activeIntervalMs)
        } else {
            requestScan(intervalMs)
        }
    }

    /**
     * Keeps events arriving immediately after a long inference from bypassing the recovery gap.
     * Cooldown used to be measured only from capture start, so any scan longer than the interval
     * effectively had no cooldown and a busy feed kept the CPU saturated indefinitely.
     */
    fun enforceCompletionRest(urgent: Boolean) {
        val rest = if (urgent) urgentCompletionRestMs else postCompletionRestMs
        nextScanNotBeforeAt = max(nextScanNotBeforeAt, now() + rest)
    }

    /**
     * Puts a generation barrier in front of any in-flight scan so an older, now-irrelevant result
     * cannot act on a screen the user has already navigated away from.
     */
    fun invalidateInFlightResults() {
        latestCompletedGeneration = max(latestCompletedGeneration, ++nextGeneration)
        if (scanInFlight) latestFramePending = true
    }

    companion object {
        private const val ACTIVE_SCAN_INTERVAL_MS = 500L
        private const val STABLE_SCAN_INTERVAL_MS = 1_500L
        private const val SCAN_BACKOFF_STEP_MS = 250L
        private const val POST_COMPLETION_REST_MS = 1_250L
        private const val URGENT_COMPLETION_REST_MS = 250L
    }
}
