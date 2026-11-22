package com.medicglobe.knoxsolar.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.f0

/** Glass card with a coloured border and a soft corner glow (hero strip + KPI tiles). */
@Composable
fun AccentCard(accent: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(KC.glass)
            .border(1.dp, accent.copy(alpha = 0.18f), shape)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width, 0f), radius = size.width * 0.7f,
                    ),
                    radius = size.width * 0.7f, center = Offset(size.width, 0f),
                )
            }
            .padding(14.dp),
        content = content,
    )
}

/** Mono-caps label like the dashboard's .klbl */
@Composable
fun MonoLabel(text: String, color: Color = KC.mute) {
    Text(text.uppercase(), color = color, fontSize = 9.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
}

@Composable
fun MonoSub(text: String) {
    Text(text, color = KC.mute2, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp))
}

/** Battery charge bar with percentage, colour changes at 60% / 30%. */
@Composable
fun BattBar(soc: Double) {
    val pct = soc.coerceIn(0.0, 100.0).toFloat()
    val frac by animateFloatAsState(pct / 100f, tween(1400), label = "batt")
    val col = when {
        pct >= 60f -> KC.ok
        pct >= 30f -> KC.warn
        else -> KC.bad
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${f0(pct.toDouble())}%", color = col, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
            modifier = Modifier.padding(end = 12.dp))
        Box(
            Modifier.weight(1f).height(10.dp)
                .clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.06f))
        ) {
            Box(
                Modifier.fillMaxWidth(frac.coerceIn(0.001f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp)).background(col)
            )
        }
    }
}

/** Two-colour donut: [pct] percent in colour a, the rest in colour b. */
@Composable
fun Donut(pct: Double, a: Color, b: Color, diameter: Dp = 120.dp) {
    val p = pct.coerceIn(0.0, 100.0).toFloat()
    Canvas(Modifier.size(diameter)) {
        val stroke = 14.dp.toPx()
        val tl = Offset(stroke / 2, stroke / 2)
        val sz = Size(size.width - stroke, size.height - stroke)
        drawArc(KC.s3, 0f, 360f, false, tl, sz, style = Stroke(stroke))
        val sa = p * 3.6f
        if (sa > 0.5f) drawArc(a, -90f, sa, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Butt))
        val sb = (100f - p) * 3.6f
        if (sb > 0.5f) drawArc(b, -90f + sa, sb, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Butt))
    }
}

/** Single-colour progress ring (270 degrees) used for the efficiency score. */
@Composable
fun ScoreRing(pct: Double, color: Color, diameter: Dp = 110.dp) {
    val sweep = (pct.coerceIn(0.0, 100.0) / 100.0 * 270.0).toFloat()
    Canvas(Modifier.size(diameter)) {
        val stroke = 12.dp.toPx()
        val tl = Offset(stroke / 2, stroke / 2)
        val sz = Size(size.width - stroke, size.height - stroke)
        drawArc(KC.s3, 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        if (sweep > 0f) drawArc(color, 135f, sweep, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
