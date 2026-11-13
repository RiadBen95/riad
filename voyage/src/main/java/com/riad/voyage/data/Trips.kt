package com.riad.voyage.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Lieu gardé dans un voyage (favori ou étape du programme). */
data class SavedPlace(
    val name: String,
    val kind: String,
    val lat: Double,
    val lon: Double,
    val hasCoordinates: Boolean,
    val website: String? = null,
    val mapsUrl: String? = null,
    val wikipedia: String? = null,
    val imageUrl: String? = null,
) {
    fun toPoi() = PointOfInterest(
        id = "saved/$name", name = name, kind = kind, lat = lat, lon = lon, distanceM = -1, address = null,
        openingHours = null, website = website, phone = null, wikipedia = wikipedia, notable = true,
        isVenue = website != null || mapsUrl != null, hasCoordinates = hasCoordinates, imageUrl = imageUrl, mapsUrl = mapsUrl,
    )

    companion object {
        fun of(p: PointOfInterest) = SavedPlace(p.name, p.kind, p.lat, p.lon, p.hasCoordinates, p.website, p.mapsUrl, p.wikipedia, p.imageUrl)
    }
}

data class SavedStep(val time: String, val moment: String, val emoji: String, val why: String, val durationMin: Int, val place: SavedPlace)

data class SavedDay(val title: String, val weather: String?, val steps: List<SavedStep>)

data class SavedFlight(
    val origin: String,
    val destination: String,
    val departureAt: String,
    val returnAt: String?,
    val price: Int,
    val currency: String,
    val airline: String,
    val url: String,
)

data class SavedArticle(
    val wiki: String,
    val title: String,
    val imageUrl: String?,
    val intro: String,
    val sections: List<Pair<String, String>>,
    val url: String,
) {
    fun toArticle() = Article(wiki, title, imageUrl, intro, sections, url)

    companion object {
        fun of(a: Article) = SavedArticle(a.wiki, a.title, a.imageUrl, a.intro, a.sections, a.url)
    }
}

/** Un voyage : tout ce que la personne a gardé pour une destination. */
data class Trip(
    val id: String,
    val city: String,
    val country: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val programTitle: String? = null,
    val program: List<SavedDay> = emptyList(),
    val flights: List<SavedFlight> = emptyList(),
    val places: List<SavedPlace> = emptyList(),
    val articles: List<SavedArticle> = emptyList(),
    val updated: Long = System.currentTimeMillis(),
) {
    val isEmpty get() = program.isEmpty() && flights.isEmpty() && places.isEmpty() && articles.isEmpty()
}

/** Voyages enregistrés sur le téléphone : consultables hors connexion. */
class TripStore(context: Context) {
    private val prefs = context.getSharedPreferences("trips", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<Trip> = runCatching { TripJson.decodeList(prefs.getString("list", "[]").orEmpty()) }
        .getOrDefault(emptyList()).sortedByDescending { it.updated }

    @Synchronized
    fun upsert(t: Trip) = write(all().filter { it.id != t.id } + t.copy(updated = System.currentTimeMillis()))

    @Synchronized
    fun delete(id: String) = write(all().filter { it.id != id })

    private fun write(list: List<Trip>) = prefs.edit().putString("list", TripJson.encodeList(list)).apply()
}

/** Conversion des voyages en JSON (et retour). */
object TripJson {
    private fun JSONObject.s(k: String): String? = if (isNull(k)) null else optString(k).ifBlank { null }
    private fun <T> JSONArray?.list(f: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else List(length()) { f(getJSONObject(it)) }
    private fun <T> List<T>.arr(f: (T) -> JSONObject) = JSONArray().also { a -> forEach { a.put(f(it)) } }

    private fun place(p: SavedPlace) = JSONObject().put("name", p.name).put("kind", p.kind).put("lat", p.lat).put("lon", p.lon)
        .put("hasCoordinates", p.hasCoordinates).put("website", p.website).put("mapsUrl", p.mapsUrl)
        .put("wikipedia", p.wikipedia).put("imageUrl", p.imageUrl)

    private fun place(o: JSONObject) = SavedPlace(
        o.optString("name"), o.optString("kind"), o.optDouble("lat", 0.0), o.optDouble("lon", 0.0),
        o.optBoolean("hasCoordinates"), o.s("website"), o.s("mapsUrl"), o.s("wikipedia"), o.s("imageUrl"),
    )

    fun encode(t: Trip): JSONObject = JSONObject()
        .put("id", t.id).put("city", t.city).put("country", t.country)
        .put("startDate", t.startDate).put("endDate", t.endDate).put("programTitle", t.programTitle).put("updated", t.updated)
        .put("program", t.program.arr { d ->
            JSONObject().put("title", d.title).put("weather", d.weather).put("steps", d.steps.arr { st ->
                JSONObject().put("time", st.time).put("moment", st.moment).put("emoji", st.emoji).put("why", st.why)
                    .put("durationMin", st.durationMin).put("place", place(st.place))
            })
        })
        .put("flights", t.flights.arr { f ->
            JSONObject().put("origin", f.origin).put("destination", f.destination).put("departureAt", f.departureAt)
                .put("returnAt", f.returnAt).put("price", f.price).put("currency", f.currency).put("airline", f.airline).put("url", f.url)
        })
        .put("places", t.places.arr(::place))
        .put("articles", t.articles.arr { a ->
            JSONObject().put("wiki", a.wiki).put("title", a.title).put("imageUrl", a.imageUrl).put("intro", a.intro).put("url", a.url)
                .put("sections", a.sections.arr { (h, b) -> JSONObject().put("h", h).put("b", b) })
        })

    fun decode(o: JSONObject) = Trip(
        id = o.optString("id"),
        city = o.optString("city"),
        country = o.optString("country"),
        startDate = o.s("startDate"),
        endDate = o.s("endDate"),
        programTitle = o.s("programTitle"),
        program = o.optJSONArray("program").list { d ->
            SavedDay(d.optString("title"), d.s("weather"), d.optJSONArray("steps").list { st ->
                SavedStep(st.optString("time"), st.optString("moment"), st.optString("emoji"), st.optString("why"),
                    st.optInt("durationMin"), place(st.getJSONObject("place")))
            })
        },
        flights = o.optJSONArray("flights").list { f ->
            SavedFlight(f.optString("origin"), f.optString("destination"), f.optString("departureAt"), f.s("returnAt"),
                f.optInt("price"), f.optString("currency"), f.optString("airline"), f.optString("url"))
        },
        places = o.optJSONArray("places").list(::place),
        articles = o.optJSONArray("articles").list { a ->
            SavedArticle(a.optString("wiki"), a.optString("title"), a.s("imageUrl"), a.optString("intro"),
                a.optJSONArray("sections").list { it.optString("h") to it.optString("b") }, a.optString("url"))
        },
        updated = o.optLong("updated"),
    )

    fun encodeList(list: List<Trip>) = list.arr(::encode).toString()
    fun decodeList(json: String) = JSONArray(json).list(::decode)
}

/** Résumé texte d'un voyage, pour le partager (messagerie, e-mail…). */
object TripText {
    fun share(t: Trip, tr: (String) -> String = { it }): String = buildString {
        append("✈️ ").append(tr(t.city))
        if (t.startDate != null) append(" · ").append(t.startDate).append(t.endDate?.let { " → $it" } ?: "")
        append("\n")
        t.flights.forEach { f ->
            append("\n").append("${f.origin} → ${f.destination} · ${f.departureAt.take(16).replace('T', ' ')} · ${f.price} ${f.currency}")
            append("\n").append(f.url).append("\n")
        }
        if (t.program.isNotEmpty()) {
            append("\n").append(t.programTitle ?: "").append("\n")
            t.program.forEach { d ->
                append("\n— ").append(d.title).append(d.weather?.let { " · $it" } ?: "").append(" —\n")
                d.steps.forEach { s -> append("${s.time}  ${s.emoji} ${s.place.name}\n") }
            }
        }
        if (t.places.isNotEmpty()) {
            append("\n♥ ")
            append(t.places.joinToString(" · ") { it.name })
            append("\n")
        }
        append("\n— Envolée")
    }
}
