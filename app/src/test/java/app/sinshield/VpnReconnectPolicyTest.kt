package app.sinshield

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnReconnectPolicyTest {
    @Test
    fun retriesQuicklyThenCapsAtOneMinute() {
        assertEquals(1_000L, VpnReconnectPolicy.delayForAttempt(0))
        assertEquals(2_000L, VpnReconnectPolicy.delayForAttempt(1))
        assertEquals(5_000L, VpnReconnectPolicy.delayForAttempt(2))
        assertEquals(15_000L, VpnReconnectPolicy.delayForAttempt(3))
        assertEquals(60_000L, VpnReconnectPolicy.delayForAttempt(4))
        assertEquals(60_000L, VpnReconnectPolicy.delayForAttempt(100))
    }

    @Test
    fun defensiveNegativeAttemptUsesFirstDelay() {
        assertEquals(1_000L, VpnReconnectPolicy.delayForAttempt(-1))
    }
}
