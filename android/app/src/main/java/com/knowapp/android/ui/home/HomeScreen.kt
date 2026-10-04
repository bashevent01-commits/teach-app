package com.knowapp.android.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
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
import com.knowapp.android.ui.components.SimpleDropdown
import com.knowapp.android.ui.components.kes
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

private val METHOD_LABELS = listOf("Cash", "M-Pesa", "Bank")
private val METHOD_VALUES = listOf("cash", "mpesa", "bank")
private val HeroTeal = Color(0xFF00695F)

private fun methodLabel(value: String) = METHOD_LABELS.getOrElse(METHOD_VALUES.indexOf(value)) { value }

private fun amountOf(raw: String): BigDecimal = raw.toBigDecimalOrNull() ?: BigDecimal.ZERO

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
                    Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(HeroTeal), contentAlignment = Alignment.Center) {
                        Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = "KNOW", modifier = Modifier.size(46.dp))
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            "${greeting()}, ${session?.fullName?.substringBefore(' ') ?: ""}",
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        state.institutionName?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { onToggleTheme(!dark) }) {
                        Icon(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, contentDescription = "Switch theme")
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(HeroTeal, CardRadius).padding(20.dp)) {
                    Text("You hold", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        kes(cash + mpesa + bank),
                        color = Color.White,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroTile("Cash", cash, Modifier.weight(1f))
                        HeroTile("M-Pesa", mpesa, Modifier.weight(1f))
                        HeroTile("Bank", bank, Modifier.weight(1f))
                    }
                    if (!state.opening.isSet) {
                        TextButton(onClick = { showOpening = true }, modifier = Modifier.padding(top = 4.dp)) {
                            Text("Add what you already hold", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        TextButton(onClick = { showOpening = true }, modifier = Modifier.padding(top = 4.dp)) {
                            Text("Edit starting balances", color = Color.White.copy(alpha = 0.85f))
                        }
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
                Text("This month", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    MonthTile("Income", income, IncomeGreen, Modifier.weight(1f))
                    MonthTile("Expense", expense, ExpenseOrange, Modifier.weight(1f))
                    MonthTile("Net", income - expense, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent activity", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onSeeAll) { Text("See all") }
                }
            }
            val recent = state.transactions.sortedByDescending { it.createdAt }.take(8)
            if (state.pending.isEmpty() && recent.isEmpty()) {
                item { Text("Nothing recorded yet. Tap Receiving or Paying to add your first entry.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            // Pending rows are tinted until the server has them, then they appear as normal rows
            items(state.pending.size) { index -> PendingRow(state.pending[index], viewModel::discardPending) }
            items(recent.size) { index -> TransactionRow(recent[index]) }
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
private fun HeroTile(label: String, value: BigDecimal, modifier: Modifier) {
    Column(modifier = modifier.background(Color.White.copy(alpha = 0.14f), SmallRadius).padding(10.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
        Text(kes(value), color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
private fun MonthTile(label: String, value: BigDecimal, color: Color, modifier: Modifier) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface, SmallRadius).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(kes(value), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
private fun QuickAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.clip(CardRadius).background(color).clickable(onClick = onClick).padding(16.dp),
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
    Column(modifier = Modifier.fillMaxWidth().background(tint.copy(alpha = 0.14f), SmallRadius).padding(12.dp)) {
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
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, SmallRadius).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(t.type)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.category, fontWeight = FontWeight.SemiBold)
            Text("${methodLabel(t.method)} · ${t.transactionDate.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${if (t.type == "income") "+" else "-"}${kes(t.amount)}",
            color = if (t.type == "income") IncomeGreen else ExpenseOrange,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var amount by remember { mutableStateOf("") }
    var methodLabel by remember { mutableStateOf(METHOD_LABELS[0]) }
    var useStock by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var mpesaCode by remember { mutableStateOf("") }
    var payer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val isIncome = kind == "income"
    val method = METHOD_VALUES[METHOD_LABELS.indexOf(methodLabel)]

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isIncome) "Receiving" else "Paying", style = MaterialTheme.typography.titleLarge, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold)

            if (!isTeacher) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !useStock, onClick = { useStock = false }, label = { Text("Other") })
                    FilterChip(selected = useStock, onClick = { useStock = true }, label = { Text("Stock") })
                }
            }

            if (useStock) {
                SimpleDropdown("Item", stock.map { it.name }, itemName, { itemName = it })
                OutlinedTextField(
                    value = quantity, onValueChange = { quantity = it }, label = { Text("Quantity") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = SmallRadius,
                )
            } else {
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            }

            OutlinedTextField(
                value = amount, onValueChange = { amount = it }, label = { Text(if (useStock) "Amount (KES, blank = price × quantity)" else "Amount (KES)") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = SmallRadius,
            )
            SimpleDropdown("Method", METHOD_LABELS, methodLabel, { methodLabel = it })
            if (method == "mpesa") {
                OutlinedTextField(value = mpesaCode, onValueChange = { mpesaCode = it }, label = { Text("M-Pesa code (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
                OutlinedTextField(value = payer, onValueChange = { payer = it }, label = { Text("Name on M-Pesa (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            }
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)

            error?.let { Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }

            Button(
                onClick = {
                    val item = stock.firstOrNull { it.name == itemName }
                    val qty = quantity.toBigDecimalOrNull()
                    var amountValue = amount.toBigDecimalOrNull()
                    if (useStock && amountValue == null && item?.unitPrice != null && qty != null) {
                        amountValue = item.unitPrice.toBigDecimalOrNull()?.multiply(qty)
                    }
                    error = when {
                        useStock && item == null -> "Choose an item."
                        useStock && (qty == null || qty <= BigDecimal.ZERO) -> "Enter a quantity."
                        !useStock && category.isBlank() -> "Enter a category."
                        amountValue == null || amountValue <= BigDecimal.ZERO -> "Enter an amount."
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
                                amount = amountValue!!.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
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
                colors = ButtonDefaults.buttonColors(containerColor = if (isIncome) IncomeGreen else ExpenseOrange),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Saving…" else "Save", fontWeight = FontWeight.SemiBold) }
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Starting balances", style = MaterialTheme.typography.titleLarge, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold)
            Text("Enter what you already hold today, before your first entry here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(Triple("Cash (KES)", c, { v: String -> c = v }), Triple("M-Pesa (KES)", m, { v: String -> m = v }), Triple("Bank (KES)", b, { v: String -> b = v })).forEach { (label, value, set) ->
                OutlinedTextField(
                    value = value, onValueChange = set, label = { Text(label) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = SmallRadius,
                )
            }
            Button(
                onClick = { onSave(c.toDoubleOrNull() ?: 0.0, m.toDoubleOrNull() ?: 0.0, b.toDoubleOrNull() ?: 0.0) },
                shape = PillShape,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save starting balances", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(onClick = onDismiss, shape = PillShape, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

