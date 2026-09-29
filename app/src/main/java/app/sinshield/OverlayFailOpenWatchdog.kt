package app.sinshield

import android.os.Handler
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Makes every full-screen overlay fail open if the service main thread stops processing work.
 *
 * WindowManager removes this process's windows when the process dies. A small independent worker
 * therefore watches a heartbeat posted on the main looper while a blocking window exists. If the
 * looper stalls long enough that the overlay controls cannot respond, terminating this process is
 * safer than leaving an opaque accessibility window on screen indefinitely. Uptime is used so a
 * sleeping device does not look like a stalled main thread immediately after it wakes.
 */
internal class OverlayFailOpenWatchdog(
    private val mainHandler: Handler,
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val terminateProcess: () -> Unit = {
        Process.killProcess(Process.myPid())
    },
    private val executor: ScheduledExecutorService =
        newBackgroundScheduledExecutor("SinShield-OverlaySafety")
) {
    private val active = AtomicBoolean(false)
    private val lastMainHeartbeat = AtomicLong(0L)
    @Volatile private var watchdogFuture: ScheduledFuture<*>? = null
    private val mainHeartbeat = object : Runnable {
        override fun run() {
            if (!active.get()) return
            lastMainHeartbeat.set(clock())
            mainHandler.postDelayed(this, MAIN_HEARTBEAT_INTERVAL_MS)
        }
    }

    fun setBlockingOverlayActive(isActive: Boolean) {
        if (isActive) {
            lastMainHeartbeat.set(clock())
            if (active.compareAndSet(false, true)) {
                mainHandler.post(mainHeartbeat)
                watchdogFuture = executor.scheduleWithFixedDelay(
                    ::checkMainThread,
                    WATCHDOG_CHECK_INTERVAL_MS,
                    WATCHDOG_CHECK_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
                )
            }
        } else if (active.compareAndSet(true, false)) {
            mainHandler.removeCallbacks(mainHeartbeat)
            watchdogFuture?.cancel(false)
            watchdogFuture = null
        }
    }

    fun terminateNow(reason: String) {
        Log.e(TAG, "Fail-open process termination: $reason")
        active.set(false)
        watchdogFuture?.cancel(false)
        watchdogFuture = null
        terminateProcess()
    }

    fun shutdown() {
        active.set(false)
        mainHandler.removeCallbacks(mainHeartbeat)
        watchdogFuture?.cancel(false)
        watchdogFuture = null
        executor.shutdownNow()
    }

    private fun checkMainThread() {
        val now = clock()
        val observedHeartbeat = lastMainHeartbeat.get()
        if (!OverlayFailOpenPolicy.shouldTerminate(
                overlayActive = active.get(),
                lastMainHeartbeat = observedHeartbeat,
                now = now,
                timeoutMs = MAIN_THREAD_STALL_TIMEOUT_MS
            )
        ) {
            return
        }
        // Do not terminate if the main thread recovered or the overlay was removed between the
        // policy snapshot above and this worker reaching the fail-open action.
        if (lastMainHeartbeat.get() != observedHeartbeat || !active.compareAndSet(true, false)) {
            return
        }
        terminateNow(
            "blocking overlay remained active while the main thread was unresponsive for " +
                "at least ${MAIN_THREAD_STALL_TIMEOUT_MS}ms"
        )
    }

    companion object {
        private const val TAG = "SinShield"
        internal const val MAIN_THREAD_STALL_TIMEOUT_MS = 10_000L
        private const val MAIN_HEARTBEAT_INTERVAL_MS = 1_000L
        private const val WATCHDOG_CHECK_INTERVAL_MS = 1_000L
    }
}

internal object OverlayFailOpenPolicy {
    fun shouldTerminate(
        overlayActive: Boolean,
        lastMainHeartbeat: Long,
        now: Long,
        timeoutMs: Long
    ): Boolean = overlayActive &&
        now >= lastMainHeartbeat &&
        now - lastMainHeartbeat >= timeoutMs
}
