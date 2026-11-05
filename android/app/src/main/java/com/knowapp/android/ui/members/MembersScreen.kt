package com.knowapp.android.ui.members

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowapp.android.data.MEMBER_PERIODS
import com.knowapp.android.data.dueLabel
import com.knowapp.android.data.model.MemberOut
import com.knowapp.android.data.niceDay
import com.knowapp.android.data.periodLabel
import com.knowapp.android.ui.components.AppCard
import com.knowapp.android.ui.components.SimpleDropdown
import com.knowapp.android.ui.components.kes
import com.knowapp.android.ui.theme.DangerRed
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.ExpenseOrange
import com.knowapp.android.ui.theme.IncomeGreen
import com.knowapp.android.ui.theme.PillShape
import com.knowapp.android.ui.theme.SmallRadius
import java.time.LocalDate

private val FILTERS = listOf("all" to "Everyone", "overdue" to "Behind", "due_today" to "Due today", "due_tomorrow" to "Due tomorrow", "paused" to "Paused")

private fun statusColor(status: String): Color = when (status) {
    "overdue" -> DangerRed
    "due_today" -> ExpenseOrange
    "due_tomorrow" -> Color(0xFFCA8A04)
    "paused" -> Color(0xFF6B7280)
    else -> IncomeGreen
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MembersScreen(viewModel: MembersViewModel, canRecordPayment: Boolean, onRecordPayment: (MemberOut) -> Unit) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<MemberOut?>(null) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    val shown = state.members
        .filter { state.filter == "all" || it.status == state.filter }
        .filter { state.query.isBlank() || it.name.contains(state.query, true) || (it.phone ?: "").contains(state.query) }
    fun count(status: String) = state.members.count { it.status == status }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Members", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add member") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Counter("Behind", count("overdue"), DangerRed, Modifier.weight(1f)) { viewModel.setFilter("overdue") }
                Counter("Due today", count("due_today"), ExpenseOrange, Modifier.weight(1f)) { viewModel.setFilter("due_today") }
                Counter("Due tomorrow", count("due_tomorrow"), Color(0xFFCA8A04), Modifier.weight(1f)) { viewModel.setFilter("due_tomorrow") }
            }
            OutlinedTextField(
                value = state.query, onValueChange = viewModel::setQuery, singleLine = true, label = { Text("Search by name or phone") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = SmallRadius,
            )
            androidx.compose.foundation.layout.FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FILTERS.forEach { (key, label) -> FilterChip(selected = state.filter == key, onClick = { viewModel.setFilter(key) }, label = { Text(label) }) }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    shown.isEmpty() -> Column(modifier = Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (state.members.isEmpty()) "No members yet." else "No one matches.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.members.isEmpty()) Button(onClick = { adding = true }, shape = PillShape, modifier = Modifier.padding(top = 12.dp)) { Text("Add the first member") }
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(shown, key = { it.id }) { m -> MemberCard(m, canRecordPayment, onPay = { onRecordPayment(m) }, onEdit = { editing = m }) }
                    }
                }
            }
        }
    }

    if (adding) MemberSheet(null, onDismiss = { adding = false }, onSave = { fields -> viewModel.save(null, fields) { ok -> if (ok) adding = false } }, onRemove = null)
    editing?.let { m ->
        MemberSheet(m, onDismiss = { editing = null }, onSave = { fields -> viewModel.save(m.id, fields) { ok -> if (ok) editing = null } }, onRemove = { viewModel.remove(m.id) { editing = null } })
    }
}

@Composable
private fun Counter(label: String, value: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = if (value > 0) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemberCard(m: MemberOut, canRecordPayment: Boolean, onPay: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(m.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("${m.plan ?: periodLabel(m.period)} · ${kes(m.fee)} ${periodLabel(m.period).lowercase()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                m.phone?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            }
            Text(dueLabel(m), color = statusColor(m.status), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canRecordPayment && m.isActive) Button(onClick = onPay, shape = PillShape, modifier = Modifier.weight(1f)) { Text("Record payment") }
            if (!m.phone.isNullOrBlank()) OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${m.phone}"))) }, shape = PillShape) { Text("Call") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberSheet(member: MemberOut?, onDismiss: () -> Unit, onSave: (Map<String, Any?>) -> Unit, onRemove: (() -> Unit)?) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var name by remember { mutableStateOf(member?.name ?: "") }
    var phone by remember { mutableStateOf(member?.phone ?: "") }
    var plan by remember { mutableStateOf(member?.plan ?: "") }
    var fee by remember { mutableStateOf(member?.fee?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: "") }
    var period by remember { mutableStateOf(periodLabel(member?.period ?: "monthly")) }
    var nextDue by remember { mutableStateOf(member?.nextDue ?: LocalDate.now().toString()) }
    var active by remember { mutableStateOf(member?.isActive ?: true) }
    var confirmRemove by remember { mutableStateOf(false) }
    val ready = name.trim().length >= 2 && (fee.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) > java.math.BigDecimal.ZERO

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (member == null) "Add a member" else "Member", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            OutlinedTextField(value = plan, onValueChange = { plan = it }, label = { Text("Plan name (optional)") }, placeholder = { Text("Monthly, Student, Couples…") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            OutlinedTextField(value = fee, onValueChange = { fee = it }, label = { Text("Subscription fee (KES)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            SimpleDropdown("Pays", MEMBER_PERIODS.map { it.second }, period, { period = it })
            OutlinedButton(
                onClick = {
                    val d = runCatching { LocalDate.parse(nextDue) }.getOrDefault(LocalDate.now())
                    DatePickerDialog(context, { _, y, m, day -> nextDue = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                },
                shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Next payment due: ${niceDay(nextDue)}") }
            Text("Leave the date as today if they are paying now, then record the payment so it moves on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (member != null) {
                OutlinedButton(onClick = { active = !active }, shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (active) "Active member (tap to pause)" else "Paused (tap to activate)")
                }
            }
            Button(
                onClick = {
                    onSave(mapOf(
                        "name" to name.trim(), "phone" to phone.trim().ifBlank { null }, "plan" to plan.trim().ifBlank { null },
                        "fee" to fee.trim(), "period" to (MEMBER_PERIODS.firstOrNull { it.second == period }?.first ?: "monthly"),
                        "next_due" to nextDue, "is_active" to active,
                    ).filterValues { it != null })
                },
                enabled = ready, shape = PillShape, modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(if (member == null) "Add member" else "Save", fontWeight = FontWeight.Bold) }
            if (onRemove != null) TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) { Text("Remove member", color = DangerRed) }
        }
    }
    if (confirmRemove && onRemove != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${member?.name}?") },
            text = { Text("A member who has paid before is paused instead, so your books keep their history.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text("Remove", color = DangerRed) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}
