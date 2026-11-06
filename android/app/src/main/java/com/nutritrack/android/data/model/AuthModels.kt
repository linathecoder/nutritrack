package com.nutritrack.android.data.model

data class SignUpRequest(
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val userId: String?,
    val email: String?,
    val message: String?,
    val token: String?
)

