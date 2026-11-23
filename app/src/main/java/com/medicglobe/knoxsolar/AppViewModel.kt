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
import android.net.Uri
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
    /** Custom date range. Both set = custom mode (year / month chips are ignored). */
    var histFrom by mutableStateOf<LocalDate?>(null); private set
    var histTo by mutableStateOf<LocalDate?>(null); private set
    val histCustom: Boolean get() = histFrom != null && histTo != null
    var days by mutableStateOf<List<Day>>(emptyList()); private set
    var histLoading by mutableStateOf(false); private set
    var histError by mutableStateOf<String?>(null); private set
    private var histSeq = 0   // only the newest request may write results, so quick taps can't show stale data

    fun loadHistory(year: Int = histYear, month: Int = histMonth) {
        histYear = year; histMonth = month
        histFrom = null; histTo = null
        fetchHistory("year=$year&month=$month")
    }

    fun loadRange(from: LocalDate, to: LocalDate) {
        val today = LocalDate.now()
        var a = if (from.isAfter(to)) to else from
        var b = if (from.isAfter(to)) from else to
        if (b.isAfter(today)) b = today
        if (a.isAfter(b)) a = b
        histFrom = a; histTo = b
        fetchHistory("date_from=$a&date_to=$b")
    }

    /** Re-fetch whatever is currently selected (used by the refresh button and when the tab opens). */
    fun reloadHistory() {
        val f = histFrom; val t = histTo
        if (f != null && t != null) loadRange(f, t) else loadHistory()
    }

    /** Short text used in file names and titles: 2026, 2026-11 or 2026-11-01_to_2026-11-20. */
    fun histLabel(): String {
        val f = histFrom; val t = histTo
        return when {
            f != null && t != null -> "${f}_to_$t"
            histMonth == 0 -> histYear.toString()
            else -> "$histYear-${histMonth.toString().padStart(2, '0')}"
        }
    }

    private suspend fun ensurePrices() {
        if (priceBuy.isNotEmpty()) return
        val j = get("proxy.php?action=get_prices") ?: return
        priceBuy = trimNum(j.d("buy")); priceSell = trimNum(j.d("sell"))
    }

    private fun fetchHistory(query: String) = viewModelScope.launch {
        val mine = ++histSeq
        histLoading = true
        if (years.isEmpty()) {
            val y = get("proxy.php?action=years")?.optJSONArray("years")
            if (y != null) {
                years = (0 until y.length()).mapNotNull { y.opt(it).toString().toIntOrNull() }.sortedDescending()
            }
        }
        ensurePrices()
        val j = get("proxy.php?action=history&$query")
        if (mine != histSeq) return@launch          // a newer request has taken over
        val arr = j?.optJSONArray("rows")
        if (arr == null) {
            histError = "Could not load history."
            days = emptyList()
        } else {
            histError = null
            val price = priceBuy.toDoubleOrNull() ?: 0.0
            days = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .map { Day.from(it, price) }.sortedBy { it.date }
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

    // ── Monthly totals (Savings tab) ──
    var monthly by mutableStateOf<List<MonthRow>>(emptyList()); private set
    var monthlyError by mutableStateOf<String?>(null); private set

    fun loadMonthly() = viewModelScope.launch {
        ensurePrices()
        val arr = get("proxy.php?action=monthly_summary&year=${LocalDate.now().year}")?.optJSONArray("months")
        if (arr == null) {
            monthlyError = "Could not load the monthly totals."
        } else {
            monthlyError = null
            val price = priceBuy.toDoubleOrNull() ?: 0.0
            monthly = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { MonthRow.from(it, price) }.sortedBy { it.mo }
        }
    }

    // ── Last 60 days, for the Insights tab (kept apart from the History selection) ──
    var insightDays by mutableStateOf<List<Day>>(emptyList()); private set
    var insightError by mutableStateOf<String?>(null); private set

    fun loadInsights() = viewModelScope.launch {
        ensurePrices()
        val to = LocalDate.now()
        val arr = get("proxy.php?action=history&date_from=${to.minusDays(59)}&date_to=$to")?.optJSONArray("rows")
        if (arr == null) {
            insightError = "Could not load recent days."
        } else {
            insightError = null
            val price = priceBuy.toDoubleOrNull() ?: 0.0
            insightDays = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { Day.from(it, price) }.sortedBy { it.date }
        }
    }

    // ── System tab: database health, cron status, editable server settings, grid diagnosis ──
    var db by mutableStateOf<DbSummary?>(null); private set
    var cron by mutableStateOf<CronLast?>(null); private set
    var cronLoaded by mutableStateOf(false); private set
    var serverSettings by mutableStateOf<List<SettingRow>>(emptyList()); private set
    var sysMsg by mutableStateOf<String?>(null); private set
    var sysError by mutableStateOf<String?>(null); private set
    var diag by mutableStateOf<List<String>>(emptyList()); private set
    var diagBusy by mutableStateOf(false); private set

    private val settingKeys = listOf("owner_name", "system_kw", "battery_kw", "co2_factor", "refresh_secs", "alert_batt_low", "alert_grid_high")
    private val settingDefaults = mapOf(
        "alert_batt_low" to SettingRow("alert_batt_low", "Battery low alert (%)", "20", true),
        "alert_grid_high" to SettingRow("alert_grid_high", "Grid import alert (kW)", "3", true),
    )

    fun loadSysInfo() = viewModelScope.launch {
        sysError = null
        val d = get("proxy.php?action=db_summary")
        if (d == null) sysError = "Could not reach the server." else {
            val s = d.optJSONObject("summary")
            db = DbSummary(
                d.flag("alive"), s?.s("total_days") ?: "0", s?.s("first_day"), s?.s("last_day"),
                s?.d("total_gen") ?: 0.0, s?.d("total_savings") ?: 0.0, s?.d("total_cost") ?: 0.0, s?.d("total_co2") ?: 0.0,
            )
        }
        val c = get("proxy.php?action=cron_status")
        if (c != null) {
            val l = c.optJSONObject("last")
            cron = if (l == null) null else CronLast(
                l.optString("ran_at"), l.s("status"), l.d("pv_power"), l.d("load_power"), l.d("battery_soc"), l.d("energy_today"),
            )
            cronLoaded = true
        }
        val st = get("proxy.php?action=get_settings")?.optJSONObject("settings")
        if (st != null) {
            serverSettings = settingKeys.mapNotNull { k ->
                val o = st.optJSONObject(k)
                if (o != null) {
                    val type = o.optString("setting_type", "text")
                    SettingRow(k, o.s("label") ?: k, o.optString("setting_value"), type == "int" || type == "float")
                } else settingDefaults[k]
            }
        }
    }

    fun saveSettings(values: Map<String, String>) = viewModelScope.launch {
        sysMsg = "Saving…"
        var ok = true
        for ((k, v) in values) {
            if (!withContext(Dispatchers.IO) { Net.ok(base() + "proxy.php?action=save_setting&key=" + Uri.encode(k) + "&val=" + Uri.encode(v)) }) ok = false
        }
        sysMsg = if (ok) "Settings saved." else "Some settings could not be saved."
        systemKw = null; batteryKwh = null; lastSystem = 0L   // re-read system size and battery capacity
        loadSysInfo()
    }

    fun diagnoseGrid() = viewModelScope.launch {
        diagBusy = true
        val j = get("proxy.php?action=debug_live_pars")
        val out = ArrayList<String>()
        if (j == null) {
            out.add("Diagnosis failed: no answer from the server.")
        } else if (j.flag("err")) {
            out.add(j.s("desc") ?: "No data available yet.")
        } else {
            out.add("Last live poll: " + (j.s("updated_at") ?: "—"))
            j.optJSONObject("detected")?.let { det ->
                val st = when (det.optInt("up", 2)) { 0 -> "DOWN"; 1 -> "UP"; else -> "UNKNOWN" }
                out.add("Detected grid state: $st" + (det.s("reason")?.let { " — $it" } ?: ""))
                out.add("Voltage parameter: " + (det.s("v_key") ?: "none found") + "   Frequency parameter: " + (det.s("f_key") ?: "none found"))
            }
            val likely = j.optJSONObject("likely_grid_related_params")
            if (likely != null && likely.length() > 0) {
                out.add("Parameters that look grid related:")
                likely.keys().forEach { k -> out.add("  $k = ${likely.opt(k)}") }
            } else {
                out.add("No grid / voltage / frequency parameters were found in the last poll.")
            }
        }
        diag = out
        diagBusy = false
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
