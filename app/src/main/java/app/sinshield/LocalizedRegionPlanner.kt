package app.sinshield

import kotlin.math.max
import kotlin.math.min

internal data class DetectionRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val source: DetectionRegionSource
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
}

internal enum class DetectionRegionSource {
    ACCESSIBILITY,
    VISUAL_MEDIA,
    ASPECT_FOCUS,
    VISUAL_FALLBACK,
    COMPACT_GRID_FALLBACK
}

/** Keeps localized inference in one-to-one correspondence with displayed OpenCV rectangles. */
internal object LocalizedRegionPlanner {
    fun plan(
        bitmapWidth: Int,
        bitmapHeight: Int,
        mediaRegions: List<DetectionRegion>
    ): List<DetectionRegion> {
        require(bitmapWidth > 0 && bitmapHeight > 0) { "Bitmap dimensions must be positive" }
        return mediaRegions.filter { it.source == DetectionRegionSource.VISUAL_MEDIA }
    }
}

internal fun normalizedIntersectionOverUnion(
    first: DetectionRegion,
    second: DetectionRegion
): Float {
    val intersectionWidth = (min(first.right, second.right) - max(first.left, second.left))
        .coerceAtLeast(0f)
    val intersectionHeight = (min(first.bottom, second.bottom) - max(first.top, second.top))
        .coerceAtLeast(0f)
    val intersection = intersectionWidth * intersectionHeight
    val union = first.area + second.area - intersection
    return if (union > 0f) intersection / union else 0f
}
