package com.riad.voyage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riad.voyage.alerts.AlertChecker
import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.AlertStore
import com.riad.voyage.data.DayPrice
import com.riad.voyage.data.DemoData
import com.riad.voyage.data.Flight
import com.riad.voyage.data.Place
import com.riad.voyage.data.PopularPlaces
import com.riad.voyage.data.PriceAlert
import com.riad.voyage.data.RecentSearch
import com.riad.voyage.data.RecentStore
import com.riad.voyage.data.Settings
import com.riad.voyage.data.SettingsStore
import com.riad.voyage.data.TravelpayoutsApi
import com.riad.voyage.ui.formatPrice
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class Tab(val label: String, val inBottomBar: Boolean = true) {
    HOME("Accueil"), FLIGHTS("Vols"), ACTIVITIES("Sorties"), EXPERIENCE("Expérience"), BLOG("Blogue"),
    ALERTS("Alertes", inBottomBar = false),
    SETTINGS("Réglages", inBottomBar = false),
}

sealed interface KeyStatus {
    data object Unknown : KeyStatus
    data object Testing : KeyStatus
    data object Ok : KeyStatus
    data class Failed(val message: String) : KeyStatus
}

enum class Sort(val label: String) { PRICE("Prix"), DURATION("Durée"), TRANSFERS("Escales") }

data class FlightForm(
    val origin: Place? = PopularPlaces.find("PAR"),
    val destination: Place? = null,
    val departure: LocalDate = LocalDate.now().plusDays(14),
    val returnDate: LocalDate = LocalDate.now().plusDays(21),
    val roundTrip: Boolean = true,
    val flexibleMonth: Boolean = false,
    val adults: Int = 1,
    val directOnly: Boolean = false,
) {
    val departureParam get() = if (flexibleMonth) YearMonth.from(departure).toString() else departure.toString()
    val returnParam: String?
        get() = if (!roundTrip) null else if (flexibleMonth) YearMonth.from(returnDate).toString() else returnDate.toString()
}

data class ResultsState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val query: FlightForm? = null,
    val flights: List<Flight> = emptyList(),
    val calendar: List<DayPrice> = emptyList(),
    val demo: Boolean = false,
    val currency: String = "EUR",
    val sort: Sort = Sort.PRICE,
) {
    val sortedFlights: List<Flight>
        get() = when (sort) {
            Sort.PRICE -> flights.sortedBy { it.price }
            Sort.DURATION -> flights.sortedWith(compareBy({ it.totalDuration.takeIf { d -> d > 0 } ?: Int.MAX_VALUE }, { it.price }))
            Sort.TRANSFERS -> flights.sortedWith(compareBy({ it.totalTransfers }, { it.price }))
        }
}

data class PlacesState(
    val loading: Boolean = false,
    val error: String? = null,
    val items: List<com.riad.voyage.data.PointOfInterest> = emptyList(),
    val city: String = "",
    val category: ActivityLinks.Category? = null,
    val guideFound: Boolean = false,
    val stage: String = "",
    val source: String = "",
    val ratingSources: List<String> = emptyList(),
    val notice: String? = null,
)

const val SOURCE_FAME = "fame"
const val SOURCE_GUIDE = "guide"
const val SOURCE_MAP = "map"
const val SOURCE_RATINGS = "ratings"

data class BlogState(
    val city: com.riad.voyage.data.BlogCity? = null,
    val filter: com.riad.voyage.data.BlogFilter = com.riad.voyage.data.BlogFilter.ALL,
    val cards: List<com.riad.voyage.data.ArticleCard> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val article: com.riad.voyage.data.Article? = null,
    val articleLoading: Boolean = false,
    val reason: String? = null,       // « D'après votre vol », « D'après vos sorties »…
)

data class ExperienceState(
    val answers: com.riad.voyage.data.Quiz.Answers = com.riad.voyage.data.Quiz.Answers(),
    val step: Int = 0,
    val loading: Boolean = false,
    val stage: String = "",
    val result: com.riad.voyage.data.Experience? = null,
    val error: String? = null,
    val previous: Set<String> = emptySet(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val alertStore = AlertStore(app)
    private val recentStore = RecentStore(app)
    val quotas = com.riad.voyage.data.QuotaStore(app)

    private val _recents = MutableStateFlow(recentStore.all())
    val recents: StateFlow<List<RecentSearch>> = _recents.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _tab = MutableStateFlow(Tab.HOME)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    // Reprend le dernier départ et le nombre de passagers de la recherche précédente.
    private val _form = MutableStateFlow(
        _recents.value.firstOrNull()?.let { FlightForm(origin = it.origin, adults = it.adults) } ?: FlightForm(),
    )
    val form: StateFlow<FlightForm> = _form.asStateFlow()

    private val _results = MutableStateFlow(ResultsState())
    val results: StateFlow<ResultsState> = _results.asStateFlow()


    private val _activityPlace = MutableStateFlow<Place?>(null)
    val activityPlace: StateFlow<Place?> = _activityPlace.asStateFlow()

    private val _activityCity = MutableStateFlow("")
    val activityCity: StateFlow<String> = _activityCity.asStateFlow()

    private val _activityCategory = MutableStateFlow(ActivityLinks.Category.ATTRACTIONS)
    val activityCategory: StateFlow<ActivityLinks.Category> = _activityCategory.asStateFlow()

    private val _places = MutableStateFlow(PlacesState())
    val places: StateFlow<PlacesState> = _places.asStateFlow()
    private var placesJob: kotlinx.coroutines.Job? = null
    private val geoCache = mutableMapOf<String, com.riad.voyage.data.GeoPoint>()
    private val placesCache = mutableMapOf<String, PlacesState>()

    private val wikiCache = mutableMapOf<String, com.riad.voyage.data.WikiSummary?>()
    private val guideCache = mutableMapOf<String, Set<String>>()

    private val _experience = MutableStateFlow(ExperienceState())
    val experience: StateFlow<ExperienceState> = _experience.asStateFlow()
    private var experiencePools: Map<ActivityLinks.Category, List<com.riad.voyage.data.PointOfInterest>> = emptyMap()

    private val _blog = MutableStateFlow(BlogState())
    val blog: StateFlow<BlogState> = _blog.asStateFlow()
    private var blogJob: kotlinx.coroutines.Job? = null

    private val _alerts = MutableStateFlow(alertStore.all())
    val alerts: StateFlow<List<PriceAlert>> = _alerts.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _keyStatus = MutableStateFlow<KeyStatus>(KeyStatus.Unknown)
    val keyStatus: StateFlow<KeyStatus> = _keyStatus.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private fun api() = _settings.value.let { TravelpayoutsApi(it.token, it.currency) }

    fun selectTab(t: Tab) {
        // Les sorties reprennent la destination du vol si rien n'est saisi.
        if (t == Tab.ACTIVITIES && _activityCity.value.isBlank()) {
            _activityCity.value = _form.value.destination?.name.orEmpty()
            _activityPlace.value = _form.value.destination
        }
        if (t == Tab.EXPERIENCE && _experience.value.answers.city.isBlank()) {
            val dest = _form.value.destination ?: _activityPlace.value
            if (dest != null) updateAnswers { it.copy(city = dest.name, place = dest) }
        }
        _tab.value = t
        if (t == Tab.ACTIVITIES) searchActivities()
        if (t == Tab.BLOG && _blog.value.city == null) blogContext().firstOrNull()?.let { (c, why) -> openBlogCity(c, why) }
    }

    fun setActivityCity(city: String) { _activityCity.value = city }

    /** Ville choisie dans les suggestions : on garde sa position et on lance la recherche. */
    fun setActivityPlace(p: Place) {
        _activityPlace.value = p
        _activityCity.value = p.name
        searchActivities(force = true)
    }
    fun setActivityCategory(c: ActivityLinks.Category) {
        _activityCategory.value = c
        searchActivities()
    }

    /** Cherche les lieux de la catégorie choisie (sauf si la même recherche est déjà affichée). */
    fun searchActivities(force: Boolean = false) {
        val city = _activityCity.value.trim()
        val category = _activityCategory.value
        if (city.isBlank() || category == ActivityLinks.Category.TOURS) return
        val key = "${city.lowercase()}|$category"
        // Déjà cherché : réponse immédiate depuis le cache.
        if (!force) placesCache[key]?.let { _places.value = it; return }
        val current = _places.value
        if (!force && current.city.equals(city, ignoreCase = true) && current.category == category && current.loading) return

        placesJob?.cancel()
        val chosen = _activityPlace.value?.takeIf { it.name.equals(city, ignoreCase = true) }
        _places.value = PlacesState(loading = true, stage = "Recherche de la ville…", city = city, category = category)
        placesJob = viewModelScope.launch {
            val geo = resolveCity(city, chosen)
            if (geo == null) {
                _places.value = PlacesState(
                    error = "Ville introuvable ou connexion indisponible. Choisissez la ville dans la liste proposée.",
                    city = city, category = category,
                )
                return@launch
            }

            _places.value = _places.value.copy(stage = "Recherche des meilleurs lieux…")
            val db = com.riad.voyage.data.NotablePlaces

            // 1) Musées, monuments, attractions, parcs, plages : top 30 de la ville par notoriété.
            if (db.supports(category)) {
                val top = kotlinx.coroutines.withTimeoutOrNull(12_000) {
                    runCatching { db.search(geo, category) }.getOrNull()
                } ?: emptyList()
                if (top.size >= 3) {
                    publish(key, PlacesState(items = top, city = city, category = category, source = SOURCE_FAME))
                    return@launch
                }
            }

            // 2) Restaurants, cafés, bars, sorties, shopping : sélection du guide Wikivoyage de la ville.
            val guidePicks = if (!db.supports(category)) {
                val listings = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                    runCatching { com.riad.voyage.data.WikivoyageGuide.listings(city, geo.wikidata) }.getOrNull()
                } ?: emptyList()
                com.riad.voyage.data.GuideParser.select(listings, category).take(30)
                    .map { com.riad.voyage.data.GuideParser.toPoi(it, category, geo) }
            } else emptyList()
            // 2 bis) Notes Google + Tripadvisor + popularité Foursquare, chacune dans sa limite gratuite.
            val agg = com.riad.voyage.data.RatingsAggregator
            val st = _settings.value
            val hasRatingKeys = st.googleKey.isNotBlank() || st.tripadvisorKey.isNotBlank() || st.foursquareKey.isNotBlank()
            var quotaNotice: String? = null
            if (hasRatingKeys && agg.supports(category)) {
                _places.value = _places.value.copy(stage = "Récupération des notes…")
                val outcome = kotlinx.coroutines.withTimeoutOrNull(20_000) {
                    runCatching { agg.search(st, quotas, city, geo, category, guidePicks) }.getOrNull()
                }
                if (outcome != null && outcome.skipped.isNotEmpty()) {
                    quotaNotice = "Limite gratuite atteinte pour " + outcome.skipped.joinToString(", ") { it.label } +
                        (if (outcome.usedSources.isNotEmpty()) " : relais pris par " + outcome.usedSources.joinToString { it.label } + "." else ".")
                }
                if (outcome != null && outcome.places.size >= 3) {
                    publish(key, PlacesState(items = outcome.places, city = city, category = category,
                        guideFound = outcome.places.any { it.inGuide }, source = SOURCE_RATINGS,
                        ratingSources = outcome.usedSources.map { it.label }, notice = quotaNotice))
                    return@launch
                }
            }

            if (guidePicks.size >= 5) {
                publish(key, PlacesState(items = guidePicks, city = city, category = category, guideFound = true,
                    source = SOURCE_GUIDE, notice = quotaNotice))
                return@launch
            }

            // 3) Secours : carte OpenStreetMap en direct (petite ville ou guide trop pauvre).
            _places.value = _places.value.copy(stage = "Recherche sur la carte…")
            var liveError: String? = null
            val live = kotlinx.coroutines.withTimeoutOrNull(20_000) {
                runCatching { com.riad.voyage.data.PlacesApi.search(geo, category) }
                    .onFailure { liveError = TravelpayoutsApi.userMessage(it) }
                    .getOrNull()
            }
            val merged = (guidePicks + (live ?: emptyList()).sortedWith(
                compareByDescending<com.riad.voyage.data.PointOfInterest> { it.notable }
                    .thenByDescending { it.website != null }
                    .thenBy { it.distanceM },
            )).distinctBy { it.name.lowercase() }.take(30)
            if (merged.isEmpty()) {
                _places.value = PlacesState(
                    error = when {
                        live != null -> "Aucun lieu trouvé pour cette ville."
                        liveError != null -> "Recherche impossible : $liveError"
                        else -> "Les serveurs de carte ne répondent pas (plus de 20 s). Réessayez dans un instant."
                    },
                    city = city, category = category,
                )
                return@launch
            }
            publish(key, PlacesState(items = merged, city = city, category = category,
                guideFound = guidePicks.isNotEmpty(), source = SOURCE_MAP))
        }
    }

    // ---------- Blogue ----------

    /** Villes liées aux recherches de la personne : vol, sorties, expérience. */
    fun blogContext(): List<Pair<com.riad.voyage.data.BlogCity, String>> {
        val out = mutableListOf<Pair<com.riad.voyage.data.BlogCity, String>>()
        fun add(p: Place?, name: String?, why: String) {
            val n = (p?.name ?: name)?.trim().orEmpty()
            if (n.isBlank() || out.any { it.first.name.equals(n, ignoreCase = true) }) return
            val known = com.riad.voyage.data.Blog.city(n)
            val c = known ?: p?.takeIf { it.lat != null && it.lon != null }
                ?.let { com.riad.voyage.data.BlogCity(it.name, it.country, "fr:${it.name}", it.lat!!, it.lon!!) }
                ?: PopularPlaces.all.firstOrNull { it.name.equals(n, ignoreCase = true) && it.lat != null }
                    ?.let { com.riad.voyage.data.BlogCity(it.name, it.country, "fr:${it.name}", it.lat!!, it.lon!!) }
                ?: return
            out += c to why
        }
        add(_experience.value.answers.place, _experience.value.answers.city, "D'après votre expérience")
        add(_results.value.query?.destination, null, "D'après votre vol")
        add(_form.value.destination, null, "D'après votre vol")
        add(_activityPlace.value, _activityCity.value, "D'après vos sorties")
        return out
    }

    fun openBlogCity(c: com.riad.voyage.data.BlogCity, reason: String? = null) {
        val personal = blogInterestCategories().isNotEmpty()
        _blog.update {
            it.copy(city = c, reason = reason, article = null,
                filter = if (personal) com.riad.voyage.data.BlogFilter.FOR_YOU else com.riad.voyage.data.BlogFilter.ALL)
        }
        loadBlogCards()
    }

    /** Ville ou lieu choisi dans la barre de recherche du blogue. */
    fun openBlogPlace(p: Place) {
        viewModelScope.launch {
            val geo = resolveCity(p.name, p) ?: run {
                _blog.update { it.copy(error = "Lieu introuvable.") }
                return@launch
            }
            openBlogCity(com.riad.voyage.data.Blog.city(p.name) ?: com.riad.voyage.data.BlogCity(p.name, p.country, "fr:${p.name}", geo.lat, geo.lon))
        }
    }

    fun clearBlogCity() = _blog.update { BlogState() }

    fun setBlogFilter(f: com.riad.voyage.data.BlogFilter) {
        _blog.update { it.copy(filter = f) }
        loadBlogCards()
    }

    /** Catégories qui intéressent la personne : envies de l'expérience, dernière catégorie de sorties. */
    private fun blogInterestCategories(): List<ActivityLinks.Category> {
        val fromExperience = _experience.value.answers.interests.mapNotNull { it.category }
        val fromActivities = listOf(_activityCategory.value).filter { com.riad.voyage.data.NotablePlaces.supports(it) }
        return (fromExperience + fromActivities).filter { com.riad.voyage.data.NotablePlaces.supports(it) }.distinct()
    }

    private fun loadBlogCards() {
        val st = _blog.value
        val city = st.city ?: return
        blogJob?.cancel()
        _blog.update { it.copy(loading = true, error = null, cards = emptyList()) }
        blogJob = viewModelScope.launch {
            val answers = _experience.value.answers
            val categories = if (st.filter == com.riad.voyage.data.BlogFilter.FOR_YOU) blogInterestCategories()
                .ifEmpty { com.riad.voyage.data.BlogFilter.ALL.categories } else st.filter.categories
            val cards = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                categories.flatMap { c ->
                    val tastes = com.riad.voyage.data.ExperienceBuilder.tastesFor(answers, c)
                    runCatching { com.riad.voyage.data.NotablePlaces.search(city.geo, c, limit = 20) }.getOrNull().orEmpty()
                        .filter { it.wikipedia != null }
                        .map { p ->
                            com.riad.voyage.data.ArticleCard(
                                wiki = p.wikipedia!!, title = p.name, kind = p.kind, category = c, imageUrl = p.imageUrl,
                                fame = p.sitelinks, tasteMatch = com.riad.voyage.data.ExperienceBuilder.tasteScore(p, tastes) > 0,
                            )
                        }
                }.distinctBy { it.wiki }
                    .sortedWith(compareByDescending<com.riad.voyage.data.ArticleCard> { it.tasteMatch }.thenByDescending { it.fame })
                    .take(40)
            }
            val cityCard = com.riad.voyage.data.ArticleCard(city.wiki, "Découvrir ${city.name}", "Ville · ${city.country}", null, null, 10_000)
            _blog.update {
                if (cards == null) it.copy(loading = false, error = "Les articles ne se chargent pas. Vérifiez la connexion.", cards = listOf(cityCard))
                else it.copy(loading = false, cards = listOf(cityCard) + cards)
            }
        }
    }

    fun openArticle(tag: String) {
        _blog.update { it.copy(articleLoading = true, article = null, error = null) }
        viewModelScope.launch {
            val article = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                runCatching { com.riad.voyage.data.WikiArticles.article(tag) }.getOrNull()
            }
            _blog.update {
                if (article == null) it.copy(articleLoading = false, error = "Article indisponible pour le moment.")
                else it.copy(articleLoading = false, article = article)
            }
        }
    }

    fun closeArticle() = _blog.update { it.copy(article = null, articleLoading = false) }

    suspend fun searchArticles(q: String): List<Pair<String, String>> =
        runCatching { com.riad.voyage.data.WikiArticles.search(q) }.getOrNull().orEmpty()

    // ---------- Expérience ----------

    fun updateAnswers(transform: (com.riad.voyage.data.Quiz.Answers) -> com.riad.voyage.data.Quiz.Answers) =
        _experience.update { it.copy(answers = transform(it.answers)) }

    fun quizStep(delta: Int) = _experience.update {
        val total = com.riad.voyage.data.Quiz.steps(it.answers).size
        it.copy(step = (it.step + delta).coerceIn(0, total - 1), error = null)
    }

    fun editExperience() = _experience.update { it.copy(result = null, step = 0, error = null) }

    /** Charge les meilleurs lieux de chaque catégorie utile, puis compose l'expérience. */
    fun generateExperience() {
        val a = _experience.value.answers
        val city = a.city.trim()
        if (city.isBlank()) return
        _experience.update { it.copy(loading = true, stage = "Recherche de la ville…", error = null, result = null) }
        viewModelScope.launch {
            val geo = resolveCity(city, a.place?.takeIf { it.name.equals(city, ignoreCase = true) })
            if (geo == null) {
                _experience.update { it.copy(loading = false, error = "Ville introuvable : choisissez-la dans la liste proposée.") }
                return@launch
            }
            val needed = com.riad.voyage.data.ExperienceBuilder.neededCategories(a)
            _experience.update { it.copy(stage = "Sélection des meilleurs lieux de $city…") }
            val pools = coroutineScope {
                needed.map { c ->
                    val hint = com.riad.voyage.data.ExperienceBuilder.searchHint(a, c)
                    async { c to (kotlinx.coroutines.withTimeoutOrNull(25_000) { loadCategory(city, geo, c, hint) } ?: emptyList()) }
                }
                    .map { it.await() }.toMap()
            }
            // Analyse des lieux : description Wikipédia des meilleurs candidats, pour vérifier
            // qu'ils correspondent vraiment aux goûts (et non au seul nom).
            _experience.update { it.copy(stage = "Analyse des lieux selon vos goûts…") }
            val analysed = coroutineScope {
                pools.map { (c, list) ->
                    async {
                        c to list.mapIndexed { i, p ->
                            if (i >= 25 || p.description != null || p.wikipedia == null) p
                            else {
                                val summary = kotlinx.coroutines.withTimeoutOrNull(6_000) { wikiSummary(p.wikipedia) }
                                if (summary != null) p.copy(description = summary.extract) else p
                            }
                        }
                    }
                }.map { it.await() }.toMap()
            }
            experiencePools = analysed
            _experience.update { it.copy(stage = "Composition du programme…") }
            val exp = com.riad.voyage.data.ExperienceBuilder.build(a, city, analysed)
            _experience.update {
                if (exp.days.isEmpty()) it.copy(loading = false, error = "Pas assez de lieux trouvés pour $city. Essayez d'autres envies ou une grande ville proche.")
                else it.copy(loading = false, result = exp, previous = emptySet())
            }
        }
    }

    /** Autre proposition : mêmes réponses, en écartant les lieux de la proposition affichée (pas de hasard). */
    fun regenerateExperience() {
        val st = _experience.value
        if (experiencePools.isEmpty()) return generateExperience()
        val avoid = st.previous + (st.result?.placeIds ?: emptySet())
        val exp = com.riad.voyage.data.ExperienceBuilder.build(st.answers, st.answers.city.trim(), experiencePools, avoid)
        _experience.update { it.copy(result = exp, previous = avoid) }
    }

    /** Meilleurs lieux d'une catégorie (même logique que l'onglet Sorties, sans l'afficher). */
    private suspend fun loadCategory(
        city: String,
        geo: com.riad.voyage.data.GeoPoint,
        category: ActivityLinks.Category,
        hint: String? = null,
    ): List<com.riad.voyage.data.PointOfInterest> {
        // Cache séparé de l'onglet Sorties : plus de lieux par catégorie pour coller aux goûts.
        val key = "${city.lowercase()}|$category|exp|${hint.orEmpty()}"
        placesCache[key]?.items?.takeIf { it.isNotEmpty() }?.let { return it }
        val db = com.riad.voyage.data.NotablePlaces
        val items: List<com.riad.voyage.data.PointOfInterest> = if (db.supports(category)) {
            runCatching { db.search(geo, category, limit = 80) }.getOrNull().orEmpty()
        } else {
            val listings = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                runCatching { com.riad.voyage.data.WikivoyageGuide.listings(city, geo.wikidata) }.getOrNull()
            }.orEmpty()
            val guide = com.riad.voyage.data.GuideParser.select(listings, category).take(30)
                .map { com.riad.voyage.data.GuideParser.toPoi(it, category, geo) }
            val st = _settings.value
            val rated = if (st.googleKey.isNotBlank() || st.tripadvisorKey.isNotBlank() || st.foursquareKey.isNotBlank()) {
                runCatching { com.riad.voyage.data.RatingsAggregator.search(st, quotas, city, geo, category, guide, hint) }
                    .getOrNull()?.places.orEmpty()
            } else emptyList()
            when {
                rated.size >= 3 -> rated
                guide.size >= 3 -> guide
                else -> (guide + runCatching { com.riad.voyage.data.PlacesApi.search(geo, category) }.getOrNull().orEmpty())
                    .distinctBy { it.name.lowercase() }.take(30)
            }
        }
        if (items.isNotEmpty()) placesCache[key] = PlacesState(items = items, city = city, category = category)
        return items
    }

    private fun publish(key: String, state: PlacesState) {
        placesCache[key] = state
        if (_places.value.city.equals(state.city, ignoreCase = true) && _places.value.category == state.category) {
            _places.value = state
        }
    }

    /** Position de la ville : suggestion choisie, liste intégrée, cache, puis OpenStreetMap. */
    private suspend fun resolveCity(city: String, chosen: Place?): com.riad.voyage.data.GeoPoint? {
        val key = city.lowercase()
        geoCache[key]?.let { return it }
        val known = chosen?.takeIf { it.lat != null && it.lon != null }
            ?: PopularPlaces.all.firstOrNull { it.name.equals(city, ignoreCase = true) && it.lat != null }
        val geo = known?.let { com.riad.voyage.data.GeoPoint(it.lat!!, it.lon!!, it.name) }
            ?: kotlinx.coroutines.withTimeoutOrNull(10_000) {
                runCatching { com.riad.voyage.data.PlacesApi.geocode(city) }.getOrNull()
            }
        geo?.let { geoCache[key] = it }
        return geo
    }

    private suspend fun cityGuide(geo: com.riad.voyage.data.GeoPoint, city: String): Set<String> {
        val id = geo.wikidata
            ?: runCatching { com.riad.voyage.data.PlacesApi.geocode(city)?.wikidata }.getOrNull()
            ?: return emptySet()
        guideCache[id]?.let { return it }
        val names = runCatching { com.riad.voyage.data.PlacesApi.guideNames(id) }.getOrDefault(emptySet())
        if (names.isNotEmpty()) guideCache[id] = names
        return names
    }

    /** Classe les lieux : cités par le guide + notoriété (nombre de Wikipédias), puis distance. */
    private suspend fun rank(
        items: List<com.riad.voyage.data.PointOfInterest>,
        guide: Set<String>,
    ): List<com.riad.voyage.data.PointOfInterest> {
        val ids = items.mapNotNull { it.wikidata }.take(150)
        val counts = if (ids.isEmpty()) emptyMap() else
            runCatching { com.riad.voyage.data.PlacesApi.sitelinkCounts(ids) }.getOrDefault(emptyMap())
        return items.map { p ->
            p.copy(
                sitelinks = p.wikidata?.let { counts[it] } ?: 0,
                inGuide = guide.isNotEmpty() && com.riad.voyage.data.PlacesParser.matchesGuide(p.name, guide),
            )
        }.sortedWith(
            compareByDescending<com.riad.voyage.data.PointOfInterest> { it.score }
                .thenByDescending { it.wikipedia != null }
                .thenByDescending { it.website != null }
                .thenBy { it.distanceM },
        )
    }

    suspend fun wikiSummary(tag: String): com.riad.voyage.data.WikiSummary? {
        if (wikiCache.containsKey(tag)) return wikiCache[tag]
        val summary = com.riad.voyage.data.PlacesApi.wikiSummary(tag)
        wikiCache[tag] = summary
        return summary
    }
    fun clearMessage() { _message.value = null }

    // ---------- Vols ----------

    suspend fun suggestPlaces(term: String): List<Place> {
        if (term.trim().length < 2) return emptyList()
        val online = runCatching { api().searchPlaces(term.trim()) }.getOrDefault(emptyList())
        return online.ifEmpty { PopularPlaces.filter(term) }
    }

    /** Suggestions de villes uniquement (pas d'aéroports) pour l'onglet Sorties. */
    suspend fun suggestCities(term: String): List<Place> {
        val all = suggestPlaces(term)
        return all.filter { !it.isAirport }.ifEmpty { all }
    }

    fun updateForm(transform: (FlightForm) -> FlightForm) {
        _form.update { f ->
            val n = transform(f)
            // Le retour ne peut pas précéder l'aller.
            if (n.returnDate.isBefore(n.departure)) n.copy(returnDate = n.departure.plusDays(7)) else n
        }
    }

    fun swapPlaces() = updateForm { it.copy(origin = it.destination, destination = it.origin) }

    fun search() {
        val f = _form.value
        val origin = f.origin
        val destination = f.destination
        if (origin == null || destination == null) {
            _message.value = "Choisissez le départ et la destination dans la liste proposée."
            return
        }
        if (origin.code == destination.code) {
            _message.value = "Le départ et la destination sont identiques."
            return
        }
        recentStore.add(RecentSearch(origin, destination, f.departure.toString(), if (f.roundTrip) f.returnDate.toString() else null, f.adults))
        _recents.value = recentStore.all()
        _tab.value = Tab.FLIGHTS
        val s = _settings.value
        _results.update {
            ResultsState(visible = true, loading = true, query = f, demo = s.isDemo, currency = s.currency, sort = it.sort)
        }
        viewModelScope.launch {
            try {
                val (flights, calendar) = if (s.isDemo) {
                    DemoData.flights(
                        origin.code, destination.code, f.departure, if (f.roundTrip) f.returnDate else null,
                        f.flexibleMonth, f.directOnly, f.adults,
                    ) to DemoData.calendar(origin.code, destination.code, YearMonth.from(f.departure))
                } else coroutineScope {
                    val api = api()
                    val flights = async {
                        api.pricesForDates(origin.code, destination.code, f.departureParam, f.returnParam, f.directOnly)
                    }
                    val calendar = async {
                        runCatching {
                            api.calendar(origin.code, destination.code, YearMonth.from(f.departure), f.directOnly)
                        }.getOrDefault(emptyList())
                    }
                    flights.await() to calendar.await().filter { !it.date.isBefore(LocalDate.now()) }
                }
                _results.update { if (it.query == f) it.copy(loading = false, flights = flights, calendar = calendar) else it }
            } catch (e: Exception) {
                _results.update { if (it.query == f) it.copy(loading = false, error = TravelpayoutsApi.userMessage(e)) else it }
            }
        }
    }

    /** Relance une recherche récente (les dates passées sont décalées à dans deux semaines). */
    fun applyRecent(r: RecentSearch) {
        val today = LocalDate.now()
        var dep = runCatching { LocalDate.parse(r.departure) }.getOrDefault(today.plusDays(14))
        var ret = r.returnDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (dep.isBefore(today)) {
            val stay = ret?.let { ChronoUnit.DAYS.between(dep, it) } ?: 7
            dep = today.plusDays(14)
            ret = ret?.let { dep.plusDays(stay) }
        }
        updateForm {
            it.copy(
                origin = r.origin, destination = r.destination, departure = dep,
                returnDate = ret ?: dep.plusDays(7), roundTrip = ret != null, flexibleMonth = false, adults = r.adults,
            )
        }
        search()
    }

    /** Choisit une destination depuis l'accueil et ouvre le formulaire de vol. */
    fun pickDestination(p: Place) {
        updateForm { it.copy(destination = p) }
        _results.update { it.copy(visible = false) }
        _tab.value = Tab.FLIGHTS
    }

    fun pickCalendarDay(date: LocalDate) {
        updateForm { f ->
            val stay = ChronoUnit.DAYS.between(f.departure, f.returnDate).coerceAtLeast(1)
            f.copy(departure = date, returnDate = date.plusDays(stay), flexibleMonth = false)
        }
        search()
    }

    fun setSort(sort: Sort) = _results.update { it.copy(sort = sort) }
    fun closeResults() = _results.update { it.copy(visible = false) }

    fun bookingUrl(flight: Flight): String {
        val marker = _settings.value.marker
        return if (flight.link.startsWith("http")) {
            if (marker.isBlank()) flight.link else TravelpayoutsApi.bookingUrl(flight.link, marker)
        } else TravelpayoutsApi.bookingUrl(flight.link, marker)
    }

    fun siteSearchUrl(): String? {
        val q = _results.value.query ?: return null
        val o = q.origin ?: return null
        val d = q.destination ?: return null
        return TravelpayoutsApi.searchUrl(
            o.code, d.code, q.departure, if (q.roundTrip) q.returnDate else null, q.adults, _settings.value.marker,
        )
    }

    // ---------- Vol + sorties ----------

    /** Ouvre les sorties de la ville de destination. */
    fun activitiesAtDestination() {
        val q = _results.value.query ?: _form.value
        q.destination?.let { _activityCity.value = it.name; _activityPlace.value = it }
        selectTab(Tab.ACTIVITIES)
    }

    // ---------- Alertes ----------

    fun followCurrentSearch() {
        val r = _results.value
        val q = r.query ?: return
        val o = q.origin ?: return
        val d = q.destination ?: return
        val id = listOf(o.code, d.code, q.departureParam, q.returnParam ?: "-", q.directOnly).joinToString("_")
        if (_alerts.value.any { it.id == id }) {
            _message.value = "Ce trajet est déjà suivi."
            return
        }
        val cheapest = if (r.demo) null else r.flights.minOfOrNull { it.price }
        alertStore.upsert(
            PriceAlert(
                id = id, origin = o.code, originName = o.name, destination = d.code, destinationName = d.name,
                departure = q.departureParam, returnDate = q.returnParam, directOnly = q.directOnly,
                lastPrice = cheapest, currency = _settings.value.currency,
                lastChecked = if (cheapest != null) System.currentTimeMillis() else 0L,
            ),
        )
        _alerts.value = alertStore.all()
        _message.value = if (r.demo) {
            "Trajet suivi. Ajoutez votre clé API dans Réglages pour activer la surveillance."
        } else "Trajet suivi : vous serez prévenu si le prix baisse."
    }

    fun removeAlert(id: String) {
        alertStore.remove(id)
        _alerts.value = alertStore.all()
    }

    fun checkAlertsNow() {
        if (_settings.value.isDemo) {
            _message.value = "Ajoutez votre clé API dans Réglages pour vérifier les prix."
            return
        }
        if (_checking.value) return
        _checking.value = true
        viewModelScope.launch {
            try {
                val drops = AlertChecker.checkAll(getApplication())
                _message.value = if (drops.isEmpty()) "Prix vérifiés : pas de baisse pour le moment." else
                    drops.joinToString("\n") {
                        "${it.alert.originName} → ${it.alert.destinationName} : " +
                            "${formatPrice(it.newPrice, it.alert.currency)} (avant ${formatPrice(it.oldPrice, it.alert.currency)})"
                    }
            } catch (e: Exception) {
                _message.value = TravelpayoutsApi.userMessage(e)
            } finally {
                _alerts.value = alertStore.all()
                _checking.value = false
            }
        }
    }

    // ---------- Réglages ----------

    fun setQuotaLimit(src: com.riad.voyage.data.RatingSource, limit: Int) {
        quotas.setLimit(src, limit)
        placesCache.clear()
    }

    fun saveSettings(s: Settings) {
        placesCache.clear()
        settingsStore.save(s)
        _settings.value = settingsStore.load()
        testKey()
    }

    /** Vérifie la clé avec une petite recherche réelle (Paris → Londres le mois prochain). */
    fun testKey() {
        val s = _settings.value
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
