package app.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewScrollTest {
    @Test
    fun `visible accessibility row is aligned to leave room for guide card`() {
        val destination = accessibilityPreviewScrollDestination(
            currentScroll = 400,
            maxScroll = 1_500,
            targetTop = 120f,
            desiredTop = 32f
        )

        assertEquals(488, destination)
    }

    @Test
    fun `scrolls up smoothly when accessibility row is above viewport`() {
        val destination = accessibilityPreviewScrollDestination(
            currentScroll = 900,
            maxScroll = 1_500,
            targetTop = -180f,
            desiredTop = 32f
        )

        assertEquals(688, destination)
    }

    @Test
    fun `scrolls down when accessibility row is below viewport`() {
        val destination = accessibilityPreviewScrollDestination(
            currentScroll = 100,
            maxScroll = 1_500,
            targetTop = 920f,
            desiredTop = 32f
        )

        assertEquals(988, destination)
    }

    @Test
    fun `partially clipped accessibility row is repositioned`() {
        val destination = accessibilityPreviewScrollDestination(
            currentScroll = 400,
            maxScroll = 1_500,
            targetTop = 20f,
            desiredTop = 32f
        )

        assertEquals(388, destination)
    }

    @Test
    fun `does not scroll when accessibility row is already aligned`() {
        val destination = accessibilityPreviewScrollDestination(
            currentScroll = 488,
            maxScroll = 1_500,
            targetTop = 32f,
            desiredTop = 32f
        )

        assertNull(destination)
    }
}
