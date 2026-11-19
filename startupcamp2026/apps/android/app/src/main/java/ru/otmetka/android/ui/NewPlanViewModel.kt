package ru.otmetka.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.otmetka.android.OtmetkaApp
import ru.otmetka.android.R
import ru.otmetka.android.data.DiaryRepository
import ru.otmetka.android.reminders.ReminderScheduler

data class NewPlanState(
    val title: String = "",
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val items: List<ItemDraft> = listOf(ItemDraft(LocalTime.of(8, 0))),
    val busy: Boolean = false,
    val error: UiMessage? = null,
    /** Set once the server has created the plan; the screen then opens its day view. */
    val createdPlanId: String? = null,
)

/** The form for a new plan. Creating needs a network. */
class NewPlanViewModel(private val diary: DiaryRepository, private val reminders: ReminderScheduler) : ViewModel() {

    /** The plan lives in the time zone of the phone it was created on. */
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _state = MutableStateFlow(LocalDate.now(zone).let { NewPlanState(startsOn = it, endsOn = it.plusDays(13)) })
    val state: StateFlow<NewPlanState> = _state.asStateFlow()

    fun setTitle(title: String) = _state.update { it.copy(title = title.take(MAX_TITLE)) }

    fun setStartsOn(date: LocalDate) =
        _state.update { it.copy(startsOn = date, endsOn = maxOf(it.endsOn, date)) }

    fun setEndsOn(date: LocalDate) = _state.update { it.copy(endsOn = date) }

    fun setItem(index: Int, item: ItemDraft) =
        _state.update { it.copy(items = it.items.toMutableList().apply { set(index, item) }) }

    fun addItem() = _state.update {
        if (it.items.size >= MAX_ITEMS) it else it.copy(items = it.items + ItemDraft(LocalTime.of(20, 0)))
    }

    fun removeLastItem() = _state.update { if (it.items.size > 1) it.copy(items = it.items.dropLast(1)) else it }

    fun create() {
        val form = _state.value
        val problem = when {
            form.endsOn < form.startsOn -> R.string.new_plan_error_dates
            form.items.any { it.days.isEmpty() } -> R.string.new_plan_error_days
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(error = UiMessage(resource = problem)) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val planId = diary.createPlan(
                    title = form.title.trim().ifEmpty { null },
                    startsOn = form.startsOn,
                    endsOn = form.endsOn,
                    zone = zone,
                    items = form.items.map { it.toRequest() },
                )
                reminders.reschedule()
                _state.update { it.copy(createdPlanId = planId) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.toUiMessage()) }
            }
        }
    }

    companion object {
        const val MAX_TITLE = 100
        const val MAX_ITEMS = 20

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as OtmetkaApp
                NewPlanViewModel(app.diary, app.reminders)
            }
        }
    }
}
