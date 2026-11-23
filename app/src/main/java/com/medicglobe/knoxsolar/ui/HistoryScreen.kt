package com.medicglobe.knoxsolar.ui

import android.widget.Toast
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.AppViewModel
import com.medicglobe.knoxsolar.Day
import com.medicglobe.knoxsolar.Export
import com.medicglobe.knoxsolar.f0
import com.medicglobe.knoxsolar.f1
import com.medicglobe.knoxsolar.f2
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val Tabular = TextStyle(fontFeatureSettings = "tnum")
private val ShortDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

// The Material date picker works in UTC midnight milliseconds.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun signed(v: Double, f: (Double) -> String) = (if (v > 0.0) "+" else "") + f(v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.reloadHistory() }
    val days = vm.days
    val ctx = LocalContext.current

    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PeriodChips(vm) }

        vm.histError?.let { err -> item { Note(err, KC.warn) } }
        if (vm.histLoading) item { Note("Loading…") }

        if (days.isNotEmpty()) {
            val gen = days.sumOf { it.gen }
            val buy = days.sumOf { it.buy }
            val sell = days.sumOf { it.sell }
            val net = days.sumOf { it.net }
            val whole = !vm.histCustom && vm.histMonth == 0

            item {
                KCard("Totals · ${days.size} record${if (days.size == 1) "" else "s"}") {
                    StatRow {
                        Stat("Generated", f1(gen), Modifier.weight(1f), KC.solar, "kWh")
                        Stat("Consumed", f1(days.sumOf { it.used }), Modifier.weight(1f), KC.load, "kWh")
                    }
                    StatRow {
                        Stat("Grid import", f1(buy), Modifier.weight(1f), KC.grid, "kWh")
                        Stat("Grid export", f1(sell), Modifier.weight(1f), KC.sell, "kWh")
                    }
                    StatRow {
                        Stat("Net grid", signed(net, ::f1), Modifier.weight(1f), if (net >= 0) KC.ok else KC.bad, "kWh")
                        Stat("Peak solar", f2(days.maxOf { it.peakPv }), Modifier.weight(1f), KC.amber, "kW")
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
                    val topImport = days.maxByOrNull { it.buy }
                    if (topImport != null && topImport.buy > 0.0) KV("Highest import", topImport.date + ", " + f1(topImport.buy) + " kWh")
                    val topExport = days.maxByOrNull { it.sell }
                    if (topExport != null && topExport.sell > 0.0) KV("Highest export", topExport.date + ", " + f1(topExport.sell) + " kWh")
                    KV("Daily average", f1(gen / days.size) + " kWh")
                    KV("Average battery", f0(days.sumOf { it.soc } / days.size) + "%")
                    KV("Days with readings", days.size.toString())
                    val f = vm.histFrom; val t = vm.histTo
                    if (f != null && t != null) {
                        val missing = (ChronoUnit.DAYS.between(f, t) + 1 - days.size).coerceAtLeast(0)
                        if (missing > 0) KV("Days without a reading", missing.toString())
                    }
                    KV("Solar share of supply", (if (gen + buy > 0) f0(gen / (gen + buy) * 100.0) else "0") + "%")
                }
            }

            item { ChartCard("Generation", days, whole, KC.solar, true) { it.gen } }
            item { ChartCard("Grid import", days, whole, KC.grid, false) { it.buy } }
            item { ChartCard("Grid export", days, whole, KC.sell, false) { it.sell } }

            if (whole) item { MonthTable(days) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val ok = Export.share(ctx, "knox_solar_history_${vm.histLabel()}.csv", "text/csv", Export.csv(days))
                            if (!ok) Toast.makeText(ctx, "Could not export", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Export CSV") }
                    OutlinedButton(
                        onClick = {
                            val ok = Export.share(ctx, "knox_solar_history_${vm.histLabel()}.json", "application/json", Export.json(days, vm.histLabel()))
                            if (!ok) Toast.makeText(ctx, "Could not export", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Export JSON") }
                }
            }

            val peak = (best?.gen ?: 0.0).coerceAtLeast(0.0001)
            items(days.reversed()) { d -> DayRow(d, peak) }
        } else if (!vm.histLoading && vm.histError == null) {
            item { Note(if (vm.histCustom) "No records between these dates." else "No data for this period.") }
        }
        item { Box(Modifier.height(8.dp)) }
    }
}

/** Year / month / quick-range chips plus the custom date-range picker. Shared by History and Graphs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodChips(vm: AppViewModel) {
    var picker by remember { mutableStateOf(false) }
    if (picker) {
        RangeDialog(
            initFrom = vm.histFrom, initTo = vm.histTo,
            onDismiss = { picker = false },
            onPick = { a, b -> picker = false; vm.loadRange(a, b) },
        )
    }
    Column(Modifier.padding(top = 12.dp)) {
                // Quick ranges + custom picker
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val today = LocalDate.now()
                    val from = vm.histFrom; val to = vm.histTo
                    val is7 = from == today.minusDays(6) && to == today
                    val is30 = from == today.minusDays(29) && to == today
                    FilterChip(selected = is7, onClick = { vm.loadRange(today.minusDays(6), today) }, label = { Text("Last 7 days") })
                    FilterChip(selected = is30, onClick = { vm.loadRange(today.minusDays(29), today) }, label = { Text("Last 30 days") })
                    FilterChip(
                        selected = vm.histCustom && !is7 && !is30,
                        onClick = { picker = true },
                        label = { Text("Custom dates") },
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val ys = if (vm.years.isEmpty()) listOf(vm.histYear) else vm.years
                    for (y in ys) FilterChip(
                        selected = !vm.histCustom && y == vm.histYear,
                        onClick = { vm.loadHistory(year = y) },
                        label = { Text(y.toString()) },
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = !vm.histCustom && vm.histMonth == 0, onClick = { vm.loadHistory(month = 0) }, label = { Text("Whole year") })
                    MONTHS.forEachIndexed { i, m ->
                        FilterChip(selected = !vm.histCustom && vm.histMonth == i + 1, onClick = { vm.loadHistory(month = i + 1) }, label = { Text(m) })
                    }
                }
                val f = vm.histFrom; val t = vm.histTo
                if (f != null && t != null) {
                    Text(
                        f.format(ShortDate) + "  –  " + t.format(ShortDate) + "   (" + (ChronoUnit.DAYS.between(f, t) + 1) + " days)",
                        color = KC.acc, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
}

/** Bar chart of one metric: per month for a whole year, otherwise per day. */
@Composable
private fun ChartCard(title: String, days: List<Day>, whole: Boolean, color: androidx.compose.ui.graphics.Color, highlightTop: Boolean, pick: (Day) -> Double) {
    val bars: List<Double> = if (whole) {
        (1..12).map { m ->
            val key = m.toString().padStart(2, '0')
            days.filter { it.date.length >= 7 && it.date.substring(5, 7) == key }.sumOf(pick)
        }
    } else days.map(pick)
    val top = bars.maxOrNull() ?: 0.0
    val hi = if (highlightTop && top > 0.0) bars.indexOf(top) else -1
    KCard(title + if (whole) " per month (kWh)" else " per day (kWh)") {
        if (top <= 0.0) {
            Note("Nothing recorded.")
        } else {
            BarChart(bars, color, highlight = hi)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (whole) {
                    Text("Jan", color = KC.mute, fontSize = 10.sp); Text("Dec", color = KC.mute, fontSize = 10.sp)
                } else {
                    Text(days.first().date.takeLast(5), color = KC.mute, fontSize = 10.sp)
                    Text("max " + f1(top) + " kWh", color = KC.mute, fontSize = 10.sp)
                    Text(days.last().date.takeLast(5), color = KC.mute, fontSize = 10.sp)
                }
            }
        }
    }
}

/** Monthly breakdown for a whole year (the web dashboard's Month tab). */
@Composable
private fun MonthTable(days: List<Day>) {
    KCard("Monthly breakdown (kWh)") {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            for ((i, h) in listOf("Month", "Gen", "Use", "Import", "Export", "Self").withIndex()) {
                Text(h, color = KC.mute, fontSize = 11.sp, modifier = Modifier.weight(if (i == 0) 1.1f else 1f))
            }
        }
        var any = false
        for (m in 1..12) {
            val key = m.toString().padStart(2, '0')
            val rows = days.filter { it.date.length >= 7 && it.date.substring(5, 7) == key }
            if (rows.isEmpty()) continue
            any = true
            val g = rows.sumOf { it.gen }; val b = rows.sumOf { it.buy }
            val self = if (g + b > 0) f0(g / (g + b) * 100.0) + "%" else "0%"
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                val cells = listOf(MONTHS[m - 1], f1(g), f1(rows.sumOf { it.used }), f1(b), f1(rows.sumOf { it.sell }), self)
                val colors = listOf(KC.text, KC.solar, KC.load, KC.grid, KC.sell, KC.ok)
                for (i in cells.indices) {
                    Text(cells[i], color = colors[i], fontSize = 12.sp, style = Tabular, modifier = Modifier.weight(if (i == 0) 1.1f else 1f))
                }
            }
        }
        if (!any) Note("Nothing recorded.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(initFrom: LocalDate?, initTo: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate, LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initFrom?.toUtcMillis(),
        initialSelectedEndDateMillis = initTo?.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = !utcTimeMillis.toUtcDate().isAfter(today)
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    state.selectedStartDateMillis?.let { s ->
                        val e = state.selectedEndDateMillis ?: s      // one tapped date = a single day
                        onPick(s.toUtcDate(), e.toUtcDate())
                    }
                },
            ) { Text("Show") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(520.dp))
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
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Used ${f1(d.used)}", color = KC.mute, fontSize = 12.sp)
            Text("Import ${f1(d.buy)}", color = KC.grid, fontSize = 12.sp)
            Text("Export ${f1(d.sell)}", color = KC.sell, fontSize = 12.sp)
            Text("Net ${signed(d.net, ::f1)}", color = if (d.net >= 0) KC.ok else KC.bad, fontSize = 12.sp)
        }
        Text(
            "Battery ${f0(d.soc)}%   Peak ${f2(d.peakPv)} kW   Cost Rs ${f0(d.cost)}   Saved Rs ${f0(d.savings)}   CO₂ ${f1(d.co2)} kg",
            color = KC.mute, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp),
        )
    }
}
