package app.sinshield

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IncrementalVerificationFlowTest {
    @Test
    fun rejectedCandidateContinuesAndConfirmedCandidateStops() {
        val regions = listOf(region(0f), region(0.3f), region(0.6f))
        val analyzer = FakeLocalizedAnalyzer(regions)
        val verifier = FakeVerifier(ArrayDeque(listOf(0.10f, 0.90f)))
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        try {
            val analysis = AnalysisFinalizer(verifier).finishIncrementally(
                input = input(bitmap),
                wholeScreen = SAFE_WHOLE_SCREEN,
                localizedAnalyzer = analyzer
            )

            assertEquals(ContentVerdict.EXPLICIT, analysis.verdict)
            assertEquals(2, analyzer.classifiedRegions.size)
            assertEquals(regions.take(2), analyzer.classifiedRegions)
            assertEquals(listOf(0, 1), analyzer.ordinalOffsets)
            assertEquals(2, verifier.candidates.size)
            assertEquals(2, analysis.localized.regionScores.size)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun allRejectedCandidatesProduceSafeResultAfterEveryRegionIsChecked() {
        val regions = listOf(region(0f), region(0.3f), region(0.6f))
        val analyzer = FakeLocalizedAnalyzer(regions)
        val verifier = FakeVerifier(ArrayDeque(listOf(0.10f, 0.20f, 0.30f)))
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        try {
            val analysis = AnalysisFinalizer(verifier).finishIncrementally(
                input = input(bitmap),
                wholeScreen = SAFE_WHOLE_SCREEN,
                localizedAnalyzer = analyzer
            )

            assertEquals(ContentVerdict.SAFE, analysis.verdict)
            assertEquals(regions, analyzer.classifiedRegions)
            assertEquals(3, verifier.candidates.size)
            assertEquals(3, analysis.localized.regionScores.size)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun rejectedWholeScreenCandidateContinuesIntoLocalizedRegions() {
        val regions = listOf(region(0f), region(0.3f))
        val analyzer = FakeLocalizedAnalyzer(regions)
        val verifier = FakeVerifier(ArrayDeque(listOf(0.10f, 0.90f)))
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val suspiciousWholeScreen = WholeScreenAnalysis(
            scores = floatArrayOf(0.05f, 0.05f, 0.05f, 0.80f, 0.05f),
            result = StageOneResult(
                ContentVerdict.SUSPICIOUS,
                ContentVerdict.EXPLICIT,
                listOf("Porn")
            )
        )
        try {
            val analysis = AnalysisFinalizer(verifier).finishIncrementally(
                input = input(bitmap),
                wholeScreen = suspiciousWholeScreen,
                localizedAnalyzer = analyzer
            )

            assertEquals(ContentVerdict.EXPLICIT, analysis.verdict)
            assertEquals(listOf(regions.first()), analyzer.classifiedRegions)
            assertEquals(listOf(null, analysis.verifierBox), verifier.candidates)
        } finally {
            bitmap.recycle()
        }
    }

    private fun input(bitmap: Bitmap) = AppAnalysisInput(
        packageName = SupportedBrowsers.CHROME,
        frameHash = 1L,
        bitmap = bitmap,
        settings = DetectionSettings(
            thresholds = DetectionThresholds(),
            requireVerifierForStrongExplicit = true,
            blockSuggestive = false
        ),
        debugSession = null
    )

    private fun region(left: Float) = DetectionRegion(
        left = left,
        top = 0.1f,
        right = left + 0.2f,
        bottom = 0.4f,
        source = DetectionRegionSource.VISUAL_MEDIA
    )

    private class FakeLocalizedAnalyzer(
        private val plannedRegions: List<DetectionRegion>
    ) : LocalizedAnalyzer {
        val classifiedRegions = mutableListOf<DetectionRegion>()
        val ordinalOffsets = mutableListOf<Int>()

        override fun analyze(
            bitmap: Bitmap,
            thresholds: DetectionThresholds,
            debugSession: ModelAnalysisDebugSession?,
            context: LocalizedAnalysisContext
        ): LocalizedDetection = error("Incremental flow must plan and score separately")

        override fun planRegions(
            bitmap: Bitmap,
            debugSession: ModelAnalysisDebugSession?,
            context: LocalizedAnalysisContext
        ): List<DetectionRegion> = plannedRegions

        override fun classifyRegions(
            bitmap: Bitmap,
            regions: List<DetectionRegion>,
            thresholds: DetectionThresholds,
            debugSession: ModelAnalysisDebugSession?,
            regionOrdinalOffset: Int
        ): LocalizedDetection {
            val candidate = regions.single()
            classifiedRegions += candidate
            ordinalOffsets += regionOrdinalOffset
            return LocalizedDetection(
                boxes = listOf(
                    DetectionBox(
                        candidate.left,
                        candidate.top,
                        candidate.right,
                        candidate.bottom,
                        "Porn",
                        0.80f
                    )
                ),
                explicitScore = 0.80f,
                semiNudeScore = 0f,
                regionScores = listOf(RegionScore(candidate, explicit = 0.80f, semiNude = 0f))
            )
        }

        override fun close() = Unit
    }

    private class FakeVerifier(
        private val scores: ArrayDeque<Float>
    ) : CandidateVerificationEngine {
        val candidates = mutableListOf<DetectionBox?>()

        override fun verify(
            bitmap: Bitmap,
            candidate: DetectionBox?,
            debugSession: ModelAnalysisDebugSession?,
            debugFileStem: String
        ): VerificationResult {
            candidates += candidate
            return VerificationResult(scores.removeFirst())
        }

        override fun warmUp(sample: Bitmap) = true
        override val isReadyOrUnavailable = true
        override fun close() = Unit
    }

    private companion object {
        val SAFE_WHOLE_SCREEN = WholeScreenAnalysis(
            scores = floatArrayOf(1f, 0f, 0f, 0f, 0f),
            result = StageOneResult(ContentVerdict.SAFE, ContentVerdict.SAFE, emptyList())
        )
    }
}
