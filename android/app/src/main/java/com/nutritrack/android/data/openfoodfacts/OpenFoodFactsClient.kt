package com.nutritrack.android.data.openfoodfacts

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit client for Open Food Facts.
 *
 * Deliberately separate from [com.nutritrack.android.data.api.ApiClient]: that one attaches your
 * NutriTrack JWT to every request, and it must never be sent to a third-party server.
 */
object OpenFoodFactsClient {

    private const val BASE_URL = "https://world.openfoodfacts.org/"

    // Open Food Facts asks every app to identify itself with a custom User-Agent.
    // TODO: replace the contact address with your own.
    private const val USER_AGENT = "NutriTrack/1.0 (Android; contact: you@example.com)"

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val api: OpenFoodFactsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenFoodFactsApi::class.java)
    }
}
