package com.riad.voyage.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ln

/** Un hôtel avec son prix pour le séjour demandé. */
data class HotelOffer(
    val id: String,
    val name: String,
    val stars: Int?,
    val rating: Double?,          // note des clients, sur 10
    val reviews: Int?,
    val address: String?,
    val photo: String?,
    val lat: Double?,
    val lon: Double?,
    val distanceM: Int?,
    val totalPrice: Double?,      // prix total du séjour
    val currency: String,
    val refundable: Boolean,
    val board: String?,           // « Petit-déjeuner inclus »…
    val offerId: String?,
) {
    fun perNight(nights: Long) = totalPrice?.let { it / nights.coerceAtLeast(1) }

    /** Recommandation : note fiable (nombre d'avis), étoiles, puis prix raisonnable. */
    val score: Double
        get() {
            val r = rating ?: 7.0
            val n = reviews ?: 0
            val reliable = (n * r + 30 * 7.5) / (n + 30)
            return reliable + (stars ?: 3) * 0.15 + ln(1.0 + n) * 0.05 - (distanceM ?: 3000) / 4000.0
        }

    fun toPoi(currencyText: String) = PointOfInterest(
        id = "hotel/$id", name = name, kind = listOfNotNull("Hôtel", stars?.let { "★".repeat(it) }, currencyText.ifBlank { null }).joinToString(" · "),
        lat = lat ?: 0.0, lon = lon ?: 0.0, distanceM = distanceM ?: -1, address = address, openingHours = null,
        website = null, phone = null, wikipedia = null, notable = true, isVenue = true, hasCoordinates = lat != null && lon != null,
        imageUrl = photo,
    )
}

/**
 * Recherche d'hôtels avec LiteAPI (Nuitée) : fiches des hôtels autour de la ville, puis prix réels
 * pour les dates et le nombre de voyageurs. Clé gratuite (« sandbox_… » pour tester, « prod_… » en réel).
 */
object LiteApi {
    private const val BASE = "https://api.liteapi.travel/v3.0"

    suspend fun search(
        key: String,
        center: GeoPoint,
        checkIn: LocalDate,
        checkOut: LocalDate,
        adults: Int,
        currency: String,
        nationality: String,
    ): List<HotelOffer> {
        val hotelsJson = get(
            "$BASE/data/hotels?latitude=${center.lat}&longitude=${center.lon}&radius=8000&limit=60",
            key,
        )
        val hotels = parseHotels(hotelsJson, center)
        if (hotels.isEmpty()) return emptyList()
        val body = JSONObject()
            .put("hotelIds", JSONArray(hotels.map { it.id }))
            .put("occupancies", JSONArray().put(JSONObject().put("adults", adults)))
            .put("currency", currency)
            .put("guestNationality", nationality)
            .put("checkin", checkIn.toString())
            .put("checkout", checkOut.toString())
            .put("timeout", 8)
            .put("maxRatesPerHotel", 3)
        val rates = parseRates(post("$BASE/hotels/rates", key, body.toString()))
        // On garde les hôtels qui ont un prix pour ces dates (disponibles), les mieux notés d'abord.
        return hotels.mapNotNull { h ->
            val r = rates[h.id] ?: return@mapNotNull null
            h.copy(totalPrice = r.price, currency = r.currency ?: currency, refundable = r.refundable, board = r.board, offerId = r.offerId)
        }
    }

    fun parseHotels(json: String, center: GeoPoint): List<HotelOffer> {
        val arr = JSONObject(json).optJSONArray("data") ?: return emptyList()
        return List(arr.length()) { arr.getJSONObject(it) }.mapNotNull { o ->
            val id = o.optString("id").ifBlank { o.optString("hotelId") }.ifBlank { return@mapNotNull null }
            val lat = o.optDouble("latitude").takeIf { !it.isNaN() }
            val lon = o.optDouble("longitude").takeIf { !it.isNaN() }
            HotelOffer(
                id = id,
                name = o.optString("name").ifBlank { return@mapNotNull null },
                stars = o.optDouble("stars").takeIf { !it.isNaN() && it > 0 }?.toInt(),
                rating = o.optDouble("rating").takeIf { !it.isNaN() && it > 0 },
                reviews = o.optInt("reviewCount", 0).takeIf { it > 0 },
                address = o.optString("address").ifBlank { null },
                photo = o.optString("main_photo").ifBlank { o.optString("thumbnail") }.ifBlank { null },
                lat = lat, lon = lon,
                distanceM = if (lat != null && lon != null) PlacesParser.distanceMeters(center.lat, center.lon, lat, lon).toInt() else null,
                totalPrice = null, currency = o.optString("currency", "EUR"), refundable = false, board = null, offerId = null,
            )
        }
    }

    data class Rate(val price: Double, val currency: String?, val refundable: Boolean, val board: String?, val offerId: String?)

    /** Prix le plus bas de chaque hôtel (offre « offerRetailRate », sinon tarif de la chambre). */
    fun parseRates(json: String): Map<String, Rate> {
        val arr = JSONObject(json).optJSONArray("data") ?: return emptyMap()
        val out = mutableMapOf<String, Rate>()
        for (i in 0 until arr.length()) {
            val h = arr.getJSONObject(i)
            val hotelId = h.optString("hotelId").ifBlank { continue }
            val rooms = h.optJSONArray("roomTypes") ?: continue
            for (j in 0 until rooms.length()) {
                val room = rooms.getJSONObject(j)
                val rate = room.optJSONArray("rates")?.optJSONObject(0)
                val total = rate?.optJSONObject("retailRate")?.optJSONArray("total")?.optJSONObject(0)
                val offer = room.optJSONObject("offerRetailRate")
                val price = offer?.optDouble("amount")?.takeIf { !it.isNaN() } ?: total?.optDouble("amount")?.takeIf { !it.isNaN() } ?: continue
                val r = Rate(
                    price = price,
                    currency = (offer?.optString("currency") ?: total?.optString("currency"))?.ifBlank { null },
                    refundable = rate?.optJSONObject("cancellationPolicies")?.optString("refundableTag") == "RFN",
                    board = rate?.optString("boardName")?.ifBlank { null },
                    offerId = room.optString("offerId").ifBlank { null },
                )
                val best = out[hotelId]
                if (best == null || r.price < best.price) out[hotelId] = r
            }
        }
        return out
    }

    private suspend fun get(url: String, key: String) = request(url, key, null)
    private suspend fun post(url: String, key: String, body: String) = request(url, key, body)

    private suspend fun request(url: String, key: String, body: String?): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("X-API-Key", key)
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code in 200..299) return@withContext conn.inputStream.bufferedReader().use { it.readText() }
            throw ApiException(
                when (code) {
                    401, 403 -> com.riad.voyage.t("Clé LiteAPI refusée. Vérifiez-la dans Réglages.")
                    429 -> com.riad.voyage.t("Trop de recherches en peu de temps. Réessayez dans une minute.")
                    else -> com.riad.voyage.tf("Erreur du service d'hôtels ({0}).", code)
                },
                code,
            )
        } catch (e: IOException) {
            throw e
        } finally {
            conn.disconnect()
        }
    }
}

/** Réservation chez les sites partenaires, avec l'hôtel, les dates et le nombre de voyageurs déjà remplis. */
object HotelBooking {
    data class Partner(val name: String, val url: String)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun partners(query: String, checkIn: LocalDate, checkOut: LocalDate, adults: Int): List<Partner> {
        val q = enc(query.trim())
        val ci = checkIn.toString()
        val co = checkOut.toString()
        return listOf(
            Partner("Booking.com", "https://www.booking.com/searchresults.html?ss=$q&checkin=$ci&checkout=$co&group_adults=$adults&no_rooms=1&group_children=0"),
            Partner("Hotels.com", "https://www.hotels.com/Hotel-Search?destination=$q&startDate=$ci&endDate=$co&adults=$adults&rooms=1"),
            Partner("Expedia", "https://www.expedia.com/Hotel-Search?destination=$q&startDate=$ci&endDate=$co&adults=$adults&rooms=1"),
            Partner("Agoda", "https://www.agoda.com/search?textToSearch=$q&checkIn=$ci&checkOut=$co&adults=$adults&rooms=1"),
            Partner("Google Hôtels", "https://www.google.com/travel/search?q=${enc("$query $ci $co")}"),
        )
    }

    fun nights(checkIn: LocalDate, checkOut: LocalDate) = ChronoUnit.DAYS.between(checkIn, checkOut).coerceAtLeast(1)
}
