package com.nutritrack.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nutritrack.android.navigation.AuthNavigator
import com.nutritrack.android.viewmodel.AuthViewModel
import com.nutritrack.android.viewmodel.appViewModels

class SplashActivity : AppCompatActivity() {

    private val authViewModel: AuthViewModel by appViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authViewModel.navigation.observe(this) { event ->
            event.getContentIfNotHandled()?.let { AuthNavigator.open(this, it.destination) }
        }
        authViewModel.routeOnLaunch()
    }
}
