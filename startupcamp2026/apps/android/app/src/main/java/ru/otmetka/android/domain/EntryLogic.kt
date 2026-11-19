package ru.otmetka.android.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What the day view shows. "No mark" is not stored: it is a planned entry whose time has passed. */
enum class EntryState { PLANNED, MARKED, NO_MARK }

fun entryState(isMarked: Boolean, scheduledAtMillis: Long, nowMillis: Long): EntryState = when {
    isMarked -> EntryState.MARKED
    scheduledAtMillis < nowMillis -> EntryState.NO_MARK
    else -> EntryState.PLANNED
}

/**
 * The moment a corrected mark stands for: the clock time the user picked, on
 * the day shown, in the plan's time zone. null when that moment is still
 * ahead, because nothing can have been done in the future.
 */
fun correctedMarkTime(date: LocalDate, time: LocalTime, zone: ZoneId, now: Instant): Instant? =
    date.atTime(time).atZone(zone).toInstant().takeUnless { it.isAfter(now) }

/** "Назначение 1, четверг, 9 октября, 08:00: нет отметки. " — the start of a question about a missed entry. */
fun questionDraft(label: String, day: String, time: String): String = "$label, $day, $time: нет отметки. "

/** An entry that may need a reminder. */
data class ReminderCandidate(val entryId: String, val scheduledAtMillis: Long, val isMarked: Boolean)

/**
 * Which entries get an alarm: not marked, still in the future, the nearest
 * first. Android allows an app only a limited number of alarms, so the list
 * is cut; alarms are set again after every sync and every reminder.
 */
fun selectReminders(entries: List<ReminderCandidate>, nowMillis: Long, limit: Int): List<ReminderCandidate> =
    entries.asSequence()
        .filter { !it.isMarked && it.scheduledAtMillis > nowMillis }
        .sortedBy { it.scheduledAtMillis }
        .take(limit)
        .toList()
