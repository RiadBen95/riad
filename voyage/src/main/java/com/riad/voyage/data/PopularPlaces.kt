package com.riad.voyage.data

import java.text.Normalizer

/** Liste de secours quand l'autocomplétion en ligne est indisponible. */
object PopularPlaces {
    val all = listOf(
        Place("PAR", "Paris", "France", "city", lat = 48.8566, lon = 2.3522),
        Place("MRS", "Marseille", "France", "city", lat = 43.2965, lon = 5.3698),
        Place("LYS", "Lyon", "France", "city", lat = 45.764, lon = 4.8357),
        Place("NCE", "Nice", "France", "city", lat = 43.7102, lon = 7.262),
        Place("TLS", "Toulouse", "France", "city", lat = 43.6047, lon = 1.4442),
        Place("BOD", "Bordeaux", "France", "city", lat = 44.8378, lon = -0.5792),
        Place("NTE", "Nantes", "France", "city", lat = 47.2184, lon = -1.5536),
        Place("LIL", "Lille", "France", "city", lat = 50.6292, lon = 3.0573),
        Place("MPL", "Montpellier", "France", "city", lat = 43.6108, lon = 3.8767),
        Place("ALG", "Alger", "Algérie", "city", lat = 36.7538, lon = 3.0588),
        Place("ORN", "Oran", "Algérie", "city", lat = 35.6971, lon = -0.6308),
        Place("CZL", "Constantine", "Algérie", "city", lat = 36.365, lon = 6.6147),
        Place("AAE", "Annaba", "Algérie", "city", lat = 36.9, lon = 7.7667),
        Place("BJA", "Béjaïa", "Algérie", "city", lat = 36.7509, lon = 5.0567),
        Place("TLM", "Tlemcen", "Algérie", "city", lat = 34.8783, lon = -1.315),
        Place("TUN", "Tunis", "Tunisie", "city", lat = 36.8065, lon = 10.1815),
        Place("CAS", "Casablanca", "Maroc", "city", lat = 33.5731, lon = -7.5898),
        Place("RAK", "Marrakech", "Maroc", "city", lat = 31.6295, lon = -7.9811),
        Place("LON", "Londres", "Royaume-Uni", "city", lat = 51.5074, lon = -0.1278),
        Place("BCN", "Barcelone", "Espagne", "city", lat = 41.3851, lon = 2.1734),
        Place("MAD", "Madrid", "Espagne", "city", lat = 40.4168, lon = -3.7038),
        Place("LIS", "Lisbonne", "Portugal", "city", lat = 38.7223, lon = -9.1393),
        Place("ROM", "Rome", "Italie", "city", lat = 41.9028, lon = 12.4964),
        Place("MIL", "Milan", "Italie", "city", lat = 45.4642, lon = 9.19),
        Place("AMS", "Amsterdam", "Pays-Bas", "city", lat = 52.3676, lon = 4.9041),
        Place("BRU", "Bruxelles", "Belgique", "city", lat = 50.8503, lon = 4.3517),
        Place("GVA", "Genève", "Suisse", "city", lat = 46.2044, lon = 6.1432),
        Place("BER", "Berlin", "Allemagne", "city", lat = 52.52, lon = 13.405),
        Place("IST", "Istanbul", "Turquie", "city", lat = 41.0082, lon = 28.9784),
        Place("DXB", "Dubaï", "Émirats arabes unis", "city", lat = 25.2048, lon = 55.2708),
        Place("CAI", "Le Caire", "Égypte", "city", lat = 30.0444, lon = 31.2357),
        Place("JED", "Djeddah", "Arabie saoudite", "city", lat = 21.4858, lon = 39.1925),
        Place("NYC", "New York", "États-Unis", "city", lat = 40.7128, lon = -74.006),
        Place("YMQ", "Montréal", "Canada", "city", lat = 45.5017, lon = -73.5673),
        Place("BKK", "Bangkok", "Thaïlande", "city", lat = 13.7563, lon = 100.5018),
        Place("TYO", "Tokyo", "Japon", "city", lat = 35.6762, lon = 139.6503),
    )

    fun find(code: String): Place? = all.firstOrNull { it.code == code }

    fun filter(term: String): List<Place> {
        val t = normalize(term)
        if (t.isBlank()) return emptyList()
        return all.filter { normalize(it.name).startsWith(t) || it.code.equals(term.trim(), ignoreCase = true) } +
            all.filter { !normalize(it.name).startsWith(t) && normalize(it.name).contains(t) }
    }

    private fun normalize(s: String) =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
