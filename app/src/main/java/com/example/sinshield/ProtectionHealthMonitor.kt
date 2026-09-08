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
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/** Periodically tells the user when the protection setup is incomplete or appears to have stopped. */
internal object ProtectionHealthMonitor {
    private const val PREFERENCES = "protection_health"
    private const val LAST_ACCESSIBILITY_HEARTBEAT = "last_accessibility_heartbeat"
    private const val LAST_MISSING_NOTIFICATION = "last_missing_notification"
    private const val LAST_STOPPED_NOTIFICATION = "last_stopped_notification"
    private const val VPN_EXPECTED = "vpn_expected"
    private const val VPN_FAILURE_AT = "vpn_failure_at"
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
        preferences(context).edit { remove(VPN_FAILURE_AT) }
    }

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
                    "Action required to activate KillLust",
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
        val vpnLooksStopped = preferences.getBoolean(VPN_EXPECTED, false) &&
            preferences.getLong(VPN_FAILURE_AT, 0L) > 0L
        if (accessibilityLooksStopped || vpnLooksStopped) {
            val last = preferences.getLong(LAST_STOPPED_NOTIFICATION, 0L)
            if (now - last >= NOTIFICATION_COOLDOWN_MS) {
                notify(
                    context,
                    STOPPED_NOTIFICATION_ID,
                    "KillLust protection needs attention",
                    if (vpnLooksStopped) {
                        "Website protection stopped unexpectedly. Open KillLust to turn it on again."
                    } else {
                        "The screen-protection service stopped responding. Open KillLust to check it."
                    }
                )
                preferences.edit { putLong(LAST_STOPPED_NOTIFICATION, now) }
            }
        }
    }

    fun missingRequirements(context: Context): List<String> = buildList {
        if (!isAccessibilityEnabled(context)) add("Accessibility")
        if (!Settings.canDrawOverlays(context)) add("display over other apps")
        if (!isBatteryOptimizationDisabled(context)) add("unrestricted battery use")
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
            .setSmallIcon(R.mipmap.ic_launcher)
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
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                ProtectionHealthMonitor.start(context)
            ACTION_CHECK -> ProtectionHealthMonitor.checkNow(context)
        }
    }

    companion object {
        const val ACTION_CHECK = "com.example.sinshield.action.CHECK_PROTECTION_HEALTH"
    }
}
