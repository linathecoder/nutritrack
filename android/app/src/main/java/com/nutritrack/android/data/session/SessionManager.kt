package com.nutritrack.android.data.session

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) : TokenStore {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "nutritrack_session"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_PROFILE_COMPLETED = "profile_completed"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_DAILY_CALORIES = "daily_calories"
    }

    fun saveUser(userId: String, email: String, token: String) {
        prefs.edit()
            .clear()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    override fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun isLoggedIn(): Boolean = !getToken().isNullOrEmpty() && !getUserId().isNullOrEmpty()

    fun saveProfileSummary(name: String?, dailyCalories: Int) {
        prefs.edit()
            .putBoolean(KEY_PROFILE_COMPLETED, true)
            .putString(KEY_USER_NAME, name)
            .putInt(KEY_DAILY_CALORIES, dailyCalories)
            .apply()
    }

    fun setProfileNotCompleted() {
        prefs.edit()
            .putBoolean(KEY_PROFILE_COMPLETED, false)
            .apply()
    }

    fun isProfileCompleted(): Boolean = prefs.getBoolean(KEY_PROFILE_COMPLETED, false)

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun getDailyCalories(): Int = prefs.getInt(KEY_DAILY_CALORIES, 0)

    override fun clearSession() {
        prefs.edit().clear().apply()
    }
}
