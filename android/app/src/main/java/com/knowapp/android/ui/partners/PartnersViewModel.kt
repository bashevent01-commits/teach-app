package com.knowapp.android.ui.partners

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.model.PartnerSummaryOut
import com.knowapp.android.data.repository.PartnersAdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PartnersUiState(
    val loading: Boolean = true,
    val partners: List<PartnerSummaryOut> = emptyList(),
    val message: String? = null,
)

class PartnersViewModel(private val repository: PartnersAdminRepository) : ViewModel() {
    private val _state = MutableStateFlow(PartnersUiState())
    val state: StateFlow<PartnersUiState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            repository.list()
                .onSuccess { _state.value = PartnersUiState(loading = false, partners = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, message = it.message) }
        }
    }

    private fun replace(updated: PartnerSummaryOut) {
        _state.value = _state.value.copy(partners = _state.value.partners.map { if (it.id == updated.id) updated else it })
    }

    private fun run(call: suspend () -> Result<PartnerSummaryOut>, done: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            call()
                .onSuccess { replace(it); _state.value = _state.value.copy(message = done); onResult(true) }
                .onFailure { _state.value = _state.value.copy(message = it.message); onResult(false) }
        }
    }

    fun create(fullName: String, username: String, email: String, phone: String, password: String, rate: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.create(fullName, username, email, phone, password, rate)
                .onSuccess { _state.value = _state.value.copy(partners = listOf(it) + _state.value.partners, message = "Partner added"); onResult(true) }
                .onFailure { _state.value = _state.value.copy(message = it.message); onResult(false) }
        }
    }

    fun setRate(id: Int, rate: String) = run({ repository.setRate(id, rate) }, "Rate saved for future fees")

    fun setActive(id: Int, active: Boolean) = run({ repository.setActive(id, active) }, if (active) "Partner reactivated" else "Partner switched off")

    fun newCode(id: Int) = run({ repository.newCode(id) }, "New partner code created")

    fun clearMessage() { _state.value = _state.value.copy(message = null) }
}
