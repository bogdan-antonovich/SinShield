package app.sinshield

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything an app-specific analysis flow needs after [FrameScanner] has captured a frame. */
internal data class AppAnalysisInput(
    val packageName: String,
    val frameHash: Long,
    val bitmap: Bitmap,
    val settings: DetectionSettings,
    val debugSession: ModelAnalysisDebugSession?,
    val accessibilityMediaRegions: List<DetectionRegion> = emptyList(),
    val screenMode: ShieldedScreenMode = ShieldedScreenMode.UNKNOWN,
    val screenSignals: ScreenSignals = ScreenSignals.EMPTY
)

/** An app owns the order in which reusable analyzers inspect its captured frame. */
internal fun interface AppAnalysisFlow {
    fun analyze(input: AppAnalysisInput): FrameAnalysis
}

internal enum class AppAnalysisFlowKind {
    X,
    INSTAGRAM,
    REDDIT,
    FULL_SCREEN_ONLY
}

/** Pure package routing kept separate so adding an Instagram flow cannot alter X's sequence. */
internal object AppAnalysisFlowSelector {
    fun select(packageName: String): AppAnalysisFlowKind = when (packageName) {
        ShieldedApp.X.packageName -> AppAnalysisFlowKind.X
        ShieldedApp.INSTAGRAM.packageName -> AppAnalysisFlowKind.INSTAGRAM
        ShieldedApp.REDDIT.packageName -> AppAnalysisFlowKind.REDDIT
        else -> AppAnalysisFlowKind.FULL_SCREEN_ONLY
    }
}

/** The localized stage is a fallback for content diluted in an otherwise-safe whole screen. */
internal object LocalizedStageGate {
    fun shouldRun(wholeScreen: StageOneResult): Boolean =
        wholeScreen.verdict == ContentVerdict.SAFE
}

/**
 * Owns the reusable model-backed components and the independently assembled per-app flows.
 * [FrameScanner] owns capture/lifecycle; this registry owns only analysis and model lifetime.
 */
internal class AppAnalysisFlowRegistry(context: Context) : AutoCloseable {
    private val fullScreenAnalyzer = FullScreenAnalyzer(context, CLASSIFIER_MODEL_ASSET)
    private val localizedAnalyzer: LocalizedAnalyzer = AsyncOpenCvLocalizedAnalyzer(
        context,
        CLASSIFIER_MODEL_ASSET
    )
    private val instagramLocalizedAnalyzer: LocalizedAnalyzer = AsyncOpenCvLocalizedAnalyzer(
        context = context,
        modelAsset = CLASSIFIER_MODEL_ASSET,
        detectorConfig = OpenCvMediaRegionDetector.Config.INSTAGRAM,
        regionPlanner = InstagramLocalizedRegionPlanner,
        // Instagram draws a full-screen block, not per-region covers, so one actionable region is
        // enough to decide the frame — stop scoring the rest of the grid as soon as it is found.
        stopAfterFirstActionable = true,
        saveFinalRegionMap = true
    )
    private val finalizer = AnalysisFinalizer(CandidateVerifier(context, VERIFIER_MODEL_ASSET))

    private val xFlow: AppAnalysisFlow = XAnalysisFlow(
        fullScreenAnalyzer,
        localizedAnalyzer,
        finalizer
    )
    private val instagramFlow: AppAnalysisFlow = InstagramAnalysisFlow(
        fullScreenAnalyzer,
        instagramLocalizedAnalyzer,
        finalizer
    )
    private val redditFlow: AppAnalysisFlow = RedditAnalysisFlow(
        fullScreenAnalyzer,
        localizedAnalyzer,
        finalizer
    )
    private val fullScreenOnlyFlow: AppAnalysisFlow = FullScreenOnlyAnalysisFlow(
        fullScreenAnalyzer,
        finalizer
    )

    fun analyze(input: AppAnalysisInput): FrameAnalysis = when (
        AppAnalysisFlowSelector.select(input.packageName)
    ) {
        AppAnalysisFlowKind.X -> xFlow.analyze(input)
        AppAnalysisFlowKind.INSTAGRAM -> instagramFlow.analyze(input)
        AppAnalysisFlowKind.REDDIT -> redditFlow.analyze(input)
        AppAnalysisFlowKind.FULL_SCREEN_ONLY -> fullScreenOnlyFlow.analyze(input)
    }

    fun warmUp(sample: Bitmap): Boolean {
        runCatching { fullScreenAnalyzer.classify(sample) }
            .onFailure { Log.w(TAG, "Classifier warmup failed", it) }
        return finalizer.warmUp(sample)
    }

    val warmUpComplete: Boolean get() = finalizer.warmUpComplete

    override fun close() {
        instagramLocalizedAnalyzer.close()
        localizedAnalyzer.close()
        fullScreenAnalyzer.close()
        finalizer.close()
    }

    private companion object {
        private const val TAG = "SinSheld"
        private const val CLASSIFIER_MODEL_ASSET = "nsfw_mobilenet_v2.tflite"
        private const val VERIFIER_MODEL_ASSET = "nsfw_marqo_vit_tiny_384.onnx"
    }
}

/** Conservative default for supported apps that do not yet have a specialized flow. */
internal class FullScreenOnlyAnalysisFlow(
    private val fullScreenAnalyzer: FullScreenAnalyzer,
    private val finalizer: AnalysisFinalizer
) : AppAnalysisFlow {
    override fun analyze(input: AppAnalysisInput): FrameAnalysis {
        val wholeScreen = fullScreenAnalyzer.analyze(input)
        return finalizer.finish(input, wholeScreen, LocalizedDetection.EMPTY)
    }
}

internal data class WholeScreenAnalysis(
    val scores: FloatArray,
    val result: StageOneResult
)

/** Shared classifier capability; app flows decide when it is called and what follows it. */
internal class FullScreenAnalyzer(context: Context, modelAsset: String) : AutoCloseable {
    private val classifier = NsfwClassifier(context, modelAsset, numThreads = FULL_SCREEN_THREADS)

    fun classify(bitmap: Bitmap): FloatArray = classifier.classify(bitmap)

    fun analyze(input: AppAnalysisInput): WholeScreenAnalysis {
        val scores = classify(input.bitmap)
        val result = ContentPolicy.evaluate(scores, input.settings.thresholds)
        input.debugSession?.saveClassifierInput(
            fileStem = "00-full-screen",
            bitmap = input.bitmap,
            scores = scores,
            verdict = result.verdict,
            details = "input ${input.bitmap.width}x${input.bitmap.height}  package ${input.packageName}"
        )
        return WholeScreenAnalysis(scores, result)
    }

    override fun close() = classifier.close()

    private companion object {
        private const val FULL_SCREEN_THREADS = 1
    }
}

/** Shared result construction and verifier policy used after an app flow finishes its stages. */
internal class AnalysisFinalizer(private val verifier: CandidateVerifier) : AutoCloseable {
    fun finish(
        input: AppAnalysisInput,
        wholeScreen: WholeScreenAnalysis,
        localized: LocalizedDetection
    ): FrameAnalysis {
        val thresholds = input.settings.thresholds
        val candidatePolicy = ContentPolicy.combine(
            wholeScreen.result,
            localized.explicitScore,
            localized.semiNudeScore,
            thresholds
        )
        val verifierBox = localized.boxes.bestSupporting(candidatePolicy.suspectedFinalVerdict)
        val decisiveNeedsNoVerifier = candidatePolicy.verdict == ContentVerdict.SEMI_NUDE ||
            (candidatePolicy.verdict == ContentVerdict.EXPLICIT &&
                !input.settings.requireVerifierForStrongExplicit)
        val verification = if (
            candidatePolicy.verdict == ContentVerdict.SAFE || decisiveNeedsNoVerifier
        ) {
            null
        } else {
            verifier.verify(input.bitmap, verifierBox, input.debugSession)
        }
        val scores = wholeScreen.scores
        return FrameAnalysis(
            frameHash = input.frameHash,
            stageOne = wholeScreen.result,
            localized = localized,
            wholeScreenScores = scores.copyOf(),
            wholeScreenExplicitScore = max(scores[1], scores[3]),
            wholeScreenSemiNudeScore = scores[4],
            verification = verification,
            verifierBox = verifierBox,
            frameWidth = input.bitmap.width,
            frameHeight = input.bitmap.height,
            deduplicated = false,
            thresholds = thresholds,
            requireVerifierForStrongExplicit = input.settings.requireVerifierForStrongExplicit
        )
    }

    fun warmUp(sample: Bitmap): Boolean = verifier.verify(sample, null, null) != null

    val warmUpComplete: Boolean get() = verifier.isReadyOrUnavailable

    override fun close() = verifier.close()
}

/** Lazy independent verifier shared by the app flows. */
internal class CandidateVerifier(
    private val context: Context,
    private val modelAsset: String
) : AutoCloseable {
    private var verifier: NsfwVerifier? = null
    private var unavailable = false

    fun verify(
        bitmap: Bitmap,
        candidate: DetectionBox?,
        debugSession: ModelAnalysisDebugSession?
    ): VerificationResult? {
        if (unavailable) return null
        return try {
            val activeVerifier = verifier ?: NsfwVerifier(context, modelAsset).also {
                verifier = it
                Log.i(TAG, "Independent NSFW verifier loaded")
            }
            activeVerifier.verify(bitmap, candidate) { input, result ->
                debugSession?.saveVerifierInput(
                    bitmap = input,
                    result = result,
                    details = if (candidate == null) {
                        "source: full screen"
                    } else {
                        "source: padded localized candidate ${formatVerifierBox(candidate)}"
                    }
                )
            }
        } catch (error: Throwable) {
            unavailable = true
            runCatching { verifier?.close() }
            verifier = null
            Log.e(TAG, "Independent NSFW verifier unavailable; failing open", error)
            null
        }
    }

    val isReadyOrUnavailable: Boolean get() = verifier != null || unavailable

    override fun close() {
        runCatching { verifier?.close() }
        verifier = null
    }

    private companion object {
        private const val TAG = "SinSheld"
    }
}

internal interface LocalizedAnalyzer : AutoCloseable {
    fun analyze(
        bitmap: Bitmap,
        thresholds: DetectionThresholds,
        debugSession: ModelAnalysisDebugSession?,
        context: LocalizedAnalysisContext = LocalizedAnalysisContext.EMPTY
    ): LocalizedDetection

    /**
     * Runs only the region-finding half — OpenCV detection plus the app planner. Split out from
     * [analyze] so a flow can overlap this ~1s CPU pass with the whole-screen classifier and then
     * decide, from that classifier's verdict, whether the detected crops are worth scoring at all.
     */
    fun planRegions(
        bitmap: Bitmap,
        debugSession: ModelAnalysisDebugSession?,
        context: LocalizedAnalysisContext = LocalizedAnalysisContext.EMPTY
    ): List<DetectionRegion>

    /** Scores the [regions] from [planRegions]; empty input yields [LocalizedDetection.EMPTY]. */
    fun classifyRegions(
        bitmap: Bitmap,
        regions: List<DetectionRegion>,
        thresholds: DetectionThresholds,
        debugSession: ModelAnalysisDebugSession?
    ): LocalizedDetection

    override fun close()
}

internal data class LocalizedAnalysisContext(
    val accessibilityMediaRegions: List<DetectionRegion>,
    val screenMode: ShieldedScreenMode,
    val screenSignals: ScreenSignals
) {
    companion object {
        val EMPTY = LocalizedAnalysisContext(emptyList(), ShieldedScreenMode.UNKNOWN, ScreenSignals.EMPTY)
    }
}

internal fun interface LocalizedAnalysisRegionPlanner {
    fun plan(
        bitmapWidth: Int,
        bitmapHeight: Int,
        visualRegions: List<DetectionRegion>,
        context: LocalizedAnalysisContext
    ): List<DetectionRegion>
}

private object ExactOpenCvRegionPlanner : LocalizedAnalysisRegionPlanner {
    override fun plan(
        bitmapWidth: Int,
        bitmapHeight: Int,
        visualRegions: List<DetectionRegion>,
        context: LocalizedAnalysisContext
    ): List<DetectionRegion> = LocalizedRegionPlanner.plan(
        bitmapWidth,
        bitmapHeight,
        visualRegions
    )
}

/** Runs exact OpenCV crops on independent interpreters and returns on the first actionable result. */
internal class AsyncOpenCvLocalizedAnalyzer(
    context: Context,
    modelAsset: String,
    detectorConfig: OpenCvMediaRegionDetector.Config = OpenCvMediaRegionDetector.Config.DEFAULT,
    private val regionPlanner: LocalizedAnalysisRegionPlanner = ExactOpenCvRegionPlanner,
    private val stopAfterFirstActionable: Boolean = true,
    private val saveFinalRegionMap: Boolean = false
) : LocalizedAnalyzer {
    private val visualMediaDetector = OpenCvMediaRegionDetector(
        config = detectorConfig,
        debugWriter = if (GlobalDebugMode.ENABLED) {
            OpenCvDetectionDebugWriter(context)
        } else {
            null
        }
    )
    // Each app-specific detector has its own geometry rules, but its native classifiers are created
    // only if that detector actually needs to score a crop. This avoids loading both the default and
    // Instagram model pools merely because the accessibility service connected.
    private val workersDelegate = lazy {
        newBackgroundFixedThreadPool("SinSheld-Region", LOCALIZED_WORKERS)
    }
    private val workers by workersDelegate
    private val classifiersDelegate = lazy {
        ArrayBlockingQueue<NsfwClassifier>(LOCALIZED_WORKERS).apply {
            repeat(LOCALIZED_WORKERS) {
                add(NsfwClassifier(context, modelAsset, numThreads = THREADS_PER_LOCALIZED_WORKER))
            }
        }
    }
    private val classifiers by classifiersDelegate

    override fun analyze(
        bitmap: Bitmap,
        thresholds: DetectionThresholds,
        debugSession: ModelAnalysisDebugSession?,
        context: LocalizedAnalysisContext
    ): LocalizedDetection = classifyRegions(
        bitmap,
        planRegions(bitmap, debugSession, context),
        thresholds,
        debugSession
    )

    override fun planRegions(
        bitmap: Bitmap,
        debugSession: ModelAnalysisDebugSession?,
        context: LocalizedAnalysisContext
    ): List<DetectionRegion> {
        // OpenCV rectangles are detector candidates. The app planner below owns the final list;
        // only the returned regions are cropped, classified, and written as NN-localized.jpg.
        val regionsToAnalyze = regionPlanner.plan(
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            visualRegions = visualMediaDetector.detect(
                bitmap,
                inferInstagramGrid = InstagramLocalizedRegionPlanner.isGridSurface(context),
                inferInstagramPost = InstagramLocalizedRegionPlanner.isPostSurface(context)
            ),
            context = context
        )
        if (saveFinalRegionMap) {
            debugSession?.saveFinalRegionMap(bitmap, regionsToAnalyze)
        }
        return regionsToAnalyze
    }

    override fun classifyRegions(
        bitmap: Bitmap,
        regions: List<DetectionRegion>,
        thresholds: DetectionThresholds,
        debugSession: ModelAnalysisDebugSession?
    ): LocalizedDetection {
        if (regions.isEmpty()) return LocalizedDetection.EMPTY

        val aborted = AtomicBoolean(false)
        val completion = ExecutorCompletionService<CompletedRegion>(workers)
        val frameWidth = bitmap.width
        val frameHeight = bitmap.height
        regions.forEachIndexed { index, region ->
            completion.submit {
                if (aborted.get()) return@submit CompletedRegion.skipped(region)
                // Allocate at worker execution time instead of queuing every crop up front. At most
                // LOCALIZED_WORKERS crop bitmaps now coexist, even on an 18-region Instagram grid.
                val crop = crop(bitmap, region)
                try {
                    val classifier = classifiers.take()
                    try {
                        if (aborted.get()) {
                            CompletedRegion.skipped(region)
                        } else {
                            val scores = classifier.classify(crop)
                            val verdict = localizedRegionVerdict(scores, thresholds)
                            debugSession?.saveClassifierInput(
                                fileStem = "${(index + 1).toString().padStart(2, '0')}-localized",
                                bitmap = crop,
                                scores = scores,
                                verdict = verdict,
                                details = formatDebugRegion(region, frameWidth, frameHeight)
                            )
                            CompletedRegion(region, scores)
                        }
                    } finally {
                        classifiers.put(classifier)
                    }
                } finally {
                    crop.recycle()
                }
            }
        }

        val accumulated = MutableLocalizedDetection()
        var earlyResult: LocalizedDetection? = null
        repeat(regions.size) {
            val completed = completion.take().get()
            if (earlyResult != null) return@repeat
            val scores = completed.scores ?: return@repeat
            accumulated.add(completed.region, scores, thresholds)
            if (stopAfterFirstActionable &&
                accumulated.verdict(thresholds) != ContentVerdict.SAFE
            ) {
                aborted.set(true)
                earlyResult = accumulated.snapshot()
            }
        }
        // Drain every submitted task before returning: worker crops may share the source bitmap's
        // pixels, and FrameScanner recycles that source as soon as this analysis call completes.
        return earlyResult ?: accumulated.snapshot()
    }

    private fun crop(bitmap: Bitmap, region: DetectionRegion): Bitmap {
        val left = (region.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (region.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (region.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (region.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    override fun close() {
        if (!workersDelegate.isInitialized()) return
        workers.shutdown()
        if (!workers.awaitTermination(WORKER_SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
            workers.shutdownNow()
        }
        if (classifiersDelegate.isInitialized()) classifiers.forEach(NsfwClassifier::close)
    }

    private data class CompletedRegion(
        val region: DetectionRegion,
        val scores: FloatArray?
    ) {
        companion object {
            fun skipped(region: DetectionRegion) = CompletedRegion(region, null)
        }
    }

    private class MutableLocalizedDetection {
        private val boxes = mutableListOf<DetectionBox>()
        private val regionScores = mutableListOf<RegionScore>()
        private var explicitScore = 0f
        private var semiNudeScore = 0f

        fun add(region: DetectionRegion, scores: FloatArray, thresholds: DetectionThresholds) {
            val regionExplicit = max(scores[1], scores[3])
            val regionSemiNude = scores[4]
            regionScores += RegionScore(region, regionExplicit, regionSemiNude)
            explicitScore = max(explicitScore, regionExplicit)
            semiNudeScore = max(semiNudeScore, regionSemiNude)
            if (regionExplicit >= thresholds.suspiciousExplicit) {
                boxes += DetectionBox(
                    region.left,
                    region.top,
                    region.right,
                    region.bottom,
                    if (scores[3] >= scores[1]) "Porn" else "Hentai",
                    regionExplicit
                )
            }
            if (regionSemiNude >= thresholds.suspiciousSemiNude) {
                boxes += DetectionBox(
                    region.left,
                    region.top,
                    region.right,
                    region.bottom,
                    "Sexy",
                    regionSemiNude
                )
            }
        }

        fun verdict(thresholds: DetectionThresholds): ContentVerdict = ContentPolicy.combine(
            wholeScreen = StageOneResult(ContentVerdict.SAFE, ContentVerdict.SAFE, emptyList()),
            localizedExplicitScore = explicitScore,
            localizedSemiNudeScore = semiNudeScore,
            thresholds = thresholds
        ).verdict

        fun snapshot() = LocalizedDetection(
            boxes.toList(),
            explicitScore,
            semiNudeScore,
            regionScores.toList()
        )
    }

    private companion object {
        // One classifier keeps peak CPU and native memory bounded on 4 GB phones. The service is
        // long-lived; saturating several cores to shave milliseconds off a scan makes OEM
        // watchdogs force-stop the entire package, which disables both Accessibility and VPN.
        private const val LOCALIZED_WORKERS = 1
        private const val THREADS_PER_LOCALIZED_WORKER = 1
        private const val WORKER_SHUTDOWN_SECONDS = 5L
    }
}

private fun localizedRegionVerdict(
    scores: FloatArray,
    thresholds: DetectionThresholds
): ContentVerdict = ContentPolicy.combine(
    wholeScreen = StageOneResult(ContentVerdict.SAFE, ContentVerdict.SAFE, emptyList()),
    localizedExplicitScore = max(scores[1], scores[3]),
    localizedSemiNudeScore = scores[4],
    thresholds = thresholds
).verdict

private fun formatDebugRegion(
    region: DetectionRegion,
    frameWidth: Int,
    frameHeight: Int
): String {
    val left = (region.left * frameWidth).roundToInt()
    val top = (region.top * frameHeight).roundToInt()
    val width = ((region.right - region.left) * frameWidth).roundToInt()
    val height = ((region.bottom - region.top) * frameHeight).roundToInt()
    return "source ${region.source}\nbox $left,$top ${width}x$height"
}
