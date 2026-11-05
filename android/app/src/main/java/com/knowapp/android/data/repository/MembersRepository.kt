package com.knowapp.android.data.repository

import com.knowapp.android.data.model.DueMembersOut
import com.knowapp.android.data.model.MemberOut
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

// A gym's members and who owes a subscription
class MembersRepository(private val api: ApiService) {
    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = withContext(Dispatchers.IO) {
        try {
            val response = block()
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body)
            else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception("Couldn't reach the server. Check your connection and try again."))
        }
    }

    suspend fun list(): Result<List<MemberOut>> = call { api.members() }

    suspend fun due(): Result<DueMembersOut> = call { api.dueMembers() }

    suspend fun add(fields: Map<String, Any?>): Result<MemberOut> = call { api.addMember(fields) }

    suspend fun update(id: Int, fields: Map<String, Any?>): Result<MemberOut> = call { api.updateMember(id, fields) }

    suspend fun remove(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteMember(id)
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception("Couldn't reach the server. Check your connection and try again."))
        }
    }
}
