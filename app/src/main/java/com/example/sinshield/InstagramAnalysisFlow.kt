package com.example.sinshield

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService

/**
 * Instagram isolates its edge-to-edge posts and gallery cells after whole-screen scoring. The two
 * heavy passes are independent, so the fast whole-screen classifier runs on [wholeScreenExecutor]
 * while this thread performs the ~1s OpenCV region detection. The detected crops are only scored
 * when the whole-screen verdict is not already a decisive block.
 */
internal class InstagramAnalysisFlow(
    private val fullScreenAnalyzer: FullScreenAnalyzer,
    private val localizedAnalyzer: LocalizedAnalyzer,
    private val finalizer: AnalysisFinalizer,
    private val wholeScreenExecutor: ExecutorService
) : AppAnalysisFlow {
    override fun analyze(input: AppAnalysisInput): FrameAnalysis {
        val wholeScreenTask = wholeScreenExecutor.submit(
            Callable { fullScreenAnalyzer.analyze(input) }
        )
        val regions = localizedAnalyzer.planRegions(
            input.bitmap,
            input.debugSession,
            LocalizedAnalysisContext(
                accessibilityMediaRegions = input.accessibilityMediaRegions,
                screenMode = input.screenMode,
                screenSignals = input.screenSignals
            )
        )
        val wholeScreen = wholeScreenTask.get()
        // A decisive whole-screen EXPLICIT verdict is terminal: localized scores can only agree with
        // it, never overturn it, so skip scoring the detected crops entirely. Any softer verdict
        // still runs localized, because Instagram's diluted whole-screen score is exactly what the
        // per-region pass exists to catch.
        val localized = if (wholeScreen.result.verdict == ContentVerdict.EXPLICIT) {
            LocalizedDetection.EMPTY
        } else {
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
