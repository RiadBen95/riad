package com.riad.voyage.data

import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

/** Météo d'une journée (Open-Meteo, gratuit et sans clé). */
data class DayWeather(
    val date: LocalDate,
    val code: Int,            // code météo WMO
    val tMax: Double,
    val tMin: Double,
    val rainProb: Int,        // probabilité de pluie maximale, en %
    val sunsetMin: Int?,      // coucher du soleil, en minutes depuis minuit (heure locale)
) {
    val rainy get() = rainProb >= 60 || code in 51..67 || code in 80..82 || code in 95..99 || code in 71..77 || code in 85..86
    val sunny get() = code <= 2 && rainProb < 30
    val hot get() = tMax >= 27
    val cold get() = tMax < 8

    val emoji get() = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67, in 80..82 -> "🌧️"
        in 71..77, in 85..86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌤️"
    }

    val label get() = "$emoji ${tMax.roundToInt()}° / ${tMin.roundToInt()}°" + if (rainProb >= 30) " · 💧 $rainProb %" else ""
}

object WeatherApi {
    /** Prévisions sur 16 jours au plus (au-delà, Open-Meteo ne prévoit pas). */
    const val FORECAST_DAYS = 16

    suspend fun forecast(lat: Double, lon: Double): List<DayWeather> {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&timezone=auto" +
            "&forecast_days=$FORECAST_DAYS&daily=weather_code,temperature_2m_max,temperature_2m_min," +
            "precipitation_probability_max,sunset"
        return parse(PlacesApi.get(url))
    }

    fun parse(json: String): List<DayWeather> {
        val d = JSONObject(json).optJSONObject("daily") ?: return emptyList()
        val time = d.optJSONArray("time") ?: return emptyList()
        val code = d.optJSONArray("weather_code")
        val max = d.optJSONArray("temperature_2m_max")
        val min = d.optJSONArray("temperature_2m_min")
        val rain = d.optJSONArray("precipitation_probability_max")
        val sunset = d.optJSONArray("sunset")
        return (0 until time.length()).mapNotNull { i ->
            val date = runCatching { LocalDate.parse(time.getString(i)) }.getOrNull() ?: return@mapNotNull null
            val tMax = max?.optDouble(i)?.takeIf { !it.isNaN() } ?: return@mapNotNull null
            DayWeather(
                date = date,
                code = code?.optInt(i, 1) ?: 1,
                tMax = tMax,
                tMin = min?.optDouble(i)?.takeIf { !it.isNaN() } ?: tMax,
                rainProb = rain?.optInt(i, 0) ?: 0,
                sunsetMin = sunset?.optString(i)?.substringAfter('T', "")?.split(':')
                    ?.takeIf { it.size >= 2 }?.let { (h, m) -> (h.toIntOrNull() ?: 0) * 60 + (m.toIntOrNull() ?: 0) },
            )
        }
    }
}
