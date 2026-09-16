package com.example.sinshield

/** Requires a streak of complete clean analyses before a full-screen shield can be removed. */
internal class OverlayReleaseGate(private val requiredSafeAnalyses: Int) {
    init {
        require(requiredSafeAnalyses > 0)
    }

    var consecutiveSafeAnalyses: Int = 0
        private set

    fun recordSafeAnalysis(): Boolean {
        consecutiveSafeAnalyses++
        return consecutiveSafeAnalyses >= requiredSafeAnalyses
    }

    fun reset() {
        consecutiveSafeAnalyses = 0
    }
}
