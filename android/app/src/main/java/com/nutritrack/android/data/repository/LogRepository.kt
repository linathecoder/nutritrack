package com.nutritrack.android.data.repository

import com.nutritrack.android.data.Resource
import com.nutritrack.android.data.api.NutriTrackApi
import com.nutritrack.android.data.local.LogEntryDao
import com.nutritrack.android.data.local.LogEntryEntity
import com.nutritrack.android.data.local.toEntity
import com.nutritrack.android.data.model.LogEntryDto
import com.nutritrack.android.data.model.LogFoodRequest
import com.nutritrack.android.data.safeApiCall
import kotlinx.coroutines.flow.Flow


class LogRepository(
    private val api: NutriTrackApi,
    private val dao: LogEntryDao
) {
    val entries: Flow<List<LogEntryEntity>> = dao.observeAll()

    suspend fun refreshToday(): Resource<Unit> =
        when (val result = safeApiCall("Couldn't load today's meals") { api.getTodayLog() }) {
            is Resource.Success -> {
                dao.replaceAll(result.data.entries.orEmpty().mapNotNull { it.toEntity() })
                Resource.Success(Unit)
            }
            is Resource.Error -> result
        }


    suspend fun addEntry(request: LogFoodRequest): Resource<LogEntryDto> =
        cacheSaved(safeApiCall("Couldn't save this entry") { api.logFood(request) })


    suspend fun updateEntry(id: String, request: LogFoodRequest): Resource<LogEntryDto> =
        cacheSaved(safeApiCall("Couldn't save this meal") { api.updateLog(id, request) })


    suspend fun deleteEntry(id: String): Resource<Unit> {
        val result = safeApiCall("Couldn't delete meal") { api.deleteLog(id) }
        if (result is Resource.Success) dao.deleteById(id)
        return result
    }

    private suspend fun cacheSaved(result: Resource<LogEntryDto>): Resource<LogEntryDto> {
        if (result is Resource.Success) result.data.toEntity()?.let { dao.upsert(it) }
        return result
    }
}
