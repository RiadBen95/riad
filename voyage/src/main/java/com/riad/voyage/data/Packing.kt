package com.riad.voyage.data

/** Un objet à emporter. [key] est le libellé (français, traduit à l'affichage) ; [count] une quantité. */
data class PackItem(val key: String, val category: String, val count: Int? = null)

/**
 * Liste de bagages générée selon la durée, la météo prévue, les activités du voyage, le pays
 * (prises électriques) et les compagnons de voyage.
 */
object Packing {
    const val DOCS = "Documents"
    const val CLOTHES = "Vêtements"
    const val HEALTH = "Toilette et santé"
    const val TECH = "Électronique"
    const val WEATHER = "Selon la météo"
    const val ACTIVITIES = "Selon vos activités"

    val categories = listOf(DOCS, CLOTHES, HEALTH, TECH, WEATHER, ACTIVITIES)

    fun generate(
        nights: Int,
        weather: List<DayWeather>,
        activityKinds: List<String>,
        country: CountryInfo?,
        family: Boolean = false,
    ): List<PackItem> {
        val days = (nights + 1).coerceAtLeast(1)
        val kinds = activityKinds.map { it.plain() }
        fun any(vararg words: String) = kinds.any { k -> words.any { it in k } }
        return buildList {
            add(PackItem("Passeport ou carte d'identité", DOCS))
            add(PackItem("Carte bancaire et un peu d'espèces", DOCS))
            add(PackItem("Réservations enregistrées hors connexion", DOCS))
            if (nights >= 2) add(PackItem("Assurance voyage", DOCS))

            add(PackItem("Tenues de jour", CLOTHES, days.coerceAtMost(7)))
            add(PackItem("Sous-vêtements et chaussettes", CLOTHES, days.coerceAtMost(8)))
            if (nights > 0) add(PackItem("Pyjama", CLOTHES))
            add(PackItem("Chaussures confortables pour marcher", CLOTHES))
            if (nights >= 5) add(PackItem("Lessive de voyage", CLOTHES))

            add(PackItem("Trousse de toilette", HEALTH))
            add(PackItem("Médicaments personnels", HEALTH))
            if (nights >= 3 || family) add(PackItem("Petite trousse de premiers secours", HEALTH))

            add(PackItem("Téléphone et chargeur", TECH))
            add(PackItem("Batterie externe", TECH))
            add(PackItem("Écouteurs", TECH))
            if (country?.needsAdapter == true) add(PackItem("Adaptateur de prise", TECH))

            if (weather.isEmpty()) {
                add(PackItem("Veste légère", WEATHER))
            } else {
                if (weather.any { it.rainy }) add(PackItem("Parapluie ou imperméable", WEATHER))
                if (weather.any { it.sunny || it.hot }) {
                    add(PackItem("Crème solaire", WEATHER))
                    add(PackItem("Lunettes de soleil", WEATHER))
                }
                if (weather.any { it.hot }) {
                    add(PackItem("Chapeau ou casquette", WEATHER))
                    add(PackItem("Gourde", WEATHER))
                }
                if (weather.any { it.tMin < 8 }) {
                    add(PackItem("Manteau chaud", WEATHER))
                    add(PackItem("Bonnet, gants et écharpe", WEATHER))
                } else if (weather.any { it.tMin < 15 }) {
                    add(PackItem("Pull ou veste", WEATHER))
                }
            }

            if (any("plage", "beach", "crique")) {
                add(PackItem("Maillot de bain", ACTIVITIES))
                add(PackItem("Serviette de plage", ACTIVITIES))
                add(PackItem("Sandales ou tongs", ACTIVITIES))
            }
            if (any("parc", "jardin", "randonnee", "montagne", "nature", "reserve", "cascade")) {
                add(PackItem("Petit sac à dos", ACTIVITIES))
            }
            if (any("randonnee", "montagne", "sentier", "volcan")) add(PackItem("Chaussures de marche", ACTIVITIES))
            if (any("eglise", "cathedrale", "basilique", "mosquee", "temple", "synagogue", "monastere")) {
                add(PackItem("Tenue couvrant épaules et genoux (lieux de culte)", ACTIVITIES))
            }
            if (any("bar", "boite", "club", "cabaret", "opera", "theatre")) add(PackItem("Une tenue pour sortir le soir", ACTIVITIES))
            if (family) add(PackItem("Jeux et en-cas pour les enfants", ACTIVITIES))
        }.distinctBy { it.key }
    }
}
