package ru.otmetka.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.otmetka.android.OtmetkaApp
import ru.otmetka.android.R
import ru.otmetka.android.data.DiaryRepository
import ru.otmetka.android.data.SyncWorker
import ru.otmetka.android.data.db.EntryEntity
import ru.otmetka.android.data.db.PlanEntity
import ru.otmetka.android.domain.correctedMarkTime
import ru.otmetka.android.reminders.ReminderScheduler

data class DayState(
    val plan: PlanEntity,
    val date: LocalDate,
    val zone: ZoneId,
    val entries: List<EntryEntity>,
)

/** The day view of one plan. Marks are written to the phone first and sent in the background. */
@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(
    private val planId: String,
    private val diary: DiaryRepository,
    private val reminders: ReminderScheduler,
    private val syncSoon: () -> Unit,
) : ViewModel() {

    /** Days from today; 0 is today in the plan's time zone. */
    private val dayOffset = MutableStateFlow(0L)

    /** null until the plan is read from the database. */
    val state: StateFlow<DayState?> = combine(diary.plan(planId).filterNotNull(), dayOffset) { plan, offset ->
        val zone = ZoneId.of(plan.timezone)
        Triple(plan, LocalDate.now(zone).plusDays(offset), zone)
    }.flatMapLatest { (plan, date, zone) ->
        diary.day(planId, date, zone).map { entries -> DayState(plan, date, zone, entries) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    fun previousDay() = moveBy(-1)

    fun nextDay() = moveBy(1)

    private fun moveBy(days: Long) {
        _error.value = null
        dayOffset.value += days
        // Days far from today are not in the phone's copy yet; load them if there is a network.
        val current = state.value ?: return
        val date = LocalDate.now(current.zone).plusDays(dayOffset.value)
        viewModelScope.launch {
            try {
                diary.refreshDay(planId, date, current.zone)
            } catch (e: Exception) {
                // Offline: the screen shows what the phone has.
            }
        }
    }

    fun mark(entryId: String) = change { diary.mark(entryId, Instant.now()) }

    fun unmark(entryId: String) = change { diary.unmark(entryId) }

    /** A new time for a mark, on the day shown, in the plan's time zone. */
    fun correct(entryId: String, time: LocalTime) {
        val day = state.value ?: return
        val markedAt = correctedMarkTime(day.date, time, day.zone, Instant.now())
        if (markedAt == null) {
            _error.value = UiMessage(resource = R.string.entry_correct_future)
            return
        }
        change { diary.correct(entryId, markedAt) }
    }

    private fun change(action: suspend () -> Unit) {
        _error.value = null
        viewModelScope.launch {
            action()
            reminders.reschedule()
            syncSoon()
        }
    }

    companion object {
        fun factory(planId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as OtmetkaApp
                DayViewModel(planId, app.diary, app.reminders) { SyncWorker.runSoon(app) }
            }
        }
    }
}
