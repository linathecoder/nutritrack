package com.nutritrack.android.data.repository

import com.nutritrack.android.data.Resource
import com.nutritrack.android.data.api.NutriTrackApi
import com.nutritrack.android.data.local.AppDatabase
import com.nutritrack.android.data.local.UserEntity
import com.nutritrack.android.data.model.AuthResponse
import com.nutritrack.android.data.model.LoginRequest
import com.nutritrack.android.data.model.SignUpRequest
import com.nutritrack.android.data.safeApiCall
import com.nutritrack.android.data.session.SessionManager
import kotlinx.coroutines.flow.Flow

enum class Destination { HOME, PROFILE_SETUP, ONBOARDING }


class AuthRepository(
    private val api: NutriTrackApi,
    private val session: SessionManager,
    private val db: AppDatabase
) {
    val authenticatedUser: Flow<UserEntity?> = db.userDao().observeUser()

    fun isLoggedIn(): Boolean = session.isLoggedIn()

    fun currentEmail(): String? = session.getEmail()

    suspend fun login(email: String, password: String): Resource<AuthResponse> {
        val result = safeApiCall("Invalid email or password", includeCode = false) {
            api.login(LoginRequest(email, password))
        }
        return persistSession(result, email, "Login failed: the server did not return a session token.")
    }

    suspend fun signUp(email: String, password: String): Resource<AuthResponse> {
        val result = safeApiCall("Sign up failed. Please try again.", includeCode = false) {
            api.signUp(SignUpRequest(email, password))
        }
        return persistSession(result, email, "Sign up failed: the server did not return a session token.")
    }

    suspend fun logout() = clearLocalData()

    /** Clears the SharedPreferences session and every table of the local database. */
    suspend fun clearLocalData() {
        session.clearSession()
        db.userDao().clear()
        db.logEntryDao().clear()
        db.weightDao().clear()
    }

    suspend fun routeOnLaunch(): Destination = when {
        !session.isLoggedIn() -> Destination.ONBOARDING
        session.isProfileCompleted() -> Destination.HOME
        else -> resolveFromBackend()
    }

    suspend fun routeAfterLogin(): Destination = resolveFromBackend()

    suspend fun routeAfterSignUp(): Destination {
        session.setProfileNotCompleted()
        db.userDao().setProfileCompleted(false)
        return Destination.PROFILE_SETUP
    }

    private suspend fun persistSession(
        result: Resource<AuthResponse>,
        email: String,
        missingTokenMessage: String
    ): Resource<AuthResponse> {
        if (result !is Resource.Success) return result
        val auth = result.data
        val token = auth.token
        if (token.isNullOrEmpty()) return Resource.Error(missingTokenMessage)

        val userId = auth.userId.orEmpty()
        // Start from a clean local state so no data from a previous account leaks through.
        db.logEntryDao().clear()
        db.weightDao().clear()
        db.userDao().clear()
        session.saveUser(userId, email, token)
        db.userDao().upsert(UserEntity(id = userId, email = email, token = token))
        return result
    }

    private suspend fun resolveFromBackend(): Destination {
        if (!session.isLoggedIn()) return Destination.ONBOARDING
        val fallback = if (session.isProfileCompleted()) Destination.HOME else Destination.PROFILE_SETUP

        return when (val result = safeApiCall("Couldn't load your profile") { api.getProfile() }) {
            is Resource.Success -> {
                val profile = result.data
                session.saveProfileSummary(profile.name, profile.calories ?: 0)
                db.userDao().updateProfileSummary(profile.name, profile.calories ?: 0)
                Destination.HOME
            }
            is Resource.Error -> when {
                result.code == 401 -> {
                    clearLocalData()
                    Destination.ONBOARDING
                }
                result.code == 404 -> {
                    if (result.message.startsWith("User not found")) {
                        clearLocalData()
                        Destination.ONBOARDING
                    } else {
                        session.setProfileNotCompleted()
                        db.userDao().setProfileCompleted(false)
                        Destination.PROFILE_SETUP
                    }
                }
                else -> fallback
            }
        }
    }
}
