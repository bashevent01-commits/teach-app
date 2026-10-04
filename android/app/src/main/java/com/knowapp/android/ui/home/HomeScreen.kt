package com.knowapp.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.knowapp.android.data.model.PendingTransaction
import com.knowapp.android.data.model.StockItemOut
import com.knowapp.android.data.model.TransactionOut
import com.knowapp.android.data.repository.NewTransaction
import com.knowapp.android.ui.components.AppCard
import com.knowapp.android.ui.components.Badge
import com.knowapp.android.ui.components.SimpleDropdown
import com.knowapp.android.ui.components.StatBox
import com.knowapp.android.ui.components.kes
import com.knowapp.android.ui.theme.CardRadius
import com.knowapp.android.ui.theme.DangerRed
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.ExpenseOrange
import com.knowapp.android.ui.theme.IncomeGreen
import com.knowapp.android.ui.theme.PillShape
import com.knowapp.android.ui.theme.SmallRadius
import com.knowapp.android.ui.theme.WarnAmber
import kotlinx.coroutines.launch
import java.math.BigDecimal

private data class MenuItem(val title: String, val description: String, val buttonLabel: String, val onClick: () -> Unit)

private val METHOD_LABELS = listOf("Cash", "M-Pesa", "Bank")
private val METHOD_VALUES = listOf("cash", "mpesa", "bank")

private fun methodLabel(value: String) = METHOD_LABELS.getOrElse(METHOD_VALUES.indexOf(value)) { value }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onViewStock: () -> Unit,
    onViewNews: () -> Unit,
    onViewAudits: () -> Unit,
    onViewBooks: () -> Unit,
    onViewInstitutions: () -> Unit,
    onViewAccounts: () -> Unit,
    onViewMarket: () -> Unit,
    onViewSettings: () -> Unit,
) {
    val session by viewModel.session.collectAsState()
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val role = session?.role
    val isStaff = role == "staff"
    val isTeacher = isStaff && session?.staffType == "teacher"
    val isSuperAdmin = role == "super_admin"
    val isInstitutionAdmin = role == "institution_admin"

    var recordKind by remember { mutableStateOf<String?>(null) }
    var showOpening by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val menuItems = buildList {
        if (!isSuperAdmin) {
            if (!isTeacher) add(MenuItem("Stock", "Browse items, unit prices, and current quantities.", "View stock", onViewStock))
            add(MenuItem("News", "Institution announcements and updates.", "View news", onViewNews))
            add(MenuItem(if (isStaff) "Statements" else "Audits", "Submit and review financial audits.", "Open", onViewAudits))
        }
        if (isInstitutionAdmin) add(MenuItem("Books", "Everyone's records together, or one staff member at a time.", "Open books", onViewBooks))
        if (isInstitutionAdmin || isSuperAdmin) add(MenuItem("Accounts", "Manage staff and admin accounts.", "View accounts", onViewAccounts))
        if (isSuperAdmin) {
            add(MenuItem("Institutions", "Onboard and browse institutions on the platform.", "View institutions", onViewInstitutions))
            add(MenuItem("Market", "Cross-institution pricing insights (5-institution minimum).", "View market insights", onViewMarket))
        }
        add(MenuItem("Settings", "App updates, password, and sign out.", "Open settings", onViewSettings))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                Column {
                    Text(
                        text = "Hello, ${session?.fullName ?: ""}",
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Badge(text = roleLabel(role, session?.staffType), modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (isStaff) {
                item { OverviewCard(state, onSetOpening = { showOpening = true }) }
                item { ActionCard("Receiving", "Money coming into the institution", IncomeGreen) { recordKind = "income" } }
                item { ActionCard("Paying", "Money leaving the institution", ExpenseOrange) { recordKind = "expense" } }
                item { ActivityCard(state, onDiscard = viewModel::discardPending) }
            }
            items(menuItems) { HomeMenuCard(it.title, it.description, it.buttonLabel, it.onClick) }
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

private fun amountOf(raw: String): BigDecimal = raw.toBigDecimalOrNull() ?: BigDecimal.ZERO

@Composable
private fun OverviewCard(state: HomeUiState, onSetOpening: () -> Unit) {
    // Pending (not yet synced) entries count immediately; ones the server rejected do not
    val live = state.pending.filter { !it.failed }
    fun total(type: String) =
        state.transactions.filter { it.type == type }.fold(BigDecimal.ZERO) { a, t -> a + amountOf(t.amount) } +
            live.filter { it.type == type }.fold(BigDecimal.ZERO) { a, t -> a + amountOf(t.amount) }
    fun held(method: String): BigDecimal {
        val start = amountOf(
            when (method) {
                "cash" -> state.opening.cash
                "mpesa" -> state.opening.mpesa
                else -> state.opening.bank
            },
        )
        val inn = state.transactions.filter { it.method == method && it.type == "income" }.fold(BigDecimal.ZERO) { a, t -> a + amountOf(t.amount) } +
            live.filter { it.method == method && it.type == "income" }.fold(BigDecimal.ZERO) { a, t -> a + amountOf(t.amount) }
        val out = state.transactions.filter { it.method == method && it.type == "expense" }.fold(BigDecimal.ZERO) { a, t -> a + amountOf(t.amount) } +
            live.filter { it.method == method && it.type == "expense" }.fold(BigDecimal.ZERO) { a, t -> a + amountOf(t.amount) }
        return start + inn - out
    }

    val income = total("income")
    val expense = total("expense")

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text("Financial overview", style = MaterialTheme.typography.titleMedium)
        Text(
            "Calculated from every recorded transaction",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox(kes(income), "Income", Modifier.weight(1f))
            StatBox(kes(expense), "Expense", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            StatBox(kes(income - expense), "Net", Modifier.weight(1f))
            Box(modifier = Modifier.weight(1f))
        }
        Text("What you hold", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox(kes(held("cash")), "Cash", Modifier.weight(1f))
            StatBox(kes(held("mpesa")), "M-Pesa", Modifier.weight(1f))
            StatBox(kes(held("bank")), "Bank", Modifier.weight(1f))
        }
        TextButton(onClick = onSetOpening, modifier = Modifier.padding(top = 6.dp)) {
            Text(if (state.opening.isSet) "Edit starting balances" else "Set starting balances")
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color, CardRadius)
            .clickable(onClick = onClick)
            .padding(22.dp),
    ) {
        Column {
            Text(title, color = Color.White, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ActivityCard(state: HomeUiState, onDiscard: (String) -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text("Recent activity", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            // Pending rows are tinted until the server has them, then they appear as normal rows
            state.pending.forEach { p -> PendingRow(p, onDiscard) }
            val recent = state.transactions.sortedByDescending { it.createdAt }.take(8)
            if (recent.isEmpty() && state.pending.isEmpty()) {
                Text("No entries yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            recent.forEach { TransactionRow(it) }
        }
    }
}

@Composable
private fun PendingRow(p: PendingTransaction, onDiscard: (String) -> Unit) {
    val tint = if (p.failed) DangerRed else WarnAmber
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.12f), SmallRadius)
            .padding(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
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
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, SmallRadius)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(t.category, fontWeight = FontWeight.SemiBold)
            Text(
                "${methodLabel(t.method)} · ${t.transactionDate.take(10)}",
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

@Composable
private fun HomeMenuCard(title: String, description: String, buttonLabel: String, onClick: () -> Unit) {
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
        ) { Text(buttonLabel, fontWeight = FontWeight.SemiBold) }
    }
}

private fun roleLabel(role: String?, staffType: String?): String = when (role) {
    "super_admin" -> "Super admin"
    "institution_admin" -> "Sub admin"
    "staff" -> if (staffType == "teacher") "Staff · Teacher" else "Staff"
    else -> role ?: ""
}
