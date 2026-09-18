package com.knowapp.android

import android.content.Context
import com.knowapp.android.data.SessionStore
import com.knowapp.android.data.network.NetworkModule
import com.knowapp.android.data.repository.AuthRepository
import com.knowapp.android.data.repository.PostsRepository
import com.knowapp.android.data.repository.StockRepository
import com.knowapp.android.data.repository.TransactionRepository

/** One instance built in KnowApplication.onCreate, threaded down to ViewModels. */
class AppContainer(context: Context) {
    val sessionStore = SessionStore(context.applicationContext)
    private val apiService = NetworkModule.buildApiService(sessionStore)

    val authRepository = AuthRepository(apiService, sessionStore)
    val transactionRepository = TransactionRepository(apiService)
    val stockRepository = StockRepository(apiService)
    val postsRepository = PostsRepository(apiService)
}
