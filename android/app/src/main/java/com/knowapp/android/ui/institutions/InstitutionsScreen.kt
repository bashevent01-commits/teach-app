package com.knowapp.android.ui.institutions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.knowapp.android.data.KENYA_COUNTIES
import com.knowapp.android.data.model.InstitutionOut
import com.knowapp.android.ui.components.AppCard
import com.knowapp.android.ui.components.Badge
import com.knowapp.android.ui.components.SimpleDropdown
import com.knowapp.android.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstitutionsScreen(
    viewModel: InstitutionsViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.uiState
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Institutions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }, shape = PillShape) {
                Icon(Icons.Filled.Add, contentDescription = "Add institution")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.errorMessage != null -> Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                state.institutions.isEmpty() -> Text(
                    "No institutions onboarded yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.institutions, key = { it.id }) { institution ->
                        InstitutionRow(institution)
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateInstitutionDialog(
            isSubmitting = state.isSubmitting,
            errorMessage = state.submitError,
            onDismiss = { showCreateDialog = false },
            onSubmit = { name, type, address, region ->
                viewModel.create(name, type, address, region) { success ->
                    if (success) showCreateDialog = false
                }
            },
        )
    }
}

@Composable
private fun InstitutionRow(institution: InstitutionOut) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(institution.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Badge(text = institution.type, modifier = Modifier.padding(top = 6.dp))
        institution.region?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        institution.address?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun CreateInstitutionDialog(
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, type: String, address: String?, region: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text("Add institution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Institution name") },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            OutlinedTextField(
                value = type,
                onValueChange = { type = it },
                label = { Text("Type (e.g. school, shop, supermarket)") },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            SimpleDropdown(
                label = "Region (county)",
                options = KENYA_COUNTIES,
                selected = region,
                onSelected = { region = it },
                modifier = Modifier.padding(top = 10.dp),
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address (optional)") },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            if (errorMessage != null) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Button(
                onClick = {
                    if (name.isNotBlank() && type.isNotBlank()) {
                        onSubmit(name.trim(), type.trim(), address.trim().ifBlank { null }, region.ifBlank { null })
                    }
                },
                enabled = !isSubmitting && name.isNotBlank() && type.isNotBlank(),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(if (isSubmitting) "Adding…" else "Add institution")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Cancel")
            }
        }
    }
}
