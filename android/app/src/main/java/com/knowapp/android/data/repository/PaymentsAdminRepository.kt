package com.knowapp.android.data.repository

import com.knowapp.android.data.model.PaymentProofRow
import com.knowapp.android.data.model.PayoutRequestRow
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

// What the super admin checks: payment messages from businesses and payout requests from agents
class PaymentsAdminRepository(private val api: ApiService) {
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

    suspend fun proofs(): Result<List<PaymentProofRow>> = call { api.paymentProofs("pending") }

    suspend fun approve(id: Int): Result<PaymentProofRow> = call { api.approveProof(id, emptyMap()) }

    suspend fun reject(id: Int, note: String?): Result<PaymentProofRow> = call { api.rejectProof(id, mapOf("note" to note)) }

    suspend fun requests(): Result<List<PayoutRequestRow>> = call { api.payoutRequests("pending") }

    suspend fun pay(id: Int, reference: String?): Result<PayoutRequestRow> = call { api.payPayoutRequest(id, mapOf("reference" to reference)) }

    suspend fun rejectRequest(id: Int, note: String?): Result<PayoutRequestRow> = call { api.rejectPayoutRequest(id, mapOf("note" to note)) }
}
