package app.sinshield

import android.content.Context
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProtectionPreviewRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ProtectionPreviewRepository.restartForTesting(context)
        ProtectionPreviewRepository.moveTo(
            context,
            ProtectionPreviewStage.WAITING_SITE_BLOCK
        )
    }

    @After
    fun tearDown() {
        ProtectionPreviewRepository.restartForTesting(context)
    }

    @Test
    fun `website step temporarily blocks only NeverSSL`() {
        assertTrue(
            ProtectionPreviewRepository.shouldTemporarilyBlock(context, "neverssl.com")
        )
        assertTrue(
            ProtectionPreviewRepository.shouldTemporarilyBlock(context, "www.neverssl.com.")
        )
        assertFalse(ProtectionPreviewRepository.shouldTemporarilyBlock(context, "google.com"))
        assertFalse(ProtectionPreviewRepository.shouldTemporarilyBlock(context, "example.com"))
        assertFalse(
            ProtectionPreviewRepository.shouldTemporarilyBlock(context, "notneverssl.com")
        )
    }

    @Test
    fun `test domain stays blocked until the real overlay is ready`() {
        assertTrue(ProtectionPreviewRepository.recordSiteBlocked(context))

        assertTrue(
            ProtectionPreviewRepository.shouldTemporarilyBlock(context, "neverssl.com")
        )
        assertTrue(
            ProtectionPreviewRepository.shouldTemporarilyBlock(context, "www.neverssl.com.")
        )
        assertFalse(ProtectionPreviewRepository.recordSiteBlocked(context))

        ProtectionPreviewRepository.releaseSiteBlockTest(context)
        assertFalse(
            ProtectionPreviewRepository.shouldTemporarilyBlock(context, "neverssl.com")
        )
    }

    @Test
    fun `test domain is released after continuing the preview`() {
        ProtectionPreviewRepository.recordSiteBlocked(context)
        ProtectionPreviewRepository.moveTo(context, ProtectionPreviewStage.WAITING_CARS_SEARCH)

        assertFalse(
            ProtectionPreviewRepository.shouldTemporarilyBlock(context, "neverssl.com")
        )
    }

    @Test
    fun `legacy required battery stage continues to vpn`() {
        context.getSharedPreferences("protection_preview", Context.MODE_PRIVATE)
            .edit()
            .putString("stage", ProtectionPreviewStage.BATTERY.name)
            .apply()

        assertTrue(ProtectionPreviewRepository.stage(context) == ProtectionPreviewStage.VPN)
    }
}
