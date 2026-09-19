package com.knowapp.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.knowapp.android.AppContainer
import com.knowapp.android.ui.accounts.AccountsScreen
import com.knowapp.android.ui.accounts.AccountsViewModel
import com.knowapp.android.ui.audits.AuditDetailScreen
import com.knowapp.android.ui.audits.AuditDetailViewModel
import com.knowapp.android.ui.audits.AuditsScreen
import com.knowapp.android.ui.audits.AuditsViewModel
import com.knowapp.android.ui.home.HomeScreen
import com.knowapp.android.ui.home.HomeViewModel
import com.knowapp.android.ui.institutions.InstitutionsScreen
import com.knowapp.android.ui.institutions.InstitutionsViewModel
import com.knowapp.android.ui.login.LoginScreen
import com.knowapp.android.ui.login.LoginViewModel
import com.knowapp.android.ui.market.CategoryDetailScreen
import com.knowapp.android.ui.market.CategoryDetailViewModel
import com.knowapp.android.ui.market.MarketAnalysisScreen
import com.knowapp.android.ui.market.MarketAnalysisViewModel
import com.knowapp.android.ui.news.NewsScreen
import com.knowapp.android.ui.news.NewsViewModel
import com.knowapp.android.ui.stock.StockScreen
import com.knowapp.android.ui.stock.StockViewModel
import com.knowapp.android.ui.transactions.TransactionsScreen
import com.knowapp.android.ui.transactions.TransactionsViewModel

/** Simple factory-per-screen — matches the rest of the app's manual-DI approach. */
private fun <T> vmFactory(create: () -> T) = object : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : androidx.lifecycle.ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}

@Composable
fun KnowNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    val session by container.sessionStore.session.collectAsState()
    val startDestination = if (session != null) Routes.HOME else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            val viewModel: LoginViewModel = viewModel(factory = vmFactory { LoginViewModel(container.authRepository) })
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = vmFactory { HomeViewModel(container.authRepository) })
            HomeScreen(
                viewModel = viewModel,
                onViewTransactions = { navController.navigate(Routes.TRANSACTIONS) },
                onViewStock = { navController.navigate(Routes.STOCK) },
                onViewNews = { navController.navigate(Routes.NEWS) },
                onViewAudits = { navController.navigate(Routes.AUDITS) },
                onViewInstitutions = { navController.navigate(Routes.INSTITUTIONS) },
                onViewAccounts = { navController.navigate(Routes.ACCOUNTS) },
                onViewMarket = { navController.navigate(Routes.MARKET) },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.TRANSACTIONS) {
            val viewModel: TransactionsViewModel = viewModel(factory = vmFactory { TransactionsViewModel(container.transactionRepository) })
            TransactionsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.STOCK) {
            val viewModel: StockViewModel = viewModel(factory = vmFactory { StockViewModel(container.stockRepository) })
            StockScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.NEWS) {
            val viewModel: NewsViewModel = viewModel(factory = vmFactory { NewsViewModel(container.postsRepository) })
            NewsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.AUDITS) {
            val viewModel: AuditsViewModel = viewModel(factory = vmFactory { AuditsViewModel(container.auditRepository, container.sessionStore) })
            AuditsScreen(
                viewModel = viewModel,
                currentUserId = session?.userId,
                onBack = { navController.popBackStack() },
                onOpenAudit = { auditId -> navController.navigate(Routes.auditDetail(auditId)) },
            )
        }
        composable(
            Routes.AUDIT_DETAIL,
            arguments = listOf(navArgument("auditId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val auditId = backStackEntry.arguments?.getInt("auditId") ?: return@composable
            val viewModel: AuditDetailViewModel = viewModel(factory = vmFactory { AuditDetailViewModel(container.auditRepository, auditId) })
            AuditDetailScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.INSTITUTIONS) {
            val viewModel: InstitutionsViewModel = viewModel(factory = vmFactory { InstitutionsViewModel(container.institutionsRepository) })
            InstitutionsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.ACCOUNTS) {
            val viewModel: AccountsViewModel = viewModel(
                factory = vmFactory { AccountsViewModel(container.usersRepository, container.institutionsRepository, container.sessionStore) },
            )
            AccountsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.MARKET) {
            val viewModel: MarketAnalysisViewModel = viewModel(
                factory = vmFactory { MarketAnalysisViewModel(container.marketAnalysisRepository, container.productCategoriesRepository) },
            )
            MarketAnalysisScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenCategory = { categoryId -> navController.navigate(Routes.categoryDetail(categoryId)) },
            )
        }
        composable(
            Routes.CATEGORY_DETAIL,
            arguments = listOf(navArgument("categoryId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getInt("categoryId") ?: return@composable
            val viewModel: CategoryDetailViewModel = viewModel(factory = vmFactory { CategoryDetailViewModel(container.marketAnalysisRepository, categoryId) })
            CategoryDetailScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
