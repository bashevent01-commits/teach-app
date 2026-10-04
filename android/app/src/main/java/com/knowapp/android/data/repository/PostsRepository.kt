package com.knowapp.android.data.repository

import com.knowapp.android.data.model.PostOut
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class PostsResult {
    data class Success(val posts: List<PostOut>) : PostsResult()
    data class Failure(val message: String) : PostsResult()
}

class PostsRepository(private val api: ApiService) {
    suspend fun list(): PostsResult = withContext(Dispatchers.IO) {
        try {
            val response = api.listPosts()
            if (response.isSuccessful) {
                PostsResult.Success(response.body() ?: emptyList())
            } else {
                PostsResult.Failure("Couldn't load news (code ${response.code()}).")
            }
        } catch (e: java.io.IOException) {
            PostsResult.Failure("Couldn't reach the server. Check your connection and try again.")
        }
    }
}
