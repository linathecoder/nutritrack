package com.nutritrack.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nutritrack.android.data.session.SessionManager
import com.nutritrack.android.navigation.AuthNavigator
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        lifecycleScope.launch {
            AuthNavigator.routeOnLaunch(this@SplashActivity, session)
        }
    }
}
