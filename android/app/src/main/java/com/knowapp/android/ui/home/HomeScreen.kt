package com.knowapp.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private data class MenuItem(val title: String, val description: String, val buttonLabel: String, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onViewTransactions: () -> Unit,
    onViewStock: () -> Unit,
    onViewNews: () -> Unit,
    onViewAudits: () -> Unit,
    onViewInstitutions: () -> Unit,
    onViewAccounts: () -> Unit,
    onViewMarket: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val session by viewModel.session.collectAsState()
    val scope = rememberCoroutineScope()

    val role = session?.role
    val isTeacher = role == "staff" && session?.staffType == "teacher"
    val isSuperAdmin = role == "super_admin"
    val isInstitutionAdmin = role == "institution_admin"

    // Institution-scoped features (super_admin has no institution_id).
    val menuItems = buildList {
        if (!isSuperAdmin) {
            add(MenuItem("Transactions", "View the income and expense ledger for your institution.", "View transactions", onViewTransactions))
            if (!isTeacher) add(MenuItem("Stock", "Browse items, unit prices, and current quantities.", "View stock", onViewStock))
            add(MenuItem("News", "Institution announcements and updates.", "View news", onViewNews))
            add(MenuItem("Audits", "Submit and review financial audits.", "View audits", onViewAudits))
        }
        if (isInstitutionAdmin || isSuperAdmin) {
            add(MenuItem("Accounts", "Manage staff and admin accounts.", "View accounts", onViewAccounts))
        }
        if (isSuperAdmin) {
            add(MenuItem("Institutions", "Onboard and browse institutions on the platform.", "View institutions", onViewInstitutions))
            add(MenuItem("Market", "Cross-institution pricing insights (5-institution minimum).", "View market insights", onViewMarket))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("K.N.O.W.", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            item {
                Column {
                    // Matches .greeting: display font, bold.
                    Text(
                        text = "Welcome, ${session?.fullName ?: ""}",
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Badge(text = roleLabel(role, session?.staffType), modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(menuItems) { menuItem ->
                HomeMenuCard(menuItem.title, menuItem.description, menuItem.buttonLabel, menuItem.onClick)
            }
            item {
                // Matches .ghost-btn: outlined pill, no fill.
                OutlinedButton(
                    onClick = { scope.launch { viewModel.logout(); onLoggedOut() } },
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    Text("Sign out", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun HomeMenuCard(
    title: String,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )
        Button(
            onClick = onClick,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(buttonLabel, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun roleLabel(role: String?, staffType: String?): String = when (role) {
    "super_admin" -> "Super admin"
    "institution_admin" -> "Institution admin"
    "staff" -> if (staffType == "teacher") "Staff · Teacher" else "Staff"
    else -> role ?: ""
}
