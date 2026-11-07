package com.knowapp.android.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowapp.android.data.model.BillingStatusOut
import com.knowapp.android.data.model.UpgradeQuoteOut
import com.knowapp.android.ui.components.PASSWORD_RULE_HINT
import com.knowapp.android.ui.components.SimpleDropdown
import com.knowapp.android.ui.components.generatePassword
import com.knowapp.android.ui.components.isStrongPassword
import com.knowapp.android.ui.theme.DangerRed
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.IncomeGreen
import com.knowapp.android.ui.theme.PillShape
import com.knowapp.android.ui.theme.SmallRadius
import kotlinx.coroutines.launch

private val BUSINESS_TYPES = listOf("Shop", "Supermarket", "School", "Gym", "Other")

// An individual who opened another branch becomes a business: details, what is due, then the M-Pesa message
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeBusinessSheet(
    status: BillingStatusOut,
    onQuote: suspend () -> Result<UpgradeQuoteOut>,
    onSubmit: suspend (Map<String, Any?>) -> Result<String>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var quote by remember { mutableStateOf<UpgradeQuoteOut?>(null) }
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Shop") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf(generatePassword()) }
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onQuote().onSuccess { quote = it }.onFailure { error = it.message } }
    val due = quote?.dueNow ?: 500
    val detailsOk = name.trim().length >= 2 && username.trim().length >= 3 && isStrongPassword(password)

    fun send() {
        busy = true
        error = null
        scope.launch {
            val fields = mapOf(
                "business_name" to name.trim(), "business_type" to type.lowercase(), "owner_username" to username.trim(),
                "owner_password" to password, "owner_email" to email.trim().ifBlank { null }, "message" to message.trim().ifBlank { null },
            )
            onSubmit(fields).onSuccess { done = it }.onFailure { error = it.message }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (done != null) {
                Text("Thank you", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = IncomeGreen)
                Text(done ?: "")
                Button(onClick = onDismiss, shape = PillShape, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Done", fontWeight = FontWeight.Bold) }
                return@Column
            }
            Text("Become a business", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(
                "Your sales, stock and history stay exactly as they are. Your current login becomes your first branch, and you get a new owner login to add more branches.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (step == 0) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Business name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
                SimpleDropdown("Business type", BUSINESS_TYPES, type, { type = it })
                OutlinedTextField(value = username, onValueChange = { username = it.trim() }, label = { Text("New owner username") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
                OutlinedTextField(value = password, onValueChange = { password = it.trim() }, label = { Text("New owner password") }, supportingText = { Text(PASSWORD_RULE_HINT) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
                TextButton(onClick = { password = generatePassword() }) { Text("Generate a new password") }
                OutlinedTextField(value = email, onValueChange = { email = it.trim() }, label = { Text("Email (for password resets, optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
                quote?.let { q ->
                    Text(
                        if (q.dueNow == 0) "You are still in your free week, so nothing is due now. The business plan starts at KES ${q.price} a month after it."
                        else "The business plan is KES ${q.price} a month. We credit KES ${q.credit} for the unused days you already paid, so you pay KES ${q.dueNow} now and your next payment is a month from today.",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                error?.let { Text(it, color = DangerRed, fontSize = 13.sp) }
                Button(
                    onClick = { if (due == 0) send() else step = 1 },
                    enabled = detailsOk && quote != null && !busy,
                    shape = PillShape, modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text(if (due == 0) (if (busy) "Setting up…" else "Become a business") else "Continue to payment", fontWeight = FontWeight.Bold) }
            } else {
                Text("Pay KES $due with M-Pesa paybill ${status.paybill}, account name ${status.paybillName}, account number ${status.accountRef}.", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = message, onValueChange = { message = it; error = null }, label = { Text("Paste the M-Pesa message") }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
                error?.let { Text(it, color = DangerRed, fontSize = 13.sp) }
                Button(onClick = { send() }, enabled = message.trim().length >= 20 && !busy, shape = PillShape, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (busy) "Sending…" else "Send message", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { step = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
        }
    }
}
