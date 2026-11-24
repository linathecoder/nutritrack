package com.nutritrack.android.navigation

import android.app.Activity
import android.content.Intent
import com.nutritrack.android.MainActivity
import com.nutritrack.android.OnboardingActivity
import com.nutritrack.android.ProfileSetupActivity
import com.nutritrack.android.data.repository.Destination


object AuthNavigator {

    fun open(activity: Activity, destination: Destination) {
        val target = when (destination) {
            Destination.HOME -> MainActivity::class.java
            Destination.PROFILE_SETUP -> ProfileSetupActivity::class.java
            Destination.ONBOARDING -> OnboardingActivity::class.java
        }
        activity.startActivity(Intent(activity, target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        activity.finish()
    }
}
