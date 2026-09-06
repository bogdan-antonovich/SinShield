package com.example.sinshield

/** X owns its working full-screen gate followed by exact OpenCV-localized inference. */
internal class XAnalysisFlow(
    private val fullScreenAnalyzer: FullScreenAnalyzer,
    private val localizedAnalyzer: LocalizedAnalyzer,
    private val finalizer: AnalysisFinalizer
) : AppAnalysisFlow {
    override fun analyze(input: AppAnalysisInput): FrameAnalysis {
        val wholeScreen = fullScreenAnalyzer.analyze(input)
        val localized = if (LocalizedStageGate.shouldRun(wholeScreen.result)) {
            localizedAnalyzer.analyze(
                input.bitmap,
                input.settings.thresholds,
                input.debugSession
            )
        } else {
            LocalizedDetection.EMPTY
        }
        return finalizer.finish(input, wholeScreen, localized)
    }
}
