package com.riad.voyage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.Blog
import com.riad.voyage.data.BlogCity
import com.riad.voyage.data.BudgetCalculator
import com.riad.voyage.data.BudgetItem
import com.riad.voyage.data.DayWeather
import com.riad.voyage.data.Experience
import com.riad.voyage.data.ExperienceBuilder
import com.riad.voyage.data.Flight
import com.riad.voyage.data.Place
import com.riad.voyage.data.PlannedActivity
import com.riad.voyage.data.PointOfInterest
import com.riad.voyage.data.hhmm
import com.riad.voyage.data.minutesOf
import com.riad.voyage.data.PopularPlaces
import com.riad.voyage.data.Quiz
import com.riad.voyage.data.RatingSource
import com.riad.voyage.data.SavedDay
import com.riad.voyage.data.SavedPlace
import com.riad.voyage.data.SavedStep
import com.riad.voyage.data.Settings
import com.riad.voyage.data.TravelpayoutsApi
import com.riad.voyage.data.Trip
import com.riad.voyage.data.WeatherApi
import com.riad.voyage.data.parseDateTime
import com.riad.voyage.ui.formatPrice
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Chef d'orchestre de l'appli : navigation, page Ville, Envolée (programme et Envolée AI), budget et voyages.
 * Les parties autonomes ont leur fichier : [FlightsModel], [HotelsModel], [BlogModel] ; ce qu'elles partagent est dans [AppCore].
 */
class AppViewModel(app: Application) : AndroidViewModel(app), EnvoleeHost {
    val core = AppCore(app, viewModelScope)
    val flights = FlightsModel(core)
    val hotels = HotelsModel(core)
    val budget = BudgetModel(core, answers = { _experience.value.answers }, choose = ::chooseFor)
    val places = PlacesModel(core)
    val trips = TripsModel(core, familyProgram = { id -> _experience.value.let { it.tripId == id && it.answers.company == Quiz.Company.FAMILY } },
        blogCity = { blog.state.value.city?.name })
    val ai = EnvoleeAi(core, this, budget, trips)
    val blog = BlogModel(core, answers = { _experience.value.answers }, activityCategory = { places.category.value })

    val settings: StateFlow<Settings> = core.settings.asStateFlow()
    val tab: StateFlow<Tab> = core.tab.asStateFlow()
    val message: StateFlow<String?> = core.message.asStateFlow()
    val quotas get() = core.quotas

    init { core.onTripsChanged = { budget.refresh() } }

    private val prefStore = com.riad.voyage.data.PreferenceStore(app)
    /** Page vierge, ou préférences habituelles si la puce « Reprendre mes préférences » est allumée. */
    private fun blankAnswers() = Quiz.Answers().let { if (prefStore.use) prefStore.applyTo(it) ?: it else it }
    private val _experience = MutableStateFlow(ExperienceState(answers = blankAnswers()))
    val experience: StateFlow<ExperienceState> = _experience.asStateFlow()
    private var experiencePools: Map<ActivityLinks.Category, List<PointOfInterest>> = emptyMap()
    private var experienceWeather: Map<LocalDate, DayWeather> = emptyMap()
    override val experienceFlow get() = _experience
    override val pools get() = experiencePools
    override val weather get() = experienceWeather

    /** Vrai quand la personne est partie choisir un vol / hôtel / activité depuis le budget. */

    private val _keyStatus = MutableStateFlow<KeyStatus>(KeyStatus.Unknown)
    val keyStatus: StateFlow<KeyStatus> = _keyStatus.asStateFlow()

    private val _citySection = MutableStateFlow(CitySection.PLACES)
    val citySection: StateFlow<CitySection> = _citySection.asStateFlow()

    fun setCitySection(s: CitySection) { _citySection.value = s; selectTab(Tab.CITY) }

    /** Ville choisie en haut de la page Ville : lieux, articles et infos pratiques la suivent. */
    fun chooseCity(p: Place) {
        places.show(p.name, p)
        selectTab(Tab.CITY)
    }

    /** Ville affichée dans la page Ville (lieux, articles, infos) : recherche en cours, sinon prochain voyage. */
    fun currentCity(): Pair<String, String>? {
        val c = places.city.value.ifBlank { blog.state.value.city?.name.orEmpty() }
            .ifBlank { _experience.value.result?.city.orEmpty() }.ifBlank { core.trips.value.firstOrNull()?.city.orEmpty() }
        if (c.isBlank()) return null
        val country = places.place.value?.takeIf { it.name.equals(c, true) }?.country
            ?: blog.state.value.city?.takeIf { it.name.equals(c, true) }?.country
            ?: core.trips.value.firstOrNull { it.city.equals(c, true) }?.country.orEmpty()
        return c to core.countryOf(c, country)
    }

    fun selectTab(t0: Tab) {
        // Sorties et Blogue sont des sections de la page Ville.
        val t = when (t0) {
            Tab.ACTIVITIES -> { _citySection.value = CitySection.PLACES; Tab.CITY }
            Tab.BLOG -> { _citySection.value = CitySection.ARTICLES; Tab.CITY }
            else -> t0
        }
        val section = _citySection.value
        // Les sorties reprennent la destination du vol si rien n'est saisi.
        if (t == Tab.CITY && places.city.value.isBlank()) {
            places.show(flights.form.value.destination?.name.orEmpty(), flights.form.value.destination)
        }
        if (t == Tab.EXPERIENCE && _experience.value.answers.city.isBlank()) {
            val dest = flights.form.value.destination ?: places.place.value
            if (dest != null) updateAnswers { it.copy(city = dest.name, place = dest) }
        }
        if (t == Tab.HOTELS && hotels.state.value.city.isBlank()) {
            val dest = flights.form.value.destination ?: places.place.value
            if (dest != null) hotels.updateHotels { it.copy(city = dest.name, place = dest) }
        }
        if (t == Tab.CITY && places.city.value.isBlank()) {
            core.trips.value.firstOrNull()?.let { places.show(it.city) }
        }
        core.tab.value = t
        if (t == Tab.CITY && section == CitySection.PLACES) places.search()
        // Les articles suivent la ville choisie en haut de la page Ville.
        if (t == Tab.CITY && section == CitySection.ARTICLES && !blog.state.value.city?.name.equals(places.city.value, ignoreCase = true)) {
            val ctx = blogContext()
            (ctx.firstOrNull { it.first.name.equals(places.city.value, true) } ?: ctx.firstOrNull())?.let { (c, why) -> blog.openBlogCity(c, why) }
        }
    }

    /** Efface l'historique des recherches de vols. */
    fun clearRecents() {
        flights.reset()
        prefStore.clear()   // préférences mémorisées des envolées passées
        _usePrefs.value = false
        _prefsSummary.value = null
        // Toutes les recherches en cours repartent de zéro (Vols, Ville, Hôtels, Blogue, Envolée) ; les voyages enregistrés restent.
        places.reset()
        core.weatherCache.clear()
        hotels.reset()
        blog.reset()
        ai.reset()
        _experience.value = ExperienceState(answers = Quiz.Answers())
        core.message.value = t("Historique et préférences effacés.")
    }

    // ---------- Nouvelle envolée et préférences ----------

    /** Nouvelle envolée : tout repart de zéro (le programme précédent reste dans « Voyages »). */
    fun newEnvolee() {
        ai.reset()
        _experience.value = ExperienceState(answers = blankAnswers())
        core.tab.value = Tab.EXPERIENCE
    }

    // Suivis par l'écran : l'interrupteur et le résumé se mettent à jour aussitôt.
    private val _usePrefs = MutableStateFlow(prefStore.use)
    val usePreferences: StateFlow<Boolean> = _usePrefs.asStateFlow()
    private val _prefsSummary = MutableStateFlow(computePrefsSummary())
    val preferences: StateFlow<String?> = _prefsSummary.asStateFlow()

    /** Résumé des préférences habituelles (null s'il n'y a encore aucune envolée). */
    fun preferenceSummary(): String? = _prefsSummary.value

    private fun computePrefsSummary(): String? = prefStore.applyTo(Quiz.Answers())?.let { p ->
        listOfNotNull(t(p.diet.label).takeIf { p.diet != Quiz.Diet.NONE }, t(p.budget.label), t(p.mobility.label),
            tf("rythme {0}", t(p.pace.label).lowercase()), t(p.style.label)).joinToString(" · ") { it.lowercase() }
    }

    fun setUsePreferences(on: Boolean) {
        prefStore.use = on
        _usePrefs.value = on
        val d = Quiz.Answers()
        updateAnswers { a ->
            if (on) prefStore.applyTo(a) ?: a
            else a.copy(diet = d.diet, budget = d.budget, mobility = d.mobility, pace = d.pace, style = d.style, start = d.start)
        }
    }

    // ---------- Ajuster mes réponses ----------

    fun editExperience() = _experience.update { it.copy(recap = true, editStep = null) }
    fun closeRecap() = _experience.update { it.copy(recap = false, editStep = null) }
    fun editRecapStep(id: String) = _experience.update { it.copy(editStep = id) }
    fun doneRecapStep() = _experience.update { it.copy(editStep = null) }

    /** Recompose avec les réponses ajustées (sans nouvelle recherche si les goûts et le régime n'ont pas changé). */
    fun applyRecap() {
        _experience.update { it.copy(recap = false, editStep = null) }
        if (_experience.value.result == null) generateExperience() else refineExperience { it }
    }

    // ---------- Budget ----------

    /** Budget du programme Expérience affiché (+ vols et hôtel choisis pour la même ville). */
    fun openBudgetForExperience() = budget.open(linkedTrip(_experience.value)?.id) { rates ->
        _experience.value.answers.budgetEur?.let { stated -> budget.edit { it.copy(targetEur = it.targetEur ?: stated.toDouble()) } }
        val st = _experience.value
        val a = st.answers
        val city = st.result?.city ?: a.city
        val program = st.result?.days.orEmpty().flatMap { d -> d.steps.map { PlannedActivity(it.place.name, it.place.kind, it.category) } }
        budget.budgetFor(city, core.countryOf(city, a.place?.country.orEmpty()), linkedTrip(st) ?: core.trips.value.firstOrNull { it.city.equals(city, true) }, rates,
            if (a.duration.halfDay) 0 else (a.duration.days - 1).coerceAtLeast(0), a.people ?: 1, program)
    }

    /** Depuis le budget : l'écran où choisir ce qui manque, pour la même ville. */
    private fun chooseFor(item: BudgetItem, city: String, travellers: Int) {
        when (item) {
            BudgetItem.HOTEL -> {
                val trip = core.trips.value.firstOrNull { it.city.equals(city, true) }
                hotels.openHotels(city, null, trip?.start, trip?.end, travellers)
            }
            BudgetItem.ACTIVITIES -> {
                places.show(city, PopularPlaces.all.firstOrNull { it.name.equals(city, true) })
                selectTab(Tab.ACTIVITIES)
            }
            else -> selectTab(Tab.FLIGHTS)
        }
    }

    // ---------- Voyage : dépenses, programme, bagages ----------

    /** Rapport de diagnostic (texte, sans aucune clé) : ce que l'appli a reçu, compris et composé. */
    fun diagnosticReport(): java.io.File {
        val st = _experience.value
        val text = com.riad.voyage.data.Diagnostics.report(
            app = getApplication(), settings = core.settings.value, state = st, trip = linkedTrip(st),
            pools = experiencePools.mapValues { it.value.size },
        )
        val dir = java.io.File(getApplication<Application>().cacheDir, "pdf").apply { mkdirs() }
        return java.io.File(dir, "Envolee-diagnostic.txt").apply { writeText(text) }
    }

    /** Carnet de voyage en PDF (charte Envolée), prêt à partager. */
    suspend fun tripPdf(source: Trip): java.io.File {
        val trip = ai.tripStory(source)   // textes du carnet par Envolée AI, si activé
        val rates = core.rates()
        val people = trip.travellers ?: 2
        val totalEur = com.riad.voyage.data.BudgetCalculator.compute(
            budget.budgetFor(trip.city, core.countryOf(trip.city, trip.country), trip, rates, trip.nights ?: 3, people),
        ).sumOf { it.amountEur }
        val cur = core.settings.value.currency.uppercase()
        val k = if (cur == "EUR") 1.0 else rates[cur]
        fun money(eur: Double) = if (k == null) com.riad.voyage.ui.formatPrice(eur.toInt(), "EUR") else com.riad.voyage.ui.formatPrice((eur * k).toInt(), cur)
        val start = trip.start
        val content = com.riad.voyage.ui.TripPdf.Content(
            trip = trip,
            weather = core.weatherFor(trip.city).filter { w -> start != null && !w.date.isBefore(start) && !w.date.isAfter(trip.end ?: start) },
            info = core.practicalFor(trip.city, trip.country),
            packing = trips.packingFor(trip),
            budgetTotal = money(totalEur),
            budgetPerPerson = money(totalEur / people),
            people = people,
        )
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.riad.voyage.ui.TripPdf.write(getApplication(), content) }
    }

    /** Ouvre les infos pratiques de la ville d'un voyage (page Ville). */
    fun openPractical(trip: Trip) {
        places.show(trip.city)
        setCitySection(CitySection.INFO)
    }

    // ---------- Mes voyages ----------

    fun saveExperience(silent: Boolean = false) {
        val st = _experience.value
        val exp = st.result ?: return
        val trip = linkedTrip(st) ?: Trip(id = "trip-${System.currentTimeMillis()}", city = exp.city.trim(),
            country = st.answers.place?.country?.ifBlank { null } ?: st.answers.country)
        val program = exp.days.map { d ->
            SavedDay(d.title, d.weather?.label, d.steps.map { s ->
                SavedStep(s.time, t(s.moment), s.category.emoji, s.why, s.durationMin,
                    SavedPlace.of(s.place))
            })
        }
        // Le voyage porte les mêmes dates, heures et voyageurs que le programme (une seule source pour le PDF,
        // le budget et les bagages).
        val a = st.answers
        val end = a.startDate.plusDays((a.duration.days - 1).toLong())
        val c = a.company
        val travellers = when {
            a.travellers != null || "company" in a.assumed -> a.travellers ?: trip.travellers   // rien de supposé dans le voyage
            c == Quiz.Company.SOLO || c == Quiz.Company.COUPLE -> a.people
            else -> trip.travellers ?: a.people
        }
        val updated = trip.copy(programTitle = exp.title, programSubtitle = exp.subtitle, program = program, travellers = travellers,
            startDate = a.startDate.toString(), endDate = end.toString(), arrivalTime = hhmm(a.arrivalMin) ?: trip.arrivalTime,
            departureTime = hhmm(a.departureMin) ?: trip.departureTime,
            restAfterArrival = a.restAfterArrival, programNotes = exp.allNotes)
        if (silent) { core.tripStore.upsert(updated); core.refreshTrips() }
        else core.saveTrip(updated, tf("Programme enregistré dans votre voyage : {0}", t(exp.city)))
        _experience.update { it.copy(tripId = updated.id) }
    }

    override fun updateTripInfo(id: String, start: LocalDate, end: LocalDate, travellers: Int, arrival: String?, departure: String?) {
        trips.updateTrip(id) {
            it.copy(startDate = start.toString(), endDate = end.toString(), travellers = travellers, arrivalTime = arrival, departureTime = departure)
        }
        // Programme de ce voyage : mêmes dates, heures et voyageurs (recalculé sans nouvelle recherche).
        if (_experience.value.tripId != id) return
        val days = (java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1).coerceIn(1, Quiz.Duration.MAX_DAYS)
        val change: (Quiz.Answers) -> Quiz.Answers = { a ->
            a.copy(startDate = start, duration = Quiz.Duration(days), arrivalMin = arrival?.let(::minutesOf),
                departureMin = departure?.let(::minutesOf), company = companyFor(travellers, a.company), travellers = travellers,
                assumed = a.assumed - "company")
        }
        if (_experience.value.result != null) refineExperience(change) else updateAnswers(change)
    }

    /** Compagnie de voyage cohérente avec un nombre de voyageurs (garde la réponse actuelle quand elle convient). */
    private fun companyFor(travellers: Int, current: Quiz.Company) = when (travellers) {
        1 -> Quiz.Company.SOLO
        2 -> if (current == Quiz.Company.SOLO) Quiz.Company.COUPLE else current
        else -> if (current == Quiz.Company.FAMILY) current else Quiz.Company.FRIENDS
    }

    /** « Programme sur mesure » depuis un voyage : ville, dates et voyageurs déjà remplis ; reste les envies. */
    override fun planTrip(trip: Trip) {
        // Repart de zéro (ou des préférences), avec la ville, les dates, les heures et les voyageurs du voyage.
        ai.reset()
        val fresh = ExperienceState(answers = freshAnswers(trip), tripId = trip.id)
        _experience.value = fresh.copy(step = fresh.steps.indexOf("interests").coerceAtLeast(0))
        core.tab.value = Tab.EXPERIENCE
    }

    // ---------- Blogue ----------

    /** Villes liées aux recherches de la personne : vol, sorties, expérience. */
    fun blogContext(): List<Pair<BlogCity, String>> {
        val out = mutableListOf<Pair<BlogCity, String>>()
        fun add(p: Place?, name: String?, why: String) {
            val n = (p?.name ?: name)?.trim().orEmpty()
            if (n.isBlank() || out.any { it.first.name.equals(n, ignoreCase = true) }) return
            val known = Blog.city(n)
            val c = known ?: p?.takeIf { it.lat != null && it.lon != null }
                ?.let { BlogCity(it.name, it.country, "fr:${it.name}", it.lat!!, it.lon!!) }
                ?: PopularPlaces.all.firstOrNull { it.name.equals(n, ignoreCase = true) && it.lat != null }
                    ?.let { BlogCity(it.name, it.country, "fr:${it.name}", it.lat!!, it.lon!!) }
                ?: return
            out += c to why
        }
        add(_experience.value.answers.place, _experience.value.answers.city, "D'après votre envolée")
        add(flights.results.value.query?.destination, null, "D'après votre vol")
        add(flights.form.value.destination, null, "D'après votre vol")
        add(places.place.value, places.city.value, "D'après vos sorties")
        return out
    }

    /** « Lire l'article complet » depuis la fiche d'un lieu : ouvre le lecteur du Blogue. */
    fun readPlaceArticle(tag: String) {
        _citySection.value = CitySection.ARTICLES
        core.tab.value = Tab.CITY
        blog.openArticle(tag)
    }

    // ---------- Expérience ----------

    override fun updateAnswers(transform: (Quiz.Answers) -> Quiz.Answers) =
        _experience.update { it.copy(answers = transform(it.answers)) }

    fun quizStep(delta: Int) = _experience.update {
        it.copy(step = (it.step + delta).coerceIn(0, it.steps.size - 1), error = null)
    }

    /** Passe du questionnaire rapide au questionnaire détaillé (et inversement). */
    fun setQuickQuiz(quick: Boolean) = _experience.update { it.copy(quick = quick, step = 0, result = null, error = null) }

    /**
     * Change un réglage depuis le résultat (compagnie, rythme, budget…) et recompose le programme.
     * Régime ou goûts changés : on recharge les lieux (la recherche en dépend).
     */
    fun refineExperience(transform: (Quiz.Answers) -> Quiz.Answers) {
        val old = _experience.value.answers
        answer(transform)
        val a = _experience.value.answers
        if (a.diet != old.diet || a.tastes != old.tastes || experiencePools.isEmpty()) return generateExperience()
        showProgram(ExperienceBuilder.build(a, a.city.trim(), experiencePools, weather = experienceWeather))
    }

    fun moveExperienceStep(dayIndex: Int, stepIndex: Int, delta: Int) = editResult {
        ExperienceBuilder.moveStep(_experience.value.answers, it, dayIndex, stepIndex, delta)
    }

    fun addExperienceStep(dayIndex: Int, name: String) = editResult {
        if (name.isBlank()) it else ExperienceBuilder.addStep(_experience.value.answers, it, dayIndex, name)
    }

    private fun editResult(transform: (Experience) -> Experience) {
        val exp = _experience.value.result ?: return
        _experience.update { it.copy(result = transform(exp)) }
        autosave()
    }

    /**
     * Point de départ d'une demande libre : réponses par défaut, plus le voyage relié (ville, dates, heures, voyageurs)
     * et les préférences personnelles (rythme, budget, régime, déplacements). Rien d'une demande précédente.
     */
    override fun freshAnswers(trip: Trip?): Quiz.Answers {
        val a = blankAnswers()
        if (trip == null) return a
        return a.copy(
            city = trip.city, startDate = trip.start ?: LocalDate.now(),
            duration = trip.nights?.let { Quiz.Duration((it + 1).coerceIn(1, Quiz.Duration.MAX_DAYS)) } ?: Quiz.Duration.DAY,
            company = trip.travellers?.let { companyFor(it, a.company) } ?: a.company, travellers = trip.travellers,
            arrivalMin = trip.arrivalMin, departureMin = trip.departureMin, restAfterArrival = trip.restAfterArrival,
        )
    }

    /** Réponse changée à la main : les remarques d'Envolée AI sur l'ancienne demande ne valent plus. */
    fun answer(transform: (Quiz.Answers) -> Quiz.Answers) = updateAnswers { transform(it).copy(extraNotes = emptyList()).confirmedFrom(it) }

    /** Programme relié à un voyage : chaque changement y est enregistré sans action de la personne. */
    override fun autosave() = saveExperience(silent = true)

    /** Nouveau programme composé : affiché, enregistré, puis vérifié et enrichi par Envolée AI selon les Réglages. */
    override fun showProgram(exp: Experience, avoid: Set<String>) {
        _experience.update { it.copy(result = exp.copy(notes = exp.notes + budgetNote(exp)), previous = avoid, undo = null, changeSummary = null) }
        autosave()
        ai.afterProgram()
    }

    /** Budget annoncé comparé à l'estimation (en euros). */
    private fun budgetNote(exp: Experience): List<String> {
        val st = _experience.value
        val a = st.answers
        val stated = a.budgetEur ?: return emptyList()
        val program = exp.days.flatMap { d -> d.steps.map { PlannedActivity(it.place.name, it.place.kind, it.category) } }
        val est = com.riad.voyage.data.BudgetCalculator.compute(budget.budgetFor(exp.city, a.country, linkedTrip(st), emptyMap(),
            if (a.duration.halfDay) 0 else a.duration.days - 1, a.people ?: 1, program)).sumOf { it.amountEur }.toInt()
        return listOf(if (est <= stated) tf("Budget estimé {0} € pour {1} € prévus ✓", est, stated)
            else tf("Budget estimé {0} € pour {1} € prévus : plus cher que prévu, voyez le budget pour ajuster.", est, stated))
    }

    /** Voyage relié au programme affiché (même ville), s'il y en a un. */
    override fun linkedTrip(st: ExperienceState): Trip? {
        val city = st.result?.city ?: st.answers.city
        return core.trips.value.firstOrNull { it.id == st.tripId && it.city.equals(city.trim(), ignoreCase = true) }
    }

    /** Nouvelle heure pour une étape ; les suivantes sont décalées, rien n'est recomposé. */
    fun setExperienceStepTime(dayIndex: Int, stepIndex: Int, minutes: Int) = editResult {
        ExperienceBuilder.setStepTime(_experience.value.answers, it, dayIndex, stepIndex, minutes)
    }

    /** Charge les meilleurs lieux de chaque catégorie utile, puis compose l'expérience. */
    override fun generateExperience() {
        updateAnswers { it.copy(country = core.countryOf(it.city.trim(), it.place?.country.orEmpty()).ifBlank { it.country }) }   // cuisine locale, fériés
        val a = _experience.value.answers
        val city = a.city.trim()
        if (city.isBlank()) return
        _experience.update { it.copy(loading = true, stage = 1, error = null, result = null) }
        viewModelScope.launch {
            // « Florence, Italie » : pas de confusion avec une homonyme (Florence aux États-Unis).
            val geo = core.resolveCity(if (a.country.isNotBlank() && a.place == null) "$city, ${a.country}" else city,
                a.place?.takeIf { it.name.equals(city, ignoreCase = true) })
            if (geo == null) {
                _experience.update { it.copy(loading = false, error = t("Ville introuvable : choisissez-la dans la liste proposée.")) }
                return@launch
            }
            // Météo des jours du séjour (prévisions jusqu'à 16 jours), chargée en parallèle des lieux.
            val weatherJob = async {
                val inRange = !a.startDate.isAfter(LocalDate.now().plusDays(WeatherApi.FORECAST_DAYS - 1L))
                if (!inRange) emptyMap() else attempt(10_000) { WeatherApi.forecast(geo.lat, geo.lon) }?.getOrNull().orEmpty().associateBy { it.date }
            }
            val needed = ExperienceBuilder.neededCategories(a)
            _experience.update { it.copy(stage = 2) }
            val pools = coroutineScope {
                needed.map { c ->
                    val hint = ExperienceBuilder.searchHint(a, c)
                    async { c to (withTimeoutOrNull(25_000) { places.loadCategory(city, geo, c, hint) } ?: emptyList()) }
                }
                    .map { it.await() }.toMap()
            }
            // Analyse des lieux : description Wikipédia des meilleurs candidats, pour vérifier
            // qu'ils correspondent vraiment aux goûts (et non au seul nom).
            _experience.update { it.copy(stage = 3) }
            val analysed = coroutineScope {
                pools.map { (c, list) ->
                    async {
                        c to list.mapIndexed { i, p ->
                            if (i >= 25 || p.description != null || p.wikipedia == null) p
                            else {
                                val summary = withTimeoutOrNull(6_000) { core.wikiSummary(p.wikipedia) }
                                if (summary != null) p.copy(description = summary.extract) else p
                            }
                        }
                    }
                }.map { it.await() }.toMap()
            }
            experiencePools = ExperienceBuilder.withoutBorrowedImages(analysed)
            _experience.update { it.copy(stage = 4) }
            experienceWeather = weatherJob.await()
            val exp = ExperienceBuilder.build(a, city, experiencePools, weather = experienceWeather)
            prefStore.record(a)
            _prefsSummary.value = computePrefsSummary()
            if (exp.days.isEmpty()) _experience.update {
                it.copy(loading = false, error =
                    if (analysed.values.any { l -> l.isNotEmpty() }) tf("Les lieux trouvés à {0} sont fermés à ces dates. Essayez d'autres dates ou d'autres envies.", t(city))
                    else tf("Pas assez de lieux trouvés pour {0}. Essayez d'autres envies ou une grande ville proche.", t(city)))
            } else {
                _experience.update { it.copy(loading = false) }
                showProgram(exp)
            }
        }
    }

    /**
     * « Planifier mon séjour » depuis les résultats de vol : ville, dates et durée déjà remplies ;
     * les Sorties et le Blogue suivent la même destination.
     */
    fun planStayFromFlight(flight: Flight?) {
        val q = flights.results.value.query ?: flights.form.value
        val dest = q.destination ?: return
        val start = flight?.departureDate ?: q.departure
        val end = flight?.returnAt?.let { parseDateTime(it)?.toLocalDate() }
            ?: if (q.roundTrip && !q.flexibleMonth) q.returnDate else null
        val nights = end?.let { ChronoUnit.DAYS.between(start, it) } ?: 1
        // L'expérience couvre tout le séjour : du jour d'arrivée au jour du départ.
        val duration = if (end == null) Quiz.Duration.DAY
        else Quiz.Duration((nights + 1).toInt().coerceIn(1, Quiz.Duration.MAX_DAYS))
        val fresh = ExperienceState(answers = blankAnswers().copy(city = dest.name, place = dest, startDate = start, duration = duration))
        _experience.value = fresh.copy(step = fresh.steps.indexOf("interests").coerceAtLeast(0))
        places.show(dest.name, dest)
        blog.reset()
        core.tab.value = Tab.EXPERIENCE
    }

    /** Lieux proposés pour remplacer une étape du programme. */
    fun stepAlternatives(dayIndex: Int, stepIndex: Int): List<Pair<ActivityLinks.Category, PointOfInterest>> {
        val st = _experience.value
        val exp = st.result ?: return emptyList()
        return alternativesFor(st.answers, exp, dayIndex, stepIndex)
    }

    override fun alternativesFor(a: Quiz.Answers, exp: Experience, d: Int, i: Int): List<Pair<ActivityLinks.Category, PointOfInterest>> =
        ExperienceBuilder.alternatives(a, exp, experiencePools, d, i, limit = 30).filter { it.second.id !in ai.knownClosed }.take(12)

    /** Retire une étape (choice = null) ou la remplace par le lieu choisi ; les horaires sont recalculés. */
    fun replaceExperienceStep(dayIndex: Int, stepIndex: Int, choice: Pair<ActivityLinks.Category, PointOfInterest>?) {
        val st = _experience.value
        val exp = st.result ?: return
        val updated = ExperienceBuilder.replaceStep(st.answers, exp, dayIndex, stepIndex, choice)
        _experience.update { it.copy(result = updated, previous = it.previous + exp.placeIds) }
        autosave()
    }

    /** Autre proposition : mêmes réponses, en écartant les lieux de la proposition affichée (pas de hasard). */
    fun regenerateExperience() {
        val st = _experience.value
        if (experiencePools.isEmpty()) return generateExperience()
        val avoid = st.previous + (st.result?.placeIds ?: emptySet())
        showProgram(ExperienceBuilder.build(st.answers, st.answers.city.trim(), experiencePools, avoid, experienceWeather), avoid)
    }

    /** Meilleurs lieux d'une catégorie (même logique que l'onglet Sorties, sans l'afficher). */
    fun clearMessage() { core.message.value = null }

    // ---------- Vol + sorties ----------

    /** Ouvre les sorties de la ville de destination. */
    fun activitiesAtDestination() {
        val q = flights.results.value.query ?: flights.form.value
        q.destination?.let { places.show(it.name, it) }
        selectTab(Tab.ACTIVITIES)
    }

    // ---------- Réglages ----------

    fun setQuotaLimit(src: RatingSource, limit: Int) {
        core.quotas.setLimit(src, limit)
        places.clear()
    }

    /** Changement de langue : on vide les données déjà traduites (Wikipédia, lieux) pour les recharger. */
    fun setLanguage(l: Lang?) {
        I18n.choose(getApplication(), l)
        core.wikiCache.clear()
        places.clear()
        blog.reload()
        _experience.update { it.copy(result = null) }
    }

    /** « Actualiser » : modèles Gemini que Google ouvre à cette clé (les réglages en cours sont enregistrés d'abord). */
    fun refreshGeminiModels(edited: Settings) {
        saveSettings(edited)
        viewModelScope.launch {
            val r = attempt(20_000) { com.riad.voyage.data.ClaudeAssistant.geminiModels(edited.geminiKey) }
            val list = r?.getOrNull()
            if (list.isNullOrEmpty()) {
                core.message.value = r?.exceptionOrNull()?.let { TravelpayoutsApi.userMessage(it) } ?: t("Google n'a pas répondu : réessayez dans un moment.")
                return@launch
            }
            val s = core.settings.value
            saveSettings(s.copy(geminiModels = list, geminiModel = s.geminiModel.takeIf { id -> list.any { it.id == id } || com.riad.voyage.data.GeminiModel.defaults.any { it.id == id } } ?: list.first().id))
            core.message.value = tf("{0} modèles Gemini disponibles avec votre clé.", list.size)
        }
    }

    fun saveSettings(s: Settings) {
        places.clear()
        ActivityLinks.tiqetsPartner = s.tiqetsPartner
        core.settingsStore.save(s)
        core.settings.value = core.settingsStore.load()
        testKey()
    }

    /** Vérifie la clé avec une petite recherche réelle (Paris → Londres le mois prochain). */
    fun testKey() {
        val s = core.settings.value
        if (s.isDemo) {
            _keyStatus.value = KeyStatus.Unknown
            return
        }
        _keyStatus.value = KeyStatus.Testing
        viewModelScope.launch {
            _keyStatus.value = try {
                TravelpayoutsApi(s.token, s.currency).pricesForDates(
                    "PAR", "LON", YearMonth.now().plusMonths(1).toString(), null, false, limit = 1,
                )
                KeyStatus.Ok
            } catch (e: Exception) {
                KeyStatus.Failed(TravelpayoutsApi.userMessage(e))
            }
        }
    }
}
