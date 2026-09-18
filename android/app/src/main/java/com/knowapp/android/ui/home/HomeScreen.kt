package com.knowapp.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onViewTransactions: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val session by viewModel.session.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("K.N.O.W.") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
        ) {
            Text(
                text = "Welcome, ${session?.fullName ?: ""}",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = roleLabel(session?.role, session?.staffType),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Transactions", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "View the income and expense ledger for your institution.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onViewTransactions, modifier = Modifier.fillMaxWidth()) {
                        Text("View transactions")
                    }
                }
            }

            Button(
                onClick = { scope.launch { viewModel.logout(); onLoggedOut() } },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Text("Sign out")
            }
        }
    }
}

private fun roleLabel(role: String?, staffType: String?): String = when (role) {
    "super_admin" -> "Super admin"
    "institution_admin" -> "Institution admin"
    "staff" -> if (staffType == "teacher") "Staff · Teacher" else "Staff"
    else -> role ?: ""
}
