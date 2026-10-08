package app.sinshield

/** X owns its working full-screen gate followed by exact OpenCV-localized inference. */
internal class XAnalysisFlow(
    private val fullScreenAnalyzer: FullScreenAnalyzer,
    private val localizedAnalyzer: LocalizedAnalyzer,
    private val finalizer: AnalysisFinalizer
) : AppAnalysisFlow {
    override fun analyze(input: AppAnalysisInput): FrameAnalysis {
        val wholeScreen = fullScreenAnalyzer.analyze(input)
        return finalizer.finishIncrementally(input, wholeScreen, localizedAnalyzer)
    }
}
