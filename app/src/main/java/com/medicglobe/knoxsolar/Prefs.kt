package com.medicglobe.knoxsolar

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("knox", Context.MODE_PRIVATE)

    fun normalize(u: String): String {
        val t = u.trim()
        return if (t.endsWith("/")) t else "$t/"
    }

    fun baseUrl(c: Context): String {
        val v = sp(c).getString("base_url", null)?.trim().orEmpty()
        return normalize(if (v.isEmpty()) BuildConfig.BASE_URL else v)
    }

    fun setBaseUrl(c: Context, v: String) {
        sp(c).edit().putString("base_url", normalize(v)).apply()
    }

    fun notifyOutage(c: Context) = sp(c).getBoolean("n_outage", true)
    fun setNotifyOutage(c: Context, v: Boolean) = sp(c).edit().putBoolean("n_outage", v).apply()

    fun notifyStuck(c: Context) = sp(c).getBoolean("n_stuck", true)
    fun setNotifyStuck(c: Context, v: Boolean) = sp(c).edit().putBoolean("n_stuck", v).apply()

    // ROI calculator inputs (-1 = never saved)
    fun roiCost(c: Context): Double = sp(c).getFloat("roi_cost", -1f).toDouble()
    fun roiSave(c: Context): Double = sp(c).getFloat("roi_save", -1f).toDouble()
    fun setRoi(c: Context, cost: Double, save: Double) =
        sp(c).edit().putFloat("roi_cost", cost.toFloat()).putFloat("roi_save", save.toFloat()).apply()
}
