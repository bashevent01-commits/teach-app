package com.knowapp.android.data.repository

import com.knowapp.android.data.local.PhotoStore
import com.knowapp.android.data.model.UserOut
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

class ProfileRepository(private val api: ApiService, private val photos: PhotoStore) {
    private val offline = "Couldn't reach the server. Check your connection and try again."

    suspend fun me(): Result<UserOut> = withContext(Dispatchers.IO) {
        try {
            val response = api.getMe()
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body)
            else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception(offline))
        }
    }

    suspend fun update(bio: String, avatarPath: String?, removeAvatar: Boolean): Result<UserOut> = withContext(Dispatchers.IO) {
        try {
            val fields = mapOf(
                "bio" to bio.toRequestBody("text/plain".toMediaType()),
                "remove_avatar" to removeAvatar.toString().toRequestBody("text/plain".toMediaType()),
            )
            val image = avatarPath?.let { File(it) }?.takeIf { it.exists() }?.let {
                MultipartBody.Part.createFormData("avatar", it.name, it.asRequestBody("image/jpeg".toMediaType()))
            }
            val response = api.updateProfile(fields, image)
            photos.delete(avatarPath)
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body)
            else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception(offline))
        }
    }
}
