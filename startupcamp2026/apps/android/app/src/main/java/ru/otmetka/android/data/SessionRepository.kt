package ru.otmetka.android.data

import ru.otmetka.android.api.models.ConsentRequest
import ru.otmetka.android.api.models.Credentials
import ru.otmetka.android.api.models.Me
import ru.otmetka.android.api.models.PasswordConfirm
import ru.otmetka.android.api.models.RefreshRequest

/** Registration and login with an email and a password, and the consent that follows the first login. */
class SessionRepository(private val api: ApiFactory, private val tokenStore: TokenStore) {

    val loggedIn = tokenStore.loggedIn

    val consentAccepted: Boolean
        get() = tokenStore.consentAccepted

    /**
     * @param register create the account first; the server then logs it in at once
     * @return whether consent is already recorded for this account
     */
    suspend fun login(email: String, password: String, register: Boolean): Boolean {
        val credentials = Credentials(email, password)
        val tokens = (if (register) api.authApi.register(credentials) else api.authApi.login(credentials)).bodyOrThrow()
        tokenStore.save(tokens.accessToken, tokens.refreshToken)
        val me = api.authApi.getMe().bodyOrThrow()
        tokenStore.consentAccepted = me.consentAccepted
        return me.consentAccepted
    }

    suspend fun acceptConsent() {
        api.authApi.recordConsent(ConsentRequest(ConsentRequest.Kind.personal_data, CONSENT_TEXT_VERSION)).bodyOrThrow()
        tokenStore.consentAccepted = true
    }

    suspend fun me(): Me = api.authApi.getMe().bodyOrThrow()

    /** Deletes the account with all its data on the server; the session ends at once. */
    suspend fun deleteAccount(password: String) {
        api.authApi.deleteAccount(PasswordConfirm(password)).throwIfFailed()
        tokenStore.clear()
    }

    suspend fun logout() {
        val refresh = tokenStore.refreshToken
        if (refresh != null) {
            try {
                api.authApi.logout(RefreshRequest(refresh)).throwIfFailed()
            } catch (_: Exception) {
                // Leave the device even if the server is gone.
            }
        }
        tokenStore.clear()
    }

    suspend fun logoutAll() {
        try {
            api.authApi.logoutAll().throwIfFailed()
        } catch (_: Exception) {
            // This device still leaves; other devices stay only if the server was unreachable.
        }
        tokenStore.clear()
    }

    companion object {
        /** Version of the consent text in strings.xml. Change both together. */
        const val CONSENT_TEXT_VERSION = "2026-10"
    }
}
