package com.knowapp.android.ui.news

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.SessionStore
import com.knowapp.android.data.local.PhotoStore
import com.knowapp.android.data.model.PostCommentOut
import com.knowapp.android.data.model.PostOut
import com.knowapp.android.data.model.resolveMediaUrl
import com.knowapp.android.data.repository.PostsRepository
import com.knowapp.android.data.repository.ProfileRepository
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
    val commentsFor: PostOut? = null,
    val comments: List<PostCommentOut> = emptyList(),
    val commentsLoading: Boolean = false,
    val sendingComment: Boolean = false,
    val myAvatarUrl: String? = null,
)

class NewsViewModel(
    private val repository: PostsRepository,
    private val photos: PhotoStore,
    sessionStore: SessionStore,
    private val profile: ProfileRepository,
) : ViewModel() {
    val userId: Int? = sessionStore.session.value?.userId
    val canPost: Boolean = sessionStore.session.value?.role == "staff"
    val userName: String = sessionStore.session.value?.fullName ?: ""
    val canComment: Boolean = sessionStore.session.value?.role.let { it == "staff" || it == "institution_admin" }
    val isAdmin: Boolean = sessionStore.session.value?.role == "institution_admin"

    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state

    init {
        refresh()
        viewModelScope.launch {
            profile.me().onSuccess { _state.value = _state.value.copy(myAvatarUrl = resolveMediaUrl(it.avatarPath)) }
        }
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

    fun openComments(post: PostOut) {
        viewModelScope.launch {
            _state.value = _state.value.copy(commentsFor = post, comments = emptyList(), commentsLoading = true)
            repository.comments(post.id)
                .onSuccess { _state.value = _state.value.copy(comments = it, commentsLoading = false) }
                .onFailure { _state.value = _state.value.copy(commentsLoading = false, message = it.message) }
        }
    }

    fun closeComments() {
        _state.value = _state.value.copy(commentsFor = null, comments = emptyList())
    }

    private fun bumpCount(postId: Int, delta: Int) {
        _state.value = _state.value.copy(
            posts = _state.value.posts.map { if (it.id == postId) it.copy(commentCount = (it.commentCount + delta).coerceAtLeast(0)) else it },
        )
    }

    fun sendComment(text: String, onDone: (Boolean) -> Unit) {
        val post = _state.value.commentsFor ?: return
        if (text.isBlank()) return onDone(false)
        viewModelScope.launch {
            _state.value = _state.value.copy(sendingComment = true)
            repository.addComment(post.id, text.trim())
                .onSuccess {
                    _state.value = _state.value.copy(sendingComment = false, comments = _state.value.comments + it)
                    bumpCount(post.id, 1)
                    onDone(true)
                }
                .onFailure {
                    _state.value = _state.value.copy(sendingComment = false, message = it.message ?: "Couldn't post the comment.")
                    onDone(false)
                }
        }
    }

    fun deleteComment(comment: PostCommentOut) {
        viewModelScope.launch {
            repository.deleteComment(comment.id)
                .onSuccess {
                    _state.value = _state.value.copy(comments = _state.value.comments.filter { c -> c.id != comment.id })
                    bumpCount(comment.postId, -1)
                }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "Couldn't delete the comment.") }
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
