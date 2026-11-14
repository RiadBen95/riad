package com.riad.voyage.data

import com.riad.voyage.t

/** Nettoyage des lieux venus des sources ouvertes : noms propres, lieux fermés au public. */
object PlaceText {
    /** Lieux qui ne se visitent pas (palais présidentiel, ministères, ambassades…). */
    private val closed = listOf(
        "presidence", "presidentiel", "el mouradia", "residence officielle", "ministere", "ambassade", "consulat",
        "caserne", "palais du gouvernement", "assemblee nationale", "conseil de la nation", "prefecture", "commissariat",
    )

    fun visitable(p: PointOfInterest) = p.isVenue || closed.none { it in "${p.name} ${p.kind}".plain() }

    /** « Le Audin » → « L'Audin », « de l 'Armée » → « de l'Armée », espaces doubles retirés. */
    fun cleanName(s: String): String = s.replace(Regex("\\s+'"), "'").replace(Regex("\\s{2,}"), " ").trim()
        .replace(Regex("^(Le|La) (?=[AEIOUYÂÀÉÈÊÎÔÛaeiouy])"), "L'")

    /** Nom propre ; ancien lieu de culte devenu mosquée : on le précise (le type indique l'usage actuel). */
    fun tidy(p: PointOfInterest): PointOfInterest {
        val name = cleanName(p.name)
        val n = name.plain()
        val converted = "mosquee" in p.kind.plain() && listOf("synagogue", "eglise", "cathedrale").any { it in n }
        return p.copy(name = if (converted) "$name (${t("aujourd'hui mosquée")})" else name)
    }

    fun tidy(list: List<PointOfInterest>) = list.filter(::visitable).map(::tidy)
}
