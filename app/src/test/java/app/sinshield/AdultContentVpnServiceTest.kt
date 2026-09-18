package app.sinshield

import android.app.Service
import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdultContentVpnServiceTest {
    private lateinit var controller: ServiceController<AdultContentVpnService>
    private lateinit var service: AdultContentVpnService
    private lateinit var shadowService: ShadowService
    private lateinit var shadowApplication: ShadowApplication
    private var destroyed = false

    @Before
    fun setUp() {
        controller = Robolectric.buildService(AdultContentVpnService::class.java).create()
        service = controller.get()
        shadowService = shadowOf(service)
        shadowApplication = shadowOf(service.application)
        shadowApplication.broadcastIntents.clear()
    }

    @After
    fun tearDown() {
        if (!destroyed) controller.destroy()
    }

    @Test
    fun nullIntentDoesNotRestartOrStopService() {
        val result = service.onStartCommand(null, 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        assertFalse(shadowService.isStoppedBySelf)
        assertTrue(shadowApplication.broadcastIntents.isEmpty())
    }

    @Test
    fun stopCommandStopsServiceAndBroadcastsStoppedState() {
        val result = service.onStartCommand(Intent(STOP_ACTION), 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        assertTrue(shadowService.isStoppedBySelf)
        assertStoppedStateWasBroadcast()
    }

    @Test
    fun revokeMarksVpnUnexpectedAndStopsService() {
        ProtectionHealthMonitor.setVpnExpected(service, true)

        service.onRevoke()

        assertFalse(ProtectionHealthMonitor.isVpnExpected(service))
        assertTrue(shadowService.isStoppedBySelf)
        assertStoppedStateWasBroadcast()
    }

    @Test
    fun destroyCleansUpWithoutExplicitlyStoppingStickyService() {
        controller.destroy()
        destroyed = true

        assertFalse(shadowService.isStoppedBySelf)
        assertStoppedStateWasBroadcast()
    }

    private fun assertStoppedStateWasBroadcast() {
        val state = shadowApplication.broadcastIntents.lastOrNull {
            it.action == AdultContentVpnService.ACTION_STATE_CHANGED
        }

        assertNotNull(state)
        assertEquals(service.packageName, state!!.`package`)
        assertFalse(state.getBooleanExtra(AdultContentVpnService.EXTRA_RUNNING, true))
        assertFalse(state.getBooleanExtra(AdultContentVpnService.EXTRA_ALWAYS_ON, true))
    }

    private companion object {
        const val STOP_ACTION = "com.example.sinshield.action.STOP_VPN"
    }
}
