package com.knowapp.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.knowapp.android.AppContainer
import androidx.navigation.compose.composable
import com.knowapp.android.ui.home.HomeScreen
import com.knowapp.android.ui.home.HomeViewModel
import com.knowapp.android.ui.login.LoginScreen
import com.knowapp.android.ui.login.LoginViewModel
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
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.TRANSACTIONS) {
            val viewModel: TransactionsViewModel = viewModel(factory = vmFactory { TransactionsViewModel(container.transactionRepository) })
            TransactionsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
