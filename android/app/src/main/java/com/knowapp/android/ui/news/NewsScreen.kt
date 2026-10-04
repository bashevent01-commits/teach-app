package com.knowapp.android.ui.news

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.knowapp.android.data.model.PostOut
import com.knowapp.android.data.model.resolveMediaUrl
import com.knowapp.android.ui.components.relativeDay
import com.knowapp.android.ui.theme.DangerRed
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.PillShape
import com.knowapp.android.ui.theme.SmallRadius

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(viewModel: NewsViewModel, onBack: () -> Unit, showBack: Boolean = true) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var composing by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf<PostOut?>(null) }
    var deleting by remember { mutableStateOf<PostOut?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("News", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (viewModel.canPost) {
                ExtendedFloatingActionButton(
                    onClick = { composing = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Post") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            when {
                state.loading && state.posts.isEmpty() -> item { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                state.error != null && state.posts.isEmpty() -> item {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "", color = DangerRed)
                        TextButton(onClick = viewModel::refresh) { Text("Try again") }
                    }
                }
                state.posts.isEmpty() -> item {
                    Column(
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(18.dp)).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.Article, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(34.dp))
                        Text("No news yet", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                        if (viewModel.canPost) Text("Post the first update for your institution.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                else -> items(state.posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        mine = post.authorId == viewModel.userId,
                        canAct = viewModel.canPost,
                        onDelete = { deleting = post },
                        onReport = { reporting = post },
                    )
                }
            }
        }
    }

    if (composing) {
        ComposeSheet(
            posting = state.posting,
            onDismiss = { composing = false },
            onPublish = { title, body, photo -> viewModel.publish(title, body, photo) { ok -> if (ok) composing = false } },
        )
    }

    reporting?.let { post ->
        ReportSheet(
            onDismiss = { reporting = null },
            onSend = { reason -> viewModel.report(post, reason) { reporting = null } },
        )
    }

    deleting?.let { post ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this post?") },
            text = { Text(post.title) },
            confirmButton = { TextButton(onClick = { deleting = null; viewModel.delete(post) }) { Text("Delete", color = DangerRed) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PostCard(post: PostOut, mine: Boolean, canAct: Boolean, onDelete: () -> Unit, onReport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Text(post.title, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(relativeDay(post.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        Text(post.body, style = MaterialTheme.typography.bodyMedium)
        resolveMediaUrl(post.imagePath)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = "Photo attached to ${post.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)),
            )
        }
        if (canAct) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                if (mine) TextButton(onClick = onDelete) { Text("Delete", color = DangerRed) }
                else TextButton(onClick = onReport) { Text("Report") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeSheet(posting: Boolean, onDismiss: () -> Unit, onPublish: (String, String, Uri?) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) photo = uri }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("New post", style = MaterialTheme.typography.titleLarge, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("What's the update?") }, minLines = 4, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = "Selected photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)))
                TextButton(onClick = { photo = null }) { Text("Remove photo") }
            } else {
                OutlinedButton(onClick = { picker.launch("image/*") }, shape = PillShape, modifier = Modifier.fillMaxWidth()) { Text("Add a photo (optional)") }
            }
            Button(onClick = { onPublish(title, body, photo) }, enabled = !posting, shape = PillShape, modifier = Modifier.fillMaxWidth()) {
                Text(if (posting) "Publishing…" else "Publish", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reason by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Report post", style = MaterialTheme.typography.titleLarge, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("What's wrong with this post? (optional)") }, minLines = 3, modifier = Modifier.fillMaxWidth(), shape = SmallRadius)
            Button(onClick = { onSend(reason) }, shape = PillShape, modifier = Modifier.fillMaxWidth()) { Text("Send report", fontWeight = FontWeight.SemiBold) }
        }
    }
}
