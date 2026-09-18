package com.knowapp.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("user_id") val userId: Int,
    val role: String,
    @SerialName("staff_type") val staffType: String? = null,
    @SerialName("full_name") val fullName: String,
    @SerialName("institution_id") val institutionId: Int? = null,
)

@Serializable
data class ApiErrorBody(
    val detail: String? = null,
)

@Serializable
data class TransactionOut(
    val id: Int,
    @SerialName("institution_id") val institutionId: Int,
    val type: String, // "income" | "expense"
    val method: String, // "cash" | "mpesa" | "bank"
    @SerialName("category_type") val categoryType: String, // "STOCK" | "OTHER"
    val category: String,
    val description: String? = null,
    val amount: String, // Decimal serialized as string by FastAPI/Pydantic
    @SerialName("stock_item_id") val stockItemId: Int? = null,
    val quantity: String? = null,
    @SerialName("mpesa_code") val mpesaCode: String? = null,
    @SerialName("mpesa_payer_name") val mpesaPayerName: String? = null,
    @SerialName("transaction_date") val transactionDate: String,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("recorded_by_id") val recordedById: Int,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class StockItemOut(
    val id: Int,
    @SerialName("institution_id") val institutionId: Int,
    @SerialName("category_id") val categoryId: Int? = null,
    @SerialName("category_name") val categoryName: String? = null,
    val name: String,
    val description: String? = null,
    @SerialName("unit_price") val unitPrice: String? = null,
    val quantity: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PostOut(
    val id: Int,
    @SerialName("institution_id") val institutionId: Int,
    @SerialName("author_id") val authorId: Int,
    val title: String,
    val body: String,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** Matches Api.posts.imageUrl / Api.institutions.logoUrl in frontend/js/api.js:
 * image_path is either already a full URL or a path relative to the API host. */
fun resolveMediaUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return if (path.startsWith("http://") || path.startsWith("https://")) path else "${com.knowapp.android.BuildConfig.API_BASE_URL}/$path"
}

