package com.knowapp.android

import android.content.Context
import com.knowapp.android.data.SessionStore
import com.knowapp.android.data.network.NetworkModule
import com.knowapp.android.data.repository.AuditRepository
import com.knowapp.android.data.repository.AuthRepository
import com.knowapp.android.data.repository.InstitutionsRepository
import com.knowapp.android.data.repository.MarketAnalysisRepository
import com.knowapp.android.data.repository.PostsRepository
import com.knowapp.android.data.repository.ProductCategoriesRepository
import com.knowapp.android.data.repository.StockRepository
import com.knowapp.android.data.repository.TransactionRepository
import com.knowapp.android.data.repository.UsersRepository

/** One instance built in KnowApplication.onCreate, threaded down to ViewModels. */
class AppContainer(context: Context) {
    val sessionStore = SessionStore(context.applicationContext)
    private val apiService = NetworkModule.buildApiService(sessionStore)

    val authRepository = AuthRepository(apiService, sessionStore)
    val transactionRepository = TransactionRepository(apiService)
    val stockRepository = StockRepository(apiService)
    val postsRepository = PostsRepository(apiService)
    val auditRepository = AuditRepository(apiService)
    val institutionsRepository = InstitutionsRepository(apiService)
    val usersRepository = UsersRepository(apiService)
    val marketAnalysisRepository = MarketAnalysisRepository(apiService)
    val productCategoriesRepository = ProductCategoriesRepository(apiService)
}
