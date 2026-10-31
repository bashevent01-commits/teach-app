package com.knowapp.android.data.repository

import com.knowapp.android.data.model.PartnerSummaryOut
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

// The super admin's view of the agents who sell K.N.O.W.
class PartnersAdminRepository(private val api: ApiService) {
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

    suspend fun list(): Result<List<PartnerSummaryOut>> = call { api.adminPartners() }

    suspend fun create(fullName: String, username: String, email: String, phone: String, password: String, rate: String): Result<PartnerSummaryOut> {
        val body = mutableMapOf<String, Any>("full_name" to fullName, "username" to username, "email" to email, "password" to password, "commission_rate" to rate)
        if (phone.isNotBlank()) body["phone"] = phone
        return call { api.createPartner(body) }
    }

    suspend fun setRate(id: Int, rate: String): Result<PartnerSummaryOut> = call { api.updatePartner(id, mapOf("commission_rate" to rate)) }

    suspend fun setActive(id: Int, active: Boolean): Result<PartnerSummaryOut> = call { api.updatePartner(id, mapOf("is_active" to active)) }

    suspend fun newCode(id: Int): Result<PartnerSummaryOut> = call { api.newPartnerCode(id) }
}
