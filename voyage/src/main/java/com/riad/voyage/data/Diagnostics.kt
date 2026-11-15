package com.riad.voyage.data

import android.content.Context
import android.os.Build
import com.riad.voyage.ExperienceState
import java.time.LocalDateTime

/**
 * Rapport de diagnostic partagé depuis les Réglages : réponses, programme (avec les horaires lus pour chaque
 * lieu), réponse d'Envolée AI, voyage relié et dernières erreurs. Aucune clé n'y figure, seulement « oui / non ».
 */
object Diagnostics {
    private val errors = ArrayDeque<String>()
    @Volatile var lastAiResponse: String? = null
    @Volatile var requestResponse: String? = null   // lecture de la demande libre (gardée à part des autres appels)

    @Synchronized
    fun log(message: String) {
        errors.addLast("${LocalDateTime.now().withNano(0)} · $message")
        while (errors.size > 30) errors.removeFirst()
    }

    private val events = ArrayDeque<String>()

    /** Événement normal (vérification, remplacement), affiché à part des erreurs. */
    @Synchronized
    fun info(message: String) {
        events.addLast("${LocalDateTime.now().withNano(0)} · $message")
        while (events.size > 30) events.removeFirst()
    }

    @Synchronized
    private fun recentErrors() = errors.toList()

    @Synchronized
    private fun recentEvents() = events.toList()

    fun report(app: Context, settings: Settings, state: ExperienceState, trip: Trip?, pools: Map<ActivityLinks.Category, Int>): String =
        buildString {
            fun line(s: String = "") = appendLine(s)
            fun yes(b: Boolean) = if (b) "oui" else "non"
            val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
            line("RAPPORT DE DIAGNOSTIC ENVOLÉE — ${LocalDateTime.now().withNano(0)}")
            line("Appli ${version ?: "?"} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            line("Langue : ${com.riad.voyage.I18n.lang.code} · devise : ${settings.currency}")
            line()
            line("== Réglages (aucune clé, seulement leur présence) ==")
            line("Travelpayouts : ${yes(settings.token.isNotBlank())} · Google Places : ${yes(settings.googleKey.isNotBlank())} · " +
                "Tripadvisor : ${yes(settings.tripadvisorKey.isNotBlank())} · Foursquare : ${yes(settings.foursquareKey.isNotBlank())} · " +
                "LiteAPI : ${yes(settings.liteApiKey.isNotBlank())}")
            line("Envolée AI : moteur ${if (settings.aiModel.gemini) "Gemini" else "Claude"} · clé ${yes(settings.hasAiKey)} · activée ${yes(settings.claudeOn)} · " +
                "saisie libre ${yes(settings.claudeDescribe)} · recherches web ${yes(settings.aiWeb)} · vérification des lieux ${yes(settings.placeChecks)} · " +
                "modèle ${settings.aiModelId}")
            line()
            val a = state.answers
            line("== Réponses du questionnaire ==")
            line("Ville : ${a.city} (pays : ${a.country.ifBlank { "?" }}) · début ${a.startDate} · ${a.duration.days} jour(s)${if (a.duration.halfDay) " (demi-journée)" else ""}")
            line("Arrivée : ${hhmm(a.arrivalMin) ?: "-"}${if (a.restAfterArrival) " (soirée à l'hôtel)" else ""} · départ : ${hhmm(a.departureMin) ?: "-"}")
            line("Compagnie : ${a.company} · envies : ${a.interests.joinToString()} · goûts : ${a.tastes.joinToString().ifBlank { "-" }}")
            line("Style : ${a.style} · rythme : ${a.pace} · visite : ${a.depth} · début : ${a.start} · déplacements : ${a.mobility} · régime : ${a.diet} · budget : ${a.budget}")
            if (a.wanted.isNotEmpty() || a.avoidNames.isNotEmpty()) line("Demandés : ${a.wanted.joinToString()} · à éviter : ${a.avoidNames.joinToString()}")
            line()
            line("== Envolée AI ==")
            line("Compris : ${state.aiSummary ?: "-"}")
            line("Non compris : ${state.aiIgnored.joinToString().ifBlank { "-" }}")
            line("Supposé (non précisé) : ${a.assumed.joinToString().ifBlank { "-" }}")
            line("Lecture de la demande (brute) : ${requestResponse?.take(3000) ?: "-"}")
            line("Dernière réponse brute : ${lastAiResponse?.take(1500) ?: "-"}")
            line()
            line("== Lieux trouvés par catégorie ==")
            line(pools.entries.joinToString(" · ") { "${it.key.name} ${it.value}" }.ifBlank { "-" })
            line()
            val exp = state.result
            line("== Programme ==")
            if (exp == null) line(state.error?.let { "Pas de programme. Erreur affichée : $it" } ?: "Pas de programme.")
            else {
                line("${exp.title} — ${exp.subtitle}")
                exp.days.forEach { d ->
                    line("${d.title} · ${d.date ?: "?"} (${d.date?.dayOfWeek ?: "?"})${d.weather?.let { " · ${it.label}" } ?: ""}")
                    d.steps.forEach { s ->
                        val hours = d.date?.let { OpeningHours.ranges(s.place.openingHours, it.dayOfWeek, it.monthValue) }
                        val read = when {
                            s.place.openingHours == null -> "pas d'horaires de la semaine (seulement la vérification du jour, ci-dessous)"
                            hours == null -> "horaires illisibles"
                            hours.isEmpty() -> "FERMÉ ce jour-là"
                            else -> "ouvert " + hours.joinToString(", ") { "${hhmm(it.first)}-${hhmm(it.last)}" }
                        }
                        line("  ${s.time} [${s.moment}] ${s.place.name} · ${s.place.kind} · ${s.durationMin} min · $read")
                        s.place.openingHours?.let { line("      horaires de la semaine : $it") }
                        if (s.why.isNotBlank()) line("      ${s.why}")
                    }
                }
                if (exp.notes.isNotEmpty()) line("À savoir : " + exp.notes.joinToString(" | "))
            }
            line()
            line("== Voyage relié ==")
            if (trip == null) line("Aucun.")
            else {
                line("${trip.city} (${trip.country}) · ${trip.startDate} → ${trip.endDate} · ${trip.travellers ?: "?"} voyageur(s)")
                line("Arrivée ${trip.arrivalTime ?: "-"}${if (trip.restAfterArrival) " (soirée à l'hôtel)" else ""} · départ ${trip.departureTime ?: "-"}")
                line("Vols : ${trip.flights.size} · lieux gardés : ${trip.places.size} · jours au programme : ${trip.program.size} · dépenses : ${trip.expenses.size}")
            }
            line()
            line("== Journal (vérifications) ==")
            recentEvents().ifEmpty { listOf("Rien.") }.forEach { line(it) }
            line()
            line("== Dernières erreurs ==")
            recentErrors().ifEmpty { listOf("Aucune.") }.forEach { line(it) }
        }
}
