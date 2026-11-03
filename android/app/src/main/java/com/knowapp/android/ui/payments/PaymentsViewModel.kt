package com.knowapp.android.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.model.PaymentProofRow
import com.knowapp.android.data.model.PayoutRequestRow
import com.knowapp.android.data.repository.PaymentsAdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PaymentsUiState(
    val loading: Boolean = true,
    val proofs: List<PaymentProofRow> = emptyList(),
    val requests: List<PayoutRequestRow> = emptyList(),
    val message: String? = null,
)

class PaymentsViewModel(private val repository: PaymentsAdminRepository) : ViewModel() {
    private val _state = MutableStateFlow(PaymentsUiState())
    val state: StateFlow<PaymentsUiState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val proofs = repository.proofs()
            val requests = repository.requests()
            _state.value = PaymentsUiState(
                loading = false,
                proofs = proofs.getOrDefault(emptyList()),
                requests = requests.getOrDefault(emptyList()),
                message = (proofs.exceptionOrNull() ?: requests.exceptionOrNull())?.message,
            )
        }
    }

    private fun <T> finish(result: Result<T>, done: String) {
        result.onSuccess { _state.value = _state.value.copy(message = done); load() }
            .onFailure { _state.value = _state.value.copy(message = it.message) }
    }

    fun approve(id: Int) { viewModelScope.launch { finish(repository.approve(id), "Payment approved. The agent has been told.") } }

    fun reject(id: Int, note: String) { viewModelScope.launch { finish(repository.reject(id, note.ifBlank { null }), "Payment message declined") } }

    fun pay(id: Int, reference: String) { viewModelScope.launch { finish(repository.pay(id, reference.ifBlank { null }), "Payout recorded") } }

    fun rejectRequest(id: Int) { viewModelScope.launch { finish(repository.rejectRequest(id, null), "Payout request declined") } }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }
}
