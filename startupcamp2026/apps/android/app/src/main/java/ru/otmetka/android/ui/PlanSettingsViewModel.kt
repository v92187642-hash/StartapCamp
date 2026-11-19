package ru.otmetka.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.otmetka.android.OtmetkaApp
import ru.otmetka.android.R
import ru.otmetka.android.api.models.PlanItem
import ru.otmetka.android.api.models.Share
import ru.otmetka.android.data.DiaryRepository
import ru.otmetka.android.data.SharingRepository
import ru.otmetka.android.reminders.ReminderScheduler

/** What just worked, for the line under the title. */
sealed interface SettingsNotice {
    data class ItemAdded(val label: String) : SettingsNotice
    data class Invited(val email: String) : SettingsNotice
}

data class PlanSettingsState(
    val title: String = "",
    /** null until loaded. */
    val items: List<PlanItem>? = null,
    val shares: List<Share>? = null,
    val newItem: ItemDraft = ItemDraft(LocalTime.of(12, 0)),
    val email: String = "",
    val busy: Boolean = false,
    val notice: SettingsNotice? = null,
    val error: UiMessage? = null,
)

/** Owner only: the plan's items and who may watch the plan. Everything here needs a network. */
class PlanSettingsViewModel(
    private val planId: String,
    private val diary: DiaryRepository,
    private val sharing: SharingRepository,
    private val reminders: ReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanSettingsState())
    val state: StateFlow<PlanSettingsState> = _state.asStateFlow()

    fun load() = run(notice = null) {}

    fun setNewItem(item: ItemDraft) = _state.update { it.copy(newItem = item) }

    fun setEmail(email: String) = _state.update { it.copy(email = email) }

    fun addItem() {
        val item = _state.value.newItem
        if (item.days.isEmpty()) {
            _state.update { it.copy(error = UiMessage(resource = R.string.new_plan_error_days), notice = null) }
            return
        }
        run {
            val created = diary.addItem(planId, item.toRequest())
            reminders.reschedule()
            _state.update { it.copy(notice = SettingsNotice.ItemAdded(created.label)) }
        }
    }

    fun invite() {
        val email = _state.value.email.trim()
        if (email.isEmpty()) return
        run {
            sharing.invite(planId, email)
            _state.update { it.copy(email = "", notice = SettingsNotice.Invited(email)) }
        }
    }

    fun revoke(shareId: UUID) = run { sharing.revoke(shareId) }

    /** Runs a change and then reads the lists again, so the screen shows what the server has. */
    private fun run(notice: SettingsNotice? = null, action: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null, notice = notice) }
        viewModelScope.launch {
            try {
                action()
                val title = diary.planTitle(planId).orEmpty()
                val items = diary.planItems(planId)
                val shares = sharing.shares(planId)
                _state.update { it.copy(title = title, items = items, shares = shares, busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.toUiMessage()) }
            }
        }
    }

    companion object {
        fun factory(planId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as OtmetkaApp
                PlanSettingsViewModel(planId, app.diary, app.sharing, app.reminders)
            }
        }
    }
}
