package com.riad.voyage.data

import java.time.DayOfWeek

/**
 * Horaires d'ouverture au format OpenStreetMap (« Mo-Fr 10:00-12:00,14:00-18:00; Sa 10:00-14:00; Jan-Mar Su off »).
 * Les cas courants sont lus (plages multiples, jours, mois, 24/7) ; sinon les horaires sont considérés comme inconnus.
 * Les heures après minuit sont comptées au-delà de 24 h (« 18:00-02:00 » → 18 h à 26 h).
 */
object OpeningHours {
    private val days = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val time = Regex("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})")

    private fun cap(s: String) = s.lowercase().replaceFirstChar { it.uppercase() }
    private fun isDays(token: String) = token.split(',').all { part -> part.split('-').all { cap(it) in days || it.uppercase() == "PH" } }
    private fun isMonths(token: String) = token.split(',').all { part -> part.split('-').all { cap(it) in months } }

    /**
     * Plages d'ouverture du jour en minutes ; liste vide = fermé ; null = inconnu.
     * Un jour qu'aucune règle ne cite est fermé (règle d'OpenStreetMap), sauf s'il n'y a aucune règle par jour.
     */
    fun ranges(spec: String?, day: DayOfWeek, month: Int? = null): List<IntRange>? {
        if (spec.isNullOrBlank()) return null
        var result: List<IntRange>? = null
        var hasDayRules = false
        for (raw in spec.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            var rule = raw
            val first = rule.substringBefore(' ')
            if (isMonths(first)) {                                  // « Jan-Mar … » : seulement ces mois-là
                if (month != null && !coversMonth(first, month)) continue
                rule = rule.substringAfter(' ', "").trim()
            }
            val head = rule.substringBefore(' ')
            val withDays = isDays(head)
            val body = if (withDays) rule.substringAfter(' ', "") else rule
            if (withDays) {
                hasDayRules = true
                if (!covers(head, day)) continue
            }
            result = when {
                body.contains("24/7") -> listOf(0..24 * 60)
                Regex("\\b(off|closed)\\b").containsMatchIn(body.lowercase()) -> emptyList()
                else -> {
                    val found = time.findAll(body).map { m -> m.destructured.toList().map { it.toInt() } }.toList()
                    // Heures impossibles (« 25:00 ») ou aucune heure lisible : horaires inconnus.
                    if (found.isEmpty() || found.any { (h1, m1, h2, m2) -> h1 > 24 || h2 > 24 || m1 > 59 || m2 > 59 }) return null
                    found.map { (h1, m1, h2, m2) ->
                        val from = h1 * 60 + m1
                        val to = h2 * 60 + m2
                        from..(if (to <= from) to + 24 * 60 else to)
                    }
                }
            }
        }
        return result ?: if (hasDayRules) emptyList() else null
    }

    /** Plage du jour, de la première ouverture à la dernière fermeture ; vide = fermé ; null = inconnu. */
    fun forDay(spec: String?, day: DayOfWeek, month: Int? = null): IntRange? =
        ranges(spec, day, month)?.let { r -> if (r.isEmpty()) IntRange.EMPTY else r.minOf { it.first }..r.maxOf { it.last } }

    /**
     * Première heure ≥ [at] où l'on peut rester [minutes] sans fermeture (pause de midi comprise).
     * [at] si les horaires sont inconnus ; null si le lieu est fermé ou ferme trop tôt.
     */
    fun nextStart(spec: String?, day: DayOfWeek, at: Int, minutes: Int, month: Int? = null): Int? {
        val r = ranges(spec, day, month) ?: return at
        return r.mapNotNull { range -> maxOf(at, range.first).takeIf { it + minutes <= range.last } }.minOrNull()
    }

    /**
     * Horaires du jour en texte (Google : « 13:00 – 22:30 », « 12:00 – 3:00 PM, 7:00 – 11:30 PM », espaces fines
     * comprises). Vrai si une plage contient toute l'étape ; vrai aussi si le texte ne donne pas d'heures lisibles.
     * [start] peut dépasser 24 h (étape après minuit).
     */
    fun fits(text: String?, start: Int, minutes: Int): Boolean {
        val s = text.orEmpty().replace(Regex("[\\u00A0\\u2009\\u202F]"), " ")
        if (Regex("24\\s*h|24 hours|24/24|24/7", RegexOption.IGNORE_CASE).containsMatchIn(s)) return true
        val clock = "(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?"
        val ranges = Regex("$clock\\s*[–-]\\s*$clock").findAll(s).map { m ->
            val g = m.groupValues
            val endHalf = g[6].uppercase()
            val startHalf = g[3].uppercase().ifEmpty { endHalf }                  // « 12:00 – 3:00 PM » : même moitié
            fun min(h: String, mm: String, half: String): Int {
                val hour = when (half) { "AM" -> h.toInt() % 12; "PM" -> h.toInt() % 12 + 12; else -> h.toInt() }
                return hour * 60 + mm.toInt()
            }
            var from = min(g[1], g[2], startHalf)
            if (startHalf == "PM" && g[3].isEmpty() && from > min(g[4], g[5], endHalf)) from -= 12 * 60   // « 11:00 – 2:00 PM »
            val to = min(g[4], g[5], endHalf)
            from to (if (to <= from) to + 24 * 60 else to)
        }.toList()
        return ranges.isEmpty() || ranges.any { (from, to) ->
            listOf(start, start + 24 * 60, start - 24 * 60).any { st -> st >= from && st + minutes <= to }
        }
    }

    private fun covers(spec: String, day: DayOfWeek): Boolean = spec.split(',').any { part ->
        val bounds = part.split('-').map(::cap)
        val from = days.indexOf(bounds[0])
        val to = days.indexOf(bounds.getOrElse(1) { bounds[0] })
        val d = day.value - 1
        from >= 0 && to >= 0 && (if (from <= to) d in from..to else d >= from || d <= to)
    }

    private fun coversMonth(spec: String, month: Int): Boolean = spec.split(',').any { part ->
        val bounds = part.split('-').map(::cap)
        val from = months.indexOf(bounds[0])
        val to = months.indexOf(bounds.getOrElse(1) { bounds[0] })
        val m = month - 1
        from >= 0 && to >= 0 && (if (from <= to) m in from..to else m >= from || m <= to)
    }
}
