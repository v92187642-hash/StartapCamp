package ru.otmetka.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.otmetka.android.OtmetkaApp
import ru.otmetka.android.data.SessionRepository

data class AccountState(
    /** null until loaded. */
    val email: String? = null,
    val premium: Boolean = false,
    val busy: Boolean = false,
    val error: UiMessage? = null,
)

/** The account: what it is, and the way to delete it with all its data. */
class AccountViewModel(private val session: SessionRepository) : ViewModel() {

    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    fun load() = run {
        val me = session.me()
        _state.update { it.copy(email = me.email, premium = me.premium) }
    }

    /** After this the session is over and the app shows the login screen by itself. */
    fun deleteAccount(password: String) = run { session.deleteAccount(password) }

    fun logoutAll() = run { session.logoutAll() }

    private fun run(action: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                action()
                _state.update { it.copy(busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.toUiMessage()) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as OtmetkaApp
                AccountViewModel(app.session)
            }
        }
    }
}
