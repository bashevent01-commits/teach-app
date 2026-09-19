package com.knowapp.android.data.model

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("user_id") val userId: Int,
    val role: String,
    @SerializedName("staff_type") val staffType: String? = null,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("institution_id") val institutionId: Int? = null,
)

data class ApiErrorBody(
    val detail: String? = null,
)

data class TransactionOut(
    val id: Int,
    @SerializedName("institution_id") val institutionId: Int,
    val type: String, // "income" | "expense"
    val method: String, // "cash" | "mpesa" | "bank"
    @SerializedName("category_type") val categoryType: String, // "STOCK" | "OTHER"
    val category: String,
    val description: String? = null,
    val amount: String, // Decimal serialized as string by FastAPI/Pydantic
    @SerializedName("stock_item_id") val stockItemId: Int? = null,
    val quantity: String? = null,
    @SerializedName("mpesa_code") val mpesaCode: String? = null,
    @SerializedName("mpesa_payer_name") val mpesaPayerName: String? = null,
    @SerializedName("transaction_date") val transactionDate: String,
    @SerializedName("image_path") val imagePath: String? = null,
    @SerializedName("recorded_by_id") val recordedById: Int,
    @SerializedName("created_at") val createdAt: String,
)

data class StockItemOut(
    val id: Int,
    @SerializedName("institution_id") val institutionId: Int,
    @SerializedName("category_id") val categoryId: Int? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    val name: String,
    val description: String? = null,
    @SerializedName("unit_price") val unitPrice: String? = null,
    val quantity: String,
    @SerializedName("created_at") val createdAt: String,
)

data class PostOut(
    val id: Int,
    @SerializedName("institution_id") val institutionId: Int,
    @SerializedName("author_id") val authorId: Int,
    val title: String,
    val body: String,
    @SerializedName("image_path") val imagePath: String? = null,
    @SerializedName("created_at") val createdAt: String,
)

/** Matches Api.posts.imageUrl / Api.institutions.logoUrl in frontend/js/api.js:
 * image_path is either already a full URL or a path relative to the API host. */
fun resolveMediaUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return if (path.startsWith("http://") || path.startsWith("https://")) path else "${com.knowapp.android.BuildConfig.API_BASE_URL}/$path"
}

data class AuditOut(
    val id: Int,
    @SerializedName("institution_id") val institutionId: Int,
    val title: String,
    @SerializedName("period_start") val periodStart: String,
    @SerializedName("period_end") val periodEnd: String,
    val summary: String? = null,
    val status: String, // "draft" | "finalized"
    @SerializedName("submitted_by_id") val submittedById: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("finalized_at") val finalizedAt: String? = null,
)

data class InstitutionOut(
    val id: Int,
    val name: String,
    val type: String,
    val address: String? = null,
    val region: String? = null,
    @SerializedName("logo_path") val logoPath: String? = null,
    @SerializedName("created_at") val createdAt: String,
)

data class UserOut(
    val id: Int,
    val username: String,
    @SerializedName("full_name") val fullName: String,
    val role: String, // "super_admin" | "institution_admin" | "staff"
    @SerializedName("staff_type") val staffType: String? = null,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("institution_id") val institutionId: Int? = null,
    @SerializedName("share_audits") val shareAudits: Boolean,
    @SerializedName("created_at") val createdAt: String,
)

data class CategoryInsightOut(
    @SerializedName("category_id") val categoryId: Int,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("institution_count") val institutionCount: Int,
    @SerializedName("average_price") val averagePrice: Double,
    @SerializedName("median_price") val medianPrice: Double,
    @SerializedName("min_price") val minPrice: Double,
    @SerializedName("max_price") val maxPrice: Double,
    @SerializedName("total_quantity_sold") val totalQuantitySold: String,
)

data class RegionBreakdownEntry(
    val region: String,
    @SerializedName("institution_count") val institutionCount: Int,
    @SerializedName("average_price") val averagePrice: Double,
    @SerializedName("median_price") val medianPrice: Double,
    @SerializedName("min_price") val minPrice: Double,
    @SerializedName("max_price") val maxPrice: Double,
)

data class TrendPointOut(
    val month: String,
    @SerializedName("average_price") val averagePrice: Double,
    @SerializedName("institution_count") val institutionCount: Int,
)

data class CategoryDetailOut(
    @SerializedName("category_id") val categoryId: Int,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("institution_count") val institutionCount: Int,
    @SerializedName("average_price") val averagePrice: Double,
    @SerializedName("median_price") val medianPrice: Double,
    @SerializedName("min_price") val minPrice: Double,
    @SerializedName("max_price") val maxPrice: Double,
    @SerializedName("total_quantity_sold") val totalQuantitySold: String,
    @SerializedName("regional_breakdown") val regionalBreakdown: List<RegionBreakdownEntry>,
    val trend: List<TrendPointOut>,
)

data class ProductCategoryOut(
    val id: Int,
    val name: String,
    @SerializedName("created_at") val createdAt: String,
)


