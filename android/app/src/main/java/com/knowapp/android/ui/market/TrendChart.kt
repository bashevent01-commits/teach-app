package com.knowapp.android.ui.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.knowapp.android.data.model.TrendPointOut
import com.knowapp.android.ui.theme.Brand
import com.knowapp.android.ui.theme.BrandSoft

/** Hand-rolled line chart — avoids pulling in a third-party charting
 * library (and its version-compatibility risk) for one simple line. */
@Composable
fun TrendChart(points: List<TrendPointOut>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return

    val values = points.map { it.averagePrice }
    val minValue = values.min()
    val maxValue = values.max()
    val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    Box(modifier = modifier.fillMaxWidth().height(160.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
            val offsets = points.mapIndexed { index, point ->
                val normalized = ((point.averagePrice - minValue) / range).toFloat()
                Offset(x = index * stepX, y = size.height - (normalized * size.height))
            }

            // Filled area under the line, matching the web app's Chart.js fill.
            if (offsets.size > 1) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(offsets.first().x, size.height)
                    offsets.forEach { lineTo(it.x, it.y) }
                    lineTo(offsets.last().x, size.height)
                    close()
                }
                drawPath(path, color = BrandSoft)
            }

            for (i in 0 until offsets.size - 1) {
                drawLine(color = Brand, start = offsets[i], end = offsets[i + 1], strokeWidth = 5f)
            }
            offsets.forEach { drawCircle(color = Brand, radius = 6f, center = it) }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${points.first().month} → ${points.last().month}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
