package com.knowapp.android.ui.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.knowapp.android.data.model.TrendPointOut
import com.knowapp.android.ui.theme.Brand
import com.knowapp.android.ui.theme.BrandSoft

// Hand-drawn line chart with price labels on the left and months underneath
@Composable
fun TrendChart(points: List<TrendPointOut>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val values = points.map { it.averagePrice }
    val minValue = values.min()
    val maxValue = values.max()
    val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val soft = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            Column(modifier = Modifier.width(48.dp).height(150.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(String.format(java.util.Locale.US, "%,.2f", maxValue), style = MaterialTheme.typography.labelSmall, color = soft)
                Text(String.format(java.util.Locale.US, "%,.2f", minValue), style = MaterialTheme.typography.labelSmall, color = soft)
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 6.dp, vertical = 6.dp)) {
                for (g in 0..2) {
                    val gy = size.height * g / 2f
                    drawLine(color = grid, start = Offset(0f, gy), end = Offset(size.width, gy), strokeWidth = 1.5f)
                }
                val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
                val offsets = points.mapIndexed { index, point ->
                    val normalized = ((point.averagePrice - minValue) / range).toFloat()
                    Offset(x = index * stepX, y = size.height - (normalized * size.height))
                }
                if (offsets.size > 1) {
                    val path = Path().apply {
                        moveTo(offsets.first().x, size.height)
                        offsets.forEach { lineTo(it.x, it.y) }
                        lineTo(offsets.last().x, size.height)
                        close()
                    }
                    drawPath(path, color = BrandSoft)
                }
                for (i in 0 until offsets.size - 1) drawLine(color = Brand, start = offsets[i], end = offsets[i + 1], strokeWidth = 6f)
                offsets.forEach { drawCircle(color = Brand, radius = 8f, center = it) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().month, style = MaterialTheme.typography.labelSmall, color = soft)
            Text(points.last().month, style = MaterialTheme.typography.labelSmall, color = soft)
        }
    }
}
