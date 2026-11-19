package ru.otmetka.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.otmetka.android.data.ApiFactory
import ru.otmetka.android.data.DiaryRepository
import ru.otmetka.android.data.QuestionsRepository
import ru.otmetka.android.data.SessionRepository
import ru.otmetka.android.data.SharingRepository
import ru.otmetka.android.data.SyncWorker
import ru.otmetka.android.data.TokenStore
import ru.otmetka.android.data.db.AppDatabase
import ru.otmetka.android.reminders.ReminderScheduler

/** Holds the objects that live as long as the app. Created once by Android. */
class OtmetkaApp : Application() {

    lateinit var tokenStore: TokenStore
        private set
    lateinit var session: SessionRepository
        private set
    lateinit var diary: DiaryRepository
        private set
    lateinit var sharing: SharingRepository
        private set
    lateinit var questions: QuestionsRepository
        private set
    lateinit var reminders: ReminderScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        val api = ApiFactory(BuildConfig.API_BASE_URL, tokenStore)
        session = SessionRepository(api, tokenStore)
        diary = DiaryRepository(api, AppDatabase.create(this).diaryDao())
        sharing = SharingRepository(api)
        questions = QuestionsRepository(api)
        reminders = ReminderScheduler(this, diary)
        SyncWorker.schedulePeriodic(this)

        // When the session ends (logout, or the server refused it), nothing of the
        // user's data stays on the phone and no reminder fires.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            tokenStore.loggedIn.collect { loggedIn ->
                if (!loggedIn) {
                    reminders.cancelAll()
                    diary.clear()
                }
            }
        }
    }
}
