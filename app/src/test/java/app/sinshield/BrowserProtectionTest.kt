package app.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserProtectionTest {
    @Test
    fun onlyChromeIsEnabledForScreenshotProtection() {
        assertTrue(SupportedBrowsers.contains("com.android.chrome"))
        assertFalse(SupportedBrowsers.contains("com.brave.browser"))
        assertTrue("com.brave.browser" in KnownBrowsers.packages)
    }

    @Test
    fun recognizesChromiumsBareVirtualImageClass() {
        assertTrue(
            BrowserMediaNodeMatcher.isLikelyMedia(
                className = "android.widget.Image",
                contentDescription = null,
                viewId = null
            )
        )
    }

    @Test
    fun recognizesChromiumsExplicitImageMetadata() {
        assertTrue(
            BrowserMediaNodeMatcher.isLikelyMedia(
                className = "android.view.View",
                contentDescription = null,
                viewId = null,
                hasImage = true
            )
        )
        assertTrue(
            BrowserMediaNodeMatcher.hasChromiumMediaMetadata(
                hasImage = false,
                chromeRole = "image",
                roleDescription = null,
                supportsImageData = false
            )
        )
        assertTrue(
            BrowserMediaNodeMatcher.hasChromiumMediaMetadata(
                hasImage = false,
                chromeRole = null,
                roleDescription = null,
                supportsImageData = true
            )
        )
    }

    @Test
    fun distinguishesDirectImageRoleFromAncestorWithPropagatedHasImage() {
        assertTrue(BrowserMediaNodeMatcher.isDirectMedia("android.widget.Image", null, null, true))
        assertTrue(BrowserMediaNodeMatcher.isDirectMedia("android.view.View", "image", null, true))
        assertTrue(BrowserMediaNodeMatcher.isDirectMedia("android.view.View", null, "graphic", true))
        assertFalse(BrowserMediaNodeMatcher.isDirectMedia("android.view.View", "link", null, true))
    }

    @Test
    fun rejectsChromeToolbarImagesOutsideWebContent() {
        assertTrue(BrowserMediaNodeMatcher.isWebContentRoot("android.webkit.WebView"))
        assertFalse(BrowserMediaNodeMatcher.isWebContentRoot("android.widget.FrameLayout"))
        assertFalse(
            BrowserMediaNodeMatcher.isDirectMedia(
                "android.widget.ImageButton",
                null,
                null,
                insideWebContent = false
            )
        )
    }

    @Test
    fun recognizesNativeImageAndVideoSurfaces() {
        listOf(
            "android.widget.ImageView",
            "com.example.PhotoView",
            "android.view.SurfaceView",
            "android.view.TextureView",
            "android.widget.VideoView"
        ).forEach { className ->
            assertTrue(
                className,
                BrowserMediaNodeMatcher.isLikelyMedia(className, null, null)
            )
        }
    }

    @Test
    fun rejectsOrdinaryTextAndLayoutNodes() {
        assertFalse(
            BrowserMediaNodeMatcher.isLikelyMedia(
                className = "android.widget.TextView",
                contentDescription = "Article body",
                viewId = "content"
            )
        )
        assertFalse(
            BrowserMediaNodeMatcher.isLikelyMedia(
                className = "android.view.View",
                contentDescription = null,
                viewId = null
            )
        )
    }

    @Test
    fun acceptsGenericClickableWebResultAsFallback() {
        assertTrue(
            BrowserMediaNodeMatcher.isLikelyClickableMediaContainer(
                className = "android.view.View",
                viewId = null,
                isClickable = true
            )
        )
        assertFalse(
            BrowserMediaNodeMatcher.isLikelyClickableMediaContainer(
                className = "android.view.View",
                viewId = null,
                isClickable = false
            )
        )
    }

    @Test
    fun clickableFallbackRejectsNativeChromeControlsAndSmallChips() {
        assertFalse(
            BrowserMediaNodeMatcher.isLikelyClickableMediaContainer(
                className = "android.view.View",
                viewId = "com.android.chrome:id/location_bar",
                isClickable = true
            )
        )
        assertFalse(BrowserMediaBoundsPolicy.acceptsClickableContainer(180, 70, 720, 1600))
        assertTrue(BrowserMediaBoundsPolicy.acceptsClickableContainer(190, 260, 720, 1600))
    }

    @Test
    fun browserBoundsAllowPageMediaButRejectToolbarIcons() {
        assertTrue(BrowserMediaBoundsPolicy.accepts(800, 900, 1080, 2400))
        assertTrue(BrowserMediaBoundsPolicy.accepts(140, 180, 1080, 2400))
        assertFalse(BrowserMediaBoundsPolicy.accepts(48, 48, 1080, 2400))
        assertFalse(BrowserMediaBoundsPolicy.accepts(1080, 2300, 1080, 2400))
    }

    @Test
    fun browserPlannerUsesDirectChromeMediaAndIgnoresVisualGuesses() {
        val accessible = region(
            0.05f,
            0.20f,
            0.95f,
            0.70f,
            DetectionRegionSource.BROWSER_DIRECT_MEDIA
        )
        val visualGuess = region(0.10f, 0.75f, 0.90f, 0.95f, DetectionRegionSource.VISUAL_MEDIA)

        val result = BrowserLocalizedRegionPlanner.plan(
            bitmapWidth = 1080,
            bitmapHeight = 2400,
            visualRegions = listOf(visualGuess),
            context = context(accessible)
        )

        assertEquals(listOf(accessible), result)
    }

    @Test
    fun browserPlannerReplacesGalleryWrapperWithVisualMediaChildren() {
        val galleryWrapper = region(
            0.10f,
            0.12f,
            0.90f,
            0.98f,
            DetectionRegionSource.ACCESSIBILITY
        )
        val leftImage = region(
            0.10f,
            0.15f,
            0.48f,
            0.43f,
            DetectionRegionSource.VISUAL_MEDIA
        )
        val rightImage = region(
            0.52f,
            0.15f,
            0.90f,
            0.43f,
            DetectionRegionSource.VISUAL_MEDIA
        )

        val result = BrowserLocalizedRegionPlanner.plan(
            bitmapWidth = 720,
            bitmapHeight = 1_600,
            visualRegions = listOf(leftImage, rightImage),
            context = context(galleryWrapper)
        )

        assertEquals(listOf(leftImage, rightImage), result)
    }

    @Test
    fun browserPlannerNeverSendsUnresolvedGalleryWrapperToModel() {
        val galleryWrapper = region(
            0.10f,
            0.12f,
            0.90f,
            0.98f,
            DetectionRegionSource.BROWSER_CHROME_METADATA
        )

        val result = BrowserLocalizedRegionPlanner.plan(
            bitmapWidth = 720,
            bitmapHeight = 1_600,
            visualRegions = emptyList(),
            context = context(galleryWrapper)
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun browserPlannerRejectsVisualRectanglesOutsideAccessibilityContainer() {
        val galleryWrapper = region(
            0.10f,
            0.40f,
            0.90f,
            0.98f,
            DetectionRegionSource.BROWSER_CHROME_METADATA
        )
        val browserToolbarGuess = region(
            0.05f,
            0.02f,
            0.35f,
            0.12f,
            DetectionRegionSource.VISUAL_MEDIA
        )

        val result = BrowserLocalizedRegionPlanner.plan(
            bitmapWidth = 720,
            bitmapHeight = 1_600,
            visualRegions = listOf(browserToolbarGuess),
            context = context(galleryWrapper)
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun browserPlannerAcceptsClickableContainerFallbackRegions() {
        val fallback = region(
            0.10f,
            0.20f,
            0.48f,
            0.55f,
            DetectionRegionSource.BROWSER_CLICKABLE_FALLBACK
        )

        val result = BrowserLocalizedRegionPlanner.plan(
            bitmapWidth = 1080,
            bitmapHeight = 2400,
            visualRegions = emptyList(),
            context = context(fallback)
        )

        assertEquals(listOf(fallback), result)
    }

    @Test
    fun browserPlannerAcceptsChromiumMetadataRegions() {
        val metadataRegion = region(
            0.10f,
            0.20f,
            0.48f,
            0.55f,
            DetectionRegionSource.BROWSER_CHROME_METADATA
        )

        val result = BrowserLocalizedRegionPlanner.plan(
            bitmapWidth = 1080,
            bitmapHeight = 2400,
            visualRegions = emptyList(),
            context = context(metadataRegion)
        )

        assertEquals(listOf(metadataRegion), result)
    }

    @Test
    fun browserPlannerRejectsSquareFancyboxMetadataControlsAndUsesVisualPhoto() {
        val zoomControl = region(
            240f / 720f,
            186f / 1_600f,
            336f / 720f,
            282f / 1_600f,
            DetectionRegionSource.BROWSER_CHROME_METADATA
        )
        val previousControl = region(
            16f / 720f,
            750f / 1_600f,
            112f / 720f,
            846f / 1_600f,
            DetectionRegionSource.BROWSER_CHROME_METADATA
        )
        val displayedPhoto = region(
            16f / 720f,
            579f / 1_600f,
            704f / 720f,
            1_095f / 1_600f,
            DetectionRegionSource.VISUAL_MEDIA
        )

        val result = BrowserLocalizedRegionPlanner.plan(
            bitmapWidth = 720,
            bitmapHeight = 1_600,
            visualRegions = listOf(displayedPhoto),
            context = context(zoomControl, previousControl)
        )

        assertEquals(listOf(displayedPhoto), result)
    }

    @Test
    fun browserPlannerAllowsLargePortraitVisualPhotoWithoutChromeContainer() {
        val displayedPhoto = region(
            16f / 720f,
            400f / 1_600f,
            704f / 720f,
            1_275f / 1_600f,
            DetectionRegionSource.VISUAL_MEDIA
        )

        val result = BrowserLocalizedRegionPlanner.plan(
            bitmapWidth = 720,
            bitmapHeight = 1_600,
            visualRegions = listOf(displayedPhoto),
            context = context()
        )

        assertEquals(listOf(displayedPhoto), result)
    }

    @Test
    fun browserPlannerRejectsIconsAndNearFullScreenContainers() {
        val icon = region(0.01f, 0.10f, 0.08f, 0.17f, DetectionRegionSource.ACCESSIBILITY)
        val container = region(0f, 0.05f, 1f, 1f, DetectionRegionSource.ACCESSIBILITY)
        val media = region(
            0.10f,
            0.20f,
            0.90f,
            0.65f,
            DetectionRegionSource.BROWSER_DIRECT_MEDIA
        )

        val result = BrowserLocalizedRegionPlanner.plan(
            bitmapWidth = 1080,
            bitmapHeight = 2400,
            visualRegions = emptyList(),
            context = context(icon, container, media)
        )

        assertEquals(listOf(media), result)
    }

    @Test
    fun browserPlannerDeduplicatesNestedMediaBounds() {
        val image = region(
            0.10f,
            0.20f,
            0.90f,
            0.70f,
            DetectionRegionSource.BROWSER_DIRECT_MEDIA
        )
        val duplicateChild = region(
            0.11f,
            0.21f,
            0.89f,
            0.69f,
            DetectionRegionSource.BROWSER_DIRECT_MEDIA
        )

        val result = BrowserLocalizedRegionPlanner.plan(
            bitmapWidth = 1080,
            bitmapHeight = 2400,
            visualRegions = emptyList(),
            context = context(image, duplicateChild)
        )

        assertEquals(listOf(duplicateChild), result)
    }

    @Test
    fun browserCandidateSelectorPrefersDirectImagesAndDropsTheirAncestors() {
        val pageAncestor = region(
            0f, 0.10f, 1f, 0.95f, DetectionRegionSource.BROWSER_CHROME_METADATA
        )
        val cardAncestor = region(
            0.02f, 0.20f, 0.49f, 0.60f, DetectionRegionSource.BROWSER_CHROME_METADATA
        )
        val directImage = region(
            0.02f, 0.20f, 0.49f, 0.48f, DetectionRegionSource.BROWSER_DIRECT_MEDIA
        )

        val result = BrowserMediaCandidateSelector.select(
            directMedia = listOf(directImage),
            metadataFallback = listOf(pageAncestor, cardAncestor),
            clickableFallback = emptyList(),
            limit = 16
        )

        assertEquals(listOf(directImage), result)
    }

    @Test
    fun browserCandidateSelectorKeepsSmallestMetadataFallbackNotWholePage() {
        val pageAncestor = region(
            0f, 0.10f, 1f, 0.95f, DetectionRegionSource.BROWSER_CHROME_METADATA
        )
        val imageLikeChild = region(
            0.02f, 0.20f, 0.49f, 0.48f, DetectionRegionSource.BROWSER_CHROME_METADATA
        )

        val result = BrowserMediaCandidateSelector.select(
            directMedia = emptyList(),
            metadataFallback = listOf(pageAncestor, imageLikeChild),
            clickableFallback = emptyList(),
            limit = 16
        )

        assertEquals(listOf(imageLikeChild), result)
    }

    private fun context(vararg regions: DetectionRegion) = LocalizedAnalysisContext(
        accessibilityMediaRegions = regions.toList(),
        screenMode = ShieldedScreenMode.UNKNOWN,
        screenSignals = ScreenSignals.EMPTY
    )

    private fun region(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        source: DetectionRegionSource
    ) = DetectionRegion(left, top, right, bottom, source)
}
