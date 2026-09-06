package com.example.sinshield

/** Decides whether a completed inference is too old to affect the current screen. */
internal object ScanFreshnessPolicy {
    fun shouldDiscard(
        contextStale: Boolean,
        eventArrivedAfterCapture: Boolean,
        verdict: ContentVerdict
    ): Boolean = contextStale ||
        (eventArrivedAfterCapture && verdict == ContentVerdict.SAFE)
}
