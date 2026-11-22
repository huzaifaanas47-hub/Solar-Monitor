package com.medicglobe.knoxsolar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.medicglobe.knoxsolar.Today
import com.medicglobe.knoxsolar.f0
import com.medicglobe.knoxsolar.f2
import com.medicglobe.knoxsolar.fmtDur
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** What the diagram needs, derived the same way as updateFlowDiagram() in the web dashboard. */
class FlowState(t: Today) {
    val pv = t.pv
    val load = t.load
    val grid = t.grid
    val battKw = t.battKw
    val soc = t.soc
    val gridUp = t.gridUp != 0
    val gridV = t.gridV
    val gridHz = t.gridHz
    val outageSec = t.outageSec

    val solarOn = pv > 0.05
    val battDisch = battKw > 0.05
    val battCharg = battKw < -0.05
    val importing = gridUp && grid > 0.05
    val exporting = gridUp && grid < -0.05
    val gridAbs = abs(grid)

    fun statusLine(): String {
        val parts = ArrayList<String>()
        if (!gridUp) parts.add("⛔ Grid outage" + if (outageSec > 0) " · " + fmtDur(outageSec) else "")
        if (solarOn) parts.add("☀️ Solar ${f2(pv)} kW")
        if (exporting) parts.add("→ ⚡ Grid export ${f2(gridAbs)} kW")
        if (importing) parts.add("⚡ Grid → 🏠 ${f2(gridAbs)} kW")
        if (battCharg && solarOn) parts.add("→ 🔋 Charging")
        if (battDisch) parts.add("🔋 → 🏠 ${f2(abs(battKw))} kW")
        if (parts.isEmpty()) parts.add("System idle")
        return parts.joinToString("  ·  ")
    }

    fun homeSub(): String = when {
        !gridUp -> when {
            battDisch && solarOn -> "battery + solar"
            battDisch -> "battery only"
            solarOn -> "solar only"
            else -> "standby"
        }
        importing && battDisch -> "grid + battery"
        importing -> "grid + solar"
        battDisch -> "battery + solar"
        solarOn -> "solar only"
        else -> "standby"
    }
}

private val SOLAR_RGB = Color(0xFF2DD4BF)
private val HOME_RGB = Color(0xFF94A3B8)
private val GRID_RGB = Color(0xFF38BDF8)
private val BATT_RGB = Color(0xFFA78BFA)

private val PARTICLE_R = floatArrayOf(7f, 4.5f, 3f, 1.8f)
private val PARTICLE_OFF = floatArrayOf(0f, 0.28f, 0.56f, 0.82f)
private val PARTICLE_ALPHA = floatArrayOf(1f, 0.55f, 0.25f, 0.10f)

private class Pipe(
    val a: Offset, val b: Offset, val tint: Color, val dot: Color,
    val active: Boolean, val rev: Boolean, val kw: Double, val badge: Offset,
)

private class Node(
    val cx: Float, val cy: Float, val rgb: Color, val valueColor: Color,
    val label: String, val value: String, val sub: String, val emoji: String,
    val active: Boolean, val outage: Boolean = false,
)

/**
 * Animated live energy flow (viewBox 400 x 290):
 * Solar -> Home, Home <-> Grid, Solar -> Battery, Battery -> Home.
 */
@Composable
fun FlowDiagram(f: FlowState, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val time by produceState(0f) {
        var start = -1L
        while (true) {
            withFrameNanos { n ->
                if (start < 0L) start = n
                value = ((n - start) / 1_000_000L).toFloat() / 1000f
            }
        }
    }

    Canvas(modifier.fillMaxWidth().aspectRatio(400f / 290f)) {
        val t = time
        val sc = size.width / 400f
        fun p(x: Float, y: Float) = Offset(x * sc, y * sc)

        fun txt(s: String, cx: Float, cy: Float, fs: Float, color: Color, bold: Boolean = false, mono: Boolean = false) {
            val r = measurer.measure(
                s,
                TextStyle(
                    color = color, fontSize = (fs * sc).toSp(),
                    fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal,
                    fontFamily = if (mono) FontFamily.Monospace else null,
                ),
            )
            drawText(r, topLeft = Offset(cx * sc - r.size.width / 2f, cy * sc - r.size.height / 2f))
        }

        // ── geometry ──
        val solarC = p(70f, 80f); val homeC = p(200f, 80f); val gridC = p(330f, 80f); val battC = p(200f, 210f)
        val pipes = listOf(
            Pipe(p(107f, 80f), p(163f, 80f), SOLAR_RGB, Color(0xFFFFB800), f.solarOn, false, f.pv, p(135f, 70f)),
            Pipe(p(237f, 80f), p(293f, 80f), GRID_RGB, Color(0xFF38C8FF), f.exporting || f.importing, f.importing, f.gridAbs, p(265f, 70f)),
            Pipe(p(92f, 112f), p(178f, 178f), BATT_RGB, Color(0xFFB57BFF), f.battCharg && f.solarOn, false, abs(f.battKw), p(122f, 150f)),
            Pipe(p(200f, 174f), p(200f, 116f), BATT_RGB, Color(0xFFB57BFF), f.battDisch, false, abs(f.battKw), p(220f, 146f)),
        )
        val nodes = listOf(
            Node(70f, 80f, SOLAR_RGB, Color(0xFFFFB800), "SOLAR", f2(f.pv) + " kW",
                if (f.solarOn) f0(f.pv * 1000) + " W" else "offline", "☀️", f.solarOn),
            Node(200f, 80f, HOME_RGB, Color(0xFF00E5A0), "HOME", f2(f.load) + " kW", f.homeSub(), "🏠", true),
            Node(330f, 80f, GRID_RGB, Color(0xFF38C8FF),
                "GRID", if (f.gridUp) f2(f.gridAbs) + " kW" else "—",
                when { !f.gridUp -> "grid down"; f.exporting -> "↑ exporting"; f.importing -> "↓ importing"; else -> "idle" },
                "⚡", f.gridUp && (f.importing || f.exporting), outage = !f.gridUp),
            Node(200f, 210f, BATT_RGB, Color(0xFFB57BFF), "BATTERY", f0(f.soc) + "%",
                when { f.battDisch -> "▼ " + f2(abs(f.battKw)) + " kW"; f.battCharg -> "▲ " + f2(abs(f.battKw)) + " kW"; else -> "standby" },
                "🔋", f.battDisch || f.battCharg),
        )
        val centers = listOf(solarC, homeC, gridC, battC)

        // ── pulse rings + halos ──
        nodes.forEachIndexed { i, n ->
            if (!n.active) return@forEachIndexed
            val c = centers[i]
            drawCircle(
                Brush.radialGradient(listOf(n.rgb.copy(alpha = 0.22f), Color.Transparent), center = c, radius = 54f * sc),
                radius = 54f * sc, center = c,
            )
            val ph = (t % 2.4f) / 2.4f
            drawCircle(
                n.rgb.copy(alpha = (1f - ph) * 0.85f), radius = (40f + 20f * ph) * sc, center = c,
                style = Stroke(width = ((2.5f * (1f - ph)) * sc).coerceAtLeast(0.1f)),
            )
        }

        // ── pipes ──
        val dashPeriod = 10f * sc
        val dashProg = (t % 0.8f) / 0.8f
        pipes.forEach { pp ->
            drawLine(pp.tint.copy(alpha = 0.10f), pp.a, pp.b, strokeWidth = 8f * sc, cap = StrokeCap.Round)
            if (!pp.active) return@forEach
            drawLine(pp.tint.copy(alpha = 0.22f), pp.a, pp.b, strokeWidth = 11f * sc, cap = StrokeCap.Round)
            drawLine(pp.tint.copy(alpha = 0.75f), pp.a, pp.b, strokeWidth = 6f * sc, cap = StrokeCap.Round)
            val phase = if (pp.rev) dashProg * dashPeriod else (1f - dashProg) * dashPeriod
            drawLine(
                Color.White.copy(alpha = 0.95f), pp.a, pp.b, strokeWidth = 3f * sc, cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * sc, 8f * sc), phase),
            )
        }

        // ── kW badges ──
        pipes.forEach { pp ->
            if (pp.active && pp.kw > 0.01) txt(f2(pp.kw) + " kW", pp.badge.x / sc, pp.badge.y / sc, 7.5f, pp.tint.copy(alpha = 0.95f), bold = true, mono = true)
        }

        // ── moving particles ──
        pipes.forEach { pp ->
            if (!pp.active) return@forEach
            val speed = (0.5f + pp.kw.toFloat() * 0.16f).coerceIn(0.6f, 1.8f)
            val base = (t * speed) % 1f
            for (i in 0 until 4) {
                val tt = (((base + PARTICLE_OFF[i]) % 1f) + 1f) % 1f
                val k = if (pp.rev) 1f - tt else tt
                val pos = Offset(pp.a.x + (pp.b.x - pp.a.x) * k, pp.a.y + (pp.b.y - pp.a.y) * k)
                if (i == 0) drawCircle(pp.dot.copy(alpha = 0.28f), radius = PARTICLE_R[0] * 1.9f * sc, center = pos)
                drawCircle(pp.dot.copy(alpha = PARTICLE_ALPHA[i]), radius = PARTICLE_R[i] * sc, center = pos)
            }
        }

        // ── node cards ──
        val pulse = 0.88f + 0.12f * sin(t * (2f * PI.toFloat() / 2.6f))
        val blink = 0.35f + 0.65f * abs(sin(t * PI.toFloat() / 1.4f))
        nodes.forEachIndexed { i, n ->
            val tl = Offset((n.cx - 38f) * sc, (n.cy - 34f) * sc)
            val sz = Size(76f * sc, 68f * sc)
            val cr = CornerRadius(16f * sc, 16f * sc)
            drawRoundRect(Color(0xF20F121C), tl, sz, cr)
            drawRoundRect(n.rgb.copy(alpha = if (n.active) 0.10f else 0.04f), tl, sz, cr)
            when {
                n.outage -> drawRoundRect(
                    KC.bad.copy(alpha = blink), tl, sz, cr,
                    style = Stroke(1.5f * sc, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * sc, 4f * sc), 0f)),
                )
                n.active -> {
                    drawRoundRect(n.rgb.copy(alpha = 0.10f * pulse), tl, sz, cr, style = Stroke(9f * sc))
                    drawRoundRect(n.rgb.copy(alpha = 0.18f * pulse), tl, sz, cr, style = Stroke(5f * sc))
                    drawRoundRect(n.rgb.copy(alpha = 0.9f), tl, sz, cr, style = Stroke(1.5f * sc))
                }
                else -> drawRoundRect(n.rgb.copy(alpha = 0.22f), tl, sz, cr, style = Stroke(1.5f * sc))
            }
            txt(n.label, n.cx, n.cy - 15f, 7f, Color.White.copy(alpha = 0.40f), mono = true)
            txt(n.value, n.cx, n.cy + 2f, 13.5f, n.valueColor, bold = true)
            txt(n.sub, n.cx, n.cy + 17f, 6.5f, Color.White.copy(alpha = 0.38f), mono = true)
            txt(n.emoji, n.cx + 29f, n.cy - 21f, 10f, Color.White.copy(alpha = 0.7f))
        }

        // ── grid voltage / frequency under the GRID node ──
        val gridNote = when {
            f.gridV > 1.0 -> f0(f.gridV) + " V" + (if (f.gridHz > 1.0) " · " + f0(f.gridHz) + " Hz" else "")
            !f.gridUp -> "no supply"
            else -> ""
        }
        if (gridNote.isNotEmpty()) {
            txt(gridNote, 330f, 126f, 8f, if (f.gridUp) GRID_RGB else KC.bad, bold = true, mono = true)
        }
    }
}
