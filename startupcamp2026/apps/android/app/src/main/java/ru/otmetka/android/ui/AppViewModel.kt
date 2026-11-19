package ru.otmetka.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.otmetka.android.OtmetkaApp
import ru.otmetka.android.R
import ru.otmetka.android.data.ApiException
import ru.otmetka.android.data.SessionRepository

/** Which screen is shown. Simple enough not to need a navigation library. */
sealed interface Screen {
    data object Login : Screen
    data object Consent : Screen
    data object Plans : Screen
    data class Day(val planId: String) : Screen
    data object BatteryGuide : Screen

    /** [formId] tells one opening of the form from the next, so each starts empty. */
    data class NewPlan(val formId: Long) : Screen
    data class PlanSettings(val planId: String) : Screen

    /** [draft]: the start of a question, when opened from a missed entry. */
    data class Questions(val planId: String, val draft: String? = null) : Screen
    data object Account : Screen
}

/** A message for the user: text from the server, or one of the app's own strings. */
data class UiMessage(val text: String? = null, val resource: Int = R.string.error_unknown)

fun Throwable.toUiMessage(): UiMessage {
    // The user sees a short message; the developer finds the cause in Logcat under "Otmetka".
    Log.w("Otmetka", "Request failed", this)
    return toMessage()
}

private fun Throwable.toMessage(): UiMessage = when (this) {
    is IOException -> UiMessage(resource = R.string.error_network)
    is ApiException -> UiMessage(text = detail)
    else -> UiMessage()
}

data class AppState(
    val screen: Screen,
    val busy: Boolean = false,
    val error: UiMessage? = null,
)

/** Login, consent and moving between screens. */
class AppViewModel(private val session: SessionRepository, private val onSessionReady: () -> Unit) : ViewModel() {

    private val _state = MutableStateFlow(AppState(startScreen()))
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        // The session can end at any moment: logout, or the server refusing the refresh token.
        viewModelScope.launch {
            session.loggedIn.collect { loggedIn ->
                if (!loggedIn) _state.value = AppState(Screen.Login)
            }
        }
        if (_state.value.screen == Screen.Plans) onSessionReady()
    }

    private fun startScreen(): Screen = when {
        !session.loggedIn.value -> Screen.Login
        !session.consentAccepted -> Screen.Consent
        else -> Screen.Plans
    }

    fun login(email: String, password: String, register: Boolean) = run {
        val consented = session.login(email.trim(), password, register)
        if (consented) ready() else _state.value = AppState(Screen.Consent)
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun acceptConsent() = run {
        session.acceptConsent()
        ready()
    }

    fun logout() = run { session.logout() }

    fun open(screen: Screen) = _state.update { it.copy(screen = screen, error = null) }

    /** @return false when there is nowhere to go back to and the app should close */
    fun back(): Boolean = when (val screen = _state.value.screen) {
        is Screen.Day, Screen.BatteryGuide, is Screen.NewPlan, Screen.Account -> {
            open(Screen.Plans)
            true
        }
        is Screen.PlanSettings -> {
            open(Screen.Day(screen.planId))
            true
        }
        is Screen.Questions -> {
            open(Screen.Day(screen.planId))
            true
        }
        Screen.Login, Screen.Consent, Screen.Plans -> false
    }

    private fun ready() {
        _state.value = AppState(Screen.Plans)
        onSessionReady()
    }

    /** Runs a request, showing progress and turning a failure into a message. */
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
                AppViewModel(app.session) { ru.otmetka.android.data.SyncWorker.runSoon(app) }
            }
        }
    }
}
