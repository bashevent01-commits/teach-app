package com.knowapp.android.ui.statements

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.core.content.FileProvider
import com.knowapp.android.ui.components.HeroCard
import com.knowapp.android.ui.components.SkeletonRows
import com.knowapp.android.ui.components.categoryIcon
import com.knowapp.android.ui.components.SimpleDropdown
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
    val lines = state.lines
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }

    fun buildPdf(): File {
        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val file = File(dir, "KNOW-Statement-${LocalDate.now()}.pdf")
        val generated = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
        StatementPdf.build(
            file,
            StatementPdfData(
                institution = state.institutionName,
                title = state.title,
                account = state.accountLabel,
                period = state.periodText,
                staff = state.staffLabel,
                preparedBy = state.preparedBy,
                generated = generated,
                lines = lines,
                opening = if (state.showBalance) state.openingBalance else null,
                closing = if (state.showBalance) state.closingBalance else null,
                income = state.income,
                expense = state.expense,
            ),
        )
        return file
    }

    fun printStatement() {
        val activity = context.findActivity() ?: return
        val file = buildPdf()
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(state.title, FilePrintAdapter(file, state.title), PrintAttributes.Builder().build())
    }

    fun shareStatement() {
        val file = buildPdf()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share statement"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statements", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold) },
                actions = { OutlinedButton(onClick = onViewAudits, shape = PillShape, modifier = Modifier.padding(end = 12.dp)) { Text("Audits") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                Button(
                    onClick = { downloading = true },
                    enabled = !state.loading,
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).height(52.dp),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Download statement", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.isAdmin) {
                        val everyone = "All staff (collective)"
                        FilterPill(
                            value = state.staffLabel ?: everyone,
                            options = listOf(everyone) + state.staff.map { it.fullName },
                            onSelect = { choice -> viewModel.selectStaff(state.staff.firstOrNull { it.fullName == choice }?.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterPill(
                            value = state.accountLabel,
                            options = ACCOUNT_OPTIONS.map { it.second },
                            onSelect = { label -> ACCOUNT_OPTIONS.firstOrNull { it.second == label }?.let { viewModel.selectMethod(it.first) } },
                            modifier = Modifier.weight(1f),
                        )
                        FilterPill(
                            value = periodLabel(state.range),
                            options = StatementRange.values().map { periodLabel(it) },
                            onSelect = { label -> StatementRange.values().firstOrNull { periodLabel(it) == label }?.let { viewModel.selectRange(it) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (state.range == StatementRange.CUSTOM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { pickingFrom = true }, shape = PillShape, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("From ${state.customFrom}", modifier = Modifier.padding(start = 6.dp), fontSize = 13.sp)
                            }
                            OutlinedButton(onClick = { pickingTo = true }, shape = PillShape, modifier = Modifier.weight(1f)) {
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
                    if (state.showBalance) {
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 14.dp, bottom = 16.dp)) {
                            Text("CLOSING  KES", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp, bottom = 5.dp))
                            Text(kesNumber(state.closingBalance), color = Color.White, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, maxLines = 1)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 14.dp, bottom = 16.dp)) {
                            Text("NET  KES", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp, bottom = 5.dp))
                            Text(kesNumber(state.income - state.expense), color = Color.White, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, maxLines = 1)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.showBalance) SummaryTile("Opening", null, state.openingBalance, Modifier.weight(1f))
                        SummaryTile("Money in", Icons.Outlined.ArrowDownward, state.income, Modifier.weight(1f))
                        SummaryTile("Money out", Icons.Outlined.ArrowUpward, state.expense, Modifier.weight(1f))
                    }
                    Text("${lines.size} ${if (lines.size == 1) "entry" else "entries"}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }

            if (state.refreshing) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

            if (state.loading && lines.isEmpty()) item { SkeletonRows() }

            val spend = lines.filter { it.row.type == "expense" }
                .groupBy { it.row.category }
                .mapValues { (_, v) -> v.fold(BigDecimal.ZERO) { a, l -> a + l.row.amount } }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
            if (spend.isNotEmpty() && state.expense.signum() > 0) {
                item { CategoryBreakdown(spend, state.expense) }
            }

            if (lines.isEmpty()) {
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

            val byDay = lines.groupBy { it.row.date }.toSortedMap(compareByDescending { it })
            byDay.forEach { (day, dayLines) ->
                item(key = "day-$day") {
                    val net = dayLines.fold(BigDecimal.ZERO) { a, l -> if (l.row.type == "income") a + l.row.amount else a - l.row.amount }
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dayHeading(day), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text("${if (net >= BigDecimal.ZERO) "+" else "\u2212"}${kes(net.abs())}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp),
                        ) {
                            dayLines.asReversed().forEachIndexed { index, line ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                StatementLineItem(line)
                            }
                        }
                    }
                }
            }
        }
    }

    if (downloading) {
        DownloadSheet(
            state = state,
            entries = lines.size,
            onDismiss = { downloading = false },
            onPrint = { downloading = false; printStatement() },
            onShare = { downloading = false; shareStatement() },
        )
    }

    if (pickingFrom) {
        DatePick(initial = state.customFrom, onDismiss = { pickingFrom = false }) { viewModel.setCustomFrom(it); pickingFrom = false }
    }
    if (pickingTo) {
        DatePick(initial = state.customTo, onDismiss = { pickingTo = false }) { viewModel.setCustomTo(it); pickingTo = false }
    }
}

@Composable
private fun SummaryTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, value: BigDecimal, modifier: Modifier) {
    Column(modifier = modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp))
        }
        Text(kesNumber(value), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StatementLineItem(line: StatementLine) {
    val row = line.row
    val color = if (row.type == "income") IncomeGreen else ExpenseOrange
    val time = row.time?.format(DateTimeFormatter.ofPattern("h:mm a"))
    val subtitle = listOfNotNull(time, row.methodLabel, row.reference).joinToString(" · ")
    val detail = listOfNotNull(row.party, row.note).joinToString(" · ")
    // Entries still waiting to sync are only tinted, with no extra wording
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .then(if (row.pending) Modifier.background(WarnAmber.copy(alpha = 0.10f), SmallRadius).padding(6.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(38.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(categoryIcon(row.category, row.type), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.category, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${if (row.type == "income") "+" else "\u2212"}${kes(row.amount)}", color = color, fontWeight = FontWeight.Bold)
            line.balance?.let { Text("Bal ${kesNumber(it)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadSheet(state: StatementsUiState, entries: Int, onDismiss: () -> Unit, onPrint: () -> Unit, onShare: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Download statement", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Column(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetRow("Statement", state.title)
                SheetRow("Account", state.accountLabel)
                SheetRow("Period", state.periodText)
                state.staffLabel?.let { SheetRow("Staff", it) }
                SheetRow("Entries", "$entries")
                if (state.showBalance) {
                    SheetRow("Opening balance", kes(state.openingBalance))
                    SheetRow("Closing balance", kes(state.closingBalance))
                }
                SheetRow("Money in", kes(state.income))
                SheetRow("Money out", kes(state.expense))
            }
            Text(
                "Each line lists the date and time, the description, the M-Pesa code or bank reference, the method and the amount.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onPrint, shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Save or print PDF", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onShare, shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Share PDF", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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

@Composable
private fun CategoryBreakdown(rows: List<Pair<String, BigDecimal>>, total: BigDecimal) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Where the money went", style = MaterialTheme.typography.titleMedium)
        rows.forEach { (name, amount) ->
            val share = amount.divide(total, 4, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0.02f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(34.dp).background(ExpenseOrange.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(categoryIcon(name, "expense"), contentDescription = null, tint = ExpenseOrange, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(kes(amount), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Box(modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, PillShape)) {
                        Box(modifier = Modifier.fillMaxWidth(share).height(6.dp).background(ExpenseOrange, PillShape))
                    }
                }
            }
        }
    }
}

private val ACCOUNT_OPTIONS = listOf("all" to "All accounts", "cash" to "Cash", "mpesa" to "M-Pesa", "bank" to "Bank")

private fun periodLabel(range: StatementRange): String = if (range == StatementRange.CUSTOM) "Custom range" else range.label

// One compact menu instead of a row of chips: the current choice is shown, the rest appear on tap
@Composable
private fun FilterPill(value: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), PillShape)
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal) },
                    trailingIcon = { if (option == value) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { open = false; onSelect(option) },
                )
            }
        }
    }
}
