package app.sinshield

/** Reddit currently uses X-like stages but owns its sequence so it can diverge independently. */
internal class RedditAnalysisFlow(
    private val fullScreenAnalyzer: FullScreenAnalyzer,
    private val localizedAnalyzer: LocalizedAnalyzer,
    private val finalizer: AnalysisFinalizer
) : AppAnalysisFlow {
    override fun analyze(input: AppAnalysisInput): FrameAnalysis {
        val wholeScreen = fullScreenAnalyzer.analyze(input)
        return finalizer.finishIncrementally(input, wholeScreen, localizedAnalyzer)
    }
}
