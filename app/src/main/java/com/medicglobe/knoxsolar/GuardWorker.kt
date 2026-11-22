package com.medicglobe.knoxsolar

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class GuardWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("knox", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // 1) Wake the cron worker if it stopped, and see how it is doing.
        val g = Net.pingGuard() ?: return Result.success() // server unreachable: say nothing
        val age = if (g.isNull("age_sec")) -1L else g.optLong("age_sec", -1L)
        val stuck = g.optInt("err", 0) != 0 || age < 0 || age > 600

        if (stuck) {
            val last = prefs.getLong("stuck_notified_at", 0L)
            if (now - last > 3 * 3600_000L) {
                val why = if (g.isNull("last_message")) "No recent readings found." else g.optString("last_message")
                Notifier.show(
                    applicationContext, 1,
                    "Solar logging is stuck",
                    "The server is not saving inverter readings. Last note: $why"
                )
                prefs.edit().putLong("stuck_notified_at", now).apply()
            }
        } else if (prefs.getLong("stuck_notified_at", 0L) != 0L) {
            Notifier.show(applicationContext, 1, "Solar logging is back", "Readings are being saved again.")
            prefs.edit().remove("stuck_notified_at").apply()
        }

        // 2) Grid outage alert (the server already tracks outages).
        val j = Net.getJson(BuildConfig.BASE_URL + "proxy.php?action=get_today", 30_000)
        val row = j?.optJSONObject("row")
        if (row != null) {
            val since = if (row.isNull("outage_since")) null else row.getString("outage_since")
            val notified = prefs.getString("outage_since", null)
            val soc = row.optDouble("battery_soc", 0.0)
            if (since != null && since != notified) {
                Notifier.show(
                    applicationContext, 2,
                    "Grid is DOWN",
                    "Outage started at $since. Battery is at ${"%.0f".format(soc)}%."
                )
                prefs.edit().putString("outage_since", since).apply()
            } else if (since == null && notified != null) {
                Notifier.show(
                    applicationContext, 2,
                    "Grid is back",
                    "Power is restored. Battery is at ${"%.0f".format(soc)}%."
                )
                prefs.edit().remove("outage_since").apply()
            }
        }
        return Result.success()
    }
}
