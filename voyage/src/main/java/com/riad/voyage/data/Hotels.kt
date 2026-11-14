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
    val rank: Int? = null,        // ordre de pertinence (recherche en langage naturel)
    val match: String? = null,    // pourquoi l'hôtel correspond (étiquettes LiteAPI)
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
 * pour les dates et le nombre de voyageurs. Clé gratuite (« sand_… » pour tester, « prod_… » en réel).
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

    /**
     * Recherche en langage naturel (« calme avec piscine près de la plage ») : LiteAPI trouve les
     * hôtels qui correspondent à l'intention (paramètre aiSearch des prix), classés par pertinence.
     * Si ce paramètre est refusé, recherche sémantique (/data/hotel/search) puis prix de ces hôtels.
     */
    suspend fun aiSearch(
        key: String,
        query: String,
        city: String,
        center: GeoPoint,
        checkIn: LocalDate,
        checkOut: LocalDate,
        adults: Int,
        currency: String,
        nationality: String,
    ): List<HotelOffer> {
        val phrase = "${query.trim()}, $city"
        fun ratesBody() = JSONObject()
            .put("occupancies", JSONArray().put(JSONObject().put("adults", adults)))
            .put("currency", currency)
            .put("guestNationality", nationality)
            .put("checkin", checkIn.toString())
            .put("checkout", checkOut.toString())
            .put("timeout", 8)
            .put("maxRatesPerHotel", 3)
        // Fiches des hôtels de la ville (nom, photo, note…) pour compléter les résultats.
        val nearby = runCatching {
            parseHotels(get("$BASE/data/hotels?latitude=${center.lat}&longitude=${center.lon}&radius=15000&limit=200", key), center)
        }.getOrDefault(emptyList()).associateBy { it.id }

        val (ratesJson, semantic) = try {
            post("$BASE/hotels/rates", key, ratesBody().put("aiSearch", phrase).toString()) to emptyList<SemanticHit>()
        } catch (e: ApiException) {
            if (e.code == 401 || e.code == 403) throw e
            val hits = parseSemantic(get("$BASE/data/hotel/search?query=${java.net.URLEncoder.encode(phrase, "UTF-8")}&limit=30", key))
            if (hits.isEmpty()) return emptyList()
            post("$BASE/hotels/rates", key, ratesBody().put("hotelIds", JSONArray(hits.map { it.id })).toString()) to hits
        }
        val rates = parseRates(ratesJson)
        val order = if (semantic.isNotEmpty()) semantic.map { it.id } else rateOrder(ratesJson)
        val inline = parseHotels(JSONObject(ratesJson).optJSONArray("hotels")?.let { JSONObject().put("data", it).toString() } ?: "{}", center)
            .associateBy { it.id }
        val tags = semantic.associate { it.id to it.tags }
        return order.mapIndexedNotNull { i, id ->
            val r = rates[id] ?: return@mapIndexedNotNull null
            val h = inline[id]?.let { a -> nearby[id]?.let { b -> b.copy(photo = b.photo ?: a.photo, rating = b.rating ?: a.rating) } ?: a }
                ?: nearby[id] ?: semantic.firstOrNull { it.id == id }?.toOffer(center) ?: return@mapIndexedNotNull null
            h.copy(totalPrice = r.price, currency = r.currency ?: currency, refundable = r.refundable, board = r.board,
                offerId = r.offerId, rank = i, match = tags[id])
        }
    }

    /** Ordre des hôtels dans la réponse des prix (pertinence). */
    fun rateOrder(json: String): List<String> {
        val arr = JSONObject(json).optJSONArray("data") ?: return emptyList()
        return List(arr.length()) { arr.getJSONObject(it).optString("hotelId") }.filter { it.isNotBlank() }.distinct()
    }

    data class SemanticHit(val id: String, val name: String?, val tags: String?, val photo: String?, val lat: Double?, val lon: Double?) {
        fun toOffer(center: GeoPoint) = name?.let {
            HotelOffer(id, it, null, null, null, null, photo, lat, lon,
                if (lat != null && lon != null) PlacesParser.distanceMeters(center.lat, center.lon, lat, lon).toInt() else null,
                null, "EUR", false, null, null)
        }
    }

    /** Résultats de la recherche sémantique : identifiant, nom, étiquettes (style, ambiance…). */
    fun parseSemantic(json: String): List<SemanticHit> {
        val arr = JSONObject(json).optJSONArray("data") ?: return emptyList()
        return List(arr.length()) { arr.getJSONObject(it) }.mapNotNull { o ->
            val id = o.optString("hotelId").ifBlank { o.optString("id") }.ifBlank { return@mapNotNull null }
            val tagArr = o.optJSONArray("tags")
            val tags = (if (tagArr != null) List(tagArr.length()) { tagArr.optString(it) } else emptyList())
                .plus(listOf(o.optString("style"), o.optString("location_type")))
                .filter { it.isNotBlank() && it != "null" }.distinct().take(4).joinToString(" · ").ifBlank { null }
            SemanticHit(id, o.optString("name").ifBlank { null }, tags,
                o.optString("main_photo").ifBlank { o.optString("thumbnail") }.ifBlank { null },
                o.optDouble("latitude").takeIf { !it.isNaN() }, o.optDouble("longitude").takeIf { !it.isNaN() })
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
            val hotelId = h.optString("hotelId")
            if (hotelId.isBlank()) continue
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

/** Fiche complète d'un hôtel (LiteAPI /data/hotel). */
data class HotelDetail(
    val id: String,
    val name: String,
    val description: String?,
    val important: String?,
    val images: List<String>,
    val facilities: List<String>,
    val checkin: String?,
    val checkout: String?,
    val address: String?,
    val stars: Int?,
    val rating: Double?,
    val reviews: Int?,
    val rooms: List<RoomInfo>,
)

data class RoomInfo(val id: String, val name: String, val description: String?, val sizeM2: Int?, val maxOccupancy: Int?, val photo: String?, val amenities: List<String>)

data class HotelReview(val score: Double?, val name: String?, val country: String?, val date: String?, val headline: String?, val pros: String?, val cons: String?, val type: String?)

/** Ce que les clients disent le plus souvent (analyse des avis par LiteAPI). */
data class ReviewSummary(val categories: List<Pair<String, Double>>, val pros: List<String>, val cons: List<String>)

/** Une offre de chambre précise, réservable. */
data class RoomOffer(
    val offerId: String,
    val roomName: String,
    val board: String?,
    val price: Double,
    val currency: String,
    val refundable: Boolean,
    val cancelDeadline: String?,
)

/** Pré-réservation. [secretKey] / [transactionId] : paiement sécurisé LiteAPI (le client paie LiteAPI directement). */
data class Prebook(
    val prebookId: String,
    val price: Double?,
    val currency: String?,
    val secretKey: String? = null,
    val transactionId: String? = null,
)

/** Page de paiement LiteAPI (formulaire de carte sécurisé), affichée dans l'app. */
object LitePayment {
    const val RETURN_URL = "https://envolee.app/paiement-termine"

    private val trustedHosts = listOf("liteapi.travel", "stripe.com", "stripe.network", "stripecdn.com")

    /** Page autorisée dans la fenêtre de paiement (https vers LiteAPI ou Stripe uniquement). */
    fun allowed(url: String): Boolean {
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val host = u.host?.lowercase() ?: return false
        return u.scheme == "https" && trustedHosts.any { host == it || host.endsWith(".$it") }
    }

    /** Clé de test LiteAPI : « sand_… » (les clés réelles commencent par « prod_ »). */
    fun isSandbox(key: String) = key.trim().lowercase().let { it.startsWith("sand") }

    fun html(secretKey: String, sandbox: Boolean, businessName: String = "Envolée"): String {
        fun js(s: String) = JSONObject.quote(s)
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font-family:sans-serif;margin:16px;background:#fff;color:#111}</style>
<script src="https://payment-wrapper.liteapi.travel/dist/liteAPIPayment.js?v=a1"></script></head>
<body><div id="targetElement" style="min-height:420px"></div>
<script>
var liteAPIConfig = {
  publicKey: ${js(if (sandbox) "sandbox" else "live")},
  appearance: { theme: 'flat' },
  options: { business: { name: ${js(businessName)} } },
  targetElement: '#targetElement',
  secretKey: ${js(secretKey)},
  returnUrl: ${js(RETURN_URL)}
};
window.onerror = function(m){ console.error(String(m)); };
function start(){
  if (typeof LiteAPIPayment === 'undefined') { console.error('Module de paiement LiteAPI non chargé'); return; }
  try { var p = new LiteAPIPayment(liteAPIConfig); p.handlePayment(); } catch (e) { console.error(String(e && e.message || e)); }
}
if (document.readyState === 'complete') start(); else window.addEventListener('load', start);
</script></body></html>"""
    }
}

data class HotelBookingResult(val bookingId: String, val status: String?, val confirmation: String?, val price: Double?, val currency: String?)

/** Au-delà de la recherche : fiche, avis, toutes les offres de chambres, pré-réservation et réservation. */
object LiteApiMore {
    private const val BASE = "https://api.liteapi.travel/v3.0"
    private const val BOOK = "https://book.liteapi.travel/v3.0"

    private fun JSONObject.s(k: String): String? = if (isNull(k)) null else optString(k).ifBlank { null }

    private fun stripHtml(s: String?) = s?.replace(Regex("<br\\s*/?>|</p>|</li>", RegexOption.IGNORE_CASE), "\n")
        ?.replace(Regex("<[^>]+>"), "")?.replace("&nbsp;", " ")?.replace("&amp;", "&")
        ?.replace(Regex("\n{3,}"), "\n\n")?.trim()?.ifBlank { null }

    suspend fun detail(key: String, hotelId: String): HotelDetail? =
        parseDetail(LiteHttp.call("$BASE/data/hotel?hotelId=${enc(hotelId)}&timeout=8", key))

    fun parseDetail(json: String): HotelDetail? {
        val d = JSONObject(json).optJSONObject("data") ?: return null
        val images = d.optJSONArray("hotelImages")?.let { a ->
            List(a.length()) { a.optJSONObject(it) }.filterNotNull()
                .sortedByDescending { it.optBoolean("defaultImage") }
                .mapNotNull { it.s("urlHd") ?: it.s("url") }
        }.orEmpty().ifEmpty { listOfNotNull(d.s("main_photo")) }
        val facilities = (d.optJSONArray("hotelFacilities")?.let { a -> List(a.length()) { a.optString(it) } }
            ?: d.optJSONArray("facilities")?.let { a -> List(a.length()) { a.optJSONObject(it)?.s("name") ?: a.optString(it) } })
            .orEmpty().filter { it.isNotBlank() }.distinct()
        val times = d.optJSONObject("checkinCheckoutTimes")
        val rooms = d.optJSONArray("rooms")?.let { a ->
            List(a.length()) { a.optJSONObject(it) }.filterNotNull().map { r ->
                RoomInfo(
                    id = r.optString("id"),
                    name = r.s("roomName") ?: r.s("name") ?: "",
                    description = stripHtml(r.s("description")),
                    sizeM2 = r.optInt("roomSizeSquare", 0).takeIf { it > 0 },
                    maxOccupancy = r.optInt("maxOccupancy", 0).takeIf { it > 0 },
                    photo = r.optJSONArray("photos")?.optJSONObject(0)?.let { it.s("hd_url") ?: it.s("url") },
                    amenities = r.optJSONArray("roomAmenities")?.let { am -> List(am.length()) { am.optJSONObject(it)?.s("name") }.filterNotNull() }.orEmpty(),
                )
            }.filter { it.name.isNotBlank() }
        }.orEmpty()
        return HotelDetail(
            id = d.optString("id"),
            name = d.optString("name"),
            description = stripHtml(d.s("hotelDescription")),
            important = stripHtml(d.s("hotelImportantInformation")),
            images = images,
            facilities = facilities,
            checkin = times?.s("checkin") ?: times?.s("checkin_start"),
            checkout = times?.s("checkout"),
            address = d.s("address"),
            stars = d.optDouble("starRating").takeIf { !it.isNaN() && it > 0 }?.toInt()
                ?: d.optDouble("stars").takeIf { !it.isNaN() && it > 0 }?.toInt(),
            rating = d.optDouble("rating").takeIf { !it.isNaN() && it > 0 },
            reviews = d.optInt("reviewCount", 0).takeIf { it > 0 },
            rooms = rooms,
        )
    }

    suspend fun reviews(key: String, hotelId: String): Pair<List<HotelReview>, ReviewSummary?> =
        parseReviews(LiteHttp.call("$BASE/data/reviews?hotelId=${enc(hotelId)}&limit=15&timeout=8&getSentiment=true", key))

    fun parseReviews(json: String): Pair<List<HotelReview>, ReviewSummary?> {
        val root = JSONObject(json)
        val list = root.optJSONArray("data")?.let { a ->
            List(a.length()) { a.optJSONObject(it) }.filterNotNull().map { r ->
                HotelReview(
                    score = r.optDouble("averageScore").takeIf { !it.isNaN() },
                    name = r.s("name"), country = r.s("country"), date = r.s("date")?.take(10),
                    headline = r.s("headline"), pros = r.s("pros"), cons = r.s("cons"), type = r.s("type"),
                )
            }.filter { it.headline != null || it.pros != null || it.cons != null }
        }.orEmpty()
        val sa = root.optJSONObject("sentimentAnalysis") ?: root.optJSONObject("data")?.optJSONObject("sentimentAnalysis")
        val summary = sa?.let { s ->
            ReviewSummary(
                categories = s.optJSONArray("categories")?.let { a ->
                    List(a.length()) { a.optJSONObject(it) }.filterNotNull().mapNotNull { c ->
                        val n = c.s("name") ?: return@mapNotNull null
                        val v = c.optDouble("rating").takeIf { !it.isNaN() } ?: return@mapNotNull null
                        n to v
                    }
                }.orEmpty(),
                pros = s.optJSONArray("pros")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty().filter { it.isNotBlank() },
                cons = s.optJSONArray("cons")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty().filter { it.isNotBlank() },
            )
        }
        return list to summary
    }

    /** Toutes les offres de chambres d'un hôtel pour les dates. */
    suspend fun offers(key: String, hotelId: String, checkIn: LocalDate, checkOut: LocalDate, adults: Int, currency: String, nationality: String): List<RoomOffer> {
        val body = JSONObject()
            .put("hotelIds", JSONArray().put(hotelId))
            .put("occupancies", JSONArray().put(JSONObject().put("adults", adults)))
            .put("currency", currency).put("guestNationality", nationality)
            .put("checkin", checkIn.toString()).put("checkout", checkOut.toString()).put("timeout", 8)
        return parseOffers(LiteHttp.call("$BASE/hotels/rates", key, body.toString()))
    }

    fun parseOffers(json: String): List<RoomOffer> {
        val arr = JSONObject(json).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<RoomOffer>()
        for (i in 0 until arr.length()) {
            val rooms = arr.getJSONObject(i).optJSONArray("roomTypes") ?: continue
            for (j in 0 until rooms.length()) {
                val room = rooms.getJSONObject(j)
                val offerId = room.s("offerId") ?: continue
                val rate = room.optJSONArray("rates")?.optJSONObject(0)
                val total = rate?.optJSONObject("retailRate")?.optJSONArray("total")?.optJSONObject(0)
                val offer = room.optJSONObject("offerRetailRate")
                val price = offer?.optDouble("amount")?.takeIf { !it.isNaN() } ?: total?.optDouble("amount")?.takeIf { !it.isNaN() } ?: continue
                val policies = rate?.optJSONObject("cancellationPolicies")
                out += RoomOffer(
                    offerId = offerId,
                    roomName = rate?.s("name") ?: room.s("name") ?: "",
                    board = rate?.s("boardName"),
                    price = price,
                    currency = offer?.s("currency") ?: total?.s("currency") ?: "EUR",
                    refundable = policies?.s("refundableTag") == "RFN",
                    cancelDeadline = policies?.optJSONArray("cancelPolicyInfos")?.optJSONObject(0)?.s("cancelTime")?.take(16)?.replace('T', ' '),
                )
            }
        }
        return out.sortedBy { it.price }
    }

    suspend fun prebook(key: String, offerId: String): Prebook? {
        val body = JSONObject().put("offerId", offerId).put("usePaymentSdk", true)
        val d = JSONObject(LiteHttp.call("$BOOK/rates/prebook", key, body.toString())).optJSONObject("data") ?: return null
        return Prebook(
            d.s("prebookId") ?: return null, d.optDouble("price").takeIf { !it.isNaN() }, d.s("currency"),
            secretKey = d.s("secretKey"), transactionId = d.s("transactionId"),
        )
    }

    /**
     * Réservation. Avec [transactionId] (paiement sécurisé LiteAPI réussi) : réservation réelle payée
     * par le voyageur. Sans : paiement fictif ACC_CREDIT_CARD, accepté seulement en mode test.
     */
    suspend fun book(key: String, prebookId: String, firstName: String, lastName: String, email: String, transactionId: String? = null): HotelBookingResult? {
        val person = JSONObject().put("firstName", firstName).put("lastName", lastName).put("email", email)
        val body = JSONObject()
            .put("prebookId", prebookId)
            .put("holder", person)
            .put("guests", JSONArray().put(JSONObject(person.toString()).put("occupancyNumber", 1)))
            .put("payment", if (transactionId != null) JSONObject().put("method", "TRANSACTION_ID").put("transactionId", transactionId)
                else JSONObject().put("method", "ACC_CREDIT_CARD"))
        val d = JSONObject(LiteHttp.call("$BOOK/rates/book", key, body.toString())).optJSONObject("data") ?: return null
        return HotelBookingResult(
            bookingId = d.s("bookingId") ?: return null,
            status = d.s("status"),
            confirmation = d.s("hotelConfirmationCode") ?: d.s("supplierBookingId"),
            price = d.optDouble("price").takeIf { !it.isNaN() },
            currency = d.s("currency"),
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** Appels LiteAPI (clé dans l'en-tête X-API-Key). */
internal object LiteHttp {
    suspend fun call(url: String, key: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 25_000
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
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val message = runCatching { JSONObject(err).optJSONObject("error")?.optString("message") }.getOrNull()?.ifBlank { null }
            throw ApiException(
                when (code) {
                    401, 403 -> com.riad.voyage.t("Clé LiteAPI refusée. Vérifiez-la dans Réglages.")
                    else -> message ?: com.riad.voyage.tf("Erreur du service d'hôtels ({0}).", code)
                },
                code,
            )
        } finally {
            conn.disconnect()
        }
    }
}
