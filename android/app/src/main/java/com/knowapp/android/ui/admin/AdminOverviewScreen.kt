package com.knowapp.android.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowapp.android.data.model.ActivityItemOut
import com.knowapp.android.ui.components.SkeletonRows
import com.knowapp.android.ui.components.lastSeen
import com.knowapp.android.ui.components.relativeDay
import com.knowapp.android.ui.theme.DangerRed
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.ExpenseOrange
import com.knowapp.android.ui.theme.IncomeGreen
import com.knowapp.android.ui.theme.WarnAmber

private fun describe(a: ActivityItemOut): String {
    val who = a.actorUsername ?: "Someone"
    return when (a.action) {
        "login_success" -> "$who signed in"
        "login_failed" -> "Failed sign-in for $who"
        "account_locked" -> "$who's account was locked"
        "login_blocked_locked" -> "Blocked: $who is locked out"
        "login_blocked_inactive" -> "Blocked: $who is deactivated"
        else -> a.action.replace('_', ' ').replaceFirstChar { it.uppercase() } + (a.detail?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
    }
}

private fun isWarning(action: String) = action.contains("failed") || action.contains("locked") || action.contains("blocked")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminOverviewScreen(
    viewModel: AdminOverviewViewModel,
    onOpenReports: () -> Unit,
    onOpenInstitutions: () -> Unit,
    onOpenAccounts: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val data = state.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Overview", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && data != null, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            ) {
                if (data == null) {
                    item {
                        if (state.error != null) {
                            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.error ?: "", color = DangerRed)
                                TextButton(onClick = viewModel::refresh) { Text("Try again") }
                            }
                        } else SkeletonRows(count = 4)
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatTile("Institutions", "${data.institutions}", "on the platform", MaterialTheme.colorScheme.primary, Modifier.weight(1f), onOpenInstitutions)
                                StatTile("Accounts", "${data.accountsActive}", "active of ${data.accountsTotal}", IncomeGreen, Modifier.weight(1f), onOpenAccounts)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatTile("Entries", "${data.entries7d}", "recorded this week", ExpenseOrange, Modifier.weight(1f), null)
                                StatTile(
                                    "Reports",
                                    "${data.openReports}",
                                    if (data.openReports == 0) "nothing waiting" else "need review",
                                    if (data.openReports == 0) IncomeGreen else DangerRed,
                                    Modifier.weight(1f),
                                    onOpenReports,
                                )
                            }
                        }
                    }

                    item {
                        Card("Needs attention") {
                            val nothing = data.openReports == 0 && data.quietInstitutions.isEmpty() && data.accountsInactive == 0
                            if (nothing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = IncomeGreen)
                                    Text("All clear. Nothing needs you right now.", modifier = Modifier.padding(start = 10.dp))
                                }
                            }
                            if (data.openReports > 0) {
                                AttentionRow(Icons.Outlined.Flag, DangerRed, "${data.openReports} ${if (data.openReports == 1) "post report" else "post reports"} waiting", "Review and decide", onOpenReports)
                            }
                            if (data.accountsInactive > 0) {
                                AttentionRow(Icons.Outlined.WarningAmber, WarnAmber, "${data.accountsInactive} deactivated ${if (data.accountsInactive == 1) "account" else "accounts"}", "See who is switched off", onOpenAccounts)
                            }
                            data.quietInstitutions.take(4).forEach { q ->
                                AttentionRow(Icons.Outlined.History, WarnAmber, "${q.name} has gone quiet", lastSeen(q.lastActivityAt), onOpenInstitutions)
                            }
                        }
                    }

                    item {
                        Card("Recent activity") {
                            if (data.recentActivity.isEmpty()) Text("Nothing yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            data.recentActivity.forEachIndexed { index, a ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                val warn = isWarning(a.action)
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(8.dp).background(if (warn) DangerRed else IncomeGreen, CircleShape),
                                    )
                                    Text(describe(a), modifier = Modifier.weight(1f).padding(horizontal = 12.dp), fontSize = 14.sp, maxLines = 2)
                                    Text(relativeDay(a.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, accent: Color, modifier: Modifier, onClick: (() -> Unit)?) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
    ) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = accent, modifier = Modifier.padding(top = 4.dp))
        Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Card(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun AttentionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(38.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
