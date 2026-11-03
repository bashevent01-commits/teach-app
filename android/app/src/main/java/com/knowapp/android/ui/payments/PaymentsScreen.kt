package com.knowapp.android.ui.payments

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowapp.android.data.model.PaymentProofRow
import com.knowapp.android.data.model.PayoutRequestRow
import com.knowapp.android.ui.components.AppCard
import com.knowapp.android.ui.components.kes
import com.knowapp.android.ui.theme.DangerRed
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.PillShape
import com.knowapp.android.ui.theme.SmallRadius

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(viewModel: PaymentsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(0) }
    var approving by remember { mutableStateOf<PaymentProofRow?>(null) }
    var declining by remember { mutableStateOf<PaymentProofRow?>(null) }
    var paying by remember { mutableStateOf<PayoutRequestRow?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payments", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Payment messages (${state.proofs.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Agent payouts (${state.requests.size})") })
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    tab == 0 && state.proofs.isEmpty() -> Text("No payment messages are waiting.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                    tab == 1 && state.requests.isEmpty() -> Text("No payout requests are waiting.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                    tab == 0 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(state.proofs, key = { it.id }) { p -> ProofCard(p, onApprove = { approving = p }, onDecline = { declining = p }) }
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(state.requests, key = { it.id }) { r -> RequestCard(r, onPay = { paying = r }, onDecline = { viewModel.rejectRequest(r.id) }) }
                    }
                }
            }
        }
    }

    approving?.let { p ->
        AlertDialog(
            onDismissRequest = { approving = null },
            title = { Text("Approve this payment?") },
            text = { Text("Check that M-Pesa code ${p.code} for ${kes(p.amount)} is on the paybill statement first. Approving books ${p.months} month(s) for ${p.business}${if (p.agent != null) " and tells ${p.agent} they can ask for a payout" else ""}.") },
            confirmButton = { TextButton(onClick = { approving = null; viewModel.approve(p.id) }) { Text("Approve") } },
            dismissButton = { TextButton(onClick = { approving = null }) { Text("Cancel") } },
        )
    }
    declining?.let { p ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { declining = null },
            title = { Text("Decline this message?") },
            text = {
                Column {
                    Text("${p.business} is told it could not be confirmed and the app closes at 6am if they do not pay again.", fontSize = 13.sp)
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Reason (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = SmallRadius)
                }
            },
            confirmButton = { TextButton(onClick = { declining = null; viewModel.reject(p.id, note) }) { Text("Decline", color = DangerRed) } },
            dismissButton = { TextButton(onClick = { declining = null }) { Text("Cancel") } },
        )
    }
    paying?.let { r ->
        var reference by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("Mark ${r.agent}'s payout as paid") },
            text = {
                Column {
                    Text("Send ${kes(r.amount)} to ${r.phone ?: "the agent"} first, then record it here.", fontSize = 13.sp)
                    OutlinedTextField(value = reference, onValueChange = { reference = it }, label = { Text("M-Pesa code (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = SmallRadius)
                }
            },
            confirmButton = { TextButton(onClick = { paying = null; viewModel.pay(r.id, reference) }) { Text("Mark paid") } },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProofCard(p: PaymentProofRow, onApprove: () -> Unit, onDecline: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(p.business, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Sent by ${p.sender ?: "unknown"}${p.senderUsername?.let { " (@$it)" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Paid ${kes(p.amount)}", fontWeight = FontWeight.SemiBold)
            Text("Due ${kes(p.amountDue)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Code ${p.code}${if (p.agent != null) " · Agent ${p.agent}" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(p.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onApprove, shape = PillShape, modifier = Modifier.weight(1f)) { Text("Approve") }
            OutlinedButton(onClick = onDecline, shape = PillShape, modifier = Modifier.weight(1f)) { Text("Decline", color = DangerRed) }
        }
    }
}

@Composable
private fun RequestCard(r: PayoutRequestRow, onPay: () -> Unit, onDecline: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(r.agent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Code ${r.code}${r.phone?.let { " · $it" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Asking for ${kes(r.amount)}", fontWeight = FontWeight.SemiBold)
            Text("Owed ${kes(r.balanceDue)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPay, shape = PillShape, modifier = Modifier.weight(1f)) { Text("Mark paid") }
            OutlinedButton(onClick = onDecline, shape = PillShape, modifier = Modifier.weight(1f)) { Text("Decline", color = DangerRed) }
        }
    }
}
