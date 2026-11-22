package com.medicglobe.knoxsolar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.AppViewModel
import com.medicglobe.knoxsolar.Today
import com.medicglobe.knoxsolar.f0
import com.medicglobe.knoxsolar.f1
import com.medicglobe.knoxsolar.f2
import com.medicglobe.knoxsolar.fmtDur
import kotlin.math.abs

@Composable
fun LiveScreen(vm: AppViewModel) {
    val t = vm.today
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        vm.todayError?.let { Note(it, KC.warn) }
        if (t == null) {
            if (vm.todayError == null) Note("Loading…")
            return@Column
        }

        // ── Status line ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            when (t.gridUp) {
                1 -> Pill("Grid UP", KC.ok)
                0 -> Pill("Grid DOWN", KC.bad)
                else -> Pill("Grid unknown", KC.mute)
            }
            val g = vm.guard
            if (g != null) {
                if (g.ok) Pill("Logging OK", KC.ok)
                else Pill("Logging stuck", KC.bad)
            }
        }
        if (t.outageSince != null) {
            KCard {
                Text("Grid outage in progress", color = KC.bad, fontWeight = FontWeight.SemiBold)
                Note("Since ${t.outageSince}" + if (t.outageSec > 0) " (${fmtDur(t.outageSec)})" else "")
            }
        }

        // ── Battery ──
        val socColor = when {
            t.soc > 50 -> KC.ok
            t.soc > 20 -> KC.warn
            else -> KC.bad
        }
        KCard("BATTERY") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SocRing(t.soc, socColor)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${f0(t.soc)}%", color = KC.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            t.battKw > 0.02 -> "Discharging ${f2(t.battKw)} kW"
                            t.battKw < -0.02 -> "Charging ${f2(-t.battKw)} kW"
                            else -> "Idle"
                        },
                        color = KC.mute, fontSize = 13.sp,
                    )
                }
            }
        }

        // ── Power now ──
        KCard("POWER NOW") {
            StatRow {
                Stat("Solar", f2(t.pv), Modifier.weight(1f), KC.solar, "kW")
                Stat("Load", f2(t.load), Modifier.weight(1f), KC.load, "kW")
            }
            StatRow {
                val gridLabel = when {
                    t.gridUp == 0 -> "Grid (down)"
                    t.grid > 0.01 -> "Grid import"
                    t.grid < -0.01 -> "Grid export"
                    else -> "Grid"
                }
                Stat(gridLabel, f2(abs(t.grid)), Modifier.weight(1f), KC.grid, "kW")
                Stat("Inverter temp", f0(t.temp), Modifier.weight(1f), KC.text, "°C")
            }
        }

        // ── Energy today ──
        KCard("TODAY") {
            StatRow {
                Stat("Generated", f2(t.gen), Modifier.weight(1f), KC.solar, "kWh")
                Stat("Consumed", f2(t.used), Modifier.weight(1f), KC.load, "kWh")
            }
            StatRow {
                Stat("Bought", f2(t.buy), Modifier.weight(1f), KC.grid, "kWh")
                Stat("Sold", f2(t.sell), Modifier.weight(1f), KC.ok, "kWh")
            }
        }

        // ── Money & CO2 ──
        KCard("VALUE TODAY") {
            StatRow {
                Stat("Solar saved", f0(t.savings), Modifier.weight(1f), KC.ok, "Rs")
                Stat("Grid cost", f0(t.costGrid), Modifier.weight(1f), KC.bad, "Rs")
            }
            StatRow {
                Stat("Export revenue", f0(t.revenue), Modifier.weight(1f), KC.ok, "Rs")
                Stat("CO₂ avoided", f1(t.co2), Modifier.weight(1f), KC.text, "kg")
            }
        }

        // ── Details ──
        KCard("DETAILS") {
            KV("Solar panels", "${f0(t.pvV)} V · ${f1(t.pvA)} A")
            KV("Battery voltage", "${f1(t.battV)} V")
            KV("Grid", if (t.gridUp == 0) "no supply" else "${f0(t.gridV)} V · ${f1(t.gridHz)} Hz")
            KV("Peak solar today", "${f2(t.peakPv)} kW")
            KV("Peak load today", "${f2(t.peakLoad)} kW")
            KV("Lowest battery today", "${f0(t.minSoc)}%")
            KV("Lifetime generation", "${f0(t.total)} kWh")
            t.updatedAt?.let { KV("Last saved", it) }
        }
    }
}
