package com.knowapp.android.data.model

import com.google.gson.annotations.SerializedName

data class PartnerSummaryOut(
    val id: Int,
    val name: String,
    val username: String,
    val email: String?,
    val phone: String?,
    val code: String,
    @SerializedName("commission_rate") val commissionRate: String,
    @SerializedName("is_active") val isActive: Boolean,
    val businesses: Int,
    @SerializedName("earned_total") val earnedTotal: String,
    @SerializedName("paid_out") val paidOut: String,
    @SerializedName("balance_due") val balanceDue: String,
)
