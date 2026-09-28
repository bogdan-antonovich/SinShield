package app.sinshield

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceBackgroundPolicyTest {
    @Test
    fun detectsXiaomiFamilyBrands() {
        assertEquals(DeviceBackgroundPolicy.XIAOMI, DeviceBackgroundPolicy.detect("Xiaomi", "Redmi"))
        assertEquals(DeviceBackgroundPolicy.XIAOMI, DeviceBackgroundPolicy.detect("unknown", "POCO"))
    }

    @Test
    fun detectsOtherManagedBackgroundFamilies() {
        assertEquals(DeviceBackgroundPolicy.HUAWEI, DeviceBackgroundPolicy.detect("HONOR", "honor"))
        assertEquals(DeviceBackgroundPolicy.OPPO, DeviceBackgroundPolicy.detect("realme", "realme"))
        assertEquals(DeviceBackgroundPolicy.VIVO, DeviceBackgroundPolicy.detect("vivo", "iQOO"))
        assertEquals(DeviceBackgroundPolicy.SAMSUNG, DeviceBackgroundPolicy.detect("samsung", "Galaxy"))
    }

    @Test
    fun leavesStandardAndroidDevicesAlone() {
        assertNull(DeviceBackgroundPolicy.detect("Google", "Pixel"))
    }

    @Test
    fun oppoSettingsIncludeKnownVariantsAndAppDetailsFallback() {
        val intents = DeviceBackgroundPolicy.settingsIntents("app.sinshield", DeviceBackgroundPolicy.OPPO)

        assertEquals(
            listOf(
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            ),
            intents.dropLast(1).map { it.component?.className }
        )
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intents.last().action)
        assertEquals("package:app.sinshield", intents.last().data.toString())
    }

    @Test
    fun deniedOrMissingManufacturerSettingsFallBackWithoutCrashing() {
        val intents = listOf(Intent("first"), Intent("second"), Intent("fallback"))
        val attemptedActions = mutableListOf<String?>()

        val launched = DeviceBackgroundPolicy.launchFirstSupported(intents) { intent ->
            attemptedActions += intent.action
            when (intent.action) {
                "first" -> throw SecurityException("system-only Oppo activity")
                "second" -> throw ActivityNotFoundException("not installed on this build")
            }
        }

        assertTrue(launched)
        assertEquals(listOf("first", "second", "fallback"), attemptedActions)
    }

    @Test
    fun returnsFalseWhenEvenStandardSettingsCannotBeOpened() {
        val launched = DeviceBackgroundPolicy.launchFirstSupported(listOf(Intent("fallback"))) {
            throw ActivityNotFoundException()
        }

        assertFalse(launched)
    }
}
