package com.example.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramMediaRegionPlannerTest {
    @Test
    fun reconstructsEveryCellInFullAndClippedProfileRows() {
        val lines = listOf(
            // A wide profile-dashboard edge must not become the gallery's first row.
            DetectedLineSegment(24.0, 618.0, 696.0, 618.0),
            DetectedLineSegment(0.0, 912.0, 720.0, 912.0),
            DetectedLineSegment(0.0, 1_230.0, 350.0, 1_230.0),
            DetectedLineSegment(370.0, 1_230.0, 720.0, 1_230.0),
            DetectedLineSegment(0.0, 1_480.0, 720.0, 1_480.0),
            DetectedLineSegment(240.0, 930.0, 240.0, 1_460.0),
            DetectedLineSegment(480.0, 930.0, 480.0, 1_460.0)
        )

        val result = InstagramGridRegionAssembler.assemble(720, 1_600, lines)

        assertEquals(6, result.size)
        assertTrue(result.any { matches(it, 0.0, 912.0, 240.0, 1_230.0) })
        assertTrue(result.any { matches(it, 240.0, 912.0, 480.0, 1_230.0) })
        assertTrue(result.any { matches(it, 480.0, 912.0, 720.0, 1_230.0) })
        assertTrue(result.any { matches(it, 0.0, 1_230.0, 240.0, 1_480.0) })
        assertTrue(result.any { matches(it, 480.0, 1_230.0, 720.0, 1_480.0) })
    }

    @Test
    fun doesNotInventGridWithoutTwoRowBoundaries() {
        val oneBoundary = listOf(
            DetectedLineSegment(0.0, 912.0, 720.0, 912.0)
        )

        assertTrue(InstagramGridRegionAssembler.assemble(720, 1_600, oneBoundary).isEmpty())
    }

    @Test
    fun reconstructsEveryGridCellFromStableVerticalSeamsWhenRowsAreInvisible() {
        val lines = listOf(
            DetectedLineSegment(240.0, 276.0, 240.0, 1_480.0),
            DetectedLineSegment(480.0, 276.0, 480.0, 910.0),
            DetectedLineSegment(480.0, 920.0, 480.0, 1_480.0),
            // Unrelated content edges must not control the inferred row pitch.
            DetectedLineSegment(30.0, 500.0, 690.0, 500.0)
        )

        val result = InstagramGridRegionAssembler.assemble(720, 1_600, lines)

        assertEquals(12, result.size)
        assertTrue(result.any { matches(it, 0.0, 276.0, 240.0, 596.0) })
        assertTrue(result.any { matches(it, 240.0, 596.0, 480.0, 916.0) })
        assertTrue(result.any { matches(it, 480.0, 1_236.0, 720.0, 1_480.0) })
    }

    @Test
    fun doesNotTreatOneLongVerticalEdgeAsAnInstagramGrid() {
        val lines = listOf(
            DetectedLineSegment(240.0, 276.0, 240.0, 1_480.0)
        )

        assertTrue(InstagramGridRegionAssembler.assemble(720, 1_600, lines).isEmpty())
    }

    @Test
    fun findsFullWidthPostAndIgnoresInsetTextCard() {
        val lines = listOf(
            DetectedLineSegment(0.0, 304.0, 720.0, 304.0),
            DetectedLineSegment(47.0, 720.0, 673.0, 720.0),
            DetectedLineSegment(47.0, 1_000.0, 673.0, 1_000.0),
            DetectedLineSegment(0.0, 1_204.0, 720.0, 1_204.0)
        )

        val result = InstagramPostRegionAssembler.assemble(720, 1_600, lines)

        assertEquals(1, result.size)
        assertTrue(matches(result.single(), 0.0, 304.0, 720.0, 1_204.0))
    }

    @Test
    fun supplementsVisualRegionsWithEveryAccessibilityThumbnail() {
        val visual = region(0f, 0.57f, 1f / 3f, 0.77f, DetectionRegionSource.VISUAL_MEDIA)
        val accessibility = (0 until 3).map { column ->
            region(
                column / 3f,
                0.57f,
                (column + 1) / 3f,
                0.77f,
                DetectionRegionSource.ACCESSIBILITY
            )
        }
        val context = LocalizedAnalysisContext(
            accessibility,
            ShieldedScreenMode.PROFILE_OR_POST,
            ScreenSignals(emptyList(), listOf("com.instagram.android:id/profile_grid"))
        )

        val result = InstagramLocalizedRegionPlanner.plan(720, 1_600, listOf(visual), context)

        assertEquals(3, result.size)
    }

    @Test
    fun enablesGridInferenceOnlyForInstagramGridSurfaces() {
        val profile = context(
            ShieldedScreenMode.PROFILE_OR_POST,
            "com.instagram.android:id/profile_header"
        )
        val post = context(
            ShieldedScreenMode.PROFILE_OR_POST,
            "com.instagram.android:id/post_viewer_container"
        )

        assertTrue(InstagramLocalizedRegionPlanner.isGridSurface(profile))
        assertFalse(InstagramLocalizedRegionPlanner.isGridSurface(post))
    }

    private fun context(mode: ShieldedScreenMode, viewId: String) = LocalizedAnalysisContext(
        emptyList(),
        mode,
        ScreenSignals(emptyList(), listOf(viewId))
    )

    private fun region(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        source: DetectionRegionSource
    ) = DetectionRegion(left, top, right, bottom, source)

    private fun matches(
        region: SupportedRectangle,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double
    ): Boolean =
        kotlin.math.abs(region.left - left) < 0.01 &&
            kotlin.math.abs(region.top - top) < 0.01 &&
            kotlin.math.abs(region.right - right) < 0.01 &&
            kotlin.math.abs(region.bottom - bottom) < 0.01
}
