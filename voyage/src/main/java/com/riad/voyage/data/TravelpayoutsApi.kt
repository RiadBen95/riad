package com.riad.voyage.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Accès à l'API de données Aviasales (programme d'affiliation Travelpayouts).
 * Les prix proviennent des recherches faites par les voyageurs ces derniers jours :
 * ce sont de vrais tarifs, mais à confirmer sur le site du vendeur.
 */
class TravelpayoutsApi(
    private val token: String,
    private val currency: String,
) {
    private fun placesLocale() = when (com.riad.voyage.I18n.lang) {
        com.riad.voyage.Lang.FR -> "fr"
        com.riad.voyage.Lang.ES -> "es"
        else -> "en"
    }

    suspend fun searchPlaces(term: String): List<Place> {
        val url = "https://autocomplete.travelpayouts.com/places2?term=${enc(term)}" +
            "&locale=${placesLocale()}&types%5B%5D=city&types%5B%5D=airport"
        return Parsers.places(get(url, auth = false))
    }

    /**
     * @param departure AAAA-MM-JJ, ou AAAA-MM pour chercher sur tout le mois.
     * @param returnDate idem ; null pour un aller simple.
     */
    suspend fun pricesForDates(
        origin: String,
        destination: String,
        departure: String,
        returnDate: String?,
        directOnly: Boolean,
        limit: Int = 30,
    ): List<Flight> {
        val sb = StringBuilder("https://api.travelpayouts.com/aviasales/v3/prices_for_dates")
            .append("?origin=").append(enc(origin))
            .append("&destination=").append(enc(destination))
            .append("&departure_at=").append(enc(departure))
            .append("&currency=").append(currency.lowercase())
            .append("&sorting=price&unique=false")
            .append("&direct=").append(directOnly)
            .append("&limit=").append(limit)
        if (returnDate != null) {
            sb.append("&return_at=").append(enc(returnDate)).append("&one_way=false")
        } else {
            sb.append("&one_way=true")
        }
        return Parsers.flights(get(sb.toString(), auth = true))
    }

    /** Prix aller le plus bas pour chaque jour du mois. */
    suspend fun calendar(origin: String, destination: String, month: YearMonth, directOnly: Boolean): List<DayPrice> {
        val url = "https://api.travelpayouts.com/aviasales/v3/grouped_prices" +
            "?origin=${enc(origin)}&destination=${enc(destination)}" +
            "&departure_at=$month&group_by=departure_at" +
            "&currency=${currency.lowercase()}&direct=$directOnly"
        return Parsers.calendar(get(url, auth = true))
    }

    private suspend fun get(url: String, auth: Boolean): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            if (auth && token.isNotBlank()) conn.setRequestProperty("X-Access-Token", token)
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw ApiException(
                    when (code) {
                        401, 403 -> com.riad.voyage.t("Clé API refusée. Vérifiez-la dans Réglages.")
                        429 -> com.riad.voyage.t("Trop de recherches en peu de temps. Réessayez dans une minute.")
                        else -> "Erreur du serveur ($code). ${detail.take(120)}"
                    },
                    code,
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        private val ddMM = DateTimeFormatter.ofPattern("ddMM")

        /** Lien vers une offre précise renvoyée par l'API, avec l'identifiant d'affilié. */
        fun bookingUrl(link: String, marker: String): String {
            val base = if (link.startsWith("http")) link else "https://www.aviasales.com$link"
            if (marker.isBlank()) return base
            val sep = if (base.contains('?')) "&" else "?"
            return "$base${sep}marker=${enc(marker)}"
        }

        /** Lien vers une recherche complète sur Aviasales (ex. /search/PAR0112ALG15121). */
        fun searchUrl(
            origin: String,
            destination: String,
            departure: LocalDate,
            returnDate: LocalDate?,
            adults: Int,
            marker: String,
        ): String {
            val path = origin + departure.format(ddMM) + destination +
                (returnDate?.format(ddMM) ?: "") + adults.coerceIn(1, 9)
            val base = "https://www.aviasales.com/search/$path"
            return if (marker.isBlank()) base else "$base?marker=${enc(marker)}"
        }

        fun userMessage(e: Throwable): String = when (e) {
            is ApiException -> e.message ?: com.riad.voyage.t("Erreur inconnue")
            is IOException -> com.riad.voyage.t("Pas de connexion internet (ou serveur injoignable).")
            else -> "Erreur : ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
