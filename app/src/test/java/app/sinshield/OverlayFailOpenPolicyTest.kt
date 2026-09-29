package app.sinshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayFailOpenPolicyTest {
    @Test
    fun inactiveOverlayNeverTerminatesProcess() {
        assertFalse(
            OverlayFailOpenPolicy.shouldTerminate(
                overlayActive = false,
                lastMainHeartbeat = 1_000L,
                now = 60_000L,
                timeoutMs = 10_000L
            )
        )
    }

    @Test
    fun responsiveMainThreadKeepsOverlayAlive() {
        assertFalse(
            OverlayFailOpenPolicy.shouldTerminate(
                overlayActive = true,
                lastMainHeartbeat = 9_999L,
                now = 19_998L,
                timeoutMs = 10_000L
            )
        )
    }

    @Test
    fun stalledMainThreadTerminatesAtTimeout() {
        assertTrue(
            OverlayFailOpenPolicy.shouldTerminate(
                overlayActive = true,
                lastMainHeartbeat = 5_000L,
                now = 15_000L,
                timeoutMs = 10_000L
            )
        )
    }

    @Test
    fun overlayShownAtBootStillTerminatesAtTimeout() {
        assertTrue(
            OverlayFailOpenPolicy.shouldTerminate(
                overlayActive = true,
                lastMainHeartbeat = 0L,
                now = 10_000L,
                timeoutMs = 10_000L
            )
        )
    }

    @Test
    fun clockResetDoesNotLookLikeAStall() {
        assertFalse(
            OverlayFailOpenPolicy.shouldTerminate(
                overlayActive = true,
                lastMainHeartbeat = 20_000L,
                now = 100L,
                timeoutMs = 10_000L
            )
        )
    }
}
