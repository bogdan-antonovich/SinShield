package com.example.sinshield

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Finds probable photo bounds in a screenshot for downstream classifier crops. */
internal class OpenCvMediaRegionDetector(
    private val config: Config = Config.DEFAULT,
    private val debugWriter: OpenCvDetectionDebugWriter? = null
) {
    private var initializationAttempted = false
    private var available = false

    fun detect(
        bitmap: Bitmap,
        inferInstagramGrid: Boolean = false,
        inferInstagramPost: Boolean = false
    ): List<DetectionRegion> {
        if (!ensureAvailable()) return emptyList()

        val source = Mat()
        val small = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val closedEdges = Mat()
        val textureDensity = Mat()
        val textureMask = Mat()
        val lines = Mat()
        val edgeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val textureKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        return try {
            Utils.bitmapToMat(bitmap, source)
            // Preserve the screenshot's horizontal resolution: media borders in a phone capture
            // are often only one anti-aliased pixel wide. Capping the long edge at 720 reduced a
            // 720x1600 screenshot to 324px wide and erased exactly those useful boundaries.
            val scale = min(1.0, ANALYSIS_MAX_WIDTH / source.width())
            Imgproc.resize(source, small, Size(), scale, scale, Imgproc.INTER_AREA)
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(BLUR_SIZE, BLUR_SIZE), 0.0)
            Imgproc.Canny(blurred, edges, CANNY_LOW, CANNY_HIGH)

            // Recover closed rounded/rectangular media borders. The tiny close repairs only
            // anti-aliasing gaps; it cannot join a whole post's unrelated text and controls.
            Imgproc.morphologyEx(edges, closedEdges, Imgproc.MORPH_CLOSE, edgeKernel)
            val borderCandidates = findBorderRectangles(closedEdges, edges, small.width(), small.height())

            // Borderless photos still contain visual detail across a two-dimensional block.
            // Local edge density is independent of the surrounding UI's actual color.
            Imgproc.blur(edges, textureDensity, Size(TEXTURE_WINDOW, TEXTURE_WINDOW))
            Imgproc.threshold(
                textureDensity,
                textureMask,
                MIN_LOCAL_EDGE_DENSITY,
                255.0,
                Imgproc.THRESH_BINARY
            )
            Imgproc.morphologyEx(textureMask, textureMask, Imgproc.MORPH_CLOSE, textureKernel)
            val textureCandidates = findTextureRectangles(
                textureMask,
                edges,
                small.width(),
                small.height()
            )

            // Straight borders are excellent evidence when present, but no longer mandatory.
            Imgproc.HoughLinesP(
                edges,
                lines,
                HOUGH_RHO,
                Math.PI / 180.0,
                HOUGH_THRESHOLD,
                small.width() * MIN_HOUGH_LINE_RATIO,
                small.width() * MAX_HOUGH_GAP_RATIO
            )
            val detectedLineSegments = lines.toDetectedSegments()
            val lineRectangles = LineRectangleAssembler.assemble(
                imageWidth = small.width(),
                imageHeight = small.height(),
                rawSegments = detectedLineSegments,
                maxResults = config.maxRegions
            ).mapNotNull { rectangle ->
                val bounds = Rect(
                    rectangle.left.toInt(),
                    rectangle.top.toInt(),
                    rectangle.width.toInt(),
                    rectangle.height.toInt()
                )
                if (hasTextLikeEdgeDistribution(edges, bounds)) {
                    Log.v(TAG, "detect: reject text-like line rectangle ${bounds.width}x${bounds.height}")
                    return@mapNotNull null
                }
                PixelCandidate(
                    bounds,
                    score = if (rectangle.isGridCell) {
                        1.10 + rectangle.score * 0.15
                    } else {
                        0.85 + rectangle.score * 0.15
                    },
                    evidence = if (rectangle.isGridCell) "grid" else "lines"
                )
            }

            val instagramGridRectangles = if (inferInstagramGrid) {
                val transitionRectangles = InstagramGridTransitionAssembler.assemble(
                    imageWidth = small.width(),
                    imageHeight = small.height(),
                    rowTransitions = adjacentTransitionScores(gray, horizontal = true),
                    columnTransitions = adjacentTransitionScores(gray, horizontal = false),
                    maxResults = config.maxRegions,
                    // Instagram thumbnails are ~4:5, so clamp transition cells to that aspect. The
                    // DEFAULT config (X's flow) passes no clamp and is therefore unchanged.
                    maxCellAspectRatio = if (config.inferInstagramLayouts) {
                        INSTAGRAM_GRID_CELL_MAX_ASPECT
                    } else {
                        Double.MAX_VALUE
                    }
                )
                val houghRectangles = InstagramGridRegionAssembler.assemble(
                    imageWidth = small.width(),
                    imageHeight = small.height(),
                    rawSegments = detectedLineSegments,
                    maxResults = config.maxRegions
                )
                // Instagram: prefer the structurally validated Hough cells (full-width row
                // separators plus three real column dividers) so a profile header cannot anchor the
                // grid; the transition cells only fill gaps they leave, and Hough wins the planner's
                // de-duplication. The DEFAULT config (X's flow) keeps its original ordering so its
                // behavior is byte-for-byte unchanged.
                val orderedGridRectangles = if (config.inferInstagramLayouts) {
                    houghRectangles + transitionRectangles
                } else {
                    transitionRectangles + houghRectangles
                }
                orderedGridRectangles.map { rectangle ->
                    PixelCandidate(
                        bounds = Rect(
                            rectangle.left.toInt(),
                            rectangle.top.toInt(),
                            rectangle.width.toInt(),
                            rectangle.height.toInt()
                        ),
                        score = 1.60,
                        evidence = "instagram-grid"
                    )
                }
            } else {
                emptyList()
            }

            val instagramPostRectangles = if (config.inferInstagramLayouts && inferInstagramPost) {
                val transitionRectangles = InstagramPostTransitionAssembler.assemble(
                    imageWidth = small.width(),
                    imageHeight = small.height(),
                    rowTransitions = adjacentTransitionScores(gray, horizontal = true),
                    sustainedRowTransitions = sustainedRowTransitionScores(gray)
                )
                val houghRectangles = InstagramPostRegionAssembler.assemble(
                    imageWidth = small.width(),
                    imageHeight = small.height(),
                    rawSegments = detectedLineSegments
                )
                (transitionRectangles + houghRectangles).map { rectangle ->
                    PixelCandidate(
                        bounds = Rect(
                            rectangle.left.toInt(),
                            rectangle.top.toInt(),
                            rectangle.width.toInt(),
                            rectangle.height.toInt()
                        ),
                        score = 1.55,
                        evidence = "instagram-post"
                    )
                }
            } else {
                emptyList()
            }

            val allLineRectangles = instagramGridRectangles + instagramPostRectangles + lineRectangles
            val gridCells = allLineRectangles.filter { it.evidence.endsWith("grid") }
            val candidates = if (gridCells.size >= MIN_CONFIRMED_GRID_CELLS) {
                val galleryTop = gridCells.minOf { it.bounds.y }
                allLineRectangles + (borderCandidates + textureCandidates).filter { candidate ->
                    candidate.bounds.y >= galleryTop - GRID_HEADER_TOLERANCE
                }
            } else {
                borderCandidates + allLineRectangles + textureCandidates
            }
            val selected = selectCandidates(candidates)
            val regions = selected.map { candidate ->
                val bounds = candidate.bounds
                Log.v(
                    TAG,
                    "detect: keep ${bounds.x},${bounds.y} ${bounds.width}x${bounds.height} " +
                        "evidence=${candidate.evidence} score=${fmt(candidate.score)}"
                )
                DetectionRegion(
                    left = (bounds.x.toDouble() / small.width()).toFloat().coerceIn(0f, 1f),
                    top = (bounds.y.toDouble() / small.height()).toFloat().coerceIn(0f, 1f),
                    right = ((bounds.x + bounds.width).toDouble() / small.width())
                        .toFloat().coerceIn(0f, 1f),
                    bottom = ((bounds.y + bounds.height).toDouble() / small.height())
                        .toFloat().coerceIn(0f, 1f),
                    source = DetectionRegionSource.VISUAL_MEDIA
                )
            }
            Log.d(
                TAG,
                "detect: frame=${source.width()}x${source.height()} " +
                    "analysis=${small.width()}x${small.height()} scale=${fmt(scale)} " +
                    "borders=${borderCandidates.size} lines=${allLineRectangles.size} " +
                    "texture=${textureCandidates.size} regions=${regions.size}"
            )
            debugWriter?.save(
                bitmap = bitmap,
                regions = regions,
                stages = OpenCvDebugStages(
                    analysis = small,
                    grayscale = gray,
                    gaussianBlur = blurred,
                    canny = edges,
                    closedEdges = closedEdges,
                    textureDensity = textureDensity,
                    textureMask = textureMask,
                    houghLines = detectedLineSegments
                )
            )
            regions
        } catch (error: Throwable) {
            Log.e(TAG, "Visual media detection failed; using accessibility and grid crops", error)
            emptyList()
        } finally {
            source.release()
            small.release()
            gray.release()
            blurred.release()
            edges.release()
            closedEdges.release()
            textureDensity.release()
            textureMask.release()
            lines.release()
            edgeKernel.release()
            textureKernel.release()
        }
    }

    private fun findBorderRectangles(
        contourImage: Mat,
        edges: Mat,
        imageWidth: Int,
        imageHeight: Int
    ): List<PixelCandidate> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        return try {
            Imgproc.findContours(
                contourImage,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            contours.mapNotNull { contour ->
                val bounds = Geometry.boundingRect(contour)
                if (!hasPlausibleGeometry(bounds, imageWidth, imageHeight)) return@mapNotNull null
                val rectangularity = Geometry.contourArea(contour) / bounds.area().coerceAtLeast(1.0)
                if (rectangularity < MIN_BORDER_RECTANGULARITY) return@mapNotNull null
                val coverage = edgeCellCoverage(edges, bounds)
                if (coverage < MIN_EDGE_CELL_COVERAGE) return@mapNotNull null
                PixelCandidate(
                    bounds,
                    score = 1.0 + rectangularity * 0.25 + coverage * 0.15,
                    evidence = "border"
                )
            }
        } finally {
            contours.forEach(MatOfPoint::release)
            hierarchy.release()
        }
    }

    private fun findTextureRectangles(
        mask: Mat,
        edges: Mat,
        imageWidth: Int,
        imageHeight: Int
    ): List<PixelCandidate> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        return try {
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            contours.mapNotNull { contour ->
                val raw = Geometry.boundingRect(contour)
                val bounds = raw.expand(TEXTURE_BOUNDS_PADDING, imageWidth, imageHeight)
                if (!hasPlausibleGeometry(bounds, imageWidth, imageHeight)) return@mapNotNull null
                val coverage = edgeCellCoverage(edges, bounds)
                if (coverage < MIN_TEXTURE_CELL_COVERAGE) return@mapNotNull null
                val fill = Geometry.contourArea(contour) / raw.area().coerceAtLeast(1.0)
                if (fill < MIN_TEXTURE_FILL) return@mapNotNull null
                if (hasTextLikeEdgeDistribution(edges, bounds)) {
                    Log.v(TAG, "detect: reject text-like texture rectangle ${bounds.width}x${bounds.height}")
                    return@mapNotNull null
                }
                PixelCandidate(
                    bounds,
                    score = 0.55 + coverage * 0.25 + fill * 0.15,
                    evidence = "texture"
                )
            }
        } finally {
            contours.forEach(MatOfPoint::release)
            hierarchy.release()
        }
    }

    private fun edgeCellCoverage(edges: Mat, bounds: Rect): Double {
        var occupied = 0
        var cells = 0
        for (row in 0 until EDGE_GRID_SIZE) {
            val top = bounds.y + bounds.height * row / EDGE_GRID_SIZE
            val bottom = bounds.y + bounds.height * (row + 1) / EDGE_GRID_SIZE
            for (column in 0 until EDGE_GRID_SIZE) {
                val left = bounds.x + bounds.width * column / EDGE_GRID_SIZE
                val right = bounds.x + bounds.width * (column + 1) / EDGE_GRID_SIZE
                if (right <= left || bottom <= top) continue
                val cell = edges.submat(Rect(left, top, right - left, bottom - top))
                try {
                    val density = Core.countNonZero(cell).toDouble() / cell.total().coerceAtLeast(1L)
                    if (density >= MIN_CELL_EDGE_DENSITY) occupied++
                    cells++
                } finally {
                    cell.release()
                }
            }
        }
        return if (cells == 0) 0.0 else occupied.toDouble() / cells
    }

    private fun hasTextLikeEdgeDistribution(edges: Mat, bounds: Rect): Boolean {
        val rowEdgeCounts = IntArray(bounds.height)
        val rowPixels = ByteArray(bounds.width)
        for (row in 0 until bounds.height) {
            edges.get(bounds.y + row, bounds.x, rowPixels)
            rowEdgeCounts[row] = rowPixels.count { it.toInt() != 0 }
        }
        return TextEdgePattern.isLikelyPlainText(rowEdgeCounts, bounds.width)
    }

    /** Fraction of pixels changing across each neighboring row/column boundary. */
    private fun adjacentTransitionScores(gray: Mat, horizontal: Boolean): DoubleArray {
        val difference = Mat()
        val significantDifference = Mat()
        val reduced = Mat()
        val first = if (horizontal) {
            gray.rowRange(1, gray.rows())
        } else {
            gray.colRange(1, gray.cols())
        }
        val second = if (horizontal) {
            gray.rowRange(0, gray.rows() - 1)
        } else {
            gray.colRange(0, gray.cols() - 1)
        }
        return try {
            Core.absdiff(first, second, difference)
            // A boundary should change a meaningful fraction of the row/column. Averaging raw
            // deltas lets one high-contrast object masquerade as a gallery divider; measuring
            // coverage requires the transition to be distributed across the screenshot.
            Imgproc.threshold(
                difference,
                significantDifference,
                MIN_ADJACENT_PIXEL_DELTA,
                255.0,
                Imgproc.THRESH_BINARY
            )
            Core.reduce(
                significantDifference,
                reduced,
                if (horizontal) 1 else 0,
                Core.REDUCE_AVG,
                CvType.CV_32F
            )
            val count = if (horizontal) reduced.rows() else reduced.cols()
            DoubleArray(count) { index ->
                reduced.get(if (horizontal) index else 0, if (horizontal) 0 else index)[0]
            }
        } finally {
            first.release()
            second.release()
            difference.release()
            significantDifference.release()
            reduced.release()
        }
    }

    /** Mean per-column change between stable pixel bands on opposite sides of each row. */
    private fun sustainedRowTransitionScores(gray: Mat): DoubleArray {
        val smoothed = Mat()
        val difference = Mat()
        val reduced = Mat()
        val result = DoubleArray(gray.rows() - 1)
        if (gray.rows() <= SUSTAINED_TRANSITION_SPAN * 2) return result

        Imgproc.blur(
            gray,
            smoothed,
            Size(1.0, SUSTAINED_TRANSITION_BAND.toDouble())
        )
        val upper = smoothed.rowRange(0, smoothed.rows() - SUSTAINED_TRANSITION_SPAN * 2)
        val lower = smoothed.rowRange(SUSTAINED_TRANSITION_SPAN * 2, smoothed.rows())
        return try {
            Core.absdiff(upper, lower, difference)
            Core.reduce(difference, reduced, 1, Core.REDUCE_AVG, CvType.CV_32F)
            for (row in 0 until reduced.rows()) {
                val boundary = row + SUSTAINED_TRANSITION_SPAN
                if (boundary in result.indices) result[boundary] = reduced.get(row, 0)[0]
            }
            result
        } finally {
            upper.release()
            lower.release()
            smoothed.release()
            difference.release()
            reduced.release()
        }
    }

    private fun hasPlausibleGeometry(bounds: Rect, imageWidth: Int, imageHeight: Int): Boolean {
        val widthRatio = bounds.width.toDouble() / imageWidth
        val heightRatio = bounds.height.toDouble() / imageHeight
        val areaRatio = bounds.area() / (imageWidth.toDouble() * imageHeight)
        val aspect = bounds.width.toDouble() / bounds.height.coerceAtLeast(1)
        val frameMarginX = imageWidth * FRAME_MARGIN_RATIO
        val hasAllowedHorizontalBounds = if (config.allowHorizontalFrameEdges) {
            bounds.x >= 0 && bounds.x + bounds.width <= imageWidth
        } else {
            bounds.x > frameMarginX && bounds.x + bounds.width < imageWidth - frameMarginX
        }
        return widthRatio >= MIN_WIDTH_RATIO &&
            heightRatio >= MIN_HEIGHT_RATIO &&
            areaRatio <= config.maxAreaRatio &&
            aspect in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO &&
            hasAllowedHorizontalBounds
    }

    private fun selectCandidates(candidates: List<PixelCandidate>): List<PixelCandidate> = candidates
        .sortedByDescending { it.score }
        .fold(mutableListOf<PixelCandidate>()) { selected, candidate ->
            val isDuplicate = selected.any { existing ->
                intersectionOverUnion(existing.bounds, candidate.bounds) >= DUPLICATE_IOU ||
                    containment(existing.bounds, candidate.bounds) >= DUPLICATE_CONTAINMENT
            }
            if (!isDuplicate && selected.size < config.maxRegions) selected += candidate
            selected
        }
        .sortedWith(compareBy({ it.bounds.y }, { it.bounds.x }))

    private fun intersectionOverUnion(first: Rect, second: Rect): Double {
        val intersection = intersectionArea(first, second)
        return intersection / (first.area() + second.area() - intersection).coerceAtLeast(1.0)
    }

    private fun containment(first: Rect, second: Rect): Double =
        intersectionArea(first, second) / min(first.area(), second.area()).coerceAtLeast(1.0)

    private fun intersectionArea(first: Rect, second: Rect): Double {
        val width = (min(first.x + first.width, second.x + second.width) - max(first.x, second.x))
            .coerceAtLeast(0)
        val height = (min(first.y + first.height, second.y + second.height) - max(first.y, second.y))
            .coerceAtLeast(0)
        return width.toDouble() * height
    }

    private fun Mat.toDetectedSegments(): List<DetectedLineSegment> {
        val result = ArrayList<DetectedLineSegment>(rows())
        val coordinates = IntArray(4)
        for (row in 0 until rows()) {
            get(row, 0, coordinates)
            result += DetectedLineSegment(
                coordinates[0].toDouble(),
                coordinates[1].toDouble(),
                coordinates[2].toDouble(),
                coordinates[3].toDouble()
            )
        }
        return result
    }

    private fun Rect.expand(padding: Int, imageWidth: Int, imageHeight: Int): Rect {
        val left = (x - padding).coerceAtLeast(0)
        val top = (y - padding).coerceAtLeast(0)
        val right = (x + width + padding).coerceAtMost(imageWidth)
        val bottom = (y + height + padding).coerceAtMost(imageHeight)
        return Rect(left, top, right - left, bottom - top)
    }

    private fun ensureAvailable(): Boolean {
        if (!initializationAttempted) {
            initializationAttempted = true
            available = runCatching { OpenCVLoader.initLocal() }
                .onFailure { Log.e(TAG, "OpenCV initialization failed", it) }
                .getOrDefault(false)
            if (available) Log.i(TAG, "OpenCV initialized (media region detector active)")
            else Log.e(TAG, "OpenCV initialization returned false")
        }
        return available
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.3f", value)

    private data class PixelCandidate(
        val bounds: Rect,
        val score: Double,
        val evidence: String
    )

    internal data class Config(
        val allowHorizontalFrameEdges: Boolean,
        val maxAreaRatio: Double,
        val maxRegions: Int,
        val inferInstagramLayouts: Boolean
    ) {
        companion object {
            val DEFAULT = Config(
                allowHorizontalFrameEdges = false,
                maxAreaRatio = 0.72,
                maxRegions = 8,
                inferInstagramLayouts = false
            )
            val INSTAGRAM = Config(
                allowHorizontalFrameEdges = true,
                maxAreaRatio = 0.95,
                maxRegions = 18,
                inferInstagramLayouts = true
            )
        }
    }

    private companion object {
        private const val TAG = "SinShield"
        private const val ANALYSIS_MAX_WIDTH = 720.0
        private const val BLUR_SIZE = 5.0
        private const val CANNY_LOW = 40.0
        private const val CANNY_HIGH = 120.0
        // Instagram profile/Explore thumbnails are ~4:5 portrait; a small margin absorbs the gutter.
        private const val INSTAGRAM_GRID_CELL_MAX_ASPECT = 1.28
        private const val MIN_ADJACENT_PIXEL_DELTA = 15.0
        private const val SUSTAINED_TRANSITION_BAND = 15
        private const val SUSTAINED_TRANSITION_SPAN = 12
        private const val TEXTURE_WINDOW = 17.0
        private const val MIN_LOCAL_EDGE_DENSITY = 8.0
        private const val TEXTURE_BOUNDS_PADDING = 10
        private const val EDGE_GRID_SIZE = 4
        private const val MIN_CELL_EDGE_DENSITY = 0.012
        private const val MIN_EDGE_CELL_COVERAGE = 0.50
        private const val MIN_TEXTURE_CELL_COVERAGE = 0.56
        private const val MIN_BORDER_RECTANGULARITY = 0.72
        private const val MIN_TEXTURE_FILL = 0.25
        private const val FRAME_MARGIN_RATIO = 0.008
        private const val MIN_WIDTH_RATIO = 0.16
        private const val MIN_HEIGHT_RATIO = 0.075
        private const val MIN_ASPECT_RATIO = 0.20
        private const val MAX_ASPECT_RATIO = 5.0
        private const val HOUGH_RHO = 1.0
        private const val HOUGH_THRESHOLD = 18
        private const val MIN_HOUGH_LINE_RATIO = 0.06
        private const val MAX_HOUGH_GAP_RATIO = 0.04
        private const val DUPLICATE_IOU = 0.70
        private const val DUPLICATE_CONTAINMENT = 0.86
        private const val MIN_CONFIRMED_GRID_CELLS = 3
        private const val GRID_HEADER_TOLERANCE = 4
    }
}
