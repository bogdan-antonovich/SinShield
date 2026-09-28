package app.sinshield

import android.content.Context
import androidx.core.content.edit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class StreakSnapshot(
    val startedAtMillis: Long?,
    val protectedDates: Set<LocalDate>
) {
    val isStarted: Boolean get() = startedAtMillis != null

    fun elapsedMillis(nowMillis: Long): Long =
        startedAtMillis?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L

    fun completedDays(nowMillis: Long): Long = elapsedMillis(nowMillis) / DAY_MILLIS

    companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

internal object StreakTracker {
    private const val PREFERENCES = "streak_tracker"
    private const val STARTED_AT = "started_at"
    private const val UNPROTECTED_SINCE = "unprotected_since"
    private const val PROTECTED_DATES = "protected_dates"
    internal const val GRACE_PERIOD_MILLIS = StreakSnapshot.DAY_MILLIS

    fun startIfEligible(
        context: Context,
        fullyProtected: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        today: LocalDate = localDate(nowMillis)
    ): StreakSnapshot {
        val preferences = preferences(context)
        if (!preferences.contains(STARTED_AT) && fullyProtected) {
            preferences.edit {
                putLong(STARTED_AT, nowMillis)
                remove(UNPROTECTED_SINCE)
                putStringSet(PROTECTED_DATES, setOf(today.toString()))
            }
        }
        return updateProtection(context, fullyProtected, nowMillis, today)
    }

    fun updateProtection(
        context: Context,
        fullyProtected: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        today: LocalDate = localDate(nowMillis)
    ): StreakSnapshot {
        val preferences = preferences(context)
        val startedAt = preferences.getLong(STARTED_AT, 0L).takeIf { it > 0L }
            ?: return StreakSnapshot(null, emptySet())
        val unprotectedSince = preferences.getLong(UNPROTECTED_SINCE, 0L)

        if (unprotectedSince > 0L && hasGracePeriodExpired(unprotectedSince, nowMillis)) {
            preferences.edit {
                remove(STARTED_AT)
                remove(UNPROTECTED_SINCE)
                remove(PROTECTED_DATES)
            }
            return StreakSnapshot(null, emptySet())
        }

        val dates = preferences.getStringSet(PROTECTED_DATES, emptySet()).orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toMutableSet()
        preferences.edit {
            if (fullyProtected) {
                remove(UNPROTECTED_SINCE)
                dates += today
                val oldestKept = today.minusDays(14)
                dates.removeAll { it.isBefore(oldestKept) }
                putStringSet(PROTECTED_DATES, dates.map(LocalDate::toString).toSet())
            } else if (unprotectedSince == 0L) {
                putLong(UNPROTECTED_SINCE, nowMillis)
            }
        }
        return StreakSnapshot(startedAt, dates)
    }

    internal fun hasGracePeriodExpired(unprotectedSince: Long, nowMillis: Long): Boolean =
        nowMillis - unprotectedSince >= GRACE_PERIOD_MILLIS

    internal fun localDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
