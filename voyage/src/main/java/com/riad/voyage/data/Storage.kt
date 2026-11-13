package com.riad.voyage.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Settings(
    val token: String = "",
    val marker: String = "",
    val currency: String = "EUR",
    val googleKey: String = "",
    val tripadvisorKey: String = "",
    val foursquareKey: String = "",
    val liteApiKey: String = "",
    val tiqetsPartner: String = "",
) {
    val isDemo get() = token.isBlank()
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load() = Settings(
        token = prefs.getString("token", "").orEmpty(),
        marker = prefs.getString("marker", "").orEmpty(),
        currency = prefs.getString("currency", "EUR").orEmpty().ifBlank { "EUR" },
        googleKey = prefs.getString("googleKey", "").orEmpty(),
        tripadvisorKey = prefs.getString("tripadvisorKey", "").orEmpty(),
        foursquareKey = prefs.getString("foursquareKey", "").orEmpty(),
        liteApiKey = prefs.getString("liteApiKey", "").orEmpty(),
        tiqetsPartner = prefs.getString("tiqetsPartner", "").orEmpty(),
    )

    fun save(s: Settings) {
        prefs.edit()
            .putString("token", s.token.trim())
            .putString("marker", s.marker.trim())
            .putString("currency", s.currency)
            .putString("googleKey", s.googleKey.trim())
            .putString("tripadvisorKey", s.tripadvisorKey.trim())
            .putString("foursquareKey", s.foursquareKey.trim())
            .putString("liteApiKey", s.liteApiKey.trim())
            .putString("tiqetsPartner", s.tiqetsPartner.trim())
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

/** Recherche de vol mémorisée, pour la relancer d'un geste. */
data class RecentSearch(
    val origin: Place,
    val destination: Place,
    val departure: String,
    val returnDate: String?,
    val adults: Int,
)

class RecentStore(context: Context) {
    private val prefs = context.getSharedPreferences("recent", Context.MODE_PRIVATE)

    fun all(): List<RecentSearch> {
        val arr = runCatching { JSONArray(prefs.getString("list", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                RecentSearch(
                    origin = place(o.getJSONObject("origin")),
                    destination = place(o.getJSONObject("destination")),
                    departure = o.getString("departure"),
                    returnDate = if (o.isNull("returnDate")) null else o.optString("returnDate"),
                    adults = o.optInt("adults", 1),
                )
            }.getOrNull()
        }
    }

    fun add(r: RecentSearch) {
        val list = (listOf(r) + all().filterNot {
            it.origin.code == r.origin.code && it.destination.code == r.destination.code
        }).take(8)
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("origin", json(it.origin)).put("destination", json(it.destination))
                    .put("departure", it.departure).put("returnDate", it.returnDate ?: JSONObject.NULL)
                    .put("adults", it.adults),
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    private fun json(p: Place) = JSONObject().put("code", p.code).put("name", p.name).put("country", p.country)
        .put("type", p.type).put("cityName", p.cityName ?: JSONObject.NULL)

    private fun place(o: JSONObject) = Place(
        code = o.getString("code"), name = o.getString("name"), country = o.optString("country"),
        type = o.optString("type", "city"), cityName = if (o.isNull("cityName")) null else o.optString("cityName"),
    )
}
