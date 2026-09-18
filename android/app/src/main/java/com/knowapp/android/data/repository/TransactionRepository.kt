package com.knowapp.android.data.repository

import com.knowapp.android.data.model.TransactionOut
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class TransactionsResult {
    data class Success(val transactions: List<TransactionOut>) : TransactionsResult()
    data class Failure(val message: String) : TransactionsResult()
}

class TransactionRepository(private val api: ApiService) {
    suspend fun list(institutionId: Int? = null): TransactionsResult = withContext(Dispatchers.IO) {
        try {
            val response = api.listTransactions(institutionId = institutionId)
            if (response.isSuccessful) {
                TransactionsResult.Success(response.body() ?: emptyList())
            } else {
                TransactionsResult.Failure("Couldn't load transactions (code ${response.code()}).")
            }
        } catch (e: java.io.IOException) {
            TransactionsResult.Failure("Couldn't reach the server. Check your connection and try again.")
        }
    }
}
