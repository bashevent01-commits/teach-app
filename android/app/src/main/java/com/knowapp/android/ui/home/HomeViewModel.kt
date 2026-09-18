package com.knowapp.android.ui.home

import androidx.lifecycle.ViewModel
import com.knowapp.android.data.Session
import com.knowapp.android.data.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(private val authRepository: AuthRepository) : ViewModel() {
    val session: StateFlow<Session?> = authRepository.session

    suspend fun logout() {
        authRepository.logout()
    }
}
