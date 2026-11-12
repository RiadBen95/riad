package com.riad.voyage.data

import java.net.URLEncoder

/** Liens de recherche pré-remplis pour les sorties : musées, attractions, visites, restaurants. */
object ActivityLinks {

    enum class Category(val label: String, val emoji: String) {
        MUSEUMS("Musées", "🏛️"),
        ATTRACTIONS("Attractions", "🎡"),
        TOURS("Visites & excursions", "🧭"),
        RESTAURANTS("Restaurants", "🍽️"),
    }

    fun partners(city: String, category: Category): List<HotelLinks.Partner> {
        val c = city.trim()
        return when (category) {
            Category.MUSEUMS -> listOf(
                HotelLinks.Partner("Tiqets", "Billets coupe-file pour musées et monuments", "https://www.tiqets.com/fr/search?q=${enc(c)}"),
                HotelLinks.Partner("GetYourGuide", "Billets et visites guidées de musées", "https://www.getyourguide.fr/s/?q=${enc("musées $c")}"),
                HotelLinks.Partner("Google", "Horaires, avis et sites officiels", "https://www.google.com/search?q=${enc("musées $c")}"),
            )
            Category.ATTRACTIONS -> listOf(
                HotelLinks.Partner("GetYourGuide", "Attractions et activités, annulation gratuite fréquente", "https://www.getyourguide.fr/s/?q=${enc(c)}"),
                HotelLinks.Partner("Tiqets", "Billets d'entrée instantanés sur mobile", "https://www.tiqets.com/fr/search?q=${enc(c)}"),
                HotelLinks.Partner("Klook", "Parcs d'attractions et pass, fort en Asie", "https://www.klook.com/fr/search/result/?query=${enc(c)}"),
                HotelLinks.Partner("Viator", "Large choix d'activités (groupe Tripadvisor)", "https://www.viator.com/fr-FR/searchResults/all?text=${enc(c)}"),
            )
            Category.TOURS -> listOf(
                HotelLinks.Partner("GetYourGuide", "Visites guidées et excursions d'une journée", "https://www.getyourguide.fr/s/?q=${enc("excursions $c")}"),
                HotelLinks.Partner("Viator", "Excursions, croisières, visites privées", "https://www.viator.com/fr-FR/searchResults/all?text=${enc("excursions $c")}"),
                HotelLinks.Partner("Klook", "Excursions et transferts", "https://www.klook.com/fr/search/result/?query=${enc(c)}"),
            )
            Category.RESTAURANTS -> listOf(
                HotelLinks.Partner("Google Maps", "Restaurants autour de vous, avis et réservation", "https://www.google.com/maps/search/${enc("restaurants $c")}"),
                HotelLinks.Partner("Tripadvisor", "Classements et avis de voyageurs", "https://www.tripadvisor.fr/Search?q=${enc("restaurants $c")}"),
                HotelLinks.Partner("GetYourGuide", "Visites gastronomiques et cours de cuisine", "https://www.getyourguide.fr/s/?q=${enc("gastronomie $c")}"),
            )
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
