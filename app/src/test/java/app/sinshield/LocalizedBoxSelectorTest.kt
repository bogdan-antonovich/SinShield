package app.sinshield

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizedBoxSelectorTest {
    @Test
    fun overlappingClassificationBandsProduceOneCover() {
        val strongest = DetectionBox(0f, 0.2f, 1f, 0.8f, "Sexy", 0.72f)
        val overlap = DetectionBox(0f, 0.4f, 1f, 1f, "Sexy", 0.55f)

        val selected = LocalizedBoxSelector.select(
            detected = listOf(strongest, overlap),
            fallback = emptyList(),
            maximum = 3
        )

        assertEquals(listOf(strongest), selected)
    }

    @Test
    fun separateUnsafeImagesKeepSeparateCovers() {
        val upper = DetectionBox(0.05f, 0.1f, 0.95f, 0.4f, "Porn", 0.8f)
        val lower = DetectionBox(0.05f, 0.55f, 0.95f, 0.9f, "Porn", 0.7f)

        val selected = LocalizedBoxSelector.select(
            detected = listOf(upper, lower),
            fallback = emptyList(),
            maximum = 3
        )

        assertEquals(listOf(upper, lower), selected)
    }
}
