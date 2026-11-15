package com.jarvis.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.jarvis.assistant.service.JState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactor(state: JState, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "reactor")
    val spinMs = if (state == JState.THINKING) 2500 else 9000
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(spinMs, easing = LinearEasing)), label = "rot")
    val rot2 by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(15000, easing = LinearEasing)), label = "rot2")
    val pulseMs = when (state) {
        JState.SPEAKING -> 320; JState.LISTENING -> 900; JState.THINKING -> 600; else -> 2600
    }
    val pulse by inf.animateFloat(0.85f, 1.12f, infiniteRepeatable(tween(pulseMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val target = when (state) {
        JState.OFFLINE -> Color(0xFF37474F)
        JState.SLEEPING -> Color(0xFF0E6B80)
        JState.LISTENING -> Color(0xFF00E5FF)
        JState.THINKING -> Color(0xFFFFB300)
        JState.SPEAKING -> Color(0xFF8CF3FF)
    }
    val c by animateColorAsState(target, tween(500), label = "color")

    Canvas(modifier) {
        val ctr = center
        val r = size.minDimension / 2
        // outer glow
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.42f * pulse), Color.Transparent), ctr, r), r)
        drawCircle(c, r * 0.93f, style = Stroke(3f))
        // rotating arc segments
        rotate(rot, ctr) {
            for (i in 0 until 4) drawArc(
                c, i * 90f + 12f, 54f, false,
                Offset(ctr.x - r * 0.80f, ctr.y - r * 0.80f), Size(r * 1.6f, r * 1.6f),
                style = Stroke(11f, cap = StrokeCap.Round)
            )
        }
        // counter-rotating tick ring
        rotate(rot2, ctr) {
            for (i in 0 until 48) {
                val a = Math.toRadians(i * 7.5)
                val r1 = r * 0.64f; val r2 = r * if (i % 4 == 0) 0.73f else 0.69f
                drawLine(c.copy(alpha = 0.8f),
                    Offset(ctr.x + (r1 * cos(a)).toFloat(), ctr.y + (r1 * sin(a)).toFloat()),
                    Offset(ctr.x + (r2 * cos(a)).toFloat(), ctr.y + (r2 * sin(a)).toFloat()), 3f)
            }
        }
        // 10 reactor coils
        for (i in 0 until 10) rotate(i * 36f, ctr) {
            drawLine(c, Offset(ctr.x, ctr.y - r * 0.40f), Offset(ctr.x, ctr.y - r * 0.57f), 15f, StrokeCap.Round)
        }
        // core
        val core = r * 0.34f * pulse
        drawCircle(Brush.radialGradient(listOf(Color.White, c, Color.Transparent), ctr, core * 1.5f), core * 1.5f)
        drawCircle(c, r * 0.36f, style = Stroke(4f))
    }
}
