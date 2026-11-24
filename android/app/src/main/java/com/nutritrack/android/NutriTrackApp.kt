package com.nutritrack.android

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.di.AppContainer
import kotlinx.coroutines.launch

class NutriTrackApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ApiClient.init(container.sessionManager) { onSessionExpired() }
    }

    private fun onSessionExpired() {
        container.appScope.launch { container.authRepository.clearLocalData() }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, getString(R.string.session_expired), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }
}
