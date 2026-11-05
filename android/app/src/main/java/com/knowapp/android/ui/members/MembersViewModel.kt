package com.knowapp.android.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.DataVersion
import com.knowapp.android.data.model.MemberOut
import com.knowapp.android.data.repository.MembersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class MembersUiState(
    val loading: Boolean = true,
    val members: List<MemberOut> = emptyList(),
    val filter: String = "all",
    val query: String = "",
    val message: String? = null,
)

class MembersViewModel(private val repository: MembersRepository) : ViewModel() {
    private val _state = MutableStateFlow(MembersUiState())
    val state: StateFlow<MembersUiState> = _state

    init {
        load()
        // A payment recorded elsewhere moves a due date, so the list is read again
        viewModelScope.launch { DataVersion.flow.drop(1).collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            repository.list()
                .onSuccess { _state.value = _state.value.copy(loading = false, members = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, message = it.message) }
        }
    }

    fun setFilter(filter: String) { _state.value = _state.value.copy(filter = filter) }

    fun setQuery(query: String) { _state.value = _state.value.copy(query = query) }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    fun save(id: Int?, fields: Map<String, Any?>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = if (id == null) repository.add(fields) else repository.update(id, fields)
            result.onSuccess { _state.value = _state.value.copy(message = if (id == null) "Member added" else "Member saved"); load(); onDone(true) }
                .onFailure { _state.value = _state.value.copy(message = it.message); onDone(false) }
        }
    }

    fun remove(id: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.remove(id)
                .onSuccess { _state.value = _state.value.copy(message = "Member removed"); load(); onDone() }
                .onFailure { _state.value = _state.value.copy(message = it.message) }
        }
    }
}
