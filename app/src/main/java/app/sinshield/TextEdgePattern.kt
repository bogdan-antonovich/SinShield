package app.sinshield

import kotlin.math.max

/** Distinguishes UI text rows from edge detail distributed throughout visual media. */
internal object TextEdgePattern {
    fun isLikelyPlainText(rowEdgeCounts: IntArray, width: Int): Boolean {
        if (rowEdgeCounts.isEmpty() || width <= 0) return false

        val activeThreshold = max(MIN_ACTIVE_EDGES, width / ACTIVE_EDGE_WIDTH_DIVISOR)
        val activeRows = BooleanArray(rowEdgeCounts.size) { rowEdgeCounts[it] >= activeThreshold }

        // Canny can leave one-pixel holes through an otherwise continuous glyph row.
        for (row in 1 until activeRows.lastIndex) {
            if (!activeRows[row] && activeRows[row - 1] && activeRows[row + 1]) {
                activeRows[row] = true
            }
        }

        val activeRowRatio = activeRows.count { it }.toDouble() / activeRows.size
        val edgeDensity = rowEdgeCounts.sumOf(Int::toLong).toDouble() /
            (width.toLong() * rowEdgeCounts.size).coerceAtLeast(1L)
        var bands = 0
        var previousActive = false
        activeRows.forEach { active ->
            if (active && !previousActive) bands++
            previousActive = active
        }

        // Paragraphs form a handful of separated horizontal edge bands. Photos—even ones
        // containing subtitles—normally retain edges through most rows of the entire crop.
        return edgeDensity <= MAX_TEXT_EDGE_DENSITY &&
            activeRowRatio <= MAX_TEXT_ACTIVE_ROW_RATIO &&
            (bands >= MIN_TEXT_BANDS || activeRowRatio <= SINGLE_BAND_MAX_ACTIVE_ROW_RATIO)
    }

    private const val MIN_ACTIVE_EDGES = 4
    private const val ACTIVE_EDGE_WIDTH_DIVISOR = 100
    private const val MAX_TEXT_EDGE_DENSITY = 0.115
    private const val MAX_TEXT_ACTIVE_ROW_RATIO = 0.78
    private const val MIN_TEXT_BANDS = 2
    private const val SINGLE_BAND_MAX_ACTIVE_ROW_RATIO = 0.48
}
