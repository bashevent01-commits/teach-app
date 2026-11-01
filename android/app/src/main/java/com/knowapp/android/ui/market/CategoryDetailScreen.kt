package com.knowapp.android.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowapp.android.data.model.CategoryDetailOut
import com.knowapp.android.data.model.RegionBreakdownEntry
import com.knowapp.android.ui.components.AppCard
import com.knowapp.android.ui.theme.Brand
import com.knowapp.android.ui.theme.BrandSoft
import com.knowapp.android.ui.theme.DisplayFontFamily
import com.knowapp.android.ui.theme.ExpenseOrange
import com.knowapp.android.ui.theme.IncomeGreen
import com.knowapp.android.ui.theme.PillShape
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    viewModel: CategoryDetailViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.uiState
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.categoryName ?: "Category", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state.detail != null) IconButton(onClick = { MarketReportPdf.print(context, state.detail, state.isDemo) }) {
                        Icon(Icons.Outlined.Download, contentDescription = "Download report")
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
                state.detail != null -> CategoryDetailContent(state.detail, state.isDemo) { MarketReportPdf.print(context, state.detail, state.isDemo) }
            }
        }
    }
}

@Composable
private fun CategoryDetailContent(detail: CategoryDetailOut, isDemo: Boolean, onDownload: () -> Unit) {
    val first = detail.trend.firstOrNull()?.averagePrice
    val last = detail.trend.lastOrNull()?.averagePrice
    val change = if (first != null && last != null && first > 0.0 && detail.trend.size > 1) (last - first) / first * 100.0 else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AVERAGE PRICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    if (isDemo) Text("DEMO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Brand, modifier = Modifier.background(BrandSoft, PillShape).padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Text(kesText(detail.averagePrice), fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, modifier = Modifier.padding(top = 4.dp))
                if (change != null) {
                    val up = change >= 0
                    Text(
                        (if (up) "Up " else "Down ") + String.format(Locale.US, "%.1f%%", Math.abs(change)) + " since " + detail.trend.first().month,
                        color = if (up) ExpenseOrange else IncomeGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("Institutions", "${detail.institutionCount}", Modifier.weight(1f))
                        Tile("Quantity sold", detail.totalQuantitySold, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("Lowest", kesText(detail.minPrice), Modifier.weight(1f))
                        Tile("Highest", kesText(detail.maxPrice), Modifier.weight(1f))
                    }
                    Tile("Median price", kesText(detail.medianPrice), Modifier.fillMaxWidth())
                }
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
                    TrendChart(points = detail.trend, modifier = Modifier.padding(top = 14.dp))
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
                    val top = detail.regionalBreakdown.maxOf { it.averagePrice }.takeIf { it > 0.0 } ?: 1.0
                    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        detail.regionalBreakdown.forEach { RegionRow(it, top) }
                    }
                }
            }
        }
        item {
            Button(onClick = onDownload, shape = PillShape, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Text("Download or print this report", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun RegionRow(region: RegionBreakdownEntry, top: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(region.region, fontWeight = FontWeight.SemiBold)
                Text("${region.institutionCount} institutions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(kesText(region.averagePrice), fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, PillShape)) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth((region.averagePrice / top).toFloat().coerceIn(0.05f, 1f)).background(Brand, PillShape))
        }
    }
}
