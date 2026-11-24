package com.nutritrack.android.data.repository

import com.nutritrack.android.data.Resource
import com.nutritrack.android.data.api.NutriTrackApi
import com.nutritrack.android.data.local.UserDao
import com.nutritrack.android.data.model.ProfileCalculationRequest
import com.nutritrack.android.data.model.ProfileCalculationResponse
import com.nutritrack.android.data.safeApiCall
import com.nutritrack.android.data.session.SessionManager


class ProfileRepository(
    private val api: NutriTrackApi,
    private val session: SessionManager,
    private val userDao: UserDao
) {
    suspend fun getProfile(): Resource<ProfileCalculationResponse> =
        remember(safeApiCall("Couldn't load your profile") { api.getProfile() })

    suspend fun createProfile(request: ProfileCalculationRequest): Resource<ProfileCalculationResponse> =
        remember(safeApiCall("Failed to save profile. Please try again.", includeCode = false) { api.saveProfile(request) })

    suspend fun updateProfile(request: ProfileCalculationRequest): Resource<ProfileCalculationResponse> =
        remember(safeApiCall("Couldn't save changes. Please try again.", includeCode = false) { api.updateProfile(request) })

    suspend fun deleteProfile(): Resource<Unit> {
        val result = safeApiCall("Couldn't reset your profile") { api.deleteProfile() }
        if (result is Resource.Success) {
            session.setProfileNotCompleted()
            userDao.setProfileCompleted(false)
        }
        return result
    }

    private suspend fun remember(result: Resource<ProfileCalculationResponse>): Resource<ProfileCalculationResponse> {
        if (result is Resource.Success) {
            val profile = result.data
            session.saveProfileSummary(profile.name, profile.calories ?: 0)
            userDao.updateProfileSummary(profile.name, profile.calories ?: 0)
        }
        return result
    }
}
