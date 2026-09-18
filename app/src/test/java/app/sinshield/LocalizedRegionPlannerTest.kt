package app.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LocalizedRegionPlannerTest {
    @Test
    fun preservesEveryOpenCvRectangleExactlyOnce() {
        val first = visualRegion(0.1f, 0.2f, 0.8f, 0.5f)
        val second = visualRegion(0.02f, 0.6f, 0.98f, 0.9f)

        val planned = LocalizedRegionPlanner.plan(720, 1600, listOf(first, second))

        assertEquals(2, planned.size)
        assertSame(first, planned[0])
        assertSame(second, planned[1])
    }

    @Test
    fun rejectsRegionsThatWereNotFoundByOpenCv() {
        val visual = visualRegion(0.1f, 0.2f, 0.8f, 0.5f)
        val accessibility = visual.copy(source = DetectionRegionSource.ACCESSIBILITY)
        val fallback = visual.copy(source = DetectionRegionSource.VISUAL_FALLBACK)

        val planned = LocalizedRegionPlanner.plan(
            720,
            1600,
            listOf(accessibility, visual, fallback)
        )

        assertEquals(listOf(visual), planned)
    }

    private fun visualRegion(left: Float, top: Float, right: Float, bottom: Float) =
        DetectionRegion(left, top, right, bottom, DetectionRegionSource.VISUAL_MEDIA)
}
