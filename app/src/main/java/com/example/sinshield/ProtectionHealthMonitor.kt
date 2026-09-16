package com.example.sinshield

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/** Periodically tells the user when the protection setup is incomplete or appears to have stopped. */
internal object ProtectionHealthMonitor {
    private const val TAG = "SinShieldHealth"
    private const val PREFERENCES = "protection_health"
    private const val LAST_ACCESSIBILITY_HEARTBEAT = "last_accessibility_heartbeat"
    private const val LAST_MISSING_NOTIFICATION = "last_missing_notification"
    private const val LAST_STOPPED_NOTIFICATION = "last_stopped_notification"
    private const val VPN_EXPECTED = "vpn_expected"
    private const val VPN_FAILURE_AT = "vpn_failure_at"
    private const val VPN_HEARTBEAT_AT = "vpn_heartbeat_at"
    private const val VPN_ALWAYS_ON_OBSERVED = "vpn_always_on_observed"
    private const val CHANNEL_ID = "sinshield_attention"
    private const val MISSING_NOTIFICATION_ID = 20
    private const val STOPPED_NOTIFICATION_ID = 21
    private const val CHECK_INTERVAL_MS = 30L * 60L * 1_000L
    private const val STALE_SERVICE_MS = 45L * 60L * 1_000L
    private const val NOTIFICATION_COOLDOWN_MS = 12L * 60L * 60L * 1_000L

    fun start(context: Context) {
        createChannel(context)
        checkNow(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val checkIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ProtectionHealthReceiver::class.java)
                .setAction(ProtectionHealthReceiver.ACTION_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            android.os.SystemClock.elapsedRealtime() + CHECK_INTERVAL_MS,
            CHECK_INTERVAL_MS,
            checkIntent
        )
    }

    fun recordAccessibilityHeartbeat(context: Context) {
        preferences(context).edit {
            putLong(LAST_ACCESSIBILITY_HEARTBEAT, System.currentTimeMillis())
        }
        context.getSystemService(NotificationManager::class.java)
            .cancel(STOPPED_NOTIFICATION_ID)
    }

    fun setVpnExpected(context: Context, expected: Boolean) {
        preferences(context).edit {
            putBoolean(VPN_EXPECTED, expected)
            if (!expected) remove(VPN_FAILURE_AT)
        }
    }

    fun recordVpnRunning(context: Context) {
        preferences(context).edit {
            remove(VPN_FAILURE_AT)
            putLong(VPN_HEARTBEAT_AT, System.currentTimeMillis())
        }
    }

    fun recordVpnAlwaysOn(context: Context, alwaysOn: Boolean) {
        preferences(context).edit { putBoolean(VPN_ALWAYS_ON_OBSERVED, alwaysOn) }
    }

    fun wasVpnAlwaysOnObserved(context: Context): Boolean =
        preferences(context).getBoolean(VPN_ALWAYS_ON_OBSERVED, false)

    fun isVpnExpected(context: Context): Boolean =
        preferences(context).getBoolean(VPN_EXPECTED, false)

    fun recordVpnFailure(context: Context) {
        preferences(context).edit { putLong(VPN_FAILURE_AT, System.currentTimeMillis()) }
        checkNow(context)
    }

    fun checkNow(context: Context) {
        if (!canNotify(context)) return
        createChannel(context)
        val now = System.currentTimeMillis()
        val preferences = preferences(context)
        val missing = missingRequirements(context)
        if (missing.isNotEmpty()) {
            val last = preferences.getLong(LAST_MISSING_NOTIFICATION, 0L)
            if (now - last >= NOTIFICATION_COOLDOWN_MS) {
                notify(
                    context,
                    MISSING_NOTIFICATION_ID,
                    "Action required to activate SinSheld",
                    missing.joinToString(prefix = "Enable ", separator = ", ")
                )
                preferences.edit { putLong(LAST_MISSING_NOTIFICATION, now) }
            }
            return
        }

        context.getSystemService(NotificationManager::class.java)
            .cancel(MISSING_NOTIFICATION_ID)
        val heartbeat = preferences.getLong(LAST_ACCESSIBILITY_HEARTBEAT, 0L)
        val accessibilityLooksStopped = heartbeat > 0L && now - heartbeat >= STALE_SERVICE_MS
        val vpnHeartbeat = preferences.getLong(VPN_HEARTBEAT_AT, 0L)
        val vpnLooksStopped = preferences.getBoolean(VPN_EXPECTED, false) && (
            preferences.getLong(VPN_FAILURE_AT, 0L) > 0L ||
                (vpnHeartbeat > 0L && now - vpnHeartbeat >= STALE_SERVICE_MS)
            )
        if (accessibilityLooksStopped || vpnLooksStopped) {
            val last = preferences.getLong(LAST_STOPPED_NOTIFICATION, 0L)
            if (now - last >= NOTIFICATION_COOLDOWN_MS) {
                notify(
                    context,
                    STOPPED_NOTIFICATION_ID,
                    "SinSheld protection needs attention",
                    if (vpnLooksStopped) {
                        "Website protection stopped unexpectedly. Open SinSheld to turn it on again."
                    } else {
                        "The screen-protection service stopped responding. Open SinSheld to check it."
                    }
                )
                preferences.edit { putLong(LAST_STOPPED_NOTIFICATION, now) }
            }
        }
    }

    fun missingRequirements(context: Context): List<String> = buildList {
        if (!isAccessibilityEnabled(context)) add("Accessibility")
        if (!Settings.canDrawOverlays(context)) add("display over other apps")
        // OEM battery modes such as Xiaomi's “No restrictions” are private settings and do not
        // necessarily update Android's Doze allowlist. Never report that unobservable OEM control
        // as disabled; its dedicated setup row explains that it must be checked manually.
        if (DeviceBackgroundPolicy.current() == null && !isBatteryOptimizationDisabled(context)) {
            add("unrestricted battery use")
        }
        DeviceBackgroundPolicy.current()?.let { policy ->
            if (!DeviceBackgroundPolicy.hasVisitedSettings(context, policy)) {
                add("${policy.displayName} background startup")
            }
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ShieldAccessibilityService::class.java)
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    /**
     * Repairs protection after boot, package replacement, or a watchdog check. Android owns an
     * enabled AccessibilityService and will bind it itself; the app must never fake or re-request
     * that grant. A user-approved VPN can be restarted without showing its consent dialog again.
     */
    fun restoreExpectedProtection(context: Context) {
        if (!isVpnExpected(context) || VpnService.prepare(context) != null) return
        runCatching { AdultContentVpnService.start(context) }
            .onFailure { Log.w(TAG, "Could not restore expected VPN from background", it) }
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notify(context: Context, id: Int, title: String, message: String) {
        val openApp = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.sinshield_notification)
            .setColor(context.getColor(R.color.sinshield_primary))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    private fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Protection alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Required-permission and stopped-protection warnings"
            }
        )
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

class ProtectionHealthReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ProtectionHealthMonitor.start(context)
                ProtectionHealthMonitor.restoreExpectedProtection(context)
            }
            ACTION_CHECK -> {
                ProtectionHealthMonitor.checkNow(context)
                ProtectionHealthMonitor.restoreExpectedProtection(context)
            }
        }
    }

    companion object {
        const val ACTION_CHECK = "com.example.sinshield.action.CHECK_PROTECTION_HEALTH"
    }
}
