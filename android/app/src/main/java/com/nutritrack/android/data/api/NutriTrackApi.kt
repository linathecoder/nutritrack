package com.nutritrack.android.data.api

import com.nutritrack.android.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface NutriTrackApi {

    @POST("api/auth/signup")
    suspend fun signUp(@Body request: SignUpRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/profile")
    suspend fun saveProfile(@Body request: ProfileCalculationRequest): Response<ProfileCalculationResponse>

    @PUT("api/profile")
    suspend fun updateProfile(@Body request: ProfileCalculationRequest): Response<ProfileCalculationResponse>

    @DELETE("api/profile")
    suspend fun deleteProfile(): Response<Unit>

    @GET("api/profile/me")
    suspend fun getProfile(): Response<ProfileCalculationResponse>

    @GET("api/logs/today")
    suspend fun getTodayLog(): Response<DailyLogResponse>

    @POST("api/logs")
    suspend fun logFood(@Body request: LogFoodRequest): Response<LogEntryDto>

    @PUT("api/logs/{id}")
    suspend fun updateLog(@Path("id") id: String, @Body request: LogFoodRequest): Response<LogEntryDto>

    @DELETE("api/logs/{id}")
    suspend fun deleteLog(@Path("id") id: String): Response<Unit>

    @POST("api/weight")
    suspend fun logWeight(@Body request: WeightRequest): Response<WeightEntryDto>

    @PUT("api/weight/{date}")
    suspend fun updateWeight(@Path("date") date: String, @Body request: WeightRequest): Response<WeightEntryDto>

    @DELETE("api/weight/{date}")
    suspend fun deleteWeight(@Path("date") date: String): Response<Unit>

    @GET("api/weight")
    suspend fun getWeightHistory(@Query("days") days: Int): Response<WeightHistoryResponse>
}
