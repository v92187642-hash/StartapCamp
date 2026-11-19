package ru.otmetka.android.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit
import ru.otmetka.android.OtmetkaApp

/**
 * Brings the phone and the server together: sends marks made offline, loads
 * fresh plans and entries, sets the reminders again. WorkManager runs it only
 * when there is a network and repeats it after a failure, also if the app was
 * closed in the meantime.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as OtmetkaApp
        if (!app.tokenStore.loggedIn.value) return Result.success()
        return try {
            if (!app.diary.pushPending()) return Result.retry()
            app.diary.refresh()
            app.reminders.reschedule()
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: ApiException) {
            // 401: the session is over and the login screen is already shown. Anything else: next run.
            Result.failure()
        }
    }

    companion object {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** As soon as there is a network. Called after every mark and when the app opens. */
        fun runSoon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build(),
            )
        }

        /** A few times a day, so reminders follow changes to the plan even if the app is not opened. */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sync-periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(online).build(),
            )
        }
    }
}
