package com.riad.voyage.data

import java.net.URLEncoder

/** Liens de recherche pré-remplis pour les sorties : musées, attractions, visites, restaurants. */
object ActivityLinks {

    /** Identifiant partenaire Tiqets (programme d'affiliation) : ajouté à tous les liens Tiqets. */
    @Volatile var tiqetsPartner: String = ""

    private fun tiqets(query: String): String {
        val lang = com.riad.voyage.I18n.lang.code.let { if (it in setOf("fr", "en", "es", "ja", "zh")) it else "en" }
        val base = "https://www.tiqets.com/$lang/search?q=${enc(query)}"
        return if (tiqetsPartner.isBlank()) base else "$base&partner=${enc(tiqetsPartner.trim())}"
    }

    data class Partner(val name: String, val description: String, val url: String)

    enum class Category(val label: String, val emoji: String) {
        MUSEUMS("Musées", "🏛️"),
        MONUMENTS("Monuments", "🏰"),
        ATTRACTIONS("Attractions", "🎡"),
        PARKS("Parcs & jardins", "🌳"),
        BEACHES("Plages", "🏖️"),
        RESTAURANTS("Restaurants", "🍽️"),
        CAFES("Cafés", "☕"),
        BARS("Bars", "🍸"),
        NIGHTLIFE("Boîtes de nuit", "🎶"),
        SHOPPING("Shopping & marchés", "🛍️"),
        TOURS("Visites & excursions", "🧭"),
    }

    fun partners(city: String, category: Category): List<Partner> {
        val c = city.trim()
        return when (category) {
            Category.MUSEUMS -> listOf(
                Partner("Tiqets", "Billets coupe-file pour musées et monuments", tiqets(c)),
                Partner("GetYourGuide", "Billets et visites guidées de musées", "https://www.getyourguide.fr/s/?q=${enc("musées $c")}"),
                Partner("Google", "Horaires, avis et sites officiels", "https://www.google.com/search?q=${enc("musées $c")}"),
            )
            Category.ATTRACTIONS -> listOf(
                Partner("GetYourGuide", "Attractions et activités, annulation gratuite fréquente", "https://www.getyourguide.fr/s/?q=${enc(c)}"),
                Partner("Tiqets", "Billets d'entrée instantanés sur mobile", tiqets(c)),
                Partner("Klook", "Parcs d'attractions et pass, fort en Asie", "https://www.klook.com/fr/search/result/?query=${enc(c)}"),
                Partner("Viator", "Large choix d'activités (groupe Tripadvisor)", "https://www.viator.com/fr-FR/searchResults/all?text=${enc(c)}"),
            )
            Category.TOURS -> listOf(
                Partner("GetYourGuide", "Visites guidées et excursions d'une journée", "https://www.getyourguide.fr/s/?q=${enc("excursions $c")}"),
                Partner("Viator", "Excursions, croisières, visites privées", "https://www.viator.com/fr-FR/searchResults/all?text=${enc("excursions $c")}"),
                Partner("Klook", "Excursions et transferts", "https://www.klook.com/fr/search/result/?query=${enc(c)}"),
            )
            Category.RESTAURANTS -> listOf(
                Partner("Google Maps", "Restaurants autour de vous, avis et réservation", "https://www.google.com/maps/search/${enc("restaurants $c")}"),
                Partner("Tripadvisor", "Classements et avis de voyageurs", "https://www.tripadvisor.fr/Search?q=${enc("restaurants $c")}"),
                Partner("GetYourGuide", "Visites gastronomiques et cours de cuisine", "https://www.getyourguide.fr/s/?q=${enc("gastronomie $c")}"),
            )
            else -> listOf(
                Partner("Google Maps", "${category.label} : carte et avis", "https://www.google.com/maps/search/${enc("${category.label} $c")}"),
                Partner("Tripadvisor", "Classements et avis de voyageurs", "https://www.tripadvisor.fr/Search?q=${enc("${category.label} $c")}"),
            )
        }
    }

    /** Billets pour un lieu précis. */
    fun ticketUrls(place: PointOfInterest, city: String): List<Partner> {
        val q = "${place.name} ${city.trim()}"
        return listOf(
            Partner("GetYourGuide", "", "https://www.getyourguide.fr/s/?q=${enc(q)}"),
            Partner("Tiqets", "", tiqets(q)),
        )
    }

    fun restaurantUrl(place: PointOfInterest, city: String): String =
        place.website ?: place.mapsUrl ?: "https://www.google.com/maps/search/?api=1&query=${enc("${place.name} ${city.trim()}")}"

    fun directionsUrl(place: PointOfInterest, city: String = ""): String =
        if (place.hasCoordinates) "https://www.google.com/maps/dir/?api=1&destination=${place.lat},${place.lon}"
        else "https://www.google.com/maps/dir/?api=1&destination=${enc("${place.name} ${city.trim()}")}"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
