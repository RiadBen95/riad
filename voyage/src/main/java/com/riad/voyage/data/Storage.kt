package com.riad.voyage.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Settings(
    val token: String = "",
    val marker: String = "",
    val currency: String = "EUR",
) {
    val isDemo get() = token.isBlank()
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load() = Settings(
        token = prefs.getString("token", "").orEmpty(),
        marker = prefs.getString("marker", "").orEmpty(),
        currency = prefs.getString("currency", "EUR").orEmpty().ifBlank { "EUR" },
    )

    fun save(s: Settings) {
        prefs.edit()
            .putString("token", s.token.trim())
            .putString("marker", s.marker.trim())
            .putString("currency", s.currency)
            .apply()
    }
}

class AlertStore(context: Context) {
    private val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<PriceAlert> {
        val arr = runCatching { JSONArray(prefs.getString("list", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::fromJson) }
    }

    @Synchronized
    fun upsert(alert: PriceAlert) = write(all().filter { it.id != alert.id } + alert)

    @Synchronized
    fun remove(id: String) = write(all().filter { it.id != id })

    private fun write(list: List<PriceAlert>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    private fun toJson(a: PriceAlert) = JSONObject()
        .put("id", a.id).put("origin", a.origin).put("originName", a.originName)
        .put("destination", a.destination).put("destinationName", a.destinationName)
        .put("departure", a.departure).put("returnDate", a.returnDate ?: JSONObject.NULL)
        .put("directOnly", a.directOnly).put("lastPrice", a.lastPrice ?: JSONObject.NULL)
        .put("currency", a.currency).put("lastChecked", a.lastChecked)

    private fun fromJson(o: JSONObject) = PriceAlert(
        id = o.optString("id"),
        origin = o.optString("origin"),
        originName = o.optString("originName"),
        destination = o.optString("destination"),
        destinationName = o.optString("destinationName"),
        departure = o.optString("departure"),
        returnDate = if (o.isNull("returnDate")) null else o.optString("returnDate"),
        directOnly = o.optBoolean("directOnly"),
        lastPrice = if (o.isNull("lastPrice")) null else o.optInt("lastPrice"),
        currency = o.optString("currency", "EUR"),
        lastChecked = o.optLong("lastChecked"),
    )
}
