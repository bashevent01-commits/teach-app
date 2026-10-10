package com.knowapp.android.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.model.ReportOut
import com.knowapp.android.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ReportsUiState(
    val loading: Boolean = true,
    val reports: List<ReportOut> = emptyList(),
    val showResolved: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class ReportsViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repository.reports(if (_state.value.showResolved) "resolved" else "open")
                .onSuccess { _state.value = _state.value.copy(loading = false, reports = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun showResolved(resolved: Boolean) {
        _state.value = _state.value.copy(showResolved = resolved)
        refresh()
    }

    fun resolve(report: ReportOut, action: String) {
        viewModelScope.launch {
            repository.resolve(report.id, action)
                .onSuccess {
                    _state.value = _state.value.copy(message = if (action == "remove_post") "Post removed." else "Report dismissed.")
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "Couldn't update the report.") }
        }
    }

    fun messageShown() {
        _state.value = _state.value.copy(message = null)
    }
}
