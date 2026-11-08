package com.knowapp.android.data.model

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("user_id") val userId: Int,
    val role: String,
    @SerializedName("staff_type") val staffType: String? = null,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("institution_id") val institutionId: Int? = null,
    @SerializedName("institution_type") val institutionType: String? = null,
    val individual: Boolean = false,
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
    val unit: String? = null,
    @SerializedName("mpesa_code") val mpesaCode: String? = null,
    @SerializedName("mpesa_payer_name") val mpesaPayerName: String? = null,
    @SerializedName("transaction_date") val transactionDate: String,
    @SerializedName("image_path") val imagePath: String? = null,
    @SerializedName("batch_id") val batchId: String? = null,
    @SerializedName("recorded_by_id") val recordedById: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("member_id") val memberId: Int? = null,
    @SerializedName("member_name") val memberName: String? = null,
)

data class StockItemOut(
    val id: Int,
    @SerializedName("institution_id") val institutionId: Int,
    @SerializedName("category_id") val categoryId: Int? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("branch_id") val branchId: Int? = null,
    @SerializedName("branch_name") val branchName: String? = null,
    val name: String,
    val description: String? = null,
    @SerializedName("unit_price") val unitPrice: String? = null,
    val quantity: String,
    val unit: String = "pieces",
    @SerializedName("low_stock_threshold") val lowStockThreshold: String? = null,
    @SerializedName("created_at") val createdAt: String,
)

data class PostOut(
    val id: Int,
    @SerializedName("institution_id") val institutionId: Int,
    @SerializedName("author_id") val authorId: Int,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("author_avatar_path") val authorAvatarPath: String? = null,
    @SerializedName("author_bio") val authorBio: String? = null,
    @SerializedName("author_institution") val authorInstitution: String? = null,
    @SerializedName("comment_count") val commentCount: Int = 0,
    val title: String,
    val body: String,
    @SerializedName("image_path") val imagePath: String? = null,
    @SerializedName("created_at") val createdAt: String,
)

// Matches Api.posts.imageUrl / Api.institutions.logoUrl in frontend/js/api.js: image_path is either already a full URL or
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
    val constituency: String? = null,
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
    @SerializedName("institution_name") val institutionName: String? = null,
    @SerializedName("institution_type") val institutionType: String? = null,
    @SerializedName("last_active_at") val lastActiveAt: String? = null,
    @SerializedName("share_audits") val shareAudits: Boolean,
    @SerializedName("created_at") val createdAt: String,
    val bio: String? = null,
    @SerializedName("avatar_path") val avatarPath: String? = null,
    val email: String? = null,
    val county: String? = null,
    val constituency: String? = null,
    val individual: Boolean = false,
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
    val unit: String = "pieces",
    @SerializedName("quantity_sold_text") val quantitySoldText: String? = null,
)

data class RegionBreakdownEntry(
    val region: String,
    val county: String? = null,
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
    @SerializedName("constituency_breakdown") val constituencyBreakdown: List<RegionBreakdownEntry>? = null,
    val unit: String = "pieces",
    @SerializedName("quantity_sold_text") val quantitySoldText: String? = null,
)

data class ProductCategoryOut(
    val id: Int,
    val name: String,
    @SerializedName("created_at") val createdAt: String,
)




data class OpeningBalancesOut(
    val cash: String,
    val mpesa: String,
    val bank: String,
    @SerializedName("is_set") val isSet: Boolean,
)

data class MoneyPositionOut(val key: String, val name: String, val balance: String)

data class SummaryOut(
    val money: List<MoneyPositionOut>,
    @SerializedName("total_money") val totalMoney: String,
    val income: String,
    val expenses: String,
    val net: String,
)

data class AccountBalanceOut(
    val id: Int,
    val code: Int,
    val key: String,
    val name: String,
    val type: String,
    val debit: String,
    val credit: String,
    val balance: String,
)

data class TrialBalanceOut(
    val accounts: List<AccountBalanceOut>,
    @SerializedName("total_debit") val totalDebit: String,
    @SerializedName("total_credit") val totalCredit: String,
    val balanced: Boolean,
)

data class PendingTransaction(
    val localId: String,
    val type: String,
    val method: String,
    val categoryType: String,
    val category: String?,
    val description: String?,
    val amount: String,
    val stockItemId: Int?,
    val quantity: String?,
    val mpesaCode: String?,
    val mpesaPayerName: String?,
    val createdAt: String,
    val failed: Boolean = false,
    val error: String? = null,
    val imagePath: String? = null,
    val batchId: String? = null,
    val memberId: Int? = null,
)


data class PostCommentOut(
    val id: Int,
    @SerializedName("post_id") val postId: Int,
    @SerializedName("author_id") val authorId: Int,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("author_avatar_path") val authorAvatarPath: String? = null,
    val body: String,
    @SerializedName("created_at") val createdAt: String,
)


data class ReferenceCheckOut(
    val exists: Boolean,
    val mine: Boolean = false,
    val date: String? = null,
    val amount: String? = null,
)


data class DocumentOut(
    val id: Int,
    @SerializedName("doc_type") val docType: String,
    @SerializedName("doc_type_name") val docTypeName: String,
    @SerializedName("reference_no") val referenceNo: String? = null,
    @SerializedName("document_date") val documentDate: String,
    @SerializedName("party_name") val partyName: String? = null,
    @SerializedName("stock_item_id") val stockItemId: Int? = null,
    @SerializedName("stock_item_name") val stockItemName: String? = null,
    val quantity: String? = null,
    val amount: String? = null,
    val notes: String? = null,
    @SerializedName("image_path") val imagePath: String? = null,
    @SerializedName("transaction_id") val transactionId: Int? = null,
    @SerializedName("recorded_by_id") val recordedById: Int,
    @SerializedName("recorded_by_name") val recordedByName: String? = null,
    @SerializedName("created_at") val createdAt: String,
)


data class QuietInstitutionOut(
    val id: Int,
    val name: String,
    @SerializedName("last_activity_at") val lastActivityAt: String? = null,
)

data class ActivityItemOut(
    val id: Int,
    val action: String,
    @SerializedName("actor_username") val actorUsername: String? = null,
    val detail: String? = null,
    @SerializedName("target_type") val targetType: String? = null,
    @SerializedName("created_at") val createdAt: String,
)

data class AdminOverviewOut(
    val institutions: Int,
    @SerializedName("accounts_total") val accountsTotal: Int,
    @SerializedName("accounts_active") val accountsActive: Int,
    @SerializedName("accounts_inactive") val accountsInactive: Int,
    val staff: Int,
    @SerializedName("institution_admins") val institutionAdmins: Int,
    @SerializedName("super_admins") val superAdmins: Int,
    @SerializedName("new_accounts_30d") val newAccounts30d: Int,
    @SerializedName("open_reports") val openReports: Int,
    @SerializedName("entries_7d") val entries7d: Int,
    @SerializedName("quiet_institutions") val quietInstitutions: List<QuietInstitutionOut> = emptyList(),
    @SerializedName("recent_activity") val recentActivity: List<ActivityItemOut> = emptyList(),
)

data class InstitutionStatsOut(
    val id: Int,
    val name: String,
    val type: String,
    val region: String? = null,
    val address: String? = null,
    @SerializedName("logo_path") val logoPath: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("staff_count") val staffCount: Int = 0,
    @SerializedName("admin_count") val adminCount: Int = 0,
    @SerializedName("active_accounts") val activeAccounts: Int = 0,
    @SerializedName("inactive_accounts") val inactiveAccounts: Int = 0,
    @SerializedName("entries_30d") val entries30d: Int = 0,
    @SerializedName("last_activity_at") val lastActivityAt: String? = null,
)

data class ReportPostOut(
    val id: Int,
    @SerializedName("institution_name") val institutionName: String? = null,
    @SerializedName("author_name") val authorName: String? = null,
    val title: String,
    val body: String,
    @SerializedName("image_path") val imagePath: String? = null,
    @SerializedName("created_at") val createdAt: String,
)

data class ReportOut(
    val id: Int,
    @SerializedName("post_id") val postId: Int,
    val post: ReportPostOut? = null,
    @SerializedName("reporter_name") val reporterName: String? = null,
    val reason: String? = null,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("resolved_at") val resolvedAt: String? = null,
    val resolution: String? = null,
)


data class JournalLineOut(
    @SerializedName("account_id") val accountId: Int,
    @SerializedName("account_name") val accountName: String,
    val debit: String,
    val credit: String,
)

data class JournalEntryOut(
    val id: Int,
    @SerializedName("entry_date") val entryDate: String,
    val memo: String? = null,
    @SerializedName("transaction_id") val transactionId: Int? = null,
    @SerializedName("recorded_by_name") val recordedByName: String? = null,
    val reference: String? = null,
    val lines: List<JournalLineOut> = emptyList(),
)

data class LedgerLineOut(
    @SerializedName("entry_id") val entryId: Int,
    @SerializedName("entry_date") val entryDate: String,
    val memo: String? = null,
    @SerializedName("recorded_by_name") val recordedByName: String? = null,
    val reference: String? = null,
    val debit: String,
    val credit: String,
    @SerializedName("running_balance") val runningBalance: String,
)

data class LedgerOut(val account: AccountBalanceOut, val lines: List<LedgerLineOut> = emptyList())


data class MessageImportOut(
    val id: Int,
    val code: String,
    val kind: String,
    val direction: String? = null,
    val amount: String? = null,
    val counterparty: String? = null,
    @SerializedName("bank_hint") val bankHint: String? = null,
    @SerializedName("message_time") val messageTime: String? = null,
    val status: String,
    val reason: String? = null,
    @SerializedName("transaction_id") val transactionId: Int? = null,
)

data class ImportRequestBody(val messages: List<String>)

data class ImportResultOut(val items: List<MessageImportOut> = emptyList(), val skipped: Int = 0)

data class ApproveBody(
    @SerializedName("category_type") val categoryType: String,
    val category: String? = null,
    @SerializedName("stock_item_id") val stockItemId: Int? = null,
    val quantity: String? = null,
    val description: String? = null,
    val force: Boolean = false,
)


data class BranchOut(val id: Int, val name: String)

data class TransferOut(
    val id: Int,
    @SerializedName("item_name") val itemName: String,
    val quantity: String,
    val unit: String = "pieces",
    @SerializedName("from_branch_id") val fromBranchId: Int,
    @SerializedName("from_branch_name") val fromBranchName: String,
    @SerializedName("to_branch_id") val toBranchId: Int,
    @SerializedName("to_branch_name") val toBranchName: String,
    val status: String,
    val note: String? = null,
    val direction: String,
)

data class TransferBody(
    @SerializedName("item_id") val itemId: Int,
    val quantity: String,
    @SerializedName("to_branch_id") val toBranchId: Int,
    @SerializedName("from_branch_id") val fromBranchId: Int? = null,
    val note: String? = null,
)


data class ProfitTotals(
    val units: String,
    val revenue: String,
    val cost: String,
    val profit: String,
    val margin: String? = null,
    @SerializedName("unknown_units") val unknownUnits: String = "0",
)

data class ProfitItemOut(
    @SerializedName("item_id") val itemId: Int?,
    val name: String,
    val unit: String = "pieces",
    val units: String,
    val revenue: String,
    val cost: String,
    val profit: String,
    val margin: String? = null,
    @SerializedName("unknown_units") val unknownUnits: String = "0",
)

data class ProfitBranchOut(
    @SerializedName("branch_id") val branchId: Int?,
    @SerializedName("branch_name") val branchName: String,
    val totals: ProfitTotals,
    val items: List<ProfitItemOut> = emptyList(),
)

data class ProfitDayOut(val day: String, val totals: ProfitTotals, val branches: List<ProfitBranchOut> = emptyList())

data class CostItemOut(
    @SerializedName("item_id") val itemId: Int,
    val name: String,
    @SerializedName("branch_name") val branchName: String? = null,
    @SerializedName("unit_price") val unitPrice: String? = null,
    @SerializedName("avg_cost") val avgCost: String? = null,
    val quantity: String = "0",
    val unit: String = "pieces",
)

data class CostBody(@SerializedName("avg_cost") val avgCost: String)


data class PartnerProfile(val name: String, val code: String, @SerializedName("commission_rate") val commissionRate: String, val active: Boolean = true)

data class PartnerTotals(
    val businesses: Int = 0,
    @SerializedName("paying_this_month") val payingThisMonth: Int = 0,
    @SerializedName("fees_collected_month") val feesCollectedMonth: String = "0",
    @SerializedName("earned_month") val earnedMonth: String = "0",
    @SerializedName("earned_total") val earnedTotal: String = "0",
    @SerializedName("paid_out") val paidOut: String = "0",
    @SerializedName("balance_due") val balanceDue: String = "0",
    val requested: String = "0",
    val requestable: String = "0",
)

data class PartnerBusiness(
    val name: String,
    val type: String,
    val region: String? = null,
    @SerializedName("owner_name") val ownerName: String? = null,
    @SerializedName("registered_at") val registeredAt: String? = null,
    @SerializedName("monthly_fee") val monthlyFee: String? = null,
    val status: String,
    @SerializedName("last_paid_period") val lastPaidPeriod: String? = null,
    @SerializedName("earned_month") val earnedMonth: String = "0",
    @SerializedName("earned_total") val earnedTotal: String = "0",
)

data class PartnerCommission(val business: String, val period: String, @SerializedName("paid_on") val paidOn: String, val fee: String, val rate: String, val commission: String)

data class PartnerPayoutRow(@SerializedName("paid_on") val paidOn: String, val amount: String, val reference: String? = null)

data class PartnerSeries(val period: String, val earned: String)

data class PartnerDashboard(
    val profile: PartnerProfile,
    val month: String,
    @SerializedName("current_month") val currentMonth: String,
    val totals: PartnerTotals,
    val businesses: List<PartnerBusiness> = emptyList(),
    @SerializedName("recent_commissions") val recentCommissions: List<PartnerCommission> = emptyList(),
    val payouts: List<PartnerPayoutRow> = emptyList(),
    val series: List<PartnerSeries> = emptyList(),
    val notices: List<PartnerNoticeRow> = emptyList(),
    @SerializedName("payout_requests") val payoutRequests: List<PartnerRequestRow> = emptyList(),
)


data class BillingStatusOut(
    val applies: Boolean,
    val plan: String? = null,
    @SerializedName("plan_label") val planLabel: String = "",
    val state: String = "none",
    @SerializedName("trial_days") val trialDays: Int = 7,
    @SerializedName("trial_ends_at") val trialEndsAt: String? = null,
    @SerializedName("days_left") val daysLeft: Int = 0,
    @SerializedName("base_amount") val baseAmount: Int = 0,
    @SerializedName("included_accounts") val includedAccounts: Int = 0,
    @SerializedName("accounts_used") val accountsUsed: Int = 0,
    @SerializedName("extra_accounts") val extraAccounts: Int = 0,
    @SerializedName("extra_rate") val extraRate: Int = 200,
    @SerializedName("extra_amount") val extraAmount: Int = 0,
    val total: Int = 0,
    val note: String = "",
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("popup_from") val popupFrom: String? = null,
    @SerializedName("lock_at") val lockAt: String? = null,
    @SerializedName("popup_due") val popupDue: Boolean = false,
    val locked: Boolean = false,
    @SerializedName("can_pay") val canPay: Boolean = false,
    @SerializedName("proof_status") val proofStatus: String = "none",
    @SerializedName("proof_note") val proofNote: String? = null,
    val till: String = "4396353",
    @SerializedName("till_name") val tillName: String = "Obsidian Labs",
    @SerializedName("account_ref") val accountRef: String = "",
    @SerializedName("agent_registered") val agentRegistered: Boolean = false,
    @SerializedName("preview_allowed") val previewAllowed: Boolean = false,
    val tier: String = "paid",
    val premium: Boolean = true,
    @SerializedName("upgrade_price") val upgradePrice: Int = 200,
    val individual: Boolean = false,
    @SerializedName("institution_type") val institutionType: String? = null,
    @SerializedName("can_upgrade_business") val canUpgradeBusiness: Boolean = false,
)

data class PartnerNoticeRow(val id: Int, val text: String, val business: String? = null, val amount: String = "0")

data class PartnerRequestRow(val id: Int, val amount: String, val status: String)

data class PaymentProofRow(
    val id: Int,
    val business: String,
    val sender: String? = null,
    @SerializedName("sender_username") val senderUsername: String? = null,
    val message: String,
    val code: String,
    val amount: String,
    @SerializedName("amount_due") val amountDue: String,
    @SerializedName("paid_at") val paidAt: String? = null,
    val recipient: String? = null,
    @SerializedName("account_ref") val accountRef: String? = null,
    val status: String,
    val note: String? = null,
    val agent: String? = null,
    val months: Int = 1,
)

data class PayoutRequestRow(
    val id: Int,
    @SerializedName("partner_id") val partnerId: Int,
    val agent: String,
    val code: String,
    val phone: String? = null,
    val amount: String,
    val status: String,
    @SerializedName("balance_due") val balanceDue: String,
    @SerializedName("requested_at") val requestedAt: String? = null,
)


data class MemberOut(
    val id: Int,
    val name: String,
    val phone: String? = null,
    val plan: String? = null,
    val fee: String,
    val period: String,
    @SerializedName("next_due") val nextDue: String,
    @SerializedName("last_paid_on") val lastPaidOn: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
    val notes: String? = null,
    @SerializedName("branch_id") val branchId: Int? = null,
    val status: String,
    @SerializedName("days_to_due") val daysToDue: Int = 0,
)

data class DueMembersOut(
    val date: String,
    val overdue: List<MemberOut> = emptyList(),
    val today: List<MemberOut> = emptyList(),
    val tomorrow: List<MemberOut> = emptyList(),
)


data class UpgradeQuoteOut(
    val price: Int = 500,
    val credit: Int = 0,
    @SerializedName("due_now") val dueNow: Int = 500,
    val state: String = "",
    val pending: Boolean = false,
)
