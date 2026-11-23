package com.medicglobe.knoxsolar

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Net {

    /** GET a URL and parse the body as JSON. Returns null on any failure. */
    fun getJson(url: String, readTimeoutMs: Int = 30_000): JSONObject? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = readTimeoutMs
            c.requestMethod = "GET"
            c.setRequestProperty("Cache-Control", "no-cache")
            c.setRequestProperty("Accept", "application/json")
            val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            c.disconnect()
            if (body.isNullOrBlank()) null else JSONObject(body)
        } catch (e: Exception) {
            null
        }
    }

    /** True when the server answered with a 2xx status (used for save calls whose body we do not need). */
    fun ok(url: String): Boolean = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.requestMethod = "GET"
        val good = c.responseCode in 200..299
        c.disconnect()
        good
    } catch (e: Exception) {
        false
    }

    /** Asks the server to check the cron worker and restart it if it has stopped. */
    fun guard(base: String, force: Boolean = false): JSONObject? =
        getJson(base + "cron_guard.php" + if (force) "?force=1" else "", 90_000)
}
