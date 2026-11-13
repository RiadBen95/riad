package com.riad.voyage.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Lecture des réponses JSON de l'API Travelpayouts / Aviasales. */
object Parsers {

    fun places(body: String): List<Place> {
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val code = o.str("code")
            if (code.isBlank()) return@mapNotNull null
            Place(
                code = code,
                name = o.str("name").ifBlank { code },
                country = o.str("country_name"),
                type = o.str("type").ifBlank { "city" },
                cityName = o.str("city_name").ifBlank { null },
                lat = o.optJSONObject("coordinates")?.optDouble("lat")?.takeIf { !it.isNaN() },
                lon = o.optJSONObject("coordinates")?.optDouble("lon")?.takeIf { !it.isNaN() },
            )
        }
    }

    fun flights(body: String): List<Flight> {
        val root = JSONObject(body)
        checkSuccess(root)
        val arr = root.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val price = o.optInt("price", 0)
            if (price <= 0) return@mapNotNull null
            val duration = o.optInt("duration", 0)
            Flight(
                origin = o.str("origin"),
                destination = o.str("destination"),
                originAirport = o.str("origin_airport").ifBlank { o.str("origin") },
                destinationAirport = o.str("destination_airport").ifBlank { o.str("destination") },
                price = price,
                airline = o.str("airline"),
                flightNumber = o.str("flight_number"),
                departureAt = o.str("departure_at"),
                returnAt = o.str("return_at").ifBlank { null },
                transfers = o.optInt("transfers", 0),
                returnTransfers = o.optInt("return_transfers", 0),
                durationTo = o.optInt("duration_to", 0).takeIf { it > 0 } ?: duration,
                durationBack = o.optInt("duration_back", 0),
                link = o.str("link"),
            )
        }
    }

    fun calendar(body: String): List<DayPrice> {
        val root = JSONObject(body)
        checkSuccess(root)
        val data = root.optJSONObject("data") ?: return emptyList()
        return data.keys().asSequence().mapNotNull { key ->
            val date = runCatching { LocalDate.parse(key.take(10)) }.getOrNull() ?: return@mapNotNull null
            val o = data.optJSONObject(key) ?: return@mapNotNull null
            val price = o.optInt("price", 0)
            if (price <= 0) null else DayPrice(date, price, o.optInt("transfers", 0), o.str("airline"))
        }.sortedBy { it.date }.toList()
    }

    private fun checkSuccess(root: JSONObject) {
        if (root.has("success") && !root.optBoolean("success", true)) {
            throw ApiException(root.str("error").ifBlank { "Réponse invalide du serveur" })
        }
    }

    /** optString renvoie "null" pour une valeur JSON null sur Android : on l'évite. */
    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")
}
