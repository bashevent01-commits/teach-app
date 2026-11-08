package com.knowapp.android.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.model.PartnerDashboard
import com.knowapp.android.data.repository.PartnerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

data class AgentUiState(
    val loading: Boolean = true,
    val data: PartnerDashboard? = null,
    val message: String? = null,
)

class AgentViewModel(private val repository: PartnerRepository) : ViewModel() {
    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state

    init { load(null) }

    fun load(month: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            repository.dashboard(month)
                .onSuccess { _state.value = AgentUiState(loading = false, data = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, message = it.message) }
        }
    }

    fun shift(months: Long) {
        val data = _state.value.data ?: return
        val target = YearMonth.parse(data.month).plusMonths(months)
        if (target.isAfter(YearMonth.parse(data.currentMonth))) return
        load(target.toString())
    }

    // Asks for the commission of one confirmed payment, or everything not yet asked for when no notice is given
    fun requestPayout(noticeId: Int?) {
        viewModelScope.launch {
            repository.requestPayout(noticeId)
                .onSuccess { amount ->
                    _state.value = _state.value.copy(message = "Payout of KES $amount requested. You will be paid soon.")
                    load(_state.value.data?.month)
                }
                .onFailure { _state.value = _state.value.copy(message = it.message) }
        }
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }
}
