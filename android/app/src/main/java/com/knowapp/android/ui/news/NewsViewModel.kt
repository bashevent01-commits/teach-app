package com.knowapp.android.ui.news

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.model.PostOut
import com.knowapp.android.data.repository.PostsRepository
import com.knowapp.android.data.repository.PostsResult
import kotlinx.coroutines.launch

data class NewsUiState(
    val isLoading: Boolean = true,
    val posts: List<PostOut> = emptyList(),
    val errorMessage: String? = null,
)

class NewsViewModel(private val repository: PostsRepository) : ViewModel() {
    var uiState by mutableStateOf(NewsUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.list()) {
                is PostsResult.Success -> uiState = uiState.copy(isLoading = false, posts = result.posts)
                is PostsResult.Failure -> uiState = uiState.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }
}
