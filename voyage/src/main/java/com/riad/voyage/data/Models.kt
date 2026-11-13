package com.riad.voyage.data

import java.time.LocalDate
import java.time.OffsetDateTime

/** Ville ou aéroport (codes IATA : PAR, ALG, CDG…). */
data class Place(
    val code: String,
    val name: String,
    val country: String,
    val type: String,          // "city" ou "airport"
    val cityName: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val isAirport get() = type == "airport"
    val label get() = if (isAirport) "$name ($code)" else name
    val subtitle: String
        get() {
            val where = listOfNotNull(cityName?.takeIf { isAirport }, country.ifBlank { null }).joinToString(", ")
            val kind = if (isAirport) "Aéroport" else "Tous les aéroports"
            return if (where.isBlank()) kind else "$where · $kind"
        }
}

data class Flight(
    val origin: String,
    val destination: String,
    val originAirport: String,
    val destinationAirport: String,
    val price: Int,
    val airline: String,
    val flightNumber: String,
    val departureAt: String,
    val returnAt: String?,
    val transfers: Int,
    val returnTransfers: Int,
    val durationTo: Int,       // minutes
    val durationBack: Int,     // minutes (0 si aller simple)
    val link: String,
) {
    val totalDuration get() = durationTo + durationBack
    val totalTransfers get() = transfers + returnTransfers
    val departureDate: LocalDate? get() = parseDateTime(departureAt)?.toLocalDate()
}

/** Prix le plus bas trouvé pour un jour de départ. */
data class DayPrice(
    val date: LocalDate,
    val price: Int,
    val transfers: Int,
    val airline: String,
)

data class PriceAlert(
    val id: String,
    val origin: String,
    val originName: String,
    val destination: String,
    val destinationName: String,
    val departure: String,       // AAAA-MM-JJ ou AAAA-MM (mois entier)
    val returnDate: String?,
    val directOnly: Boolean,
    val lastPrice: Int?,
    val currency: String,
    val lastChecked: Long,
)

class ApiException(message: String, val code: Int = 0) : Exception(message)

fun parseDateTime(s: String?): OffsetDateTime? =
    s?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
