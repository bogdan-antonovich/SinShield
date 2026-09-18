package app.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
