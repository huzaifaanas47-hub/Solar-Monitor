package com.medicglobe.knoxsolar.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.AppViewModel
import com.medicglobe.knoxsolar.f0
import com.medicglobe.knoxsolar.f1
import com.medicglobe.knoxsolar.f2

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadHistory() }
    val days = vm.days

    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ys = if (vm.years.isEmpty()) listOf(vm.histYear) else vm.years
                    for (y in ys) FilterChip(
                        selected = y == vm.histYear,
                        onClick = { vm.loadHistory(year = y) },
                        label = { Text(y.toString()) },
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = vm.histMonth == 0, onClick = { vm.loadHistory(month = 0) }, label = { Text("Whole year") })
                    MONTHS.forEachIndexed { i, m ->
                        FilterChip(selected = vm.histMonth == i + 1, onClick = { vm.loadHistory(month = i + 1) }, label = { Text(m) })
                    }
                }
            }
        }

        vm.histError?.let { err -> item { Note(err, KC.warn) } }
        if (vm.histLoading) item { Note("Loading…") }

        if (days.isNotEmpty()) {
            item {
                val gen = days.sumOf { it.gen }
                KCard("TOTALS") {
                    StatRow {
                        Stat("Generated", f1(gen), Modifier.weight(1f), KC.solar, "kWh")
                        Stat("Consumed", f1(days.sumOf { it.used }), Modifier.weight(1f), KC.load, "kWh")
                    }
                    StatRow {
                        Stat("Bought", f1(days.sumOf { it.buy }), Modifier.weight(1f), KC.grid, "kWh")
                        Stat("Sold", f1(days.sumOf { it.sell }), Modifier.weight(1f), KC.ok, "kWh")
                    }
                    StatRow {
                        Stat("Solar saved", f0(days.sumOf { it.savings }), Modifier.weight(1f), KC.ok, "Rs")
                        Stat("Grid cost", f0(days.sumOf { it.cost }), Modifier.weight(1f), KC.bad, "Rs")
                    }
                    StatRow {
                        Stat("Export revenue", f0(days.sumOf { it.revenue }), Modifier.weight(1f), KC.ok, "Rs")
                        Stat("CO₂ avoided", f0(days.sumOf { it.co2 }), Modifier.weight(1f), KC.text, "kg")
                    }
                }
            }
            item {
                val whole = vm.histMonth == 0
                val bars: List<Double> = if (whole) {
                    (1..12).map { m ->
                        val key = m.toString().padStart(2, '0')
                        days.filter { it.date.length >= 7 && it.date.substring(5, 7) == key }.sumOf { it.gen }
                    }
                } else days.map { it.gen }
                KCard(if (whole) "GENERATION PER MONTH (kWh)" else "GENERATION PER DAY (kWh)") {
                    BarChart(bars, KC.solar)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (whole) { Text("Jan", color = KC.mute, fontSize = 10.sp); Text("Dec", color = KC.mute, fontSize = 10.sp) }
                        else {
                            Text(days.first().date.takeLast(5), color = KC.mute, fontSize = 10.sp)
                            Text(days.last().date.takeLast(5), color = KC.mute, fontSize = 10.sp)
                        }
                    }
                }
            }
            items(days.reversed()) { d ->
                KCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(d.date, color = KC.text, fontWeight = FontWeight.SemiBold)
                        Text("${f2(d.gen)} kWh", color = KC.solar, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Used ${f1(d.used)}", color = KC.mute, fontSize = 13.sp)
                        Text("Bought ${f1(d.buy)}", color = KC.mute, fontSize = 13.sp)
                        Text("Sold ${f1(d.sell)}", color = KC.mute, fontSize = 13.sp)
                    }
                }
            }
        } else if (!vm.histLoading && vm.histError == null) {
            item { Note("No data for this period.") }
        }
        item { Note("") }
    }
}
