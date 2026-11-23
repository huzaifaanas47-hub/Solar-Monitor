package com.medicglobe.knoxsolar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.AppViewModel
import com.medicglobe.knoxsolar.Day
import com.medicglobe.knoxsolar.f0
import com.medicglobe.knoxsolar.f1
import com.medicglobe.knoxsolar.f2

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.reloadHistory() }
    val days = vm.days
    val range = vm.histRange
    val today = LocalDate.now()
    var picking by remember { mutableStateOf(false) }
    val is7 = range != null && range.second == today && range.first == today.minusDays(6)
    val is30 = range != null && range.second == today && range.first == today.minusDays(29)
    val custom = range != null && !is7 && !is30

    if (picking) {
        val todayMs = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = range?.first?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = range?.second?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeInMillis: Long): Boolean = utcTimeInMillis <= todayMs
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val a = state.selectedStartDateMillis
                        val b = state.selectedEndDateMillis ?: a
                        if (a != null && b != null) {
                            vm.loadHistoryRange(
                                Instant.ofEpochMilli(a).atZone(ZoneOffset.UTC).toLocalDate(),
                                Instant.ofEpochMilli(b).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        picking = false
                    },
                ) { Text("Show") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(520.dp))
        }
    }

    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(top = 12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = is7, onClick = { vm.loadHistoryRange(today.minusDays(6), today) }, label = { Text("Last 7 days") })
                    FilterChip(selected = is30, onClick = { vm.loadHistoryRange(today.minusDays(29), today) }, label = { Text("Last 30 days") })
                    FilterChip(selected = custom, onClick = { picking = true }, label = { Text(if (custom) "Custom dates" else "Pick dates") })
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val ys = if (vm.years.isEmpty()) listOf(vm.histYear) else vm.years
                    for (y in ys) FilterChip(
                        selected = range == null && y == vm.histYear,
                        onClick = { vm.loadHistory(year = y) },
                        label = { Text(y.toString()) },
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = range == null && vm.histMonth == 0, onClick = { vm.loadHistory(month = 0) }, label = { Text("Whole year") })
                    MONTHS.forEachIndexed { i, m ->
                        FilterChip(selected = range == null && vm.histMonth == i + 1, onClick = { vm.loadHistory(month = i + 1) }, label = { Text(m) })
                    }
                }
                if (range != null) {
                    Text(
                        range.first.toString() + " to " + range.second.toString(),
                        color = KC.text, fontSize = 14.sp, style = Tabular, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        vm.histError?.let { err -> item { Note(err, KC.warn) } }
        if (vm.histLoading) item { Note("Loading…") }

        if (days.isNotEmpty()) {
            val gen = days.sumOf { it.gen }
            val buy = days.sumOf { it.buy }

            item {
                KCard("Totals") {
                    StatRow {
                        Stat("Generated", f1(gen), Modifier.weight(1f), KC.solar, "kWh")
                        Stat("Consumed", f1(days.sumOf { it.used }), Modifier.weight(1f), KC.load, "kWh")
                    }
                    StatRow {
                        Stat("Bought", f1(buy), Modifier.weight(1f), KC.grid, "kWh")
                        Stat("Sold", f1(days.sumOf { it.sell }), Modifier.weight(1f), KC.ok, "kWh")
                    }
                    StatRow {
                        Stat("Solar saved", f0(days.sumOf { it.savings }), Modifier.weight(1f), KC.ok, "Rs")
                        Stat("Grid cost", f0(days.sumOf { it.cost }), Modifier.weight(1f), KC.bad, "Rs")
                    }
                    StatRow {
                        Stat("Export income", f0(days.sumOf { it.revenue }), Modifier.weight(1f), KC.ok, "Rs")
                        Stat("CO₂ avoided", f0(days.sumOf { it.co2 }), Modifier.weight(1f), KC.text, "kg")
                    }
                }
            }

            val best = days.maxByOrNull { it.gen }
            item {
                KCard("Highlights") {
                    if (best != null) KV("Best day", best.date + ", " + f1(best.gen) + " kWh")
                    KV("Daily average", f1(gen / days.size) + " kWh")
                    KV("Days with readings", days.size.toString())
                    val chg = days.sumOf { it.chg }
                    val dis = days.sumOf { it.dis }
                    if (chg > 0.0 || dis > 0.0) {
                        KV("Battery charged", f1(chg) + " kWh")
                        KV("Battery gave", f1(dis) + " kWh")
                    }
                    KV("Solar share of supply", (if (gen + buy > 0) f0(gen / (gen + buy) * 100.0) else "0") + "%")
                }
            }

            item {
                val whole = range == null && vm.histMonth == 0
                val bars: List<Double> = if (whole) {
                    (1..12).map { m ->
                        val key = m.toString().padStart(2, '0')
                        days.filter { it.date.length >= 7 && it.date.substring(5, 7) == key }.sumOf { it.gen }
                    }
                } else days.map { it.gen }
                val top = bars.maxOrNull() ?: 0.0
                val hi = if (top > 0.0) bars.indexOf(top) else -1
                KCard(if (whole) "Generation per month (kWh)" else "Generation per day (kWh)") {
                    BarChart(bars, KC.solar, highlight = hi)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (whole) {
                            Text("Jan", color = KC.mute, fontSize = 10.sp); Text("Dec", color = KC.mute, fontSize = 10.sp)
                        } else {
                            Text(days.first().date.takeLast(5), color = KC.mute, fontSize = 10.sp)
                            Text(days.last().date.takeLast(5), color = KC.mute, fontSize = 10.sp)
                        }
                    }
                }
            }

            val peak = (best?.gen ?: 0.0).coerceAtLeast(0.0001)
            items(days.reversed()) { d -> DayRow(d, peak) }
        } else if (!vm.histLoading && vm.histError == null) {
            item { Note(if (range != null) "No readings between those dates." else "No data for this period.") }
        }
        item { Box(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DayRow(d: Day, peak: Double) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(d.date, color = KC.text, fontSize = 14.sp, style = Tabular, modifier = Modifier.weight(1f))
            Text("${f2(d.gen)} kWh", color = KC.solar, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
        }
        Box(
            Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(KC.track)
        ) {
            Box(
                Modifier.fillMaxWidth((d.gen / peak).toFloat().coerceIn(0.01f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp)).background(KC.solar)
            )
        }
        Text(
            "Used ${f1(d.used)}   Bought ${f1(d.buy)}   Sold ${f1(d.sell)} kWh",
            color = KC.mute, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
        )
    }
}
