package com.knowapp.android.ui.partners

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
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowapp.android.data.model.PartnerSummaryOut
import com.knowapp.android.ui.components.AppCard
import com.knowapp.android.ui.components.Badge
import com.knowapp.android.ui.components.KpiEntry
import com.knowapp.android.ui.components.KpiRow
import com.knowapp.android.ui.components.PASSWORD_RULE_HINT
import com.knowapp.android.ui.components.generatePassword
import com.knowapp.android.ui.components.isStrongPassword
import com.knowapp.android.ui.theme.DangerRed
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.IncomeGreen
import com.knowapp.android.ui.theme.PillShape
import com.knowapp.android.ui.theme.SmallRadius

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnersScreen(viewModel: PartnersViewModel, onOpenPayments: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<Int?>(null) }
    val open = state.partners.firstOrNull { it.id == openId }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Partners", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenPayments) { Icon(Icons.Outlined.Receipt, contentDescription = "Payments to check") }
                    IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add partner") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.partners.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.partners.isEmpty() -> Column(modifier = Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No partners yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("A partner sells K.N.O.W. and earns a share of each monthly fee.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    Button(onClick = { adding = true }, shape = PillShape) { Text("Add the first partner") }
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(state.partners, key = { it.id }) { p -> PartnerCard(p) { openId = p.id } }
                }
            }
        }
    }

    if (adding) AddPartnerSheet(onDismiss = { adding = false }, onCreate = { n, u, e, ph, pw, r, cb -> viewModel.create(n, u, e, ph, pw, r) { ok -> cb(ok); if (ok) adding = false } })
    if (open != null) PartnerSheet(open, onDismiss = { openId = null }, viewModel = viewModel)
}

@Composable
private fun PartnerCard(p: PartnerSummaryOut, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Code ${p.code}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            if (p.isActive) Badge("Active") else Text("Off", color = DangerRed, fontWeight = FontWeight.SemiBold)
        }
        KpiRow(
            entries = listOf(
                KpiEntry("${p.businesses}", "Businesses"),
                KpiEntry("${p.commissionRate}%", "Rate"),
                KpiEntry("KES ${p.balanceDue}", "Owed"),
            ),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartnerSheet(p: PartnerSummaryOut, onDismiss: () -> Unit, viewModel: PartnersViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    var editingRate by remember { mutableStateOf(false) }
    var confirmCode by remember { mutableStateOf(false) }
    var confirmToggle by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(p.name, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("@${p.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Line("Partner code", p.code)
                Line("Commission rate", "${p.commissionRate}%")
                Line("Businesses", "${p.businesses}")
                Line("Earned so far", "KES ${p.earnedTotal}")
                Line("Paid out", "KES ${p.paidOut}")
                Line("Still owed", "KES ${p.balanceDue}")
                Line("Email", p.email ?: "None")
                Line("Phone", p.phone ?: "None")
            }
            Button(onClick = { clipboard.setText(AnnotatedString(p.code)) }, shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Copy partner code", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = { editingRate = true }, shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Change commission rate", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(onClick = { confirmCode = true }, shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Make a new code", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(onClick = { confirmToggle = true }, shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (p.isActive) "Switch partner off" else "Switch partner on", color = if (p.isActive) DangerRed else IncomeGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (editingRate) {
        var rate by remember { mutableStateOf(p.commissionRate) }
        val valid = rate.toDoubleOrNull()?.let { it in 0.0..100.0 } == true
        AlertDialog(
            onDismissRequest = { editingRate = false },
            title = { Text("Commission rate") },
            text = {
                Column {
                    OutlinedTextField(value = rate, onValueChange = { rate = it.trim() }, singleLine = true, label = { Text("Percent") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = SmallRadius)
                    Text("Applies to fees paid from now on. Earlier commissions keep the rate they were earned at.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(enabled = valid, onClick = { editingRate = false; viewModel.setRate(p.id, rate) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingRate = false }) { Text("Cancel") } },
        )
    }
    if (confirmCode) {
        AlertDialog(
            onDismissRequest = { confirmCode = false },
            title = { Text("Make a new code?") },
            text = { Text("The old code stops working for new sign-ups. Businesses already linked stay with ${p.name}.") },
            confirmButton = { TextButton(onClick = { confirmCode = false; viewModel.newCode(p.id) }) { Text("Make new code") } },
            dismissButton = { TextButton(onClick = { confirmCode = false }) { Text("Cancel") } },
        )
    }
    if (confirmToggle) {
        AlertDialog(
            onDismissRequest = { confirmToggle = false },
            title = { Text(if (p.isActive) "Switch off ${p.name}?" else "Switch on ${p.name}?") },
            text = { Text(if (p.isActive) "They are signed out and can't sign in until you switch them on again." else "They can sign in again.") },
            confirmButton = { TextButton(onClick = { confirmToggle = false; viewModel.setActive(p.id, !p.isActive) }) { Text(if (p.isActive) "Switch off" else "Switch on", color = if (p.isActive) DangerRed else IncomeGreen) } },
            dismissButton = { TextButton(onClick = { confirmToggle = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPartnerSheet(onDismiss: () -> Unit, onCreate: (String, String, String, String, String, String, (Boolean) -> Unit) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf(generatePassword()) }
    var rate by remember { mutableStateOf("20") }
    var busy by remember { mutableStateOf(false) }
    val rateOk = rate.toDoubleOrNull()?.let { it in 0.0..100.0 } == true
    val ready = name.trim().length >= 2 && username.trim().length >= 3 && email.contains("@") && rateOk && isStrongPassword(password) && !busy

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add a partner", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            OutlinedTextField(value = username, onValueChange = { username = it.trim() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            OutlinedTextField(value = email, onValueChange = { email = it.trim() }, label = { Text("Email (for password resets)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            OutlinedTextField(value = rate, onValueChange = { rate = it.trim() }, label = { Text("Commission rate (%)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            OutlinedTextField(value = password, onValueChange = { password = it.trim() }, label = { Text("Password") }, supportingText = { Text(PASSWORD_RULE_HINT) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            TextButton(onClick = { password = generatePassword() }) { Text("Generate a new password") }
            Button(
                onClick = { busy = true; onCreate(name.trim(), username.trim(), email.trim(), phone.trim(), password, rate) { busy = false } },
                enabled = ready,
                shape = PillShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (busy) "Adding…" else "Add partner", fontWeight = FontWeight.Bold) }
        }
    }
}
