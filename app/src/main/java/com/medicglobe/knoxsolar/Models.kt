package com.medicglobe.knoxsolar

import org.json.JSONObject
import java.util.Locale

// ── tolerant JSON helpers (MySQL returns numbers as strings, NULLs are common) ──
fun JSONObject.d(k: String): Double {
    val v = optDouble(k, 0.0)
    return if (v.isNaN() || v.isInfinite()) 0.0 else v
}

fun JSONObject.s(k: String): String? = if (isNull(k)) null else optString(k).ifEmpty { null }

fun JSONObject.flag(k: String): Boolean = when (val v = opt(k)) {
    is Boolean -> v
    is Number -> v.toInt() != 0
    is String -> v == "1" || v.equals("true", true)
    else -> false
}

/** Energy sent to the grid. The server stores it as `sent_to_grid`; `sell_energy` is the legacy name. */
fun JSONObject.sentKwh(): Double =
    if (has("sent_to_grid") && !isNull("sent_to_grid")) d("sent_to_grid") else d("sell_energy")

fun f0(v: Double): String = String.format(Locale.US, "%.0f", v)
fun f1(v: Double): String = String.format(Locale.US, "%.1f", v)
fun f2(v: Double): String = String.format(Locale.US, "%.2f", v)

fun fmtDur(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m"
}

/** Today's row from proxy.php?action=get_today (kW / kWh / Rs). */
data class Today(
    val date: String, val updatedAt: String?,
    val gen: Double, val used: Double, val buy: Double, val sell: Double, val total: Double,
    val pv: Double, val load: Double, val grid: Double,   // grid: + import, - export
    val soc: Double, val battKw: Double,                  // battKw: + discharging, - charging
    val battV: Double, val gridV: Double, val gridHz: Double, val gridUp: Int, // 1 up, 0 down, 2 unknown
    val pvV: Double, val pvA: Double, val temp: Double,
    val costGrid: Double, val savings: Double, val revenue: Double, val co2: Double,
    val peakPv: Double, val peakLoad: Double, val minSoc: Double,
    val outageSince: String?, val outageSec: Long,
) {
    companion object {
        fun from(o: JSONObject) = Today(
            date = o.optString("record_date"), updatedAt = o.s("updated_at"),
            gen = o.d("energy_today"), used = o.d("consumed_today"),
            buy = o.d("buy_energy"), sell = o.sentKwh(), total = o.d("energy_total"),
            pv = o.d("pv_power"), load = o.d("load_power"), grid = o.d("grid_power"),
            soc = o.d("battery_soc"), battKw = o.d("battery_power"),
            battV = o.d("battery_voltage"), gridV = o.d("grid_voltage"), gridHz = o.d("grid_frequency"),
            gridUp = if (o.isNull("grid_up")) 2 else o.optInt("grid_up", 2),
            pvV = o.d("pv_voltage"), pvA = o.d("pv_current"), temp = o.d("inverter_temp"),
            costGrid = o.d("cost_grid"), savings = o.d("savings_solar"),
            revenue = o.d("revenue_export"), co2 = o.d("co2_avoided"),
            peakPv = o.d("peak_pv_power"), peakLoad = o.d("peak_load_power"), minSoc = o.d("min_battery_soc"),
            outageSince = o.s("outage_since"), outageSec = o.optLong("outage_sec", 0L),
        )
    }
}

/**
 * One saved day from proxy.php?action=history.
 * Money and CO2 columns are sometimes 0 in old rows, so (like the web dashboard) they are
 * estimated from the energy figures and the current tariff when the server left them empty.
 */
data class Day(
    val date: String, val gen: Double, val used: Double, val buy: Double, val sell: Double,
    val net: Double, val soc: Double, val peakPv: Double,
    val cost: Double, val savings: Double, val revenue: Double, val co2: Double,
) {
    companion object {
        fun from(o: JSONObject, buyPrice: Double = 0.0): Day {
            val gen = o.d("energy_today")
            val buy = o.d("buy_energy")
            val sell = o.sentKwh()
            return Day(
                date = o.optString("record_date"), gen = gen, used = o.d("consumed_today"),
                buy = buy, sell = sell,
                net = if (o.has("net_difference") && !o.isNull("net_difference")) o.d("net_difference") else sell - buy,
                soc = o.d("battery_soc"), peakPv = o.d("peak_pv_power"),
                cost = o.d("cost_grid").takeIf { it > 0.0 } ?: Math.round(buy * buyPrice).toDouble(),
                savings = o.d("savings_solar").takeIf { it > 0.0 } ?: Math.round(gen * buyPrice).toDouble(),
                revenue = o.d("revenue_export"),
                co2 = o.d("co2_avoided").takeIf { it > 0.0 } ?: (gen * 0.4),
            )
        }
    }
}

/** One 5-minute bucket from proxy.php?action=cron_log. */
data class Point(
    val minute: Float, val time: String,
    val pv: Double, val load: Double, val grid: Double,
    val soc: Double, val batt: Double, val gridV: Double, val gridUp: Int,
) {
    companion object {
        fun from(o: JSONObject): Point? {
            val t = o.optString("ran_at")
            if (t.length < 16) return null
            val hh = t.substring(11, 13).toIntOrNull() ?: return null
            val mm = t.substring(14, 16).toIntOrNull() ?: return null
            return Point(
                (hh * 60 + mm).toFloat(), t.substring(11, 16),
                o.d("pv_power"), o.d("load_power"), o.d("grid_power"),
                o.d("battery_soc"), o.d("battery_power"), o.d("grid_voltage"),
                if (o.isNull("grid_up")) 2 else o.optInt("grid_up", 2),
            )
        }
    }
}

data class GridEvent(
    val start: String, val end: String?, val durSec: Long, val ongoing: Boolean,
    val battKwh: Double, val loadKwh: Double, val blip: Boolean,
) {
    companion object {
        fun from(o: JSONObject) = GridEvent(
            o.optString("started_at"), o.s("ended_at"), o.optLong("duration_sec", 0L),
            o.flag("ongoing"), o.d("battery_kwh"), o.d("load_kwh"), o.flag("blip"),
        )
    }
}

data class GridSummary(val count: Int, val totalSec: Long, val longestSec: Long, val battKwh: Double, val loadKwh: Double)

/** Result of cron_guard.php. */
data class Guard(
    val ok: Boolean, val ageSec: Long?, val lastStatus: String?, val lastMessage: String?,
    val lastRunAt: String?, val kicked: Boolean,
) {
    companion object {
        fun from(o: JSONObject): Guard {
            val age = if (o.isNull("age_sec")) null else o.optLong("age_sec", -1L)
            val healthy = o.optInt("err", 0) == 0 && age != null && age in 0..600
            return Guard(healthy, age, o.s("last_status"), o.s("last_message"), o.s("last_run_at"), o.flag("kicked"))
        }
    }
}

/** One month from proxy.php?action=monthly_summary. Empty money / CO2 columns are estimated like the web dashboard. */
data class MonthRow(
    val mo: Int, val name: String, val days: Int,
    val gen: Double, val use: Double, val buy: Double, val sent: Double,
    val cost: Double, val savings: Double, val co2: Double,
) {
    companion object {
        fun from(o: JSONObject, price: Double): MonthRow {
            val gen = o.d("gen"); val buy = o.d("buy")
            return MonthRow(
                mo = o.optInt("mo", 0), name = o.s("mon_name") ?: "", days = o.optInt("days", 0),
                gen = gen, use = o.d("use_kwh"), buy = buy, sent = o.d("sent"),
                cost = o.d("cost").takeIf { it > 0.0 } ?: Math.round(buy * price).toDouble(),
                savings = o.d("savings").takeIf { it > 0.0 } ?: Math.round(gen * price).toDouble(),
                co2 = o.d("co2").takeIf { it > 0.0 } ?: (gen * 0.4),
            )
        }
    }
}

/** One editable server setting (proxy.php?action=get_settings). */
data class SettingRow(val key: String, val label: String, val value: String, val numeric: Boolean)

/** Database health (proxy.php?action=db_summary). */
data class DbSummary(
    val alive: Boolean, val totalDays: String, val first: String?, val last: String?,
    val gen: Double, val savings: Double, val cost: Double, val co2: Double,
)

/** Last cron worker run (proxy.php?action=cron_status). */
data class CronLast(
    val ranAt: String, val status: String?, val pv: Double, val load: Double, val soc: Double, val gen: Double,
)
