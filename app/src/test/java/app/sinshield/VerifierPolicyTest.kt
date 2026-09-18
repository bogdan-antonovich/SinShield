package app.sinshield

import org.junit.Assert.assertEquals
import org.junit.Test

class VerifierPolicyTest {
    @Test
    fun savedFalsePositiveVerifierScoresAreVetoed() {
        val explicit = StageOneResult(
            ContentVerdict.EXPLICIT,
            ContentVerdict.EXPLICIT,
            listOf("Porn")
        )

        listOf(
            Triple(explicit, 0.05037019f, ProtectionLevel.BALANCED.thresholds.verifier),
            Triple(explicit, 0.7570694f, ProtectionLevel.BALANCED.thresholds.verifier),
            Triple(explicit, 0.64651585f, ProtectionLevel.RELAXED.thresholds.verifier),
            Triple(explicit, 0.16970333f, ProtectionLevel.RELAXED.thresholds.verifier)
        ).forEach { (candidate, verifierScore, threshold) ->
            assertEquals(
                ContentVerdict.SAFE,
                VerifierPolicy.finalVerdict(
                    candidate,
                    verifierScore,
                    threshold,
                    requireVerifierForStrongExplicit = true
                )
            )
        }
    }

    @Test
    fun safeCandidateNeverBlocks() {
        val candidate = StageOneResult(
            ContentVerdict.SAFE,
            ContentVerdict.SAFE,
            emptyList()
        )

        assertEquals(
            ContentVerdict.SAFE,
            VerifierPolicy.finalVerdict(candidate, 0.99f)
        )
    }

    @Test
    fun missingVerifierDoesNotDiscardDecisiveExplicitWhenApprovalIsDisabled() {
        val candidate = StageOneResult(
            ContentVerdict.EXPLICIT,
            ContentVerdict.EXPLICIT,
            listOf("Porn")
        )

        assertEquals(
            ContentVerdict.EXPLICIT,
            VerifierPolicy.finalVerdict(
                candidate,
                nsfwScore = null,
                requireVerifierForStrongExplicit = false
            )
        )
    }

    @Test
    fun optionalStrictVerifierModeFailsOpenWhenVerifierIsMissing() {
        val candidate = StageOneResult(
            ContentVerdict.EXPLICIT,
            ContentVerdict.EXPLICIT,
            listOf("Porn")
        )

        assertEquals(
            ContentVerdict.SAFE,
            VerifierPolicy.finalVerdict(
                candidate,
                nsfwScore = null,
                requireVerifierForStrongExplicit = true
            )
        )
    }

    @Test
    fun verifierNeverVetoesFirmSemiNudeCandidate() {
        val candidate = StageOneResult(
            ContentVerdict.SEMI_NUDE,
            ContentVerdict.SEMI_NUDE,
            listOf("Sexy")
        )

        // The binary porn verifier cannot recognize suggestive content as NSFW, so a low score is
        // not evidence against a confident Sexy detection and must never downgrade it to SAFE.
        assertEquals(
            ContentVerdict.SEMI_NUDE,
            VerifierPolicy.finalVerdict(candidate, VerifierPolicy.CONFIRM_THRESHOLD - 0.001f)
        )
        assertEquals(
            ContentVerdict.SEMI_NUDE,
            VerifierPolicy.finalVerdict(candidate, 0.0f)
        )
    }

    @Test
    fun strongVerifierPromotesFirmSemiNudeToExplicit() {
        val candidate = StageOneResult(
            ContentVerdict.SEMI_NUDE,
            ContentVerdict.SEMI_NUDE,
            listOf("Sexy")
        )

        assertEquals(
            ContentVerdict.EXPLICIT,
            VerifierPolicy.finalVerdict(
                candidate,
                nsfwScore = 0.96f,
                confirmThreshold = ProtectionLevel.RELAXED.thresholds.verifier
            )
        )
    }

    @Test
    fun verifierPromotesScaleDilutedSemiNudeCandidateToExplicit() {
        val candidate = StageOneResult(
            ContentVerdict.SUSPICIOUS,
            ContentVerdict.SEMI_NUDE,
            listOf("Sexy")
        )

        assertEquals(
            ContentVerdict.EXPLICIT,
            VerifierPolicy.finalVerdict(
                candidate,
                nsfwScore = 0.96f,
                confirmThreshold = ProtectionLevel.RELAXED.thresholds.verifier
            )
        )
    }

    @Test
    fun explicitCandidateAtRecommendedVerifierThresholdBlocks() {
        val candidate = StageOneResult(
            ContentVerdict.EXPLICIT,
            ContentVerdict.EXPLICIT,
            listOf("Porn")
        )

        assertEquals(
            ContentVerdict.EXPLICIT,
            VerifierPolicy.finalVerdict(candidate, DetectionThresholds().verifier)
        )
    }

    @Test
    fun verifierNsfwMajorityDoesNotMeetRecommendedThreshold() {
        val candidate = StageOneResult(
            ContentVerdict.EXPLICIT,
            ContentVerdict.EXPLICIT,
            listOf("Porn")
        )

        assertEquals(
            ContentVerdict.SAFE,
            VerifierPolicy.finalVerdict(candidate, 0.61f)
        )
    }

    @Test
    fun selectedProtectionLevelMakesVerifierDisagreementSafe() {
        val candidate = StageOneResult(
            ContentVerdict.EXPLICIT,
            ContentVerdict.EXPLICIT,
            listOf("Porn")
        )

        assertEquals(
            ContentVerdict.SAFE,
            VerifierPolicy.finalVerdict(
                candidate,
                nsfwScore = 0.80f,
                confirmThreshold = ProtectionLevel.RELAXED.thresholds.verifier,
                requireVerifierForStrongExplicit = true
            )
        )
    }

    @Test
    fun optionalStrictVerifierModeVetoesStrongExplicitCandidate() {
        val candidate = StageOneResult(
            ContentVerdict.EXPLICIT,
            ContentVerdict.EXPLICIT,
            listOf("Porn")
        )

        assertEquals(
            ContentVerdict.SAFE,
            VerifierPolicy.finalVerdict(
                candidate,
                0.12f,
                requireVerifierForStrongExplicit = true
            )
        )
    }

    @Test
    fun verifierDisagreementStillRejectsBorderlineExplicitCandidate() {
        val candidate = StageOneResult(
            ContentVerdict.SUSPICIOUS,
            ContentVerdict.EXPLICIT,
            listOf("Porn")
        )

        assertEquals(
            ContentVerdict.SAFE,
            VerifierPolicy.finalVerdict(candidate, 0.12f)
        )
    }

    @Test
    fun verifierDisagreementVetoesSuspiciousSemiNudeCandidate() {
        val candidate = StageOneResult(
            ContentVerdict.SUSPICIOUS,
            ContentVerdict.SEMI_NUDE,
            listOf("Sexy")
        )

        assertEquals(
            ContentVerdict.SAFE,
            VerifierPolicy.finalVerdict(candidate, 0.49f)
        )
    }
}
