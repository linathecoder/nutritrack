package com.nutritrack.android.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.nutritrack.android.data.Event
import com.nutritrack.android.data.Resource
import com.nutritrack.android.data.local.LogEntryEntity
import com.nutritrack.android.data.model.NutrientsDto
import com.nutritrack.android.data.model.ProfileCalculationResponse
import com.nutritrack.android.data.repository.LogRepository
import com.nutritrack.android.data.repository.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class DiaryViewModel(
    private val logRepository: LogRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {


    val entries: LiveData<List<LogEntryEntity>> = logRepository.entries.asLiveData()


    val totals: LiveData<NutrientsDto> = entries.map { list ->
        NutrientsDto(
            calories = list.sumOf { it.calories },
            proteinG = list.sumOf { it.proteinG },
            carbsG = list.sumOf { it.carbsG },
            fatG = list.sumOf { it.fatG }
        )
    }

    private val _profile = MutableLiveData<ProfileCalculationResponse?>()
    val profile: LiveData<ProfileCalculationResponse?> = _profile

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _message = MutableLiveData<Event<String>>()
    val message: LiveData<Event<String>> = _message

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val (profileResult, logResult) = coroutineScope {
                val profileCall = async { profileRepository.getProfile() }
                val logCall = async { logRepository.refreshToday() }
                profileCall.await() to logCall.await()
            }
            if (profileResult is Resource.Success) _profile.value = profileResult.data
            val failure = listOf(profileResult, logResult)
                .filterIsInstance<Resource.Error>()
                .firstOrNull { !it.isUnauthorized }
            if (failure != null) _error.value = failure.message
            _loading.value = false
        }
    }

    fun deleteMeal(id: String) {
        viewModelScope.launch {
            when (val result = logRepository.deleteEntry(id)) {
                is Resource.Success -> _message.value = Event("Meal deleted")
                is Resource.Error ->
                    if (!result.isUnauthorized) _message.value = Event(result.message)
            }
        }
    }
}
