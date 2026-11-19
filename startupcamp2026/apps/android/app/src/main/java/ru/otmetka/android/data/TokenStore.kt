package ru.otmetka.android.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the tokens from POST /auth/login between app starts. The file is
 * private to the app. [ApiFactory] reads [accessToken] for every request.
 */
class TokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("tokens", Context.MODE_PRIVATE)

    private val _loggedIn = MutableStateFlow(prefs.contains(ACCESS))

    /** Turns false when the user logs out or the server no longer accepts the session. */
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    val accessToken: String?
        get() = prefs.getString(ACCESS, null)

    val refreshToken: String?
        get() = prefs.getString(REFRESH, null)

    /** Remembered so that the app opens offline without asking the server again. */
    var consentAccepted: Boolean
        get() = prefs.getBoolean(CONSENT, false)
        set(value) = prefs.edit { putBoolean(CONSENT, value) }

    fun save(accessToken: String, refreshToken: String) {
        prefs.edit {
            putString(ACCESS, accessToken)
            putString(REFRESH, refreshToken)
        }
        _loggedIn.value = true
    }

    fun clear() {
        prefs.edit { clear() }
        _loggedIn.value = false
    }

    private companion object {
        const val ACCESS = "access"
        const val REFRESH = "refresh"
        const val CONSENT = "consent"
    }
}
