package com.knowapp.android.ui.news

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.SessionStore
import com.knowapp.android.data.local.PhotoStore
import com.knowapp.android.data.model.PostOut
import com.knowapp.android.data.repository.PostsRepository
import com.knowapp.android.data.repository.PostsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NewsUiState(
    val loading: Boolean = true,
    val posts: List<PostOut> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val posting: Boolean = false,
)

class NewsViewModel(
    private val repository: PostsRepository,
    private val photos: PhotoStore,
    sessionStore: SessionStore,
) : ViewModel() {
    val userId: Int? = sessionStore.session.value?.userId
    val canPost: Boolean = sessionStore.session.value?.role == "staff"

    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val result = repository.list()) {
                is PostsResult.Success -> _state.value = _state.value.copy(loading = false, posts = result.posts)
                is PostsResult.Failure -> _state.value = _state.value.copy(loading = false, error = result.message)
            }
        }
    }

    fun messageShown() {
        _state.value = _state.value.copy(message = null)
    }

    fun publish(title: String, body: String, photo: Uri?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (title.isBlank() || body.isBlank()) {
                _state.value = _state.value.copy(message = "Add a title and some text.")
                onDone(false)
                return@launch
            }
            _state.value = _state.value.copy(posting = true)
            val path = photo?.let { withContext(Dispatchers.IO) { photos.import(it) } }
            repository.create(title.trim(), body.trim(), path)
                .onSuccess {
                    _state.value = _state.value.copy(posting = false, message = "Posted.")
                    refresh()
                    onDone(true)
                }
                .onFailure {
                    _state.value = _state.value.copy(posting = false, message = it.message ?: "Couldn't post.")
                    onDone(false)
                }
        }
    }

    fun delete(post: PostOut) {
        viewModelScope.launch {
            repository.delete(post.id)
                .onSuccess {
                    _state.value = _state.value.copy(message = "Post deleted.")
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "Couldn't delete the post.") }
        }
    }

    fun report(post: PostOut, reason: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.report(post.id, reason.trim())
                .onSuccess {
                    _state.value = _state.value.copy(message = "Reported to the super admins for review.")
                    onDone(true)
                }
                .onFailure {
                    _state.value = _state.value.copy(message = it.message ?: "Couldn't send the report.")
                    onDone(false)
                }
        }
    }
}
