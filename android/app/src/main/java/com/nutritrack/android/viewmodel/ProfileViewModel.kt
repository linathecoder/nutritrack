package com.nutritrack.android.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.nutritrack.android.data.Event
import com.nutritrack.android.data.Resource
import com.nutritrack.android.data.local.UserEntity
import com.nutritrack.android.data.model.ProfileCalculationRequest
import com.nutritrack.android.data.model.ProfileCalculationResponse
import com.nutritrack.android.data.repository.AuthRepository
import com.nutritrack.android.data.repository.ProfileRepository
import com.nutritrack.android.data.session.AppPreferences
import kotlinx.coroutines.launch


class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val preferences: AppPreferences
) : ViewModel() {


    var weightUnit: String
        get() = preferences.weightUnit
        set(value) {
            preferences.weightUnit = value
        }

    val authenticatedUser: LiveData<UserEntity?> = authRepository.authenticatedUser.asLiveData()

    private val _profile = MutableLiveData<ProfileCalculationResponse?>()
    val profile: LiveData<ProfileCalculationResponse?> = _profile

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _message = MutableLiveData<Event<String>>()
    val message: LiveData<Event<String>> = _message

    private val _created = MutableLiveData<Event<ProfileCalculationResponse>>()
    val created: LiveData<Event<ProfileCalculationResponse>> = _created

    private val _updated = MutableLiveData<Event<Unit>>()
    val updated: LiveData<Event<Unit>> = _updated

    private val _reset = MutableLiveData<Event<Unit>>()
    val reset: LiveData<Event<Unit>> = _reset

    private val _loggedOut = MutableLiveData<Event<Unit>>()
    val loggedOut: LiveData<Event<Unit>> = _loggedOut

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    fun currentEmail(): String? = authRepository.currentEmail()

    fun loadProfile() {
        viewModelScope.launch {
            _loading.value = true
            when (val result = profileRepository.getProfile()) {
                is Resource.Success -> _profile.value = result.data
                is Resource.Error -> if (!result.isUnauthorized) _message.value = Event(result.message)
            }
            _loading.value = false
        }
    }


    fun createProfile(request: ProfileCalculationRequest) {
        viewModelScope.launch {
            _loading.value = true
            when (val result = profileRepository.createProfile(request)) {
                is Resource.Success -> _created.value = Event(result.data)
                is Resource.Error -> if (!result.isUnauthorized) _message.value = Event(result.message)
            }
            _loading.value = false
        }
    }


    fun updateProfile(request: ProfileCalculationRequest) {
        viewModelScope.launch {
            _loading.value = true
            when (val result = profileRepository.updateProfile(request)) {
                is Resource.Success -> {
                    _profile.value = result.data
                    _message.value = Event("Profile updated successfully")
                    _updated.value = Event(Unit)
                }
                is Resource.Error -> if (!result.isUnauthorized) _message.value = Event(result.message)
            }
            _loading.value = false
        }
    }


    fun resetProfile() {
        viewModelScope.launch {
            _loading.value = true
            when (val result = profileRepository.deleteProfile()) {
                is Resource.Success -> _reset.value = Event(Unit)
                is Resource.Error -> if (!result.isUnauthorized) _message.value = Event(result.message)
            }
            _loading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _loggedOut.value = Event(Unit)
        }
    }
}
