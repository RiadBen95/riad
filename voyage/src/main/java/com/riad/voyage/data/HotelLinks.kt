package com.riad.voyage.data

import java.net.URLEncoder
import java.time.LocalDate

/** Liens de recherche d'hôtels pré-remplis chez les principaux sites de réservation. */
object HotelLinks {

    data class Partner(val name: String, val description: String, val url: String)

    fun partners(city: String, checkIn: LocalDate, checkOut: LocalDate, adults: Int, rooms: Int): List<Partner> {
        val c = enc(city.trim())
        val ci = checkIn.toString()
        val co = checkOut.toString()
        return listOf(
            Partner(
                "Booking.com", "Le plus grand choix, annulation gratuite fréquente",
                "https://www.booking.com/searchresults.fr.html?ss=$c&checkin=$ci&checkout=$co" +
                    "&group_adults=$adults&no_rooms=$rooms&group_children=0",
            ),
            Partner(
                "Hotels.com", "Programme de fidélité : nuits offertes",
                "https://fr.hotels.com/Hotel-Search?destination=$c&startDate=$ci&endDate=$co&adults=$adults&rooms=$rooms",
            ),
            Partner(
                "Expedia", "Bons tarifs sur les formules vol + hôtel",
                "https://www.expedia.fr/Hotel-Search?destination=$c&startDate=$ci&endDate=$co&adults=$adults&rooms=$rooms",
            ),
            Partner(
                "Agoda", "Souvent moins cher en Asie et au Moyen-Orient",
                "https://www.agoda.com/fr-fr/search?textToSearch=$c&checkIn=$ci&checkOut=$co&adults=$adults&rooms=$rooms",
            ),
            Partner(
                "Google Hôtels", "Compare de nombreux sites à la fois",
                "https://www.google.com/travel/search?q=${enc("hôtels $city du $ci au $co")}",
            ),
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
