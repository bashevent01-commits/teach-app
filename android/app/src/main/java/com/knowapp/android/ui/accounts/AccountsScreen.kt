package com.knowapp.android.ui.accounts

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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.knowapp.android.data.model.UserOut
import com.knowapp.android.ui.components.AppCard
import com.knowapp.android.ui.components.Badge
import com.knowapp.android.ui.components.SimpleDropdown
import com.knowapp.android.ui.theme.PillShape

private val ROLE_OPTIONS = listOf("staff", "institution_admin", "super_admin")
private val STAFF_TYPE_OPTIONS = listOf("general", "teacher")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.uiState
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
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
                Icon(Icons.Filled.Add, contentDescription = "Add account")
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
                state.users.isEmpty() -> Text(
                    "No accounts yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.users, key = { it.id }) { user ->
                        AccountRow(user, onToggleActive = { viewModel.setActive(user.id, !user.isActive) })
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateAccountDialog(
            state = state,
            onDismiss = { showCreateDialog = false },
            onSubmit = { username, fullName, password, role, staffType, institutionId ->
                viewModel.create(username, fullName, password, role, staffType, institutionId) { success ->
                    if (success) showCreateDialog = false
                }
            },
        )
    }
}

@Composable
private fun AccountRow(user: UserOut, onToggleActive: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(user.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "@${user.username}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Badge(text = roleLabel(user.role, user.staffType), modifier = Modifier.padding(top = 8.dp))
        if (!user.isActive) {
            Badge(text = "Deactivated", modifier = Modifier.padding(top = 6.dp))
        }
        OutlinedButton(
            onClick = onToggleActive,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text(if (user.isActive) "Deactivate" else "Reactivate")
        }
    }
}

private fun roleLabel(role: String, staffType: String?): String = when (role) {
    "super_admin" -> "Super admin"
    "institution_admin" -> "Institution admin"
    "staff" -> if (staffType == "teacher") "Staff · Teacher" else "Staff"
    else -> role
}

@Composable
private fun CreateAccountDialog(
    state: AccountsUiState,
    onDismiss: () -> Unit,
    onSubmit: (username: String, fullName: String, password: String, role: String, staffType: String?, institutionId: Int?) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("staff") }
    var staffType by remember { mutableStateOf("general") }
    var institutionLabel by remember { mutableStateOf("") }

    val institutionOptions = state.institutions.map { it.name }
    val selectedInstitutionId = state.institutions.firstOrNull { it.name == institutionLabel }?.id

    Dialog(onDismissRequest = onDismiss) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text("Add account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full name") },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Temporary password (8+ characters)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            SimpleDropdown(
                label = "Role",
                options = ROLE_OPTIONS,
                selected = role,
                onSelected = { role = it },
                modifier = Modifier.padding(top = 10.dp),
            )
            if (role == "staff") {
                SimpleDropdown(
                    label = "Staff type",
                    options = STAFF_TYPE_OPTIONS,
                    selected = staffType,
                    onSelected = { staffType = it },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            // Only super admins choose an institution — an institution_admin's
            // new accounts are silently pinned to their own institution
            // server-side (see backend/app/routers/users.py's create_user).
            if (state.isSuperAdmin && role != "super_admin") {
                SimpleDropdown(
                    label = "Institution",
                    options = institutionOptions,
                    selected = institutionLabel,
                    onSelected = { institutionLabel = it },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (state.submitError != null) {
                Text(
                    state.submitError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            val needsInstitution = state.isSuperAdmin && role != "super_admin"
            val canSubmit = username.length >= 3 && fullName.isNotBlank() && password.length >= 8 &&
                (!needsInstitution || selectedInstitutionId != null)
            Button(
                onClick = {
                    onSubmit(
                        username.trim(),
                        fullName.trim(),
                        password,
                        role,
                        if (role == "staff") staffType else null,
                        selectedInstitutionId,
                    )
                },
                enabled = !state.isSubmitting && canSubmit,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(if (state.isSubmitting) "Adding…" else "Add account")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Cancel")
            }
        }
    }
}
