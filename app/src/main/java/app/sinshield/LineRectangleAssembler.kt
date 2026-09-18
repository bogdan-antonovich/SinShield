package app.sinshield

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A raw line segment reported by OpenCV in analysis-image pixels. */
internal data class DetectedLineSegment(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double
)

internal data class SupportedRectangle(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val sideSupport: List<Double>,
    val isGridCell: Boolean = false
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val area: Double get() = width * height
    val score: Double get() = sideSupport.sortedDescending().take(3).average()
}

/**
 * Constructs rectangles from long, axis-aligned side evidence.
 *
 * Unlike boundingRect(contour), this cannot turn an arbitrary closed silhouette into a media box:
 * a result needs independent support for at least three straight sides. The fourth side may be
 * weak because borderless photos and rounded corners often blend into the surrounding surface.
 */
internal object LineRectangleAssembler {
    fun assemble(
        imageWidth: Int,
        imageHeight: Int,
        rawSegments: List<DetectedLineSegment>,
        maxResults: Int = 6
    ): List<SupportedRectangle> {
        if (imageWidth <= 0 || imageHeight <= 0 || rawSegments.isEmpty()) return emptyList()

        val minLineLength = imageWidth * MIN_LINE_LENGTH_RATIO
        val positionTolerance = max(imageWidth, imageHeight) * POSITION_TOLERANCE_RATIO
        val horizontal = mutableListOf<AxisSegment>()
        val vertical = mutableListOf<AxisSegment>()
        for (line in rawSegments) {
            val dx = abs(line.x2 - line.x1)
            val dy = abs(line.y2 - line.y1)
            when {
                dx >= minLineLength && dy <= dx * MAX_AXIS_SLOPE -> horizontal += AxisSegment(
                    position = (line.y1 + line.y2) / 2.0,
                    start = min(line.x1, line.x2),
                    end = max(line.x1, line.x2)
                )
                dy >= minLineLength && dx <= dy * MAX_AXIS_SLOPE -> vertical += AxisSegment(
                    position = (line.x1 + line.x2) / 2.0,
                    start = min(line.y1, line.y2),
                    end = max(line.y1, line.y2)
                )
            }
        }

        val realHorizontalAxes = cluster(horizontal, positionTolerance)
            .filterNot {
                it.position <= imageHeight * FRAME_MARGIN_RATIO ||
                    it.position >= imageHeight * (1.0 - FRAME_MARGIN_RATIO)
            }
            .takeMostUseful(MAX_AXES)
        val horizontalAxes = realHorizontalAxes
            .toMutableList()
            .apply {
                // A photo may continue beyond the top or bottom of the screenshot. Treat the
                // viewport boundary as one supported side, while still requiring the other three
                // sides from image evidence. Horizontal viewport sides are handled separately and
                // only for a proven regular grid, to avoid full-width post-card false positives.
                add(AxisEvidence.viewportBoundary(0.0, imageWidth.toDouble()))
                add(AxisEvidence.viewportBoundary(imageHeight.toDouble(), imageWidth.toDouble()))
                sortBy(AxisEvidence::position)
            }
        val realVerticalAxes = cluster(vertical, positionTolerance)
            .filterNot {
                it.position <= imageWidth * FRAME_MARGIN_RATIO ||
                    it.position >= imageWidth * (1.0 - FRAME_MARGIN_RATIO)
            }
            .takeMostUseful(MAX_AXES)
        val verticalAxes = realVerticalAxes.toMutableList().apply {
            // Instagram-style galleries have no visible outer border: their first and last
            // tiles terminate at the viewport. Only infer those two sides when multiple real,
            // regularly spaced dividers demonstrate a full-width grid. A lone full-width card
            // therefore cannot regain the false-positive behavior this intentionally replaced.
            if (realHorizontalAxes.size >= MIN_GRID_HORIZONTAL_AXES &&
                formsFullWidthGrid(realVerticalAxes, imageWidth)
            ) {
                add(AxisEvidence.viewportBoundary(0.0, imageHeight.toDouble()))
                add(AxisEvidence.viewportBoundary(imageWidth.toDouble(), imageHeight.toDouble()))
                sortBy(AxisEvidence::position)
            }
        }
        if (horizontalAxes.size < 2 || verticalAxes.size < 2) return emptyList()

        val candidates = ArrayList<SupportedRectangle>()
        if (verticalAxes.size > realVerticalAxes.size) {
            candidates += assembleGridCells(
                horizontalAxes = horizontalAxes,
                verticalAxes = verticalAxes,
                imageWidth = imageWidth,
                imageHeight = imageHeight
            )
        }
        for (topIndex in 0 until horizontalAxes.lastIndex) {
            val top = horizontalAxes[topIndex]
            for (bottomIndex in topIndex + 1 until horizontalAxes.size) {
                val bottom = horizontalAxes[bottomIndex]
                if (top.isViewportBoundary && bottom.isViewportBoundary) continue
                val height = bottom.position - top.position
                if (height < imageHeight * MIN_HEIGHT_RATIO) continue
                for (leftIndex in 0 until verticalAxes.lastIndex) {
                    val left = verticalAxes[leftIndex]
                    for (rightIndex in leftIndex + 1 until verticalAxes.size) {
                        val right = verticalAxes[rightIndex]
                        if (left.isViewportBoundary && right.isViewportBoundary) continue
                        val width = right.position - left.position
                        if (width < imageWidth * MIN_WIDTH_RATIO) continue
                        val aspect = width / height
                        val areaRatio = width * height / (imageWidth.toDouble() * imageHeight)
                        if (aspect !in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO ||
                            areaRatio > MAX_AREA_RATIO
                        ) continue

                        val support = listOf(
                            top.coverage(left.position, right.position),
                            right.coverage(top.position, bottom.position),
                            bottom.coverage(left.position, right.position),
                            left.coverage(top.position, bottom.position)
                        )
                        if (support.count { it >= MIN_SIDE_SUPPORT } < MIN_SUPPORTED_SIDES) continue
                        val cornerTolerance = min(width, height) * CORNER_TOLERANCE_RATIO
                        val supportedCorners = listOf(
                            top.reaches(left.position, cornerTolerance) &&
                                left.reaches(top.position, cornerTolerance),
                            top.reaches(right.position, cornerTolerance) &&
                                right.reaches(top.position, cornerTolerance),
                            bottom.reaches(right.position, cornerTolerance) &&
                                right.reaches(bottom.position, cornerTolerance),
                            bottom.reaches(left.position, cornerTolerance) &&
                                left.reaches(bottom.position, cornerTolerance)
                        ).count { it }
                        if (supportedCorners < MIN_SUPPORTED_CORNERS) continue
                        val candidate = SupportedRectangle(
                            left.position,
                            top.position,
                            right.position,
                            bottom.position,
                            support
                        )
                        if (candidate.score >= MIN_RECTANGLE_SCORE) candidates += candidate
                    }
                }
            }
        }

        // Prefer well-supported, specific rectangles. The small specificity term makes individual
        // collage tiles win over an equally supported outer group without favoring tiny noise.
        return candidates
            .sortedByDescending { candidate ->
                candidate.score + SPECIFICITY_WEIGHT *
                    (1.0 - candidate.area / (imageWidth.toDouble() * imageHeight))
            }
            .fold(mutableListOf()) { selected, candidate ->
                if (selected.size < maxResults && selected.none { conflicts(it, candidate) }) {
                    selected += candidate
                }
                selected
            }
    }

    private fun List<AxisEvidence>.takeMostUseful(limit: Int): List<AxisEvidence> =
        if (size <= limit) this else sortedByDescending(AxisEvidence::totalLength)
            .take(limit)
            .sortedBy(AxisEvidence::position)

    private fun formsFullWidthGrid(axes: List<AxisEvidence>, imageWidth: Int): Boolean {
        if (axes.size < MIN_GRID_VERTICAL_DIVIDERS || imageWidth <= 0) return false
        val positions = axes.map(AxisEvidence::position).sorted()
        val gaps = positions.zipWithNext { first, second -> second - first }
        val spacing = gaps.average()
        if (spacing < imageWidth * MIN_GRID_COLUMN_WIDTH_RATIO) return false
        val tolerance = spacing * GRID_SPACING_TOLERANCE_RATIO
        return gaps.all { abs(it - spacing) <= tolerance } &&
            abs(positions.first() - spacing) <= tolerance &&
            abs((imageWidth - positions.last()) - spacing) <= tolerance
    }

    private fun assembleGridCells(
        horizontalAxes: List<AxisEvidence>,
        verticalAxes: List<AxisEvidence>,
        imageWidth: Int,
        imageHeight: Int
    ): List<SupportedRectangle> {
        val rowAxes = horizontalAxes.filter { axis ->
            axis.isViewportBoundary ||
                axis.coverage(0.0, imageWidth.toDouble()) >= MIN_GRID_HORIZONTAL_SUPPORT
        }
        if (rowAxes.size < 2) return emptyList()

        val cells = mutableListOf<SupportedRectangle>()
        for ((top, bottom) in rowAxes.zipWithNext()) {
            if (top.isViewportBoundary && bottom.isViewportBoundary) continue
            val height = bottom.position - top.position
            if (height < imageHeight * MIN_HEIGHT_RATIO) continue
            for ((left, right) in verticalAxes.zipWithNext()) {
                val width = right.position - left.position
                val aspect = width / height
                if (width < imageWidth * MIN_WIDTH_RATIO ||
                    aspect !in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO
                ) continue

                val support = listOf(
                    top.coverage(left.position, right.position),
                    right.coverage(top.position, bottom.position),
                    bottom.coverage(left.position, right.position),
                    left.coverage(top.position, bottom.position)
                )
                if (support[0] < MIN_GRID_HORIZONTAL_SUPPORT ||
                    support[2] < MIN_GRID_HORIZONTAL_SUPPORT ||
                    support[1] < MIN_GRID_VERTICAL_SUPPORT ||
                    support[3] < MIN_GRID_VERTICAL_SUPPORT
                ) continue
                cells += SupportedRectangle(
                    left = left.position,
                    top = top.position,
                    right = right.position,
                    bottom = bottom.position,
                    sideSupport = support,
                    isGridCell = true
                )
            }
        }
        return cells
    }

    private fun cluster(
        segments: List<AxisSegment>,
        positionTolerance: Double
    ): List<AxisEvidence> {
        if (segments.isEmpty()) return emptyList()
        val clusters = mutableListOf<MutableList<AxisSegment>>()
        for (segment in segments.sortedBy(AxisSegment::position)) {
            val current = clusters.lastOrNull()
            val weightedPosition = current?.let { items ->
                items.sumOf { it.position * it.length } / items.sumOf(AxisSegment::length)
            }
            if (current == null || weightedPosition == null ||
                abs(segment.position - weightedPosition) > positionTolerance
            ) {
                clusters += mutableListOf(segment)
            } else {
                current += segment
            }
        }
        return clusters.map { items ->
            val totalLength = items.sumOf(AxisSegment::length)
            AxisEvidence(
                position = items.sumOf { it.position * it.length } / totalLength,
                intervals = mergeIntervals(items.map { Interval(it.start, it.end) }),
                totalLength = totalLength
            )
        }
    }

    private fun mergeIntervals(intervals: List<Interval>): List<Interval> {
        val merged = mutableListOf<Interval>()
        for (interval in intervals.sortedBy(Interval::start)) {
            val previous = merged.lastOrNull()
            if (previous == null || interval.start > previous.end + INTERVAL_JOIN_GAP) {
                merged += interval
            } else if (interval.end > previous.end) {
                merged[merged.lastIndex] = previous.copy(end = interval.end)
            }
        }
        return merged
    }

    private fun conflicts(first: SupportedRectangle, second: SupportedRectangle): Boolean {
        val intersectionWidth = (min(first.right, second.right) - max(first.left, second.left))
            .coerceAtLeast(0.0)
        val intersectionHeight = (min(first.bottom, second.bottom) - max(first.top, second.top))
            .coerceAtLeast(0.0)
        val intersection = intersectionWidth * intersectionHeight
        if (intersection <= 0.0) return false
        val iou = intersection / (first.area + second.area - intersection)
        val containment = intersection / min(first.area, second.area)
        return iou >= DUPLICATE_IOU || containment >= CONTAINMENT_THRESHOLD
    }

    private data class AxisSegment(val position: Double, val start: Double, val end: Double) {
        val length: Double get() = end - start
    }

    private data class Interval(val start: Double, val end: Double)

    private data class AxisEvidence(
        val position: Double,
        val intervals: List<Interval>,
        val totalLength: Double,
        val isViewportBoundary: Boolean = false
    ) {
        fun coverage(start: Double, end: Double): Double {
            if (end <= start) return 0.0
            val covered = intervals.sumOf { interval ->
                (min(interval.end, end) - max(interval.start, start)).coerceAtLeast(0.0)
            }
            return (covered / (end - start)).coerceIn(0.0, 1.0)
        }

        fun reaches(point: Double, tolerance: Double): Boolean = intervals.any { interval ->
            interval.start <= point + tolerance && interval.end >= point - tolerance
        }

        companion object {
            fun viewportBoundary(position: Double, width: Double) = AxisEvidence(
                position = position,
                intervals = listOf(Interval(0.0, width)),
                totalLength = width,
                isViewportBoundary = true
            )
        }
    }

    private const val MAX_AXIS_SLOPE = 0.10
    private const val MIN_LINE_LENGTH_RATIO = 0.10
    private const val POSITION_TOLERANCE_RATIO = 0.006
    private const val FRAME_MARGIN_RATIO = 0.012
    private const val MIN_WIDTH_RATIO = 0.18
    private const val MIN_HEIGHT_RATIO = 0.08
    private const val MIN_ASPECT_RATIO = 0.22
    private const val MAX_ASPECT_RATIO = 4.5
    private const val MAX_AREA_RATIO = 0.85
    private const val MIN_SIDE_SUPPORT = 0.42
    private const val MIN_SUPPORTED_SIDES = 3
    private const val CORNER_TOLERANCE_RATIO = 0.08
    private const val MIN_SUPPORTED_CORNERS = 2
    private const val MIN_RECTANGLE_SCORE = 0.48
    private const val SPECIFICITY_WEIGHT = 0.08
    private const val INTERVAL_JOIN_GAP = 12.0
    private const val DUPLICATE_IOU = 0.72
    private const val CONTAINMENT_THRESHOLD = 0.86
    private const val MAX_AXES = 24
    private const val MIN_GRID_VERTICAL_DIVIDERS = 2
    private const val MIN_GRID_HORIZONTAL_AXES = 2
    private const val MIN_GRID_COLUMN_WIDTH_RATIO = 0.16
    private const val GRID_SPACING_TOLERANCE_RATIO = 0.14
    private const val MIN_GRID_HORIZONTAL_SUPPORT = 0.38
    private const val MIN_GRID_VERTICAL_SUPPORT = 0.20
}
