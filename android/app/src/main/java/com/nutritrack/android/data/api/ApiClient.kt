package com.nutritrack.android.data.api

import com.nutritrack.android.data.session.TokenStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    var BASE_URL = "http://192.168.1.29:8080/"

    private var tokenStore: TokenStore? = null
    private var onSessionExpired: () -> Unit = {}

    fun init(tokenStore: TokenStore, onSessionExpired: () -> Unit) {
        this.tokenStore = tokenStore
        this.onSessionExpired = onSessionExpired
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("Authorization")
    }

    private val okHttpClient by lazy {
        val store = checkNotNull(tokenStore) {
            "ApiClient.init() must be called in Application.onCreate() before the first request"
        }
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store) { onSessionExpired() })
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val api: NutriTrackApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NutriTrackApi::class.java)
    }
}
