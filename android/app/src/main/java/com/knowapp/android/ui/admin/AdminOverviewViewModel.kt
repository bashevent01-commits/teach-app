package com.knowapp.android.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.model.AdminOverviewOut
import com.knowapp.android.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AdminOverviewUiState(
    val loading: Boolean = true,
    val data: AdminOverviewOut? = null,
    val error: String? = null,
)

class AdminOverviewViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _state = MutableStateFlow(AdminOverviewUiState())
    val state: StateFlow<AdminOverviewUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repository.overview()
                .onSuccess { _state.value = AdminOverviewUiState(loading = false, data = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }
}
