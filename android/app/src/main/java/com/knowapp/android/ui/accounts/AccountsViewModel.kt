package com.knowapp.android.ui.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.SessionStore
import com.knowapp.android.data.model.InstitutionOut
import com.knowapp.android.data.model.UserOut
import com.knowapp.android.data.repository.InstitutionsRepository
import com.knowapp.android.data.repository.InstitutionsResult
import com.knowapp.android.data.repository.UserActionResult
import com.knowapp.android.data.repository.UsersRepository
import com.knowapp.android.data.repository.UsersResult
import kotlinx.coroutines.launch

data class AccountsUiState(
    val isLoading: Boolean = true,
    val users: List<UserOut> = emptyList(),
    val institutions: List<InstitutionOut> = emptyList(),
    val isSuperAdmin: Boolean = false,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
)

class AccountsViewModel(
    private val usersRepository: UsersRepository,
    private val institutionsRepository: InstitutionsRepository,
    sessionStore: SessionStore,
) : ViewModel() {
    var uiState by mutableStateOf(AccountsUiState(isSuperAdmin = sessionStore.session.value?.role == "super_admin"))
        private set

    init {
        refresh()
        if (uiState.isSuperAdmin) {
            viewModelScope.launch {
                when (val result = institutionsRepository.list()) {
                    is InstitutionsResult.Success -> uiState = uiState.copy(institutions = result.institutions)
                    is InstitutionsResult.Failure -> Unit // institution picker just stays empty
                }
            }
        }
    }

    fun refresh() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = usersRepository.list()) {
                is UsersResult.Success -> uiState = uiState.copy(isLoading = false, users = result.users)
                is UsersResult.Failure -> uiState = uiState.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun create(
        username: String,
        fullName: String,
        password: String,
        role: String,
        staffType: String?,
        institutionId: Int?,
        onDone: (Boolean) -> Unit,
    ) {
        uiState = uiState.copy(isSubmitting = true, submitError = null)
        viewModelScope.launch {
            when (val result = usersRepository.create(username, fullName, password, role, staffType, institutionId)) {
                is UserActionResult.Success -> {
                    uiState = uiState.copy(isSubmitting = false)
                    refresh()
                    onDone(true)
                }
                is UserActionResult.Failure -> {
                    uiState = uiState.copy(isSubmitting = false, submitError = result.message)
                    onDone(false)
                }
            }
        }
    }

    fun setActive(userId: Int, active: Boolean) {
        viewModelScope.launch {
            usersRepository.setActive(userId, active)
            refresh()
        }
    }
}
