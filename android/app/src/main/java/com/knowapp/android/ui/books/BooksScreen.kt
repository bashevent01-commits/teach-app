package com.knowapp.android.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.knowapp.android.ui.components.AppCard
import com.knowapp.android.ui.components.SimpleDropdown
import com.knowapp.android.ui.components.StatBox
import com.knowapp.android.ui.components.kes
import com.knowapp.android.ui.theme.DangerRed
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.ExpenseOrange
import com.knowapp.android.ui.theme.IncomeGreen
import com.knowapp.android.ui.theme.SmallRadius

private const val ALL_STAFF = "All staff (collective)"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(viewModel: BooksViewModel, onBack: () -> Unit, showBack: Boolean = true, onViewAudits: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val names = state.staff.associate { it.id to it.fullName }
    val options = listOf(ALL_STAFF) + state.staff.map { "${it.fullName} (${it.username})" }
    val selected = state.staff.firstOrNull { it.id == state.selectedStaffId }?.let { "${it.fullName} (${it.username})" } ?: ALL_STAFF

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Books", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold) },
                navigationIcon = { if (showBack) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } } },
                actions = { TextButton(onClick = onViewAudits) { Text("Audits") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SimpleDropdown("Show", options, selected, { choice ->
                    val id = state.staff.firstOrNull { "${it.fullName} (${it.username})" == choice }?.id
                    viewModel.select(id)
                })
            }
            state.error?.let { item { Text(it, color = DangerRed) } }
            if (state.loading && state.data == null) item { Text("Loading…") }
            state.data?.let { data ->
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox(kes(data.summary.income), "Income", Modifier.weight(1f))
                            StatBox(kes(data.summary.expenses), "Expense", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            StatBox(kes(data.summary.net), "Net", Modifier.weight(1f))
                            StatBox(kes(data.summary.totalMoney), "Held in total", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            data.summary.money.forEach { StatBox(kes(it.balance), it.name, Modifier.weight(1f)) }
                        }
                    }
                }
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Trial balance", style = MaterialTheme.typography.titleMedium)
                            Text(if (data.trialBalance.balanced) "Balanced" else "Out of balance", color = if (data.trialBalance.balanced) IncomeGreen else DangerRed, fontWeight = FontWeight.SemiBold)
                        }
                        data.trialBalance.accounts.forEach { a ->
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${a.code} ${a.name}", modifier = Modifier.weight(1f))
                                Text("Dr ${kes(a.debit)}  Cr ${kes(a.credit)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", fontWeight = FontWeight.Bold)
                            Text("Dr ${kes(data.trialBalance.totalDebit)}  Cr ${kes(data.trialBalance.totalCredit)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { Text("Records", style = MaterialTheme.typography.titleMedium) }
                if (data.transactions.isEmpty()) item { Text("No records for this selection.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(data.transactions.sortedByDescending { it.createdAt }) { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, SmallRadius).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                            Text(t.category, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${names[t.recordedById] ?: "—"} · ${t.method} · ${t.transactionDate.take(10)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${if (t.type == "income") "+" else "-"}${kes(t.amount)}",
                            color = if (t.type == "income") IncomeGreen else ExpenseOrange,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp)) }
        }
    }
}
