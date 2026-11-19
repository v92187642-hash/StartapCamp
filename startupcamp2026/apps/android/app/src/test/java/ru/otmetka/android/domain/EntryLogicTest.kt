package ru.otmetka.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryLogicTest {

    private val now = 1_000_000L

    @Test
    fun futureEntryIsPlanned() {
        assertEquals(EntryState.PLANNED, entryState(isMarked = false, scheduledAtMillis = now + 1, nowMillis = now))
    }

    @Test
    fun pastEntryWithoutMarkHasNoMark() {
        assertEquals(EntryState.NO_MARK, entryState(isMarked = false, scheduledAtMillis = now - 1, nowMillis = now))
    }

    @Test
    fun markedEntryIsMarkedWheneverItWasDue() {
        assertEquals(EntryState.MARKED, entryState(isMarked = true, scheduledAtMillis = now - 1, nowMillis = now))
        assertEquals(EntryState.MARKED, entryState(isMarked = true, scheduledAtMillis = now + 1, nowMillis = now))
    }

    @Test
    fun remindersAreTheNearestUnmarkedFutureEntries() {
        val entries = listOf(
            ReminderCandidate("past", now - 10, isMarked = false),
            ReminderCandidate("later", now + 300, isMarked = false),
            ReminderCandidate("marked", now + 50, isMarked = true),
            ReminderCandidate("soon", now + 100, isMarked = false),
            ReminderCandidate("far", now + 900, isMarked = false),
        )

        val selected = selectReminders(entries, now, limit = 2)

        assertEquals(listOf("soon", "later"), selected.map { it.entryId })
    }

    private val zone = java.time.ZoneId.of("Asia/Yekaterinburg")
    private val day = java.time.LocalDate.of(2026, 10, 9)

    @Test
    fun correctedMarkIsTheClockTimeInThePlansZone() {
        val noon = java.time.Instant.parse("2026-10-09T07:00:00Z") // 12:00 in Yekaterinburg
        assertEquals(
            java.time.Instant.parse("2026-10-09T03:30:00Z"),
            correctedMarkTime(day, java.time.LocalTime.of(8, 30), zone, noon),
        )
    }

    @Test
    fun correctedMarkCannotBeInTheFuture() {
        val noon = java.time.Instant.parse("2026-10-09T07:00:00Z")
        assertEquals(null, correctedMarkTime(day, java.time.LocalTime.of(12, 1), zone, noon))
        assertEquals(noon, correctedMarkTime(day, java.time.LocalTime.of(12, 0), zone, noon))
    }
}
