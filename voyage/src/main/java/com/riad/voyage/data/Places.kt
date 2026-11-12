package com.riad.voyage.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lon: Double, val label: String)

data class PointOfInterest(
    val id: String,
    val name: String,
    val kind: String,
    val lat: Double,
    val lon: Double,
    val distanceM: Int,
    val address: String?,
    val openingHours: String?,
    val website: String?,
    val phone: String?,
    val wikipedia: String?,      // ex. "fr:Musée du Louvre"
    val notable: Boolean,
    val isRestaurant: Boolean,
)

data class WikiSummary(val extract: String, val imageUrl: String?, val pageUrl: String)

/**
 * Lieux issus d'OpenStreetMap (Nominatim pour trouver la ville, Overpass pour les lieux)
 * et résumés Wikipédia. Services gratuits et sans clé : on reste raisonnable sur le nombre d'appels.
 */
object PlacesApi {
    private const val UA = "VoyageMalin/1.0 (Android app; https://github.com/RiadBen95/riad)"
    private val overpassServers = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    )

    suspend fun geocode(city: String): GeoPoint? {
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&accept-language=fr&q=${enc(city)}"
        return PlacesParser.geocode(get(url))
    }

    suspend fun search(center: GeoPoint, category: ActivityLinks.Category): List<PointOfInterest> {
        val query = overpassQuery(category, center.lat, center.lon)
        var last: Exception? = null
        for (server in overpassServers) {
            try {
                return PlacesParser.pois(post(server, "data=${enc(query)}"), center)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Serveur de lieux injoignable")
    }

    suspend fun wikiSummary(tag: String): WikiSummary? {
        val (lang, title) = splitWikiTag(tag) ?: return null
        val url = "https://$lang.wikipedia.org/api/rest_v1/page/summary/${enc(title.replace(' ', '_'))}"
        return runCatching { PlacesParser.wiki(get(url)) }.getOrNull()
    }

    fun wikiPageUrl(tag: String): String? {
        val (lang, title) = splitWikiTag(tag) ?: return null
        return "https://$lang.wikipedia.org/wiki/${enc(title.replace(' ', '_'))}"
    }

    private fun splitWikiTag(tag: String): Pair<String, String>? {
        val i = tag.indexOf(':')
        if (i !in 2..3) return null
        return tag.substring(0, i) to tag.substring(i + 1)
    }

    fun overpassQuery(category: ActivityLinks.Category, lat: Double, lon: Double): String {
        fun around(r: Int) = "(around:$r,$lat,$lon)"
        val body = when (category) {
            ActivityLinks.Category.MUSEUMS -> {
                val a = around(8000)
                """nwr["tourism"="museum"]["name"]$a;
                   nwr["tourism"="gallery"]["name"]["wikidata"]$a;"""
            }
            ActivityLinks.Category.ATTRACTIONS -> {
                val a = around(8000)
                """nwr["tourism"~"^(attraction|viewpoint|zoo|theme_park|aquarium)$"]["name"]$a;
                   nwr["historic"~"^(monument|castle|memorial|ruins|archaeological_site|fort)$"]["name"]["wikidata"]$a;
                   nwr["leisure"="park"]["name"]["wikidata"]$a;"""
            }
            ActivityLinks.Category.RESTAURANTS -> """nwr["amenity"="restaurant"]["name"]${around(1500)};"""
            ActivityLinks.Category.TOURS -> ""
        }
        return "[out:json][timeout:25];($body);out center tags 200;"
    }

    private suspend fun get(url: String): String = request(url, null)
    private suspend fun post(url: String, form: String): String = request(url, form)

    private suspend fun request(url: String, form: String?): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 40_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            if (form != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                conn.outputStream.use { it.write(form.toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw ApiException(
                    if (code == 429 || code == 504) "Le service des lieux est très sollicité, réessayez dans un instant."
                    else "Erreur du service des lieux ($code).",
                    code,
                )
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

object PlacesParser {

    fun geocode(body: String): GeoPoint? {
        val arr = JSONArray(body)
        val o = arr.optJSONObject(0) ?: return null
        val lat = o.optString("lat").toDoubleOrNull() ?: return null
        val lon = o.optString("lon").toDoubleOrNull() ?: return null
        return GeoPoint(lat, lon, o.optString("display_name"))
    }

    fun pois(body: String, center: GeoPoint, limit: Int = 60): List<PointOfInterest> {
        val elements = JSONObject(body).optJSONArray("elements") ?: return emptyList()
        val seen = HashSet<String>()
        return (0 until elements.length()).mapNotNull { i ->
            val e = elements.optJSONObject(i) ?: return@mapNotNull null
            val tags = e.optJSONObject("tags") ?: return@mapNotNull null
            val name = tags.str("name:fr").ifBlank { tags.str("name") }
            if (name.isBlank()) return@mapNotNull null
            val c = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.optDouble("lat") else c?.optDouble("lat") ?: return@mapNotNull null
            val lon = if (e.has("lon")) e.optDouble("lon") else c?.optDouble("lon") ?: return@mapNotNull null
            if (lat.isNaN() || lon.isNaN()) return@mapNotNull null
            // Un même lieu peut être décrit deux fois (point + bâtiment).
            if (!seen.add(name.lowercase())) return@mapNotNull null
            val isRestaurant = tags.str("amenity") == "restaurant"
            val street = listOf(tags.str("addr:housenumber"), tags.str("addr:street")).filter { it.isNotBlank() }.joinToString(" ")
            PointOfInterest(
                id = "${e.optString("type")}/${e.optLong("id")}",
                name = name,
                kind = kindLabel(tags),
                lat = lat,
                lon = lon,
                distanceM = distance(center.lat, center.lon, lat, lon).toInt(),
                address = listOf(street, tags.str("addr:city")).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                openingHours = tags.str("opening_hours").ifBlank { null },
                website = tags.str("website").ifBlank { tags.str("contact:website") }.ifBlank { null },
                phone = tags.str("phone").ifBlank { tags.str("contact:phone") }.ifBlank { null },
                wikipedia = tags.str("wikipedia").ifBlank { null },
                notable = tags.has("wikipedia") || tags.has("wikidata"),
                isRestaurant = isRestaurant,
            )
        }.sortedWith(
            compareByDescending<PointOfInterest> { it.wikipedia != null }
                .thenByDescending { it.notable }
                .thenByDescending { it.website != null }
                .thenBy { it.distanceM },
        ).take(limit)
    }

    fun wiki(body: String): WikiSummary? {
        val o = JSONObject(body)
        val extract = o.str("extract")
        if (extract.isBlank()) return null
        val image = o.optJSONObject("thumbnail")?.str("source")?.ifBlank { null }
        val page = o.optJSONObject("content_urls")?.optJSONObject("mobile")?.str("page").orEmpty()
        return WikiSummary(extract, image, page)
    }

    private val kinds = mapOf(
        "museum" to "Musée", "gallery" to "Galerie d'art", "attraction" to "Attraction",
        "viewpoint" to "Point de vue", "zoo" to "Zoo", "theme_park" to "Parc d'attractions",
        "aquarium" to "Aquarium", "monument" to "Monument", "castle" to "Château", "memorial" to "Mémorial",
        "ruins" to "Ruines", "archaeological_site" to "Site archéologique", "fort" to "Fort", "park" to "Parc",
    )

    private val cuisines = mapOf(
        "french" to "français", "italian" to "italien", "pizza" to "pizza", "japanese" to "japonais",
        "sushi" to "sushi", "chinese" to "chinois", "indian" to "indien", "lebanese" to "libanais",
        "moroccan" to "marocain", "algerian" to "algérien", "tunisian" to "tunisien", "burger" to "burgers",
        "seafood" to "fruits de mer", "vegetarian" to "végétarien", "vegan" to "vegan", "thai" to "thaï",
        "mexican" to "mexicain", "turkish" to "turc", "kebab" to "kebab", "asian" to "asiatique",
        "mediterranean" to "méditerranéen", "spanish" to "espagnol", "greek" to "grec", "regional" to "régional",
        "vietnamese" to "vietnamien", "korean" to "coréen", "american" to "américain", "steak_house" to "grillades",
        "portuguese" to "portugais", "african" to "africain", "crepe" to "crêperie", "fish" to "poisson",
    )

    private fun kindLabel(tags: JSONObject): String {
        if (tags.str("amenity") == "restaurant") {
            val cuisine = tags.str("cuisine").split(';').map { it.trim() }.filter { it.isNotBlank() }
                .take(2).joinToString(", ") { cuisines[it] ?: it.replace('_', ' ') }
            return if (cuisine.isBlank()) "Restaurant" else "Restaurant · $cuisine"
        }
        return kinds[tags.str("tourism")] ?: kinds[tags.str("historic")] ?: kinds[tags.str("leisure")] ?: "Lieu à visiter"
    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

    /** « Mo-Fr 09:00-18:00; Su off » → « Lun-Ven 09:00-18:00 ; Dim fermé ». */
    fun frenchHours(raw: String): String {
        val days = mapOf("Mo" to "Lun", "Tu" to "Mar", "We" to "Mer", "Th" to "Jeu", "Fr" to "Ven", "Sa" to "Sam", "Su" to "Dim", "PH" to "fériés")
        var s = raw
        days.forEach { (en, fr) -> s = s.replace(Regex("\\b$en\\b"), fr) }
        return s.replace(Regex("\\boff\\b"), "fermé").replace(Regex("\\bclosed\\b"), "fermé").replace(";", " ;")
    }
}
