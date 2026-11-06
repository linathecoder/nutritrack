package com.nutritrack.android.data.session

interface TokenStore {
    fun getToken(): String?
    fun clearSession()
}
