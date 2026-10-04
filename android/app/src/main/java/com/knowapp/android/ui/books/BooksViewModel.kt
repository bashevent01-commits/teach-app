package com.knowapp.android.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.model.UserOut
import com.knowapp.android.data.repository.BooksData
import com.knowapp.android.data.repository.BooksRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BooksUiState(
    val loading: Boolean = true,
    val staff: List<UserOut> = emptyList(),
    val selectedStaffId: Int? = null,
    val data: BooksData? = null,
    val error: String? = null,
)

class BooksViewModel(private val repository: BooksRepository) : ViewModel() {
    private val _state = MutableStateFlow(BooksUiState())
    val state: StateFlow<BooksUiState> = _state

    init {
        viewModelScope.launch {
            repository.staff().onSuccess { _state.value = _state.value.copy(staff = it) }
            load(null)
        }
    }

    fun select(staffId: Int?) {
        viewModelScope.launch { load(staffId) }
    }

    private suspend fun load(staffId: Int?) {
        _state.value = _state.value.copy(loading = true, selectedStaffId = staffId, error = null)
        repository.load(staffId)
            .onSuccess { _state.value = _state.value.copy(loading = false, data = it) }
            .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
    }
}
