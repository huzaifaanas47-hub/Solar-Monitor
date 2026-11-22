package com.medicglobe.knoxsolar

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Net {

    /** GET a URL and parse the body as JSON. Returns null on any failure. */
    fun getJson(url: String, readTimeoutMs: Int = 70_000): JSONObject? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = readTimeoutMs
            c.requestMethod = "GET"
            c.setRequestProperty("Cache-Control", "no-cache")
            val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            c.disconnect()
            if (body.isNullOrBlank()) null else JSONObject(body)
        } catch (e: Exception) {
            null
        }
    }

    /** Asks the server to check the cron worker and restart it if it has stopped. */
    fun pingGuard(): JSONObject? = getJson(BuildConfig.BASE_URL + "cron_guard.php")
}
