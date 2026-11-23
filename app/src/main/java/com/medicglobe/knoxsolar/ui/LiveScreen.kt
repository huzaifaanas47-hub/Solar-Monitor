package com.medicglobe.knoxsolar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.AppViewModel
import com.medicglobe.knoxsolar.Today
import com.medicglobe.knoxsolar.f0
import com.medicglobe.knoxsolar.f1
import com.medicglobe.knoxsolar.f2
import com.medicglobe.knoxsolar.fmtDur
import kotlin.math.abs
import kotlin.math.ceil

/**
 * The cron worker stores the inverter's raw grid reading, which is 0 whenever the grid is
 * simply feeding the house. The web dashboard works out the real flow from load / solar / battery
 * (buildFromDbRow in index.html). Do the same here so the flow diagram has something to animate.
 */
private fun withDerivedFlow(t: Today): Today {
    var grid = t.grid
    var batt = t.battKw
    if (t.gridUp == 0) {
        grid = 0.0                                   // grid down: nothing is imported or exported
        val deficit = t.load - t.pv
        if (deficit > 0.05 && batt < deficit - 0.05) batt = deficit   // the battery covers the rest
    } else if (abs(grid) < 0.01) {
        grid = t.load - t.pv - maxOf(0.0, batt)      // whatever solar + battery don't cover is grid import
        if (abs(grid) < 0.05) grid = 0.0
    }
    return t.copy(grid = grid, battKw = batt)
}

private val Tabular = TextStyle(fontFamily = KFont, fontFeatureSettings = "tnum")

/** The one sentence that answers "what is running my house right now?" */
private fun headline(f: FlowState): String = when {
    !f.gridUp -> when {
        f.battDisch && f.solarOn -> "Grid is down. Running on battery and solar"
        f.battDisch -> "Grid is down. Running on battery"
        f.solarOn -> "Grid is down. Running on solar"
        else -> "Grid is down"
    }
    f.importing && f.battDisch -> "Grid and battery are sharing the load"
    f.importing && f.solarOn -> "Grid and solar are sharing the load"
    f.importing -> "Running on the grid"
    f.exporting -> "Solar covers the house and sells the rest"
    f.battDisch && f.solarOn -> "Solar and battery are powering the house"
    f.battDisch -> "Battery is powering the house"
    f.solarOn -> "Solar is powering the house"
    else -> "Everything is quiet"
}

private fun subline(t: Today, f: FlowState): String? {
    val since = t.outageSince
    return when {
        !f.gridUp && since != null ->
            "Outage since " + since.takeLast(8).take(5) + (if (t.outageSec > 0) ", " + fmtDur(t.outageSec) + " so far" else "")
        else -> null
    }
}

private class Estimate(val title: String, val big: String, val caption: String)

/** Rough battery time from the charge level and the battery size set on your server. */
private fun estimate(t: Today, f: FlowState, capKwh: Double?): Estimate? {
    if (capKwh == null) return null
    val drain = when {
        f.battDisch -> f.battKw
        !f.gridUp -> maxOf(0.0, f.load - f.pv)
        else -> 0.0
    }
    if (drain >= 0.05) {
        val hours = capKwh * t.soc / 100.0 / drain
        val big = if (hours >= 48.0) "Over 2 days" else fmtDur((hours * 3600.0).toLong())
        return Estimate("Battery backup", big, "left at ${f2(drain)} kW. An estimate from the charge level.")
    }
    val rate = -f.battKw
    if (f.battCharg && rate >= 0.05 && t.soc < 99.0) {
        val hours = capKwh * (100.0 - t.soc) / 100.0 / rate
        return Estimate("Battery charging", fmtDur((hours * 3600.0).toLong()), "until full at ${f2(rate)} kW. An estimate.")
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(vm: AppViewModel) {
    val t = vm.today?.let { withDerivedFlow(it) }
    var cursor by remember { mutableStateOf<Float?>(null) }
    var view by remember { mutableStateOf(0) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        vm.todayError?.let { Note(it, KC.warn) }
        if (t == null) {
            if (vm.todayError == null) Note("Loading…")
            return@Column
        }
        val f = FlowState(t)

        StatusHeader(t, f, vm)

        Segmented(listOf("House", "Diagram"), view, { view = it }, Modifier.fillMaxWidth())
        if (view == 0) HouseFlow(vm.flow, f) else FlowDiagram(vm.flow, f)
        Text(
            f.statusLine(), color = KC.mute, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        KeyNumbers(t, f, vm.systemKw)

        estimate(t, f, vm.batteryKwh)?.let { e ->
            KCard(e.title) {
                Text(e.big, color = KC.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = Tabular)
                Text(e.caption, color = KC.mute, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
                BattBar(t.soc)
            }
        }

        // ── Today's power curve ──
        val pts = vm.curve
        KCard("Today’s power") {
            if (pts.size >= 2) {
                val picked = cursor?.let { c -> pts.minByOrNull { abs(it.minute - c) } }
                if (picked != null) {
                    Text(
                        picked.time + "   Solar " + f2(picked.pv) + "   Home " + f2(picked.load) + " kW   Battery " + f0(picked.soc) + "%",
                        color = KC.text, fontSize = 13.sp, style = Tabular,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                } else {
                    Text("Touch the chart to see exact values.", color = KC.mute, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                val maxKw = ceil(pts.maxOf { maxOf(it.pv, it.load) }.coerceAtLeast(1.0)).toFloat()
                val shaded = pts.filter { it.gridUp == 0 }.map { it.minute to it.minute + 5f }
                LineChart(
                    listOf(
                        Series("Solar", KC.solar, pts.map { it.minute to it.pv.toFloat() }, fill = true),
                        Series("Home", KC.load, pts.map { it.minute to it.load.toFloat() }),
                    ),
                    yMin = 0f, yMax = maxKw, shaded = shaded,
                    cursor = cursor, onCursor = { cursor = it },
                )
                HourAxis()
                Legend(listOf("Solar" to KC.solar, "Home" to KC.load))
                if (shaded.isNotEmpty()) Note("Red bands show when the grid was down.", KC.bad)
            } else {
                Note("The curve fills in as readings arrive during the day.")
            }
        }

        // ── Energy today ──
        val mx = maxOf(t.gen, t.used, t.buy, t.sell, 0.05)
        KCard("Energy today") {
            BarRow("Generated by solar", t.gen, "kWh", mx, KC.solar)
            BarRow("Used by the house", t.used, "kWh", mx, KC.load)
            BarRow("Bought from grid", t.buy, "kWh", mx, KC.grid)
            BarRow("Sent to grid", t.sell, "kWh", mx, KC.sell)
            Text(
                "Lifetime generation " + f1(t.total) + " kWh", color = KC.mute, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ── Money today ──
        val net = t.gen - t.used
        KCard("Money today") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumStat("Solar saved", t.savings, 0, "Rs", KC.ok, Modifier.weight(1f))
                NumStat("Grid bill", t.costGrid, 0, "Rs", KC.bad, Modifier.weight(1f))
                NumStat("Export income", t.revenue, 0, "Rs", KC.sell, Modifier.weight(1f))
            }
            Box(Modifier.height(10.dp))
            KV("Net energy today", (if (net >= 0) "+" else "−") + f2(abs(net)) + " kWh")
            KV("CO₂ avoided", f1(t.co2) + " kg")
        }

        // ── Where the energy comes from, and how well the day went ──
        val selfKwh = minOf(t.gen, t.used)
        val selfPct = if (t.gen + t.buy > 0) Math.round(t.gen / (t.gen + t.buy) * 100.0).toInt() else 0
        val eff = minOf(
            100,
            Math.round(selfPct * 0.6 + (if (t.soc > 30) 20 else 0) + (if (t.sell > 0) 10 else 0) + (if (t.buy < 1) 10 else 0)).toInt(),
        )
        KCard("Solar share today") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Donut(selfPct.toDouble(), KC.solar, KC.grid)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$selfPct%", color = KC.solar, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, style = Tabular)
                        Text("solar", color = KC.mute, fontSize = 11.sp)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendRow("Solar generated", "${f2(t.gen)} kWh", KC.solar)
                    LegendRow("Grid bought", "${f2(t.buy)} kWh", KC.grid)
                    LegendRow("Self-used solar", "${f2(selfKwh)} kWh", KC.ok)
                }
            }
            Box(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    ScoreRing(eff.toDouble(), if (eff >= 70) KC.ok else if (eff >= 40) KC.warn else KC.bad, diameter = 84.dp)
                    Text("$eff", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = KC.text, style = Tabular)
                }
                Column(Modifier.weight(1f)) {
                    Text("Efficiency score", color = KC.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    KV("Peak solar", "${f1(t.peakPv)} kW")
                    KV("Lowest battery", "${f0(t.minSoc)}%")
                }
            }
        }

        // ── Live inverter readings ──
        SectionLabel("Inverter readings")
        ParamTiles(t)
        KCard {
            KV("Peak load today", "${f2(t.peakLoad)} kW")
            KV("Lowest battery today", "${f0(t.minSoc)}%")
            t.updatedAt?.let { KV("Last saved on server", it) }
        }
        Box(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun StatusHeader(t: Today, f: FlowState, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
        Crossfade(headline(f), label = "headline") { h ->
            Text(
                h, color = if (!f.gridUp) KC.bad else KC.text,
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp,
            )
        }
        subline(t, f)?.let { Text(it, color = KC.mute, fontSize = 14.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val gv = if (t.gridV > 1.0) ", ${f0(t.gridV)} V" else if (t.gridHz > 1.0) ", ${f1(t.gridHz)} Hz" else ""
            when (t.gridUp) {
                1 -> DotPill("Grid up$gv", KC.ok)
                0 -> DotPill("Grid down$gv", KC.bad)
                else -> DotPill("Grid unknown$gv", KC.mute)
            }
            val g = vm.guard
            if (g != null) DotPill(if (g.ok) "Logging OK" else "Logging stuck", if (g.ok) KC.ok else KC.bad)
        }
        vm.gridToday?.let { g ->
            Text(
                if (g.count == 0) "No outages today"
                else "${g.count} outage${if (g.count > 1) "s" else ""} today, ${fmtDur(g.totalSec)} without grid",
                color = KC.mute, fontSize = 13.sp,
            )
        }
    }
}

/** Solar, home and battery as three bento tiles, each tinted by its own colour. */
@Composable
private fun KeyNumbers(t: Today, f: FlowState, systemKw: Double?) {
    val share = if (systemKw != null && systemKw > 0.0) (t.pv / systemKw).toFloat().coerceIn(0f, 1f) else null
    val pvSub = if (systemKw != null && systemKw > 0.0) "${f0(t.pv / systemKw * 100.0)}% of ${f1(systemKw)} kW" else f.solarSub()
    val battColor = if (t.soc >= 60) KC.ok else if (t.soc >= 30) KC.warn else KC.bad
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KeyTile("Solar", t.pv, 2, "kW", KC.solar, pvSub, share, Modifier.weight(1f).fillMaxHeight())
        KeyTile("Home", t.load, 2, "kW", KC.load, f.homeSub().replaceFirstChar { it.uppercase() }, null, Modifier.weight(1f).fillMaxHeight())
        KeyTile("Battery", t.soc, 0, "%", battColor, f.battSub().replaceFirstChar { it.uppercase() }, (t.soc / 100.0).toFloat().coerceIn(0f, 1f), Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun KeyTile(
    label: String, value: Double, decimals: Int, unit: String, color: Color,
    sub: String, bar: Float?, modifier: Modifier,
) {
    AccentCard(color, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
            Text(label, color = KC.mute, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp)) {
            AnimatedNumber(value, decimals, KC.text, 22.sp, FontWeight.ExtraBold)
            Text(" $unit", color = KC.mute, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (bar != null) {
            val frac = easedFloat(bar, 900)
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(KC.track)) {
                Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.01f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
            }
        }
        Text(sub, color = KC.mute, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun NumStat(label: String, value: Double, decimals: Int, unit: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = KC.mute, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedNumber(value, decimals, color, 22.sp)
            Text(" $unit", color = KC.mute, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun LegendRow(label: String, value: String, dot: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(end = 8.dp).size(8.dp).background(dot, RoundedCornerShape(50)))
            Text(label, color = KC.mute, fontSize = 12.sp)
        }
        Text(value, color = KC.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
    }
}
