package com.knowapp.android.ui.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.knowapp.android.data.model.CategoryInsightOut
import com.knowapp.android.data.model.ProductCategoryOut
import com.knowapp.android.ui.components.AppCard
import com.knowapp.android.ui.components.KpiEntry
import com.knowapp.android.ui.components.KpiRow
import com.knowapp.android.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketAnalysisScreen(
    viewModel: MarketAnalysisViewModel,
    onBack: () -> Unit,
    onOpenCategory: (Int) -> Unit,
) {
    val state = viewModel.uiState
    var showManageDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Market") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadManageableCategories()
                        showManageDialog = true
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Manage categories")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
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
                state.categories.isEmpty() -> Text(
                    "No category has reached the 5-institution minimum yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.categories, key = { it.categoryId }) { category ->
                        CategoryInsightRow(category, onClick = { onOpenCategory(category.categoryId) })
                    }
                }
            }
        }
    }

    if (showManageDialog) {
        ManageCategoriesDialog(
            categories = state.manageCategories,
            errorMessage = state.manageError,
            onAdd = viewModel::addCategory,
            onDelete = viewModel::deleteCategory,
            onDismiss = {
                showManageDialog = false
                viewModel.refresh()
            },
        )
    }
}

@Composable
private fun CategoryInsightRow(category: CategoryInsightOut, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(category.categoryName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        KpiRow(
            entries = listOf(
                KpiEntry(value = "${category.institutionCount}", label = "Institutions"),
                KpiEntry(value = "KES ${category.averagePrice}", label = "Avg price"),
                KpiEntry(value = category.totalQuantitySold, label = "Qty sold"),
            ),
            modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
        )
        OutlinedButton(onClick = onClick, shape = PillShape, modifier = Modifier.fillMaxWidth()) {
            Text("View trend & regions")
        }
    }
}

@Composable
private fun ManageCategoriesDialog(
    categories: List<ProductCategoryOut>,
    errorMessage: String?,
    onAdd: (String) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text("Manage categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New category") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { if (newName.isNotBlank()) { onAdd(newName.trim()); newName = "" } },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("Add")
                }
            }
            if (errorMessage != null) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(categories, key = { it.id }) { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(category.name, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { onDelete(category.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${category.name}")
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Done")
            }
        }
    }
}
