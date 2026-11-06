package com.nutritrack.android.data.api

import com.nutritrack.android.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface NutriTrackApi {

    @POST("api/auth/signup")
    suspend fun signUp(@Body request: SignUpRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/profile")
    suspend fun saveProfile(@Body request: ProfileCalculationRequest): Response<ProfileCalculationResponse>

    @GET("api/profile/me")
    suspend fun getProfile(): Response<ProfileCalculationResponse>

    @GET("api/logs/today")
    suspend fun getTodayLog(): Response<DailyLogResponse>
}
