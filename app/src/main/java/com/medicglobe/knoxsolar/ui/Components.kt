package com.medicglobe.knoxsolar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KC.glass)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(16.dp)
    ) {
        if (title != null) {
            Text(
                title.uppercase(), color = KC.mute, fontSize = 10.sp, letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
            )
            Box(Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = KC.text, unit: String = "") {
    Column(modifier) {
        Text(label, color = KC.mute, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (unit.isNotEmpty()) Text(" $unit", color = KC.mute, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
fun StatRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun KV(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = KC.mute, fontSize = 14.sp)
        Text(value, color = KC.text, fontSize = 14.sp)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun Note(text: String, color: Color = KC.mute) {
    Text(text, color = color, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
}

/** Battery ring: 270-degree arc. */
@Composable
fun SocRing(soc: Double, color: Color, modifier: Modifier = Modifier) {
    val sweep = (soc.coerceIn(0.0, 100.0) / 100.0 * 270.0).toFloat()
    Canvas(modifier.size(170.dp)) {
        val stroke = 16.dp.toPx()
        val tl = Offset(stroke / 2, stroke / 2)
        val sz = Size(size.width - stroke, size.height - stroke)
        drawArc(KC.s2, 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        if (sweep > 0f) drawArc(color, 135f, sweep, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun BarChart(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        if (values.isEmpty()) return@Canvas
        val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
        val n = values.size
        val gap = if (n > 40) 1.dp.toPx() else 3.dp.toPx()
        val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        for (i in 0 until n) {
            val h = (values[i] / max * size.height).toFloat()
            drawRect(color, Offset(i * (bw + gap), size.height - h), Size(bw, h))
        }
    }
}

class Series(val name: String, val color: Color, val pts: List<Pair<Float, Float>>)

/** Line chart over a full day (x = minute of day 0..1440). shaded = (fromMin, toMin) spans drawn in red. */
@Composable
fun LineChart(
    series: List<Series>, yMin: Float, yMax: Float,
    shaded: List<Pair<Float, Float>> = emptyList(), modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        val w = size.width
        val h = size.height
        val span = (yMax - yMin).coerceAtLeast(0.0001f)
        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(KC.line, Offset(0f, y), Offset(w, y), 1.dp.toPx())
        }
        for (r in shaded) {
            val x0 = r.first / 1440f * w
            val x1 = r.second / 1440f * w
            drawRect(KC.bad.copy(alpha = 0.18f), Offset(x0, 0f), Size((x1 - x0).coerceAtLeast(2f), h))
        }
        if (yMin < 0f && yMax > 0f) {
            val zy = h - (0f - yMin) / span * h
            drawLine(KC.mute.copy(alpha = 0.5f), Offset(0f, zy), Offset(w, zy), 1.dp.toPx())
        }
        for (s in series) {
            if (s.pts.size < 2) continue
            val p = Path()
            s.pts.forEachIndexed { i, pt ->
                val px = pt.first / 1440f * w
                val py = h - ((pt.second - yMin) / span).coerceIn(0f, 1f) * h
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            drawPath(p, s.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun Legend(items: List<Pair<String, Color>>) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for (item in items) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(item.second, CircleShape))
                Text(" " + item.first, color = KC.mute, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun HourAxis() {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        for (t in listOf("00:00", "06:00", "12:00", "18:00", "24:00")) Text(t, color = KC.mute, fontSize = 10.sp)
    }
}
