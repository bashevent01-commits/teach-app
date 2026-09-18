package com.knowapp.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class KpiEntry(val value: String, val label: String)

/** Matches .kpi-row / .kpi: a horizontal row of small value/label stacks. */
@Composable
fun KpiRow(entries: List<KpiEntry>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        entries.forEach { entry ->
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(entry.value, style = MaterialTheme.typography.titleMedium)
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
