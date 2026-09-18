package app.sinshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEdgePatternTest {
    @Test
    fun separatedSparseTextRowsAreRejected() {
        val rows = IntArray(140)
        listOf(8..24, 42..58, 76..92, 110..126).forEach { band ->
            band.forEach { rows[it] = 32 }
        }

        assertTrue(TextEdgePattern.isLikelyPlainText(rows, width = 560))
    }

    @Test
    fun oneShortLineInTallCandidateIsRejected() {
        val rows = IntArray(120)
        (44..70).forEach { rows[it] = 38 }

        assertTrue(TextEdgePattern.isLikelyPlainText(rows, width = 600))
    }

    @Test
    fun photoLikeEdgesAcrossMostRowsAreKept() {
        val rows = IntArray(320) { row -> if (row % 17 == 0) 4 else 72 }

        assertFalse(TextEdgePattern.isLikelyPlainText(rows, width = 576))
    }

    @Test
    fun denseVisualDetailIsKeptEvenWhenHorizontallyBanded() {
        val rows = IntArray(240) { row -> if (row % 40 < 25) 100 else 55 }

        assertFalse(TextEdgePattern.isLikelyPlainText(rows, width = 576))
    }
}
