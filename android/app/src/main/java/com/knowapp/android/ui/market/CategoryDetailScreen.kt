package com.knowapp.android.ui.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.knowapp.android.data.model.CategoryDetailOut
import com.knowapp.android.data.model.RegionBreakdownEntry
import com.knowapp.android.ui.components.AppCard
import com.knowapp.android.ui.components.KpiEntry
import com.knowapp.android.ui.components.KpiRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    viewModel: CategoryDetailViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.categoryName ?: "Category") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                state.detail != null -> CategoryDetailContent(state.detail)
            }
        }
    }
}

@Composable
private fun CategoryDetailContent(detail: CategoryDetailOut) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                KpiRow(
                    entries = listOf(
                        KpiEntry(value = "${detail.institutionCount}", label = "Institutions"),
                        KpiEntry(value = "KES ${detail.averagePrice}", label = "Avg price"),
                        KpiEntry(value = "KES ${detail.minPrice}–${detail.maxPrice}", label = "Min–Max"),
                        KpiEntry(value = detail.totalQuantitySold, label = "Qty sold"),
                    ),
                )
            }
        }
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("Price trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (detail.trend.isEmpty()) {
                    Text(
                        "Not enough months with 5+ contributing institutions yet to show a trend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    TrendChart(points = detail.trend, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("Regional breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (detail.regionalBreakdown.isEmpty()) {
                    Text(
                        "No region has reached the 5-institution minimum for this category yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        detail.regionalBreakdown.forEach { RegionRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionRow(region: RegionBreakdownEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(region.region, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${region.institutionCount} institutions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("KES ${region.averagePrice}", style = MaterialTheme.typography.bodyMedium)
    }
}
