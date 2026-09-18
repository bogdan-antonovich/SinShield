package app.sinshield

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.text.DateFormat
import java.util.Date

internal data class ProcessExitReport(
    val headline: String,
    val detail: String,
    val needsAttention: Boolean
)

/** Turns Android's previous-process record into an explanation a user can act on. */
internal object ProcessExitDiagnostics {
    fun latest(context: Context): ProcessExitReport {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ProcessExitReport(
                "Restart diagnostics unavailable",
                "This Android version cannot report why the previous app process ended.",
                false
            )
        }
        val exits = context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName, 0, EXIT_HISTORY_LIMIT)
        // A package update is expected to stop the old process and is normally the newest record
        // immediately after shipping a fix. Do not let it hide the overload/crash the user needed
        // explained. Fall back to the newest benign record when no failure exists in history.
        val exit = exits.firstOrNull(::needsAttention)
            ?: exits.firstOrNull()
            ?: return ProcessExitReport(
                "No previous shutdown recorded",
                "Android has not reported a prior SinShield process termination.",
                false
            )
        val occurredAt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(exit.timestamp))
        val memory = exit.pss.takeIf { it > 0L }?.let { " · about ${it / 1024} MB in use" }.orEmpty()
        val description = exit.description?.takeIf(String::isNotBlank)
        val systemDetail = description?.let { " · $it" }.orEmpty()
        val stoppedByOemLoadWatchdog = isOemLoadWatchdogStop(exit.reason, description)
        val explanation = reason(exit.reason, stoppedByOemLoadWatchdog)
        return ProcessExitReport(
            headline = "Last protection stop: ${explanation.first}",
            detail = "$occurredAt · ${explanation.second}$memory$systemDetail",
            needsAttention = needsAttention(exit)
        )
    }

    private fun reason(reason: Int, stoppedByOemLoadWatchdog: Boolean): Pair<String, String> = when {
        stoppedByOemLoadWatchdog ->
            "stopped by device load watchdog" to
                "The device force-stopped SinShield after detecting excessive load or an ANR; " +
                "Android cannot restart a force-stopped package automatically"
        reason == ApplicationExitInfo.REASON_LOW_MEMORY ->
            "low memory" to "Android reclaimed RAM; protection should recover automatically"
        reason == ApplicationExitInfo.REASON_CRASH ->
            "app crash" to "Android recorded an unhandled app error"
        reason == ApplicationExitInfo.REASON_CRASH_NATIVE ->
            "native crash" to "A native ML or image-processing component failed"
        reason == ApplicationExitInfo.REASON_ANR ->
            "not responding" to "Android stopped an unresponsive process"
        reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE ->
            "resource limit" to "Android stopped excessive resource use"
        reason == ApplicationExitInfo.REASON_USER_REQUESTED ->
            "force-stopped" to "The user or system UI force-stopped the package"
        reason == ApplicationExitInfo.REASON_PERMISSION_CHANGE ->
            "permission changed" to "Android restarted SinShield after a permission change"
        reason == ApplicationExitInfo.REASON_PACKAGE_UPDATED ->
            "app updated" to "Android replaced the running app with a new version"
        reason == ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE ->
            "app configuration changed" to "Android restarted the package after a component change"
        reason == ApplicationExitInfo.REASON_SIGNALED ->
            "process terminated" to "Android or the device firmware sent a termination signal"
        reason == ApplicationExitInfo.REASON_FREEZER ->
            "background freezer" to "Android's app freezer terminated the process"
        reason == ApplicationExitInfo.REASON_EXIT_SELF ->
            "normal app exit" to "The previous process stopped itself"
        else -> "system restart" to "Android did not provide a more specific reason"
    }

    private fun isOemLoadWatchdogStop(reason: Int, description: String?): Boolean =
        reason == ApplicationExitInfo.REASON_USER_REQUESTED &&
            description.orEmpty().contains("system loading is too high", ignoreCase = true)

    @RequiresApi(Build.VERSION_CODES.R)
    private fun needsAttention(exit: ApplicationExitInfo): Boolean =
        isOemLoadWatchdogStop(exit.reason, exit.description) || exit.reason in attentionReasons

    private val attentionReasons = setOf(
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_SIGNALED,
        ApplicationExitInfo.REASON_FREEZER
    )

    private const val EXIT_HISTORY_LIMIT = 10
}
