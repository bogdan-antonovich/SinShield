package app.sinshield

/**
 * Instagram isolates its edge-to-edge posts and gallery cells after whole-screen scoring. The two
 * heavy passes deliberately run in sequence. Parallel ML and full-frame OpenCV made scrolling
 * faster only by saturating the phone, which causes OEM watchdogs to force-stop Accessibility
 * services. A decisive whole-screen result also avoids OpenCV entirely.
 */
internal class InstagramAnalysisFlow(
    private val fullScreenAnalyzer: FullScreenAnalyzer,
    private val localizedAnalyzer: LocalizedAnalyzer,
    private val finalizer: AnalysisFinalizer
) : AppAnalysisFlow {
    override fun analyze(input: AppAnalysisInput): FrameAnalysis {
        val wholeScreen = fullScreenAnalyzer.analyze(input)
        return finalizer.finishIncrementally(
            input = input,
            wholeScreen = wholeScreen,
            localizedAnalyzer = localizedAnalyzer,
            context = LocalizedAnalysisContext(
                accessibilityMediaRegions = input.accessibilityMediaRegions,
                screenMode = input.screenMode,
                screenSignals = input.screenSignals
            )
        )
    }
}
