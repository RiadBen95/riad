package com.riad.voyage.data

import java.text.Normalizer

/** Liste de secours quand l'autocomplétion en ligne est indisponible. */
object PopularPlaces {
    val all = listOf(
        Place("PAR", "Paris", "France", "city"),
        Place("MRS", "Marseille", "France", "city"),
        Place("LYS", "Lyon", "France", "city"),
        Place("NCE", "Nice", "France", "city"),
        Place("TLS", "Toulouse", "France", "city"),
        Place("BOD", "Bordeaux", "France", "city"),
        Place("NTE", "Nantes", "France", "city"),
        Place("LIL", "Lille", "France", "city"),
        Place("MPL", "Montpellier", "France", "city"),
        Place("ALG", "Alger", "Algérie", "city"),
        Place("ORN", "Oran", "Algérie", "city"),
        Place("CZL", "Constantine", "Algérie", "city"),
        Place("AAE", "Annaba", "Algérie", "city"),
        Place("BJA", "Béjaïa", "Algérie", "city"),
        Place("TLM", "Tlemcen", "Algérie", "city"),
        Place("TUN", "Tunis", "Tunisie", "city"),
        Place("CAS", "Casablanca", "Maroc", "city"),
        Place("RAK", "Marrakech", "Maroc", "city"),
        Place("LON", "Londres", "Royaume-Uni", "city"),
        Place("BCN", "Barcelone", "Espagne", "city"),
        Place("MAD", "Madrid", "Espagne", "city"),
        Place("LIS", "Lisbonne", "Portugal", "city"),
        Place("ROM", "Rome", "Italie", "city"),
        Place("MIL", "Milan", "Italie", "city"),
        Place("AMS", "Amsterdam", "Pays-Bas", "city"),
        Place("BRU", "Bruxelles", "Belgique", "city"),
        Place("GVA", "Genève", "Suisse", "city"),
        Place("BER", "Berlin", "Allemagne", "city"),
        Place("IST", "Istanbul", "Turquie", "city"),
        Place("DXB", "Dubaï", "Émirats arabes unis", "city"),
        Place("CAI", "Le Caire", "Égypte", "city"),
        Place("JED", "Djeddah", "Arabie saoudite", "city"),
        Place("NYC", "New York", "États-Unis", "city"),
        Place("YMQ", "Montréal", "Canada", "city"),
        Place("BKK", "Bangkok", "Thaïlande", "city"),
        Place("TYO", "Tokyo", "Japon", "city"),
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
