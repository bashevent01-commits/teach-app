package com.knowapp.android.data.repository

import com.knowapp.android.data.model.ProductCategoryOut
import com.knowapp.android.data.model.StockItemOut
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import com.knowapp.android.data.DataVersion

sealed class StockResult {
    data class Success(val items: List<StockItemOut>) : StockResult()
    data class Failure(val message: String) : StockResult()
}

class StockRepository(private val api: ApiService) {
    private val offline = "Couldn't reach the server. Check your connection and try again."

    suspend fun list(): StockResult = withContext(Dispatchers.IO) {
        try {
            val response = api.listStockItems()
            if (response.isSuccessful) {
                StockResult.Success(response.body() ?: emptyList())
            } else {
                StockResult.Failure(errorDetail(response.errorBody()?.string(), response.code()))
            }
        } catch (e: IOException) {
            StockResult.Failure(offline)
        }
    }

    suspend fun categories(): List<ProductCategoryOut> = withContext(Dispatchers.IO) {
        try {
            api.listProductCategories().body() ?: emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }

    suspend fun createCategory(name: String): Result<ProductCategoryOut> = withContext(Dispatchers.IO) {
        try {
            val response = api.createProductCategory(mapOf("name" to name))
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body)
            else {
                val raw = response.errorBody()?.string()
                val existing = runCatching { com.google.gson.Gson().fromJson(raw, CategoryClash::class.java)?.existing }.getOrNull()
                if (response.code() == 409 && existing != null) Result.failure(CategoryExistsException(existing.name, errorDetail(raw, response.code())))
                else Result.failure(Exception(errorDetail(raw, response.code())))
            }
        } catch (e: IOException) {
            Result.failure(Exception(offline))
        }
    }

    private suspend fun createRaw(name: String, categoryId: Int, description: String?, unitPrice: Double?, quantity: Double, branchId: Int? = null, lowAt: Double? = null, unit: String = "pieces"): Result<StockItemOut> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf<String, Any?>("name" to name, "category_id" to categoryId, "description" to description, "unit_price" to unitPrice, "quantity" to quantity, "unit" to unit, "branch_id" to branchId, "low_stock_threshold" to lowAt)
            val response = api.createStockItem(body)
            val item = response.body()
            if (response.isSuccessful && item != null) Result.success(item)
            else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception(offline))
        }
    }

    private suspend fun updateRaw(id: Int, name: String, categoryId: Int, description: String?, unitPrice: Double?, branchId: Int? = null, lowAt: Double? = null, unit: String? = null): Result<StockItemOut> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf<String, Any?>("name" to name, "category_id" to categoryId, "description" to description, "unit_price" to unitPrice, "unit" to unit, "branch_id" to branchId, "low_stock_threshold" to lowAt)
            val response = api.updateStockItem(id, body)
            val item = response.body()
            if (response.isSuccessful && item != null) Result.success(item)
            else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception(offline))
        }
    }

    private suspend fun deleteRaw(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteStockItem(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
        } catch (e: IOException) {
            Result.failure(Exception(offline))
        }
    }

    suspend fun create(name: String, categoryId: Int, description: String?, unitPrice: Double?, quantity: Double, branchId: Int? = null, lowAt: Double? = null, unit: String = "pieces"): Result<StockItemOut> = createRaw(name, categoryId, description, unitPrice, quantity, branchId, lowAt, unit).also { if (it.isSuccess) DataVersion.bump() }

    suspend fun update(id: Int, name: String, categoryId: Int, description: String?, unitPrice: Double?, branchId: Int? = null, lowAt: Double? = null, unit: String? = null): Result<StockItemOut> = updateRaw(id, name, categoryId, description, unitPrice, branchId, lowAt, unit).also { if (it.isSuccess) DataVersion.bump() }

    suspend fun delete(id: Int): Result<Unit> = deleteRaw(id).also { if (it.isSuccess) DataVersion.bump() }
}


// What the server sends back when a new category means the same as one already in the shared list
private data class CategoryClash(val existing: ExistingCategory? = null)
private data class ExistingCategory(val id: Int, val name: String)

class CategoryExistsException(val existingName: String, message: String) : Exception(message)
