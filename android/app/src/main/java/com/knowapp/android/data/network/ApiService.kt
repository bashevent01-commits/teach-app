package com.knowapp.android.data.network

import com.knowapp.android.data.model.TokenResponse
import com.knowapp.android.data.model.TransactionOut
import retrofit2.Response
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
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
}
