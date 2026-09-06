package com.example.sinshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFreshnessPolicyTest {
    @Test
    fun packageOrWindowChangeAlwaysDiscardsResult() {
        assertTrue(
            ScanFreshnessPolicy.shouldDiscard(
                contextStale = true,
                eventArrivedAfterCapture = false,
                verdict = ContentVerdict.EXPLICIT
            )
        )
    }

    @Test
    fun noisyContentEventRetriesLateSafeResult() {
        assertTrue(
            ScanFreshnessPolicy.shouldDiscard(
                contextStale = false,
                eventArrivedAfterCapture = true,
                verdict = ContentVerdict.SAFE
            )
        )
    }

    @Test
    fun noisyContentEventCannotSuppressUnsafeResult() {
        assertFalse(
            ScanFreshnessPolicy.shouldDiscard(
                contextStale = false,
                eventArrivedAfterCapture = true,
                verdict = ContentVerdict.EXPLICIT
            )
        )
        assertFalse(
            ScanFreshnessPolicy.shouldDiscard(
                contextStale = false,
                eventArrivedAfterCapture = true,
                verdict = ContentVerdict.SEMI_NUDE
            )
        )
    }
}
