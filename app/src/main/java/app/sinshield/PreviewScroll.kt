package app.sinshield

import kotlin.math.roundToInt

/**
 * Returns the scroll position that aligns the accessibility row near the top of the viewport.
 * Merely being visible is insufficient: the guide card needs the space below the row, otherwise
 * it is clamped upward and overlaps the permission control it is explaining.
 */
internal fun accessibilityPreviewScrollDestination(
    currentScroll: Int,
    maxScroll: Int,
    targetTop: Float,
    desiredTop: Float
): Int? {
    val destination = (currentScroll + targetTop - desiredTop)
        .roundToInt()
        .coerceIn(0, maxScroll)
    return destination.takeUnless { it == currentScroll }
}
