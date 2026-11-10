package com.example.qrhelper

import com.example.qrhelper.domain.RouteDistanceMatrix
import com.example.qrhelper.domain.RouteStop
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a directed OSRM distance table for a small route.
 * This is called only after the driver explicitly taps Optimize.
 */
internal object RoadDistanceMatrixLoader {
    fun fetch(stops: List<RouteStop>): RouteDistanceMatrix? {
        if (stops.size !in 2..100) return null
        if (stops.any { it.latitude == null || it.longitude == null ||
                !it.latitude.isFinite() || !it.longitude.isFinite() ||
                it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0 }) return null

        val coordinates = stops.joinToString(";") { "${it.longitude},${it.latitude}" }
        val endpoint = "https://router.project-osrm.org/table/v1/driving/$coordinates?annotations=distance"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "DigiDrop-Android")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optString("code") != "Ok") return null
            val rows = json.optJSONArray("distances") ?: return null
            if (rows.length() != stops.size) return null

            val matrix = LinkedHashMap<Pair<String, String>, Double>()
            for (fromIndex in stops.indices) {
                val row = rows.optJSONArray(fromIndex) ?: return null
                if (row.length() != stops.size) return null
                for (toIndex in stops.indices) {
                    if (fromIndex == toIndex) continue
                    val distance = if (row.isNull(toIndex)) null else row.optDouble(toIndex)
                    if (distance == null || !distance.isFinite() || distance < 0.0) return null
                    matrix[stops[fromIndex].id to stops[toIndex].id] = distance
                }
            }
            RouteDistanceMatrix(matrix)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
