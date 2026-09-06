package com.example.sinshield

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Combines Instagram's visual crops with media bounds exposed by its accessibility hierarchy. */
internal object InstagramLocalizedRegionPlanner : LocalizedAnalysisRegionPlanner {
    override fun plan(
        bitmapWidth: Int,
        bitmapHeight: Int,
        visualRegions: List<DetectionRegion>,
        context: LocalizedAnalysisContext
    ): List<DetectionRegion> {
        require(bitmapWidth > 0 && bitmapHeight > 0) { "Bitmap dimensions must be positive" }

        val accessibilityRegions = context.accessibilityMediaRegions.filter { region ->
            region.source == DetectionRegionSource.ACCESSIBILITY &&
                region.width >= MIN_ACCESSIBILITY_WIDTH &&
                region.height >= MIN_ACCESSIBILITY_HEIGHT &&
                region.area >= MIN_ACCESSIBILITY_AREA
        }
        val orderedAccessibilityRegions = if (isGridSurface(context)) {
            // Instagram often exposes both the gallery container and its children. Let the cells
            // win containment de-duplication so a broad parent cannot erase every thumbnail.
            accessibilityRegions.sortedBy(DetectionRegion::area)
        } else {
            accessibilityRegions.sortedByDescending(DetectionRegion::area)
        }
        val candidates = visualRegions + orderedAccessibilityRegions
        return candidates
            .filter(::hasValidBounds)
            .fold(mutableListOf<DetectionRegion>()) { selected, candidate ->
                val duplicate = selected.any { existing ->
                    normalizedIntersectionOverUnion(existing, candidate) >= DUPLICATE_IOU ||
                        normalizedContainment(existing, candidate) >= DUPLICATE_CONTAINMENT
                }
                if (!duplicate && selected.size < MAX_INSTAGRAM_REGIONS) selected += candidate
                selected
            }
            .sortedWith(compareBy({ it.top }, { it.left }))
    }

    fun isGridSurface(context: LocalizedAnalysisContext): Boolean {
        if (context.screenMode == ShieldedScreenMode.EXPLORE) return true
        val ids = context.screenSignals.viewIds
        val isPostViewer = ids.any { id -> POST_VIEWER_HINTS.any(id::contains) }
        if (isPostViewer) return false
        if (ids.any { id -> GRID_SURFACE_HINTS.any(id::contains) }) return true
        val labels = context.screenSignals.labels
        val profileEvidence = PROFILE_GRID_LABELS.count { expected ->
            labels.any { it == expected || it.startsWith("$expected ") }
        }
        val searchEvidence = labels.any { it == "search" || it.startsWith("search ") }
        return profileEvidence >= 2 || searchEvidence
    }

    private fun hasValidBounds(region: DetectionRegion): Boolean =
        region.left in 0f..1f && region.top in 0f..1f &&
            region.right in 0f..1f && region.bottom in 0f..1f &&
            region.right > region.left && region.bottom > region.top

    private fun normalizedContainment(first: DetectionRegion, second: DetectionRegion): Float {
        val width = (min(first.right, second.right) - max(first.left, second.left)).coerceAtLeast(0f)
        val height = (min(first.bottom, second.bottom) - max(first.top, second.top)).coerceAtLeast(0f)
        return width * height / min(first.area, second.area).coerceAtLeast(0.000001f)
    }

    private const val MIN_ACCESSIBILITY_WIDTH = 0.12f
    private const val MIN_ACCESSIBILITY_HEIGHT = 0.06f
    private const val MIN_ACCESSIBILITY_AREA = 0.012f
    private const val DUPLICATE_IOU = 0.72f
    private const val DUPLICATE_CONTAINMENT = 0.90f
    private const val MAX_INSTAGRAM_REGIONS = 18

    private val GRID_SURFACE_HINTS = listOf(
        "grid",
        "profile_header",
        "profile_tab",
        "profile_viewpager",
        "explore_grid",
        "search_grid"
    )
    private val POST_VIEWER_HINTS = listOf(
        "post_viewer",
        "clips_viewer",
        "reel_viewer",
        "media_viewer"
    )
    private val PROFILE_GRID_LABELS = setOf("posts", "followers", "following")
}

/**
 * Reconstructs Instagram's three-column thumbnail sheet from horizontal row separators.
 *
 * Adjacent thumbnails often have no useful vertical edge between them. Once accessibility says
 * that the current surface is a grid, two long horizontal separators are enough to establish a
 * row; the column boundaries are the stable thirds of Instagram's full-width gallery.
 */
internal object InstagramGridRegionAssembler {
    fun assemble(
        imageWidth: Int,
        imageHeight: Int,
        rawSegments: List<DetectedLineSegment>,
        maxResults: Int = 18
    ): List<SupportedRectangle> {
        if (imageWidth <= 0 || imageHeight <= 0 || rawSegments.isEmpty()) return emptyList()

        val axes = clusterHorizontalAxes(rawSegments, imageWidth)
            .filter { axis ->
                axis.position >= imageHeight * MIN_GALLERY_TOP_RATIO &&
                    axis.position <= imageHeight * MAX_GALLERY_BOTTOM_RATIO &&
                    axis.coverage >= MIN_HORIZONTAL_COVERAGE
            }

        val firstPair = axes.indices.asSequence().flatMap { topIndex ->
            (topIndex + 1 until axes.size).asSequence().map { bottomIndex ->
                AxisPair(axes[topIndex], axes[bottomIndex])
            }
        }.filter { pair ->
            isPlausibleRowHeight(pair.height, imageWidth, allowClipped = false) &&
                hasThreeColumnDividers(rawSegments, pair, imageWidth)
        }
            // Gallery dividers span the viewport. Prefer that evidence over an earlier card edge
            // in the profile header, then choose the upper row when coverage ties.
            .maxWithOrNull(compareBy<AxisPair>({ it.combinedCoverage }, { -it.top.position }))

        if (firstPair == null) {
            return assembleFromVerticalDividers(imageWidth, imageHeight, rawSegments, maxResults)
        }

        val rowAxes = mutableListOf(firstPair.top, firstPair.bottom)
        var expectedPitch = firstPair.height
        while (rowAxes.size <= MAX_GRID_ROWS) {
            val previous = rowAxes.last()
            val next = axes.asSequence()
                .filter { it.position > previous.position + MIN_DISTINCT_AXIS_GAP }
                .filter {
                    isPlausibleRowHeight(it.position - previous.position, imageWidth, allowClipped = true)
                }
                .minByOrNull { abs((it.position - previous.position) - expectedPitch) }
                ?: break
            val gap = next.position - previous.position
            if (gap > expectedPitch * MAX_PITCH_DEVIATION && gap < imageWidth * MIN_FULL_ROW_RATIO) {
                break
            }
            rowAxes += next
            if (gap >= imageWidth * MIN_FULL_ROW_RATIO) {
                expectedPitch = (expectedPitch + gap) / 2.0
            }
        }

        val columnWidth = imageWidth / GRID_COLUMNS.toDouble()
        val rectangles = mutableListOf<SupportedRectangle>()
        for ((top, bottom) in rowAxes.zipWithNext()) {
            if (!isPlausibleRowHeight(bottom.position - top.position, imageWidth, allowClipped = true)) {
                continue
            }
            for (column in 0 until GRID_COLUMNS) {
                if (rectangles.size >= maxResults) return rectangles
                rectangles += SupportedRectangle(
                    left = column * columnWidth,
                    top = top.position,
                    right = if (column == GRID_COLUMNS - 1) imageWidth.toDouble() else (column + 1) * columnWidth,
                    bottom = bottom.position,
                    sideSupport = listOf(top.coverage, 1.0, bottom.coverage, 1.0),
                    isGridCell = true
                )
            }
        }
        return rectangles
    }

    /**
     * A grid must not disappear merely because thumbnail content hides a horizontal separator.
     * Instagram's two column seams are much more stable: once both seams cover a common gallery
     * band, its 3:4 thumbnail geometry determines the row boundaries without inspecting content.
     */
    private fun assembleFromVerticalDividers(
        imageWidth: Int,
        imageHeight: Int,
        rawSegments: List<DetectedLineSegment>,
        maxResults: Int
    ): List<SupportedRectangle> {
        val dividerBands = (1 until GRID_COLUMNS).mapNotNull { divider ->
            verticalDividerBand(rawSegments, imageWidth, imageHeight, divider)
        }
        if (dividerBands.size != GRID_COLUMNS - 1) return emptyList()

        val galleryTop = dividerBands.maxOf(VerticalBand::top)
        val galleryBottom = dividerBands.minOf(VerticalBand::bottom)
        val galleryHeight = galleryBottom - galleryTop
        if (galleryHeight < imageWidth * MIN_VERTICAL_GRID_BAND_RATIO) return emptyList()

        val expectedRowHeight = imageWidth * INSTAGRAM_TILE_HEIGHT_TO_SCREEN_WIDTH
        val rowAxes = mutableListOf(galleryTop)
        while (rowAxes.size <= MAX_GRID_ROWS && rowAxes.last() + expectedRowHeight < galleryBottom) {
            rowAxes += rowAxes.last() + expectedRowHeight
        }
        if (galleryBottom - rowAxes.last() >= imageWidth * MIN_CLIPPED_ROW_RATIO) {
            rowAxes += galleryBottom
        }
        if (rowAxes.size < 2) return emptyList()

        val columnWidth = imageWidth / GRID_COLUMNS.toDouble()
        return rowAxes.zipWithNext().flatMap { (top, bottom) ->
            (0 until GRID_COLUMNS).map { column ->
                SupportedRectangle(
                    left = column * columnWidth,
                    top = top,
                    right = if (column == GRID_COLUMNS - 1) {
                        imageWidth.toDouble()
                    } else {
                        (column + 1) * columnWidth
                    },
                    bottom = bottom,
                    sideSupport = listOf(1.0, 1.0, 1.0, 1.0),
                    isGridCell = true
                )
            }
        }.take(maxResults)
    }

    private fun verticalDividerBand(
        rawSegments: List<DetectedLineSegment>,
        imageWidth: Int,
        imageHeight: Int,
        divider: Int
    ): VerticalBand? {
        val expectedX = imageWidth * divider / GRID_COLUMNS.toDouble()
        val intervals = rawSegments.mapNotNull { line ->
            val dx = abs(line.x2 - line.x1)
            val dy = abs(line.y2 - line.y1)
            val x = (line.x1 + line.x2) / 2.0
            if (dy < imageWidth * MIN_VERTICAL_SEGMENT_RATIO ||
                dx > dy * MAX_VERTICAL_SLOPE ||
                abs(x - expectedX) > imageWidth * DIVIDER_POSITION_TOLERANCE
            ) {
                null
            } else {
                min(line.y1, line.y2).coerceIn(0.0, imageHeight.toDouble()) to
                    max(line.y1, line.y2).coerceIn(0.0, imageHeight.toDouble())
            }
        }.filter { it.second > it.first }.sortedBy { it.first }
        if (intervals.isEmpty()) return null

        val joined = mergeIntervals(intervals)
        val strongest = joined.maxByOrNull { it.second - it.first } ?: return null
        if (strongest.second - strongest.first < imageWidth * MIN_VERTICAL_GRID_BAND_RATIO) {
            return null
        }
        return VerticalBand(strongest.first, strongest.second)
    }

    private fun clusterHorizontalAxes(
        rawSegments: List<DetectedLineSegment>,
        imageWidth: Int
    ): List<HorizontalAxis> {
        val segments = rawSegments.mapNotNull { line ->
            val dx = abs(line.x2 - line.x1)
            val dy = abs(line.y2 - line.y1)
            if (dx < imageWidth * MIN_SEGMENT_WIDTH_RATIO || dy > dx * MAX_AXIS_SLOPE) {
                null
            } else {
                HorizontalSegment(
                    position = (line.y1 + line.y2) / 2.0,
                    left = min(line.x1, line.x2).coerceIn(0.0, imageWidth.toDouble()),
                    right = max(line.x1, line.x2).coerceIn(0.0, imageWidth.toDouble())
                )
            }
        }.sortedBy(HorizontalSegment::position)

        val clusters = mutableListOf<MutableList<HorizontalSegment>>()
        for (segment in segments) {
            val current = clusters.lastOrNull()
            val position = current?.map(HorizontalSegment::position)?.average()
            if (current == null || position == null ||
                abs(segment.position - position) > AXIS_POSITION_TOLERANCE
            ) {
                clusters += mutableListOf(segment)
            } else {
                current += segment
            }
        }
        return clusters.map { cluster ->
            val intervals = cluster.map { it.left to it.right }.sortedBy { it.first }
            var covered = 0.0
            var activeLeft = intervals.first().first
            var activeRight = intervals.first().second
            for ((left, right) in intervals.drop(1)) {
                if (left > activeRight + INTERVAL_JOIN_GAP) {
                    covered += activeRight - activeLeft
                    activeLeft = left
                    activeRight = right
                } else {
                    activeRight = max(activeRight, right)
                }
            }
            covered += activeRight - activeLeft
            HorizontalAxis(
                position = cluster.map(HorizontalSegment::position).average(),
                coverage = (covered / imageWidth).coerceIn(0.0, 1.0),
                reachesLeftEdge = intervals.first().first <= imageWidth * VIEWPORT_EDGE_TOLERANCE,
                reachesRightEdge = intervals.maxOf { it.second } >=
                    imageWidth * (1.0 - VIEWPORT_EDGE_TOLERANCE)
            )
        }
    }

    private fun hasThreeColumnDividers(
        rawSegments: List<DetectedLineSegment>,
        row: AxisPair,
        imageWidth: Int
    ): Boolean = (1 until GRID_COLUMNS).all { divider ->
        val expectedX = imageWidth * divider / GRID_COLUMNS.toDouble()
        val intervals = rawSegments.mapNotNull { line ->
            val dx = abs(line.x2 - line.x1)
            val dy = abs(line.y2 - line.y1)
            val x = (line.x1 + line.x2) / 2.0
            if (dy < row.height * MIN_DIVIDER_SEGMENT_RATIO ||
                dx > dy * MAX_VERTICAL_SLOPE ||
                abs(x - expectedX) > imageWidth * DIVIDER_POSITION_TOLERANCE
            ) {
                null
            } else {
                max(min(line.y1, line.y2), row.top.position) to
                    min(max(line.y1, line.y2), row.bottom.position)
            }
        }.filter { it.second > it.first }.sortedBy { it.first }
        mergedCoverage(intervals) / row.height >= MIN_DIVIDER_COVERAGE
    }

    private fun mergedCoverage(intervals: List<Pair<Double, Double>>): Double {
        return mergeIntervals(intervals).sumOf { it.second - it.first }
    }

    private fun mergeIntervals(
        intervals: List<Pair<Double, Double>>
    ): List<Pair<Double, Double>> {
        if (intervals.isEmpty()) return emptyList()
        val merged = mutableListOf<Pair<Double, Double>>()
        var start = intervals.first().first
        var end = intervals.first().second
        for ((nextStart, nextEnd) in intervals.drop(1)) {
            if (nextStart > end + INTERVAL_JOIN_GAP) {
                merged += start to end
                start = nextStart
                end = nextEnd
            } else {
                end = max(end, nextEnd)
            }
        }
        merged += start to end
        return merged
    }

    private fun isPlausibleRowHeight(
        height: Double,
        imageWidth: Int,
        allowClipped: Boolean
    ): Boolean {
        val minimum = imageWidth * if (allowClipped) MIN_CLIPPED_ROW_RATIO else MIN_FULL_ROW_RATIO
        return height in minimum..(imageWidth * MAX_ROW_RATIO)
    }

    private data class HorizontalSegment(val position: Double, val left: Double, val right: Double)
    private data class HorizontalAxis(
        val position: Double,
        val coverage: Double,
        val reachesLeftEdge: Boolean,
        val reachesRightEdge: Boolean
    )
    private data class AxisPair(val top: HorizontalAxis, val bottom: HorizontalAxis) {
        val height: Double get() = bottom.position - top.position
        val combinedCoverage: Double get() = top.coverage + bottom.coverage
    }
    private data class VerticalBand(val top: Double, val bottom: Double)

    private const val GRID_COLUMNS = 3
    private const val MAX_GRID_ROWS = 6
    private const val MIN_GALLERY_TOP_RATIO = 0.08
    private const val MAX_GALLERY_BOTTOM_RATIO = 0.96
    private const val MIN_SEGMENT_WIDTH_RATIO = 0.42
    private const val MIN_HORIZONTAL_COVERAGE = 0.72
    private const val MAX_AXIS_SLOPE = 0.05
    private const val MAX_VERTICAL_SLOPE = 0.08
    private const val AXIS_POSITION_TOLERANCE = 6.0
    private const val INTERVAL_JOIN_GAP = 18.0
    private const val MIN_DISTINCT_AXIS_GAP = 20.0
    private const val MIN_FULL_ROW_RATIO = 0.28
    private const val MIN_CLIPPED_ROW_RATIO = 0.16
    private const val MAX_ROW_RATIO = 0.58
    private const val MAX_PITCH_DEVIATION = 1.45
    private const val VIEWPORT_EDGE_TOLERANCE = 0.025
    private const val DIVIDER_POSITION_TOLERANCE = 0.035
    private const val MIN_DIVIDER_SEGMENT_RATIO = 0.16
    private const val MIN_DIVIDER_COVERAGE = 0.42
    private const val MIN_VERTICAL_SEGMENT_RATIO = 0.12
    private const val MIN_VERTICAL_GRID_BAND_RATIO = 0.72
    private const val INSTAGRAM_TILE_HEIGHT_TO_SCREEN_WIDTH = 4.0 / 9.0
}

/** Finds an edge-to-edge Instagram post between consecutive viewport-spanning separators. */
internal object InstagramPostRegionAssembler {
    fun assemble(
        imageWidth: Int,
        imageHeight: Int,
        rawSegments: List<DetectedLineSegment>
    ): List<SupportedRectangle> {
        if (imageWidth <= 0 || imageHeight <= 0) return emptyList()
        val axes = (listOf(0.0, imageHeight.toDouble()) + rawSegments.mapNotNull { line ->
            val dx = abs(line.x2 - line.x1)
            val dy = abs(line.y2 - line.y1)
            val left = min(line.x1, line.x2)
            val right = max(line.x1, line.x2)
            if (dy > dx * MAX_AXIS_SLOPE ||
                left > imageWidth * EDGE_TOLERANCE ||
                right < imageWidth * (1.0 - EDGE_TOLERANCE)
            ) {
                null
            } else {
                (line.y1 + line.y2) / 2.0
            }
        }).sorted().fold(mutableListOf<Double>()) { selected, position ->
            if (selected.isEmpty() || position - selected.last() > AXIS_MERGE_DISTANCE) {
                selected += position
            } else {
                selected[selected.lastIndex] = (selected.last() + position) / 2.0
            }
            selected
        }

        val best = axes.zipWithNext()
            .map { (top, bottom) -> top to bottom }
            .filter { (top, bottom) ->
                val height = bottom - top
                top <= imageHeight * MAX_MEDIA_TOP_RATIO &&
                    height >= imageHeight * MIN_MEDIA_HEIGHT_RATIO &&
                    height <= imageHeight * MAX_MEDIA_HEIGHT_RATIO
            }
            .maxByOrNull { (top, bottom) -> bottom - top }
            ?: return emptyList()

        return listOf(
            SupportedRectangle(
                left = 0.0,
                top = best.first,
                right = imageWidth.toDouble(),
                bottom = best.second,
                sideSupport = listOf(1.0, 1.0, 1.0, 1.0)
            )
        )
    }

    private const val MAX_AXIS_SLOPE = 0.04
    private const val EDGE_TOLERANCE = 0.025
    private const val AXIS_MERGE_DISTANCE = 7.0
    private const val MAX_MEDIA_TOP_RATIO = 0.55
    private const val MIN_MEDIA_HEIGHT_RATIO = 0.25
    private const val MAX_MEDIA_HEIGHT_RATIO = 0.95
}
