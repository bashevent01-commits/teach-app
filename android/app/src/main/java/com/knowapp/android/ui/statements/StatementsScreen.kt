package com.knowapp.android.ui.statements

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.knowapp.android.ui.components.AppCard
import com.knowapp.android.ui.components.StatBox
import com.knowapp.android.ui.components.kes
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.ExpenseOrange
import com.knowapp.android.ui.theme.IncomeGreen
import com.knowapp.android.ui.theme.PillShape
import com.knowapp.android.ui.theme.SmallRadius
import com.knowapp.android.ui.theme.WarnAmber
import java.io.File

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementsScreen(viewModel: StatementsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val rows = state.visible

    fun printStatement() {
        val activity = context.findActivity() ?: return
        val file = File(context.cacheDir, "statement.pdf")
        StatementPdf.build(file, state.title, "Period: ${state.periodText}", rows, state.income, state.expense)
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(state.title, FilePrintAdapter(file, state.title), PrintAttributes.Builder().build())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statements", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    STATEMENT_METHODS.forEach { (value, label) ->
                        FilterChip(selected = state.method == value, onClick = { viewModel.selectMethod(value) }, label = { Text(label) })
                    }
                }
            }
            item {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatementRange.values().forEach { range ->
                        FilterChip(selected = state.range == range, onClick = { viewModel.selectRange(range) }, label = { Text(range.label) })
                    }
                }
            }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(state.title, style = MaterialTheme.typography.titleMedium)
                    Text("Period: ${state.periodText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBox(kes(state.income), "Money in", Modifier.weight(1f))
                        StatBox(kes(state.expense), "Money out", Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        StatBox(kes(state.income - state.expense), "Net", Modifier.weight(1f))
                    }
                }
            }
            if (state.loading) item { Text("Loading…") }
            else if (rows.isEmpty()) item { Text("No entries for this selection.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(rows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (row.pending) WarnAmber.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant, SmallRadius)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.category, fontWeight = FontWeight.SemiBold)
                        Text("${row.methodLabel} · ${row.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${if (row.type == "income") "+" else "-"}${kes(row.amount)}",
                        color = if (row.type == "income") IncomeGreen else ExpenseOrange,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item {
                Button(
                    onClick = { printStatement() },
                    enabled = !state.loading && rows.isNotEmpty(),
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) { Text("Print / Save PDF", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
