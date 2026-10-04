package com.knowapp.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The shared teal feature card: gradient with two soft circles for depth
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF12948A), Color(0xFF0F766E), Color(0xFF0B5A54))))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.width * 0.42f, center = Offset(size.width * 0.96f, -size.height * 0.08f))
                drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width * 0.30f, center = Offset(size.width * 0.05f, size.height * 1.02f))
            }
            .padding(22.dp),
        content = content,
    )
}
