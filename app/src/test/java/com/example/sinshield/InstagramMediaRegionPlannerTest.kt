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
    fun doesNotInventRowsFromVerticalSeamsAlone() {
        val lines = listOf(
            DetectedLineSegment(240.0, 276.0, 240.0, 1_480.0),
            DetectedLineSegment(480.0, 276.0, 480.0, 910.0),
            DetectedLineSegment(480.0, 920.0, 480.0, 1_480.0),
            // Unrelated content edges must not control the inferred row pitch.
            DetectedLineSegment(30.0, 500.0, 690.0, 500.0)
        )

        val result = InstagramGridRegionAssembler.assemble(720, 1_600, lines)

        assertTrue(result.isEmpty())
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
    fun dropsVisualGridCellsAnchoredAboveFirstAccessibilityThumbnail() {
        // Phantom cell the transition detector anchored on the profile header/bio/buttons.
        val header = region(0f, 0.15f, 1f / 3f, 0.35f, DetectionRegionSource.VISUAL_MEDIA)
        // A real thumbnail column, level with the first measured thumbnail row.
        val thumbnail = region(1f / 3f, 0.45f, 2f / 3f, 0.65f, DetectionRegionSource.VISUAL_MEDIA)
        val firstThumbnail = region(0f, 0.45f, 1f / 3f, 0.65f, DetectionRegionSource.ACCESSIBILITY)
        val context = LocalizedAnalysisContext(
            listOf(firstThumbnail),
            ShieldedScreenMode.PROFILE_OR_POST,
            ScreenSignals(emptyList(), listOf("com.instagram.android:id/profile_grid"))
        )

        val result = InstagramLocalizedRegionPlanner.plan(
            720,
            1_600,
            listOf(header, thumbnail),
            context
        )

        assertFalse(result.contains(header))
        assertTrue(result.contains(thumbnail))
        assertTrue(result.contains(firstThumbnail))
    }

    @Test
    fun keepsVisualGridCellsWhenNoAccessibilityThumbnailProvidesAFloor() {
        val header = region(0f, 0.15f, 1f / 3f, 0.35f, DetectionRegionSource.VISUAL_MEDIA)
        val context = context(
            ShieldedScreenMode.PROFILE_OR_POST,
            "com.instagram.android:id/profile_grid"
        )

        val result = InstagramLocalizedRegionPlanner.plan(720, 1_600, listOf(header), context)

        assertTrue(result.contains(header))
    }

    @Test
    fun rejectsBroadSearchGridWhenCellEvidenceIsMissing() {
        val broadContainer = region(
            0f,
            256f / 1_600f,
            1f,
            1_568f / 1_600f,
            DetectionRegionSource.VISUAL_MEDIA
        )
        val context = context(
            ShieldedScreenMode.EXPLORE,
            "com.instagram.android:id/explore_grid_container"
        )

        val result = InstagramLocalizedRegionPlanner.plan(
            720,
            1_600,
            listOf(broadContainer),
            context
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun reconstructsOnlyMeasuredRepeatedGridTransitions() {
        val rows = DoubleArray(1_599) { 3.0 }
        val columns = DoubleArray(719) { 2.0 }
        listOf(276, 597, 918, 1_236, 1_479).forEach { rows[it - 1] = 220.0 }
        columns[239 - 1] = 210.0
        columns[481 - 1] = 205.0

        val result = InstagramGridTransitionAssembler.assemble(720, 1_600, rows, columns)

        assertEquals(12, result.size)
        assertTrue(result.any { matches(it, 0.0, 276.0, 239.0, 597.0) })
        assertTrue(result.any { matches(it, 239.0, 918.0, 481.0, 1_236.0) })
        assertTrue(result.any { matches(it, 481.0, 1_236.0, 720.0, 1_479.0) })
    }

    @Test
    fun refusesTransitionsWithoutRepeatedRowsAndBothColumnSeams() {
        val rows = DoubleArray(1_599) { 3.0 }.apply {
            this[276 - 1] = 220.0
            this[597 - 1] = 220.0
        }
        val columns = DoubleArray(719) { 2.0 }.apply { this[239 - 1] = 210.0 }

        assertTrue(
            InstagramGridTransitionAssembler.assemble(720, 1_600, rows, columns).isEmpty()
        )
    }

    @Test
    fun measuredPostMediaReplacesNearFullScreenAccessibilityContainer() {
        val broadContainer = region(
            0f,
            0f,
            1f,
            0.925f,
            DetectionRegionSource.ACCESSIBILITY
        )
        val media = region(
            0f,
            526f / 1_600f,
            1f,
            1_164f / 1_600f,
            DetectionRegionSource.VISUAL_MEDIA
        )

        val result = InstagramLocalizedRegionPlanner.plan(
            720,
            1_600,
            listOf(media),
            LocalizedAnalysisContext(
                listOf(broadContainer),
                ShieldedScreenMode.FEED,
                ScreenSignals(emptyList(), listOf("com.instagram.android:id/feed_tab"))
            )
        )

        assertEquals(listOf(media), result)
    }

    @Test
    fun measuredPostRegionsAreAuthoritativeModelCrops() {
        val measuredPost = region(
            0f,
            292f / 1_600f,
            1f,
            1_252f / 1_600f,
            DetectionRegionSource.VISUAL_MEDIA
        )
        val accessibilityContainer = region(
            0f,
            190f / 1_600f,
            1f,
            1_480f / 1_600f,
            DetectionRegionSource.ACCESSIBILITY
        )
        val context = LocalizedAnalysisContext(
            listOf(accessibilityContainer),
            ShieldedScreenMode.UNKNOWN,
            ScreenSignals(labels = listOf("posts", "search"), viewIds = emptyList())
        )

        val result = InstagramLocalizedRegionPlanner.plan(
            720,
            1_600,
            listOf(measuredPost),
            context
        )

        assertEquals(listOf(measuredPost), result)
    }

    @Test
    fun postUsesAccessibilityOnlyWhenOpenCvFoundNothing() {
        val accessibilityMedia = region(
            0f,
            292f / 1_600f,
            1f,
            1_252f / 1_600f,
            DetectionRegionSource.ACCESSIBILITY
        )
        val context = LocalizedAnalysisContext(
            listOf(accessibilityMedia),
            ShieldedScreenMode.UNKNOWN,
            ScreenSignals(labels = listOf("posts", "search"), viewIds = emptyList())
        )

        val result = InstagramLocalizedRegionPlanner.plan(720, 1_600, emptyList(), context)

        assertEquals(listOf(accessibilityMedia), result)
    }

    @Test
    fun postAssemblerDoesNotInventNearFullScreenMediaFromViewportEdges() {
        val result = InstagramPostRegionAssembler.assemble(
            720,
            1_600,
            emptyList()
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun postAssemblerRequiresBothMeasuredMediaBoundaries() {
        val oneBoundary = listOf(
            DetectedLineSegment(0.0, 1_204.0, 720.0, 1_204.0)
        )

        assertTrue(InstagramPostRegionAssembler.assemble(720, 1_600, oneBoundary).isEmpty())
    }

    @Test
    fun postTransitionsReturnOnlyTheMeasuredMediaBand() {
        val adjacent = DoubleArray(1_599) { 3.0 }
        val sustained = DoubleArray(1_599)
        adjacent[525 - 1] = 250.0
        sustained[525 - 1] = 145.0
        adjacent[1_164 - 1] = 248.0
        sustained[1_164 - 1] = 72.0
        // A strong one-pixel UI rule is not a media boundary when both surrounding bands match.
        adjacent[1_252 - 1] = 255.0
        sustained[1_252 - 1] = 1.0
        // Navigation is a real surface change, but pairing it with the media top would create a
        // band taller than Instagram's feed-media canvas.
        adjacent[1_462 - 1] = 255.0
        sustained[1_462 - 1] = 170.0

        val result = InstagramPostTransitionAssembler.assemble(
            720,
            1_600,
            adjacent,
            sustained
        )

        assertEquals(1, result.size)
        assertTrue(matches(result.single(), 0.0, 525.0, 720.0, 1_164.0))
    }

    @Test
    fun postTransitionsAcceptMeasuredFourByThreePortraitMedia() {
        val adjacent = DoubleArray(1_599) { 3.0 }.apply {
            this[292 - 1] = 217.0
            this[1_252 - 1] = 238.0
        }
        val sustained = DoubleArray(1_599).apply {
            this[292 - 1] = 92.0
            this[1_252 - 1] = 87.0
        }

        val result = InstagramPostTransitionAssembler.assemble(
            720,
            1_600,
            adjacent,
            sustained
        )

        assertEquals(1, result.size)
        assertTrue(matches(result.single(), 0.0, 292.0, 720.0, 1_252.0))
    }

    @Test
    fun postTransitionsRefuseUnsustainedUiLinesAndSingleBoundaries() {
        val adjacent = DoubleArray(1_599) { 3.0 }.apply {
            this[525 - 1] = 250.0
            this[1_252 - 1] = 255.0
        }
        val sustained = DoubleArray(1_599).apply {
            this[525 - 1] = 145.0
            this[1_252 - 1] = 1.0
        }

        assertTrue(
            InstagramPostTransitionAssembler.assemble(
                720,
                1_600,
                adjacent,
                sustained
            ).isEmpty()
        )
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
        assertFalse(InstagramLocalizedRegionPlanner.isPostSurface(profile))
        assertTrue(InstagramLocalizedRegionPlanner.isPostSurface(post))
    }

    @Test
    fun enablesMeasuredPostInferenceForSparsePostsViewerHierarchy() {
        val viewer = LocalizedAnalysisContext(
            emptyList(),
            ShieldedScreenMode.UNKNOWN,
            // Search is also visible because it is a bottom-navigation destination. It must not
            // turn an opened post into a search grid.
            ScreenSignals(labels = listOf("posts", "search"), viewIds = emptyList())
        )

        assertFalse(InstagramLocalizedRegionPlanner.isGridSurface(viewer))
        assertTrue(InstagramLocalizedRegionPlanner.isPostSurface(viewer))
    }

    @Test
    fun rowFeedProfileHeaderIsPostEvidenceNotProfileGridEvidence() {
        val viewer = LocalizedAnalysisContext(
            emptyList(),
            ShieldedScreenMode.PROFILE_OR_POST,
            ScreenSignals(
                labels = listOf("posts"),
                viewIds = listOf(
                    "com.instagram.android:id/row_feed_profile_header",
                    "com.instagram.android:id/carousel_viewpager"
                )
            )
        )

        assertFalse(InstagramLocalizedRegionPlanner.isGridSurface(viewer))
        assertTrue(InstagramLocalizedRegionPlanner.isPostSurface(viewer))
    }

    @Test
    fun sparsePostsLabelDoesNotOverrideMeasuredProfileGridEvidence() {
        val profile = LocalizedAnalysisContext(
            emptyList(),
            ShieldedScreenMode.UNKNOWN,
            ScreenSignals(
                labels = listOf("posts", "followers", "following", "search"),
                viewIds = emptyList()
            )
        )

        assertTrue(InstagramLocalizedRegionPlanner.isGridSurface(profile))
        assertFalse(InstagramLocalizedRegionPlanner.isPostSurface(profile))
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
