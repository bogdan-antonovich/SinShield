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
        assertEquals(0.20f, normalized.suspiciousSemiNude)
        assertEquals(0.50f, normalized.verifier)
    }

    @Test
    fun explicitScoreProducesFinalExplicitVerdict() {
        val result = ContentPolicy.evaluate(floatArrayOf(0f, 0.41f, 0.4f, 0.1f, 0.09f))

        assertEquals(ContentVerdict.EXPLICIT, result.verdict)
        assertTrue("Hentai" in result.causes)
    }

    @Test
    fun semiNudeScoreRemainsDistinctFromExplicit() {
        val result = ContentPolicy.evaluate(floatArrayOf(0f, 0.05f, 0.3f, 0.1f, 0.51f))

        assertEquals(ContentVerdict.SEMI_NUDE, result.verdict)
        assertEquals(ContentVerdict.SEMI_NUDE, result.suspectedFinalVerdict)
    }

    @Test
    fun borderlineExplicitScoreRequiresConfirmation() {
        val result = ContentPolicy.evaluate(floatArrayOf(0f, 0.26f, 0.5f, 0.1f, 0.14f))

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
    fun relaxedLevelDoesNotBlockScoreThatMaximumLevelBlocks() {
        val scores = floatArrayOf(0.05f, 0.62f, 0.2f, 0.05f, 0.08f)

        assertEquals(ContentVerdict.EXPLICIT, ContentPolicy.evaluate(scores).verdict)
        assertEquals(
            ContentVerdict.SAFE,
            ContentPolicy.evaluate(scores, ProtectionLevel.RELAXED.thresholds).verdict
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
            localizedExplicitScore = 0.72f,
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
            localizedExplicitScore = 0.3f,
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
            localizedSemiNudeScore = 0.268f
        )

        assertEquals(ContentVerdict.SUSPICIOUS, result.verdict)
        assertEquals(ContentVerdict.SEMI_NUDE, result.suspectedFinalVerdict)
    }
}
