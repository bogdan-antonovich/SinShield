package app.sinshield

import kotlin.math.max
import kotlin.math.min

/** Browser packages whose page accessibility contract SinShield has tested and supports. */
internal object SupportedBrowsers {
    const val CHROME = "com.android.chrome"

    val packages: Set<String> = setOf(CHROME)

    fun contains(packageName: String?): Boolean = packageName in packages
}

/**
 * Browser packages recognized only for DNS-block overlays and setup previews. Screenshot analysis
 * must use [SupportedBrowsers] so a Chromium family name never becomes an untested support claim.
 */
internal object KnownBrowsers {
    val packages: Set<String> = setOf(
        SupportedBrowsers.CHROME,
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.sec.android.app.sbrowser",
        "com.opera.browser",
        "com.opera.mini.native"
    )
}

/** Pure browser-node matching kept outside AccessibilityService so Chrome mappings are testable. */
internal object BrowserMediaNodeMatcher {
    fun isDirectMedia(
        className: String?,
        chromeRole: String?,
        roleDescription: String?,
        insideWebContent: Boolean
    ): Boolean {
        if (!insideWebContent) return false
        val simpleClass = className.orEmpty().lowercase().substringAfterLast('.')
        if (simpleClass in DIRECT_MEDIA_CLASSES) return true

        val normalizedRole = chromeRole.orEmpty().lowercase()
        val normalizedRoleDescription = roleDescription.orEmpty().lowercase()
        return CHROMIUM_MEDIA_ROLES.any { role ->
            role == normalizedRole || role == normalizedRoleDescription
        }
    }

    fun isWebContentRoot(className: String?): Boolean =
        className.orEmpty().equals(WEB_VIEW_CLASS, ignoreCase = true)

    fun isLikelyMedia(
        className: String?,
        contentDescription: String?,
        viewId: String?,
        hasImage: Boolean = false,
        chromeRole: String? = null,
        roleDescription: String? = null,
        supportsImageData: Boolean = false
    ): Boolean {
        if (hasChromiumMediaMetadata(
                hasImage,
                chromeRole,
                roleDescription,
                supportsImageData
            )
        ) {
            return true
        }
        val normalizedClass = className.orEmpty().lowercase()
        val simpleClass = normalizedClass.substringAfterLast('.')
        val normalizedDescription = contentDescription.orEmpty().lowercase()
        val normalizedViewId = viewId.orEmpty().lowercase()

        // Chromium exposes a semantic HTML <img> as android.widget.Image: it is a virtual node,
        // not an ImageView. Native image/video classes remain useful for Chrome UI surfaces and
        // embedded players.
        if (simpleClass in DIRECT_MEDIA_CLASSES) {
            return true
        }

        return BROWSER_MEDIA_HINTS.any { hint ->
            hint in normalizedDescription || hint in normalizedViewId
        }
    }

    fun hasChromiumMediaMetadata(
        hasImage: Boolean,
        chromeRole: String?,
        roleDescription: String?,
        supportsImageData: Boolean
    ): Boolean {
        if (hasImage || supportsImageData) return true
        val normalizedRole = chromeRole.orEmpty().lowercase()
        val normalizedRoleDescription = roleDescription.orEmpty().lowercase()
        return CHROMIUM_MEDIA_ROLES.any { role ->
            role == normalizedRole || role == normalizedRoleDescription
        }
    }

    /**
     * Some Chromium pages paint thumbnails with CSS or lazy-loading primitives and expose only the
     * surrounding HTML link in the virtual accessibility tree. This fallback is intentionally
     * limited to generic clickable web nodes; native Chrome controls have package resource IDs and
     * are never treated as page media containers.
     */
    fun isLikelyClickableMediaContainer(
        className: String?,
        viewId: String?,
        isClickable: Boolean
    ): Boolean {
        if (!isClickable) return false
        val simpleClass = className.orEmpty().lowercase().substringAfterLast('.')
        val normalizedViewId = viewId.orEmpty().lowercase()
        return simpleClass == "view" &&
            !normalizedViewId.startsWith(CHROME_NATIVE_VIEW_ID_PREFIX)
    }

    private val BROWSER_MEDIA_HINTS = setOf(
        "image",
        "photo",
        "video",
        "thumbnail",
        "media"
    )

    private val CHROMIUM_MEDIA_ROLES = setOf(
        "image",
        "image map",
        "imageMap".lowercase(),
        "graphic",
        "video"
    )

    private val DIRECT_MEDIA_CLASSES = setOf(
        "image",
        "imagebutton",
        "imageview",
        "photoview",
        "surfaceview",
        "textureview",
        "videoview"
    )

    private const val CHROME_NATIVE_VIEW_ID_PREFIX = "com.android.chrome:id/"
    private const val WEB_VIEW_CLASS = "android.webkit.WebView"

    const val EXTRA_HAS_IMAGE = "AccessibilityNodeInfo.hasImage"
    const val EXTRA_CHROME_ROLE = "AccessibilityNodeInfo.chromeRole"
    const val EXTRA_ROLE_DESCRIPTION = "AccessibilityNodeInfo.roleDescription"
    const val EXTRA_TARGET_URL = "AccessibilityNodeInfo.targetUrl"
    const val EXTRA_REQUEST_IMAGE_DATA = "AccessibilityNodeInfo.requestImageData"
}

internal data class ChromiumMediaMetadata(
    val hasImage: Boolean,
    val chromeRole: String?,
    val roleDescription: String?,
    val supportsImageData: Boolean
) {
    val hasImageInSubtree: Boolean get() = hasImage || supportsImageData
}

/**
 * Chrome propagates `hasImage` through every ancestor of an image. Prefer nodes whose role/class
 * identifies the node itself as media; only use propagated metadata when no direct node survived.
 */
internal object BrowserMediaCandidateSelector {
    fun select(
        directMedia: List<DetectionRegion>,
        metadataFallback: List<DetectionRegion>,
        clickableFallback: List<DetectionRegion>,
        limit: Int
    ): List<DetectionRegion> {
        val source = when {
            directMedia.isNotEmpty() -> directMedia
            metadataFallback.isNotEmpty() -> metadataFallback
            else -> clickableFallback
        }
        return source
            .sortedBy(DetectionRegion::area)
            .fold(mutableListOf<DetectionRegion>()) { selected, candidate ->
                val redundantAncestorOrDuplicate = selected.any { existing ->
                    normalizedIntersectionOverUnion(existing, candidate) >= DUPLICATE_IOU ||
                        normalizedContainment(existing, candidate) >= DUPLICATE_CONTAINMENT
                }
                if (!redundantAncestorOrDuplicate) selected += candidate
                selected
            }
            .sortedWith(compareBy<DetectionRegion>({ it.top }, { it.left }))
            .take(limit)
    }

    private fun normalizedContainment(first: DetectionRegion, second: DetectionRegion): Float {
        val width = (min(first.right, second.right) - max(first.left, second.left))
            .coerceAtLeast(0f)
        val height = (min(first.bottom, second.bottom) - max(first.top, second.top))
            .coerceAtLeast(0f)
        return width * height / min(first.area, second.area).coerceAtLeast(0.000001f)
    }

    private const val DUPLICATE_IOU = 0.80f
    private const val DUPLICATE_CONTAINMENT = 0.92f
}

/** Browser pages need smaller candidates than social feeds, while still excluding toolbar icons. */
internal object BrowserMediaBoundsPolicy {
    fun accepts(
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (width <= 0 || height <= 0 || screenWidth <= 0 || screenHeight <= 0) return false
        val widthFraction = width.toFloat() / screenWidth
        val heightFraction = height.toFloat() / screenHeight
        val areaFraction = (width.toLong() * height).toFloat() /
            (screenWidth.toLong() * screenHeight).toFloat()
        return widthFraction >= MIN_WIDTH &&
            heightFraction >= MIN_HEIGHT &&
            areaFraction >= MIN_AREA &&
            areaFraction <= MAX_AREA
    }

    fun acceptsClickableContainer(
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (!accepts(width, height, screenWidth, screenHeight)) return false
        val widthFraction = width.toFloat() / screenWidth
        val heightFraction = height.toFloat() / screenHeight
        val areaFraction = (width.toLong() * height).toFloat() /
            (screenWidth.toLong() * screenHeight).toFloat()
        return widthFraction >= CLICKABLE_MIN_WIDTH &&
            heightFraction >= CLICKABLE_MIN_HEIGHT &&
            areaFraction >= CLICKABLE_MIN_AREA &&
            areaFraction <= CLICKABLE_MAX_AREA
    }

    private const val MIN_WIDTH = 0.10f
    private const val MIN_HEIGHT = 0.06f
    private const val MIN_AREA = 0.008f
    private const val MAX_AREA = 0.90f
    private const val CLICKABLE_MIN_WIDTH = 0.12f
    private const val CLICKABLE_MIN_HEIGHT = 0.10f
    private const val CLICKABLE_MIN_AREA = 0.015f
    private const val CLICKABLE_MAX_AREA = 0.60f
}

/**
 * Uses exact Chrome media nodes when the page exposes them. A less accessible page may expose one
 * recursive node for an entire gallery; that node is only a search boundary, never a model crop.
 * Pixel-detected rectangles inside the boundary supply the individual media crops instead.
 */
internal object BrowserLocalizedRegionPlanner : LocalizedAnalysisRegionPlanner {
    override fun plan(
        bitmapWidth: Int,
        bitmapHeight: Int,
        visualRegions: List<DetectionRegion>,
        context: LocalizedAnalysisContext
    ): List<DetectionRegion> {
        require(bitmapWidth > 0 && bitmapHeight > 0) { "Bitmap dimensions must be positive" }

        val accessibilityRegions = context.accessibilityMediaRegions
            .asSequence()
            .filter {
                it.source == DetectionRegionSource.ACCESSIBILITY ||
                    it.source == DetectionRegionSource.BROWSER_DIRECT_MEDIA ||
                    it.source == DetectionRegionSource.BROWSER_CHROME_METADATA ||
                    it.source == DetectionRegionSource.BROWSER_CLICKABLE_FALLBACK
            }
            .filter(::hasValidBounds)
            // Fancybox and similar lightboxes mark toolbar SVGs as images while giving the actual
            // photo alt="". Chrome consequently reports exact, square hasImage nodes for the
            // controls but no direct media node for the photo. Do not send those icon-sized
            // metadata regions to the classifier; the screenshot detector below owns the fallback.
            .filterNot { region ->
                isLikelyMetadataControl(region, bitmapWidth, bitmapHeight)
            }
            .filter { region ->
                region.width >= MIN_WIDTH &&
                    region.height >= MIN_HEIGHT &&
                    region.area >= MIN_AREA &&
                    region.area <= MAX_AREA
            }
            .toList()

        val directMedia = accessibilityRegions.filter {
            it.source == DetectionRegionSource.BROWSER_DIRECT_MEDIA
        }
        if (directMedia.isNotEmpty()) {
            // Google Images and other accessible pages provide exact image nodes. Do not mix noisy
            // screenshot guesses into a result that is already authoritative.
            return deduplicate(directMedia)
        }

        val (containers, boundedFallbacks) = accessibilityRegions.partition(::isContainer)
        val plausibleVisualMedia = visualRegions
            .asSequence()
            .filter { it.source == DetectionRegionSource.VISUAL_MEDIA }
            .filter(::hasValidBounds)
            .filter { region ->
                region.width >= MIN_WIDTH &&
                    region.height >= MIN_HEIGHT &&
                    region.area >= MIN_AREA &&
                    region.area <= MAX_VISUAL_MEDIA_AREA
            }
            .toList()

        val visualMediaFallback = when {
            containers.isNotEmpty() -> plausibleVisualMedia.filter { visual ->
                containers.any { container ->
                    intersectionArea(visual, container) / visual.area.coerceAtLeast(MIN_NON_ZERO_AREA) >=
                        MIN_VISUAL_CONTAINMENT &&
                        visual.area <= container.area * MAX_CONTAINER_CHILD_AREA
                }
            }
            boundedFallbacks.isEmpty() -> plausibleVisualMedia.filter {
                // With no usable Chrome media node, a substantial pixel rectangle is better
                // evidence than the accessibility tree. OpenCV's own geometry/texture checks have
                // already rejected tiny icons and plain text before candidates reach this planner.
                it.area >= MIN_STANDALONE_VISUAL_AREA
            }
            else -> emptyList()
        }

        // Oversized accessibility rectangles are deliberately absent here. If the pixels do not
        // reveal individual media, sending the whole gallery/page to the model recreates the exact
        // false localization this planner exists to prevent.
        return deduplicate(boundedFallbacks + visualMediaFallback)
    }

    private fun isLikelyMetadataControl(
        region: DetectionRegion,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): Boolean {
        if (region.source != DetectionRegionSource.BROWSER_CHROME_METADATA) return false
        val pixelWidth = region.width * bitmapWidth
        val pixelHeight = region.height * bitmapHeight
        val pixelAspect = pixelWidth / pixelHeight.coerceAtLeast(1f)
        return region.area <= MAX_METADATA_CONTROL_AREA &&
            pixelAspect in MIN_SQUARE_CONTROL_ASPECT..MAX_SQUARE_CONTROL_ASPECT
    }

    private fun deduplicate(regions: List<DetectionRegion>): List<DetectionRegion> =
        regions
            // Chrome's hasImage flag is recursive: ancestors inherit it from image descendants.
            // Smallest-first keeps the actual media node and rejects its enclosing card/page.
            .sortedBy(DetectionRegion::area)
            .fold(mutableListOf()) { selected, candidate ->
                val duplicate = selected.any { existing ->
                    normalizedIntersectionOverUnion(existing, candidate) >= DUPLICATE_IOU ||
                        normalizedContainment(existing, candidate) >= DUPLICATE_CONTAINMENT
                }
                if (!duplicate && selected.size < MAX_REGIONS) selected += candidate
                selected
            }

    private fun isContainer(region: DetectionRegion): Boolean =
        region.area >= CONTAINER_MIN_AREA ||
            region.height >= CONTAINER_MIN_HEIGHT ||
            (region.width >= WIDE_CONTAINER_MIN_WIDTH && region.area >= WIDE_CONTAINER_MIN_AREA)

    private fun intersectionArea(first: DetectionRegion, second: DetectionRegion): Float {
        val width = (min(first.right, second.right) - max(first.left, second.left))
            .coerceAtLeast(0f)
        val height = (min(first.bottom, second.bottom) - max(first.top, second.top))
            .coerceAtLeast(0f)
        return width * height
    }

    private fun hasValidBounds(region: DetectionRegion): Boolean =
        region.left in 0f..1f && region.top in 0f..1f &&
            region.right in 0f..1f && region.bottom in 0f..1f &&
            region.right > region.left && region.bottom > region.top

    private fun normalizedContainment(
        first: DetectionRegion,
        second: DetectionRegion
    ): Float {
        val width = (min(first.right, second.right) - max(first.left, second.left))
            .coerceAtLeast(0f)
        val height = (min(first.bottom, second.bottom) - max(first.top, second.top))
            .coerceAtLeast(0f)
        return width * height / min(first.area, second.area).coerceAtLeast(0.000001f)
    }

    private const val MIN_WIDTH = 0.10f
    private const val MIN_HEIGHT = 0.06f
    private const val MIN_AREA = 0.008f
    private const val MAX_AREA = 0.90f
    private const val MAX_VISUAL_MEDIA_AREA = 0.60f
    private const val MIN_STANDALONE_VISUAL_AREA = 0.03f
    private const val MAX_METADATA_CONTROL_AREA = 0.015f
    private const val MIN_SQUARE_CONTROL_ASPECT = 0.75f
    private const val MAX_SQUARE_CONTROL_ASPECT = 1.33f
    private const val CONTAINER_MIN_AREA = 0.30f
    private const val CONTAINER_MIN_HEIGHT = 0.58f
    private const val WIDE_CONTAINER_MIN_WIDTH = 0.88f
    private const val WIDE_CONTAINER_MIN_AREA = 0.20f
    private const val MIN_VISUAL_CONTAINMENT = 0.82f
    private const val MAX_CONTAINER_CHILD_AREA = 0.80f
    private const val MIN_NON_ZERO_AREA = 0.000001f
    private const val DUPLICATE_IOU = 0.80f
    private const val DUPLICATE_CONTAINMENT = 0.92f
    private const val MAX_REGIONS = 16
}
