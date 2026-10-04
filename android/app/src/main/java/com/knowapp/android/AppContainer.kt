package com.knowapp.android

import android.content.Context
import com.knowapp.android.data.SessionStore
import com.knowapp.android.data.ThemeStore
import com.knowapp.android.data.UpdateManager
import com.knowapp.android.data.local.OfflineStore
import com.knowapp.android.data.local.PhotoStore
import com.knowapp.android.data.network.NetworkMonitor
import com.knowapp.android.data.repository.BooksRepository
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
    val offlineStore = OfflineStore(context)
    val photoStore = PhotoStore(context)
    val networkMonitor = NetworkMonitor(context)
    val updateManager = UpdateManager(context)
    val themeStore = ThemeStore(context)
    val transactionRepository = TransactionRepository(apiService, offlineStore, sessionStore)
    val booksRepository = BooksRepository(apiService)
    val stockRepository = StockRepository(apiService)
    val postsRepository = PostsRepository(apiService, photoStore)
    val auditRepository = AuditRepository(apiService)
    val institutionsRepository = InstitutionsRepository(apiService)
    val usersRepository = UsersRepository(apiService)
    val marketAnalysisRepository = MarketAnalysisRepository(apiService)
    val productCategoriesRepository = ProductCategoriesRepository(apiService)
}
