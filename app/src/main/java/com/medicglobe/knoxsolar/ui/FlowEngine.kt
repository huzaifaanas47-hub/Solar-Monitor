package com.medicglobe.knoxsolar.ui

import com.medicglobe.knoxsolar.Today
import com.medicglobe.knoxsolar.f0
import com.medicglobe.knoxsolar.f2
import com.medicglobe.knoxsolar.fmtDur
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

/** What is happening right now, as the server reported it. These are the targets the diagram glides towards. */
class FlowState(t: Today) {
    val pv = t.pv
    val load = t.load
    val grid = t.grid
    val battKw = t.battKw
    val soc = t.soc
    val gridUp = t.gridUp != 0
    val outageSec = t.outageSec

    val solarOn = pv > 0.05
    val battDisch = battKw > 0.05
    val battCharg = battKw < -0.05
    val importing = gridUp && grid > 0.05
    val exporting = gridUp && grid < -0.05
    val gridAbs = abs(grid)

    fun statusLine(): String {
        val parts = ArrayList<String>()
        if (!gridUp) parts.add("Grid outage" + if (outageSec > 0) " for " + fmtDur(outageSec) else "")
        if (solarOn) parts.add("Solar ${f2(pv)} kW")
        if (exporting) parts.add("Exporting ${f2(gridAbs)} kW")
        if (importing) parts.add("Importing ${f2(gridAbs)} kW")
        if (battCharg) parts.add("Charging ${f2(abs(battKw))} kW")
        if (battDisch) parts.add("Battery giving ${f2(abs(battKw))} kW")
        if (parts.isEmpty()) parts.add("System idle")
        return parts.joinToString("   ")
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

    fun gridSub(): String = when {
        !gridUp -> "grid down"
        exporting -> "exporting"
        importing -> "importing"
        else -> "idle"
    }

    fun battSub(): String = when {
        battDisch -> "giving " + f2(abs(battKw)) + " kW"
        battCharg -> "taking " + f2(abs(battKw)) + " kW"
        else -> "standby"
    }

    fun solarSub(): String = if (solarOn) f0(pv * 1000) + " W" else "offline"
}

/**
 * Drives the energy-flow animation.
 *
 * Why it is smooth:
 *  - every number and every on/off state is eased towards its target, never switched
 *  - easing uses exp(-dt/tau), so it looks the same at 60 Hz and 120 Hz
 *  - particles move by adding (speed x dt) to a phase that only ever wraps 0..1,
 *    so a change in power changes the speed, not the position
 *  - the clocks are wrapped small floats, so there is no precision loss on a phone
 *    that has been up for days
 */
class FlowEngine {
    var pv = 0f; private set
    var load = 0f; private set
    var grid = 0f; private set       // + import / - export
    var batt = 0f; private set       // + discharging / - charging
    var soc = 0f; private set
    var gridUp = 1f; private set     // 1 = up, 0 = down, eased

    var ringT = 0f; private set      // 0..2.4 s
    var pulseT = 0f; private set     // 0..2.6 s
    var blinkT = 0f; private set     // 0..1.4 s
    var sunDeg = 0f; private set

    /** Position of the particle stream on each pipe, 0..1. */
    val phase = FloatArray(PIPES)
    /** Marching-dash offset on each pipe, in diagram units (0..10). */
    val dash = FloatArray(PIPES)

    private var target: FlowState? = null
    var ready = false; private set

    val state: FlowState? get() = target

    fun setTarget(s: FlowState) {
        target = s
        if (!ready) {
            pv = s.pv.toFloat(); load = s.load.toFloat(); grid = s.grid.toFloat()
            batt = s.battKw.toFloat(); soc = s.soc.toFloat()
            gridUp = if (s.gridUp) 1f else 0f
            ready = true
        }
    }

    // ── derived, all continuous ──
    fun solarF(): Float = smooth(pv, 0.03f, 0.15f)

    /** 0..1 how visible each pipe is. 0 solar>home, 1 home<>grid, 2 solar>battery, 3 battery<>home */
    fun pipeI(i: Int): Float = when (i) {
        0 -> solarF()
        1 -> gridUp * smooth(abs(grid), 0.03f, 0.15f)
        2 -> smooth(max(0f, -batt), 0.03f, 0.15f) * solarF()
        else -> if (batt >= 0f) smooth(batt, 0.03f, 0.15f)
        else smooth(-batt, 0.03f, 0.15f) * (1f - solarF())   // charging with no sun: it comes from the grid
    }

    fun pipeKw(i: Int): Float = when (i) {
        0 -> pv
        1 -> abs(grid)
        2 -> max(0f, -batt)
        else -> abs(batt)
    }

    /** Signed direction, -1..1, in the pipe's own a>b sense. Passes smoothly through 0 when the flow reverses. */
    fun pipeDir(i: Int): Float = when (i) {
        1 -> -tanh(grid * 8f)      // export flows home>grid, import grid>home
        3 -> tanh(batt * 8f)       // discharge flows battery>home, grid charging home>battery
        else -> 1f
    }

    fun nodeAct(i: Int): Float = when (i) {
        0 -> solarF()
        1 -> 1f
        2 -> pipeI(1)
        else -> smooth(abs(batt), 0.03f, 0.15f)
    }

    fun step(dtRaw: Float) {
        val s = target ?: return
        val dt = dtRaw.coerceIn(0f, 0.05f)       // after a pause, never leap
        if (dt <= 0f) return

        val k = 1f - exp(-dt / 0.55f)
        pv += (s.pv.toFloat() - pv) * k
        load += (s.load.toFloat() - load) * k
        grid += (s.grid.toFloat() - grid) * k
        batt += (s.battKw.toFloat() - batt) * k
        soc += (s.soc.toFloat() - soc) * k
        val kg = 1f - exp(-dt / 0.25f)
        gridUp += ((if (s.gridUp) 1f else 0f) - gridUp) * kg

        ringT = (ringT + dt) % 2.4f
        pulseT = (pulseT + dt) % 2.6f
        blinkT = (blinkT + dt) % 1.4f
        sunDeg = (sunDeg + dt * 16f * solarF()) % 360f

        for (i in 0 until PIPES) {
            val v = pipeDir(i) * speed(pipeKw(i))
            phase[i] = wrap1(phase[i] + v * dt)
            dash[i] = (dash[i] + v * LEN[i] * dt) % 10f
        }
    }

    companion object {
        const val PIPES = 4
        private val LEN = floatArrayOf(50f, 50f, 140f, 54f)

        private fun speed(kw: Float) = 0.16f + 0.08f * min(kw, 6f)
        private fun wrap1(x: Float) = ((x % 1f) + 1f) % 1f

        fun smooth(x: Float, a: Float, b: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
