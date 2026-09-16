package com.example.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPolicyTest {
    @Test
    fun persistedThresholdsAreClampedAndCandidatesCannotExceedFinalThresholds() {
        val normalized = DetectionThresholds(
            explicit = 0.30f,
            semiNude = 0.45f,
            suspiciousExplicit = 0.90f,
            suspiciousSemiNude = Float.NaN,
            verifier = 0.10f
        ).normalized()

        assertEquals(0.30f, normalized.explicit)
        assertEquals(0.45f, normalized.semiNude)
        assertEquals(0.30f, normalized.suspiciousExplicit)
        assertEquals(0.45f, normalized.suspiciousSemiNude)
        assertEquals(0.50f, normalized.verifier)
    }

    @Test
    fun defaultThresholdsMatchRecommendedTuning() {
        assertEquals(
            DetectionThresholds(
                explicit = 0.93f,
                semiNude = 0.92f,
                suspiciousExplicit = 0.75f,
                suspiciousSemiNude = 0.55f,
                verifier = 0.86f
            ),
            DetectionThresholds()
        )
    }

    @Test
    fun explicitScoreProducesFinalExplicitVerdict() {
        val result = ContentPolicy.evaluate(floatArrayOf(0f, 0.94f, 0.01f, 0.01f, 0.01f))

        assertEquals(ContentVerdict.EXPLICIT, result.verdict)
        assertTrue("Hentai" in result.causes)
    }

    @Test
    fun semiNudeScoreRemainsDistinctFromExplicit() {
        val result = ContentPolicy.evaluate(floatArrayOf(0f, 0.01f, 0.04f, 0.01f, 0.93f))

        assertEquals(ContentVerdict.SEMI_NUDE, result.verdict)
        assertEquals(ContentVerdict.SEMI_NUDE, result.suspectedFinalVerdict)
    }

    @Test
    fun borderlineExplicitScoreRequiresConfirmation() {
        val result = ContentPolicy.evaluate(floatArrayOf(0f, 0.76f, 0.20f, 0.01f, 0.02f))

        assertEquals(ContentVerdict.SUSPICIOUS, result.verdict)
        assertEquals(ContentVerdict.EXPLICIT, result.suspectedFinalVerdict)
    }

    @Test
    fun ordinarySafeScoresStayInvisible() {
        val result = ContentPolicy.evaluate(floatArrayOf(0.1f, 0.1f, 0.65f, 0.05f, 0.1f))

        assertEquals(ContentVerdict.SAFE, result.verdict)
        assertTrue(result.causes.isEmpty())
    }

    @Test
    fun recommendedLevelDoesNotBlockScoreThatMaximumLevelBlocks() {
        val scores = floatArrayOf(0.05f, 0.62f, 0.2f, 0.05f, 0.08f)

        assertEquals(ContentVerdict.SAFE, ContentPolicy.evaluate(scores).verdict)
        assertEquals(
            ContentVerdict.EXPLICIT,
            ContentPolicy.evaluate(scores, ProtectionLevel.MAXIMUM.thresholds).verdict
        )
    }

    @Test
    fun selectedThresholdsAlsoApplyToLocalizedScores() {
        val safe = ContentPolicy.evaluate(
            floatArrayOf(0.05f, 0.05f, 0.8f, 0.05f, 0.05f),
            ProtectionLevel.BALANCED.thresholds
        )

        val result = ContentPolicy.combine(
            safe,
            localizedExplicitScore = 0.60f,
            localizedSemiNudeScore = 0.1f,
            thresholds = ProtectionLevel.BALANCED.thresholds
        )

        assertEquals(ContentVerdict.SUSPICIOUS, result.verdict)
        assertEquals(ContentVerdict.EXPLICIT, result.suspectedFinalVerdict)
    }

    @Test
    fun localizedPornOverridesSafeWholeScreen() {
        val wholeScreen = ContentPolicy.evaluate(
            floatArrayOf(0.05f, 0.05f, 0.8f, 0.05f, 0.05f)
        )

        val result = ContentPolicy.combine(
            wholeScreen,
            localizedExplicitScore = 0.94f,
            localizedSemiNudeScore = 0.1f
        )

        assertEquals(ContentVerdict.EXPLICIT, result.verdict)
        assertEquals(ContentVerdict.EXPLICIT, result.suspectedFinalVerdict)
    }

    @Test
    fun localizedBorderlinePornRequiresConfirmation() {
        val wholeScreen = ContentPolicy.evaluate(
            floatArrayOf(0.05f, 0.05f, 0.8f, 0.05f, 0.05f)
        )

        val result = ContentPolicy.combine(
            wholeScreen,
            localizedExplicitScore = 0.80f,
            localizedSemiNudeScore = 0.1f
        )

        assertEquals(ContentVerdict.SUSPICIOUS, result.verdict)
        assertEquals(ContentVerdict.EXPLICIT, result.suspectedFinalVerdict)
    }

    @Test
    fun dilutedLocalizedSexyScoreRequiresConfirmation() {
        val wholeScreen = ContentPolicy.evaluate(
            floatArrayOf(0.05f, 0.05f, 0.8f, 0.05f, 0.05f)
        )

        val result = ContentPolicy.combine(
            wholeScreen,
            localizedExplicitScore = 0.1f,
            localizedSemiNudeScore = 0.60f
        )

        assertEquals(ContentVerdict.SUSPICIOUS, result.verdict)
        assertEquals(ContentVerdict.SEMI_NUDE, result.suspectedFinalVerdict)
    }
}
