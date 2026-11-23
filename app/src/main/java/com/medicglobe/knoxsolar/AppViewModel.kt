package com.medicglobe.knoxsolar

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.medicglobe.knoxsolar.ui.FlowEngine
import org.json.JSONObject
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private fun base() = Prefs.baseUrl(getApplication())
    private suspend fun get(path: String, timeout: Int = 30_000): JSONObject? =
        withContext(Dispatchers.IO) { Net.getJson(base() + path, timeout) }

    // ── Live ──
    var today by mutableStateOf<Today?>(null); private set
    var todayError by mutableStateOf<String?>(null); private set
    var refreshing by mutableStateOf(false); private set
    var guard by mutableStateOf<Guard?>(null); private set
    var lastUpdated by mutableStateOf(0L); private set          // when the last good reading arrived
    private var lastGuard = 0L

    /** Drives the energy-flow animation. Kept here so it carries on smoothly when you switch tabs. */
    val flow = FlowEngine()

    // System size and battery capacity come from the server's own settings.
    var systemKw by mutableStateOf<Double?>(null); private set
    var batteryKwh by mutableStateOf<Double?>(null); private set
    private var lastSystem = 0L

    // Today's power curve and outage summary, shown on the Live tab.
    var curve by mutableStateOf<List<Point>>(emptyList()); private set
    var gridToday by mutableStateOf<GridSummary?>(null); private set
    private var lastExtras = 0L

    fun refreshLive() = viewModelScope.launch {
        refreshing = true
        val j = get("proxy.php?action=get_today")
        val row = j?.optJSONObject("row")
        val now = System.currentTimeMillis()
        when {
            j == null -> todayError = "Cannot reach the server."
            row == null -> { todayError = "No reading saved for today yet."; today = null }
            else -> { today = Today.from(row); todayError = null; lastUpdated = now }
        }
        refreshing = false
        if (now - lastGuard > 60_000L) { lastGuard = now; loadGuard(false) }
        if (row != null && now - lastExtras > 120_000L) { lastExtras = now; loadExtras() }
        if ((systemKw == null || batteryKwh == null) && now - lastSystem > 300_000L) { lastSystem = now; loadSystem() }
    }

    private fun loadSystem() = viewModelScope.launch {
        val s = get("proxy.php?action=get_settings")?.optJSONObject("settings") ?: return@launch
        systemKw = s.optJSONObject("system_kw")?.d("setting_value")?.takeIf { it > 0.0 }
        batteryKwh = s.optJSONObject("battery_kw")?.d("setting_value")?.takeIf { it > 0.0 }
    }

    private fun loadExtras() = viewModelScope.launch {
        val day = LocalDate.now()
        val log = get("proxy.php?action=cron_log&date=$day")?.optJSONArray("rows")
        if (log != null) {
            curve = (0 until log.length()).mapNotNull { log.optJSONObject(it) }.mapNotNull { Point.from(it) }
        }
        val g = get("proxy.php?action=grid_events&from=$day&to=$day")
        val sm = g?.optJSONObject("summary")
        if (sm != null) {
            gridToday = GridSummary(
                sm.optInt("count", 0), sm.optLong("total_sec", 0), sm.optLong("longest_sec", 0),
                sm.d("battery_kwh"), sm.d("load_kwh"),
            )
        }
    }

    fun loadGuard(force: Boolean) = viewModelScope.launch {
        val g = withContext(Dispatchers.IO) { Net.guard(base(), force) }
        if (g != null) guard = Guard.from(g)
    }

    // ── History ──
    var years by mutableStateOf<List<Int>>(emptyList()); private set
    var histYear by mutableStateOf(LocalDate.now().year); private set
    var histMonth by mutableStateOf(0); private set   // 0 = whole year
    var days by mutableStateOf<List<Day>>(emptyList()); private set
    var histLoading by mutableStateOf(false); private set
    var histError by mutableStateOf<String?>(null); private set

    fun loadHistory(year: Int = histYear, month: Int = histMonth) = viewModelScope.launch {
        histYear = year; histMonth = month
        histLoading = true
        if (years.isEmpty()) {
            val y = get("proxy.php?action=years")?.optJSONArray("years")
            if (y != null) {
                years = (0 until y.length()).mapNotNull { y.opt(it).toString().toIntOrNull() }.sortedDescending()
            }
        }
        val j = get("proxy.php?action=history&year=$year&month=$month")
        val arr = j?.optJSONArray("rows")
        if (arr == null) {
            histError = "Could not load history."
        } else {
            histError = null
            days = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { Day.from(it) }.sortedBy { it.date }
        }
        histLoading = false
    }

    // ── Timeline ──
    var tlDate by mutableStateOf(LocalDate.now()); private set
    var points by mutableStateOf<List<Point>>(emptyList()); private set
    var tlIntervalMin by mutableStateOf(5); private set
    var tlLoading by mutableStateOf(false); private set
    var tlError by mutableStateOf<String?>(null); private set

    fun loadTimeline(date: LocalDate = tlDate) = viewModelScope.launch {
        tlDate = date
        tlLoading = true
        val j = get("proxy.php?action=cron_log&date=$date")
        val arr = j?.optJSONArray("rows")
        if (arr == null) {
            tlError = "Could not load the timeline."
        } else {
            tlError = null
            tlIntervalMin = ((j?.optInt("interval_sec", 300) ?: 300) / 60).coerceAtLeast(1)
            points = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull { Point.from(it) }
        }
        tlLoading = false
    }

    // ── Grid outages ──
    var gridRange by mutableStateOf(0); private set      // 0 today, 1 7 days, 2 30 days, 3 this year
    var events by mutableStateOf<List<GridEvent>>(emptyList()); private set
    var gridSummary by mutableStateOf<GridSummary?>(null); private set
    var gridLoading by mutableStateOf(false); private set
    var gridError by mutableStateOf<String?>(null); private set

    fun loadGrid(range: Int = gridRange) = viewModelScope.launch {
        gridRange = range
        gridLoading = true
        val to = LocalDate.now()
        val from = when (range) {
            0 -> to
            1 -> to.minusDays(6)
            2 -> to.minusDays(29)
            else -> LocalDate.of(to.year, 1, 1)
        }
        val j = get("proxy.php?action=grid_events&from=$from&to=$to")
        val arr = j?.optJSONArray("events")
        val sm = j?.optJSONObject("summary")
        if (arr == null || sm == null) {
            gridError = "Could not load grid events."
        } else {
            gridError = null
            events = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { GridEvent.from(it) }
                .sortedByDescending { it.start }
            gridSummary = GridSummary(
                sm.optInt("count", 0), sm.optLong("total_sec", 0), sm.optLong("longest_sec", 0),
                sm.d("battery_kwh"), sm.d("load_kwh"),
            )
        }
        gridLoading = false
    }

    // ── Tariff (Settings) ──
    var priceBuy by mutableStateOf(""); private set
    var priceSell by mutableStateOf(""); private set
    var priceMsg by mutableStateOf<String?>(null); private set

    fun loadPrices() = viewModelScope.launch {
        val j = get("proxy.php?action=get_prices") ?: return@launch
        priceBuy = trimNum(j.d("buy")); priceSell = trimNum(j.d("sell"))
    }

    fun savePrices(buy: String, sell: String) = viewModelScope.launch {
        val b = buy.toDoubleOrNull(); val s = sell.toDoubleOrNull()
        if (b == null || s == null || b <= 0 || s < 0) { priceMsg = "Enter valid numbers."; return@launch }
        val j = get("proxy.php?action=save_prices&buy=$b&sell=$s")
        priceMsg = if (j?.optBoolean("saved", false) == true) "Prices saved." else "Could not save prices."
        if (j != null) { priceBuy = trimNum(b); priceSell = trimNum(s) }
    }

    private fun trimNum(v: Double) = if (v == Math.floor(v)) f0(v) else f2(v)
}
