package ru.otmetka.android.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

/** A failed request. [detail] is Russian text from the server, when it sent one. */
class ApiException(val status: Int, val detail: String?) : Exception(detail ?: "HTTP $status")

/** The body of a successful response, or an [ApiException] carrying the server's message. */
fun <T> Response<T>.bodyOrThrow(): T {
    throwIfFailed()
    @Suppress("UNCHECKED_CAST")
    return body() as T
}

/** For answers without a body (204): only checks that the request succeeded. */
fun Response<*>.throwIfFailed() {
    if (isSuccessful) return
    val detail = try {
        errorBody()?.string()?.let { Json.parseToJsonElement(it).jsonObject["detail"]?.jsonPrimitive?.content }
    } catch (e: Exception) {
        null
    }
    throw ApiException(code(), detail)
}
