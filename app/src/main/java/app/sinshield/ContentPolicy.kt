package app.sinshield

internal enum class ContentVerdict {
    SAFE,
    SUSPICIOUS,
    SEMI_NUDE,
    EXPLICIT
}

internal data class StageOneResult(
    val verdict: ContentVerdict,
    val suspectedFinalVerdict: ContentVerdict,
    val causes: List<String>
)

internal data class DetectionThresholds(
    val explicit: Float = 0.93f,
    val semiNude: Float = 0.92f,
    val suspiciousExplicit: Float = 0.75f,
    val suspiciousSemiNude: Float = 0.55f,
    val verifier: Float = 0.86f
) {
    /** Keeps persisted tuning values finite, useful, and internally ordered. */
    fun normalized(): DetectionThresholds {
        val safeExplicit = explicit.finiteOr(0.93f).coerceIn(0.10f, 0.99f)
        val safeSemiNude = semiNude.finiteOr(0.92f).coerceIn(0.10f, 0.99f)
        return copy(
            explicit = safeExplicit,
            semiNude = safeSemiNude,
            suspiciousExplicit = suspiciousExplicit.finiteOr(0.75f)
                .coerceIn(0.05f, safeExplicit),
            suspiciousSemiNude = suspiciousSemiNude.finiteOr(0.55f)
                .coerceIn(0.05f, safeSemiNude),
            verifier = verifier.finiteOr(0.86f).coerceIn(0.50f, 0.99f)
        )
    }
}

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

/** Converts the five MobileNet outputs into the policy used by both app and service. */
internal object ContentPolicy {
    const val EXPLICIT_THRESHOLD = 0.93f
    const val SEMI_NUDE_THRESHOLD = 0.92f
    const val SUSPICIOUS_EXPLICIT_THRESHOLD = 0.75f
    // Full-screen social feeds dilute the model's Sexy score with surrounding text/navigation.
    // This lower value only enters the multi-frame confirmation path; it never blocks immediately.
    const val SUSPICIOUS_SEMI_NUDE_THRESHOLD = 0.55f

    fun evaluate(
        scores: FloatArray,
        thresholds: DetectionThresholds = DetectionThresholds()
    ): StageOneResult {
        require(scores.size >= 5) { "Expected five classifier scores" }
        val hentai = scores[1]
        val porn = scores[3]
        val sexy = scores[4]
        val causes = buildList {
            if (hentai >= thresholds.suspiciousExplicit) add("Hentai")
            if (porn >= thresholds.suspiciousExplicit) add("Porn")
            if (sexy >= thresholds.suspiciousSemiNude) add("Sexy")
        }
        return when {
            hentai >= thresholds.explicit || porn >= thresholds.explicit ->
                StageOneResult(ContentVerdict.EXPLICIT, ContentVerdict.EXPLICIT, causes)
            sexy >= thresholds.semiNude ->
                StageOneResult(ContentVerdict.SEMI_NUDE, ContentVerdict.SEMI_NUDE, causes)
            hentai >= thresholds.suspiciousExplicit || porn >= thresholds.suspiciousExplicit ->
                StageOneResult(ContentVerdict.SUSPICIOUS, ContentVerdict.EXPLICIT, causes)
            sexy >= thresholds.suspiciousSemiNude ->
                StageOneResult(ContentVerdict.SUSPICIOUS, ContentVerdict.SEMI_NUDE, causes)
            else -> StageOneResult(ContentVerdict.SAFE, ContentVerdict.SAFE, emptyList())
        }
    }

    /** Combines whole-screen and localized scores without letting either stage weaken the other. */
    fun combine(
        wholeScreen: StageOneResult,
        localizedExplicitScore: Float,
        localizedSemiNudeScore: Float,
        thresholds: DetectionThresholds = DetectionThresholds()
    ): StageOneResult {
        val wholeScreenExplicit = wholeScreen.verdict == ContentVerdict.EXPLICIT ||
            (wholeScreen.verdict == ContentVerdict.SUSPICIOUS &&
                wholeScreen.suspectedFinalVerdict == ContentVerdict.EXPLICIT)
        val wholeScreenSemiNude = wholeScreen.verdict == ContentVerdict.SEMI_NUDE ||
            (wholeScreen.verdict == ContentVerdict.SUSPICIOUS &&
                wholeScreen.suspectedFinalVerdict == ContentVerdict.SEMI_NUDE)

        return when {
            wholeScreen.verdict == ContentVerdict.EXPLICIT ||
                localizedExplicitScore >= thresholds.explicit ->
                wholeScreen.copy(
                    verdict = ContentVerdict.EXPLICIT,
                    suspectedFinalVerdict = ContentVerdict.EXPLICIT
                )
            // A confirmed Sexy score outranks a merely suspicious Porn score, so it is tested first.
            // The other order let a 0.40-shy explicit hint downgrade a 0.91 semi-nude certainty to
            // SUSPICIOUS/EXPLICIT, which hands the binary verifier a veto it must never have here.
            wholeScreen.verdict == ContentVerdict.SEMI_NUDE ||
                localizedSemiNudeScore >= thresholds.semiNude ->
                wholeScreen.copy(
                    verdict = ContentVerdict.SEMI_NUDE,
                    suspectedFinalVerdict = ContentVerdict.SEMI_NUDE
                )
            wholeScreenExplicit || localizedExplicitScore >= thresholds.suspiciousExplicit ->
                wholeScreen.copy(
                    verdict = ContentVerdict.SUSPICIOUS,
                    suspectedFinalVerdict = ContentVerdict.EXPLICIT
                )
            wholeScreenSemiNude || localizedSemiNudeScore >= thresholds.suspiciousSemiNude ->
                wholeScreen.copy(
                    verdict = ContentVerdict.SUSPICIOUS,
                    suspectedFinalVerdict = ContentVerdict.SEMI_NUDE
                )
            else -> wholeScreen
        }
    }
}

internal object VerifierPolicy {
    // Marqo's documented inference path selects the largest of the NSFW and SFW softmax outputs,
    // making 0.50 the binary decision boundary. Protection levels can deliberately require a
    // larger margin before the verifier is allowed to confirm a block.
    const val CONFIRM_THRESHOLD = 0.50f

    fun finalVerdict(
        candidate: StageOneResult,
        nsfwScore: Float?,
        confirmThreshold: Float = DetectionThresholds().verifier,
        requireVerifierForStrongExplicit: Boolean = true
    ): ContentVerdict {
        if (candidate.verdict == ContentVerdict.SAFE) return ContentVerdict.SAFE

        val verifierAgrees = nsfwScore != null && nsfwScore.isFinite() && nsfwScore >= confirmThreshold

        if (candidate.verdict == ContentVerdict.SEMI_NUDE) {
            // A firm Sexy detection is trusted on the primary detector alone. The verifier is a
            // binary porn-vs-SFW model: suggestive-but-not-explicit imagery sits in its uncertain
            // middle (~0.65-0.9), so a low score is consistent with Sexy, never evidence against
            // it, and must not veto a confident detection to SAFE. A high score still promotes the
            // cover to the stricter explicit policy.
            return if (verifierAgrees) ContentVerdict.EXPLICIT else ContentVerdict.SEMI_NUDE
        }

        // A decisive Porn/Hentai result from the primary model can be sufficient when verifier
        // approval is disabled. Recommended tuning keeps approval enabled to reduce false alarms;
        // users can opt out when they value recall over false-positive avoidance.
        if (candidate.verdict == ContentVerdict.EXPLICIT &&
            !requireVerifierForStrongExplicit
        ) {
            return ContentVerdict.EXPLICIT
        }

        // Merely SUSPICIOUS candidates, and firm candidates in verifier-required mode, need the
        // independent model to agree before they can block.
        if (!verifierAgrees) return ContentVerdict.SAFE
        if (candidate.suspectedFinalVerdict == ContentVerdict.SEMI_NUDE) {
            // The verifier is binary, so threshold-strength NSFW evidence promotes a suggestive
            // candidate to the safer explicit policy rather than pretending it can distinguish
            // the two classes.
            return ContentVerdict.EXPLICIT
        }
        return if (candidate.verdict == ContentVerdict.SUSPICIOUS) {
            candidate.suspectedFinalVerdict
        } else {
            candidate.verdict
        }
    }
}
