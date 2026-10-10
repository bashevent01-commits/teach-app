package com.knowapp.android.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.knowapp.android.data.model.ReportOut
import com.knowapp.android.data.model.resolveMediaUrl
import com.knowapp.android.ui.components.SkeletonRows
import com.knowapp.android.ui.components.relativeDay
import com.knowapp.android.ui.theme.DangerRed
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var removing by remember { mutableStateOf<ReportOut?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post reports", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !state.showResolved, onClick = { viewModel.showResolved(false) }, label = { Text("Waiting") })
                    FilterChip(selected = state.showResolved, onClick = { viewModel.showResolved(true) }, label = { Text("Resolved") })
                }
            }
            when {
                state.loading && state.reports.isEmpty() -> item { SkeletonRows(count = 3) }
                state.error != null && state.reports.isEmpty() -> item { Text(state.error ?: "", color = DangerRed) }
                state.reports.isEmpty() -> item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        Text(if (state.showResolved) "No resolved reports yet" else "No reports waiting", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                else -> items(state.reports, key = { it.id }) { report ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Reported by ${report.reporterName ?: "a member"} · ${relativeDay(report.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(report.reason?.takeIf { it.isNotBlank() } ?: "No reason given", fontWeight = FontWeight.SemiBold, color = DangerRed)
                        report.post?.let { post ->
                            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(12.dp)) {
                                Text(post.title, fontWeight = FontWeight.Bold)
                                Text(listOfNotNull(post.authorName, post.institutionName).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(post.body, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp), fontSize = 14.sp)
                                resolveMediaUrl(post.imagePath)?.let {
                                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)))
                                }
                            }
                        } ?: Text("This post has already been removed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!state.showResolved) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                                OutlinedButton(onClick = { viewModel.resolve(report, "dismiss") }, shape = PillShape, modifier = Modifier.weight(1f)) { Text("Keep post") }
                                Button(
                                    onClick = { removing = report },
                                    enabled = report.post != null,
                                    shape = PillShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = androidx.compose.ui.graphics.Color.White),
                                    modifier = Modifier.weight(1f),
                                ) { Text("Remove post") }
                            }
                        } else {
                            Text(report.resolution ?: "Resolved", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    removing?.let { report ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this post?") },
            text = { Text("It will disappear from everyone's news feed and can't be brought back.") },
            confirmButton = { TextButton(onClick = { removing = null; viewModel.resolve(report, "remove_post") }) { Text("Remove", color = DangerRed) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
