package com.knowapp.android.data.repository

import com.knowapp.android.data.local.PhotoStore
import com.knowapp.android.data.model.PostOut
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

sealed class PostsResult {
    data class Success(val posts: List<PostOut>) : PostsResult()
    data class Failure(val message: String) : PostsResult()
}

class PostsRepository(private val api: ApiService, private val photos: PhotoStore) {
    private val offline = "Couldn't reach the server. Check your connection and try again."

    suspend fun list(): PostsResult = withContext(Dispatchers.IO) {
        try {
            val response = api.listPosts()
            if (response.isSuccessful) {
                PostsResult.Success(response.body() ?: emptyList())
            } else {
                PostsResult.Failure(errorDetail(response.errorBody()?.string(), response.code()))
            }
        } catch (e: IOException) {
            PostsResult.Failure(offline)
        }
    }

    suspend fun create(title: String, body: String, imagePath: String?): Result<PostOut> = withContext(Dispatchers.IO) {
        try {
            val fields = mapOf(
                "title" to title.toRequestBody("text/plain".toMediaType()),
                "body" to body.toRequestBody("text/plain".toMediaType()),
            )
            val image = imagePath?.let { File(it) }?.takeIf { it.exists() }?.let {
                MultipartBody.Part.createFormData("image", it.name, it.asRequestBody("image/jpeg".toMediaType()))
            }
            val response = api.createPost(fields, image)
            photos.delete(imagePath)
            val post = response.body()
            if (response.isSuccessful && post != null) Result.success(post)
            else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception(offline))
        }
    }

    suspend fun delete(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deletePost(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception(offline))
        }
    }

    suspend fun report(id: Int, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.reportPost(id, mapOf("reason" to reason))
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception(offline))
        }
    }
}
