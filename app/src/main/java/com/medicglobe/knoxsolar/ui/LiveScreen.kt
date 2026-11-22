package com.medicglobe.knoxsolar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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

@Composable
fun LiveScreen(vm: AppViewModel) {
    val t = vm.today?.let { withDerivedFlow(it) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        vm.todayError?.let { Note(it, KC.warn) }
        if (t == null) {
            if (vm.todayError == null) Note("Loading…")
            return@Column
        }
        val f = FlowState(t)

        // ── Status row ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val gv = if (t.gridV > 1.0) " · ${f0(t.gridV)} V" else if (t.gridHz > 1.0) " · ${f1(t.gridHz)} Hz" else ""
            when (t.gridUp) {
                1 -> Pill("Grid UP$gv", KC.ok)
                0 -> Pill("Grid DOWN$gv", KC.bad)
                else -> Pill("Grid unknown$gv", KC.mute)
            }
            val g = vm.guard
            if (g != null) Pill(if (g.ok) "Logging OK" else "Logging stuck", if (g.ok) KC.ok else KC.bad)
        }
        if (t.outageSince != null) {
            AccentCard(KC.bad, Modifier.fillMaxWidth()) {
                Text("Grid outage in progress", color = KC.bad, fontWeight = FontWeight.SemiBold)
                MonoSub("Since ${t.outageSince}" + if (t.outageSec > 0) " (${fmtDur(t.outageSec)})" else "")
            }
        }

        // ── Hero strip: Solar / Load / Battery now ──
        val solarOn = f.solarOn
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroTile("☀️", "Solar now", f2(t.pv), "kW", KC.solar,
                if (solarOn) "${f0(t.pv * 1000)} W output" else "Solar offline", Modifier.weight(1f))
            HeroTile("🏠", "Load now", f2(t.load), "kW", KC.load,
                when {
                    !f.gridUp -> "⛔ Grid down"
                    f.importing && !f.battDisch -> "Grid +${f2(f.gridAbs)} kW"
                    f.battDisch -> "Battery ${f2(f.battKw)} kW"
                    f.exporting -> "Exporting"
                    else -> "Solar = Load"
                }, Modifier.weight(1f))
            HeroTile("🔋", "Battery", f0(t.soc), "%", KC.sell,
                (if (t.soc >= 80) "↑ High" else if (t.soc >= 40) "→ Mid" else "↓ Low") +
                    (if (t.battV > 10) " · ${f1(t.battV)}V" else ""), Modifier.weight(1f))
        }

        // ── Live energy flow ──
        KCard("Live energy flow") {
            FlowDiagram(f)
            Box(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(f.statusLine(), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, lineHeight = 18.sp)
            }
            Box(Modifier.padding(top = 16.dp)) {
                Column {
                    MonoLabel("Battery charge level")
                    Box(Modifier.padding(top = 8.dp)) { BattBar(t.soc) }
                }
            }
        }

        // ── KPI tiles ──
        val selfKwh = minOf(t.gen, t.used)
        val selfPct = if (t.gen + t.buy > 0) Math.round(t.gen / (t.gen + t.buy) * 100.0).toInt() else 0
        val net = t.gen - t.used
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiTile("Generated", f2(t.gen), "kWh", KC.solar, "Lifetime: ${f1(t.total)} kWh", Modifier.weight(1f))
            KpiTile("Consumed", f2(t.used), "kWh", KC.load, "Live: ${f2(t.load)} kW", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiTile("Grid bought", f2(t.buy), "kWh", KC.grid,
                when {
                    !f.gridUp -> "⛔ Grid down"
                    f.importing && !f.battDisch -> "Now: ${f2(f.gridAbs)} kW"
                    else -> "Self-sufficient"
                }, Modifier.weight(1f))
            KpiTile("Sent to grid", f2(t.sell), "kWh", KC.sell, "Exported today", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiTile("Grid cost", f0(t.costGrid), "Rs", KC.bad, "${f2(t.buy)} kWh bought", Modifier.weight(1f))
            KpiTile("Solar saved", f0(t.savings), "Rs", KC.ok, "${f2(t.gen)} kWh generated", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiTile("Self-sufficiency", selfPct.toString(), "%", KC.ok, "Solar share of supply", Modifier.weight(1f))
            KpiTile("Net today", (if (net >= 0) "+" else "−") + f2(abs(net)), "kWh",
                if (net >= 0) KC.ok else KC.bad, "Generated − consumed", Modifier.weight(1f))
        }

        // ── Self-sufficiency donut + efficiency ──
        val eff = minOf(
            100,
            Math.round(selfPct * 0.6 + (if (t.soc > 30) 20 else 0) + (if (t.sell > 0) 10 else 0) + (if (t.buy < 1) 10 else 0)).toInt(),
        )
        KCard("Energy sources today") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Donut(selfPct.toDouble(), KC.solar, KC.grid)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$selfPct%", color = KC.solar, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        MonoLabel("solar")
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendRow("Solar generated", "${f2(t.gen)} kWh", KC.solar)
                    LegendRow("Home used", "${f2(t.used)} kWh", KC.load)
                    LegendRow("Grid bought", "${f2(t.buy)} kWh", KC.grid)
                    LegendRow("Sent to grid", "${f2(t.sell)} kWh", KC.sell)
                    LegendRow("Self-used solar", "${f2(selfKwh)} kWh", KC.ok)
                }
            }
        }
        KCard("Efficiency score") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    ScoreRing(eff.toDouble(), if (eff >= 70) KC.ok else if (eff >= 40) KC.warn else KC.bad)
                    Text("$eff", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = KC.text)
                }
                Column(Modifier.weight(1f)) {
                    KV("Self-sufficiency", "$selfPct%")
                    KV("Grid share", "${100 - selfPct}%")
                    KV("Battery", "${f0(t.soc)}%")
                    KV("Peak solar", "${f1(t.peakPv)} kW")
                }
            }
        }

        // ── Value + details ──
        KCard("Value today") {
            StatRow {
                Stat("Export revenue", f0(t.revenue), Modifier.weight(1f), KC.ok, "Rs")
                Stat("CO₂ avoided", f1(t.co2), Modifier.weight(1f), KC.text, "kg")
            }
        }
        KCard("Details") {
            KV("Solar panels", "${f0(t.pvV)} V · ${f1(t.pvA)} A")
            KV("Battery voltage", "${f1(t.battV)} V")
            KV("Grid voltage", if (t.gridV > 1.0) "${f1(t.gridV)} V" else if (t.gridUp == 0) "no supply" else "not reported")
            KV("Grid frequency", if (t.gridHz > 1.0) "${f1(t.gridHz)} Hz" else "not reported")
            KV("Inverter temperature", "${f0(t.temp)} °C")
            KV("Peak load today", "${f2(t.peakLoad)} kW")
            KV("Lowest battery today", "${f0(t.minSoc)}%")
            t.updatedAt?.let { KV("Last saved", it) }
        }
        Box(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun HeroTile(icon: String, label: String, value: String, unit: String, accent: Color, sub: String, modifier: Modifier) {
    AccentCard(accent, modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 20.sp)
            Box(Modifier.padding(top = 4.dp))
            MonoLabel(label)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text(value, color = accent, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(" $unit", color = KC.mute, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(sub, color = KC.mute2, fontSize = 9.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun KpiTile(label: String, value: String, unit: String, accent: Color, sub: String, modifier: Modifier) {
    AccentCard(accent, modifier) {
        MonoLabel(label)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            Text(value, color = accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(" $unit", color = KC.mute, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        MonoSub(sub)
    }
}

@Composable
private fun LegendRow(label: String, value: String, dot: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(end = 8.dp).background(dot, RoundedCornerShape(50)).padding(4.dp))
            Text(label, color = KC.mute, fontSize = 12.sp)
        }
        Text(value, color = KC.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
