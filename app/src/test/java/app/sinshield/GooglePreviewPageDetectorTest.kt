package app.sinshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePreviewPageDetectorTest {
    @Test
    fun `recognizes direct navigation to preview domain`() {
        val evidence = BrowserPageEvidence(
            editableTexts = emptyList(),
            selectedTexts = emptyList(),
            addressBarTexts = listOf("https://www.example.com/path"),
            visibleTexts = listOf("Example Domain")
        )

        assertTrue(PreviewBrowserPageDetector.isAtDomain(evidence, "example.com"))
    }

    @Test
    fun `does not confuse a google query with direct domain navigation`() {
        val evidence = BrowserPageEvidence(
            editableTexts = listOf("example.com"),
            selectedTexts = emptyList(),
            addressBarTexts = listOf("https://www.google.com/search?q=example.com"),
            visibleTexts = listOf("Google")
        )

        assertFalse(PreviewBrowserPageDetector.isAtDomain(evidence, "example.com"))
    }

    @Test
    fun `does not accept a lookalike preview domain`() {
        val evidence = BrowserPageEvidence(
            editableTexts = emptyList(),
            selectedTexts = emptyList(),
            addressBarTexts = listOf("https://example.com.attacker.test"),
            visibleTexts = emptyList()
        )

        assertFalse(PreviewBrowserPageDetector.isAtDomain(evidence, "example.com"))
    }

    @Test
    fun `recognizes requested google search`() {
        val evidence = BrowserPageEvidence(
            editableTexts = listOf("example.com"),
            selectedTexts = emptyList(),
            addressBarTexts = listOf("https://www.google.com/search?q=example.com"),
            visibleTexts = listOf("Google")
        )

        assertTrue(GooglePreviewPageDetector.isSearchFor(evidence, "example.com"))
    }

    @Test
    fun `does not accept matching words from a non-google page`() {
        val evidence = BrowserPageEvidence(
            editableTexts = listOf("cars"),
            selectedTexts = listOf("Images"),
            addressBarTexts = listOf("https://example.com/search?q=cars&udm=2"),
            visibleTexts = emptyList()
        )

        assertFalse(GooglePreviewPageDetector.isCarsImages(evidence))
    }

    @Test
    fun `recognizes cars image mode from current url`() {
        val evidence = BrowserPageEvidence(
            editableTexts = listOf("cars"),
            selectedTexts = emptyList(),
            addressBarTexts = listOf("https://www.google.com/search?q=cars&udm=2"),
            visibleTexts = listOf("Google")
        )

        assertTrue(GooglePreviewPageDetector.isCarsImages(evidence))
    }

    @Test
    fun `recognizes selected images tab when url is shortened`() {
        val evidence = BrowserPageEvidence(
            editableTexts = listOf("cars"),
            selectedTexts = listOf("Images"),
            addressBarTexts = listOf("google.com"),
            visibleTexts = listOf("Google", "Cars")
        )

        assertTrue(GooglePreviewPageDetector.isCarsImages(evidence))
    }

    @Test
    fun `cars web results do not count as image results`() {
        val evidence = BrowserPageEvidence(
            editableTexts = listOf("cars"),
            selectedTexts = listOf("All"),
            addressBarTexts = listOf("https://www.google.com/search?q=cars"),
            visibleTexts = listOf("Google", "Images")
        )

        assertFalse(GooglePreviewPageDetector.isCarsImages(evidence))
    }
}
