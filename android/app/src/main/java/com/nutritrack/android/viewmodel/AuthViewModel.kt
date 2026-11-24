package com.nutritrack.android.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.nutritrack.android.data.Event
import com.nutritrack.android.data.Resource
import com.nutritrack.android.data.local.UserEntity
import com.nutritrack.android.data.repository.AuthRepository
import com.nutritrack.android.data.repository.Destination
import kotlinx.coroutines.launch

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

data class AuthNavigation(val destination: Destination, val message: String? = null)

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val authenticatedUser: LiveData<UserEntity?> = authRepository.authenticatedUser.asLiveData()

    private val _state = MutableLiveData<AuthUiState>(AuthUiState.Idle)
    val state: LiveData<AuthUiState> = _state

    private val _navigation = MutableLiveData<Event<AuthNavigation>>()
    val navigation: LiveData<Event<AuthNavigation>> = _navigation

    fun login(email: String, password: String) {
        if (_state.value == AuthUiState.Loading) return
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.login(email, password)) {
                is Resource.Success -> {
                    val destination = authRepository.routeAfterLogin()
                    _state.value = AuthUiState.Idle
                    _navigation.value = Event(AuthNavigation(destination, "Login successful!"))
                }
                is Resource.Error -> _state.value = AuthUiState.Error(result.message)
            }
        }
    }

    fun signUp(email: String, password: String) {
        if (_state.value == AuthUiState.Loading) return
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.signUp(email, password)) {
                is Resource.Success -> {
                    val destination = authRepository.routeAfterSignUp()
                    _state.value = AuthUiState.Idle
                    _navigation.value = Event(AuthNavigation(destination, "Account created successfully!"))
                }
                is Resource.Error -> _state.value = AuthUiState.Error(result.message)
            }
        }
    }

    fun routeOnLaunch() {
        viewModelScope.launch {
            _navigation.value = Event(AuthNavigation(authRepository.routeOnLaunch()))
        }
    }

    fun clearError() {
        if (_state.value is AuthUiState.Error) _state.value = AuthUiState.Idle
    }
}
