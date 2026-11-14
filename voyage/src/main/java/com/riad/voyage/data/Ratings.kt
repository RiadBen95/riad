package com.riad.voyage.data

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ln

/** Sources de notes, chacune limitée à son offre gratuite. */
enum class RatingSource(val label: String, val defaultMonthlyLimit: Int) {
    GOOGLE("Google", 950),           // 1 000 recherches gratuites / mois (Text Search Enterprise)
    TRIPADVISOR("Tripadvisor", 900), // offre gratuite à vérifier : réglable dans l'app
    FOURSQUARE("Foursquare", 5000),  // offre gratuite à vérifier : réglable dans l'app
}

/**
 * Compteur d'appels par source et par mois, enregistré sur le téléphone. Une source s'arrête
 * d'elle-même à sa limite, ou pour la journée si le fournisseur répond « quota dépassé ».
 */
class QuotaStore(context: Context) {
    private val prefs = context.getSharedPreferences("quotas", Context.MODE_PRIVATE)
    private fun month() = YearMonth.now().toString()

    fun used(s: RatingSource): Int = if (prefs.getString("month", "") == month()) prefs.getInt("used_${s.name}", 0) else 0
    fun limit(s: RatingSource): Int = prefs.getInt("limit_${s.name}", s.defaultMonthlyLimit)
    fun setLimit(s: RatingSource, value: Int) = prefs.edit().putInt("limit_${s.name}", value.coerceAtLeast(0)).apply()

    fun exhaustedToday(s: RatingSource) = prefs.getString("blocked_${s.name}", "") == LocalDate.now().toString()
    fun markExhausted(s: RatingSource) = prefs.edit().putString("blocked_${s.name}", LocalDate.now().toString()).apply()

    fun remaining(s: RatingSource): Int = if (exhaustedToday(s)) 0 else (limit(s) - used(s)).coerceAtLeast(0)

    @Synchronized
    fun spend(s: RatingSource, n: Int = 1) {
        val e = prefs.edit()
        if (prefs.getString("month", "") != month()) {
            e.putString("month", month())
            RatingSource.entries.forEach { e.putInt("used_${it.name}", 0) }
            e.apply()
        }
        prefs.edit().putInt("used_${s.name}", used(s) + n).apply()
    }
}

/** Une note affichée sous un lieu. */
data class RatingInfo(val source: RatingSource, val rating: Double, val count: Int, val ranking: String? = null, val url: String? = null)

class QuotaExceeded(message: String) : IOException(message)

/** Un lieu trouvé par une source, avant fusion. */
data class Candidate(
    val source: RatingSource,
    val name: String,
    val lat: Double?,
    val lon: Double?,
    val address: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val price: String? = null,
    val rating: RatingInfo? = null,
    val popularityRank: Int? = null,   // position dans le classement de popularité (Foursquare)
    val openNow: Boolean? = null,
    val mapsUrl: String? = null,
    val typeLabel: String? = null,     // ex. « Restaurant italien », « Bar à cocktails »
)

private object Http {
    suspend fun call(url: String, headers: Map<String, String> = emptyMap(), body: String? = null): String = try {
        Net.fetch(url, headers, body, connectMs = 7_000, readMs = 10_000)
    } catch (e: HttpError) {
        if (e.code == 429 || e.body.contains("RESOURCE_EXHAUSTED") || e.body.contains("quota", ignoreCase = true)) throw QuotaExceeded("quota atteint")
        throw IOException("HTTP ${e.code} ${e.body.take(150)}")
    }

    fun enc(s: String): String = Net.enc(s)
}

private fun JSONObject.s(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

/** Google Places API (New) : une recherche texte = 1 appel, avec note et nombre d'avis. */
object GoogleRatings {
    private const val FIELDS = "places.id,places.displayName,places.location,places.formattedAddress," +
        "places.rating,places.userRatingCount,places.priceLevel,places.websiteUri," +
        "places.nationalPhoneNumber,places.googleMapsUri,places.currentOpeningHours.openNow," +
        "places.primaryTypeDisplayName"

    suspend fun search(key: String, query: String, center: GeoPoint): List<Candidate> {
        val body = JSONObject()
            .put("textQuery", query)
            .put("languageCode", com.riad.voyage.I18n.wikiLang)
            .put("pageSize", 20)
            .put(
                "locationBias",
                JSONObject().put(
                    "circle",
                    JSONObject()
                        .put("center", JSONObject().put("latitude", center.lat).put("longitude", center.lon))
                        .put("radius", 15000.0),
                ),
            )
        val json = Http.call(
            "https://places.googleapis.com/v1/places:searchText",
            mapOf(
                "X-Goog-Api-Key" to key,
                "X-Goog-FieldMask" to FIELDS,
                // Permet de restreindre la clé à l'application Envolée dans Google Cloud.
                "X-Android-Package" to "com.riad.voyage",
                "X-Android-Cert" to "603C871E7D07E6B88530DD6E78D3DCC6C9F6A46F",
            ),
            body.toString(),
        )
        return parse(json)
    }

    fun parse(json: String): List<Candidate> {
        val arr = JSONObject(json).optJSONArray("places") ?: return emptyList()
        return List(arr.length()) { arr.getJSONObject(it) }.mapNotNull { p ->
            val name = p.optJSONObject("displayName")?.s("text") ?: return@mapNotNull null
            val loc = p.optJSONObject("location")
            val count = p.optInt("userRatingCount", 0)
            Candidate(
                source = RatingSource.GOOGLE,
                name = name,
                lat = loc?.optDouble("latitude")?.takeIf { !it.isNaN() },
                lon = loc?.optDouble("longitude")?.takeIf { !it.isNaN() },
                address = p.s("formattedAddress"),
                phone = p.s("nationalPhoneNumber"),
                website = p.s("websiteUri"),
                price = when (p.s("priceLevel")) {
                    "PRICE_LEVEL_INEXPENSIVE" -> "€"
                    "PRICE_LEVEL_MODERATE" -> "€€"
                    "PRICE_LEVEL_EXPENSIVE" -> "€€€"
                    "PRICE_LEVEL_VERY_EXPENSIVE" -> "€€€€"
                    else -> null
                },
                rating = p.optDouble("rating").takeIf { !it.isNaN() && count > 0 }
                    ?.let { RatingInfo(RatingSource.GOOGLE, it, count, url = p.s("googleMapsUri")) },
                openNow = p.optJSONObject("currentOpeningHours")?.let { if (it.has("openNow")) it.optBoolean("openNow") else null },
                mapsUrl = p.s("googleMapsUri"),
                typeLabel = p.optJSONObject("primaryTypeDisplayName")?.s("text"),
            )
        }
    }
}

/** Tripadvisor Content API : 1 recherche + 1 appel de détails par lieu (note, avis, classement). */
object TripadvisorRatings {
    private const val BASE = "https://api.content.tripadvisor.com/api/v1/location"
    // Tripadvisor lie les clés à un domaine : l'app s'annonce avec celui du site d'Envolée.
    private val headers = mapOf("Referer" to "https://riadben95.github.io/riad/")

    suspend fun search(key: String, query: String, category: String, center: GeoPoint, maxDetails: Int, onCall: () -> Unit): List<Candidate> {
        val found = Http.call(
            "$BASE/search?key=${Http.enc(key)}&searchQuery=${Http.enc(query)}&category=$category" +
                "&latLong=${center.lat},${center.lon}&radius=15&radiusUnit=km&language=fr",
            headers,
        ).also { onCall() }
        val ids = JSONObject(found).optJSONArray("data")?.let { a -> List(a.length()) { a.getJSONObject(it).optString("location_id") } }
            .orEmpty().filter { it.isNotBlank() }.take(maxDetails)
        return coroutineScope {
            ids.map { id ->
                async {
                    withTimeoutOrNull(8_000) {
                        runCatching {
                            parseDetails(Http.call("$BASE/$id/details?key=${Http.enc(key)}&language=fr&currency=EUR", headers).also { onCall() })
                        }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    fun parseDetails(json: String): Candidate? {
        val o = JSONObject(json)
        val name = o.s("name") ?: return null
        val count = o.s("num_reviews")?.replace(Regex("\\D"), "")?.toIntOrNull() ?: 0
        val rating = o.s("rating")?.toDoubleOrNull()
        return Candidate(
            source = RatingSource.TRIPADVISOR,
            name = name,
            lat = o.s("latitude")?.toDoubleOrNull(),
            lon = o.s("longitude")?.toDoubleOrNull(),
            address = o.optJSONObject("address_obj")?.s("address_string"),
            phone = o.s("phone"),
            website = o.s("website"),
            price = o.s("price_level"),
            rating = rating?.takeIf { count > 0 }?.let {
                RatingInfo(RatingSource.TRIPADVISOR, it, count, o.optJSONObject("ranking_data")?.s("ranking_string"), o.s("web_url"))
            },
            mapsUrl = o.s("web_url"),
            typeLabel = o.optJSONArray("cuisine")?.let { arr ->
                List(arr.length()) { arr.optJSONObject(it)?.s("localized_name") }.filterNotNull().take(2)
                    .joinToString(", ").ifBlank { null }?.let { "Cuisine $it".lowercase().replaceFirstChar { c -> c.uppercase() } }
            },
        )
    }
}

/** Foursquare Places : 1 appel, lieux classés par popularité (sans les champs payants). */
object FoursquareRatings {
    suspend fun search(key: String, query: String, center: GeoPoint): List<Candidate> {
        val params = "query=${Http.enc(query)}&ll=${center.lat},${center.lon}&radius=10000&limit=50&sort=POPULARITY"
        val json = if (key.startsWith("fsq3")) {
            // Anciennes clés (API v3).
            Http.call("https://api.foursquare.com/v3/places/search?$params", mapOf("Authorization" to key))
        } else {
            Http.call(
                "https://places-api.foursquare.com/places/search?$params",
                mapOf("Authorization" to "Bearer $key", "X-Places-Api-Version" to "2025-06-17"),
            )
        }
        return parse(json)
    }

    fun parse(json: String): List<Candidate> {
        val arr: JSONArray = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return List(arr.length()) { arr.getJSONObject(it) }.mapIndexedNotNull { i, p ->
            val name = p.s("name") ?: return@mapIndexedNotNull null
            val main = p.optJSONObject("geocodes")?.optJSONObject("main")
            Candidate(
                source = RatingSource.FOURSQUARE,
                name = name,
                lat = p.optDouble("latitude").takeIf { !it.isNaN() } ?: main?.optDouble("latitude")?.takeIf { !it.isNaN() },
                lon = p.optDouble("longitude").takeIf { !it.isNaN() } ?: main?.optDouble("longitude")?.takeIf { !it.isNaN() },
                address = p.optJSONObject("location")?.s("formatted_address"),
                phone = p.s("tel"),
                website = p.s("website"),
                popularityRank = i,
                typeLabel = p.optJSONArray("categories")?.optJSONObject(0)?.s("name"),
            )
        }
    }
}

/** Interroge une source à la fois (Google, puis Tripadvisor, puis Foursquare) selon les quotas gratuits. */
object RatingsAggregator {
    data class Outcome(val places: List<PointOfInterest>, val usedSources: Set<RatingSource>, val skipped: Set<RatingSource>)

    private fun terms(c: ActivityLinks.Category) = when (c) {
        ActivityLinks.Category.RESTAURANTS -> Triple("restaurants", "restaurant", "restaurants")
        ActivityLinks.Category.CAFES -> Triple("cafés", "café", "restaurants")
        ActivityLinks.Category.BARS -> Triple("bars", "bar", "restaurants")
        ActivityLinks.Category.NIGHTLIFE -> Triple("boîtes de nuit", "nightclub", "attractions")
        ActivityLinks.Category.SHOPPING -> Triple("shopping", "shopping mall", "attractions")
        else -> null
    }

    fun supports(c: ActivityLinks.Category) = terms(c) != null

    suspend fun search(
        settings: Settings,
        quotas: QuotaStore,
        city: String,
        center: GeoPoint,
        category: ActivityLinks.Category,
        guide: List<PointOfInterest>,
        hint: String? = null,   // précise la recherche selon les goûts : « restaurants italiens »…
    ): Outcome {
        val (basePlural, baseSingular, taCategory) = terms(category) ?: return Outcome(emptyList(), emptySet(), emptySet())
        val plural = hint ?: basePlural
        val singular = hint ?: baseSingular
        val skipped = mutableSetOf<RatingSource>()
        fun ready(s: RatingSource, key: String, cost: Int): Boolean {
            if (key.isBlank()) return false
            if (quotas.remaining(s) < cost) { skipped += s; return false }
            return true
        }
        suspend fun <T> guarded(s: RatingSource, block: suspend () -> List<T>): List<T> =
            withTimeoutOrNull(12_000) {
                try {
                    block()
                } catch (e: QuotaExceeded) {
                    quotas.markExhausted(s); skipped += s; emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()

        // Une seule source à la fois, dans l'ordre : Google tant que sa limite gratuite n'est pas
        // atteinte, puis Tripadvisor, puis Foursquare. On passe à la suivante si la source est
        // épuisée, en erreur, ou ne trouve presque rien.
        val order = listOf(
            Triple(RatingSource.GOOGLE, settings.googleKey, 1),
            Triple(RatingSource.TRIPADVISOR, settings.tripadvisorKey, 4),
            Triple(RatingSource.FOURSQUARE, settings.foursquareKey, 1),
        )
        for ((source, key, cost) in order) {
            if (!ready(source, key, cost)) continue
            val found: List<Candidate> = guarded(source) {
                when (source) {
                    RatingSource.GOOGLE -> {
                        quotas.spend(RatingSource.GOOGLE)
                        GoogleRatings.search(key, "meilleurs $plural à $city", center)
                    }
                    RatingSource.TRIPADVISOR -> {
                        // 1 recherche + 3 à 10 fiches détaillées selon le quota restant.
                        val details = (quotas.remaining(RatingSource.TRIPADVISOR) - 1).coerceIn(3, 10)
                        TripadvisorRatings.search(key, "$plural $city", taCategory, center, details) {
                            quotas.spend(RatingSource.TRIPADVISOR)
                        }
                    }
                    RatingSource.FOURSQUARE -> {
                        quotas.spend(RatingSource.FOURSQUARE)
                        FoursquareRatings.search(key, singular, center)
                    }
                }
            }
            if (found.size >= 3) {
                return Outcome(merge(found, guide, center, category), setOf(source), skipped - source)
            }
        }
        return Outcome(emptyList(), emptySet(), skipped)
    }

    private class Group(val items: MutableList<Candidate>, var guide: PointOfInterest? = null)

    /** Regroupe les mêmes lieux venant de sources différentes, puis classe. */
    fun merge(candidates: List<Candidate>, guide: List<PointOfInterest>, center: GeoPoint, category: ActivityLinks.Category, limit: Int = 30): List<PointOfInterest> {
        val groups = mutableListOf<Group>()
        val fsqTotal = candidates.count { it.source == RatingSource.FOURSQUARE }.coerceAtLeast(1)
        for (c in candidates) {
            val g = groups.firstOrNull { grp -> grp.items.any { same(it.name, it.lat, it.lon, c.name, c.lat, c.lon) } }
            if (g != null) g.items += c else groups += Group(mutableListOf(c))
        }
        for (p in guide) {
            val g = groups.firstOrNull { grp -> grp.items.any { same(it.name, it.lat, it.lon, p.name, p.lat.takeIf { p.hasCoordinates }, p.lon.takeIf { p.hasCoordinates }) } }
            if (g != null) g.guide = p
        }
        val scored = groups.map { g ->
            val ratings = g.items.mapNotNull { it.rating }
            var score = if (ratings.isEmpty()) 3.6 else {
                val weights = ratings.map { ln(1.0 + it.count) }
                ratings.zip(weights).sumOf { (r, w) -> bayes(r.rating, r.count) * w } / weights.sum().coerceAtLeast(0.001)
            }
            g.items.firstOrNull { it.source == RatingSource.FOURSQUARE }?.popularityRank?.let { score += 0.3 * (1 - it.toDouble() / fsqTotal) }
            if (g.guide != null) score += 0.25
            score += 0.1 * (g.items.map { it.source }.distinct().size - 1)
            g to score
        }.sortedByDescending { it.second }

        return scored.take(limit).map { (g, score) ->
            fun <T> pick(f: (Candidate) -> T?): T? =
                listOf(RatingSource.GOOGLE, RatingSource.TRIPADVISOR, RatingSource.FOURSQUARE)
                    .firstNotNullOfOrNull { s -> g.items.firstOrNull { it.source == s }?.let(f) }
            val lat = pick { it.lat }
            val lon = pick { it.lon }
            val hasCoords = lat != null && lon != null
            val ratings = g.items.mapNotNull { it.rating }.sortedBy { it.source.ordinal }
            val kind = (pick { it.typeLabel } ?: when (category) {
                ActivityLinks.Category.RESTAURANTS -> "Restaurant"
                ActivityLinks.Category.CAFES -> "Café"
                ActivityLinks.Category.BARS -> "Bar"
                ActivityLinks.Category.NIGHTLIFE -> "Sortie de nuit"
                ActivityLinks.Category.SHOPPING -> "Shopping"
                else -> "Lieu"
            }) + (pick { it.price }?.let { " · $it" } ?: "")
            PointOfInterest(
                id = "rt/" + PlacesParser.normalizeName(pick { it.name } ?: "?") + "/$lat,$lon",
                name = pick { it.name } ?: "?",
                kind = kind,
                lat = lat ?: center.lat,
                lon = lon ?: center.lon,
                distanceM = if (hasCoords) PlacesParser.distanceMeters(center.lat, center.lon, lat!!, lon!!).toInt() else -1,
                address = pick { it.address },
                openingHours = g.guide?.openingHours,
                website = pick { it.website } ?: g.guide?.website,
                phone = pick { it.phone } ?: g.guide?.phone,
                wikipedia = null,
                notable = true,
                isVenue = true,
                inGuide = g.guide != null,
                description = g.guide?.description,
                hasCoordinates = hasCoords,
                ratings = ratings,
                openNow = pick { it.openNow },
                mapsUrl = pick { it.mapsUrl },
                ratingScore = score,
            )
        }
    }

    /** Note « bayésienne » : 5★ avec 3 avis pèse moins que 4,7★ avec 3 000 avis. */
    fun bayes(rating: Double, count: Int, prior: Double = 4.0, weight: Int = 50) =
        (count * rating + weight * prior) / (count + weight)

    private fun same(n1: String, lat1: Double?, lon1: Double?, n2: String, lat2: Double?, lon2: Double?): Boolean {
        val a = PlacesParser.normalizeName(n1)
        val b = PlacesParser.normalizeName(n2)
        val nameMatch = a == b || (a.length >= 6 && b.contains(a)) || (b.length >= 6 && a.contains(b))
        if (!nameMatch) return false
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return true
        return PlacesParser.distanceMeters(lat1, lon1, lat2, lon2) < 300
    }
}
