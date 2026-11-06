package com.nutritrack.android.navigation

import android.app.Activity
import android.content.Intent
import com.nutritrack.android.MainActivity
import com.nutritrack.android.OnboardingActivity
import com.nutritrack.android.ProfileSetupActivity
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.session.SessionManager
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

object AuthNavigator {

    private enum class Destination { HOME, PROFILE_SETUP, ONBOARDING }

    suspend fun routeOnLaunch(activity: Activity, session: SessionManager) {
        val destination = when {
            !session.isLoggedIn() -> Destination.ONBOARDING
            session.isProfileCompleted() -> Destination.HOME
            else -> resolveFromBackend(session)
        }
        open(activity, destination)
    }

    suspend fun routeAfterLogin(activity: Activity, session: SessionManager) {
        open(activity, resolveFromBackend(session))
    }

    fun routeAfterSignUp(activity: Activity, session: SessionManager) {
        session.setProfileNotCompleted()
        open(activity, Destination.PROFILE_SETUP)
    }

    private suspend fun resolveFromBackend(session: SessionManager): Destination {
        if (!session.isLoggedIn()) return Destination.ONBOARDING

        val fallback = if (session.isProfileCompleted()) Destination.HOME else Destination.PROFILE_SETUP

        return try {
            val response = ApiClient.api.getProfile()
            val body = response.body()
            when {
                response.isSuccessful && body != null -> {
                    session.saveProfileSummary(body.name, body.calories ?: 0)
                    Destination.HOME
                }
                response.code() == 401 -> {
                    session.clearSession()
                    Destination.ONBOARDING
                }
                response.code() == 404 -> {
                    val message = errorMessage(response.errorBody()?.string())
                    if (message?.startsWith("User not found") == true) {
                        session.clearSession()
                        Destination.ONBOARDING
                    } else {
                        session.setProfileNotCompleted()
                        Destination.PROFILE_SETUP
                    }
                }
                else -> fallback
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallback
        }
    }

    private fun open(activity: Activity, destination: Destination) {
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

    private fun errorMessage(json: String?): String? {
        if (json.isNullOrEmpty()) return null
        return try {
            JSONObject(json).optString("message", "").ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }
}
