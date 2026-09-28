package app.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewScrollTest {
    @Test
    fun `does not scroll when accessibility row is fully visible`() {
        val destination = accessibilityPreviewScrollDestination(
            currentScroll = 400,
            maxScroll = 1_500,
            targetTop = 120f,
            targetBottom = 260f,
            viewportHeight = 800f,
            desiredTop = 32f,
            viewportMargin = 40f
        )

        assertNull(destination)
    }

    @Test
    fun `scrolls up smoothly when accessibility row is above viewport`() {
        val destination = accessibilityPreviewScrollDestination(
            currentScroll = 900,
            maxScroll = 1_500,
            targetTop = -180f,
            targetBottom = -40f,
            viewportHeight = 800f,
            desiredTop = 32f,
            viewportMargin = 40f
        )

        assertEquals(688, destination)
    }

    @Test
    fun `scrolls down when accessibility row is below viewport`() {
        val destination = accessibilityPreviewScrollDestination(
            currentScroll = 100,
            maxScroll = 1_500,
            targetTop = 920f,
            targetBottom = 1_060f,
            viewportHeight = 800f,
            desiredTop = 32f,
            viewportMargin = 40f
        )

        assertEquals(988, destination)
    }

    @Test
    fun `partially clipped accessibility row is repositioned`() {
        val destination = accessibilityPreviewScrollDestination(
            currentScroll = 400,
            maxScroll = 1_500,
            targetTop = 20f,
            targetBottom = 160f,
            viewportHeight = 800f,
            desiredTop = 32f,
            viewportMargin = 40f
        )

        assertEquals(388, destination)
    }
}
