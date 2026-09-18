package app.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineRectangleAssemblerTest {
    @Test
    fun constructsRectangleFromIndependentSides() {
        val result = LineRectangleAssembler.assemble(
            imageWidth = 720,
            imageHeight = 1_600,
            rawSegments = rectangleLines(120.0, 300.0, 690.0, 1_050.0)
        )

        assertEquals(1, result.size)
        assertRectangle(result.single(), 120.0, 300.0, 690.0, 1_050.0)
    }

    @Test
    fun rejectsArbitraryClosedSilhouette() {
        val silhouette = listOf(
            DetectedLineSegment(250.0, 350.0, 350.0, 260.0),
            DetectedLineSegment(350.0, 260.0, 490.0, 410.0),
            DetectedLineSegment(490.0, 410.0, 430.0, 720.0),
            DetectedLineSegment(430.0, 720.0, 260.0, 690.0),
            DetectedLineSegment(260.0, 690.0, 250.0, 350.0)
        )

        assertTrue(
            LineRectangleAssembler.assemble(720, 1_600, silhouette).isEmpty()
        )
    }

    @Test
    fun ignoresRectangleBuiltFromScreenEdges() {
        val result = LineRectangleAssembler.assemble(
            imageWidth = 720,
            imageHeight = 1_600,
            rawSegments = rectangleLines(0.0, 100.0, 719.0, 1_100.0)
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun keepsIndividualCollageTilesInsteadOfOuterGroup() {
        val lines = rectangleLines(120.0, 300.0, 400.0, 850.0) +
            rectangleLines(410.0, 300.0, 690.0, 850.0)

        val result = LineRectangleAssembler.assemble(720, 1_600, lines)

        assertEquals(2, result.size)
        assertTrue(result.any { matches(it, 120.0, 300.0, 400.0, 850.0) })
        assertTrue(result.any { matches(it, 410.0, 300.0, 690.0, 850.0) })
    }

    @Test
    fun acceptsOneWeakSideForBorderlessMedia() {
        val lines = rectangleLines(120.0, 300.0, 690.0, 1_050.0).dropLast(1) +
            DetectedLineSegment(120.0, 300.0, 120.0, 390.0)

        val result = LineRectangleAssembler.assemble(720, 1_600, lines)

        assertEquals(1, result.size)
        assertRectangle(result.single(), 120.0, 300.0, 690.0, 1_050.0)
    }

    @Test
    fun completesPhotoClippedByBottomOfScreenshot() {
        val lines = listOf(
            DetectedLineSegment(120.0, 900.0, 690.0, 900.0),
            DetectedLineSegment(120.0, 900.0, 120.0, 1_600.0),
            DetectedLineSegment(690.0, 900.0, 690.0, 1_600.0)
        )

        val result = LineRectangleAssembler.assemble(720, 1_600, lines)

        assertEquals(1, result.size)
        assertRectangle(result.single(), 120.0, 900.0, 690.0, 1_600.0)
    }

    @Test
    fun recoversOuterColumnsOfFullWidthThreeColumnGrid() {
        val lines = listOf(
            DetectedLineSegment(0.0, 600.0, 720.0, 600.0),
            DetectedLineSegment(0.0, 1_020.0, 720.0, 1_020.0),
            DetectedLineSegment(0.0, 1_440.0, 720.0, 1_440.0),
            DetectedLineSegment(240.0, 600.0, 240.0, 1_440.0),
            DetectedLineSegment(480.0, 600.0, 480.0, 1_440.0)
        )

        val result = LineRectangleAssembler.assemble(720, 1_600, lines, maxResults = 8)

        assertTrue(result.any { matches(it, 0.0, 600.0, 240.0, 1_020.0) })
        assertTrue(result.any { matches(it, 240.0, 600.0, 480.0, 1_020.0) })
        assertTrue(result.any { matches(it, 480.0, 600.0, 720.0, 1_020.0) })
        assertTrue(result.any { matches(it, 0.0, 1_020.0, 240.0, 1_440.0) })
        assertTrue(result.any { matches(it, 480.0, 1_020.0, 720.0, 1_440.0) })
    }

    @Test
    fun doesNotInferViewportSidesFromIrregularInteriorLines() {
        val lines = listOf(
            DetectedLineSegment(0.0, 600.0, 720.0, 600.0),
            DetectedLineSegment(0.0, 1_020.0, 720.0, 1_020.0),
            DetectedLineSegment(170.0, 600.0, 170.0, 1_020.0),
            DetectedLineSegment(490.0, 600.0, 490.0, 1_020.0)
        )

        val result = LineRectangleAssembler.assemble(720, 1_600, lines)

        assertTrue(result.none { it.left == 0.0 || it.right == 720.0 })
    }

    @Test
    fun gridCellsSurviveInterruptedDividersAndCorners() {
        val lines = listOf(
            DetectedLineSegment(0.0, 600.0, 330.0, 600.0),
            DetectedLineSegment(390.0, 600.0, 720.0, 600.0),
            DetectedLineSegment(0.0, 1_020.0, 300.0, 1_020.0),
            DetectedLineSegment(410.0, 1_020.0, 720.0, 1_020.0),
            DetectedLineSegment(240.0, 660.0, 240.0, 940.0),
            DetectedLineSegment(480.0, 650.0, 480.0, 930.0)
        )

        val result = LineRectangleAssembler.assemble(720, 1_600, lines, maxResults = 8)

        assertTrue(result.any { matches(it, 0.0, 600.0, 240.0, 1_020.0) })
        assertTrue(result.any { matches(it, 240.0, 600.0, 480.0, 1_020.0) })
        assertTrue(result.any { matches(it, 480.0, 600.0, 720.0, 1_020.0) })
    }

    private fun rectangleLines(
        left: Double,
        top: Double,
        right: Double,
        bottom: Double
    ): List<DetectedLineSegment> = listOf(
        DetectedLineSegment(left, top, right, top),
        DetectedLineSegment(right, top, right, bottom),
        DetectedLineSegment(right, bottom, left, bottom),
        DetectedLineSegment(left, bottom, left, top)
    )

    private fun assertRectangle(
        actual: SupportedRectangle,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double
    ) {
        assertEquals(left, actual.left, 0.01)
        assertEquals(top, actual.top, 0.01)
        assertEquals(right, actual.right, 0.01)
        assertEquals(bottom, actual.bottom, 0.01)
    }

    private fun matches(
        actual: SupportedRectangle,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double
    ): Boolean =
        kotlin.math.abs(actual.left - left) < 0.01 &&
            kotlin.math.abs(actual.top - top) < 0.01 &&
            kotlin.math.abs(actual.right - right) < 0.01 &&
            kotlin.math.abs(actual.bottom - bottom) < 0.01
}
