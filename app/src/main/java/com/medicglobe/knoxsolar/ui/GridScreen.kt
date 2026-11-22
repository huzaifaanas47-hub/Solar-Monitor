package com.medicglobe.knoxsolar.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicglobe.knoxsolar.AppViewModel
import com.medicglobe.knoxsolar.f2
import com.medicglobe.knoxsolar.fmtDur

@Composable
fun GridScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadGrid() }
    val sm = vm.gridSummary

    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Today", "7 days", "30 days", "This year").forEachIndexed { i, l ->
                    FilterChip(selected = vm.gridRange == i, onClick = { vm.loadGrid(i) }, label = { Text(l) })
                }
            }
        }
        vm.gridError?.let { err -> item { Note(err, KC.warn) } }
        if (vm.gridLoading) item { Note("Loading…") }

        if (sm != null && vm.gridError == null) {
            item {
                KCard("OUTAGES (ignoring blips under 2 min)") {
                    StatRow {
                        Stat("Count", sm.count.toString(), Modifier.weight(1f), if (sm.count > 0) KC.bad else KC.ok)
                        Stat("Total downtime", fmtDur(sm.totalSec), Modifier.weight(1f))
                    }
                    StatRow {
                        Stat("Longest", fmtDur(sm.longestSec), Modifier.weight(1f))
                        Stat("Battery used", f2(sm.battKwh), Modifier.weight(1f), KC.ok, "kWh")
                    }
                    StatRow { Stat("Load served", f2(sm.loadKwh), Modifier.weight(1f), KC.load, "kWh") }
                }
            }
            if (vm.events.isEmpty()) item { Note("No outages in this period.") }
            items(vm.events) { e ->
                KCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            e.start.take(16) + " → " + (if (e.ongoing) "now" else (e.end?.takeLast(8)?.take(5) ?: "?")),
                            color = if (e.blip) KC.mute else KC.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        )
                        when {
                            e.ongoing -> Pill("ONGOING", KC.bad)
                            e.blip -> Pill("blip", KC.mute)
                            else -> Text(fmtDur(e.durSec), color = KC.text, fontSize = 14.sp)
                        }
                    }
                    if (!e.blip) Note("Battery ${f2(e.battKwh)} kWh · Load ${f2(e.loadKwh)} kWh")
                }
            }
        }
        item { Note("") }
    }
}
