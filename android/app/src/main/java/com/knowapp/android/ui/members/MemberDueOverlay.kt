package com.knowapp.android.ui.members

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.knowapp.android.AppContainer
import com.knowapp.android.data.NavRequests
import com.knowapp.android.data.model.DueMembersOut
import com.knowapp.android.ui.components.kes
import com.knowapp.android.ui.navigation.Routes
import java.time.LocalDate
import java.time.ZoneId

// Once a day, the first time the gym owner opens the app: who pays tomorrow and who pays today
@Composable
fun MemberDueOverlay(container: AppContainer) {
    val session by container.sessionStore.session.collectAsState()
    val context = LocalContext.current
    var due by remember { mutableStateOf<DueMembersOut?>(null) }
    val userId = session?.userId

    LaunchedEffect(userId, session?.institutionType) {
        val s = session ?: return@LaunchedEffect
        if (s.role != "institution_admin" || s.institutionType != "gym") return@LaunchedEffect
        val prefs = context.getSharedPreferences("know_member_due", Context.MODE_PRIVATE)
        val day = LocalDate.now(ZoneId.of("Africa/Nairobi")).toString()
        if (prefs.getString("dialog_${s.userId}", "") == day) return@LaunchedEffect
        val result = container.membersRepository.due().getOrNull() ?: return@LaunchedEffect
        if (result.today.isNotEmpty() || result.tomorrow.isNotEmpty()) {
            due = result
            prefs.edit().putString("dialog_${s.userId}", day).apply()
        }
    }

    val shown = due ?: return
    AlertDialog(
        onDismissRequest = { due = null },
        title = { Text("Members to chase") },
        text = {
            Column {
                @Composable
                fun block(label: String, list: List<com.knowapp.android.data.model.MemberOut>) {
                    if (list.isEmpty()) return
                    Text("$label (${list.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    list.take(8).forEach { Text("${it.name} · ${kes(it.fee)}") }
                    if (list.size > 8) Text("and ${list.size - 8} more")
                }
                block("Due today", shown.today)
                block("Due tomorrow", shown.tomorrow)
            }
        },
        confirmButton = { TextButton(onClick = { due = null; NavRequests.route.value = Routes.MEMBERS }) { Text("Open members") } },
        dismissButton = { TextButton(onClick = { due = null }) { Text("Close") } },
    )
}
