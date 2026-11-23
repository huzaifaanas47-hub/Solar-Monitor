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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.medicglobe.knoxsolar.f0
import com.medicglobe.knoxsolar.f2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Scene is laid out in a 400 x 350 box and scaled to the width of the screen.
private const val W = 400f
private const val H = 350f

private val SKY_TOP = Color(0xFF10263A)
private val SKY_BOT = Color(0xFF0B1626)
private val WALL = Color(0xFF7FA6A0)
private val WALL_SIDE = Color(0xFF668E89)
private val PANEL = Color(0xFF14233B)
private val PANEL_LINE = Color(0xFF6C86A8)
private val CABLE = Color(0xFF1E4A47)
private val C_SOLAR = Color(0xFFFFB800)
private val C_GRID = Color(0xFF38BDF8)
private val C_BATT = Color(0xFFB57BFF)
private val C_LOAD = Color(0xFF2DD4BF)

/** The cable paths. Built once per size, reused every frame. */
private class HouseGeo(val sc: Float) {
    private fun poly(vararg p: Float) = Path().apply {
        moveTo(p[0] * sc, p[1] * sc)
        var i = 2
        while (i < p.size) { lineTo(p[i] * sc, p[i + 1] * sc); i += 2 }
    }

    val roof = poly(104f, 202f, 168f, 120f, 336f, 120f, 308f, 202f).also { it.close() }
    val pvCable = poly(214f, 168f, 214f, 206f, 190f, 206f, 190f, 238f)          // roof -> inverter
    val gridCable = poly(190f, 262f, 190f, 318f, 30f, 318f, 30f, 262f)         // inverter -> pylon
    val battCable = poly(100f, 250f, 182f, 250f)                                 // battery -> inverter
    val toBattCable = poly(182f, 250f, 100f, 250f)                               // inverter -> battery
    val loadCable = poly(200f, 248f, 226f, 248f)                                 // inverter -> window

    val all = listOf(pvCable, gridCable, toBattCable, battCable, loadCable)
    val meas = all.map { PathMeasure().apply { setPath(it, false) } }
    val len = FloatArray(all.size) { meas[it].length }
}

/**
 * Cut-away house with moving energy: dashes march along each cable in the direction of the real flow,
 * glowing dots ride on top, and the roof panels shimmer while the sun is up.
 * It shares the [FlowEngine] with the node diagram, so only one of the two should be on screen at a time.
 */
@Composable
fun HouseFlow(engine: FlowEngine, state: FlowState, modifier: Modifier = Modifier) {
    SideEffect { engine.setTarget(state) }
    val measurer = rememberTextMeasurer(cacheSize = 120)
    var tick by remember { mutableLongStateOf(0L) }
    val appear = remember { Animatable(0f) }

    LaunchedEffect(Unit) { appear.animateTo(1f, tween(700)) }
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
            .aspectRatio(W / H)
            .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 24f }
            .drawWithCache {
                val sc = size.width / W
                val geo = HouseGeo(sc)
                onDrawBehind { drawHouse(engine, geo, measurer, sc, tick) }
            }
    ) {}
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun DrawScope.drawHouse(e: FlowEngine, g: HouseGeo, m: TextMeasurer, sc: Float, frame: Long) {
    if (!e.ready) return
    val st = e.state ?: return
    val solar = e.solarF()
    val seconds = (frame / 1_000_000L % 1_000_000L) / 1000f   // wraps every ~11 days; only used for slow drift

    fun x(v: Float) = v * sc

    // ── sky, sun, drifting clouds ──
    drawRoundRect(
        Brush.verticalGradient(listOf(SKY_TOP, SKY_BOT)),
        Offset.Zero, Size(x(W), x(H)), CornerRadius(x(22f), x(22f)),
    )
    val sunC = Offset(x(52f), x(46f))
    drawCircle(
        Brush.radialGradient(listOf(C_SOLAR.copy(alpha = 0.40f * solar), Color.Transparent), center = sunC, radius = x(80f)),
        radius = x(80f), center = sunC,
    )
    if (solar > 0.02f) {
        drawCircle(C_SOLAR.copy(alpha = 0.9f * solar), x(9f), sunC)
        withTransform({ rotate(e.sunDeg, sunC) }) {
            for (r in 0 until 10) {
                val a = r * (2f * PI.toFloat() / 10f)
                drawLine(
                    C_SOLAR.copy(alpha = 0.8f * solar),
                    Offset(sunC.x + cos(a) * x(14f), sunC.y + sin(a) * x(14f)),
                    Offset(sunC.x + cos(a) * x(20f), sunC.y + sin(a) * x(20f)),
                    strokeWidth = x(2f), cap = StrokeCap.Round,
                )
            }
        }
    }
    for (i in 0 until 3) {
        val drift = ((seconds * (4f + i * 2f) + i * 130f) % (W + 120f)) - 60f
        val cy = 30f + i * 38f
        val c = Color.White.copy(alpha = 0.07f)
        drawOval(c, Offset(x(drift), x(cy)), Size(x(70f), x(16f)))
        drawOval(c, Offset(x(drift + 18f), x(cy - 8f)), Size(x(40f), x(18f)))
    }

    // ── ground ──
    drawOval(Color.Black.copy(alpha = 0.28f), Offset(x(20f), x(292f)), Size(x(330f), x(26f)))

    // ── pylon (grid) ──
    drawPylon(sc, st.gridUp, e.pipeI(1))

    // ── house body ──
    drawRect(WALL, Offset(x(120f), x(200f)), Size(x(170f), x(100f)))
    val side = Path().apply {
        moveTo(x(290f), x(200f)); lineTo(x(340f), x(176f)); lineTo(x(340f), x(280f)); lineTo(x(290f), x(300f)); close()
    }
    drawPath(side, WALL_SIDE)
    drawRect(Color.Black.copy(alpha = 0.10f), Offset(x(120f), x(284f)), Size(x(170f), x(16f)))

    // roof slab with shimmering panels
    drawPath(g.roof, Color(0xFF3E5A63))
    drawPanels(g, sc, solar, e.sunDeg)

    // ── battery ──
    drawRoundRect(Color(0xFF1B2330), Offset(x(62f), x(206f)), Size(x(38f), x(94f)), CornerRadius(x(6f), x(6f)))
    drawRoundRect(Color(0xFF3B475A), Offset(x(62f), x(206f)), Size(x(38f), x(94f)), CornerRadius(x(6f), x(6f)), style = Stroke(x(1.5f)))
    val segs = 6
    val socF = (e.soc / 100f).coerceIn(0f, 1f)
    val chargingGlow = 0.55f + 0.45f * (0.5f + 0.5f * sin(e.pulseT / 2.6f * 2f * PI.toFloat()))
    for (i in 0 until segs) {
        val on = (socF * segs - i).coerceIn(0f, 1f)
        if (on <= 0.02f) continue
        val base = if (e.soc < 20f) KC.bad else KC.ok
        val a = if (st.battCharg && on < 1f) chargingGlow else 1f
        drawRoundRect(
            base.copy(alpha = 0.95f * on * a),
            Offset(x(67f), x(290f - (i + 1) * 13f)), Size(x(28f), x(10f)), CornerRadius(x(2f), x(2f)),
        )
    }

    // ── inverter ──
    drawRoundRect(Color(0xFFE7EEF2), Offset(x(180f), x(236f)), Size(x(20f), x(28f)), CornerRadius(x(3f), x(3f)))
    val led = if (e.blinkT < 0.7f) 1f else 0.25f
    drawCircle((if (st.gridUp) KC.ok else KC.bad).copy(alpha = led), x(2.4f), Offset(x(190f), x(244f)))
    drawRect(Color(0xFF9FB1BC), Offset(x(184f), x(250f)), Size(x(12f), x(2f)))
    drawRect(Color(0xFF9FB1BC), Offset(x(184f), x(255f)), Size(x(12f), x(2f)))

    // ── window = load, glows with consumption ──
    val loadGlow = smoothLoad(e.load)
    drawRoundRect(Color(0xFF1A2A33), Offset(x(226f), x(226f)), Size(x(40f), x(42f)), CornerRadius(x(3f), x(3f)))
    drawRoundRect(
        Color(0xFFFFE08A).copy(alpha = 0.25f + 0.70f * loadGlow),
        Offset(x(229f), x(229f)), Size(x(34f), x(36f)), CornerRadius(x(2f), x(2f)),
    )
    if (loadGlow > 0.05f) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFE08A).copy(alpha = 0.30f * loadGlow), Color.Transparent), center = Offset(x(246f), x(247f)), radius = x(50f)),
            radius = x(50f), center = Offset(x(246f), x(247f)),
        )
    }

    // ── cables: base, marching dashes, riding dots ──
    for (p in g.all) drawPath(p, CABLE, style = Stroke(x(3.2f), cap = StrokeCap.Round, join = StrokeJoin.Round))

    flow(g, 0, e.pipeI(0), e.dash[0], e.phase[0], C_SOLAR, sc)                     // solar -> inverter
    flow(g, 1, e.pipeI(1), e.dash[1], e.phase[1], C_GRID, sc)  // grid <-> inverter (direction comes from the signed dash / phase)
    flow(g, 2, e.pipeI(2), e.dash[2], e.phase[2], C_BATT, sc)                      // solar -> battery
    flow(g, 3, e.pipeI(3), e.dash[3], e.phase[3], C_BATT, sc)                      // battery <-> inverter
    val loadI = smoothLoad(e.load)
    flow(g, 4, loadI, (e.pulseT / 2.6f) * 10f * 3f, e.pulseT / 2.6f, C_LOAD, sc)  // inverter -> window

    // ── bubbles ──
    val pulse = 1f + 0.018f * sin(e.pulseT / 2.6f * 2f * PI.toFloat())
    val gridText: String; val gridCol: Color
    when {
        !st.gridUp -> { gridText = "Down"; gridCol = KC.bad }
        abs(e.grid) < 0.01f -> { gridText = "--"; gridCol = Color.White }
        else -> { gridText = f0(abs(e.grid) * 1000.0) + " W"; gridCol = C_GRID }
    }
    val gridSub = if (!st.gridUp) "" else if (e.grid < -0.01f) "export" else if (e.grid > 0.01f) "import" else ""
    bubble(m, sc, 10f, 60f, 98f, "Grid", gridText, gridCol, gridSub, pulse)
    bubble(m, sc, 268f, 26f, 120f, "PV", f0(e.pv * 1000.0) + " W", C_SOLAR, if (solar > 0.05f) "generating" else "offline", pulse)
    bubble(m, sc, 74f, 152f, 108f, "Battery", f0(e.soc.toDouble()) + " %", if (e.soc < 20f) KC.bad else KC.ok,
        if (st.battCharg) "charging" else if (st.battDisch) "discharging" else "standby", pulse)
    bubble(m, sc, 262f, 296f, 126f, "Load", f0(e.load * 1000.0) + " W", C_LOAD, "", pulse)
}

private fun smoothLoad(kw: Float) = FlowEngine.smooth(kw, 0.02f, 0.2f)

private fun DrawScope.flow(
    g: HouseGeo, idx: Int, intensity: Float, dash: Float, phase: Float, color: Color, sc: Float,
) {
    if (intensity < 0.02f) return
    val p = g.all[idx]
    val d = dash
    drawPath(
        p, color.copy(alpha = 0.35f + 0.65f * intensity),
        style = Stroke(
            width = 3.2f * sc, cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * sc, 9f * sc), -d * 1.5f * sc),
        ),
    )
    val total = g.len[idx]
    for (k in 0 until 3) {
        val pos = (((phase + k / 3f) % 1f) + 1f) % 1f
        val o = g.meas[idx].getPosition(pos * total)
        drawCircle(color.copy(alpha = 0.30f * intensity), 7f * sc, o)
        drawCircle(Color.White.copy(alpha = 0.9f * intensity), 2.4f * sc, o)
    }
}

private fun DrawScope.drawPanels(g: HouseGeo, sc: Float, solar: Float, sunDeg: Float) {
    // Panel grid inside the roof trapezoid, by bilinear interpolation of its corners.
    val tl = Offset(168f, 120f); val tr = Offset(336f, 120f); val bl = Offset(104f, 202f); val br = Offset(308f, 202f)
    fun at(u: Float, v: Float): Offset {
        val top = Offset(lerp(tl.x, tr.x, u), lerp(tl.y, tr.y, u))
        val bot = Offset(lerp(bl.x, br.x, u), lerp(bl.y, br.y, u))
        return Offset(lerp(top.x, bot.x, v) * sc, lerp(top.y, bot.y, v) * sc)
    }
    val rows = 2; val cols = 4
    val gap = 0.035f
    for (r in 0 until rows) for (c in 0 until cols) {
        val u0 = c / cols.toFloat() + gap; val u1 = (c + 1) / cols.toFloat() - gap
        val v0 = r / rows.toFloat() + gap * 1.6f; val v1 = (r + 1) / rows.toFloat() - gap * 1.6f
        val q = Path().apply {
            val a = at(u0, v0); val b = at(u1, v0); val d = at(u1, v1); val c2 = at(u0, v1)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(d.x, d.y); lineTo(c2.x, c2.y); close()
        }
        drawPath(q, PANEL)
        drawPath(q, PANEL_LINE.copy(alpha = 0.8f), style = Stroke(1.1f * sc))
        // cell lines
        for (k in 1..2) {
            val a = at(lerp(u0, u1, k / 3f), v0); val b = at(lerp(u0, u1, k / 3f), v1)
            drawLine(PANEL_LINE.copy(alpha = 0.35f), a, b, strokeWidth = 0.8f * sc)
        }
        val a = at(u0, lerp(v0, v1, 0.5f)); val b = at(u1, lerp(v0, v1, 0.5f))
        drawLine(PANEL_LINE.copy(alpha = 0.35f), a, b, strokeWidth = 0.8f * sc)
    }
    // glint that slides across the roof while the sun is up
    if (solar > 0.03f) {
        val t = (sunDeg / 360f)
        val cx = lerp(90f, 350f, t) * sc
        clipPath(g.roof) {
            val band = Path().apply {
                moveTo(cx - 26f * sc, 120f * sc); lineTo(cx + 6f * sc, 120f * sc)
                lineTo(cx - 22f * sc, 202f * sc); lineTo(cx - 54f * sc, 202f * sc); close()
            }
            drawPath(band, Color.White.copy(alpha = 0.14f * solar))
        }
    }
}

private fun DrawScope.drawPylon(sc: Float, gridUp: Boolean, active: Float) {
    fun x(v: Float) = v * sc
    val col = if (gridUp) Color(0xFF7FB6D6) else Color(0xFF55616E)
    val s = Stroke(x(1.6f), cap = StrokeCap.Round)
    // legs
    drawLine(col, Offset(x(14f), x(292f)), Offset(x(26f), x(130f)), strokeWidth = x(2f), cap = StrokeCap.Round)
    drawLine(col, Offset(x(46f), x(292f)), Offset(x(34f), x(130f)), strokeWidth = x(2f), cap = StrokeCap.Round)
    // braces
    var y = 290f
    var left = true
    while (y > 150f) {
        val t0 = (292f - y) / 162f
        val t1 = (292f - (y - 24f)) / 162f
        val xl0 = lerp(14f, 26f, t0); val xr0 = lerp(46f, 34f, t0)
        val xl1 = lerp(14f, 26f, t1); val xr1 = lerp(46f, 34f, t1)
        drawLine(col.copy(alpha = 0.8f), Offset(x(if (left) xl0 else xr0), x(y)), Offset(x(if (left) xr1 else xl1), x(y - 24f)), strokeWidth = x(1.2f))
        drawLine(col.copy(alpha = 0.6f), Offset(x(xl1), x(y - 24f)), Offset(x(xr1), x(y - 24f)), strokeWidth = x(1.2f))
        left = !left
        y -= 24f
    }
    // cross arms and insulators
    drawLine(col, Offset(x(8f), x(140f)), Offset(x(52f), x(140f)), strokeWidth = x(2f), cap = StrokeCap.Round)
    drawLine(col, Offset(x(12f), x(160f)), Offset(x(48f), x(160f)), strokeWidth = x(2f), cap = StrokeCap.Round)
    val spark = Color(0xFF38BDF8).copy(alpha = 0.25f + 0.6f * active)
    for (px in listOf(10f, 30f, 50f)) drawCircle(if (gridUp) spark else Color(0xFF3A4350), x(2.6f), Offset(x(px), x(140f)))
    // wire up to the Grid bubble
    drawLine(col.copy(alpha = 0.7f), Offset(x(30f), x(130f)), Offset(x(30f), x(104f)), strokeWidth = s.width)
}

private fun DrawScope.bubble(
    m: TextMeasurer, sc: Float, bx: Float, by: Float, bw: Float,
    title: String, value: String, valueColor: Color, sub: String, pulse: Float,
) {
    val bh = 48f
    val cx = (bx + bw / 2f) * sc; val cy = (by + bh / 2f) * sc
    withTransform({ scale(pulse, pulse, Offset(cx, cy)) }) {
        drawRoundRect(Color(0xFF0E1A2A).copy(alpha = 0.82f), Offset(bx * sc, by * sc), Size(bw * sc, bh * sc), CornerRadius(22f * sc, 22f * sc))
        drawRoundRect(valueColor.copy(alpha = 0.35f), Offset(bx * sc, by * sc), Size(bw * sc, bh * sc), CornerRadius(22f * sc, 22f * sc), style = Stroke(1.2f * sc))
        val t = m.measure(title, TextStyle(fontSize = (10.5f * sc).toSp(), color = Color.White.copy(alpha = 0.65f)))
        drawText(t, topLeft = Offset((bx + 14f) * sc, (by + 7f) * sc))
        val v = m.measure(value, TextStyle(fontSize = (17f * sc).toSp(), fontWeight = FontWeight.Bold, color = valueColor, fontFeatureSettings = "tnum"))
        drawText(v, topLeft = Offset((bx + 14f) * sc, (by + 21f) * sc))
        if (sub.isNotEmpty()) {
            val s = m.measure(sub, TextStyle(fontSize = (8.5f * sc).toSp(), color = Color.White.copy(alpha = 0.45f)))
            drawText(s, topLeft = Offset((bx + bw - 14f) * sc - s.size.width, (by + 10f) * sc))
        }
    }
}
