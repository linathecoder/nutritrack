package com.nutritrack.android.data

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException

/**
 * Result wrapper returned by every repository call so the ViewModels never deal with
 * raw Retrofit responses, HTTP codes or exceptions.
 */
sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()

    data class Error(val message: String, val code: Int? = null) : Resource<Nothing>() {
        /** 401s are handled globally by AuthInterceptor (session cleared + redirect to login). */
        val isUnauthorized: Boolean get() = code == 401
    }
}

/**
 * Encapsulates a Retrofit call:
 *  - network failures (IOException)            -> Resource.Error
 *  - HTTP 4xx / 5xx (response.isSuccessful)    -> Resource.Error with the server message when available
 *  - any other exception                       -> Resource.Error
 */
suspend fun <T> safeApiCall(
    fallbackMessage: String,
    includeCode: Boolean = true,
    call: suspend () -> Response<T>
): Resource<T> = try {
    val response = call()
    val body = response.body()
    if (response.isSuccessful && body != null) {
        Resource.Success(body)
    } else {
        val serverMessage = parseServerMessage(response.errorBody()?.string())
        val message = serverMessage
            ?: if (includeCode) "$fallbackMessage (error ${response.code()})" else fallbackMessage
        Resource.Error(message, response.code())
    }
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Resource.Error("Unable to reach the server. Check your connection and try again.")
} catch (e: Exception) {
    Resource.Error("$fallbackMessage: ${e.localizedMessage ?: "unexpected error"}")
}

fun parseServerMessage(json: String?): String? {
    if (json.isNullOrEmpty()) return null
    return try {
        JSONObject(json).optString("message", "").takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

/** One-shot event (toast, navigation...) that must not be replayed after a configuration change. */
class Event<out T>(private val content: T) {
    private var handled = false

    fun getContentIfNotHandled(): T? =
        if (handled) null else {
            handled = true
            content
        }
}
