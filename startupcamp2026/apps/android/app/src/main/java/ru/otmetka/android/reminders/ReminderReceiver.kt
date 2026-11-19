package ru.otmetka.android.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.otmetka.android.MainActivity
import ru.otmetka.android.OtmetkaApp
import ru.otmetka.android.R
import ru.otmetka.android.data.SyncWorker

/**
 * Receives two things: the alarm at the time of an entry (shows the reminder)
 * and the tap on «Отметить» in that reminder (marks the entry).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as OtmetkaApp
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
        // A receiver must return quickly; goAsync keeps it alive while the database is read.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> remind(app, entryId)
                    ACTION_MARK -> {
                        app.diary.mark(entryId, Instant.now())
                        NotificationManagerCompat.from(app).cancel(entryId.hashCode())
                        SyncWorker.runSoon(app)
                    }
                }
                app.reminders.reschedule()
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun remind(app: OtmetkaApp, entryId: String) {
        val entry = app.diary.entry(entryId) ?: return
        // Marked ahead of time, or the user logged out: nothing to remind about.
        if (entry.isMarked || !app.tokenStore.loggedIn.value) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(app)

        // The same clock the day view shows: the plan's time zone, not necessarily the phone's.
        val zone = app.diary.planZone(entry.planId) ?: ZoneId.systemDefault()
        val time = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(entry.scheduledAt).atZone(zone))
        // The data URI makes this intent different from the launcher's, so Android delivers it
        // to the activity instead of only bringing the app to the front.
        val open = PendingIntent.getActivity(
            app, entryId.hashCode(),
            Intent(app, MainActivity::class.java)
                .setData(Uri.parse("otmetka://plan/" + entry.planId))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_PLAN_ID, entry.planId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val mark = PendingIntent.getBroadcast(
            app, entryId.hashCode(),
            Intent(app, ReminderReceiver::class.java).setAction(ACTION_MARK).putExtra(EXTRA_ENTRY_ID, entryId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            // The label is generic («Назначение 1»): nothing about health appears on the lock screen.
            .setContentTitle(entry.label)
            .setContentText(app.getString(R.string.reminder_text, time))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, app.getString(R.string.reminder_mark), mark)
            .build()
        @Suppress("MissingPermission") // checked above
        NotificationManagerCompat.from(app).notify(entryId.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_REMIND = "ru.otmetka.android.REMIND"
        const val ACTION_MARK = "ru.otmetka.android.MARK"
        const val EXTRA_ENTRY_ID = "entryId"
        private const val CHANNEL = "reminders"
    }
}
