package com.knowapp.android.data.repository

import com.knowapp.android.data.model.AdminOverviewOut
import com.knowapp.android.data.model.InstitutionStatsOut
import com.knowapp.android.data.model.ReportOut
import com.knowapp.android.data.model.UserOut
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

// Everything the super admin needs to oversee the platform, and the account actions both kinds of admin share
class AdminRepository(private val api: ApiService) {
    private val offline = "Couldn't reach the server. Check your connection and try again."

    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = withContext(Dispatchers.IO) {
        try {
            val response = block()
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body)
            else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception(offline))
        }
    }

    suspend fun overview(): Result<AdminOverviewOut> = call { api.adminOverview() }

    suspend fun institutions(): Result<List<InstitutionStatsOut>> = call { api.adminInstitutions() }

    suspend fun reports(status: String?): Result<List<ReportOut>> = call { api.listReports(status) }

    suspend fun resolve(reportId: Int, action: String): Result<Unit> = call { api.resolveReport(reportId, action) }.map { }

    suspend fun resetPassword(userId: Int, password: String): Result<UserOut> = call { api.resetPassword(userId, mapOf("new_password" to password)) }

    suspend fun rename(userId: Int, fullName: String): Result<UserOut> = call { api.updateUser(userId, mapOf("full_name" to fullName)) }
}
