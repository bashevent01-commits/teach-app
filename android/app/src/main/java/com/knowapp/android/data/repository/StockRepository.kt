package com.knowapp.android.data.repository

import com.knowapp.android.data.model.StockItemOut
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class StockResult {
    data class Success(val items: List<StockItemOut>) : StockResult()
    data class Failure(val message: String) : StockResult()
}

class StockRepository(private val api: ApiService) {
    suspend fun list(): StockResult = withContext(Dispatchers.IO) {
        try {
            val response = api.listStockItems()
            if (response.isSuccessful) {
                StockResult.Success(response.body() ?: emptyList())
            } else {
                StockResult.Failure("Couldn't load stock items (code ${response.code()}).")
            }
        } catch (e: java.io.IOException) {
            StockResult.Failure("Couldn't reach the server. Check your connection and try again.")
        }
    }
}
