package com.example.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSchedulerTest {

    private class FakeScheduler : ScanScheduler.Scheduler {
        var pending: (() -> Unit)? = null
        var lastDelay: Long = -1L
        var cancelCount = 0

        override fun schedule(delayMs: Long, action: () -> Unit): Any {
            lastDelay = delayMs
            pending = action
            return Any()
        }

        override fun cancel(handle: Any) {
            cancelCount++
            pending = null
        }

        fun runPending() {
            val action = pending
            pending = null
            action?.invoke()
        }
    }

    private class Harness(
        val clock: LongArray = longArrayOf(0L),
        val scheduler: FakeScheduler = FakeScheduler(),
        var scannable: Boolean = true,
        var beginCount: Int = 0
    ) {
        val scanScheduler = ScanScheduler(
            scheduler = scheduler,
            now = { clock[0] },
            canScan = { scannable },
            onBeginScan = { beginCount++ },
            activeIntervalMs = 100L,
            stableIntervalMs = 250L,
            backoffStepMs = 100L
        )
    }

    @Test
    fun requestScanDoesNothingWhenNotScannable() {
        val h = Harness(scannable = false)
        h.scanScheduler.requestScan(10L)
        assertNull(h.scheduler.pending)
        assertEquals(-1L, h.scheduler.lastDelay)
    }

    @Test
    fun eventsDuringInFlightScanOnlyMarkFramePending() {
        val h = Harness()
        h.clock[0] = 100L
        assertEquals(1L, h.scanScheduler.startFlightOrDefer())
        assertTrue(h.scanScheduler.inFlight)

        h.scanScheduler.requestScan(10L)
        assertTrue(h.scanScheduler.framePending)
        assertNull(h.scheduler.pending)
    }

    @Test
    fun rapidRequestsDuringCooldownCollapseToOneTrailingScan() {
        val h = Harness()
        h.clock[0] = 0L
        // cooldownEnd = lastCaptureAt(0) + interval(100) = 100, now(0) < 100 -> fire at 100.
        h.scanScheduler.requestScan(10L)
        assertEquals(100L, h.scheduler.lastDelay)

        // A second request that wants the same-or-later time is dropped, not rescheduled.
        h.scanScheduler.requestScan(5L)
        assertEquals(0, h.scheduler.cancelCount)

        h.scheduler.runPending()
        assertEquals(1, h.beginCount)
    }

    @Test
    fun startFlightDefersWithinCooldownAndReturnsNull() {
        val h = Harness()
        h.clock[0] = 0L
        // cooldownRemaining = 0 + 100 - 0 = 100 > 0 -> defer and re-request.
        assertNull(h.scanScheduler.startFlightOrDefer())
        assertFalse(h.scanScheduler.inFlight)
        assertEquals(100L, h.scheduler.lastDelay)
    }

    @Test
    fun startFlightBeyondCooldownAssignsIncreasingGenerations() {
        val h = Harness()
        h.clock[0] = 100L
        assertEquals(1L, h.scanScheduler.startFlightOrDefer())
        h.scanScheduler.clearInFlight()
        h.clock[0] = 500L
        assertEquals(2L, h.scanScheduler.startFlightOrDefer())
    }

    @Test
    fun recordCompletionMarksOnlyTheOlderResultStale() {
        val h = Harness()
        h.clock[0] = 100L
        val first = h.scanScheduler.startFlightOrDefer()!!
        h.scanScheduler.clearInFlight()
        h.clock[0] = 500L
        val second = h.scanScheduler.startFlightOrDefer()!!

        // The newer scan completing first is current; the older one arriving after is superseded.
        assertFalse(h.scanScheduler.recordCompletion(second))
        assertTrue(h.scanScheduler.recordCompletion(first))
    }

    @Test
    fun invalidateInFlightResultsSupersedesTheRunningScan() {
        val h = Harness()
        h.clock[0] = 100L
        val generation = h.scanScheduler.startFlightOrDefer()!!

        h.scanScheduler.invalidateInFlightResults()
        assertTrue(h.scanScheduler.framePending)
        assertTrue(h.scanScheduler.recordCompletion(generation))
    }

    @Test
    fun forceFastCompletionResetsIntervalToActive() {
        val h = Harness()
        h.scanScheduler.clampToStable()
        assertEquals(250L, h.scanScheduler.currentIntervalMs)

        h.clock[0] = 10_000L
        h.scanScheduler.scheduleAfterCompletion(forceFast = true)
        assertEquals(100L, h.scanScheduler.currentIntervalMs)
    }

    @Test
    fun backOffStepsUpButNeverPastStable() {
        val h = Harness()
        h.scanScheduler.resetToActiveInterval()
        h.scanScheduler.backOff()
        assertEquals(200L, h.scanScheduler.currentIntervalMs)
        h.scanScheduler.backOff()
        assertEquals(250L, h.scanScheduler.currentIntervalMs)
        h.scanScheduler.backOff()
        assertEquals(250L, h.scanScheduler.currentIntervalMs)
    }
}
