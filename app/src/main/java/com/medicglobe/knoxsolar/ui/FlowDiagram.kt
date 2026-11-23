package com.medicglobe.knoxsolar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.medicglobe.knoxsolar.f0
import com.medicglobe.knoxsolar.f2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val SOLAR_RGB = Color(0xFF2DD4BF)
private val HOME_RGB = Color(0xFF94A3B8)
private val GRID_RGB = Color(0xFF38BDF8)
private val BATT_RGB = Color(0xFFA78BFA)

private val NODE_X = floatArrayOf(70f, 200f, 330f, 200f)
private val NODE_Y = floatArrayOf(80f, 80f, 80f, 210f)
private val NODE_RGB = arrayOf(SOLAR_RGB, HOME_RGB, GRID_RGB, BATT_RGB)
private val NODE_VAL = arrayOf(Color(0xFFFFB800), Color(0xFF00E5A0), Color(0xFF38C8FF), Color(0xFFB57BFF))
private val NODE_LABEL = arrayOf("Solar", "Home", "Grid", "Battery")

private val PIPE_TINT = arrayOf(SOLAR_RGB, GRID_RGB, BATT_RGB, BATT_RGB)
private val PIPE_DOT = arrayOf(Color(0xFFFFB800), Color(0xFF38C8FF), Color(0xFFB57BFF), Color(0xFFB57BFF))

// Icons are drawn in a 16 x 16 box centred on (0, 0) and scaled to the diagram.
private val ICON_HOUSE = Path().apply {
    moveTo(-7f, 0f); lineTo(0f, -7f); lineTo(7f, 0f)
    moveTo(-5f, -1.5f); lineTo(-5f, 6f); lineTo(5f, 6f); lineTo(5f, -1.5f)
    moveTo(-1.6f, 6f); lineTo(-1.6f, 2f); lineTo(1.6f, 2f); lineTo(1.6f, 6f)
}
private val ICON_BOLT = Path().apply {
    moveTo(1.8f, -8f); lineTo(-4.5f, 1f); lineTo(-0.6f, 1f)
    lineTo(-1.8f, 8f); lineTo(4.5f, -1.5f); lineTo(0.6f, -1.5f); close()
}

/** Pipe paths for one canvas size. Built once and reused every frame. */
private class Geo(sc: Float) {
    val paths: List<Path>
    val measures: List<PathMeasure>
    val lengths: FloatArray
    val counts: IntArray

    init {
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = Path().apply {
            moveTo(x1 * sc, y1 * sc); lineTo(x2 * sc, y2 * sc)
        }
        val solarHome = line(110f, 80f, 160f, 80f)
        val homeGrid = line(240f, 80f, 290f, 80f)
        val solarBatt = Path().apply {
            moveTo(70f * sc, 118f * sc)
            cubicTo(70f * sc, 168f * sc, 104f * sc, 210f * sc, 160f * sc, 210f * sc)
        }
        val battHome = line(200f, 172f, 200f, 118f)
        paths = listOf(solarHome, homeGrid, solarBatt, battHome)
        measures = paths.map { p -> PathMeasure().also { it.setPath(p, false) } }
        lengths = FloatArray(paths.size) { measures[it].length }
        counts = IntArray(paths.size) { ((lengths[it] / sc) / 24f).roundToInt().coerceIn(3, 7) }
    }
}

/**
 * Live energy flow: solar > home, home <> grid, solar > battery, battery <> home.
 * All motion comes from [FlowEngine], which eases every value instead of switching it.
 */
@Composable
fun FlowDiagram(engine: FlowEngine, state: FlowState, modifier: Modifier = Modifier) {
    SideEffect { engine.setTarget(state) }

    val measurer = rememberTextMeasurer(cacheSize = 160)
    var tick by remember { mutableLongStateOf(0L) }
    val appear = remember { Animatable(0f) }

    LaunchedEffect(Unit) { appear.animateTo(1f, tween(650)) }
    LaunchedEffect(engine) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) engine.step((now - last) / 1_000_000_000f)
                last = now
                tick = now
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(400f / 280f)
            .graphicsLayer { alpha = appear.value }
            .drawWithCache {
                val sc = size.width / 400f
                val geo = Geo(sc)
                onDrawBehind {
                    drawFlow(engine, geo, measurer, sc, tick)
                }
            }
    ) {}
}

private fun wrap1(x: Float): Float = ((x % 1f) + 1f) % 1f
private fun k2(v: Float): String = f2(v.toDouble())
private fun k0(v: Float): String = f0(v.toDouble())

@Suppress("UNUSED_PARAMETER")
private fun DrawScope.drawFlow(e: FlowEngine, g: Geo, m: TextMeasurer, sc: Float, frame: Long) {
    val st = e.state ?: return
    if (!e.ready) return

    fun txt(s: String, cx: Float, cy: Float, fs: Float, color: Color, bold: Boolean = false, mono: Boolean = false) {
        if (color.alpha < 0.01f) return
        val r = m.measure(
            s,
            TextStyle(
                color = Color.White, fontSize = (fs * sc).toSp(),
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = if (mono) FontFamily.Monospace else KFont,
                fontFeatureSettings = "tnum",
            ),
        )
        drawText(r, color = color, topLeft = Offset(cx * sc - r.size.width / 2f, cy * sc - r.size.height / 2f))
    }

    // ── halos and pulse rings (behind everything) ──
    val ring = e.ringT / 2.4f
    for (i in 0 until 4) {
        val act = e.nodeAct(i)
        if (act < 0.01f) continue
        val c = Offset(NODE_X[i] * sc, NODE_Y[i] * sc)
        drawCircle(
            Brush.radialGradient(listOf(NODE_RGB[i].copy(alpha = 0.22f * act), Color.Transparent), center = c, radius = 58f * sc),
            radius = 58f * sc, center = c,
        )
        drawCircle(
            NODE_RGB[i].copy(alpha = (1f - ring) * 0.8f * act), radius = (40f + 22f * ring) * sc, center = c,
            style = Stroke(((2.4f * (1f - ring)) * sc).coerceAtLeast(0.1f)),
        )
    }

    // ── pipes ──
    for (i in 0 until FlowEngine.PIPES) {
        val tint = PIPE_TINT[i]
        val path = g.paths[i]
        val inten = e.pipeI(i)
        drawPath(path, tint.copy(alpha = 0.10f), style = Stroke(8f * sc, cap = StrokeCap.Round))
        if (inten < 0.01f) continue

        drawPath(path, tint.copy(alpha = 0.22f * inten), style = Stroke(11f * sc, cap = StrokeCap.Round))
        drawPath(path, tint.copy(alpha = 0.70f * inten), style = Stroke(5.5f * sc, cap = StrokeCap.Round))
        val dashPhase = (((-e.dash[i]) % 10f) + 10f) % 10f
        drawPath(
            path, Color.White.copy(alpha = 0.80f * inten),
            style = Stroke(
                2.6f * sc, cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * sc, 8f * sc), dashPhase * sc),
            ),
        )

        // particles: they fade in and out at the ends, so nothing pops at a node
        val dot = PIPE_DOT[i]
        val meas = g.measures[i]
        val len = g.lengths[i]
        val n = g.counts[i]
        val dirSign = if (e.pipeDir(i) >= 0f) 1f else -1f
        for (k in 0 until n) {
            val u = wrap1(e.phase[i] + k.toFloat() / n)
            val env = sqrt(sin(PI.toFloat() * u).coerceAtLeast(0f))
            val a = env * inten
            if (a < 0.02f) continue
            for (j in 3 downTo 1) {
                val ut = u - dirSign * j * 0.022f
                if (ut <= 0f || ut >= 1f) continue
                val tp = meas.getPosition(ut * len)
                drawCircle(dot.copy(alpha = a * (0.32f - j * 0.08f)), radius = (3.6f - j * 0.7f) * sc, center = tp)
            }
            val p = meas.getPosition(u * len)
            drawCircle(dot.copy(alpha = 0.26f * a), radius = 7f * sc, center = p)
            drawCircle(dot.copy(alpha = a), radius = 3.4f * sc, center = p)
            drawCircle(Color.White.copy(alpha = 0.7f * a), radius = 1.3f * sc, center = p)
        }
    }

    // ── kW badges ──
    val badgeX = floatArrayOf(135f, 265f, 60f, 222f)
    val badgeY = floatArrayOf(68f, 68f, 183f, 148f)
    for (i in 0 until FlowEngine.PIPES) {
        val inten = e.pipeI(i)
        if (inten < 0.05f) continue
        txt(k2(e.pipeKw(i)) + " kW", badgeX[i], badgeY[i], 7.5f, PIPE_TINT[i].copy(alpha = 0.95f * inten), bold = true)
    }

    // ── node cards ──
    val pulse = 0.88f + 0.12f * sin(e.pulseT * (2f * PI.toFloat() / 2.6f))
    val blink = 0.35f + 0.65f * abs(sin(e.blinkT * PI.toFloat() / 1.4f))
    for (i in 0 until 4) {
        val cx = NODE_X[i]
        val cy = NODE_Y[i]
        val rgb = NODE_RGB[i]
        val act = e.nodeAct(i)
        val outage = if (i == 2) 1f - e.gridUp else 0f

        val tl = Offset((cx - 40f) * sc, (cy - 38f) * sc)
        val sz = Size(80f * sc, 76f * sc)
        val cr = CornerRadius(18f * sc, 18f * sc)

        drawRoundRect(Color(0xF20F121C), tl, sz, cr)
        drawRoundRect(rgb.copy(alpha = 0.04f + 0.07f * act), tl, sz, cr)
        if (act > 0.01f) {
            drawRoundRect(rgb.copy(alpha = 0.10f * pulse * act), tl, sz, cr, style = Stroke(9f * sc))
            drawRoundRect(rgb.copy(alpha = 0.18f * pulse * act), tl, sz, cr, style = Stroke(5f * sc))
        }
        drawRoundRect(rgb.copy(alpha = (0.22f + 0.68f * act) * (1f - outage)), tl, sz, cr, style = Stroke(1.5f * sc))
        if (outage > 0.01f) {
            drawRoundRect(
                KC.bad.copy(alpha = blink * outage), tl, sz, cr,
                style = Stroke(1.5f * sc, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * sc, 4f * sc), 0f)),
            )
        }

        val ink = 0.55f + 0.45f * act
        drawNodeIcon(i, cx * sc, (cy - 21f) * sc, sc, NODE_VAL[i].copy(alpha = ink), e)

        val value = when (i) {
            0 -> k2(e.pv) + " kW"
            1 -> k2(e.load) + " kW"
            2 -> if (st.gridUp) k2(abs(e.grid)) + " kW" else "—"
            else -> k0(e.soc) + "%"
        }
        val sub = when (i) {
            0 -> st.solarSub()
            1 -> st.homeSub()
            2 -> st.gridSub()
            else -> st.battSub()
        }
        txt(value, cx, cy + 1f, 13f, NODE_VAL[i].copy(alpha = ink), bold = true)
        txt(NODE_LABEL[i], cx, cy + 15f, 6.8f, Color.White.copy(alpha = 0.50f))
        txt(sub, cx, cy + 27f, 6.4f, Color.White.copy(alpha = 0.38f))
    }
}

private fun DrawScope.drawNodeIcon(i: Int, x: Float, y: Float, sc: Float, color: Color, e: FlowEngine) {
    withTransform({
        translate(x, y)
        scale(sc, sc, Offset.Zero)
    }) {
        when (i) {
            0 -> {
                drawCircle(color, radius = 3.4f, center = Offset.Zero)
                withTransform({ rotate(e.sunDeg, Offset.Zero) }) {
                    for (r in 0 until 8) {
                        val a = r * (PI.toFloat() / 4f)
                        val c = cos(a)
                        val s = sin(a)
                        drawLine(color, Offset(c * 5.6f, s * 5.6f), Offset(c * 7.8f, s * 7.8f), strokeWidth = 1.3f, cap = StrokeCap.Round)
                    }
                }
            }
            1 -> drawPath(ICON_HOUSE, color, style = Stroke(1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            2 -> drawPath(ICON_BOLT, color)
            else -> {
                drawRoundRect(color, Offset(-8f, -4.5f), Size(14f, 9f), CornerRadius(2f, 2f), style = Stroke(1.2f))
                drawRect(color, Offset(6.4f, -1.8f), Size(1.8f, 3.6f))
                val w = 11.2f * (e.soc / 100f).coerceIn(0f, 1f)
                if (w > 0.5f) {
                    val fill = if (e.soc < 20f) KC.bad.copy(alpha = color.alpha) else color
                    drawRoundRect(fill, Offset(-6.6f, -3.1f), Size(w, 6.2f), CornerRadius(1f, 1f))
                }
            }
        }
    }
}
