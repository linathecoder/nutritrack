package com.nutritrack.android.di

import android.content.Context
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.local.AppDatabase
import com.nutritrack.android.data.repository.AuthRepository
import com.nutritrack.android.data.repository.LogRepository
import com.nutritrack.android.data.repository.ProfileRepository
import com.nutritrack.android.data.repository.WeightRepository
import com.nutritrack.android.data.session.AppPreferences
import com.nutritrack.android.data.session.SessionManager
import com.nutritrack.android.viewmodel.ViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(private val appContext: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sessionManager: SessionManager by lazy { SessionManager(appContext) }
    val appPreferences: AppPreferences by lazy { AppPreferences(appContext) }
    private val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val authRepository: AuthRepository by lazy {
        AuthRepository(ApiClient.api, sessionManager, database)
    }
    val profileRepository: ProfileRepository by lazy {
        ProfileRepository(ApiClient.api, sessionManager, database.userDao())
    }
    val logRepository: LogRepository by lazy {
        LogRepository(ApiClient.api, database.logEntryDao())
    }
    val weightRepository: WeightRepository by lazy {
        WeightRepository(ApiClient.api, database.weightDao())
    }

    val viewModelFactory: ViewModelFactory by lazy {
        ViewModelFactory(appContext, authRepository, profileRepository, logRepository, weightRepository, appPreferences)
    }
}
