package com.medicglobe.knoxsolar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.AppViewModel
import com.medicglobe.knoxsolar.f0
import com.medicglobe.knoxsolar.f1
import com.medicglobe.knoxsolar.f2
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadTimeline() }
    val pts = vm.points
    val date = vm.tlDate
    var cursor by remember(date) { mutableStateOf<Float?>(null) }
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        val todayMs = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeInMillis: Long): Boolean = utcTimeInMillis <= todayMs
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { ms ->
                            vm.loadTimeline(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        picking = false
                    },
                ) { Text("Show") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { vm.loadTimeline(date.minusDays(1)) }) { Text("◀") }
                TextButton(onClick = { picking = true }) {
                    Text(if (date == LocalDate.now()) "Today, $date" else date.toString(), color = KC.text)
                }
                OutlinedButton(onClick = { vm.loadTimeline(date.plusDays(1)) }, enabled = date.isBefore(LocalDate.now())) { Text("▶") }
            }
        }
        vm.tlError?.let { err -> item { Note(err, KC.warn) } }
        if (vm.tlLoading) item { Note("Loading…") }

        if (pts.isNotEmpty()) {
            val shaded = pts.filter { it.gridUp == 0 }.map { it.minute to it.minute + vm.tlIntervalMin }
            item {
                val maxKw = ceil(pts.maxOf { maxOf(it.pv, it.load, abs(it.grid)) }.coerceAtLeast(0.5)).toFloat()
                val minKw = pts.minOf { it.grid }.coerceAtMost(0.0).let { if (it < 0) -ceil(-it).toFloat() else 0f }
                KCard("Power (kW)") {
                    val picked = cursor?.let { c -> pts.minByOrNull { abs(it.minute - c) } }
                    Text(
                        if (picked != null)
                            picked.time + "   Solar " + f2(picked.pv) + "   Home " + f2(picked.load) + "   Grid " + f2(picked.grid) +
                                "   Battery " + (if (picked.batt >= 0) "gave " else "took ") + f2(abs(picked.batt)) + " kW, " + f0(picked.soc) + "%"
                        else "Touch the chart to see exact values.",
                        color = if (picked != null) KC.text else KC.mute, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    LineChart(
                        listOf(
                            Series("Solar", KC.solar, pts.map { it.minute to it.pv.toFloat() }, fill = true),
                            Series("Load", KC.load, pts.map { it.minute to it.load.toFloat() }),
                            Series("Grid", KC.grid, pts.map { it.minute to it.grid.toFloat() }),
                        ),
                        yMin = minKw, yMax = maxKw, shaded = shaded,
                        cursor = cursor, onCursor = { cursor = it },
                    )
                    HourAxis()
                    Legend(listOf("Solar" to KC.solar, "Load" to KC.load, "Grid (+ import / − export)" to KC.grid))
                    if (shaded.isNotEmpty()) Note("Red bands: grid down", KC.bad)
                }
            }
            item {
                val maxB = ceil(pts.maxOf { it.batt }.coerceAtLeast(0.5)).toFloat()
                val minB = pts.minOf { it.batt }.coerceAtMost(0.0).let { if (it < 0) -ceil(-it).toFloat() else 0f }
                val hrs = vm.tlIntervalMin / 60.0
                val charged = pts.sumOf { if (it.batt < 0) -it.batt * hrs else 0.0 }
                val gave = pts.sumOf { if (it.batt > 0) it.batt * hrs else 0.0 }
                KCard("Battery flow (kW)") {
                    LineChart(
                        listOf(Series("Battery", KC.sell, pts.map { it.minute to it.batt.toFloat() }, fill = true)),
                        yMin = minB, yMax = maxB, shaded = shaded, cursor = cursor, onCursor = { cursor = it },
                    )
                    HourAxis()
                    Text(
                        "Above the line the battery is giving power. Below it, the battery is charging.",
                        color = KC.mute, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                    Box(Modifier.padding(top = 8.dp))
                    KV("Charged this day", f2(charged) + " kWh")
                    KV("Gave this day", f2(gave) + " kWh")
                }
            }
            item {
                KCard("Battery (%)") {
                    LineChart(
                        listOf(Series("Battery", KC.ok, pts.map { it.minute to it.soc.toFloat() }, fill = true)),
                        yMin = 0f, yMax = 100f, shaded = shaded,
                        cursor = cursor,
                    )
                    HourAxis()
                }
            }
            items(pts.reversed()) { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(p.time, color = KC.text, fontSize = 13.sp)
                    Text("☀ ${f2(p.pv)}", color = KC.solar, fontSize = 13.sp)
                    Text("⌂ ${f2(p.load)}", color = KC.load, fontSize = 13.sp)
                    Text("⚡ ${f2(p.grid)}", color = KC.grid, fontSize = 13.sp)
                    Text("${f0(p.soc)}%", color = KC.ok, fontSize = 13.sp)
                    Text(
                        when (p.gridUp) { 1 -> "UP"; 0 -> "DOWN"; else -> "?" },
                        color = if (p.gridUp == 0) KC.bad else KC.mute, fontSize = 13.sp,
                    )
                }
            }
        } else if (!vm.tlLoading && vm.tlError == null) {
            item { Note("No readings for this day.") }
        }
        item { Note("") }
    }
}
