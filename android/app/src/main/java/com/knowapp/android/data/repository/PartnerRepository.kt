package com.knowapp.android.data.repository

import com.knowapp.android.data.model.PartnerDashboard
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class PartnerRepository(private val api: ApiService) {
    suspend fun dashboard(month: String?): Result<PartnerDashboard> = withContext(Dispatchers.IO) {
        try {
            val response = api.partnerDashboard(month)
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body)
            else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception("Couldn't reach the server. Check your connection and try again."))
        }
    }

    // With a notice id the amount is that payment's commission, otherwise everything not yet asked for
    suspend fun requestPayout(noticeId: Int?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.requestPayout(mapOf("notice_id" to noticeId))
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body["amount"] ?: "")
            else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception("Couldn't reach the server. Check your connection and try again."))
        }
    }
}
