package com.knowapp.android.data.repository

import com.knowapp.android.data.model.CategoryDetailOut
import com.knowapp.android.data.model.CategoryInsightOut
import com.knowapp.android.data.model.ProductCategoryOut
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class CategoryInsightsResult {
    data class Success(val categories: List<CategoryInsightOut>) : CategoryInsightsResult()
    data class Failure(val message: String) : CategoryInsightsResult()
}

sealed class CategoryDetailResult {
    data class Success(val detail: CategoryDetailOut) : CategoryDetailResult()
    data class Failure(val message: String) : CategoryDetailResult()
}

sealed class ProductCategoriesResult {
    data class Success(val categories: List<ProductCategoryOut>) : ProductCategoriesResult()
    data class Failure(val message: String) : ProductCategoriesResult()
}

class MarketAnalysisRepository(private val api: ApiService) {
    suspend fun listCategoryInsights(): CategoryInsightsResult = withContext(Dispatchers.IO) {
        try {
            val response = api.listCategoryInsights()
            if (response.isSuccessful) CategoryInsightsResult.Success(response.body() ?: emptyList())
            else CategoryInsightsResult.Failure("Couldn't load market insights (code ${response.code()}).")
        } catch (e: java.io.IOException) {
            CategoryInsightsResult.Failure("Couldn't reach the server. Check your connection and try again.")
        }
    }

    suspend fun categoryDetail(categoryId: Int): CategoryDetailResult = withContext(Dispatchers.IO) {
        try {
            val response = api.getCategoryDetail(categoryId)
            if (response.isSuccessful && response.body() != null) CategoryDetailResult.Success(response.body()!!)
            else if (response.code() == 404) CategoryDetailResult.Failure("Not enough contributing institutions yet for this category to be shown.")
            else CategoryDetailResult.Failure("Couldn't load this category (code ${response.code()}).")
        } catch (e: java.io.IOException) {
            CategoryDetailResult.Failure("Couldn't reach the server. Check your connection and try again.")
        }
    }
}

class ProductCategoriesRepository(private val api: ApiService) {
    suspend fun list(): ProductCategoriesResult = withContext(Dispatchers.IO) {
        try {
            val response = api.listProductCategories()
            if (response.isSuccessful) ProductCategoriesResult.Success(response.body() ?: emptyList())
            else ProductCategoriesResult.Failure("Couldn't load categories (code ${response.code()}).")
        } catch (e: java.io.IOException) {
            ProductCategoriesResult.Failure("Couldn't reach the server. Check your connection and try again.")
        }
    }

    suspend fun create(name: String): ProductCategoriesResult = withContext(Dispatchers.IO) {
        try {
            val response = api.createProductCategory(mapOf("name" to name))
            if (response.isSuccessful) list() else ProductCategoriesResult.Failure(
                if (response.code() == 409) "A category with this name already exists." else "Couldn't add category (code ${response.code()}).",
            )
        } catch (e: java.io.IOException) {
            ProductCategoriesResult.Failure("Couldn't reach the server. Check your connection and try again.")
        }
    }

    suspend fun delete(categoryId: Int): ProductCategoriesResult = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteProductCategory(categoryId)
            if (response.isSuccessful) list() else ProductCategoriesResult.Failure(
                if (response.code() == 400) "This category is in use by stock items and can't be deleted." else "Couldn't delete category (code ${response.code()}).",
            )
        } catch (e: java.io.IOException) {
            ProductCategoriesResult.Failure("Couldn't reach the server. Check your connection and try again.")
        }
    }
}
