package app.sinshield

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainCountFormatterTest {
    @Test
    fun `small domain counts remain exact`() {
        assertEquals("999 domains ready", formatDomainCount(999))
    }

    @Test
    fun `large domain counts use compact thousands`() {
        assertEquals("76k+ domains ready", formatDomainCount(76_845))
    }
}
