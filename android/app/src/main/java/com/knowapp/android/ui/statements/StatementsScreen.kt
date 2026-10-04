package com.knowapp.android.ui.statements

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowapp.android.ui.components.HeroCard
import com.knowapp.android.ui.components.kes
import com.knowapp.android.ui.components.kesNumber
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.ExpenseOrange
import com.knowapp.android.ui.theme.IncomeGreen
import com.knowapp.android.ui.theme.PillShape
import com.knowapp.android.ui.theme.SmallRadius
import com.knowapp.android.ui.theme.WarnAmber
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun dayHeading(date: LocalDate): String {
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatementsScreen(viewModel: StatementsViewModel, onBack: () -> Unit, showBack: Boolean = true, onViewAudits: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val rows = state.visible
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

    fun printStatement() {
        val activity = context.findActivity() ?: return
        val file = File(context.cacheDir, "statement.pdf")
        StatementPdf.build(file, state.title, listOfNotNull(state.staffLabel?.let { "Staff: $it" }, "Period: ${state.periodText}").joinToString("  ·  "), rows, state.income, state.expense)
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(state.title, FilePrintAdapter(file, state.title), PrintAttributes.Builder().build())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statements", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (showBack) {
                        androidx.compose.material3.IconButton(onClick = onBack) {
                            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = { OutlinedButton(onClick = onViewAudits, shape = PillShape, modifier = Modifier.padding(end = 12.dp)) { Text("Audits") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                Button(
                    onClick = { printStatement() },
                    enabled = rows.isNotEmpty(),
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).height(52.dp),
                ) {
                    Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Print / Save PDF", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (state.isAdmin) {
                        val allLabel = "All staff (collective)"
                        val options = listOf(allLabel) + state.staff.map { "${it.fullName} (${it.username})" }
                        val current = state.staff.firstOrNull { it.id == state.selectedStaffId }?.let { "${it.fullName} (${it.username})" } ?: allLabel
                        Text("STAFF MEMBER", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        com.knowapp.android.ui.components.SimpleDropdown("Show", options, current, { choice ->
                            viewModel.selectStaff(state.staff.firstOrNull { "${it.fullName} (${it.username})" == choice }?.id)
                        })
                    }
                    Text("ACCOUNT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        STATEMENT_METHODS.forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = state.method == value,
                                onClick = { viewModel.selectMethod(value) },
                                shape = SegmentedButtonDefaults.itemShape(index, STATEMENT_METHODS.size),
                                label = { Text(label, maxLines = 1, softWrap = false, fontSize = 13.sp) },
                            )
                        }
                    }
                    Text("PERIOD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatementRange.values().forEach { range ->
                            FilterChip(selected = state.range == range, onClick = { viewModel.selectRange(range) }, label = { Text(range.label) })
                        }
                    }
                    if (state.range == StatementRange.CUSTOM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { pickingFrom = true }, shape = SmallRadius, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("From ${state.customFrom}", modifier = Modifier.padding(start = 6.dp), fontSize = 13.sp)
                            }
                            OutlinedButton(onClick = { pickingTo = true }, shape = SmallRadius, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("To ${state.customTo}", modifier = Modifier.padding(start = 6.dp), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            item {
                HeroCard {
                    Text(state.title.uppercase(), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Text(state.periodText, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 14.dp, bottom = 16.dp)) {
                        Text("NET  KES", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp, bottom = 5.dp))
                        Text(kesNumber(state.income - state.expense), color = Color.White, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryTile("Money in", Icons.Outlined.ArrowDownward, state.income, Modifier.weight(1f))
                        SummaryTile("Money out", Icons.Outlined.ArrowUpward, state.expense, Modifier.weight(1f))
                    }
                    Text("${rows.size} ${if (rows.size == 1) "entry" else "entries"}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }

            if (state.refreshing) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

            if (rows.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(22.dp)).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(34.dp))
                        Text("No entries for this selection", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                        Text("Try a different account or period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            val byDay = rows.groupBy { it.date }.toSortedMap(compareByDescending { it })
            byDay.forEach { (day, dayRows) ->
                item(key = "day-$day") {
                    val net = dayRows.fold(BigDecimal.ZERO) { a, r -> if (r.type == "income") a + r.amount else a - r.amount }
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dayHeading(day), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text("${if (net >= BigDecimal.ZERO) "+" else "-"}${kes(net.abs())}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp),
                        ) {
                            dayRows.forEachIndexed { index, row ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                StatementLine(row)
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickingFrom) {
        DatePick(initial = state.customFrom, onDismiss = { pickingFrom = false }) { viewModel.setCustomFrom(it); pickingFrom = false }
    }
    if (pickingTo) {
        DatePick(initial = state.customTo, onDismiss = { pickingTo = false }) { viewModel.setCustomTo(it); pickingTo = false }
    }
}

@Composable
private fun SummaryTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: BigDecimal, modifier: Modifier) {
    Column(modifier = modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        }
        Text(kesNumber(value), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StatementLine(row: StatementRow) {
    val color = if (row.type == "income") IncomeGreen else ExpenseOrange
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .then(if (row.pending) Modifier.background(WarnAmber.copy(alpha = 0.14f), SmallRadius).padding(6.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(38.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (row.type == "income") Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.category, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(if (row.pending) "${row.methodLabel} · waiting to sync" else row.methodLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${if (row.type == "income") "+" else "-"}${kes(row.amount)}", color = color, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePick(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = pickerState) }
}
