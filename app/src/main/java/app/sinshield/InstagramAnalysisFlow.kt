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
        // A decisive whole-screen EXPLICIT verdict is terminal: localized scores can only agree with
        // it, never overturn it, so skip scoring the detected crops entirely. Any softer verdict
        // still runs localized, because Instagram's diluted whole-screen score is exactly what the
        // per-region pass exists to catch.
        val localized = if (wholeScreen.result.verdict == ContentVerdict.EXPLICIT) {
            LocalizedDetection.EMPTY
        } else {
            val regions = localizedAnalyzer.planRegions(
                input.bitmap,
                input.debugSession,
                LocalizedAnalysisContext(
                    accessibilityMediaRegions = input.accessibilityMediaRegions,
                    screenMode = input.screenMode,
                    screenSignals = input.screenSignals
                )
            )
            localizedAnalyzer.classifyRegions(
                input.bitmap,
                regions,
                input.settings.thresholds,
                input.debugSession
            )
        }
        return finalizer.finish(input, wholeScreen, localized)
    }
}
