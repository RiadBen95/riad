package com.riad.voyage.data

import java.time.DayOfWeek

/**
 * Horaires d'ouverture au format OpenStreetMap (« Mo-Fr 10:00-18:00; Sa 10:00-14:00; Su off »).
 * Seuls les cas simples sont lus ; sinon les horaires sont considérés comme inconnus.
 */
object OpeningHours {
    private val days = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    private val time = Regex("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})")

    /** Plage d'ouverture du jour en minutes ; vide = fermé ce jour-là ; null = inconnu. */
    fun forDay(spec: String?, day: DayOfWeek): IntRange? {
        if (spec.isNullOrBlank()) return null
        if (spec.trim() == "24/7") return 0..24 * 60
        var result: IntRange? = null
        for (rule in spec.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            val head = rule.substringBefore(' ')
            val hasDays = head.split(',').all { part -> part.split('-').all { it in days || it == "PH" } }
            val body = if (hasDays) rule.substringAfter(' ', "") else rule
            if (hasDays && !covers(head, day)) continue
            result = when {
                body.contains("off") || body.contains("closed") -> IntRange.EMPTY
                else -> time.findAll(body).map { m ->
                    val (h1, m1, h2, m2) = m.destructured
                    (h1.toInt() * 60 + m1.toInt())..(h2.toInt() * 60 + m2.toInt())
                }.toList().takeIf { it.isNotEmpty() }?.let { ranges -> ranges.minOf { it.first }..ranges.maxOf { it.last } }
                    ?: return null
            }
        }
        return result
    }

    private fun covers(spec: String, day: DayOfWeek): Boolean = spec.split(',').any { part ->
        val bounds = part.split('-')
        val from = days.indexOf(bounds[0])
        val to = days.indexOf(bounds.getOrElse(1) { bounds[0] })
        val d = day.value - 1
        from >= 0 && to >= 0 && (if (from <= to) d in from..to else d >= from || d <= to)
    }
}
