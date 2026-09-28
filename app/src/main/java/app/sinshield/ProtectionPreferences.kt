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
    private const val PREVIEW_BUBBLE_X = "preview_bubble_x"
    private const val PREVIEW_BUBBLE_Y = "preview_bubble_y"

    fun strictMode(context: Context): Boolean = protectionLevel(context) == ProtectionLevel.STRICT

    fun setStrictMode(context: Context, enabled: Boolean) {
        setProtectionLevel(
            context,
            if (enabled) ProtectionLevel.STRICT else ProtectionLevel.BALANCED
        )
    }

    fun protectionLevel(context: Context): ProtectionLevel {
        val preferences = preferences(context)
        val saved = ProtectionLevel.entries.firstOrNull {
            it.name == preferences.getString(PROTECTION_LEVEL, null)
        }
        return when (saved) {
            ProtectionLevel.RELAXED -> ProtectionLevel.RELAXED
            ProtectionLevel.STRICT, ProtectionLevel.HIGH, ProtectionLevel.MAXIMUM ->
                ProtectionLevel.STRICT
            ProtectionLevel.BALANCED, ProtectionLevel.RECOMMENDED -> ProtectionLevel.BALANCED
            null -> if (preferences.getBoolean(STRICT_MODE, false)) {
                ProtectionLevel.STRICT
            } else {
                ProtectionLevel.BALANCED
            }
        }
    }

    fun setProtectionLevel(context: Context, level: ProtectionLevel) {
        preferences(context).edit {
            putString(PROTECTION_LEVEL, level.name)
            putBoolean(CUSTOM_THRESHOLDS, false)
            putBoolean(STRICT_MODE, level == ProtectionLevel.STRICT)
            putBoolean(REQUIRE_VERIFIER_FOR_STRONG_EXPLICIT, true)
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
            blockSuggestive = protectionLevel(context) == ProtectionLevel.STRICT
        )
    }

    fun hasCustomThresholds(context: Context): Boolean =
        preferences(context).getBoolean(CUSTOM_THRESHOLDS, false)

    fun hasCustomTuning(context: Context): Boolean {
        val preferences = preferences(context)
        return preferences.getBoolean(CUSTOM_THRESHOLDS, false) ||
            !preferences.getBoolean(REQUIRE_VERIFIER_FOR_STRONG_EXPLICIT, true)
    }

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
            putString(PROTECTION_LEVEL, ProtectionLevel.BALANCED.name)
            putBoolean(CUSTOM_THRESHOLDS, false)
            putBoolean(STRICT_MODE, false)
            putBoolean(REQUIRE_VERIFIER_FOR_STRONG_EXPLICIT, true)
        }
    }

    fun setRequireVerifierForStrongExplicit(context: Context, required: Boolean) {
        preferences(context).edit { putBoolean(REQUIRE_VERIFIER_FOR_STRONG_EXPLICIT, required) }
    }

    /** The preview bubble's last dragged position, as (distance from right edge, distance from
     * top) in pixels — null until the user has dragged it at least once. */
    fun previewBubblePosition(context: Context): Pair<Int, Int>? {
        val preferences = preferences(context)
        if (!preferences.contains(PREVIEW_BUBBLE_X)) return null
        return preferences.getInt(PREVIEW_BUBBLE_X, 0) to preferences.getInt(PREVIEW_BUBBLE_Y, 0)
    }

    fun setPreviewBubblePosition(context: Context, x: Int, y: Int) {
        preferences(context).edit {
            putInt(PREVIEW_BUBBLE_X, x)
            putInt(PREVIEW_BUBBLE_Y, y)
        }
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
        description = "A little more forgiving",
        thresholds = DetectionThresholds(
            explicit = 0.96f,
            semiNude = 0.95f,
            suspiciousExplicit = 0.80f,
            suspiciousSemiNude = 0.60f,
            verifier = 0.90f
        )
    ),
    BALANCED(
        displayName = "Balanced",
        description = "The original SinShield settings",
        thresholds = DetectionThresholds()
    ),
    STRICT(
        displayName = "Strict",
        description = "Catches a little more, including suggestive content",
        thresholds = DetectionThresholds(
            explicit = 0.88f,
            semiNude = 0.87f,
            suspiciousExplicit = 0.70f,
            suspiciousSemiNude = 0.50f,
            verifier = 0.81f
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
