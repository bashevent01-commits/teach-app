package com.knowapp.android.ui.institutions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.model.InstitutionOut
import com.knowapp.android.data.repository.InstitutionActionResult
import com.knowapp.android.data.repository.InstitutionsRepository
import com.knowapp.android.data.repository.InstitutionsResult
import kotlinx.coroutines.launch

data class InstitutionsUiState(
    val isLoading: Boolean = true,
    val institutions: List<InstitutionOut> = emptyList(),
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
)

class InstitutionsViewModel(private val repository: InstitutionsRepository) : ViewModel() {
    var uiState by mutableStateOf(InstitutionsUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.list()) {
                is InstitutionsResult.Success -> uiState = uiState.copy(isLoading = false, institutions = result.institutions)
                is InstitutionsResult.Failure -> uiState = uiState.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun create(name: String, type: String, address: String?, region: String?, onDone: (Boolean) -> Unit) {
        uiState = uiState.copy(isSubmitting = true, submitError = null)
        viewModelScope.launch {
            when (val result = repository.create(name, type, address, region)) {
                is InstitutionActionResult.Success -> {
                    uiState = uiState.copy(isSubmitting = false)
                    refresh()
                    onDone(true)
                }
                is InstitutionActionResult.Failure -> {
                    uiState = uiState.copy(isSubmitting = false, submitError = result.message)
                    onDone(false)
                }
            }
        }
    }
}
