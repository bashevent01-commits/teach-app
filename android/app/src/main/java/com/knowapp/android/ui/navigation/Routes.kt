package com.knowapp.android.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val STOCK = "stock"
    const val NEWS = "news"
    const val AUDITS = "audits"
    const val AUDIT_DETAIL = "audit_detail/{auditId}"
    const val INSTITUTIONS = "institutions"
    const val ACCOUNTS = "accounts"
    const val MARKET = "market"
    const val CATEGORY_DETAIL = "category_detail/{categoryId}"

    fun auditDetail(auditId: Int) = "audit_detail/$auditId"
    fun categoryDetail(categoryId: Int) = "category_detail/$categoryId"
}
