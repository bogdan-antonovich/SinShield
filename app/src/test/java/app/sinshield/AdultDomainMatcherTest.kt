package app.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultDomainMatcherTest {
    @Test
    fun parsesHostsAndPlainDomainFormats() {
        val matcher = AdultDomainMatcher.fromLines(
            sequenceOf(
                "# comment",
                "0.0.0.0 adult-site.test",
                "127.0.0.1 www.second-adult.test # trailing comment",
                "third-adult.test",
                "::1 localhost"
            )
        )

        assertEquals(3, matcher.size)
        assertTrue(matcher.isBlocked("adult-site.test"))
        assertTrue(matcher.isBlocked("CDN.ADULT-SITE.TEST."))
        assertTrue(matcher.isBlocked("www.second-adult.test"))
        assertFalse(matcher.isBlocked("safe.test"))
        assertFalse(matcher.isBlocked("notadult-site.test"))
    }

    @Test
    fun userDomainsAreValidatedAndCanExtendAMatcher() {
        assertEquals("example.com", AdultDomainMatcher.normalizeUserDomain(" Example.COM. "))
        assertEquals("xn--bcher-kva.example", AdultDomainMatcher.normalizeUserDomain("bücher.example"))
        assertEquals(null, AdultDomainMatcher.normalizeUserDomain("https://example.com/page"))
        assertEquals(null, AdultDomainMatcher.normalizeUserDomain("not-a-domain"))

        val matcher = AdultDomainMatcher.empty().withDomains(listOf("example.com"))
        assertTrue(matcher.isBlocked("images.example.com"))
    }
}
