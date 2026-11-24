package com.nutritrack.android.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutritrack.android.data.Resource
import com.nutritrack.android.data.model.LogFoodRequest
import com.nutritrack.android.data.openfoodfacts.ProductRepository
import com.nutritrack.android.data.openfoodfacts.ProductResult
import com.nutritrack.android.data.openfoodfacts.ScannedProduct
import com.nutritrack.android.data.repository.LogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed class ProductUiState {
    data object Loading : ProductUiState()
    data class Found(val product: ScannedProduct) : ProductUiState()
    data object NotFound : ProductUiState()
    data class Failure(val message: String) : ProductUiState()
}

class ProductViewModel(private val logRepository: LogRepository) : ViewModel() {

    private val _state = MutableLiveData<ProductUiState>()
    val state: LiveData<ProductUiState> = _state

    private val _saveState = MutableLiveData<SaveState>(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    private var loadJob: Job? = null

    fun load(barcode: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = ProductUiState.Loading
            _state.value = when (val result = ProductRepository.lookup(barcode)) {
                is ProductResult.Found -> ProductUiState.Found(result.product)
                ProductResult.NotFound -> ProductUiState.NotFound
                is ProductResult.Failure -> ProductUiState.Failure(result.message)
            }
        }
    }

    fun logProduct(request: LogFoodRequest) {
        if (_saveState.value == SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            _saveState.value = when (val result = logRepository.addEntry(request)) {
                is Resource.Success -> SaveState.Saved(updated = false)
                is Resource.Error ->
                    if (result.isUnauthorized) SaveState.Idle else SaveState.Failed(result.message)
            }
        }
    }
}
