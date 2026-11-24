package com.nutritrack.android.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.nutritrack.android.data.Event
import com.nutritrack.android.data.Resource
import com.nutritrack.android.data.local.WeightEntity
import com.nutritrack.android.data.repository.WeightRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs


data class WeightStats(
    val entryCount: Int,
    val startKg: Double,
    val currentKg: Double,
    val changeKg: Double,
    val targetKg: Double?,
    val remainingKg: Double?,
    val weeklyRateKg: Double?,
    val progressPercent: Int?,
    val estimatedWeeksToGoal: Int?
)

class WeightViewModel(private val weightRepository: WeightRepository) : ViewModel() {

    val entries: LiveData<List<WeightEntity>> = weightRepository.entries.asLiveData()
    val targetKg: LiveData<Double?> = weightRepository.targetWeightKg.asLiveData()

    val stats: LiveData<WeightStats?> = MediatorLiveData<WeightStats?>().apply {
        fun recompute() {
            value = computeStats(entries.value.orEmpty(), targetKg.value)
        }
        addSource(entries) { recompute() }
        addSource(targetKg) { recompute() }
    }

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _message = MutableLiveData<Event<String>>()
    val message: LiveData<Event<String>> = _message


    private val _saved = MutableLiveData<Event<Unit>>()
    val saved: LiveData<Event<Unit>> = _saved

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val result = weightRepository.refresh()
            if (result is Resource.Error && !result.isUnauthorized) _message.value = Event(result.message)
            _loading.value = false
        }
    }


    fun add(weightKg: Double, date: String?) = execute("Weight saved") { weightRepository.add(weightKg, date) }


    fun update(date: String, weightKg: Double) = execute("Entry updated") { weightRepository.update(date, weightKg) }


    fun delete(date: String) = execute("Entry deleted") { weightRepository.delete(date) }

    private fun execute(successMessage: String, call: suspend () -> Resource<Unit>) {
        viewModelScope.launch {
            _loading.value = true
            when (val result = call()) {
                is Resource.Success -> {
                    _message.value = Event(successMessage)
                    _saved.value = Event(Unit)
                }
                is Resource.Error -> if (!result.isUnauthorized) _message.value = Event(result.message)
            }
            _loading.value = false
        }
    }

    private fun computeStats(list: List<WeightEntity>, target: Double?): WeightStats? {
        if (list.isEmpty()) return null
        val sorted = list.sortedBy { it.date }
        val first = sorted.first()
        val last = sorted.last()
        val change = last.weightKg - first.weightKg

        val weekly = runCatching {
            val days = ChronoUnit.DAYS.between(LocalDate.parse(first.date), LocalDate.parse(last.date))
            if (days >= 7) change / days * 7.0 else null
        }.getOrNull()

        val remaining = target?.let { last.weightKg - it }
        val progress = target?.let {
            val totalToGo = first.weightKg - it
            if (abs(totalToGo) < 0.05) 100
            else (((first.weightKg - last.weightKg) / totalToGo) * 100).toInt().coerceIn(0, 100)
        }
        val weeks = if (remaining != null && weekly != null && weekly != 0.0 &&
            (remaining > 0) == (weekly < 0) && abs(remaining) > 0.05
        ) (abs(remaining) / abs(weekly)).toInt().coerceAtLeast(1) else null

        return WeightStats(
            entryCount = sorted.size,
            startKg = first.weightKg,
            currentKg = last.weightKg,
            changeKg = change,
            targetKg = target,
            remainingKg = remaining,
            weeklyRateKg = weekly,
            progressPercent = progress,
            estimatedWeeksToGoal = weeks
        )
    }
}
