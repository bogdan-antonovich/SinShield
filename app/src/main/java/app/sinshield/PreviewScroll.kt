package app.sinshield

import kotlin.math.roundToInt

/**
 * Returns the scroll position needed to reveal the accessibility row, or `null` when the whole
 * highlighted row is already inside the viewport and the UI should pause without scrolling.
 */
internal fun accessibilityPreviewScrollDestination(
    currentScroll: Int,
    maxScroll: Int,
    targetTop: Float,
    targetBottom: Float,
    viewportHeight: Float,
    desiredTop: Float,
    viewportMargin: Float
): Int? {
    val fullyVisible = targetTop >= viewportMargin &&
        targetBottom <= viewportHeight - viewportMargin
    if (fullyVisible) return null

    return (currentScroll + targetTop - desiredTop)
        .roundToInt()
        .coerceIn(0, maxScroll)
}
