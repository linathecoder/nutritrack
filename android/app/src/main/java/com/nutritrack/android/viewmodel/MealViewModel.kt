package com.nutritrack.android.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nutritrack.android.data.Event
import com.nutritrack.android.data.Resource
import com.nutritrack.android.data.food.FoodItem
import com.nutritrack.android.data.food.FoodRepository
import com.nutritrack.android.data.food.SavedIngredient
import com.nutritrack.android.data.food.ScannedMealItem
import com.nutritrack.android.data.model.LogFoodRequest
import com.nutritrack.android.data.openfoodfacts.ProductRepository
import com.nutritrack.android.data.openfoodfacts.ProductResult
import com.nutritrack.android.data.repository.LogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class SearchState {
    data object Loading : SearchState()
    data class Results(val foods: List<FoodItem>) : SearchState()
    data object Failed : SearchState()
}

data class RestoredIngredient(val food: FoodItem, val qty: Double, val unitIndex: Int?)

sealed class RestoreState {
    data class Done(val items: List<RestoredIngredient>) : RestoreState()
    data class Failed(val message: String) : RestoreState()
}


class MealViewModel(
    private val appContext: Context,
    private val logRepository: LogRepository
) : ViewModel() {

    private val _searchState = MutableLiveData<SearchState>()
    val searchState: LiveData<SearchState> = _searchState

    private val _restored = MutableLiveData<Event<RestoreState>>()
    val restored: LiveData<Event<RestoreState>> = _restored

    private val _saveState = MutableLiveData<SaveState>(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    private var searchJob: Job? = null

    fun search(query: String, immediate: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediate) delay(120)
            if (!FoodRepository.isLoaded) _searchState.value = SearchState.Loading
            try {
                FoodRepository.ensureLoaded(appContext)
                _searchState.value = SearchState.Results(FoodRepository.search(query))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _searchState.value = SearchState.Failed
            }
        }
    }

    fun tintFor(category: String): Int = FoodRepository.tintFor(category)


    fun restoreIngredients(raw: String?) {
        viewModelScope.launch {
            try {
                FoodRepository.ensureLoaded(appContext)
                val items = mutableListOf<RestoredIngredient>()
                val parsed = raw?.let {
                    runCatching {
                        val type = object : TypeToken<List<SavedIngredient>>() {}.type
                        Gson().fromJson<List<SavedIngredient>>(it, type)
                    }.getOrNull()
                }
                if (!parsed.isNullOrEmpty()) {
                    parsed.forEach { saved ->
                        var food = FoodRepository.findById(saved.foodId) ?: saved.food
                        if (food == null && saved.foodId.startsWith("scan-")) {
                            val barcode = saved.foodId.removePrefix("scan-")
                            food = when (val result = ProductRepository.lookup(barcode)) {
                                is ProductResult.Found -> ScannedMealItem.from(result.product, saved.qty).toFoodItem()
                                else -> null
                            }
                        }
                        if (food != null) items += RestoredIngredient(food, saved.qty, saved.unitIndex)
                    }
                } else if (!raw.isNullOrBlank()) {
                    val pattern = Regex("(.+?)\\s*\\(([0-9]+(?:[.,][0-9]+)?)\\s*g\\)")
                    pattern.findAll(raw).forEach { match ->
                        val label = match.groupValues[1].trim()
                        val grams = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return@forEach
                        val food = FoodRepository.search(label, limit = 10).firstOrNull() ?: return@forEach
                        items += RestoredIngredient(food, grams, null)
                    }
                }
                _restored.value = Event(RestoreState.Done(items))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _restored.value = Event(RestoreState.Failed(e.localizedMessage ?: "unknown error"))
            }
        }
    }


    fun saveMeal(request: LogFoodRequest, editingEntryId: String?) {
        if (_saveState.value == SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            val isUpdate = !editingEntryId.isNullOrBlank()
            val result = if (isUpdate) logRepository.updateEntry(editingEntryId!!, request)
            else logRepository.addEntry(request)
            _saveState.value = when (result) {
                is Resource.Success -> SaveState.Saved(isUpdate)
                is Resource.Error ->
                    if (result.isUnauthorized) SaveState.Idle else SaveState.Failed(result.message)
            }
        }
    }
}
