package com.knowapp.android.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.knowapp.android.ui.components.AppCard
import com.knowapp.android.ui.components.Badge
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.PillShape
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
            TopAppBar(
                title = { Text("K.N.O.W.", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
        ) {
            // Matches .greeting: display font, bold.
            Text(
                text = "Welcome, ${session?.fullName ?: ""}",
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )
            Badge(
                text = roleLabel(session?.role, session?.staffType),
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("Transactions", style = MaterialTheme.typography.titleMedium)
                Text(
                    "View the income and expense ledger for your institution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                )
                Button(
                    onClick = onViewTransactions,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View transactions", fontWeight = FontWeight.SemiBold)
                }
            }

            // Matches .ghost-btn: outlined pill, no fill.
            OutlinedButton(
                onClick = { scope.launch { viewModel.logout(); onLoggedOut() } },
                shape = PillShape,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Text("Sign out", fontWeight = FontWeight.SemiBold)
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
