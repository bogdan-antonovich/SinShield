package app.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakTrackerTest {
    @Test
    fun protectionGapIsAllowedUntilTwentyFourHours() {
        val gapStarted = 1_000L

        assertFalse(
            StreakTracker.hasGracePeriodExpired(
                gapStarted,
                gapStarted + StreakTracker.GRACE_PERIOD_MILLIS - 1L
            )
        )
        assertTrue(
            StreakTracker.hasGracePeriodExpired(
                gapStarted,
                gapStarted + StreakTracker.GRACE_PERIOD_MILLIS
            )
        )
    }

    @Test
    fun elapsedTimerUsesCompletedTwentyFourHourDays() {
        val snapshot = StreakSnapshot(startedAtMillis = 5_000L, protectedDates = emptySet())

        assertEquals(3L, snapshot.completedDays(5_000L + 3L * StreakSnapshot.DAY_MILLIS + 42L))
    }

    @Test
    fun elapsedTimeDoesNotBecomeNegativeAfterClockChange() {
        val snapshot = StreakSnapshot(startedAtMillis = 10_000L, protectedDates = emptySet())

        assertEquals(0L, snapshot.elapsedMillis(9_000L))
    }

    @Test
    fun epochMillisUsesTheDevicesCalendarDay() {
        val instant = java.time.Instant.parse("2026-09-28T21:30:00Z").toEpochMilli()

        assertEquals(
            LocalDate.of(2026, 9, 29),
            StreakTracker.localDate(instant, ZoneId.of("Asia/Tbilisi"))
        )
    }
}
