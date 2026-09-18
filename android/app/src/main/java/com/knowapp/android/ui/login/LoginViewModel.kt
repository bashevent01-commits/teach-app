package com.knowapp.android.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.repository.AuthRepository
import com.knowapp.android.data.repository.LoginResult
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    var uiState by mutableStateOf(LoginUiState())
        private set

    fun onUsernameChange(value: String) {
        uiState = uiState.copy(username = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, errorMessage = null)
    }

    fun login(onSuccess: () -> Unit) {
        if (uiState.username.isBlank() || uiState.password.isBlank()) {
            uiState = uiState.copy(errorMessage = "Enter both username and password.")
            return
        }
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = authRepository.login(uiState.username.trim(), uiState.password)) {
                is LoginResult.Success -> {
                    uiState = uiState.copy(isLoading = false)
                    onSuccess()
                }
                is LoginResult.Failure -> {
                    uiState = uiState.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }
}
