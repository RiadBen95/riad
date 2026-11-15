package com.riad.voyage

import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.DayWeather
import com.riad.voyage.data.Experience
import com.riad.voyage.data.ExperienceBuilder
import com.riad.voyage.data.PointOfInterest
import com.riad.voyage.data.hhmm
import com.riad.voyage.data.minutesOf
import com.riad.voyage.data.Quiz
import com.riad.voyage.data.RatingSource
import com.riad.voyage.data.SavedFlight
import com.riad.voyage.data.SavedPlace
import com.riad.voyage.data.TravelpayoutsApi
import com.riad.voyage.data.Trip
import com.riad.voyage.ui.formatPrice
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.riad.voyage.data.plain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce dont Envolée AI a besoin du programme affiché ; fourni par [AppViewModel]. */
interface EnvoleeHost {
    val experienceFlow: MutableStateFlow<ExperienceState>
    val pools: Map<ActivityLinks.Category, List<PointOfInterest>>
    val weather: Map<LocalDate, DayWeather>
    fun updateAnswers(transform: (Quiz.Answers) -> Quiz.Answers)
    fun generateExperience()
    fun showProgram(exp: Experience, avoid: Set<String> = emptySet())
    fun autosave()
    fun alternativesFor(a: Quiz.Answers, exp: Experience, d: Int, i: Int): List<Pair<ActivityLinks.Category, PointOfInterest>>
    fun linkedTrip(st: ExperienceState): Trip?
    fun freshAnswers(trip: Trip?): Quiz.Answers
    fun planTrip(trip: Trip)
    fun updateTripInfo(id: String, start: LocalDate, end: LocalDate, travellers: Int, arrival: String?, departure: String?)
}

/**
 * Envolée AI (facultative, selon les Réglages) : lit la demande libre, vérifie les lieux et les dates, conseille,
 * modifie le programme en parlant, lit les réservations, commente le budget, écrit le carnet et le souvenir.
 * Elle ne choisit jamais les lieux : ils viennent des sources de l'appli.
 */
class EnvoleeAi(
    private val core: AppCore,
    private val host: EnvoleeHost,
    private val budget: BudgetModel,
    private val trips: TripsModel,
) {
    /** Lieux dits fermés par la vérification : jamais proposés en remplacement (sinon ils reviennent au programme). */
    val knownClosed = mutableSetOf<String>()

    /** Nouvelle envolée : lieux vérifiés, conseillés ou fermés oubliés. */
    fun reset() {
        verifiedIds.clear()
        advisedIds.clear()
        knownClosed.clear()
    }

    /** Après chaque nouveau programme : vérification, conseils, dates, balades (chacun selon les Réglages). */
    fun afterProgram() {
        verifyExperience()
        adviseExperience()
        checkTripDates()
        suggestOutings()
    }

    /** Saisie libre : Claude traduit la demande en réponses du questionnaire, puis le programme est composé. */
    fun describeTrip(text: String) {
        val s = core.settings.value
        val c = s.aiConfig ?: return
        if (!s.claudeDescribe || text.isBlank()) return
        val full = text   // nouvelle demande : jamais mélangée à la précédente
        host.experienceFlow.update { it.copy(describing = true, stage = 0, error = null, aiQuestion = null, lastRequest = full) }
        core.scope.launch {
            val r = attempt(90_000) { com.riad.voyage.data.ClaudeAssistant.describe(c, full, LocalDate.now(), s.has(com.riad.voyage.data.AiFeature.QUESTIONS)) }
            val req = r?.getOrNull()
            // Seul « Programme sur mesure » depuis un voyage (pas encore de programme) part de ce voyage, et pour la même ville.
            val st = host.experienceFlow.value
            val trip = host.linkedTrip(st)?.takeIf { st.result == null && (req?.city.isNullOrBlank() || it.city.equals(req?.city?.trim(), true)) }
            val base = host.freshAnswers(trip)
            if (req != null && req.question.isNotBlank() && (req.city.isBlank() && base.city.isBlank() || req.startDate.isBlank())) {
                host.experienceFlow.update { it.copy(describing = false, aiQuestion = req.question) }
                return@launch
            }
            if (req == null || (req.city.isBlank() && base.city.isBlank())) {
                val why = when {
                    r == null -> t("Envolée AI met trop de temps à répondre : réessayez dans un moment.")
                    else -> r.exceptionOrNull()?.let { TravelpayoutsApi.userMessage(it) } ?: t("Précisez au moins la ville.")
                }
                host.experienceFlow.update { it.copy(describing = false, error = t("Envolée AI n'a pas pu lire la demande.") + " " + why) }
                return@launch
            }
            val (answers, ignored) = com.riad.voyage.data.ClaudeAssistant.toAnswers(req, base)
            updateAnswers { answers }
            host.experienceFlow.update { it.copy(describing = false, aiSummary = summaryOf(answers), aiIgnored = ignored, tripId = trip?.id) }
            host.generateExperience()
        }
    }

    /** Résumé lisible de ce qu'Envolée AI a compris. */
    fun summaryOf(a: Quiz.Answers): String {
        val end = a.startDate.plusDays((a.duration.days - 1).toLong())
        return listOfNotNull(
            t(a.city),
            com.riad.voyage.ui.formatDay(a.startDate) + (if (end != a.startDate) " → " + com.riad.voyage.ui.formatDay(end) else ""),
            a.arrivalMin?.let { tf("arrivée {0}", hhmm(it)!!) },
            t("soirée à l'hôtel").takeIf { a.restAfterArrival && a.arrivalMin != null },
            a.departureMin?.let { tf("départ {0}", hhmm(it)!!) },
            t(a.company.label).takeIf { "company" !in a.assumed },
            a.interests.joinToString(", ") { t(it.label) }.ifBlank { null },
            a.tastes.joinToString(", ") { t(it.label) }.ifBlank { null },
            t(a.diet.label).takeIf { a.diet != Quiz.Diet.NONE },
            t(a.budget.label).takeIf { "budget" !in a.assumed },
        ).joinToString(" · ")
    }

    /** Réponse à la question de précision d'Envolée AI : complète la demande qui l'a posée. */
    fun answerQuestion(answer: String) {
        val st = host.experienceFlow.value
        if (st.aiQuestion == null || answer.isBlank()) return
        host.experienceFlow.update { it.copy(aiQuestion = null) }
        describeTrip(st.lastRequest + "\n" + answer)
    }

    fun dismissQuestion() = host.experienceFlow.update { it.copy(aiQuestion = null) }

    private val verifiedIds = mutableSetOf<String>()
    private var verifying = false

    /**
     * Vérification en 3 étages : Google (fermé, horaires du jour, avis), recherche web Claude pour les
     * lieux inconnus de Google, résumé des avis par Claude. Lieu fermé → remplacé par un lieu proche.
     */
    private fun verifyExperience() {
        val s = core.settings.value
        val exp = host.experienceFlow.value.result ?: return
        val c = s.aiConfig ?: return
        if (!s.placeChecks || verifying) return
        val todo = exp.days.flatMap { d -> d.steps.map { d.date to it.place } }
            .filter { (_, p) -> p.id !in verifiedIds && listOf("transfer/", "custom/", "free/", "pause/").none { p.id.startsWith(it) } }
        if (todo.isEmpty()) return
        verifying = true
        host.experienceFlow.update { it.copy(checking = true) }
        core.scope.launch {
            val checks = mutableMapOf<String, com.riad.voyage.data.PlaceCheck>()
            val unknown = mutableListOf<Pair<LocalDate?, PointOfInterest>>()
            for ((date, p) in todo) {
                if (s.googleKey.isBlank()) { unknown += date to p; continue }
                if (core.quotas.remaining(com.riad.voyage.data.RatingSource.GOOGLE) <= 0) break
                core.quotas.spend(com.riad.voyage.data.RatingSource.GOOGLE)
                val c = attempt(10_000) { com.riad.voyage.data.GoogleRatings.check(s.googleKey, p, exp.city, (date ?: LocalDate.now()).dayOfWeek) }?.getOrNull()
                if (c != null) checks[p.id] = c else unknown += date to p
            }
            if (s.aiWeb) unknown.take(4).forEach { (date, p) ->
                attempt(90_000) { com.riad.voyage.data.ClaudeAssistant.webCheck(c, p.name, exp.city, date) }?.getOrNull()?.let { checks[p.id] = it }
            }
            val names = todo.associate { it.second.id to it.second.name }
            com.riad.voyage.data.Diagnostics.info("Vérification : ${todo.size} lieux, ${checks.size} vérifiés, ${checks.count { it.value.isClosed }} fermés")
            val withReviews = checks.filter { it.value.reviews.isNotEmpty() && !it.value.isClosed }.map { (id, c) -> Triple(id, names[id].orEmpty(), c.reviews) }
            val summaries = if (withReviews.isEmpty()) emptyMap()
                else attempt(90_000) { com.riad.voyage.data.ClaudeAssistant.summarize(c, withReviews) }?.getOrNull().orEmpty()
            verifiedIds += todo.map { it.second.id }
            applyChecks(checks, summaries)
            verifying = false
            host.experienceFlow.update { it.copy(checking = false) }
            verifyExperience()   // lieux de remplacement à vérifier à leur tour
        }
    }

    private fun applyChecks(checks: Map<String, com.riad.voyage.data.PlaceCheck>, summaries: Map<String, String>) {
        val a = host.experienceFlow.value.answers
        var exp = host.experienceFlow.value.result ?: return
        // Lieux fermés (définitivement ou ce jour-là) : remplacés par le premier lieu proposé à la place.
        fun misfits(st: com.riad.voyage.data.ExperienceStep) =
            checks[st.place.id]?.hoursToday?.let { !com.riad.voyage.data.OpeningHours.fits(it, minutesOf(st.time) ?: 0, st.durationMin) } == true
        // Ouvert plus tard ce jour-là (musée qui ouvre à 11 h) : l'étape est décalée plutôt que perdue, si la journée suit.
        exp = exp.copy(days = exp.days.map { day ->
            if (day.steps.none { misfits(it) && checks[it.place.id]?.isClosed != true }) return@map day
            val steps = day.steps.map { st ->
                val spec = com.riad.voyage.data.OpeningHours.specOf(checks[st.place.id]?.hoursToday)
                if (spec != null && misfits(st)) st.copy(place = st.place.copy(openingHours = spec)) else st
            }
            val moved = ExperienceBuilder.retime(a, steps, day.date)
            val endOf = { l: List<com.riad.voyage.data.ExperienceStep> -> l.lastOrNull()?.let { (minutesOf(it.time) ?: 0) + it.durationMin } ?: 0 }
            if (endOf(moved) - endOf(day.steps) <= 45) day.copy(steps = moved) else day
        })
        val closedThen = exp.days.flatMap { it.steps }.filter(::misfits).map { it.place.id }
        val replaced = mutableListOf<String>()
        knownClosed += checks.filter { it.value.isClosed }.keys + closedThen
        (checks.filter { it.value.isClosed }.keys + closedThen).forEach { id ->
            exp.days.forEachIndexed { d, day ->
                val i = day.steps.indexOfFirst { it.place.id == id }
                if (i >= 0) {
                    val c = checks[id]
                    replaced += tf("« {0} » remplacé : {1}", day.steps[i].place.name, when {
                        c?.closedForGood == true -> t("fermé définitivement")
                        c?.isClosed == true -> t("fermé ce jour-là")
                        else -> t("fermé à cette heure-là")
                    })
                    exp = ExperienceBuilder.replaceStep(a, exp, d, i, host.alternativesFor(a, exp, d, i).firstOrNull())
                }
            }
        }
        exp = exp.copy(changes = exp.changes + replaced)
        exp = exp.copy(days = exp.days.map { day ->
            day.copy(steps = day.steps.map { st ->
                val c = checks[st.place.id]?.takeIf { !it.isClosed } ?: return@map st
                val note = listOfNotNull(
                    "✓ " + (c.hoursToday?.let { tf("Ouvert ce jour-là : {0}", it) } ?: t("Ouvert ce jour-là")),
                    summaries[st.place.id]?.let { "« $it »" },
                ).joinToString(" · ")
                val why = if (c.hoursToday != null) st.why.replace(" · " + t("horaires à vérifier"), "") else st.why
                // Horaires précis trouvés pour une tour vue « en passant » : elle se visite, durée normale.
                val visitable = st.durationMin <= 15 && c.hoursToday?.let { Regex("\\d{1,2}:\\d{2}").containsMatchIn(it) && !Regex("24").containsMatchIn(it) } == true
                val kind = com.riad.voyage.tk(st.place.kind.substringBefore(" · "))
                val label = if (visitable) why.replace(tf("{0} à voir en passant", kind), tf("{0} à visiter", kind)) else why
                st.copy(why = listOf(label, note).filter { it.isNotBlank() }.joinToString(" · "), durationMin = if (visitable) 45 else st.durationMin)
            }.let { steps -> if (steps.map { it.durationMin } != day.steps.map { it.durationMin }) ExperienceBuilder.retime(a, steps, day.date) else steps })
        })
        host.experienceFlow.update { it.copy(result = exp) }
        host.autosave()
    }

    // ---------- Envolée AI : modifier en parlant, conseils, dates ----------

    /** Modification demandée en parlant (« demain plus tranquille », « il pleut », « 1 h de retard »). */
    fun askChange(request: String) {
        val s = core.settings.value
        val c = s.aiConfig ?: return
        val st = host.experienceFlow.value
        val exp = st.result ?: return
        if (!s.has(com.riad.voyage.data.AiFeature.EDIT) || request.isBlank()) return
        host.experienceFlow.update { it.copy(editing = true, error = null) }
        core.scope.launch {
            val program = exp.days.mapIndexed { d, day ->
                "${day.title} (${day.date ?: ""}) :\n" + day.steps.mapIndexed { i, x -> "  ${d + 1}/${i + 1} ${x.time} ${t(x.moment)} · ${x.place.name} (${x.place.kind})" }.joinToString("\n")
            }.joinToString("\n")
            val now = java.time.LocalDateTime.now().withSecond(0).withNano(0).toString().replace('T', ' ')
            val plan = attempt(90_000) { com.riad.voyage.data.ClaudeAssistant.edit(c, request, program, summaryOf(st.answers), now) }?.getOrNull()
            if (plan == null) {
                host.experienceFlow.update { it.copy(editing = false, error = t("Envolée AI n'a pas pu appliquer cette demande.")) }
                return@launch
            }
            val a = plan.answers?.let { com.riad.voyage.data.ClaudeAssistant.patch(it, st.answers) } ?: st.answers
            var result = if (a != st.answers && host.pools.isNotEmpty())
                ExperienceBuilder.build(a, a.city.trim(), host.pools, weather = host.weather) else exp
            plan.ops.forEach { op -> result = applyOp(a, result, op) }
            updateAnswers { a }
            host.experienceFlow.update { it.copy(result = result, editing = false, undo = st.answers to exp, changeSummary = plan.summary.ifBlank { t("Programme modifié.") }) }
            host.autosave()
            verifyExperience()
        }
    }

    /** Annule la dernière modification faite en parlant. */
    fun undoChange() {
        val (a, exp) = host.experienceFlow.value.undo ?: return
        updateAnswers { a }
        host.experienceFlow.update { it.copy(result = exp, undo = null, changeSummary = null) }
        host.autosave()
    }

    /** Une opération du plan d'Envolée AI (jours et étapes numérotés à partir de 1). */
    private fun applyOp(a: Quiz.Answers, exp: Experience, op: org.json.JSONObject): Experience {
        val d = op.optInt("day") - 1
        val i = op.optInt("step") - 1
        val day = exp.days.getOrNull(d) ?: return exp
        val step = day.steps.getOrNull(i)
        return when (op.optString("op")) {
            "time" -> minutesOf(op.optString("time"))?.let { ExperienceBuilder.setStepTime(a, exp, d, i, it) } ?: exp
            "shift" -> step?.time?.let(::minutesOf)?.let { ExperienceBuilder.setStepTime(a, exp, d, i, it + op.optInt("minutes")) } ?: exp
            "remove" -> if (step == null || ExperienceBuilder.isAnchored(step)) exp else ExperienceBuilder.replaceStep(a, exp, d, i, null)
            "replace" -> {
                if (step == null || ExperienceBuilder.isAnchored(step) && step.category !in ExperienceBuilder.venues) return exp
                val alts = ExperienceBuilder.alternatives(a, exp, host.pools, d, i)
                val pick = when (op.optString("prefer")) {
                    "indoor" -> alts.firstOrNull { (c, p) -> ExperienceBuilder.isIndoor(p, c) }
                    "outdoor" -> alts.firstOrNull { (c, p) -> ExperienceBuilder.isOutdoor(p, c) }
                    else -> null
                } ?: alts.firstOrNull()
                if (pick == null) exp else ExperienceBuilder.replaceStep(a, exp, d, i, pick)
            }
            "add" -> ExperienceBuilder.addStep(a, exp, d, op.optString("name"))
            else -> exp
        }
    }

    private val advisedIds = mutableSetOf<String>()

    /** Conseils par étape, accessibilité et restaurants qui ne collent pas (remplacés), selon les Réglages. */
    private fun adviseExperience() {
        val s = core.settings.value
        val c = s.aiConfig ?: return
        val st = host.experienceFlow.value
        val exp = st.result ?: return
        val a = st.answers
        val tips = s.has(com.riad.voyage.data.AiFeature.TIPS)
        val food = s.has(com.riad.voyage.data.AiFeature.FOOD)
        val access = s.has(com.riad.voyage.data.AiFeature.ACCESS) && (a.stroller || a.youngKids)
        if (!tips && !food && !access) return
        val todo = exp.days.flatMap { it.steps }.filter { st0 -> st0.place.id !in advisedIds && listOf("transfer/", "pause/", "custom/", "free/").none { st0.place.id.startsWith(it) } }
        if (todo.isEmpty()) return
        advisedIds += todo.map { it.place.id }
        val constraints = listOfNotNull(
            a.avoidNames.takeIf { it.isNotEmpty() }?.let { "à éviter : " + it.joinToString(", ") },
            a.diet.takeIf { it != Quiz.Diet.NONE }?.label,
            a.tastes.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.label }?.let { "envies : $it" },
            "jeunes enfants".takeIf { a.youngKids }, "poussette".takeIf { a.stroller },
        ).joinToString(" ; ")
        core.scope.launch {
            val adv = attempt(90_000) {
                com.riad.voyage.data.ClaudeAssistant.advise(c, exp.city, todo.map { x ->
                    val day = exp.days.first { d -> x in d.steps }
                    x.place.id to "${x.place.name} · ${x.place.kind} · prévu le ${day.date?.let { "${it.dayOfWeek} $it" } ?: day.title} à ${x.time} pour ${x.durationMin} min"
                }, constraints,
                    tips, access, food, web = s.aiWeb && (tips || access))
            }?.getOrNull() ?: return@launch
            var r = host.experienceFlow.value.result?.takeIf { it.city == exp.city } ?: return@launch
            val notes = mutableListOf<String>()
            // Restaurant qui ne colle pas aux envies, ou lieu qui ne se visite pas ce jour-là : remplacé, et dit.
            val rejected = adv.filterValues { it.unsuitable != null || it.notThen != null }
            knownClosed += adv.filterValues { it.notThen != null }.keys
            rejected.forEach { (id, v) ->
                r.days.forEachIndexed { d, day ->
                    val i = day.steps.indexOfFirst { it.place.id == id }
                    val reason = v.notThen ?: v.unsuitable.takeIf { i >= 0 && day.steps[i].category == ActivityLinks.Category.RESTAURANTS }
                    if (i >= 0 && reason != null) {
                        notes += tf("« {0} » remplacé : {1}", day.steps[i].place.name, reason)
                        r = ExperienceBuilder.replaceStep(a, r, d, i, host.alternativesFor(a, r, d, i).firstOrNull { it.second.id !in rejected.keys })
                    }
                }
            }
            // Durée habituelle d'après Envolée AI (« comptez 1 h ») quand elle diffère nettement.
            adv.filterValues { it.minutes > 0 && !it.passage }.forEach { (id, v) ->
                r.days.forEachIndexed { d, day ->
                    val i = day.steps.indexOfFirst { it.place.id == id }
                    if (i >= 0 && kotlin.math.abs(day.steps[i].durationMin - v.minutes) >= 15) r = ExperienceBuilder.setDuration(a, r, d, i, v.minutes)
                }
            }
            // Vu de l'extérieur seulement : la visite devient un passage de 15 minutes.
            adv.filterValues { it.passage }.keys.forEach { id ->
                r.days.forEachIndexed { d, day ->
                    val i = day.steps.indexOfFirst { it.place.id == id }
                    if (i >= 0 && day.steps[i].durationMin > 15) r = ExperienceBuilder.setDuration(a, r, d, i, 15)
                }
            }
            r = r.copy(changes = r.changes + notes, days = r.days.map { day ->
                day.copy(steps = day.steps.map { x ->
                    val v = adv[x.place.id] ?: return@map x
                    x.copy(why = (listOf(x.why) + listOfNotNull(v.tip?.let { "💡 $it" }, v.access?.let { "♿ $it" })).filter { it.isNotBlank() }.joinToString(" · "))
                })
            })
            host.experienceFlow.update { it.copy(result = r) }
            host.autosave()
            verifyExperience()   // restaurants de remplacement
        }
    }

    /** Jours fériés, fermetures, grèves et événements sur les dates du séjour (recherche web). */
    /** Remarques de dates déjà cherchées (ville, début, fin) : un programme recomposé les reprend sans nouvelle recherche. */
    private var dateNotes: Pair<String, List<String>>? = null
    private var datesPending: String? = null

    private fun checkTripDates() {
        val s = core.settings.value
        val c = s.aiConfig ?: return
        if (!s.has(com.riad.voyage.data.AiFeature.DATES) || !s.aiWeb) return
        val a = host.experienceFlow.value.answers
        val exp = host.experienceFlow.value.result ?: return
        val end = a.startDate.plusDays((a.duration.days - 1).toLong())
        val key = "${exp.city}|${a.startDate}|$end"
        fun add(notes: List<String>) {
            if (notes.isEmpty()) return
            host.experienceFlow.update { st -> st.result?.takeIf { it.city == exp.city }?.let { st.copy(result = it.copy(events = (it.events + notes).distinct())) } ?: st }
            host.autosave()
        }
        dateNotes?.takeIf { it.first == key }?.let { return add(it.second) }
        if (datesPending == key) return
        datesPending = key
        val places = exp.days.flatMap { d -> d.steps.filter { it.category !in ExperienceBuilder.venues }.map { it.place.name } }
        core.scope.launch {
            val notes = attempt(90_000) { com.riad.voyage.data.ClaudeAssistant.checkDates(c, exp.city, a.startDate, end, exp.allNotes, places) }?.getOrNull()
            datesPending = null
            if (notes != null) dateNotes = key to notes
            add(notes.orEmpty())
        }
    }

    // ---------- Envolée AI : billets, budget, carnet, souvenir ----------

    private val _bookingImport = MutableStateFlow<BookingImport?>(null)
    val bookingImport: StateFlow<BookingImport?> = _bookingImport.asStateFlow()

    /** Billet ou confirmation partagé avec l'appli (fichier ou texte) : lu, puis à confirmer. */
    fun importBooking(uri: android.net.Uri?, mime: String?, text: String?) = importDocuments(listOfNotNull(uri), mime, text)

    /** Documents importés (plusieurs possibles : aller, retour, hôtel) : lus ensemble, puis à confirmer. */
    fun importDocuments(uris: List<android.net.Uri>, mime: String? = null, text: String? = null, plan: Boolean = false) {
        val s = core.settings.value
        val c = s.aiConfig
        if (c == null || !s.has(com.riad.voyage.data.AiFeature.IMPORT)) {
            core.message.value = t("Activez Envolée AI et « Lire les réservations » dans les Réglages pour importer un billet.")
            return
        }
        if (uris.isEmpty() && text.isNullOrBlank()) return
        _bookingImport.value = BookingImport(loading = true, plan = plan)
        core.scope.launch {
            val r = attempt(120_000) {
                val read = withContext(Dispatchers.IO) { uris.map { readAttachment(it, mime.takeIf { uris.size == 1 }) } }
                val texts = (listOfNotNull(text) + read.mapNotNull { it.second }).joinToString("\n\n").ifBlank { null }
                com.riad.voyage.data.ClaudeAssistant.readBooking(c, texts, read.mapNotNull { it.first })
            }
            _bookingImport.value = r?.getOrNull()?.let { BookingImport(it, plan = plan) }
                ?: BookingImport(error = t("Envolée AI n'a pas pu lire ce document.") + (r?.exceptionOrNull()?.let { " " + TravelpayoutsApi.userMessage(it) } ?: ""))
        }
    }

    fun cancelBookingImport() { _bookingImport.value = null }

    /** PDF tel quel ; image redimensionnée en JPEG ; texte lu comme texte. */
    private fun readAttachment(uri: android.net.Uri, mime: String?): Pair<com.riad.voyage.data.ClaudeAssistant.Attachment?, String?> {
        val resolver = core.app.contentResolver
        val type = mime ?: resolver.getType(uri).orEmpty()
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null to null
        return when {
            type == "application/pdf" -> com.riad.voyage.data.ClaudeAssistant.Attachment(bytes, type) to null
            type.startsWith("image/") -> {
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null to null
                val k = minOf(1.0, 2000.0 / maxOf(bmp.width, bmp.height))
                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
                val out = java.io.ByteArrayOutputStream().also { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                com.riad.voyage.data.ClaudeAssistant.Attachment(out.toByteArray(), "image/jpeg") to null
            }
            else -> null to String(bytes).take(20_000)
        }
    }

    /** Billet confirmé : le voyage de la ville d'arrivée se remplit (dates, heures, voyageurs, vol). */
    fun applyBooking() {
        val imp = _bookingImport.value ?: return
        val b = imp.booking ?: return
        val city = b.destination.ifBlank { return }
        val out = b.outbound
        val back = b.inbound
        val h = b.hotel
        fun iso(d: LocalDate?, m: Int?) = d?.let { "%sT%02d:%02d:00+00:00".format(it, (m ?: 0) / 60, (m ?: 0) % 60) }
        val base = core.trips.value.firstOrNull { it.city.plain() == city.plain() } ?: Trip(id = "trip-${System.currentTimeMillis()}", city = city)
        val flight = b.segments.firstOrNull()?.let { first ->
            SavedFlight(first.fromCity, city, iso(first.depDate, first.depTime).orEmpty(), back?.let { iso(it.depDate, it.depTime) },
                b.price?.toInt() ?: 0, b.currency, b.segments.map { it.number }.filter { it.isNotBlank() }.joinToString(" · "),
                "booking:" + b.reference.ifBlank { first.number })
        }
        // Hôtel réservé ailleurs : gardé dans le voyage avec sa référence et son prix (compté dans le budget).
        val hotel = h?.let {
            val label = listOfNotNull("Hôtel réservé", it.reference.takeIf { r -> r.isNotBlank() }?.let { r -> "Réf. $r" },
                it.price?.let { p -> com.riad.voyage.ui.formatPrice(p.toInt(), it.currency) }).joinToString(" · ")
            SavedPlace(it.name, label, 0.0, 0.0, false)
        }
        val trip = base.copy(
            startDate = (out?.arrDate ?: h?.checkIn)?.toString() ?: base.startDate,
            endDate = (back?.depDate ?: h?.checkOut)?.toString() ?: base.endDate,
            arrivalTime = hhmm(out?.arrTime) ?: base.arrivalTime, departureTime = hhmm(back?.depTime) ?: base.departureTime,
            travellers = if (b.segments.isNotEmpty()) b.passengers else base.travellers,
            flights = if (flight == null || base.flights.any { it.url == flight.url }) base.flights else base.flights + flight,
            places = if (hotel == null || base.places.any { it.name == hotel.name }) base.places else base.places + hotel,
        )
        core.saveTrip(trip, tf("Réservation ajoutée à votre voyage : {0}", t(trip.city)))
        _bookingImport.value = null
        val start = trip.start
        val end = trip.end
        if (start != null && end != null && host.experienceFlow.value.tripId == trip.id)
            host.updateTripInfo(trip.id, start, end, trip.travellers ?: 2, trip.arrivalTime, trip.departureTime)
        // Depuis l'Envolée : on compose aussitôt le programme de ce voyage ; sinon on ouvre le voyage.
        if (imp.plan) host.planTrip(trip) else trips.openTrip(trip.id)
    }

    /** Commentaire du budget par Envolée AI. */
    fun budgetAdvice() {
        val s = core.settings.value
        val c = s.aiConfig ?: return
        val b = budget.state.value ?: return
        if (!s.has(com.riad.voyage.data.AiFeature.BUDGET)) return
        budget.edit { it.copy(advising = true) }
        val summary = b.lines.joinToString("\n") { "${t(it.item.label)} : ${it.amountEur.toInt()} €${if (it.missing) " (estimation)" else ""}" } +
            "\nTotal : ${b.totalEur.toInt()} € pour ${b.input.travellers} voyageur(s), ${b.input.nights} nuit(s) à ${b.input.city}" +
            (b.targetEur?.let { "\nBudget annoncé : ${it.toInt()} €" } ?: "")
        core.scope.launch {
            val text = attempt(90_000) { com.riad.voyage.data.ClaudeAssistant.budgetAdvice(c, summary) }?.getOrNull()
            budget.edit { it.copy(advising = false, advice = text ?: t("Envolée AI n'a pas pu commenter le budget.")) }
        }
    }

    /** Souvenir du voyage à partager (null si Envolée AI ne peut pas l'écrire). */
    suspend fun souvenir(trip: Trip): String? {
        val s = core.settings.value
        val c = s.aiConfig?.takeIf { s.has(com.riad.voyage.data.AiFeature.SOUVENIR) } ?: return null
        val spent = trip.expenses.joinToString("\n") { "${it.label} : ${it.amount} ${it.currency}" }
        return attempt(90_000) { com.riad.voyage.data.ClaudeAssistant.souvenir(c, com.riad.voyage.data.TripText.share(trip) { t(it) } + "\nDépenses :\n" + spent) }?.getOrNull()
    }

    val souvenirEnabled get() = core.settings.value.has(com.riad.voyage.data.AiFeature.SOUVENIR)

    /** Textes du carnet (introduction, jours, phrases sur mesure), écrits une fois puis gardés dans le voyage. */
    suspend fun tripStory(trip: Trip): Trip {
        val s = core.settings.value
        val c = s.aiConfig?.takeIf { s.has(com.riad.voyage.data.AiFeature.STORY) } ?: return trip
        if (trip.storyIntro != null || trip.program.isEmpty()) return trip
        val a = host.experienceFlow.value.answers.takeIf { host.experienceFlow.value.tripId == trip.id }
        val needs = listOfNotNull(a?.diet?.takeIf { it != Quiz.Diet.NONE }?.label, "jeunes enfants".takeIf { a?.youngKids == true },
            "poussette".takeIf { a?.stroller == true }).joinToString(", ")
        val local = core.practicalFor(trip.city, trip.country)?.phrases?.let { "la langue locale de ${trip.city}" } ?: "la langue locale"
        val who = when {
            a != null && "company" !in a.assumed -> t(a.company.label) + (a.people?.let { " ($it)" } ?: "")
            trip.travellers != null -> tf("{0} voyageur(s)", trip.travellers)
            else -> null
        }
        val story = attempt(90_000) { com.riad.voyage.data.ClaudeAssistant.story(c, com.riad.voyage.data.TripText.share(trip) { t(it) }, trip.program.size, local, needs, who) }?.getOrNull() ?: return trip
        val updated = trip.copy(storyIntro = story.intro, storyDays = story.days, extraPhrases = story.phrases)
        core.tripStore.upsert(updated)
        core.refreshTrips()
        return updated
    }

    /** Journées sans nouveau lieu : Envolée AI propose balades de quartier et excursions, puis le programme est recomposé. */
    private fun suggestOutings() {
        val s = core.settings.value
        val c = s.aiConfig ?: return
        val st = host.experienceFlow.value
        val exp = st.result ?: return
        val free = exp.days.count { d -> d.steps.any { it.place.id.startsWith("free/") } }
        val wine = Quiz.Taste.WINE in st.answers.tastes && st.answers.duration.days >= 3   // une journée dans le vignoble
        if (!s.has(com.riad.voyage.data.AiFeature.OUTINGS) || (free == 0 && !wine) || st.answers.outings.isNotEmpty()) return
        core.scope.launch {
            val seen = exp.days.flatMap { d -> d.steps.filter { it.category !in ExperienceBuilder.venues }.map { it.place.name } }
            val ideas = attempt(90_000) { com.riad.voyage.data.ClaudeAssistant.outings(c, exp.city, free + 2, seen, wine) }?.getOrNull().orEmpty()
            if (ideas.isEmpty() || host.experienceFlow.value.result?.city != exp.city) return@launch
            updateAnswers { it.copy(outings = ideas) }
            val a = host.experienceFlow.value.answers
            if (host.pools.isNotEmpty()) host.showProgram(ExperienceBuilder.build(a, a.city.trim(), host.pools, weather = host.weather))
        }
    }
}
