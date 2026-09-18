package app.sinshield

import android.content.Context
import androidx.core.content.edit

internal object ProtectionPreferences {
    private const val PREFERENCES = "protection_preferences"
    private const val STRICT_MODE = "strict_mode"
    private const val PROTECTION_LEVEL = "protection_level"
    private const val CUSTOM_THRESHOLDS = "custom_thresholds"
    private const val EXPLICIT_THRESHOLD = "explicit_threshold"
    private const val SEMI_NUDE_THRESHOLD = "semi_nude_threshold"
    private const val SUSPICIOUS_EXPLICIT_THRESHOLD = "suspicious_explicit_threshold"
    private const val SUSPICIOUS_SEMI_NUDE_THRESHOLD = "suspicious_semi_nude_threshold"
    private const val VERIFIER_THRESHOLD = "verifier_threshold"
    private const val REQUIRE_VERIFIER_FOR_STRONG_EXPLICIT =
        "require_verifier_for_strong_explicit"

    fun strictMode(context: Context): Boolean = preferences(context).getBoolean(STRICT_MODE, false)

    fun setStrictMode(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean(STRICT_MODE, enabled) }
    }

    fun protectionLevel(context: Context): ProtectionLevel {
        val saved = preferences(context).getString(PROTECTION_LEVEL, null)
        return ProtectionLevel.entries.firstOrNull { it.name == saved } ?: ProtectionLevel.RECOMMENDED
    }

    fun setProtectionLevel(context: Context, level: ProtectionLevel) {
        preferences(context).edit {
            putString(PROTECTION_LEVEL, level.name)
            putBoolean(CUSTOM_THRESHOLDS, false)
        }
    }

    fun detectionSettings(context: Context): DetectionSettings {
        val preferences = preferences(context)
        val preset = protectionLevel(context).thresholds
        val thresholds = if (preferences.getBoolean(CUSTOM_THRESHOLDS, false)) {
            DetectionThresholds(
                explicit = preferences.getFloat(EXPLICIT_THRESHOLD, preset.explicit),
                semiNude = preferences.getFloat(SEMI_NUDE_THRESHOLD, preset.semiNude),
                suspiciousExplicit = preferences.getFloat(
                    SUSPICIOUS_EXPLICIT_THRESHOLD,
                    preset.suspiciousExplicit
                ),
                suspiciousSemiNude = preferences.getFloat(
                    SUSPICIOUS_SEMI_NUDE_THRESHOLD,
                    preset.suspiciousSemiNude
                ),
                verifier = preferences.getFloat(VERIFIER_THRESHOLD, preset.verifier)
            ).normalized()
        } else {
            preset
        }
        return DetectionSettings(
            thresholds = thresholds,
            requireVerifierForStrongExplicit = preferences.getBoolean(
                REQUIRE_VERIFIER_FOR_STRONG_EXPLICIT,
                true
            ),
            blockSuggestive = preferences.getBoolean(STRICT_MODE, false)
        )
    }

    fun hasCustomThresholds(context: Context): Boolean =
        preferences(context).getBoolean(CUSTOM_THRESHOLDS, false)

    fun setCustomThresholds(context: Context, thresholds: DetectionThresholds) {
        val safe = thresholds.normalized()
        preferences(context).edit {
            putBoolean(CUSTOM_THRESHOLDS, true)
            putFloat(EXPLICIT_THRESHOLD, safe.explicit)
            putFloat(SEMI_NUDE_THRESHOLD, safe.semiNude)
            putFloat(SUSPICIOUS_EXPLICIT_THRESHOLD, safe.suspiciousExplicit)
            putFloat(SUSPICIOUS_SEMI_NUDE_THRESHOLD, safe.suspiciousSemiNude)
            putFloat(VERIFIER_THRESHOLD, safe.verifier)
        }
    }

    fun resetCustomThresholds(context: Context) {
        preferences(context).edit { putBoolean(CUSTOM_THRESHOLDS, false) }
    }

    /** Restores every advanced tuning control to the app's current recommended defaults. */
    fun resetDetectionTuningToRecommended(context: Context) {
        preferences(context).edit {
            putString(PROTECTION_LEVEL, ProtectionLevel.RECOMMENDED.name)
            putBoolean(CUSTOM_THRESHOLDS, false)
            putBoolean(REQUIRE_VERIFIER_FOR_STRONG_EXPLICIT, true)
        }
    }

    fun setRequireVerifierForStrongExplicit(context: Context, required: Boolean) {
        preferences(context).edit { putBoolean(REQUIRE_VERIFIER_FOR_STRONG_EXPLICIT, required) }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

internal data class DetectionSettings(
    val thresholds: DetectionThresholds,
    val requireVerifierForStrongExplicit: Boolean,
    val blockSuggestive: Boolean
)

internal enum class ProtectionLevel(
    val displayName: String,
    val description: String,
    val thresholds: DetectionThresholds
) {
    RELAXED(
        displayName = "Relaxed",
        description = "Blocks only very confident detections",
        // Headline dials shown in the UI: Explicit 95% · Suggestive 96% · Second model 90%.
        // The two suspicious values are the lower "watch the next frames" band that drives
        // provisional covers; they are lifted in step with the headline dials so raising the
        // ceiling does not widen the uncertain band and produce more flicker.
        thresholds = DetectionThresholds(
            explicit = 0.95f,
            semiNude = 0.96f,
            suspiciousExplicit = 0.80f,
            suspiciousSemiNude = 0.82f,
            verifier = 0.90f
        )
    ),
    BALANCED(
        displayName = "Balanced",
        description = "Fewer false alarms with strong protection",
        thresholds = DetectionThresholds(
            explicit = 0.70f,
            semiNude = 0.80f,
            suspiciousExplicit = 0.50f,
            suspiciousSemiNude = 0.60f,
            // Leave a real margin above the verifier's binary boundary. A saved false positive
            // crossed the former 0.75 threshold by only 0.007.
            verifier = 0.80f
        )
    ),
    HIGH(
        displayName = "High",
        description = "Catches more content and may make mistakes",
        thresholds = DetectionThresholds(
            explicit = 0.55f,
            semiNude = 0.65f,
            suspiciousExplicit = 0.35f,
            suspiciousSemiNude = 0.40f,
            verifier = 0.65f
        )
    ),
    MAXIMUM(
        displayName = "Maximum",
        description = "Most sensitive; catches more content and may make mistakes",
        thresholds = DetectionThresholds(
            explicit = 0.40f,
            semiNude = 0.50f,
            suspiciousExplicit = 0.25f,
            suspiciousSemiNude = 0.20f,
            verifier = 0.50f
        )
    ),
    RECOMMENDED(
        displayName = "Recommended",
        description = "Default detection tuning",
        thresholds = DetectionThresholds()
    )
}
