package com.example.sinshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanBurstPolicyTest {
    @Test
    fun eventAllowsOnlyConfiguredFollowUps() {
        val policy = ScanBurstPolicy(followUpScans = 2)

        policy.noteEvent()

        assertTrue(policy.shouldSchedule(forceFast = false, framePending = false))
        assertTrue(policy.shouldSchedule(forceFast = false, framePending = false))
        assertFalse(policy.shouldSchedule(forceFast = false, framePending = false))
    }

    @Test
    fun newEventRefillsBurst() {
        val policy = ScanBurstPolicy(followUpScans = 1)
        policy.noteEvent()
        assertTrue(policy.shouldSchedule(forceFast = false, framePending = false))
        assertFalse(policy.shouldSchedule(forceFast = false, framePending = false))

        policy.noteEvent()

        assertTrue(policy.shouldSchedule(forceFast = false, framePending = false))
    }

    @Test
    fun urgentAndPendingFramesDoNotConsumeFollowUpBudget() {
        val policy = ScanBurstPolicy(followUpScans = 1)
        policy.noteEvent()

        assertTrue(policy.shouldSchedule(forceFast = true, framePending = false))
        assertTrue(policy.shouldSchedule(forceFast = false, framePending = true))
        assertTrue(policy.shouldSchedule(forceFast = false, framePending = false))
        assertFalse(policy.shouldSchedule(forceFast = false, framePending = false))
    }
}
