package com.medicglobe.knoxsolar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun KCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KC.glass)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        if (title != null) {
            Text(title, color = KC.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
            Text(
                value, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
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
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = KC.mute, fontSize = 14.sp)
        Text(value, color = KC.text, fontSize = 14.sp, style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp)
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

/** Bars grow in when the data changes. [highlight] is drawn at full strength, the rest slightly dimmer. */
@Composable
fun BarChart(values: List<Double>, color: Color, modifier: Modifier = Modifier, highlight: Int = -1) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        if (values.isEmpty()) return@Canvas
        val g = grow.value
        val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
        val n = values.size
        val gap = if (n > 40) 1.dp.toPx() else 3.dp.toPx()
        val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val r = minOf(bw / 2f, 4.dp.toPx())
        for (i in 0 until n) {
            val h = (values[i] / max * size.height).toFloat() * g
            if (h < 0.5f) continue
            val c = if (highlight >= 0 && i != highlight) color.copy(alpha = 0.55f) else color
            drawRoundRect(c, Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(r, r))
        }
    }
}

class Series(
    val name: String, val color: Color, val pts: List<Pair<Float, Float>>,
    val fill: Boolean = false,
)

/**
 * Line chart over a full day (x = minute of day 0..1440).
 * shaded = (fromMin, toMin) spans drawn in red.
 * Pass [onCursor] to let the person touch or drag across the chart; [cursor] is the minute being inspected.
 */
@Composable
fun LineChart(
    series: List<Series>, yMin: Float, yMax: Float,
    shaded: List<Pair<Float, Float>> = emptyList(), modifier: Modifier = Modifier,
    cursor: Float? = null, onCursor: ((Float?) -> Unit)? = null, height: Dp = 170.dp,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val cb = rememberUpdatedState(onCursor)

    val touch = if (onCursor == null) Modifier else Modifier
        .pointerInput(Unit) {
            detectTapGestures { o -> cb.value?.invoke((o.x / size.width * 1440f).coerceIn(0f, 1440f)) }
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { o -> cb.value?.invoke((o.x / size.width * 1440f).coerceIn(0f, 1440f)) },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    cb.value?.invoke((change.position.x / size.width * 1440f).coerceIn(0f, 1440f))
                },
            )
        }

    Canvas(modifier.fillMaxWidth().height(height).then(touch)) {
        val w = size.width
        val h = size.height
        val span = (yMax - yMin).coerceAtLeast(0.0001f)
        fun yOf(v: Float) = h - ((v - yMin) / span).coerceIn(0f, 1f) * h
        fun xOf(m: Float) = m / 1440f * w

        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(KC.line, Offset(0f, y), Offset(w, y), 1.dp.toPx())
        }
        for (r in shaded) {
            val x0 = xOf(r.first)
            val x1 = xOf(r.second)
            drawRect(KC.bad.copy(alpha = 0.18f), Offset(x0, 0f), Size((x1 - x0).coerceAtLeast(2f), h))
        }
        if (yMin < 0f && yMax > 0f) {
            val zy = yOf(0f)
            drawLine(KC.mute.copy(alpha = 0.5f), Offset(0f, zy), Offset(w, zy), 1.dp.toPx())
        }

        clipRect(right = w * reveal.value) {
            for (s in series) {
                if (s.pts.size < 2) continue
                val line = Path()
                s.pts.forEachIndexed { i, pt ->
                    val px = xOf(pt.first)
                    val py = yOf(pt.second)
                    if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
                }
                if (s.fill) {
                    val base = yOf(maxOf(yMin, 0f))
                    val area = Path()
                    area.addPath(line)
                    area.lineTo(xOf(s.pts.last().first), base)
                    area.lineTo(xOf(s.pts.first().first), base)
                    area.close()
                    drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.30f), Color.Transparent), startY = 0f, endY = h))
                }
                drawPath(line, s.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        if (cursor != null) {
            val cx = xOf(cursor)
            drawLine(Color.White.copy(alpha = 0.35f), Offset(cx, 0f), Offset(cx, h), 1.dp.toPx())
            for (s in series) {
                val p = s.pts.minByOrNull { abs(it.first - cursor) } ?: continue
                val c = Offset(xOf(p.first), yOf(p.second))
                drawCircle(Color(0xFF04080F), radius = 5.dp.toPx(), center = c)
                drawCircle(s.color, radius = 3.5.dp.toPx(), center = c)
            }
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
