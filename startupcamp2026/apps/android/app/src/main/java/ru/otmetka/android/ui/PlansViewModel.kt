package ru.otmetka.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.otmetka.android.OtmetkaApp
import ru.otmetka.android.data.DiaryRepository
import ru.otmetka.android.data.db.PlanEntity
import ru.otmetka.android.reminders.ReminderScheduler

data class LoadStatus(val loading: Boolean = false, val offline: Boolean = false, val error: UiMessage? = null)

class PlansViewModel(private val diary: DiaryRepository, private val reminders: ReminderScheduler) : ViewModel() {

    /** Always from the phone's database; [refresh] updates the database. */
    val plans: StateFlow<List<PlanEntity>> =
        diary.plans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow(LoadStatus())
    val status: StateFlow<LoadStatus> = _status.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _status.value = LoadStatus(loading = true)
        viewModelScope.launch {
            _status.value = try {
                diary.pushPending()
                diary.refresh()
                reminders.reschedule()
                LoadStatus()
            } catch (e: IOException) {
                LoadStatus(offline = true)
            } catch (e: Exception) {
                LoadStatus(error = e.toUiMessage())
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as OtmetkaApp
                PlansViewModel(app.diary, app.reminders)
            }
        }
    }
}
