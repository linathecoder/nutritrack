package com.nutritrack.android.data.repository

import com.nutritrack.android.data.Resource
import com.nutritrack.android.data.api.NutriTrackApi
import com.nutritrack.android.data.local.WeightDao
import com.nutritrack.android.data.local.WeightEntity
import com.nutritrack.android.data.model.WeightRequest
import com.nutritrack.android.data.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WeightRepository(
    private val api: NutriTrackApi,
    private val dao: WeightDao
) {
    val entries: Flow<List<WeightEntity>> = dao.observeAll()

    private val _targetWeightKg = MutableStateFlow<Double?>(null)
    val targetWeightKg: StateFlow<Double?> = _targetWeightKg

    suspend fun refresh(days: Int = HISTORY_DAYS): Resource<Unit> =
        when (val result = safeApiCall("Couldn't load your weight history") { api.getWeightHistory(days) }) {
            is Resource.Success -> {
                _targetWeightKg.value = result.data.targetWeightKg
                dao.replaceAll(result.data.entries.map { WeightEntity(it.date, it.weightKg) })
                Resource.Success(Unit)
            }
            is Resource.Error -> result
        }

    suspend fun add(weightKg: Double, date: String?): Resource<Unit> =
        store(safeApiCall("Couldn't save your weight", includeCode = false) {
            api.logWeight(WeightRequest(weightKg, date))
        })

    suspend fun update(date: String, weightKg: Double): Resource<Unit> =
        store(safeApiCall("Couldn't update this entry", includeCode = false) {
            api.updateWeight(date, WeightRequest(weightKg, date))
        })

    suspend fun delete(date: String): Resource<Unit> {
        val result = safeApiCall("Couldn't delete this entry", includeCode = false) { api.deleteWeight(date) }
        return when (result) {
            is Resource.Success -> {
                dao.deleteByDate(date)
                Resource.Success(Unit)
            }
            is Resource.Error -> result
        }
    }

    private suspend fun store(result: Resource<com.nutritrack.android.data.model.WeightEntryDto>): Resource<Unit> =
        when (result) {
            is Resource.Success -> {
                dao.upsert(WeightEntity(result.data.date, result.data.weightKg))
                Resource.Success(Unit)
            }
            is Resource.Error -> result
        }

    companion object {
        const val HISTORY_DAYS = 365
    }
}
