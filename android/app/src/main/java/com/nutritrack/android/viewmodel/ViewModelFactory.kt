package com.nutritrack.android.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nutritrack.android.data.repository.AuthRepository
import com.nutritrack.android.data.repository.LogRepository
import com.nutritrack.android.data.repository.ProfileRepository
import com.nutritrack.android.data.repository.WeightRepository
import com.nutritrack.android.data.session.AppPreferences

class ViewModelFactory(
    private val appContext: Context,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val logRepository: LogRepository,
    private val weightRepository: WeightRepository,
    private val preferences: AppPreferences
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(authRepository)
        modelClass.isAssignableFrom(DiaryViewModel::class.java) -> DiaryViewModel(logRepository, profileRepository)
        modelClass.isAssignableFrom(MealViewModel::class.java) -> MealViewModel(appContext, logRepository)
        modelClass.isAssignableFrom(ProductViewModel::class.java) -> ProductViewModel(logRepository)
        modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(profileRepository, authRepository, preferences)
        modelClass.isAssignableFrom(WeightViewModel::class.java) -> WeightViewModel(weightRepository)
        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    } as T
}
