package com.knowapp.android.ui.billing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.knowapp.android.AppContainer
import com.knowapp.android.data.BillingSignal
import com.knowapp.android.data.model.BillingStatusOut
import com.knowapp.android.ui.theme.DangerRed
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.IncomeGreen
import com.knowapp.android.ui.theme.PillShape
import com.knowapp.android.ui.theme.SmallRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

enum class FlowMode { REMINDER, LOCKED, PAY, PREVIEW }

private val Deep = Color(0xFF134E4A)
private val Teal = Color(0xFF0F766E)
private val Mint = Color(0xFF5EEAD4)

private fun today(): String = LocalDate.now(ZoneId.of("Africa/Nairobi")).toString()

// Wakes up exactly at the 4pm reminder and the 6am closing, and checks every few minutes otherwise
private fun nextCheckMillis(status: BillingStatusOut?): Long {
    val now = OffsetDateTime.now()
    val next = listOfNotNull(status?.popupFrom, status?.lockAt)
        .mapNotNull { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
        .filter { it.isAfter(now) }
        .minOrNull() ?: return 300_000L
    val wait = java.time.Duration.between(now, next).toMillis() + 2_000L
    return wait.coerceIn(5_000L, 300_000L)
}

private fun kesInt(v: Int) = "KES " + String.format(java.util.Locale.US, "%,d", v)

// Sits on top of the whole app: the 4pm reminder, the closed screen, the pay steps and the owner-only preview
@Composable
fun BillingOverlay(container: AppContainer) {
    val session by container.sessionStore.session.collectAsState()
    val gate by container.billingGate.state.collectAsState()
    val signal by BillingSignal.closed.collectAsState()
    val scope = rememberCoroutineScope()
    val userId = session?.userId
    val relevant = session?.role == "staff" || session?.role == "institution_admin"
    val upgradeAsk by com.knowapp.android.data.PlanState.upgradeRequest.collectAsState()
    LaunchedEffect(upgradeAsk) { if (upgradeAsk != 0L) container.billingGate.openPay() }
    var step by remember { mutableStateOf(0) }
    var thanks by remember { mutableStateOf<String?>(null) }
    var lastMode by remember { mutableStateOf<FlowMode?>(null) }

    LaunchedEffect(userId, relevant, signal) {
        if (userId == null || !relevant) {
            container.billingGate.reset()
            return@LaunchedEffect
        }
        while (true) {
            container.billingGate.refresh(userId)
            delay(nextCheckMillis(container.billingGate.state.value.status))
        }
    }

    val status = gate.status
    val mode: FlowMode? = when {
        !relevant || userId == null -> null
        gate.preview -> FlowMode.PREVIEW
        status?.locked == true -> FlowMode.LOCKED
        gate.payOpen || thanks != null -> FlowMode.PAY
        status?.popupDue == true && gate.dismissedDay != today() -> FlowMode.REMINDER
        else -> null
    }

    LaunchedEffect(mode) {
        if (lastMode == null && mode != null) step = if (mode == FlowMode.PAY) 1 else 0
        if (mode == null) { step = 0; thanks = null }
        lastMode = mode
    }

    if (mode == null || userId == null) return
    val shown = if (mode == FlowMode.PREVIEW) {
        val base = status ?: BillingStatusOut(applies = true, plan = "owner", total = 500, accountRef = "obsidian", canPay = true)
        base.copy(total = if (base.total > 0) base.total else 500, canPay = true, popupDue = true, locked = false)
    } else status ?: return

    val flow: @Composable () -> Unit = {
        PaymentFlow(
            mode = mode,
            status = shown,
            step = step,
            onStep = { step = it },
            thanks = thanks,
            previewAgent = gate.previewAgent,
            onPreviewAgent = container.billingGate::setPreviewAgent,
            onLater = { container.billingGate.remindTomorrow(userId) },
            onClose = {
                thanks = null
                if (mode == FlowMode.PREVIEW) container.billingGate.endPreview() else container.billingGate.closePay()
            },
            onSend = { text -> container.billingGate.sendProof(text, userId).onSuccess { thanks = it } },
            onRecheck = { scope.launch { container.billingGate.refresh(userId) } },
            onSignOut = { scope.launch { container.authRepository.logout() } },
        )
    }

    if (mode == FlowMode.LOCKED) {
        BackHandler(enabled = true) {}
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { flow() }
    } else {
        Dialog(
            onDismissRequest = { if (mode == FlowMode.REMINDER) container.billingGate.remindTomorrow(userId) else { thanks = null; if (mode == FlowMode.PREVIEW) container.billingGate.endPreview() else container.billingGate.closePay() } },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            Surface(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(14.dp).heightIn(max = 680.dp)) { flow() }
        }
    }
}

@Composable
fun PaymentFlow(
    mode: FlowMode,
    status: BillingStatusOut,
    step: Int,
    onStep: (Int) -> Unit,
    thanks: String?,
    previewAgent: Boolean,
    onPreviewAgent: (Boolean) -> Unit,
    onLater: () -> Unit,
    onClose: () -> Unit,
    onSend: suspend (String) -> Result<String>,
    onRecheck: () -> Unit,
    onSignOut: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<String?>(null) }
    val preview = mode == FlowMode.PREVIEW
    val firstBill = status.state == "trial_ended" || preview
    val commission = status.total * 20 / 100

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (preview) {
            Text(
                "PREVIEW. Nothing is sent or charged.",
                color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                modifier = Modifier.background(Mint.copy(alpha = 0.25f), PillShape).padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }

        if (!status.canPay && !preview) {
            Text("This workspace is closed", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("The monthly fee has not been paid. Ask your owner to pay it, and the app opens again for everyone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRecheck, shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Check again", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onSignOut, shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Sign out") }
            return@Column
        }

        when (step) {
            0 -> {
                Text(
                    if (mode == FlowMode.LOCKED) "Your KNOW fee is overdue" else if (firstBill) "Your free week has ended" else "Your KNOW fee is due",
                    fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                )
                Text("${kesInt(status.total)} a month", fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = Teal)
                Text(
                    if (mode == FlowMode.LOCKED) "The app is closed until the fee is paid. Pay, then paste your M-Pesa message and it opens again straight away."
                    else "Pay to keep KNOW running for your business. You can keep using the app until 6am tomorrow. After that it closes until the fee is paid.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (status.proofStatus == "rejected") {
                    Text(
                        "Your last payment message could not be confirmed" + (status.proofNote?.let { ": $it." } ?: "."),
                        color = DangerRed, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().background(DangerRed.copy(alpha = 0.12f), SmallRadius).padding(12.dp),
                    )
                }
                if (preview) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pretend an agent registered this account", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Switch(checked = previewAgent, onCheckedChange = onPreviewAgent)
                    }
                }
                Button(onClick = { onStep(1) }, shape = PillShape, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Pay now", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                if (mode == FlowMode.REMINDER) TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text("Remind me later") }
                if (preview) TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close preview") }
                if (mode == FlowMode.LOCKED) TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            }
            1 -> {
                Text("Pay with M-Pesa", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("On your phone: M-Pesa, Lipa na M-Pesa, Pay Bill. Then enter these details.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(
                    modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Teal, Deep)), RoundedCornerShape(22.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PayRow("Paybill number", status.paybill) { clipboard.setText(AnnotatedString(status.paybill)); copied = "Paybill copied" }
                    PayRow("Account name", status.paybillName, null)
                    PayRow("Account number", status.accountRef.ifBlank { "your username" }) { clipboard.setText(AnnotatedString(status.accountRef)); copied = "Account number copied" }
                    PayRow("Amount", kesInt(status.total)) { clipboard.setText(AnnotatedString(status.total.toString())); copied = "Amount copied" }
                }
                copied?.let { Text(it, color = IncomeGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                Button(onClick = { onStep(2) }, shape = PillShape, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("I have paid", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                TextButton(onClick = { onStep(0) }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
            2 -> {
                Text("Show us your payment", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Paste the whole M-Pesa message you got from Safaricom. The app stays open while we check it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it; error = null },
                    label = { Text("M-Pesa message") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SmallRadius,
                )
                error?.let {
                    Text(it, color = DangerRed, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(DangerRed.copy(alpha = 0.12f), SmallRadius).padding(12.dp))
                }
                Button(
                    onClick = {
                        if (preview) { onStep(3); return@Button }
                        sending = true
                        scope.launch {
                            onSend(message.trim())
                                .onSuccess { onStep(3) }
                                .onFailure { error = it.message }
                            sending = false
                        }
                    },
                    enabled = !sending && (preview || message.trim().length >= 20),
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text(if (sending) "Sending…" else if (preview) "Send (preview only)" else "Send message", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                TextButton(onClick = { onStep(1) }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
            else -> {
                Text("Thank you", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = IncomeGreen)
                Text(thanks ?: "Your payment message is being checked and the app stays open.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (preview) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pretend an agent registered this account", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Switch(checked = previewAgent, onCheckedChange = onPreviewAgent)
                    }
                    if (previewAgent) {
                        Column(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("WHAT THE AGENT SEES", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Teal)
                            Text("Your business paid ${kesInt(status.total)}. You earned ${kesInt(commission)} and can ask for a payout of that amount.")
                            Button(onClick = {}, enabled = false, shape = PillShape, modifier = Modifier.fillMaxWidth()) { Text("Ask for payout of ${kesInt(commission)}") }
                        }
                    }
                }
                Button(onClick = onClose, shape = PillShape, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Done", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun PayRow(label: String, value: String, onCopy: (() -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label.uppercase(), color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (onCopy != null) TextButton(onClick = onCopy) { Text("Copy", color = Mint, fontWeight = FontWeight.Bold) }
    }
}
