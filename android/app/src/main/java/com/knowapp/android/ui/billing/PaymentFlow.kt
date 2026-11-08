package com.knowapp.android.ui.billing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
private val Amber = Color(0xFFB45309)

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
    val locked = mode == FlowMode.LOCKED
    val upgrade = status.tier == "free"
    val firstBill = status.state == "trial_ended" || preview
    val amount = kesInt(status.total)
    val commission = status.total * 20 / 100
    val planName = if (upgrade) "Paid plan" else if (status.plan == "owner") "Business plan" else "Individual plan"

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (preview) PreviewTag()

        if (!status.canPay && !preview) {
            Header(Icons.Outlined.Lock, Amber, "Your workspace is paused", "Your business fee hasn't been paid yet. Please ask your owner to pay it. As soon as it's paid, KNOW opens again for everyone.")
            BigButton("Check again") { onRecheck() }
            QuietLink("Sign out", onSignOut)
            return@Column
        }

        when (step) {
            0 -> {
                if (locked) Header(Icons.Outlined.Lock, Amber, "Your workspace is paused", "This month's fee isn't paid yet, so we've paused KNOW for now. Your records are safe. Pay in a minute and carry on.")
                else if (upgrade) Header(Icons.Outlined.Star, Teal, "Upgrade your plan", "Store documents and photos, and print your statements.")
                else Header(Icons.Outlined.Receipt, Teal, if (firstBill) "Your free week is over" else "Time to renew", "You can carry on until 6am tomorrow. After that KNOW pauses until the fee is paid.")
                AmountBox(amount, planName, if (upgrade) "per month" else "every month")
                if (status.proofStatus == "rejected") {
                    Notice("We couldn't confirm your last payment" + (status.proofNote?.let { ": $it" } ?: "") + ". Please check the message you pasted and try again.")
                }
                if (preview) AgentSwitch(previewAgent, onPreviewAgent)
                BigButton(if (locked) "Pay with M-Pesa" else if (upgrade) "Upgrade now" else "Pay now") { onStep(1) }
                if (locked) SecondaryButton("I've already paid") { onStep(2) }
                if (mode == FlowMode.REMINDER) QuietLink("Remind me tomorrow", onLater)
                if (preview) QuietLink("Close preview", onClose)
                if (locked) QuietLink("Sign out", onSignOut)
            }
            1 -> {
                Steps(1)
                Header(null, Teal, "Pay $amount with M-Pesa", "It only takes a minute.")
                HowTo(listOf("Open M-Pesa and choose Lipa na M-Pesa", "Choose Buy Goods and Services", "Enter the till number below", "Enter $amount, then your M-Pesa PIN"))
                TillCard(status.till, status.tillName, amount, onCopyTill = { clipboard.setText(AnnotatedString(status.till)); copied = "Till number copied" }, onCopyAmount = { clipboard.setText(AnnotatedString(status.total.toString())); copied = "Amount copied" })
                copied?.let { Text(it, color = IncomeGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                Text("Check that the name shows as ${status.tillName} before you enter your PIN.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
                BigButton("I've paid") { onStep(2) }
                QuietLink("Back") { onStep(0) }
            }
            2 -> {
                Steps(2)
                Header(null, Teal, "Confirm your payment", "M-Pesa sends you a confirmation message. Paste it here so we can match your payment. It starts with a code like SJK4X9PQLM.")
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it; error = null },
                    label = { Text("M-Pesa confirmation message") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SmallRadius,
                )
                TextButton(onClick = { clipboard.getText()?.text?.let { message = it; error = null } }) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Paste from clipboard", modifier = Modifier.padding(start = 8.dp))
                }
                error?.let { Notice(it) }
                BigButton(if (sending) "Checking…" else "Confirm payment", enabled = !sending && (preview || message.trim().length >= 20)) {
                    if (preview) { onStep(3); return@BigButton }
                    sending = true
                    scope.launch {
                        onSend(message.trim()).onSuccess { onStep(3) }.onFailure { error = it.message }
                        sending = false
                    }
                }
                QuietLink("Back") { onStep(1) }
            }
            else -> {
                Header(Icons.Outlined.CheckCircle, IncomeGreen, "Thank you!", thanks ?: "We're checking your payment. You can keep using KNOW in the meantime.")
                if (preview) {
                    AgentSwitch(previewAgent, onPreviewAgent)
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
                BigButton("Done") { onClose() }
            }
        }
    }
}

@Composable
private fun Header(icon: ImageVector?, tint: Color, title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) {
            Box(modifier = Modifier.size(68.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
            }
        }
        Text(title, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun AmountBox(amount: String, plan: String, caption: String) {
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(plan.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Teal)
        Text(amount, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
        Text(caption, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Steps(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("1  Pay", "2  Confirm").forEachIndexed { index, label ->
            val active = index + 1 == current
            Text(
                label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.background(if (active) Teal else MaterialTheme.colorScheme.surfaceVariant, PillShape).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun HowTo(items: List<String>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(26.dp).background(Teal.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teal)
                }
                Text(text, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun TillCard(till: String, name: String, amount: String, onCopyTill: () -> Unit, onCopyAmount: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Teal, Deep)), RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("TILL NUMBER", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(till, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            }
            TextButton(onClick = onCopyTill) { Text("Copy", color = Mint, fontWeight = FontWeight.Bold) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("BUSINESS NAME", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(name.uppercase(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("AMOUNT", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(amount, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onCopyAmount))
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, color = DangerRed, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().background(DangerRed.copy(alpha = 0.10f), SmallRadius).padding(14.dp))
}

@Composable
private fun PreviewTag() {
    Text(
        "PREVIEW. Nothing is sent or charged.",
        color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.background(Mint.copy(alpha = 0.25f), PillShape).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun AgentSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Pretend an agent registered this account", modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shape = PillShape, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = PillShape, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun QuietLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
