package ru.otmetka.android.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import java.time.Instant
import ru.otmetka.android.data.DiaryRepository
import ru.otmetka.android.domain.ReminderCandidate
import ru.otmetka.android.domain.selectReminders

/**
 * Reminders fire on the phone, not from the server: an alarm set here works
 * without a network. One alarm per upcoming entry of the user's own plans.
 *
 * [reschedule] is called after every sync, after every mark, after every
 * reminder and when the phone restarts.
 */
class ReminderScheduler(private val context: Context, private val diary: DiaryRepository) {

    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    suspend fun reschedule() {
        val now = Instant.now()
        val candidates = diary.upcomingOwnEntries(now, limit = 200)
            .map { ReminderCandidate(it.id, it.scheduledAt, it.isMarked) }
        val selected = selectReminders(candidates, now.toEpochMilli(), MAX_ALARMS)

        // Cancel what was set before and is no longer wanted (marked, or gone from the plan).
        val wanted = selected.map { it.entryId }.toSet()
        for (entryId in prefs.getStringSet(SCHEDULED, emptySet()).orEmpty() - wanted) {
            alarms.cancel(alarmIntent(entryId))
        }
        for (reminder in selected) {
            setAlarm(reminder.entryId, reminder.scheduledAtMillis)
        }
        prefs.edit { putStringSet(SCHEDULED, wanted) }
    }

    fun cancelAll() {
        for (entryId in prefs.getStringSet(SCHEDULED, emptySet()).orEmpty()) {
            alarms.cancel(alarmIntent(entryId))
        }
        prefs.edit { remove(SCHEDULED) }
    }

    private fun setAlarm(entryId: String, atMillis: Long) {
        val intent = alarmIntent(entryId)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exactAllowed) {
            // Fires on time even when the phone is idle.
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        } else {
            // The user switched exact alarms off: the reminder may be some minutes late.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    /** The same entry always gives an equal intent, so setting it again replaces the old alarm. */
    private fun alarmIntent(entryId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        entryId.hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            .putExtra(ReminderReceiver.EXTRA_ENTRY_ID, entryId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        /** Android gives an app a few hundred alarms at most; a week rarely needs more than this. */
        const val MAX_ALARMS = 60
        const val SCHEDULED = "scheduled"
    }
}
