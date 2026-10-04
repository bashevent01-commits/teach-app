package com.knowapp.android.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Teal = Color(0xFF00695F)
private val Paper = Color(0xFFF8FAF7)
private val Gold = Color(0xFFF5B82E)
private val Ink = Color(0xFF102C29)
private val Shadow = Color(0xFF174B46)
private val Rule = Color(0xFFCEE0DB)

private fun segment(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

// A pen writes two lines into a ledger; the brand kit's loading scene, drawn natively
@Composable
fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "splash")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )

    Box(modifier = Modifier.fillMaxSize().background(Teal), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(300.dp, 209.dp)) {
                val d = size.width / 330f
                // Ledger shadow and page
                drawRoundRect(Shadow, topLeft = Offset(12f * d, 16f * d), size = Size(330f * d, 230f * d), cornerRadius = CornerRadius(14f * d))
                drawRoundRect(Paper, size = Size(330f * d, 230f * d), cornerRadius = CornerRadius(14f * d))
                drawLine(Gold, Offset(48f * d, 0f), Offset(48f * d, 230f * d), strokeWidth = 1.5f * d)
                listOf(44f, 84f, 124f, 164f, 204f).forEach { y ->
                    drawLine(Rule, Offset(24f * d, y * d), Offset(306f * d, y * d), strokeWidth = 1f * d)
                }

                val p1 = segment(t, 0.10f, 0.47f)
                val p2 = segment(t, 0.51f, 0.79f)
                val fade = 1f - segment(t, 0.90f, 0.98f)
                val inkColor = Teal.copy(alpha = fade)
                if (p1 > 0f) drawLine(inkColor, Offset(72f * d, 78f * d), Offset((72f + 176f * p1) * d, 78f * d), strokeWidth = 4f * d, cap = StrokeCap.Round)
                if (p2 > 0f) drawLine(inkColor, Offset(72f * d, 122f * d), Offset((72f + 146f * p2) * d, 122f * d), strokeWidth = 4f * d, cap = StrokeCap.Round)

                // Pen tip follows the line being written
                val tip = when {
                    t < 0.10f -> Offset(72f * d, 78f * d)
                    t < 0.47f -> Offset((72f + 176f * p1) * d, 78f * d)
                    t < 0.51f -> Offset(72f * d, (78f + 44f * segment(t, 0.47f, 0.51f)) * d)
                    else -> Offset((72f + 146f * p2) * d, 122f * d)
                }
                val penAlpha = (segment(t, 0.08f, 0.15f)) * fade
                if (penAlpha > 0f) {
                    rotate(-34f, pivot = tip) {
                        drawRoundRect(Gold.copy(alpha = penAlpha), topLeft = Offset(tip.x + 18f * d, tip.y - 6.5f * d), size = Size(132f * d, 13f * d), cornerRadius = CornerRadius(6f * d))
                        val nib = Path().apply {
                            moveTo(tip.x, tip.y)
                            lineTo(tip.x + 18f * d, tip.y - 6.5f * d)
                            lineTo(tip.x + 18f * d, tip.y + 6.5f * d)
                            close()
                        }
                        drawPath(nib, Ink.copy(alpha = penAlpha))
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            Text("Preparing your accounts", color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Bringing every entry into balance", color = Paper.copy(alpha = 0.68f), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
