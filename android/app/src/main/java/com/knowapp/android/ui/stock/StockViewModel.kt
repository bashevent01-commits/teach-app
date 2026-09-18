package com.knowapp.android.ui.stock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.model.StockItemOut
import com.knowapp.android.data.repository.StockRepository
import com.knowapp.android.data.repository.StockResult
import kotlinx.coroutines.launch

data class StockUiState(
    val isLoading: Boolean = true,
    val items: List<StockItemOut> = emptyList(),
    val errorMessage: String? = null,
)

class StockViewModel(private val repository: StockRepository) : ViewModel() {
    var uiState by mutableStateOf(StockUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.list()) {
                is StockResult.Success -> uiState = uiState.copy(isLoading = false, items = result.items)
                is StockResult.Failure -> uiState = uiState.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }
}
