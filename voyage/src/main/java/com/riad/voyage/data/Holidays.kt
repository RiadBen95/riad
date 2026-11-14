package com.riad.voyage.data

import java.time.LocalDate

/**
 * Grands jours fériés (jour fixe et lundi de Pâques) des pays les plus visités : ce jour-là, beaucoup de
 * lieux ferment ou réduisent leurs horaires (règle « PH » d'OpenStreetMap appliquée).
 */
object Holidays {
    private val common = setOf("01-01", "12-25")
    private val byCountry: Map<String, Set<String>> = mapOf(
        "france" to setOf("05-01", "05-08", "07-14", "08-15", "11-01", "11-11"),
        "espagne" to setOf("01-06", "05-01", "08-15", "10-12", "11-01", "12-06", "12-08"),
        "italie" to setOf("01-06", "04-25", "05-01", "06-02", "08-15", "11-01", "12-08", "12-26"),
        "portugal" to setOf("04-25", "05-01", "06-10", "08-15", "10-05", "11-01", "12-01", "12-08"),
        "allemagne" to setOf("05-01", "10-03", "12-26"),
        "autriche" to setOf("01-06", "05-01", "08-15", "10-26", "11-01", "12-08", "12-26"),
        "suisse" to setOf("08-01", "12-26"),
        "belgique" to setOf("05-01", "07-21", "08-15", "11-01", "11-11"),
        "pays bas" to setOf("04-27", "12-26"),
        "royaume uni" to setOf("12-26"),
        "irlande" to setOf("03-17", "12-26"),
        "grece" to setOf("01-06", "03-25", "05-01", "08-15", "10-28", "12-26"),
        "republique tcheque" to setOf("05-01", "05-08", "07-05", "07-06", "09-28", "10-28", "11-17", "12-24", "12-26"),
        "tchequie" to setOf("05-01", "05-08", "07-05", "07-06", "09-28", "10-28", "11-17", "12-24", "12-26"),
        "hongrie" to setOf("03-15", "05-01", "08-20", "10-23", "11-01", "12-26"),
        "pologne" to setOf("01-06", "05-01", "05-03", "08-15", "11-01", "11-11", "12-26"),
        "croatie" to setOf("01-06", "05-01", "05-30", "06-22", "08-05", "08-15", "11-01", "12-26"),
        "maroc" to setOf("01-11", "05-01", "07-30", "08-14", "08-20", "08-21", "11-06", "11-18"),
        "algerie" to setOf("05-01", "07-05", "11-01"),
        "tunisie" to setOf("03-20", "04-09", "05-01", "07-25", "08-13", "12-17"),
        "turquie" to setOf("04-23", "05-01", "05-19", "07-15", "08-30", "10-29"),
        "etats unis" to setOf("07-04", "11-11"),
    )
    /** Pays où le lundi de Pâques est férié. */
    private val easterMonday = setOf("france", "espagne", "italie", "allemagne", "autriche", "suisse", "belgique", "pays bas",
        "royaume uni", "irlande", "republique tcheque", "tchequie", "hongrie", "pologne", "croatie")
    /** Pays où Noël et le 1er janvier ne sont pas fériés de la même façon (on garde seulement le 1er janvier). */
    private val noChristmas = setOf("maroc", "algerie", "tunisie", "turquie")

    fun isHoliday(country: String, d: LocalDate): Boolean {
        val c = country.plain().replace('-', ' ').trim()
        val md = "%02d-%02d".format(d.monthValue, d.dayOfMonth)
        if (md == "01-01" || (md == "12-25" && c !in noChristmas)) return true
        if (md in byCountry[c].orEmpty()) return true
        return c in easterMonday && d == easter(d.year).plusDays(1)
    }

    /** Dimanche de Pâques (calendrier grégorien, méthode de Meeus). */
    private fun easter(y: Int): LocalDate {
        val a = y % 19; val b = y / 100; val c = y % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
        return LocalDate.of(y, (h + l - 7 * m + 114) / 31, (h + l - 7 * m + 114) % 31 + 1)
    }
}
