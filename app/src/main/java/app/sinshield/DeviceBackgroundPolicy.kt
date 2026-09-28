package app.sinshield

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

internal enum class DeviceBackgroundPolicy(
    val displayName: String,
    internal val settingsComponents: List<Pair<String, String>>
) {
    XIAOMI(
        "Xiaomi",
        listOf(
            "com.miui.securitycenter" to
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
    ),
    HUAWEI(
        "Huawei",
        listOf(
            "com.huawei.systemmanager" to
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        )
    ),
    OPPO(
        "Oppo",
        listOf(
            "com.coloros.safecenter" to
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to
                "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to
                "com.oppo.safe.permission.startup.StartupAppListActivity"
        )
    ),
    VIVO(
        "Vivo",
        listOf(
            "com.vivo.permissionmanager" to
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        )
    ),
    SAMSUNG("Samsung", emptyList());

    companion object {
        private const val PREFERENCES = "device_background_setup"
        private const val BATTERY_REVIEWED_SUFFIX = "_battery_no_restrictions"

        fun current(): DeviceBackgroundPolicy? = detect(Build.MANUFACTURER, Build.BRAND)

        internal fun detect(manufacturer: String?, brand: String?): DeviceBackgroundPolicy? {
            val identity = "${manufacturer.orEmpty()} ${brand.orEmpty()}".lowercase()
            return when {
                listOf("xiaomi", "redmi", "poco").any(identity::contains) -> XIAOMI
                listOf("huawei", "honor").any(identity::contains) -> HUAWEI
                listOf("oppo", "realme", "oneplus").any(identity::contains) -> OPPO
                listOf("vivo", "iqoo").any(identity::contains) -> VIVO
                identity.contains("samsung") -> SAMSUNG
                else -> null
            }
        }

        fun hasVisitedSettings(context: Context, policy: DeviceBackgroundPolicy): Boolean =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(policy.name, false)

        fun markSettingsVisited(context: Context, policy: DeviceBackgroundPolicy) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(policy.name, true)
                .apply()
        }

        fun hasConfirmedNoRestrictions(context: Context, policy: DeviceBackgroundPolicy): Boolean =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(policy.name + BATTERY_REVIEWED_SUFFIX, false)

        fun confirmNoRestrictions(context: Context, policy: DeviceBackgroundPolicy) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(policy.name + BATTERY_REVIEWED_SUFFIX, true)
                .apply()
        }

        fun launchSettings(
            context: Context,
            policy: DeviceBackgroundPolicy,
            launch: (Intent) -> Unit
        ): Boolean = launchFirstSupported(settingsIntents(context.packageName, policy), launch)

        internal fun settingsIntents(
            packageName: String,
            policy: DeviceBackgroundPolicy
        ): List<Intent> = policy.settingsComponents.map { (settingsPackage, className) ->
            Intent().setComponent(ComponentName(settingsPackage, className))
        } + Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )

        internal fun launchFirstSupported(
            intents: List<Intent>,
            launch: (Intent) -> Unit
        ): Boolean {
            intents.forEach { intent ->
                try {
                    launch(intent)
                    return true
                } catch (_: ActivityNotFoundException) {
                    // Manufacturer setting components vary between Android builds.
                } catch (_: SecurityException) {
                    // Some builds expose the component but reserve it for system-signed apps.
                }
            }
            return false
        }
    }
}
