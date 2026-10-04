package com.knowapp.android.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.knowapp.android.R
import com.knowapp.android.data.model.PendingTransaction
import com.knowapp.android.data.model.StockItemOut
import com.knowapp.android.data.model.TransactionOut
import com.knowapp.android.data.repository.NewTransaction
import com.knowapp.android.ui.components.HeroCard
import com.knowapp.android.ui.components.SimpleDropdown
import com.knowapp.android.ui.components.kes
import com.knowapp.android.ui.components.kesNumber
import com.knowapp.android.ui.statements.dateOf
import com.knowapp.android.ui.theme.CardRadius
import com.knowapp.android.ui.theme.DangerRed
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.ExpenseOrange
import com.knowapp.android.ui.theme.IncomeGreen
import com.knowapp.android.ui.theme.PillShape
import com.knowapp.android.ui.theme.SmallRadius
import com.knowapp.android.ui.theme.WarnAmber
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val METHOD_LABELS = listOf("Cash", "M-Pesa", "Bank")
private val METHOD_VALUES = listOf("cash", "mpesa", "bank")
private val HeroTeal = Color(0xFF00695F)

private fun methodLabel(value: String) = METHOD_LABELS.getOrElse(METHOD_VALUES.indexOf(value)) { value }

private fun amountOf(raw: String): BigDecimal = raw.toBigDecimalOrNull() ?: BigDecimal.ZERO

private fun dayLabel(iso: String): String {
    val date = dateOf(iso)
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMM"))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSeeAll: () -> Unit,
    onToggleTheme: (Boolean) -> Unit,
) {
    val session by viewModel.session.collectAsState()
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isTeacher = session?.staffType == "teacher"

    var recordKind by remember { mutableStateOf<String?>(null) }
    var showOpening by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    // Pending (not yet synced) entries count immediately; ones the server rejected do not
    val live = state.pending.filter { !it.failed }
    val today = LocalDate.now()
    fun inMonth(iso: String) = dateOf(iso).let { it.year == today.year && it.month == today.month }
    fun monthTotal(type: String): BigDecimal =
        state.transactions.filter { it.type == type && inMonth(it.transactionDate) }.fold(BigDecimal.ZERO) { a, t -> a + amountOf(t.amount) } +
            live.filter { it.type == type && inMonth(it.createdAt) }.fold(BigDecimal.ZERO) { a, t -> a + amountOf(t.amount) }
    fun held(method: String): BigDecimal {
        val start = amountOf(when (method) { "cash" -> state.opening.cash; "mpesa" -> state.opening.mpesa; else -> state.opening.bank })
        fun sum(type: String) =
            state.transactions.filter { it.method == method && it.type == type }.fold(BigDecimal.ZERO) { a, t -> a + amountOf(t.amount) } +
                live.filter { it.method == method && it.type == type }.fold(BigDecimal.ZERO) { a, t -> a + amountOf(t.amount) }
        return start + sum("income") - sum("expense")
    }
    val cash = held("cash")
    val mpesa = held("mpesa")
    val bank = held("bank")
    val income = monthTotal("income")
    val expense = monthTotal("expense")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(R.drawable.know_logo), contentDescription = "KNOW", modifier = Modifier.size(50.dp))
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            "${greeting()}, ${session?.fullName?.substringBefore(' ') ?: ""}",
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(state.institutionName ?: "Welcome back", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onToggleTheme(!dark) }) {
                        Icon(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, contentDescription = "Switch theme")
                    }
                }
            }

            item {
                HeroCard {
                    Text("TOTAL YOU HOLD", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)) {
                        Text("KES", color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp, bottom = 6.dp))
                        Text(
                            kesNumber(cash + mpesa + bank),
                            color = Color.White,
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 38.sp,
                            maxLines = 1,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroTile("Cash", Icons.Outlined.Payments, cash, Modifier.weight(1f))
                        HeroTile("M-Pesa", Icons.Outlined.PhoneAndroid, mpesa, Modifier.weight(1f))
                        HeroTile("Bank", Icons.Outlined.AccountBalance, bank, Modifier.weight(1f))
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clip(PillShape)
                            .border(1.dp, Color.White.copy(alpha = 0.55f), PillShape)
                            .clickable { showOpening = true }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(
                            if (state.opening.isSet) "Edit starting balances" else "Add what you already hold",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickAction("Receiving", "Money in", Icons.Outlined.ArrowDownward, IncomeGreen, Modifier.weight(1f)) { recordKind = "income" }
                    QuickAction("Paying", "Money out", Icons.Outlined.ArrowUpward, ExpenseOrange, Modifier.weight(1f)) { recordKind = "expense" }
                }
            }

            item {
                CardShell {
                    Text("This month", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        MonthTile("Income", income, IncomeGreen, Modifier.weight(1f))
                        MonthTile("Expense", expense, ExpenseOrange, Modifier.weight(1f))
                        MonthTile("Net", income - expense, if (income - expense < BigDecimal.ZERO) DangerRed else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    }
                    FlowBar(income, expense, modifier = Modifier.padding(top = 14.dp))
                }
            }

            item {
                val recent = state.transactions.sortedByDescending { it.createdAt }.take(8)
                CardShell {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent activity", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onSeeAll) { Text("See all") }
                    }
                    if (state.pending.isEmpty() && recent.isEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(34.dp))
                            Text("Nothing recorded yet", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                            Text("Tap Receiving or Paying to add your first entry.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    // Pending rows are tinted until the server has them, then they appear as normal rows
                    state.pending.forEach { PendingRow(it, viewModel::discardPending) }
                    recent.forEachIndexed { index, t ->
                        if (index > 0 || state.pending.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        TransactionRow(t)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    recordKind?.let { kind ->
        RecordSheet(
            kind = kind,
            stock = state.stock,
            isTeacher = isTeacher,
            saving = state.saving,
            onDismiss = { recordKind = null },
            onSubmit = { draft -> viewModel.record(draft) { ok -> if (ok) recordKind = null } },
        )
    }

    if (showOpening) {
        OpeningSheet(
            cash = state.opening.cash,
            mpesa = state.opening.mpesa,
            bank = state.opening.bank,
            onDismiss = { showOpening = false },
            onSave = { c, m, b -> viewModel.saveOpening(c, m, b) { ok -> if (ok) showOpening = false } },
        )
    }
}

@Composable
private fun CardShell(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun HeroTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: BigDecimal, modifier: Modifier) {
    Column(modifier = modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 12.dp)) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
        Text(label, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Text(kesNumber(value), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun MonthTile(label: String, value: BigDecimal, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(kesNumber(value), color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun FlowBar(income: BigDecimal, expense: BigDecimal, modifier: Modifier = Modifier) {
    val total = income + expense
    Row(modifier = modifier.fillMaxWidth().height(8.dp).clip(PillShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (total > BigDecimal.ZERO) {
            val share = income.divide(total, 4, java.math.RoundingMode.HALF_UP).toFloat()
            Box(modifier = Modifier.weight(share.coerceIn(0.001f, 0.999f)).fillMaxSize().background(IncomeGreen))
            Box(modifier = Modifier.weight((1f - share).coerceIn(0.001f, 0.999f)).fillMaxSize().background(ExpenseOrange))
        }
    }
}

@Composable
private fun QuickAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(color).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(38.dp).background(Color.White.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title, color = Color.White, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RowIcon(type: String) {
    val color = if (type == "income") IncomeGreen else ExpenseOrange
    Box(modifier = Modifier.size(38.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(if (type == "income") Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, contentDescription = null, tint = color)
    }
}

@Composable
private fun PendingRow(p: PendingTransaction, onDiscard: (String) -> Unit) {
    val tint = if (p.failed) DangerRed else WarnAmber
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(tint.copy(alpha = 0.14f), SmallRadius).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RowIcon(p.type)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(p.category?.takeIf { it.isNotBlank() } ?: "Stock entry", fontWeight = FontWeight.SemiBold)
                Text(methodLabel(p.method), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${if (p.type == "income") "+" else "-"}${kes(p.amount)}",
                color = if (p.type == "income") IncomeGreen else ExpenseOrange,
                fontWeight = FontWeight.Bold,
            )
        }
        if (p.failed) {
            Text("Not saved: ${p.error ?: "rejected by the server"}", style = MaterialTheme.typography.bodySmall, color = DangerRed, modifier = Modifier.padding(top = 4.dp))
            TextButton(onClick = { onDiscard(p.localId) }) { Text("Discard") }
        }
    }
}

@Composable
private fun TransactionRow(t: TransactionOut) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(t.type)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.category, fontWeight = FontWeight.SemiBold)
            Text("${methodLabel(t.method)} · ${dayLabel(t.transactionDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${if (t.type == "income") "+" else "-"}${kes(t.amount)}",
            color = if (t.type == "income") IncomeGreen else ExpenseOrange,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val INCOME_SUGGESTIONS = listOf("Fees", "Sales", "Donation", "Other")
private val EXPENSE_SUGGESTIONS = listOf("Rent", "Salaries", "Transport", "Utilities", "Supplies", "Other")

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChoiceCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(top = if (icon != null) 6.dp else 0.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RecordSheet(
    kind: String,
    stock: List<StockItemOut>,
    isTeacher: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (NewTransaction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isIncome = kind == "income"
    val accent = if (isIncome) IncomeGreen else ExpenseOrange
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("cash") }
    var useStock by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var mpesaCode by remember { mutableStateOf("") }
    var payer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val item = stock.firstOrNull { it.name == itemName }
    val qty = quantity.toBigDecimalOrNull()
    val computed = if (useStock && item?.unitPrice != null && qty != null) item.unitPrice.toBigDecimalOrNull()?.multiply(qty) else null
    val shownTotal = amount.toBigDecimalOrNull() ?: computed

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(46.dp).background(accent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (isIncome) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, contentDescription = null, tint = accent)
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(if (isIncome) "Receiving" else "Paying", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(if (isIncome) "Money coming in" else "Money going out", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Amount")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    prefix = { Text("KES ", fontWeight = FontWeight.SemiBold) },
                    placeholder = { Text(if (useStock) "Auto from price × quantity" else "0.00") },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(if (isIncome) "Received through" else "Paid through")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceCard("Cash", Icons.Outlined.Payments, method == "cash", accent, Modifier.weight(1f)) { method = "cash" }
                    ChoiceCard("M-Pesa", Icons.Outlined.PhoneAndroid, method == "mpesa", accent, Modifier.weight(1f)) { method = "mpesa" }
                    ChoiceCard("Bank", Icons.Outlined.AccountBalance, method == "bank", accent, Modifier.weight(1f)) { method = "bank" }
                }
            }

            if (method == "mpesa") {
                Column(
                    modifier = Modifier.fillMaxWidth().background(accent.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionLabel("M-Pesa details (optional)")
                    OutlinedTextField(value = mpesaCode, onValueChange = { mpesaCode = it }, label = { Text("Transaction code") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
                    OutlinedTextField(value = payer, onValueChange = { payer = it }, label = { Text("Name on M-Pesa") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
                }
            }

            if (!isTeacher) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("What is it for")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChoiceCard("General", null, !useStock, accent, Modifier.weight(1f)) { useStock = false }
                        ChoiceCard("Stock item", null, useStock, accent, Modifier.weight(1f)) { useStock = true }
                    }
                }
            }

            if (useStock) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SimpleDropdown("Item", stock.map { it.name }, itemName, { itemName = it })
                    if (item?.unitPrice != null) {
                        Text("Price ${kes(item.unitPrice)} · ${item.quantity} in stock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = quantity, onValueChange = { quantity = it }, label = { Text("Quantity") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = SmallRadius,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Category")
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (if (isIncome) INCOME_SUGGESTIONS else EXPENSE_SUGGESTIONS).forEach { suggestion ->
                            FilterChip(
                                selected = category == suggestion,
                                onClick = { category = suggestion },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Or type your own") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
                }
            }

            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Note (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)

            error?.let {
                Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().background(DangerRed.copy(alpha = 0.12f), SmallRadius).padding(12.dp))
            }

            Button(
                onClick = {
                    val chosenAmount = amount.toBigDecimalOrNull() ?: computed
                    error = when {
                        useStock && item == null -> "Choose a stock item."
                        useStock && (qty == null || qty <= BigDecimal.ZERO) -> "Enter a quantity."
                        !useStock && category.isBlank() -> "Pick or type a category."
                        chosenAmount == null || chosenAmount <= BigDecimal.ZERO -> "Enter an amount."
                        else -> null
                    }
                    if (error == null) {
                        onSubmit(
                            NewTransaction(
                                type = kind,
                                method = method,
                                categoryType = if (useStock) "STOCK" else "OTHER",
                                category = if (useStock) item?.name else category.trim(),
                                description = description.trim().ifBlank { null },
                                amount = chosenAmount!!.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                                stockItemId = if (useStock) item?.id else null,
                                quantity = if (useStock) quantity else null,
                                mpesaCode = if (method == "mpesa") mpesaCode.trim().ifBlank { null } else null,
                                mpesaPayerName = if (method == "mpesa") payer.trim().ifBlank { null } else null,
                            ),
                        )
                    }
                },
                enabled = !saving,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(
                    if (saving) "Saving…" else if (shownTotal != null && shownTotal > BigDecimal.ZERO) "Save · ${kes(shownTotal)}" else "Save",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpeningSheet(cash: String, mpesa: String, bank: String, onDismiss: () -> Unit, onSave: (Double, Double, Double) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var c by remember { mutableStateOf(cash.toBigDecimalOrNull()?.toPlainString() ?: "0") }
    var m by remember { mutableStateOf(mpesa.toBigDecimalOrNull()?.toPlainString() ?: "0") }
    var b by remember { mutableStateOf(bank.toBigDecimalOrNull()?.toPlainString() ?: "0") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Starting balances", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("What you already hold today, before your first entry here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(
                Triple("Cash", Icons.Outlined.Payments, c) to { v: String -> c = v },
                Triple("M-Pesa", Icons.Outlined.PhoneAndroid, m) to { v: String -> m = v },
                Triple("Bank", Icons.Outlined.AccountBalance, b) to { v: String -> b = v },
            ).forEach { (spec, set) ->
                val (label, icon, value) = spec
                OutlinedTextField(
                    value = value, onValueChange = set, label = { Text(label) }, singleLine = true,
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    prefix = { Text("KES ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                )
            }
            Button(
                onClick = { onSave(c.toDoubleOrNull() ?: 0.0, m.toDoubleOrNull() ?: 0.0, b.toDoubleOrNull() ?: 0.0) },
                shape = PillShape,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text("Save starting balances", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}
