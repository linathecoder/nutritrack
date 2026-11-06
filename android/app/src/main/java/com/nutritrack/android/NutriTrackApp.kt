package com.nutritrack.android

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.session.SessionManager

class NutriTrackApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ApiClient.init(SessionManager(this)) { onSessionExpired() }
    }

    private fun onSessionExpired() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, getString(R.string.session_expired), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }
}
