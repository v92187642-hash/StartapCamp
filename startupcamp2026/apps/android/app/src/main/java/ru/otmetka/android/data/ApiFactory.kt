package ru.otmetka.android.data

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ru.otmetka.android.api.apis.AuthApi
import ru.otmetka.android.api.apis.DiaryApi
import ru.otmetka.android.api.apis.PlansApi
import ru.otmetka.android.api.apis.QuestionsApi
import ru.otmetka.android.api.apis.ReportsApi
import ru.otmetka.android.api.apis.SharingApi
import ru.otmetka.android.api.infrastructure.ApiClient
import ru.otmetka.android.api.infrastructure.Serializer
import ru.otmetka.android.api.models.RefreshRequest
import ru.otmetka.android.api.models.Tokens

/**
 * The one place where networking is set up. Everything under
 * ru.otmetka.android.api is generated from api/openapi.yaml at build time.
 *
 * To call another group of endpoints, add a line like [plans].
 */
class ApiFactory(private val baseUrl: String, private val tokenStore: TokenStore) {

    private val json = Serializer.kotlinxSerializationJson

    /** Adds the access token, when there is one, to every request. */
    private val auth = Interceptor { chain ->
        val token = tokenStore.accessToken
        val request = if (token == null) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        }
        chain.proceed(request)
    }

    /** Used only for the refresh call, so that it cannot trigger itself. */
    private val plainClient = OkHttpClient()

    /**
     * Called by OkHttp when the server answers 401: the access token has
     * expired. Gets a new one with the refresh token and repeats the request
     * once. Returning null gives up and the 401 reaches the caller.
     */
    private val refresher = Authenticator { _, response ->
        val request = response.request
        if (skipRefreshOn401(request.url.encodedPath) || response.priorResponse != null) {
            return@Authenticator null
        }
        synchronized(this) {
            // Another request may have refreshed the token while this one waited.
            val sent = request.header("Authorization")?.removePrefix("Bearer ")
            val current = tokenStore.accessToken
            val fresh = if (current != null && current != sent) current else refresh()
            fresh?.let { request.newBuilder().header("Authorization", "Bearer $it").build() }
        }
    }

    private fun refresh(): String? {
        val refreshToken = tokenStore.refreshToken ?: return null
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
            .toRequestBody("application/json".toMediaType())
        val call = plainClient.newCall(Request.Builder().url(baseUrl + "auth/refresh").post(body).build())
        return try {
            call.execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val tokens = json.decodeFromString(Tokens.serializer(), response.body!!.string())
                        tokenStore.save(tokens.accessToken, tokens.refreshToken)
                        tokens.accessToken
                    }
                    // The session is over: the user has to log in again.
                    response.code == 401 -> {
                        tokenStore.clear()
                        null
                    }
                    else -> null
                }
            }
        } catch (e: java.io.IOException) {
            null
        }
    }

    /** Login and refresh must not trigger another refresh. logout-all still should. */
    private fun skipRefreshOn401(path: String): Boolean {
        val trimmed = path.trimEnd('/')
        return trimmed.endsWith("/auth/login") ||
            trimmed.endsWith("/auth/register") ||
            trimmed.endsWith("/auth/refresh") ||
            trimmed.endsWith("/auth/logout")
    }

    private val client = ApiClient(
        baseUrl = baseUrl,
        okHttpClientBuilder = OkHttpClient.Builder().addInterceptor(auth).authenticator(refresher),
    )

    val authApi: AuthApi by lazy { client.createService(AuthApi::class.java) }
    val plans: PlansApi by lazy { client.createService(PlansApi::class.java) }
    val diary: DiaryApi by lazy { client.createService(DiaryApi::class.java) }
    val sharing: SharingApi by lazy { client.createService(SharingApi::class.java) }
    val questions: QuestionsApi by lazy { client.createService(QuestionsApi::class.java) }
    val reports: ReportsApi by lazy { client.createService(ReportsApi::class.java) }
}
