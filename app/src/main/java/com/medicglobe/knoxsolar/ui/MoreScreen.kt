package com.medicglobe.knoxsolar.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.AppViewModel
import com.medicglobe.knoxsolar.Day
import com.medicglobe.knoxsolar.Prefs
import com.medicglobe.knoxsolar.f0
import com.medicglobe.knoxsolar.f1
import com.medicglobe.knoxsolar.f2
import com.medicglobe.knoxsolar.fmtDur
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

private val MON = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val Tab = TextStyle(fontFeatureSettings = "tnum")

private fun money(v: Double): String = String.format(Locale.US, "%,.0f", v)
private fun sgn(v: Double, f: (Double) -> String) = (if (v > 0.0) "+" else "") + f(v)
private fun trimNum(v: Double) = if (v == Math.floor(v)) f0(v) else f2(v)

/** Savings, Graphs, System and Insights, reached from the "More" tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(vm: AppViewModel) {
    var sub by remember { mutableStateOf(0) }
    val names = listOf("Savings", "Graphs", "System", "Insights")
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            names.forEachIndexed { i, n -> FilterChip(selected = sub == i, onClick = { sub = i }, label = { Text(n) }) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (sub) {
                0 -> SavingsScreen(vm)
                1 -> GraphsScreen(vm)
                2 -> SystemScreen(vm)
                else -> InsightsScreen(vm)
            }
        }
    }
}

// ════════════════════════════════ Savings & ROI ════════════════════════════════

@Composable
private fun SavingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.loadMonthly(); vm.loadPrices() }
    val t = vm.today
    val buyP = vm.priceBuy.toDoubleOrNull() ?: 0.0
    val sellP = vm.priceSell.toDoubleOrNull() ?: 0.0
    val now = LocalDate.now()
    val thisMonth = vm.monthly.firstOrNull { it.mo == now.monthValue }
    val total = t?.total ?: 0.0
    val lifetimeValue = total * buyP

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (t == null) {
            Note("Waiting for today's reading…")
        } else {
            val save = if (t.savings > 0.0) t.savings else t.gen * buyP
            val exp = if (t.revenue > 0.0) t.revenue else t.sell * sellP
            val cost = if (t.costGrid > 0.0) t.costGrid else t.buy * buyP
            val net = save + exp - cost
            val co2 = if (t.co2 > 0.0) t.co2 else t.gen * 0.4
            val selfKwh = min(t.gen, t.used)
            val selfPct = if (t.gen + t.buy > 0.0) f0(t.gen / (t.gen + t.buy) * 100.0) else "0"

            KCard("Today") {
                StatRow {
                    Stat("Solar saved", money(save), Modifier.weight(1f), KC.ok, "Rs")
                    Stat("Export income", money(exp), Modifier.weight(1f), KC.sell, "Rs")
                }
                StatRow {
                    Stat("Grid cost", money(cost), Modifier.weight(1f), KC.bad, "Rs")
                    Stat("Net profit", sgn(net, ::money), Modifier.weight(1f), if (net >= 0) KC.ok else KC.bad, "Rs")
                }
                StatRow {
                    Stat("CO₂ avoided", f1(co2), Modifier.weight(1f), KC.text, "kg")
                    Stat("Trees equivalent", Math.round(total * 0.04).toString(), Modifier.weight(1f), KC.ok)
                }
            }

            KCard("Today's breakdown") {
                KV("Solar generated", f2(t.gen) + " kWh")
                KV("Home consumed", f2(t.used) + " kWh")
                KV("Grid imported", f2(t.buy) + " kWh")
                KV("Sent to grid", f2(t.sell) + " kWh")
                KV("Self-used solar", f2(selfKwh) + " kWh")
                KV("Net difference", sgn(t.sell - t.buy, ::f2) + " kWh")
                KV("Self-sufficiency", "$selfPct%")
            }

            OutlinedButton(onClick = {
                val text = "Knox Solar · ${t.date}\n" +
                    "Generated ${f2(t.gen)} kWh · Used ${f2(t.used)} kWh\n" +
                    "Imported ${f2(t.buy)} kWh · Exported ${f2(t.sell)} kWh\n" +
                    "Saved Rs ${money(save)} · Grid cost Rs ${money(cost)} · Net Rs ${money(net)}\n" +
                    "Battery ${f0(t.soc)}% · CO₂ avoided ${f1(co2)} kg"
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                try { ctx.startActivity(Intent.createChooser(send, "Share today's summary")) } catch (e: Exception) { }
            }, modifier = Modifier.fillMaxWidth()) { Text("Share today's summary") }
        }

        vm.monthlyError?.let { Note(it, KC.warn) }

        if (thisMonth != null) {
            KCard("This month · ${thisMonth.name}") {
                StatRow {
                    Stat("Generated", f1(thisMonth.gen), Modifier.weight(1f), KC.solar, "kWh")
                    Stat("Consumed", f1(thisMonth.use), Modifier.weight(1f), KC.load, "kWh")
                }
                StatRow {
                    Stat("Grid cost", money(thisMonth.cost), Modifier.weight(1f), KC.bad, "Rs")
                    Stat("Solar saved", money(thisMonth.savings), Modifier.weight(1f), KC.ok, "Rs")
                }
                StatRow {
                    Stat("Sent to grid", f1(thisMonth.sent), Modifier.weight(1f), KC.sell, "kWh")
                    Stat("CO₂ avoided", f1(thisMonth.co2), Modifier.weight(1f), KC.text, "kg")
                }
                Note("${thisMonth.days} days recorded · ${f1(thisMonth.buy)} kWh bought × Rs ${trimNum(buyP)}")
            }
        }

        if (vm.monthly.isNotEmpty()) {
            val bars = (1..12).map { m -> vm.monthly.firstOrNull { it.mo == m }?.gen ?: 0.0 }
            KCard("Monthly generation ${now.year} (kWh)") {
                BarChart(bars, KC.solar, highlight = now.monthValue - 1)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Jan", color = KC.mute, fontSize = 10.sp); Text("Dec", color = KC.mute, fontSize = 10.sp)
                }
            }
            KCard("Monthly breakdown") {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    for ((i, h) in listOf("Month", "Gen", "Use", "Import", "Export", "Saved Rs").withIndex()) {
                        Text(h, color = KC.mute, fontSize = 11.sp, modifier = Modifier.weight(if (i == 0) 1f else 1.05f))
                    }
                }
                for (m in vm.monthly.reversed()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        val cells = listOf(MON.getOrElse(m.mo - 1) { m.name }, f1(m.gen), f1(m.use), f1(m.buy), f1(m.sent), money(m.savings))
                        val cols = listOf(KC.text, KC.solar, KC.load, KC.grid, KC.sell, KC.ok)
                        for (i in cells.indices) Text(cells[i], color = cols[i], fontSize = 12.sp, style = Tab, modifier = Modifier.weight(if (i == 0) 1f else 1.05f))
                    }
                }
            }
        }

        // ── ROI calculator ──
        var costTxt by remember { mutableStateOf(Prefs.roiCost(ctx).let { if (it > 0.0) trimNum(it) else "450000" }) }
        var saveTxt by remember { mutableStateOf(Prefs.roiSave(ctx).let { if (it > 0.0) trimNum(it) else "" }) }
        LaunchedEffect(costTxt, saveTxt) {
            val c = costTxt.toDoubleOrNull()
            if (c != null && c > 0.0) Prefs.setRoi(ctx, c, saveTxt.toDoubleOrNull() ?: -1.0)
        }
        val sysCost = costTxt.toDoubleOrNull() ?: 0.0
        val monthSave = saveTxt.toDoubleOrNull() ?: (thisMonth?.savings?.takeIf { it > 0.0 } ?: 8500.0)
        KCard("ROI calculator") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = costTxt, onValueChange = { costTxt = it }, singleLine = true, label = { Text("System cost (Rs)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = saveTxt, onValueChange = { saveTxt = it }, singleLine = true, label = { Text("Saved per month (Rs)") },
                    placeholder = { Text(money(monthSave)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
            }
            Note("Leave “Saved per month” empty to use this month's actual savings.")
            if (sysCost > 0.0 && monthSave > 0.0) {
                val years = sysCost / monthSave / 12.0
                StatRow {
                    Stat("Payback", f1(years), Modifier.weight(1f), KC.amber, "years")
                    Stat("25-year return", money(monthSave * 12 * 25 - sysCost), Modifier.weight(1f), KC.ok, "Rs")
                }
                val frac = easedFloat((lifetimeValue / sysCost).toFloat().coerceIn(0f, 1f), 900)
                Text(
                    "Paid back so far: Rs ${money(lifetimeValue)} of Rs ${money(sysCost)} (${f0(lifetimeValue / sysCost * 100.0)}%)",
                    color = KC.text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(KC.track)) {
                    Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.01f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(KC.ok))
                }
                Note("Based on lifetime generation × your buy price. Months of lifetime value left: " +
                    (if (lifetimeValue >= sysCost) "none, it has paid for itself" else f0((sysCost - lifetimeValue) / monthSave)))
            }
        }

        if (t != null) {
            KCard("Lifetime") {
                KV("Total generated", f2(total) + " kWh")
                KV("Lifetime value", "Rs " + money(lifetimeValue))
                KV("CO₂ avoided", f0(total * 0.4) + " kg")
                KV("Trees equivalent", Math.round(total * 0.04).toString())
                KV("Peak solar today", f2(t.peakPv) + " kW")
                KV("Peak load today", f2(t.peakLoad) + " kW")
                KV("Lowest battery today", f0(t.minSoc) + "%")
                KV("Buy / sell price", "Rs ${trimNum(buyP)} / Rs ${trimNum(sellP)} per kWh")
            }
        }
        Box(Modifier.height(8.dp))
    }
}

// ════════════════════════════════ Graphs ════════════════════════════════

@Composable
private fun GraphsScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.reloadHistory() }
    val days = vm.days
    var cursor by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(days) { cursor = null }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PeriodChips(vm)
        vm.histError?.let { Note(it, KC.warn) }
        if (vm.histLoading) Note("Loading…")

        if (days.size >= 2) {
            val n = days.size
            fun xs(i: Int) = i * 1440f / (n - 1)
            val idx = cursor?.let { c -> (c / 1440f * (n - 1)).roundToInt().coerceIn(0, n - 1) }
            val picked: Day? = idx?.let { days[it] }
            val top1 = ceil(maxOf(days.maxOf { it.gen }, days.maxOf { it.buy }, days.maxOf { it.used }, 1.0)).toFloat()
            val top2 = ceil(maxOf(days.maxOf { it.buy }, days.maxOf { it.sell }, 1.0)).toFloat()

            KCard("Solar vs grid vs home (kWh per day)") {
                Text(
                    if (picked != null) picked.date + "   Solar " + f1(picked.gen) + "   Grid " + f1(picked.buy) + "   Home " + f1(picked.used)
                    else "Touch the chart to see exact values.",
                    color = if (picked != null) KC.text else KC.mute, fontSize = 13.sp, style = Tab,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                LineChart(
                    listOf(
                        Series("Solar", KC.solar, days.mapIndexed { i, d -> xs(i) to d.gen.toFloat() }, fill = true),
                        Series("Grid import", KC.grid, days.mapIndexed { i, d -> xs(i) to d.buy.toFloat() }),
                        Series("Home use", KC.load, days.mapIndexed { i, d -> xs(i) to d.used.toFloat() }),
                    ),
                    yMin = 0f, yMax = top1, cursor = cursor, onCursor = { cursor = it },
                )
                DateEnds(days)
                Legend(listOf("Solar" to KC.solar, "Grid import" to KC.grid, "Home use" to KC.load))
            }

            KCard("Grid import vs export (kWh per day)") {
                Text(
                    if (picked != null) picked.date + "   Import " + f1(picked.buy) + "   Export " + f1(picked.sell) + "   Net " + sgn(picked.net, ::f1)
                    else "Touch the chart to see exact values.",
                    color = if (picked != null) KC.text else KC.mute, fontSize = 13.sp, style = Tab,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                LineChart(
                    listOf(
                        Series("Import", KC.grid, days.mapIndexed { i, d -> xs(i) to d.buy.toFloat() }, fill = true),
                        Series("Export", KC.sell, days.mapIndexed { i, d -> xs(i) to d.sell.toFloat() }),
                    ),
                    yMin = 0f, yMax = top2, cursor = cursor, onCursor = { cursor = it },
                )
                DateEnds(days)
                Legend(listOf("Import" to KC.grid, "Export" to KC.sell))
            }

            val share = days.map { if (it.gen + it.buy > 0.0) (it.gen / (it.gen + it.buy) * 100.0).toFloat() else 0f }
            KCard("Solar share per day (%)") {
                LineChart(
                    listOf(Series("Solar share", KC.ok, share.mapIndexed { i, v -> xs(i) to v }, fill = true)),
                    yMin = 0f, yMax = 100f, cursor = cursor, onCursor = { cursor = it },
                )
                DateEnds(days)
                Note(
                    if (idx != null) days[idx].date + ": " + f0(share[idx].toDouble()) + "% of supply came from solar"
                    else "How much of each day's supply came from solar instead of the grid.",
                )
            }
        } else if (!vm.histLoading && vm.histError == null) {
            Note(if (days.isEmpty()) "No data for this period." else "Pick a longer period to draw a graph.")
        }
        Box(Modifier.height(8.dp))
    }
}

@Composable
private fun DateEnds(days: List<Day>) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(days.first().date.takeLast(5), color = KC.mute, fontSize = 10.sp)
        Text(days.last().date.takeLast(5), color = KC.mute, fontSize = 10.sp)
    }
}

// ════════════════════════════════ System ════════════════════════════════

@Composable
private fun SystemScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadSysInfo(); vm.loadGuard(false) }
    val edits = remember(vm.serverSettings) {
        mutableStateMapOf<String, String>().also { m -> vm.serverSettings.forEach { m[it.key] = it.value } }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        vm.sysError?.let { Note(it, KC.warn) }

        KCard("Data logger") {
            val g = vm.guard
            if (g == null) Note("Checking…") else {
                Pill(if (g.ok) "Working" else "Stuck", if (g.ok) KC.ok else KC.bad)
                g.ageSec?.let { KV("Last reading", fmtDur(it) + " ago") }
            }
            vm.cron?.let { c ->
                KV("Last run", c.ranAt)
                c.status?.let { KV("Status", it) }
                KV("Solar / Home", f2(c.pv) + " / " + f2(c.load) + " kW")
                KV("Battery", f0(c.soc) + "%")
                KV("Generated", f2(c.gen) + " kWh")
            }
            if (vm.cron == null && vm.cronLoaded) Note("No cron runs recorded yet. Check that the scheduled task is running on the server.", KC.warn)
            OutlinedButton(onClick = { vm.loadGuard(true) }, modifier = Modifier.padding(top = 8.dp)) { Text("Restart logger now") }
        }

        vm.db?.let { d ->
            KCard("Database") {
                Pill(if (d.alive) "Connected" else "Error", if (d.alive) KC.ok else KC.bad)
                KV("Days stored", d.totalDays)
                KV("First record", d.first ?: "—")
                KV("Last record", d.last ?: "—")
                KV("Lifetime generation", f1(d.gen) + " kWh")
                KV("Total savings", "Rs " + money(d.savings))
                KV("Total grid cost", "Rs " + money(d.cost))
                KV("CO₂ avoided", f1(d.co2) + " kg")
            }
        }

        if (vm.serverSettings.isNotEmpty()) {
            KCard("System settings") {
                for (row in vm.serverSettings) {
                    OutlinedTextField(
                        value = edits[row.key] ?: row.value,
                        onValueChange = { edits[row.key] = it },
                        singleLine = true, label = { Text(row.label) },
                        keyboardOptions = KeyboardOptions(keyboardType = if (row.numeric) KeyboardType.Decimal else KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { vm.saveSettings(edits.toMap()) }) { Text("Save settings") }
                    vm.sysMsg?.let { Note(it) }
                }
            }
        }

        KCard("Grid detection") {
            Note("Shows which inverter parameters the server uses to decide whether the grid is up. Useful if outages are missed or reported wrongly.")
            OutlinedButton(onClick = { vm.diagnoseGrid() }, enabled = !vm.diagBusy) { Text(if (vm.diagBusy) "Checking…" else "Diagnose grid") }
            for (line in vm.diag) Text(line, color = KC.text, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Box(Modifier.height(8.dp))
    }
}

// ════════════════════════════════ Insights ════════════════════════════════

@Composable
private fun StatusRow(label: String, text: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, color = KC.mute, fontSize = 14.sp)
        Pill(text, color)
    }
}

@Composable
private fun InsightsScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadInsights(); vm.loadGuard(false); vm.loadPrices() }
    val t = vm.today
    val buyP = vm.priceBuy.toDoubleOrNull() ?: 0.0
    val today = LocalDate.now().toString()
    val done = vm.insightDays.filter { it.date < today && it.gen > 0.0 }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        vm.insightError?.let { Note(it, KC.warn) }

        if (done.size >= 3) {
            val last7 = done.takeLast(7)
            val avg7 = last7.sumOf { it.gen } / last7.size

            KCard("Tomorrow's estimate") {
                Text(f1(avg7) + " kWh", color = KC.solar, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = Tab)
                Text(
                    "Average of your last ${last7.size} full days (${f1(last7.minOf { it.gen })} to ${f1(last7.maxOf { it.gen })} kWh). " +
                        "A simple average, so weather can move it a lot." +
                        if (buyP > 0.0) " At your buy price that is about Rs ${money(avg7 * buyP)} of grid power avoided." else "",
                    color = KC.mute, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (done.size >= 14) {
                val bestWeek = done.windowed(7).maxOf { w -> w.sumOf { it.gen } / 7.0 }
                val drop = if (bestWeek > 0.0) (1.0 - avg7 / bestWeek) * 100.0 else 0.0
                KCard("Panel check") {
                    if (drop > 15.0) {
                        Pill("Below your best", KC.warn)
                        Note("Your last 7 days average ${f1(avg7)} kWh, ${f0(drop)}% under your best week (${f1(bestWeek)} kWh). " +
                            "Cloudy weather is the usual reason; if the sky was clear, dust or shade on the panels is worth checking.")
                    } else {
                        Pill("Looks normal", KC.ok)
                        Note("Your last 7 days average ${f1(avg7)} kWh, within 15% of your best week (${f1(bestWeek)} kWh).")
                    }
                }

                fun share(l: List<Day>): Double {
                    val g = l.sumOf { it.gen }; val b = l.sumOf { it.buy }
                    return if (g + b > 0.0) g / (g + b) * 100.0 else 0.0
                }
                val prev7 = done.dropLast(7).takeLast(7)
                if (prev7.size == 7) {
                    val a = share(last7); val b = share(prev7)
                    KCard("Solar share trend") {
                        StatRow {
                            Stat("Last 7 days", f0(a), Modifier.weight(1f), KC.solar, "%")
                            Stat("Week before", f0(b), Modifier.weight(1f), KC.load, "%")
                        }
                        Note(
                            if (a - b >= 2.0) "You are using more of your own solar than the week before."
                            else if (b - a >= 2.0) "You leaned on the grid more than the week before."
                            else "About the same as the week before."
                        )
                    }
                }
            }

            val best = done.maxByOrNull { it.gen }
            val worst = done.minByOrNull { it.gen }
            KCard("Last ${done.size} days") {
                if (best != null) KV("Best day", best.date + ", " + f1(best.gen) + " kWh")
                if (worst != null) KV("Weakest day", worst.date + ", " + f1(worst.gen) + " kWh")
                KV("Daily average", f1(done.sumOf { it.gen } / done.size) + " kWh")
                KV("Average grid import", f1(done.sumOf { it.buy } / done.size) + " kWh")
                KV("Average export", f1(done.sumOf { it.sell } / done.size) + " kWh")
                if (buyP > 0.0) KV("Average saved per day", "Rs " + money(done.sumOf { it.savings } / done.size))
            }
        } else {
            Note(if (vm.insightError == null) "Insights appear after a few full days of recorded data." else "")
        }

        if (t != null) {
            KCard("Right now") {
                val g = vm.guard
                if (g != null) StatusRow("Data logger", if (g.ok) "Working" else "Stuck", if (g.ok) KC.ok else KC.bad)
                StatusRow("Solar", if (t.pv > 0.05) "Producing " + f2(t.pv) + " kW" else "Idle", if (t.pv > 0.05) KC.solar else KC.mute)
                StatusRow(
                    "Battery", f0(t.soc) + "%" + if (t.battKw < -0.05) " charging" else if (t.battKw > 0.05) " discharging" else "",
                    if (t.soc < 20.0) KC.bad else KC.ok,
                )
                StatusRow(
                    "Grid", if (t.gridUp == 0) "Down" else if (t.gridUp == 1) "Up" else "Unknown",
                    if (t.gridUp == 0) KC.bad else if (t.gridUp == 1) KC.ok else KC.warn,
                )
                StatusRow("Inverter temperature", f0(t.temp) + " °C", if (t.temp >= 60.0) KC.warn else KC.ok)
            }

            val tips = ArrayList<String>()
            if (t.soc >= 95.0 && t.pv > t.load + 0.3) tips.add("The battery is full and solar is beating your load by ${f1(t.pv - t.load)} kW. Good moment to run heavy appliances.")
            if (t.gridUp == 0) tips.add("The grid is down. Keep heavy loads off to make the battery last.")
            if (t.soc < 20.0 && t.pv < 0.05) tips.add("The battery is low and there is no sun. Only essential loads until the grid or sun returns.")
            if (t.gridUp == 1 && t.grid > 2.0) tips.add("You are importing ${f1(t.grid)} kW from the grid. Check for a large load running.")
            if (tips.isNotEmpty()) {
                KCard("Tips for now") { for (tip in tips) Note(tip, KC.text) }
            }
        }
        Box(Modifier.height(8.dp))
    }
}
