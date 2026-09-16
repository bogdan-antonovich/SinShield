package com.example.sinshield

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
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
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

        fun settingsIntent(context: Context, policy: DeviceBackgroundPolicy): Intent {
            policy.settingsComponents.forEach { (packageName, className) ->
                val intent = Intent().setComponent(ComponentName(packageName, className))
                if (intent.resolveActivity(context.packageManager) != null) return intent
            }
            return Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        }
    }
}
