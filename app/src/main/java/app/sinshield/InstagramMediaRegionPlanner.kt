package app.sinshield

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
        val gridSurface = isGridSurface(context)
        val postSurface = !gridSurface && isPostSurface(context)
        val orderedAccessibilityRegions = if (gridSurface) {
            // Instagram often exposes both the gallery container and its children. Let the cells
            // win containment de-duplication so a broad parent cannot erase every thumbnail.
            accessibilityRegions.sortedBy(DetectionRegion::area)
        } else {
            accessibilityRegions.sortedByDescending(DetectionRegion::area)
        }
        val validVisualRegions = visualRegions.filter(::hasValidBounds)
        if (postSurface && validVisualRegions.isNotEmpty()) {
            // A measured OpenCV post is authoritative. Mixing accessibility rectangles into this
            // list made the green OpenCV result diverge from the crops actually sent to models.
            // Keep both coordinates and ordering unchanged: OpenCV rectangle N is model crop N.
            return validVisualRegions.take(MAX_INSTAGRAM_REGIONS)
        }
        // On a grid the thumbnail sheet begins at the first thumbnail the accessibility tree
        // exposes; the profile header (bio, music player, dashboard card, buttons, tab bar) sits
        // above it. The transition detector can lock its row phase onto that evenly-spaced header
        // chrome and emit a whole column of cells there, so discard any visual cell that starts
        // above the first real thumbnail. This needs measured thumbnail cells — a broad gallery
        // container is not a floor — and does nothing when accessibility exposes no cells.
        val gridScopedVisualRegions = if (gridSurface) {
            val gridTopFloor = accessibilityRegions
                .filterNot(::isBroadGridContainer)
                .minByOrNull(DetectionRegion::top)
                ?.let { firstCell -> firstCell.top - firstCell.height * GRID_TOP_FLOOR_TOLERANCE }
            if (gridTopFloor != null) {
                validVisualRegions.filter { it.top >= gridTopFloor }
            } else {
                validVisualRegions
            }
        } else {
            validVisualRegions
        }
        val rawCandidates = gridScopedVisualRegions + orderedAccessibilityRegions
        val candidates = if (gridSurface) {
            // A gallery parent is navigation/layout evidence, not proof that every geometric slot
            // contains media. Individual cells must come from measured visual or accessibility
            // bounds; never subdivide this container speculatively.
            rawCandidates.filterNot(::isBroadGridContainer)
        } else {
            // Feed accessibility commonly exposes the whole RecyclerView/card rather than its
            // media child. Never let that near-full-screen container become a classifier crop.
            rawCandidates.filterNot { region ->
                context.screenMode != ShieldedScreenMode.REELS &&
                    context.screenMode != ShieldedScreenMode.STORY &&
                    region.width >= BROAD_CONTAINER_MIN_WIDTH &&
                    region.height >= BROAD_FEED_CONTAINER_MIN_HEIGHT
            }
        }
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
        val labels = context.screenSignals.labels
        val profileEvidence = PROFILE_GRID_LABELS.count { expected ->
            labels.any { it == expected || it.startsWith("$expected ") }
        }
        if (profileEvidence >= 2) return true

        // The bottom navigation exposes a "Search" accessibility label on posts as well as on
        // Explore. A visible "Posts" viewer title wins over that weak, shared navigation label;
        // actual Explore/grid ids and a real profile-label quorum have already won above.
        if (labels.any(::isPostsViewerTitle)) return false
        if (ids.any { id -> GRID_SURFACE_HINTS.any(id::contains) }) return true
        val searchEvidence = labels.any { it == "search" || it.startsWith("search ") }
        return searchEvidence
    }

    fun isPostSurface(context: LocalizedAnalysisContext): Boolean {
        if (isGridSurface(context)) return false
        if (context.screenMode in setOf(
                ShieldedScreenMode.FEED,
                ShieldedScreenMode.PROFILE_OR_POST
            )
        ) return true

        // Instagram's profile-post viewer sometimes exposes only its visible title ("Posts")
        // and no stable resource id. Recovery therefore classifies that sparse hierarchy as
        // UNKNOWN, but the exact title is still sufficient to enable visual boundary detection.
        // A profile gallery also exposes "Posts"; isGridSurface above has already excluded it
        // using its grid/profile evidence. This signal only enables measurement and never invents
        // a crop: the detector must still find both media boundaries in the screenshot.
        return context.screenMode == ShieldedScreenMode.UNKNOWN &&
            context.screenSignals.labels.any(::isPostsViewerTitle)
    }

    private fun isPostsViewerTitle(label: String): Boolean =
        label == "posts" || label.startsWith("posts,") || label.startsWith("posts ·")

    private fun hasValidBounds(region: DetectionRegion): Boolean =
        region.left in 0f..1f && region.top in 0f..1f &&
            region.right in 0f..1f && region.bottom in 0f..1f &&
            region.right > region.left && region.bottom > region.top

    private fun isBroadGridContainer(region: DetectionRegion): Boolean =
        region.width >= BROAD_CONTAINER_MIN_WIDTH &&
            region.height >= BROAD_GRID_CONTAINER_MIN_HEIGHT

    private fun normalizedContainment(first: DetectionRegion, second: DetectionRegion): Float {
        val width = (min(first.right, second.right) - max(first.left, second.left)).coerceAtLeast(0f)
        val height = (min(first.bottom, second.bottom) - max(first.top, second.top)).coerceAtLeast(0f)
        return width * height / min(first.area, second.area).coerceAtLeast(0.000001f)
    }

    // How far above the first measured thumbnail a visual cell may still begin (a fraction of that
    // thumbnail's height) before it is treated as header chrome rather than a clipped first row.
    private const val GRID_TOP_FLOOR_TOLERANCE = 0.25f
    private const val MIN_ACCESSIBILITY_WIDTH = 0.12f
    private const val MIN_ACCESSIBILITY_HEIGHT = 0.06f
    private const val MIN_ACCESSIBILITY_AREA = 0.012f
    private const val DUPLICATE_IOU = 0.72f
    private const val DUPLICATE_CONTAINMENT = 0.90f
    private const val MAX_INSTAGRAM_REGIONS = 18
    private const val BROAD_CONTAINER_MIN_WIDTH = 0.88f
    private const val BROAD_GRID_CONTAINER_MIN_HEIGHT = 0.32f
    private const val BROAD_FEED_CONTAINER_MIN_HEIGHT = 0.78f

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
        "media_viewer",
        // Real ids observed in Instagram's opened-post hierarchy. In particular,
        // row_feed_profile_header must win before the broader profile_header grid hint.
        "row_feed_profile_header",
        "carousel_media_group",
        "carousel_viewpager",
        "zoomable_view_container"
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

        if (firstPair == null) return emptyList()

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
}

/**
 * Builds a grid only from boundaries measured directly in the screenshot.
 *
 * Instagram context narrows the search, but does not create cells. A result requires three real,
 * regularly spaced horizontal transitions (two complete rows) and two independently measured
 * vertical transitions. Reported bounds use the measured peak positions.
 */
internal object InstagramGridTransitionAssembler {
    fun assemble(
        imageWidth: Int,
        imageHeight: Int,
        rowTransitions: DoubleArray,
        columnTransitions: DoubleArray,
        maxResults: Int = 18,
        // Height/width ceiling per cell. A row band measured slightly taller than a real thumbnail
        // otherwise bleeds into the post below, so each crop straddles two photos. The default is
        // effectively no clamp so callers that do not know the surface aspect are unaffected.
        maxCellAspectRatio: Double = Double.MAX_VALUE
    ): List<SupportedRectangle> {
        if (imageWidth <= 0 || imageHeight <= 0 ||
            rowTransitions.size != imageHeight - 1 || columnTransitions.size != imageWidth - 1
        ) return emptyList()

        val rowThreshold = robustThreshold(
            rowTransitions,
            ROW_SIGNAL_MULTIPLIER,
            MIN_ROW_TRANSITION_COVERAGE * FULL_SIGNAL
        )
        val rowPeaks = localPeaks(rowTransitions, rowThreshold)
            .map { it + 1 }
            .filter { it in (imageHeight * MIN_GRID_TOP_RATIO).toInt()..
                (imageHeight * MAX_GRID_BOTTOM_RATIO).toInt() }
        val regularAxes = strongestRegularRun(rowPeaks, rowTransitions, imageWidth)
        if (regularAxes.size < MIN_REQUIRED_ROW_AXES) return emptyList()

        val columnThreshold = robustThreshold(
            columnTransitions,
            COLUMN_SIGNAL_MULTIPLIER,
            MIN_COLUMN_TRANSITION_COVERAGE * FULL_SIGNAL
        )
        val dividers = (1 until GRID_COLUMNS).mapNotNull { divider ->
            val expected = imageWidth * divider / GRID_COLUMNS
            val tolerance = (imageWidth * DIVIDER_SEARCH_RATIO).toInt()
            val start = (expected - tolerance).coerceAtLeast(1)
            val end = (expected + tolerance).coerceAtMost(imageWidth - 2)
            (start..end).maxByOrNull { columnTransitions[it - 1] }
                ?.takeIf { columnTransitions[it - 1] >= columnThreshold }
        }
        if (dividers.size != GRID_COLUMNS - 1) return emptyList()

        val columnAxes = listOf(0) + dividers + imageWidth
        return regularAxes.zipWithNext().flatMap { (top, bottom) ->
            columnAxes.zipWithNext().map { (left, right) ->
                // Clamp the cell to the surface aspect anchored at its top separator, so a tall row
                // band frames a single post instead of the bottom of one plus the top of the next.
                val cellBottom = min(
                    bottom.toDouble(),
                    top.toDouble() + (right - left).toDouble() * maxCellAspectRatio
                )
                SupportedRectangle(
                    left = left.toDouble(),
                    top = top.toDouble(),
                    right = right.toDouble(),
                    bottom = cellBottom,
                    sideSupport = listOf(1.0, 1.0, 1.0, 1.0),
                    isGridCell = true
                )
            }
        }.take(maxResults)
    }

    private fun strongestRegularRun(
        peaks: List<Int>,
        scores: DoubleArray,
        imageWidth: Int
    ): List<Int> {
        var best = emptyList<Int>()
        var bestRank = Double.NEGATIVE_INFINITY
        for (first in 0 until peaks.size - 2) {
            for (second in first + 1 until peaks.size - 1) {
                val pitch = peaks[second] - peaks[first]
                if (pitch !in (imageWidth * MIN_ROW_PITCH_RATIO).toInt()..
                    (imageWidth * MAX_ROW_PITCH_RATIO).toInt()
                ) continue

                val run = mutableListOf(peaks[first], peaks[second])
                while (true) {
                    val expected = run.last() + pitch
                    val tolerance = max(
                        MIN_PITCH_TOLERANCE_PX,
                        (pitch * PITCH_TOLERANCE_RATIO).toInt()
                    )
                    val next = peaks.asSequence()
                        .filter { it > run.last() && abs(it - expected) <= tolerance }
                        .maxByOrNull { scores[it - 1] }
                        ?: break
                    run += next
                }
                if (run.size < MIN_REQUIRED_ROW_AXES) continue

                // A final measured boundary can close a row clipped by bottom navigation.
                val terminal = peaks.firstOrNull { peak ->
                    val gap = peak - run.last()
                    peak > run.last() &&
                        gap >= imageWidth * MIN_CLIPPED_ROW_RATIO && gap < pitch * 0.9
                }
                if (terminal != null) run += terminal

                val pitchError = run.zipWithNext()
                    .dropLast(if (terminal == null) 0 else 1)
                    .sumOf { (top, bottom) -> abs((bottom - top) - pitch) }
                val rank = run.size * RUN_LENGTH_WEIGHT +
                    run.sumOf { scores[it - 1] } - pitchError * PITCH_ERROR_WEIGHT
                if (rank > bestRank) {
                    best = run
                    bestRank = rank
                }
            }
        }
        return best
    }

    private fun localPeaks(values: DoubleArray, threshold: Double): List<Int> {
        val raw = values.indices.filter { index ->
            val start = max(0, index - PEAK_RADIUS)
            val end = min(values.lastIndex, index + PEAK_RADIUS)
            var localMaximum = values[start]
            for (nearby in start + 1..end) localMaximum = max(localMaximum, values[nearby])
            values[index] >= threshold && values[index] >= localMaximum
        }
        return raw.fold(mutableListOf()) { selected, index ->
            if (selected.isEmpty() || index - selected.last() > PEAK_MERGE_DISTANCE) {
                selected += index
            } else if (values[index] > values[selected.last()]) {
                selected[selected.lastIndex] = index
            }
            selected
        }
    }

    private fun robustThreshold(
        values: DoubleArray,
        multiplier: Double,
        minimum: Double
    ): Double {
        val sorted = values.sorted()
        val median = sorted[sorted.size / 2]
        return max(minimum, median * multiplier)
    }

    private const val GRID_COLUMNS = 3
    private const val MIN_REQUIRED_ROW_AXES = 3
    private const val MIN_GRID_TOP_RATIO = 0.08
    private const val MAX_GRID_BOTTOM_RATIO = 0.96
    private const val MIN_ROW_PITCH_RATIO = 0.38
    private const val MAX_ROW_PITCH_RATIO = 0.50
    private const val MIN_CLIPPED_ROW_RATIO = 0.18
    private const val PITCH_TOLERANCE_RATIO = 0.10
    private const val MIN_PITCH_TOLERANCE_PX = 8
    private const val DIVIDER_SEARCH_RATIO = 0.055
    private const val ROW_SIGNAL_MULTIPLIER = 2.0
    private const val COLUMN_SIGNAL_MULTIPLIER = 1.8
    private const val MIN_ROW_TRANSITION_COVERAGE = 0.20
    private const val MIN_COLUMN_TRANSITION_COVERAGE = 0.25
    private const val FULL_SIGNAL = 255.0
    private const val PEAK_RADIUS = 3
    private const val PEAK_MERGE_DISTANCE = 7
    private const val RUN_LENGTH_WEIGHT = 1_000.0
    private const val PITCH_ERROR_WEIGHT = 5.0
}

/** Finds an edge-to-edge Instagram post between consecutive viewport-spanning separators. */
internal object InstagramPostRegionAssembler {
    fun assemble(
        imageWidth: Int,
        imageHeight: Int,
        rawSegments: List<DetectedLineSegment>
    ): List<SupportedRectangle> {
        if (imageWidth <= 0 || imageHeight <= 0) return emptyList()
        val axes = rawSegments.mapNotNull { line ->
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
        }.sorted().fold(mutableListOf<Double>()) { selected, position ->
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
                    bottom <= imageHeight * MAX_MEDIA_BOTTOM_RATIO &&
                    height >= imageWidth * MIN_MEDIA_HEIGHT_TO_WIDTH &&
                    height <= imageWidth * MAX_MEDIA_HEIGHT_TO_WIDTH
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
    private const val MAX_MEDIA_BOTTOM_RATIO = 0.88
    private const val MIN_MEDIA_HEIGHT_TO_WIDTH = 0.50
    private const val MAX_MEDIA_HEIGHT_TO_WIDTH = 1.36
}

/**
 * Finds a post only when the screenshot itself contains two sustained, viewport-wide changes.
 *
 * The one-pixel transition proves the exact boundary position. The wider-band transition proves
 * that the pixels on opposite sides really belong to different surfaces, which rejects thin UI
 * rules inside a post card. Screen edges are intentionally absent from the candidate set.
 */
internal object InstagramPostTransitionAssembler {
    fun assemble(
        imageWidth: Int,
        imageHeight: Int,
        rowTransitions: DoubleArray,
        sustainedRowTransitions: DoubleArray
    ): List<SupportedRectangle> {
        if (imageWidth <= 0 || imageHeight <= 0 ||
            rowTransitions.size != imageHeight - 1 ||
            sustainedRowTransitions.size != imageHeight - 1
        ) return emptyList()

        val threshold = robustThreshold(rowTransitions)
        val boundaries = localPeaks(rowTransitions, threshold)
            .map { index ->
                MeasuredBoundary(
                    position = index + 1,
                    adjacentScore = rowTransitions[index],
                    sustainedScore = sustainedRowTransitions[index]
                )
            }
            .filter { boundary ->
                boundary.position in (imageHeight * MIN_BOUNDARY_Y_RATIO).toInt()..
                    (imageHeight * MAX_BOUNDARY_Y_RATIO).toInt() &&
                    boundary.sustainedScore >= MIN_SUSTAINED_TRANSITION
            }

        val best = boundaries.indices.asSequence().flatMap { topIndex ->
            (topIndex + 1 until boundaries.size).asSequence().map { bottomIndex ->
                boundaries[topIndex] to boundaries[bottomIndex]
            }
        }.filter { (top, bottom) ->
            val height = bottom.position - top.position
            top.position <= imageHeight * MAX_MEDIA_TOP_RATIO &&
                bottom.position <= imageHeight * MAX_MEDIA_BOTTOM_RATIO &&
                height >= imageWidth * MIN_MEDIA_HEIGHT_TO_WIDTH &&
                height <= imageWidth * MAX_MEDIA_HEIGHT_TO_WIDTH
        }.maxByOrNull { (top, bottom) ->
            top.adjacentScore + bottom.adjacentScore +
                SUSTAINED_SCORE_WEIGHT * (top.sustainedScore + bottom.sustainedScore)
        } ?: return emptyList()

        return listOf(
            SupportedRectangle(
                left = 0.0,
                top = best.first.position.toDouble(),
                right = imageWidth.toDouble(),
                bottom = best.second.position.toDouble(),
                sideSupport = listOf(
                    best.first.adjacentScore / FULL_SIGNAL,
                    1.0,
                    best.second.adjacentScore / FULL_SIGNAL,
                    1.0
                )
            )
        )
    }

    private fun localPeaks(values: DoubleArray, threshold: Double): List<Int> {
        val raw = values.indices.filter { index ->
            val start = max(0, index - PEAK_RADIUS)
            val end = min(values.lastIndex, index + PEAK_RADIUS)
            var localMaximum = values[start]
            for (nearby in start + 1..end) localMaximum = max(localMaximum, values[nearby])
            values[index] >= threshold && values[index] >= localMaximum
        }
        return raw.fold(mutableListOf()) { selected, index ->
            if (selected.isEmpty() || index - selected.last() > PEAK_MERGE_DISTANCE) {
                selected += index
            } else if (values[index] > values[selected.last()]) {
                selected[selected.lastIndex] = index
            }
            selected
        }
    }

    private fun robustThreshold(values: DoubleArray): Double {
        val sorted = values.sorted()
        val median = sorted[sorted.size / 2]
        return max(MIN_ADJACENT_TRANSITION, median * SIGNAL_MULTIPLIER)
    }

    private data class MeasuredBoundary(
        val position: Int,
        val adjacentScore: Double,
        val sustainedScore: Double
    )

    private const val FULL_SIGNAL = 255.0
    private const val MIN_BOUNDARY_Y_RATIO = 0.08
    private const val MAX_BOUNDARY_Y_RATIO = 0.88
    private const val MAX_MEDIA_TOP_RATIO = 0.55
    private const val MAX_MEDIA_BOTTOM_RATIO = 0.88
    private const val MIN_MEDIA_HEIGHT_TO_WIDTH = 0.50
    private const val MAX_MEDIA_HEIGHT_TO_WIDTH = 1.36
    private const val MIN_ADJACENT_TRANSITION = 0.20 * FULL_SIGNAL
    private const val MIN_SUSTAINED_TRANSITION = 12.0
    private const val SIGNAL_MULTIPLIER = 2.0
    private const val SUSTAINED_SCORE_WEIGHT = 2.0
    private const val PEAK_RADIUS = 3
    private const val PEAK_MERGE_DISTANCE = 7
}
