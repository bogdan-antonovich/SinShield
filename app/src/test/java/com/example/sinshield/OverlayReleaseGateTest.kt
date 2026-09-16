package com.example.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReleaseGateTest {
    @Test
    fun requiresThreeConsecutiveSafeAnalyses() {
        val gate = OverlayReleaseGate(requiredSafeAnalyses = 3)

        assertFalse(gate.recordSafeAnalysis())
        assertFalse(gate.recordSafeAnalysis())
        assertTrue(gate.recordSafeAnalysis())
        assertEquals(3, gate.consecutiveSafeAnalyses)
    }

    @Test
    fun unsafeAnalysisResetStartsTheStreakOver() {
        val gate = OverlayReleaseGate(requiredSafeAnalyses = 3)

        assertFalse(gate.recordSafeAnalysis())
        assertFalse(gate.recordSafeAnalysis())
        gate.reset()

        assertFalse(gate.recordSafeAnalysis())
        assertEquals(1, gate.consecutiveSafeAnalyses)
    }
}
