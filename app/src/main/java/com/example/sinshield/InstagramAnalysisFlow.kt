package com.example.sinshield

/** Instagram always isolates its edge-to-edge posts and gallery cells after whole-screen scoring. */
internal class InstagramAnalysisFlow(
    private val fullScreenAnalyzer: FullScreenAnalyzer,
    private val localizedAnalyzer: LocalizedAnalyzer,
    private val finalizer: AnalysisFinalizer
) : AppAnalysisFlow {
    override fun analyze(input: AppAnalysisInput): FrameAnalysis {
        val wholeScreen = fullScreenAnalyzer.analyze(input)
        val localized = localizedAnalyzer.analyze(
            input.bitmap,
            input.settings.thresholds,
            input.debugSession,
            LocalizedAnalysisContext(
                accessibilityMediaRegions = input.accessibilityMediaRegions,
                screenMode = input.screenMode,
                screenSignals = input.screenSignals
            )
        )
        return finalizer.finish(input, wholeScreen, localized)
    }
}
