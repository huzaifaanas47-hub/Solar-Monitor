package com.medicglobe.knoxsolar

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Turns the loaded history into a CSV / JSON file and hands it to the Android share sheet. */
object Export {

    fun csv(days: List<Day>): String {
        val sb = StringBuilder("Date,Gen kWh,Use kWh,Grid import kWh,Grid export kWh,Net kWh,Battery %,Peak PV kW,Cost PKR,Saved PKR,Export income PKR,CO2 kg\n")
        for (d in days) {
            sb.append(d.date).append(',')
                .append(f2(d.gen)).append(',').append(f2(d.used)).append(',')
                .append(f2(d.buy)).append(',').append(f2(d.sell)).append(',').append(f2(d.net)).append(',')
                .append(f0(d.soc)).append(',').append(f2(d.peakPv)).append(',')
                .append(f0(d.cost)).append(',').append(f0(d.savings)).append(',').append(f0(d.revenue)).append(',')
                .append(f2(d.co2)).append('\n')
        }
        return sb.toString()
    }

    fun json(days: List<Day>, label: String): String {
        val rows = JSONArray()
        for (d in days) {
            rows.put(
                JSONObject()
                    .put("date", d.date).put("gen_kwh", d.gen).put("use_kwh", d.used)
                    .put("grid_import_kwh", d.buy).put("grid_export_kwh", d.sell).put("net_kwh", d.net)
                    .put("battery_pct", d.soc).put("peak_pv_kw", d.peakPv)
                    .put("cost_pkr", d.cost).put("saved_pkr", d.savings)
                    .put("export_income_pkr", d.revenue).put("co2_kg", d.co2)
            )
        }
        return JSONObject()
            .put("exported", Instant.now().toString()).put("period", label)
            .put("count", days.size).put("records", rows)
            .toString(2)
    }

    /** Writes [content] to the app cache and opens the share sheet. Returns false if that failed. */
    fun share(ctx: Context, fileName: String, mime: String, content: String): Boolean = try {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeText(content) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Export history")
        if (ctx !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
        true
    } catch (e: Exception) {
        false
    }
}
