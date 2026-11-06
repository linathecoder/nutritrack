package com.nutritrack.android.data.api

import com.nutritrack.android.data.session.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val onSessionExpired: () -> Unit
) : Interceptor {

    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.encodedPath.contains(AUTH_PATH)) {
            return chain.proceed(request)
        }

        val token = tokenStore.getToken()
        val authenticated = if (token.isNullOrEmpty()) {
            request
        } else {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        }

        val response = chain.proceed(authenticated)

        if (response.code == 401 && !token.isNullOrEmpty()) {
            val firstToExpire = synchronized(lock) {
                if (tokenStore.getToken() == token) {
                    tokenStore.clearSession()
                    true
                } else {
                    false
                }
            }
            if (firstToExpire) onSessionExpired()
        }

        return response
    }

    private companion object {
        const val AUTH_PATH = "/api/auth/"
    }
}
