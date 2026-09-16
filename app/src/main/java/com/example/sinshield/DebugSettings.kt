package com.example.sinshield

import android.content.Context
import androidx.core.content.edit

/**
 * Code-only gate for every developer-facing feature in the app.
 *
 * Set [LOCAL_ENABLED] to true locally to reveal the Debug card in a debuggable build and allow its
 * settings to take effect. The build-type check keeps every developer feature disabled in release
 * and production-like builds even when the local switch is accidentally left on.
 */
internal object GlobalDebugMode {
    private const val LOCAL_ENABLED = false
    val ENABLED: Boolean = BuildConfig.DEBUG && LOCAL_ENABLED
}

/** Individual debug controls. Every read is fail-closed behind [GlobalDebugMode]. */
internal object DebugSettings {
    private const val PREFERENCES = "debug_settings"
    private const val PHOTO_DUMPS = "photo_dumps"
    private const val OVERLAY_FEEDBACK = "overlay_feedback"
    private const val OVERLAY_DISMISS = "overlay_dismiss"
    private const val LAST_SHUTDOWN = "last_shutdown"

    @Suppress("SimplifyBooleanWithConstants")
    fun photoDumps(context: Context): Boolean =
        GlobalDebugMode.ENABLED && preferences(context).getBoolean(PHOTO_DUMPS, false)

    fun setPhotoDumps(context: Context, enabled: Boolean) = write(context, PHOTO_DUMPS, enabled)

    @Suppress("SimplifyBooleanWithConstants")
    fun overlayFeedback(context: Context): Boolean =
        GlobalDebugMode.ENABLED && preferences(context).getBoolean(OVERLAY_FEEDBACK, false)

    fun setOverlayFeedback(context: Context, enabled: Boolean) =
        write(context, OVERLAY_FEEDBACK, enabled)

    @Suppress("SimplifyBooleanWithConstants")
    fun overlayDismiss(context: Context): Boolean =
        GlobalDebugMode.ENABLED && preferences(context).getBoolean(OVERLAY_DISMISS, false)

    fun setOverlayDismiss(context: Context, enabled: Boolean) =
        write(context, OVERLAY_DISMISS, enabled)

    @Suppress("SimplifyBooleanWithConstants")
    fun lastShutdown(context: Context): Boolean =
        GlobalDebugMode.ENABLED && preferences(context).getBoolean(LAST_SHUTDOWN, false)

    fun setLastShutdown(context: Context, enabled: Boolean) =
        write(context, LAST_SHUTDOWN, enabled)

    private fun write(context: Context, key: String, enabled: Boolean) {
        if (!GlobalDebugMode.ENABLED) return
        preferences(context).edit { putBoolean(key, enabled) }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
