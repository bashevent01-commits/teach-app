package com.knowapp.android.data.network

import com.knowapp.android.data.model.AuditOut
import com.knowapp.android.data.model.CategoryDetailOut
import com.knowapp.android.data.model.CategoryInsightOut
import com.knowapp.android.data.model.InstitutionOut
import com.knowapp.android.data.model.PostOut
import com.knowapp.android.data.model.ProductCategoryOut
import com.knowapp.android.data.model.StockItemOut
import com.knowapp.android.data.model.TokenResponse
import com.knowapp.android.data.model.TransactionOut
import com.knowapp.android.data.model.UserOut
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    // /api/auth/login expects application/x-www-form-urlencoded
    // (FastAPI's OAuth2PasswordRequestForm), not JSON.
    @FormUrlEncoded
    @POST("/api/auth/login")
    suspend fun login(@FieldMap fields: Map<String, String>): Response<TokenResponse>

    @POST("/api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("/api/transactions")
    suspend fun listTransactions(
        @Query("institution_id") institutionId: Int? = null,
        @Query("category_type") categoryType: String? = null,
    ): Response<List<TransactionOut>>

    // Scoped server-side to the caller's institution automatically.
    @GET("/api/stock")
    suspend fun listStockItems(@Query("institution_id") institutionId: Int? = null): Response<List<StockItemOut>>

    @GET("/api/posts")
    suspend fun listPosts(@Query("institution_id") institutionId: Int? = null): Response<List<PostOut>>

    @GET("/api/audits")
    suspend fun listAudits(@Query("institution_id") institutionId: Int? = null): Response<List<AuditOut>>

    @POST("/api/audits")
    suspend fun createAudit(@Body body: Map<String, String?>): Response<AuditOut>

    @GET("/api/audits/{auditId}/transactions")
    suspend fun getAuditTransactions(@Path("auditId") auditId: Int): Response<List<TransactionOut>>

    @POST("/api/audits/{auditId}/finalize")
    suspend fun finalizeAudit(@Path("auditId") auditId: Int): Response<AuditOut>

    @GET("/api/institutions")
    suspend fun listInstitutions(): Response<List<InstitutionOut>>

    @POST("/api/institutions")
    suspend fun createInstitution(@Body body: Map<String, String?>): Response<InstitutionOut>

    @GET("/api/users")
    suspend fun listUsers(): Response<List<UserOut>>

    @POST("/api/users")
    suspend fun createUser(@Body body: Map<String, String?>): Response<UserOut>

    @PATCH("/api/users/{userId}/deactivate")
    suspend fun deactivateUser(@Path("userId") userId: Int): Response<UserOut>

    @PATCH("/api/users/{userId}/reactivate")
    suspend fun reactivateUser(@Path("userId") userId: Int): Response<UserOut>

    @GET("/api/product-categories")
    suspend fun listProductCategories(): Response<List<ProductCategoryOut>>

    @POST("/api/product-categories")
    suspend fun createProductCategory(@Body body: Map<String, String>): Response<ProductCategoryOut>

    @DELETE("/api/product-categories/{categoryId}")
    suspend fun deleteProductCategory(@Path("categoryId") categoryId: Int): Response<Unit>

    @GET("/api/market-analysis/categories")
    suspend fun listCategoryInsights(): Response<List<CategoryInsightOut>>

    @GET("/api/market-analysis/categories/{categoryId}")
    suspend fun getCategoryDetail(@Path("categoryId") categoryId: Int): Response<CategoryDetailOut>
}
