package com.riad.voyage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riad.voyage.alerts.AlertChecker
import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.AlertStore
import com.riad.voyage.data.Article
import com.riad.voyage.data.ArticleCard
import com.riad.voyage.data.Blog
import com.riad.voyage.data.BlogCity
import com.riad.voyage.data.BlogFilter
import com.riad.voyage.data.BudgetCalculator
import com.riad.voyage.data.BudgetInput
import com.riad.voyage.data.BudgetItem
import com.riad.voyage.data.Chosen
import com.riad.voyage.data.DayPrice
import com.riad.voyage.data.DayWeather
import com.riad.voyage.data.DemoData
import com.riad.voyage.data.Experience
import com.riad.voyage.data.ExperienceBuilder
import com.riad.voyage.data.Flight
import com.riad.voyage.data.Fx
import com.riad.voyage.data.GeoPoint
import com.riad.voyage.data.GuideParser
import com.riad.voyage.data.HotelBooking
import com.riad.voyage.data.HotelBookingResult
import com.riad.voyage.data.HotelDetail
import com.riad.voyage.data.HotelOffer
import com.riad.voyage.data.HotelReview
import com.riad.voyage.data.LiteApi
import com.riad.voyage.data.LiteApiMore
import com.riad.voyage.data.LitePayment
import com.riad.voyage.data.NotablePlaces
import com.riad.voyage.data.PendingBooking
import com.riad.voyage.data.PendingBookingStore
import com.riad.voyage.data.Place
import com.riad.voyage.data.PlacesApi
import com.riad.voyage.data.PlacesParser
import com.riad.voyage.data.PlannedActivity
import com.riad.voyage.data.PointOfInterest
import com.riad.voyage.data.PopularPlaces
import com.riad.voyage.data.Prebook
import com.riad.voyage.data.PriceAlert
import com.riad.voyage.data.PriceText
import com.riad.voyage.data.Quiz
import com.riad.voyage.data.QuotaStore
import com.riad.voyage.data.RatingSource
import com.riad.voyage.data.RatingsAggregator
import com.riad.voyage.data.RecentSearch
import com.riad.voyage.data.RecentStore
import com.riad.voyage.data.ReviewSummary
import com.riad.voyage.data.RoomOffer
import com.riad.voyage.data.SavedArticle
import com.riad.voyage.data.SavedDay
import com.riad.voyage.data.SavedFlight
import com.riad.voyage.data.SavedPlace
import com.riad.voyage.data.SavedStep
import com.riad.voyage.data.Settings
import com.riad.voyage.data.SettingsStore
import com.riad.voyage.data.TravelpayoutsApi
import com.riad.voyage.data.Trip
import com.riad.voyage.data.TripStore
import com.riad.voyage.data.WeatherApi
import com.riad.voyage.data.WikiArticles
import com.riad.voyage.data.WikiSummary
import com.riad.voyage.data.WikivoyageGuide
import com.riad.voyage.data.parseDateTime
import com.riad.voyage.ui.formatPrice
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class Tab(val label: String, val inBottomBar: Boolean = true) {
    HOME("Accueil"), FLIGHTS("Vols"), HOTELS("Hôtels"), ACTIVITIES("Sorties"), EXPERIENCE("Expérience"), BLOG("Blogue"),
    ALERTS("Alertes", inBottomBar = false), TRIPS("Mes voyages", inBottomBar = false), BUDGET("Budget", inBottomBar = false),
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
    val items: List<PointOfInterest> = emptyList(),
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

enum class HotelSort(val label: String) { BEST("Recommandés"), PRICE("Prix"), RATING("Note"), DISTANCE("Distance") }

data class HotelState(
    val city: String = "",
    val place: Place? = null,
    val checkIn: LocalDate = LocalDate.now().plusDays(14),
    val checkOut: LocalDate = LocalDate.now().plusDays(17),
    val adults: Int = 2,
    val loading: Boolean = false,
    val error: String? = null,
    val offers: List<HotelOffer> = emptyList(),
    val searched: Boolean = false,
    val sort: HotelSort = HotelSort.BEST,
    val minStars: Int = 0,
    val minRating: Double = 0.0,
    val freeCancelOnly: Boolean = false,
    val breakfastOnly: Boolean = false,
    val detail: HotelDetailState? = null,
    val aiQuery: String = "",                      // description libre : « calme avec piscine… »
) {
    /** Hôtels après filtres, dans l'ordre choisi. */
    val visible: List<HotelOffer>
        get() = sorted.filter { o ->
            (o.stars ?: 0) >= minStars && (o.rating ?: 0.0) >= minRating &&
                (!freeCancelOnly || o.refundable) &&
                (!breakfastOnly || (o.board ?: "").lowercase().let { "break" in it || "déj" in it || "dej" in it || "bed and" in it })
        }
    val nights get() = HotelBooking.nights(checkIn, checkOut)
    val sorted: List<HotelOffer>
        get() = when (sort) {
            HotelSort.BEST -> if (offers.any { it.rank != null }) offers.sortedBy { it.rank ?: Int.MAX_VALUE }
                else offers.sortedByDescending { it.score }
            HotelSort.PRICE -> offers.sortedBy { it.totalPrice ?: Double.MAX_VALUE }
            HotelSort.RATING -> offers.sortedByDescending { it.rating ?: 0.0 }
            HotelSort.DISTANCE -> offers.sortedBy { it.distanceM ?: Int.MAX_VALUE }
        }
}

/** Page d'un hôtel : fiche, avis, offres de chambres et réservation directe. */
data class HotelDetailState(
    val hotel: HotelOffer,
    val loading: Boolean = true,
    val info: HotelDetail? = null,
    val reviews: List<HotelReview> = emptyList(),
    val summary: ReviewSummary? = null,
    val offers: List<RoomOffer> = emptyList(),
    val error: String? = null,
    val booking: RoomOffer? = null,     // offre en cours de réservation
    val prebook: Prebook? = null,
    val bookingBusy: Boolean = false,
    val bookingError: String? = null,
    val confirmed: HotelBookingResult? = null,
    val paymentHtml: String? = null,                          // page de paiement sécurisé affichée
    val guest: List<String> = emptyList(),                    // prénom, nom, e-mail
)

/** Calculateur de budget du séjour. */
data class BudgetState(
    val input: BudgetInput,
    val overrides: Map<BudgetItem, Double> = emptyMap(),
    val targetEur: Double? = null,
    val rates: Map<String, Double> = emptyMap(),
    val from: Tab = Tab.HOME,
) {
    val lines get() = BudgetCalculator.compute(input, overrides)
    val totalEur get() = lines.sumOf { it.amountEur }
}

data class BlogState(
    val city: BlogCity? = null,
    val filter: BlogFilter = BlogFilter.ALL,
    val cards: List<ArticleCard> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val article: Article? = null,
    val articleLoading: Boolean = false,
    val reason: String? = null,       // « D'après votre vol », « D'après vos sorties »…
)

data class ExperienceState(
    val answers: Quiz.Answers = Quiz.Answers(),
    val step: Int = 0,
    val loading: Boolean = false,
    val stage: String = "",
    val result: Experience? = null,
    val error: String? = null,
    val previous: Set<String> = emptySet(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val pendingStore = PendingBookingStore(app)
    private val _pendingBooking = MutableStateFlow(pendingStore.load())
    /** Réservation payée en attente de confirmation (à finaliser). */
    val pendingBooking: StateFlow<PendingBooking?> = _pendingBooking.asStateFlow()
    private val alertStore = AlertStore(app)
    private val recentStore = RecentStore(app)
    val quotas = QuotaStore(app)
    private val tripStore = TripStore(app)

    private val _trips = MutableStateFlow(tripStore.all())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    /** Voyage ouvert dans « Mes voyages » (null = la liste) ; article gardé en cours de lecture. */
    private val _openTrip = MutableStateFlow<String?>(null)
    val openTrip: StateFlow<String?> = _openTrip.asStateFlow()
    private val _tripArticle = MutableStateFlow<Article?>(null)
    val tripArticle: StateFlow<Article?> = _tripArticle.asStateFlow()

    private val _recents = MutableStateFlow(recentStore.all())
    val recents: StateFlow<List<RecentSearch>> = _recents.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load().also { ActivityLinks.tiqetsPartner = it.tiqetsPartner })
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
    private val geoCache = mutableMapOf<String, GeoPoint>()
    private val placesCache = mutableMapOf<String, PlacesState>()

    private val wikiCache = mutableMapOf<String, WikiSummary?>()
    private val guideCache = mutableMapOf<String, Set<String>>()

    private val _experience = MutableStateFlow(ExperienceState())
    val experience: StateFlow<ExperienceState> = _experience.asStateFlow()
    private var experiencePools: Map<ActivityLinks.Category, List<PointOfInterest>> = emptyMap()
    private var experienceWeather: Map<LocalDate, DayWeather> = emptyMap()

    private val _hotels = MutableStateFlow(HotelState())
    val hotels: StateFlow<HotelState> = _hotels.asStateFlow()

    private val _budget = MutableStateFlow<BudgetState?>(null)
    val budget: StateFlow<BudgetState?> = _budget.asStateFlow()

    /** Vrai quand la personne est partie choisir un vol / hôtel / activité depuis le budget. */
    private val _budgetReturn = MutableStateFlow(false)
    val budgetReturn: StateFlow<Boolean> = _budgetReturn.asStateFlow()

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
        if (t == Tab.HOTELS && _hotels.value.city.isBlank()) {
            val dest = _form.value.destination ?: _activityPlace.value
            if (dest != null) _hotels.update { it.copy(city = dest.name, place = dest) }
        }
        _tab.value = t
        if (t == Tab.ACTIVITIES) searchActivities()
        if (t == Tab.BLOG && _blog.value.city == null) blogContext().firstOrNull()?.let { (c, why) -> openBlogCity(c, why) }
    }

    /** Efface l'historique des recherches de vols. */
    fun clearRecents() {
        recentStore.clear()
        _recents.value = emptyList()
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
        _places.value = PlacesState(loading = true, stage = t("Recherche de la ville…"), city = city, category = category)
        placesJob = viewModelScope.launch {
            val geo = resolveCity(city, chosen)
            if (geo == null) {
                _places.value = PlacesState(
                    error = t("Ville introuvable ou connexion indisponible. Choisissez la ville dans la liste proposée."),
                    city = city, category = category,
                )
                return@launch
            }

            _places.value = _places.value.copy(stage = t("Recherche des meilleurs lieux…"))
            val db = NotablePlaces

            // 1) Musées, monuments, attractions, parcs, plages : top 30 de la ville par notoriété.
            if (db.supports(category)) {
                val top = attempt(12_000) { db.search(geo, category) }?.getOrNull() ?: emptyList()
                if (top.size >= 3) {
                    publish(key, PlacesState(items = top, city = city, category = category, source = SOURCE_FAME))
                    return@launch
                }
            }

            // 2) Restaurants, cafés, bars, sorties, shopping : sélection du guide Wikivoyage de la ville.
            val guidePicks = if (!db.supports(category)) {
                val listings = attempt(15_000) { WikivoyageGuide.listings(city, geo.wikidata) }?.getOrNull() ?: emptyList()
                GuideParser.select(listings, category).take(30)
                    .map { GuideParser.toPoi(it, category, geo) }
            } else emptyList()
            // 2 bis) Notes Google + Tripadvisor + popularité Foursquare, chacune dans sa limite gratuite.
            val agg = RatingsAggregator
            val st = _settings.value
            val hasRatingKeys = st.googleKey.isNotBlank() || st.tripadvisorKey.isNotBlank() || st.foursquareKey.isNotBlank()
            var quotaNotice: String? = null
            if (hasRatingKeys && agg.supports(category)) {
                _places.value = _places.value.copy(stage = t("Récupération des notes…"))
                val outcome = attempt(20_000) { agg.search(st, quotas, city, geo, category, guidePicks) }?.getOrNull()
                if (outcome != null && outcome.skipped.isNotEmpty()) {
                    quotaNotice = if (outcome.usedSources.isNotEmpty()) tf("Limite gratuite atteinte pour {0} : relais pris par {1}.", outcome.skipped.joinToString(", ") { it.label }, outcome.usedSources.joinToString { it.label })
                        else tf("Limite gratuite atteinte pour {0}.", outcome.skipped.joinToString(", ") { it.label })
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
            _places.value = _places.value.copy(stage = t("Recherche sur la carte…"))
            var liveError: String? = null
            val live = withTimeoutOrNull(20_000) {
                runCatching { PlacesApi.search(geo, category) }
                    .onFailure { liveError = TravelpayoutsApi.userMessage(it) }
                    .getOrNull()
            }
            val merged = (guidePicks + (live ?: emptyList()).sortedWith(
                compareByDescending<PointOfInterest> { it.notable }
                    .thenByDescending { it.website != null }
                    .thenBy { it.distanceM },
            )).distinctBy { it.name.lowercase() }.take(30)
            if (merged.isEmpty()) {
                _places.value = PlacesState(
                    error = when {
                        live != null -> t("Aucun lieu trouvé pour cette ville.")
                        liveError != null -> tf("Recherche impossible : {0}", liveError ?: "")
                        else -> t("Les serveurs de carte ne répondent pas (plus de 20 s). Réessayez dans un instant.")
                    },
                    city = city, category = category,
                )
                return@launch
            }
            publish(key, PlacesState(items = merged, city = city, category = category,
                guideFound = guidePicks.isNotEmpty(), source = SOURCE_MAP))
        }
    }

    // ---------- Budget du séjour ----------

    private fun travellersFor(c: Quiz.Company) = when (c) {
        Quiz.Company.SOLO -> 1
        Quiz.Company.COUPLE -> 2
        Quiz.Company.FRIENDS -> 3
        Quiz.Company.FAMILY -> 4
    }

    private fun countryOf(city: String, fallback: String = ""): String =
        fallback.ifBlank {
            Blog.city(city)?.country ?: PopularPlaces.all.firstOrNull { it.name.equals(city, true) }?.country.orEmpty()
        }

    /** Reconstruit le budget à partir des choix actuels (taux de change en paramètre). */
    private var budgetBuilder: ((Map<String, Double>) -> BudgetInput)? = null

    private fun openBudget(build: (Map<String, Double>) -> BudgetInput) {
        val from = if (_tab.value == Tab.BUDGET) _budget.value?.from ?: Tab.HOME else _tab.value
        budgetBuilder = build
        val rates = _budget.value?.rates.orEmpty()
        _budget.value = BudgetState(input = build(rates), rates = rates, from = from)
        _tab.value = Tab.BUDGET
        viewModelScope.launch {
            val fresh = attempt(8_000) { Fx.rates() }?.getOrNull().orEmpty()
            if (fresh.isNotEmpty()) { _budget.update { it?.copy(rates = fresh) }; refreshBudget() }
        }
    }

    /** Recalcule les postes « choisis » (vols, hôtel, activités) en gardant les réglages de la personne. */
    private fun refreshBudget() {
        val build = budgetBuilder ?: return
        _budget.update { b ->
            b?.let {
                val n = build(it.rates)
                it.copy(input = n.copy(travellers = it.input.travellers, nights = it.input.nights, level = it.input.level,
                    mobility = it.input.mobility, includeDaily = it.input.includeDaily))
            }
        }
    }

    /** Prix d'un libellé « … · 540 € » (dernier segment), converti en euros. */
    private fun priceOfLabel(label: String, rates: Map<String, Double>): Double? =
        PriceText.parse(label.substringAfterLast(" · "))
            ?.let { (amount, cur) -> Fx.toEur(amount, cur, rates) }

    /** Ce qui a été choisi dans un voyage : vols gardés, hôtel (réservé sinon favori), activités. */
    private fun choicesOf(trip: Trip?, rates: Map<String, Double>): Triple<List<Chosen>, Pair<Chosen, Boolean>?, List<PlannedActivity>> {
        if (trip == null) return Triple(emptyList(), null, emptyList())
        val flights = trip.flights.map { f ->
            Chosen("${f.origin} → ${f.destination}", Fx.toEur(f.price.toDouble(), f.currency, rates))
        }
        val hotels = trip.places.filter { it.kind.startsWith("Hôtel") }
        val hotelPlace = hotels.firstOrNull { it.kind.startsWith("Hôtel réservé") } ?: hotels.lastOrNull { priceOfLabel(it.kind, rates) != null }
        val hotel = hotelPlace?.let { h ->
            priceOfLabel(h.kind, rates)?.let { Chosen(h.name, it) to h.kind.startsWith("Hôtel réservé") }
        }
        val acts = trip.program.flatMap { d -> d.steps }.map { st ->
            PlannedActivity(st.place.name, st.place.kind, categoryOfEmoji(st.emoji))
        } + trip.places.filterNot { it.kind.startsWith("Hôtel") }.map { PlannedActivity(it.name, it.kind) }
        return Triple(flights, hotel, acts)
    }

    /** Budget d'un voyage enregistré, à partir de ce qui y a été choisi. */
    fun openBudgetForTrip(trip0: Trip) = openBudget { rates ->
        val trip = _trips.value.firstOrNull { it.id == trip0.id } ?: trip0
        val nights = runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(trip.startDate!!), LocalDate.parse(trip.endDate!!)).toInt()
        }.getOrNull()?.coerceAtLeast(0) ?: 3
        val a = _experience.value.answers
        budgetFor(trip.city, countryOf(trip.city, trip.country), trip, rates, nights,
            if (a.city.equals(trip.city, true)) travellersFor(a.company) else 2)
    }

    /** Budget du programme Expérience affiché (+ vols et hôtel choisis pour la même ville). */
    fun openBudgetForExperience() = openBudget { rates ->
        val st = _experience.value
        val a = st.answers
        val city = st.result?.city ?: a.city
        val program = st.result?.days.orEmpty().flatMap { d -> d.steps.map { PlannedActivity(it.place.name, it.place.kind, it.category) } }
        budgetFor(city, countryOf(city, a.place?.country.orEmpty()), _trips.value.firstOrNull { it.city.equals(city, true) }, rates,
            if (a.duration.halfDay) 0 else (a.duration.days - 1).coerceAtLeast(0), travellersFor(a.company), program)
    }

    private fun budgetFor(
        city: String, country: String, trip: Trip?, rates: Map<String, Double>, nights: Int, travellers: Int,
        program: List<PlannedActivity> = emptyList(),
    ): BudgetInput {
        val (flights, hotel, saved) = choicesOf(trip, rates)
        val a = _experience.value.answers
        return BudgetInput(
            city = city, country = country, travellers = travellers, nights = nights, level = a.budget, mobility = a.mobility,
            flights = flights, hotel = hotel?.first, hotelBooked = hotel?.second == true, activities = program + saved,
        )
    }

    /** Depuis le budget, aller choisir ce qui manque (vol, hôtel, activités) pour la même ville. */
    fun chooseForBudget(item: BudgetItem) {
        val b = _budget.value ?: return
        val city = b.input.city
        when (item) {
            BudgetItem.HOTEL -> {
                val trip = _trips.value.firstOrNull { it.city.equals(city, true) }
                openHotels(city, null, trip?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    trip?.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, b.input.travellers)
            }
            BudgetItem.ACTIVITIES -> {
                _activityCity.value = city
                _activityPlace.value = PopularPlaces.all.firstOrNull { it.name.equals(city, true) }
                selectTab(Tab.ACTIVITIES)
            }
            else -> selectTab(Tab.FLIGHTS)
        }
        _budgetReturn.value = true
    }

    /** Revenir au budget (recalculé avec les nouveaux choix). */
    fun backToBudget() {
        _budgetReturn.value = false
        if (_budget.value == null) return
        refreshBudget()
        _tab.value = Tab.BUDGET
    }

    private fun categoryOfEmoji(e: String) = ActivityLinks.Category.entries.firstOrNull { it.emoji == e }

    fun updateBudgetInput(transform: (BudgetInput) -> BudgetInput) =
        _budget.update { it?.copy(input = transform(it.input)) }

    fun setBudgetOverride(item: BudgetItem, eur: Double?) = _budget.update { b ->
        b?.copy(overrides = if (eur == null) b.overrides - item else b.overrides + (item to eur))
    }

    fun setBudgetTarget(eur: Double?) = _budget.update { it?.copy(targetEur = eur) }

    fun closeBudget() { _budgetReturn.value = false; val from = _budget.value?.from ?: Tab.HOME; _tab.value = from }

    // ---------- Hôtels ----------

    /** Ouvre les hôtels pour une ville et des dates (depuis un vol, une expérience ou un voyage). */
    fun openHotels(city: String?, place: Place?, checkIn: LocalDate?, checkOut: LocalDate?, adults: Int? = null) {
        _hotels.update { h ->
            val ci = checkIn ?: h.checkIn
            val co = (checkOut ?: ci.plusDays(h.nights)).let { if (it.isAfter(ci)) it else ci.plusDays(1) }
            val sameCity = city.isNullOrBlank() || city.equals(h.city, ignoreCase = true)
            h.copy(
                city = city?.takeIf { it.isNotBlank() } ?: h.city, place = place ?: h.place.takeIf { sameCity },
                checkIn = ci, checkOut = co, adults = adults ?: h.adults,
                offers = if (sameCity && ci == h.checkIn && co == h.checkOut) h.offers else emptyList(),
                searched = sameCity && ci == h.checkIn && co == h.checkOut && h.searched,
            )
        }
        _tab.value = Tab.HOTELS
        if (!_hotels.value.searched && _hotels.value.city.isNotBlank() && _settings.value.liteApiKey.isNotBlank()) searchHotels()
    }

    fun updateHotels(transform: (HotelState) -> HotelState) = _hotels.update { h ->
        val n = transform(h)
        val fixed = if (!n.checkOut.isAfter(n.checkIn)) n.copy(checkOut = n.checkIn.plusDays(1)) else n
        if (fixed.city != h.city || fixed.checkIn != h.checkIn || fixed.checkOut != h.checkOut || fixed.adults != h.adults)
            fixed.copy(searched = false, offers = emptyList(), error = null) else fixed
    }

    fun searchHotels() {
        val h = _hotels.value
        val key = _settings.value.liteApiKey
        if (h.city.isBlank()) return
        if (key.isBlank()) { _hotels.update { it.copy(searched = true) }; return }
        _hotels.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val geo = resolveCity(h.city, h.place?.takeIf { it.name.equals(h.city, ignoreCase = true) })
            if (geo == null) {
                _hotels.update { it.copy(loading = false, searched = true, error = t("Ville introuvable : choisissez-la dans la liste proposée.")) }
                return@launch
            }
            val nationality = java.util.Locale.getDefault().country.ifBlank { "FR" }
            val result = withTimeoutOrNull(35_000) {
                runCatching {
                    if (h.aiQuery.isNotBlank())
                        LiteApi.aiSearch(key, h.aiQuery, h.city, geo, h.checkIn, h.checkOut, h.adults, _settings.value.currency, nationality)
                    else LiteApi.search(key, geo, h.checkIn, h.checkOut, h.adults, _settings.value.currency, nationality)
                }
            }
            _hotels.update {
                when {
                    result == null -> it.copy(loading = false, searched = true, error = t("Le service d'hôtels ne répond pas. Réessayez dans un instant."))
                    result.isFailure -> it.copy(loading = false, searched = true, error = TravelpayoutsApi.userMessage(result.exceptionOrNull()!!))
                    else -> it.copy(loading = false, searched = true, offers = result.getOrDefault(emptyList()), sort = HotelSort.BEST,
                        error = if (result.getOrDefault(emptyList()).isEmpty()) t("Aucun hôtel disponible pour ces dates.") else null)
                }
            }
        }
    }

    private val nationality get() = java.util.Locale.getDefault().country.ifBlank { "FR" }

    /** Ouvre la page d'un hôtel : fiche, avis et toutes les offres, chargés en parallèle. */
    fun openHotelDetail(o: HotelOffer) {
        val key = _settings.value.liteApiKey
        val h = _hotels.value
        _hotels.update { it.copy(detail = HotelDetailState(o)) }
        if (key.isBlank()) { _hotels.update { it.copy(detail = it.detail?.copy(loading = false)) }; return }
        viewModelScope.launch {
            val more = LiteApiMore
            val info = async { attempt(20_000) { more.detail(key, o.id) }?.getOrNull() }
            val reviews = async { attempt(20_000) { more.reviews(key, o.id) }?.getOrNull() }
            val offers = async {
                attempt(25_000) { more.offers(key, o.id, h.checkIn, h.checkOut, h.adults, _settings.value.currency, nationality) }
            }
            val off = offers.await()
            val rev = reviews.await()
            _hotels.update { st ->
                if (st.detail?.hotel?.id != o.id) st else st.copy(
                    detail = st.detail!!.copy(
                        loading = false, info = info.await(), reviews = rev?.first.orEmpty(), summary = rev?.second,
                        offers = off?.getOrNull().orEmpty(),
                        error = off?.exceptionOrNull()?.let { TravelpayoutsApi.userMessage(it) },
                    ),
                )
            }
        }
    }

    fun closeHotelDetail() = _hotels.update { it.copy(detail = null) }

    /** Étape 1 de la réservation directe : LiteAPI confirme la disponibilité et le prix exact. */
    fun startHotelBooking(offer: RoomOffer) {
        val key = _settings.value.liteApiKey
        // Même chambre déjà pré-réservée : on la réutilise (pas de seconde pré-réservation ni de doublon).
        _hotels.value.detail?.let { d ->
            if (d.prebook != null && d.booking?.offerId == offer.offerId && d.confirmed == null) {
                _hotels.update { it.copy(detail = d.copy(booking = offer, bookingError = null)) }
                return
            }
        }
        _hotels.update { it.copy(detail = it.detail?.copy(booking = offer, prebook = null, bookingBusy = true, bookingError = null, confirmed = null)) }
        viewModelScope.launch {
            val r = attempt(25_000) { LiteApiMore.prebook(key, offer.offerId) }
            _hotels.update { st ->
                val d = st.detail ?: return@update st
                st.copy(detail = when {
                    r == null -> d.copy(bookingBusy = false, bookingError = t("Le service d'hôtels ne répond pas. Réessayez dans un instant."))
                    r.isFailure -> d.copy(bookingBusy = false, bookingError = TravelpayoutsApi.userMessage(r.exceptionOrNull()!!))
                    r.getOrNull() == null -> d.copy(bookingBusy = false, bookingError = t("Cette offre n'est plus disponible. Choisissez-en une autre."))
                    else -> d.copy(bookingBusy = false, prebook = r.getOrNull())
                })
            }
        }
    }

    /**
     * Étape 2 : coordonnées du voyageur, puis paiement sécurisé LiteAPI (carte saisie sur la page
     * LiteAPI, jamais dans l'app). En mode test sans page de paiement : paiement fictif.
     */
    fun confirmHotelBooking(firstName: String, lastName: String, email: String) {
        val key = _settings.value.liteApiKey
        val d = _hotels.value.detail ?: return
        val pre = d.prebook ?: return
        val guest = listOf(firstName.trim(), lastName.trim(), email.trim())
        val sandbox = LitePayment.isSandbox(key)
        when {
            pre.secretKey != null && pre.transactionId != null -> _hotels.update {
                it.copy(detail = it.detail?.copy(guest = guest, bookingError = null,
                    paymentHtml = LitePayment.html(pre.secretKey, sandbox)))
            }
            sandbox -> bookHotel(guest, null)
            else -> _hotels.update {
                it.copy(detail = it.detail?.copy(bookingError = t("Le paiement sécurisé n'est pas disponible pour cette offre. Réservez chez un partenaire.")))
            }
        }
    }

    /** Retour de la page de paiement : paiement accepté → réservation. */
    fun hotelPaymentDone() {
        val d = _hotels.value.detail ?: return
        _hotels.update { it.copy(detail = it.detail?.copy(paymentHtml = null)) }
        bookHotel(d.guest, d.prebook?.transactionId)
    }

    fun hotelPaymentCancelled() = _hotels.update { it.copy(detail = it.detail?.copy(paymentHtml = null)) }

    private fun bookHotel(guest: List<String>, transactionId: String?) {
        val d = _hotels.value.detail ?: return
        val pre = d.prebook ?: return
        if (guest.size < 3) return
        val h = _hotels.value
        val p = PendingBooking(
            pre.prebookId, transactionId.orEmpty(), guest, h.city, h.checkIn.toString(), h.checkOut.toString(),
            d.hotel.name, d.hotel.lat ?: 0.0, d.hotel.lon ?: 0.0, pre.price ?: d.booking?.price, pre.currency ?: d.booking?.currency,
        )
        // Paiement fait : on garde une trace tant que la réservation n'est pas confirmée.
        if (transactionId != null) { pendingStore.save(p); _pendingBooking.value = p }
        runBooking(p)
    }

    /** Finalise une réservation déjà payée (même paiement, aucun nouveau débit). */
    fun retryPendingBooking() { _pendingBooking.value?.let { runBooking(it) } }

    private fun runBooking(p: PendingBooking) {
        val key = _settings.value.liteApiKey
        val paid = p.transactionId.isNotBlank()
        val (firstName, lastName, email) = p.guest
        _hotels.update { it.copy(detail = it.detail?.copy(bookingBusy = true, bookingError = null)) }
        viewModelScope.launch {
            val r = attempt(40_000) { LiteApiMore.book(key, p.prebookId, firstName, lastName, email, p.transactionId.ifBlank { null }) }
            val ok = r?.getOrNull()
            val failure = when {
                ok != null -> null
                paid -> t("Paiement reçu, mais la réservation n'est pas encore confirmée. Réessayez : aucun nouveau paiement ne sera demandé.")
                r == null -> t("Le service d'hôtels ne répond pas. Réessayez dans un instant.")
                else -> r.exceptionOrNull()?.let { TravelpayoutsApi.userMessage(it) } ?: t("La réservation n'a pas abouti.")
            }
            _hotels.update { st -> st.copy(detail = st.detail?.copy(bookingBusy = false, confirmed = ok, bookingError = failure)) }
            if (_hotels.value.detail == null && failure != null) _message.value = failure
            if (ok == null) return@launch
            pendingStore.clear()
            _pendingBooking.value = null
            val trip = tripFor(p.city)
            val price = (ok.price ?: p.price)?.let { com.riad.voyage.ui.formatPrice(it.toInt(), ok.currency ?: p.currency ?: "EUR") }
            val label = listOfNotNull("Hôtel réservé", "Réf. " + (ok.confirmation ?: ok.bookingId), price).joinToString(" · ")
            saveTrip(
                trip.copy(
                    places = trip.places + SavedPlace(p.hotelName, label, p.lat, p.lon, p.lat != 0.0),
                    startDate = trip.startDate ?: p.checkIn, endDate = trip.endDate ?: p.checkOut,
                ),
                tf("Réservation ajoutée à votre voyage : {0}", t(p.city)),
            )
        }
    }

    /** Ferme la fenêtre ; la pré-réservation reste valable pour la même chambre (sauf si déjà confirmée). */
    fun cancelHotelBooking() = _hotels.update {
        it.copy(detail = it.detail?.let { d -> d.copy(booking = null, prebook = d.prebook.takeIf { d.confirmed == null }, bookingError = null, confirmed = null) })
    }

    fun saveHotel(o: HotelOffer) {
        val h = _hotels.value
        val price = o.totalPrice?.let { com.riad.voyage.ui.formatPrice(it.toInt(), o.currency) }.orEmpty()
        toggleFavorite(o.toPoi(price), h.city)
    }

    // ---------- Mes voyages ----------

    private fun refreshTrips() { _trips.value = tripStore.all(); refreshBudget() }

    /** Voyage d'une ville (créé s'il n'existe pas). */
    private fun tripFor(city: String, country: String = ""): Trip =
        _trips.value.firstOrNull { it.city.equals(city.trim(), ignoreCase = true) }
            ?: Trip(id = "trip-${System.currentTimeMillis()}", city = city.trim(), country = country)

    private fun saveTrip(t: Trip, message: String) {
        tripStore.upsert(t)
        refreshTrips()
        _message.value = message
    }

    fun saveFlight(f: Flight) {
        val q = _results.value.query ?: return
        val dest = q.destination ?: return
        val trip = tripFor(dest.name, dest.country)
        val saved = SavedFlight(
            q.origin?.name ?: f.origin, dest.name, f.departureAt, f.returnAt, f.price * q.adults,
            _results.value.currency, f.airline, bookingUrl(f),
        )
        if (trip.flights.any { it.url == saved.url }) { _message.value = t("Ce vol est déjà dans votre voyage."); return }
        saveTrip(
            trip.copy(
                flights = trip.flights + saved,
                startDate = trip.startDate ?: f.departureDate?.toString(),
                endDate = trip.endDate ?: f.returnAt?.let { parseDateTime(it)?.toLocalDate()?.toString() },
            ),
            tf("Vol ajouté à votre voyage : {0}", t(dest.name)),
        )
    }

    fun saveExperience() {
        val st = _experience.value
        val exp = st.result ?: return
        val trip = tripFor(exp.city, st.answers.place?.country.orEmpty())
        val program = exp.days.map { d ->
            SavedDay(d.title, d.weather?.label, d.steps.map { s ->
                SavedStep(s.time, t(s.moment), s.category.emoji, s.why, s.durationMin,
                    SavedPlace.of(s.place))
            })
        }
        val end = st.answers.startDate.plusDays((st.answers.duration.days - 1).toLong())
        saveTrip(
            trip.copy(programTitle = exp.title, program = program,
                startDate = trip.startDate ?: st.answers.startDate.toString(), endDate = trip.endDate ?: end.toString()),
            tf("Programme enregistré dans votre voyage : {0}", t(exp.city)),
        )
    }

    fun isFavorite(name: String) = _trips.value.any { tr -> tr.places.any { it.name.equals(name, ignoreCase = true) } }

    fun toggleFavorite(p: PointOfInterest, city: String) {
        val existing = _trips.value.firstOrNull { tr -> tr.places.any { it.name.equals(p.name, ignoreCase = true) } }
        if (existing != null) {
            tripStore.upsert(existing.copy(places = existing.places.filterNot { it.name.equals(p.name, ignoreCase = true) }))
            refreshTrips()
            return
        }
        val trip = tripFor(city)
        saveTrip(trip.copy(places = trip.places + SavedPlace.of(p)), tf("Ajouté à votre voyage : {0}", t(trip.city)))
    }

    fun saveArticle(a: Article) {
        val city = _blog.value.city?.name ?: _trips.value.firstOrNull()?.city ?: t("Mes lectures")
        val trip = tripFor(city)
        if (trip.articles.any { it.wiki == a.wiki }) { _message.value = t("Article déjà gardé."); return }
        saveTrip(trip.copy(articles = trip.articles + SavedArticle.of(a)),
            tf("Article gardé pour la lecture hors connexion : {0}", t(trip.city)))
    }

    fun openTrip(id: String?) { _openTrip.value = id; _tripArticle.value = null; _tab.value = Tab.TRIPS }
    fun readSavedArticle(a: SavedArticle?) { _tripArticle.value = a?.toArticle() }

    fun deleteTrip(id: String) {
        tripStore.delete(id)
        refreshTrips()
        _openTrip.value = null
    }

    fun removeFromTrip(id: String, kind: String, index: Int) {
        val trip = _trips.value.firstOrNull { it.id == id } ?: return
        val updated = when (kind) {
            "flight" -> trip.copy(flights = trip.flights.filterIndexed { i, _ -> i != index })
            "place" -> trip.copy(places = trip.places.filterIndexed { i, _ -> i != index })
            "article" -> trip.copy(articles = trip.articles.filterIndexed { i, _ -> i != index })
            "program" -> trip.copy(program = emptyList(), programTitle = null)
            else -> trip
        }
        tripStore.upsert(updated)
        refreshTrips()
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
        add(_experience.value.answers.place, _experience.value.answers.city, "D'après votre expérience")
        add(_results.value.query?.destination, null, "D'après votre vol")
        add(_form.value.destination, null, "D'après votre vol")
        add(_activityPlace.value, _activityCity.value, "D'après vos sorties")
        return out
    }

    fun openBlogCity(c: BlogCity, reason: String? = null) {
        val personal = blogInterestCategories().isNotEmpty()
        _blog.update {
            it.copy(city = c, reason = reason, article = null,
                filter = if (personal) BlogFilter.FOR_YOU else BlogFilter.ALL)
        }
        loadBlogCards()
    }

    /** Ville ou lieu choisi dans la barre de recherche du blogue. */
    fun openBlogPlace(p: Place) {
        viewModelScope.launch {
            val geo = resolveCity(p.name, p) ?: run {
                _blog.update { it.copy(error = t("Lieu introuvable.")) }
                return@launch
            }
            openBlogCity(Blog.city(p.name) ?: BlogCity(p.name, p.country, "fr:${p.name}", geo.lat, geo.lon))
        }
    }

    fun clearBlogCity() = _blog.update { BlogState() }

    fun setBlogFilter(f: BlogFilter) {
        _blog.update { it.copy(filter = f) }
        loadBlogCards()
    }

    /** Catégories qui intéressent la personne : envies de l'expérience, dernière catégorie de sorties. */
    private fun blogInterestCategories(): List<ActivityLinks.Category> {
        val fromExperience = _experience.value.answers.interests.mapNotNull { it.category }
        val fromActivities = listOf(_activityCategory.value).filter { NotablePlaces.supports(it) }
        return (fromExperience + fromActivities).filter { NotablePlaces.supports(it) }.distinct()
    }

    private fun loadBlogCards() {
        val st = _blog.value
        val city = st.city ?: return
        blogJob?.cancel()
        _blog.update { it.copy(loading = true, error = null, cards = emptyList()) }
        blogJob = viewModelScope.launch {
            val answers = _experience.value.answers
            val categories = if (st.filter == BlogFilter.FOR_YOU) blogInterestCategories()
                .ifEmpty { BlogFilter.ALL.categories } else st.filter.categories
            val cards = withTimeoutOrNull(15_000) {
                categories.flatMap { c ->
                    val tastes = ExperienceBuilder.tastesFor(answers, c)
                    runCatching { NotablePlaces.search(city.geo, c, limit = 20) }.getOrNull().orEmpty()
                        .filter { it.wikipedia != null }
                        .map { p ->
                            ArticleCard(
                                wiki = p.wikipedia!!, title = p.name, kind = p.kind, category = c, imageUrl = p.imageUrl,
                                fame = p.sitelinks, tasteMatch = ExperienceBuilder.tasteScore(p, tastes) > 0,
                            )
                        }
                }.distinctBy { it.wiki }
                    .sortedWith(compareByDescending<ArticleCard> { it.tasteMatch }.thenByDescending { it.fame })
                    .take(40)
            }
            val cityCard = ArticleCard(city.wiki, tf("Découvrir {0}", t(city.name)), tf("Ville · {0}", t(city.country)), null, null, 10_000)
            _blog.update {
                if (cards == null) it.copy(loading = false, error = t("Les articles ne se chargent pas. Vérifiez la connexion."), cards = listOf(cityCard))
                else it.copy(loading = false, cards = listOf(cityCard) + cards)
            }
        }
    }

    fun openArticle(tag: String) {
        _blog.update { it.copy(articleLoading = true, article = null, error = null) }
        viewModelScope.launch {
            val article = attempt(15_000) { WikiArticles.article(tag) }?.getOrNull()
            _blog.update {
                if (article == null) it.copy(articleLoading = false, error = t("Article indisponible pour le moment."))
                else it.copy(articleLoading = false, article = article)
            }
        }
    }

    /** « Lire l'article complet » depuis la fiche d'un lieu : ouvre le lecteur du Blogue. */
    fun readPlaceArticle(tag: String) {
        _tab.value = Tab.BLOG
        openArticle(tag)
    }

    fun closeArticle() = _blog.update { it.copy(article = null, articleLoading = false) }

    suspend fun searchArticles(q: String): List<Pair<String, String>> =
        runCatching { WikiArticles.search(q) }.getOrNull().orEmpty()

    // ---------- Expérience ----------

    fun updateAnswers(transform: (Quiz.Answers) -> Quiz.Answers) =
        _experience.update { it.copy(answers = transform(it.answers)) }

    fun quizStep(delta: Int) = _experience.update {
        val total = Quiz.steps(it.answers).size
        it.copy(step = (it.step + delta).coerceIn(0, total - 1), error = null)
    }

    fun editExperience() = _experience.update { it.copy(result = null, step = 0, error = null) }

    /** Charge les meilleurs lieux de chaque catégorie utile, puis compose l'expérience. */
    fun generateExperience() {
        val a = _experience.value.answers
        val city = a.city.trim()
        if (city.isBlank()) return
        _experience.update { it.copy(loading = true, stage = t("Recherche de la ville…"), error = null, result = null) }
        viewModelScope.launch {
            val geo = resolveCity(city, a.place?.takeIf { it.name.equals(city, ignoreCase = true) })
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
            _experience.update { it.copy(stage = tf("Sélection des meilleurs lieux de {0}…", t(city))) }
            val pools = coroutineScope {
                needed.map { c ->
                    val hint = ExperienceBuilder.searchHint(a, c)
                    async { c to (withTimeoutOrNull(25_000) { loadCategory(city, geo, c, hint) } ?: emptyList()) }
                }
                    .map { it.await() }.toMap()
            }
            // Analyse des lieux : description Wikipédia des meilleurs candidats, pour vérifier
            // qu'ils correspondent vraiment aux goûts (et non au seul nom).
            _experience.update { it.copy(stage = t("Analyse des lieux selon vos goûts…")) }
            val analysed = coroutineScope {
                pools.map { (c, list) ->
                    async {
                        c to list.mapIndexed { i, p ->
                            if (i >= 25 || p.description != null || p.wikipedia == null) p
                            else {
                                val summary = withTimeoutOrNull(6_000) { wikiSummary(p.wikipedia) }
                                if (summary != null) p.copy(description = summary.extract) else p
                            }
                        }
                    }
                }.map { it.await() }.toMap()
            }
            experiencePools = analysed
            _experience.update { it.copy(stage = t("Composition du programme…")) }
            experienceWeather = weatherJob.await()
            val exp = ExperienceBuilder.build(a, city, analysed, weather = experienceWeather)
            _experience.update {
                if (exp.days.isEmpty()) it.copy(loading = false, error = tf("Pas assez de lieux trouvés pour {0}. Essayez d'autres envies ou une grande ville proche.", t(city)))
                else it.copy(loading = false, result = exp, previous = emptySet())
            }
        }
    }

    /**
     * « Planifier mon séjour » depuis les résultats de vol : ville, dates et durée déjà remplies ;
     * les Sorties et le Blogue suivent la même destination.
     */
    fun planStayFromFlight(flight: Flight?) {
        val q = _results.value.query ?: _form.value
        val dest = q.destination ?: return
        val start = flight?.departureDate ?: q.departure
        val end = flight?.returnAt?.let { parseDateTime(it)?.toLocalDate() }
            ?: if (q.roundTrip && !q.flexibleMonth) q.returnDate else null
        val nights = end?.let { ChronoUnit.DAYS.between(start, it) } ?: 1
        // L'expérience couvre tout le séjour : du jour d'arrivée au jour du départ.
        val duration = if (end == null) Quiz.Duration.DAY
        else Quiz.Duration((nights + 1).toInt().coerceIn(1, Quiz.Duration.MAX_DAYS))
        updateAnswers { it.copy(city = dest.name, place = dest, startDate = start, duration = duration) }
        val firstOpen = Quiz.steps(_experience.value.answers).indexOf("company").coerceAtLeast(0)
        _experience.update { it.copy(step = firstOpen, result = null, error = null) }
        _activityCity.value = dest.name
        _activityPlace.value = dest
        _blog.update { BlogState() }
        _tab.value = Tab.EXPERIENCE
    }

    /** Lieux proposés pour remplacer une étape du programme. */
    fun stepAlternatives(dayIndex: Int, stepIndex: Int): List<Pair<ActivityLinks.Category, PointOfInterest>> {
        val st = _experience.value
        val exp = st.result ?: return emptyList()
        return ExperienceBuilder.alternatives(st.answers, exp, experiencePools, dayIndex, stepIndex)
    }

    /** Retire une étape (choice = null) ou la remplace par le lieu choisi ; les horaires sont recalculés. */
    fun replaceExperienceStep(dayIndex: Int, stepIndex: Int, choice: Pair<ActivityLinks.Category, PointOfInterest>?) {
        val st = _experience.value
        val exp = st.result ?: return
        val updated = ExperienceBuilder.replaceStep(st.answers, exp, dayIndex, stepIndex, choice)
        _experience.update { it.copy(result = updated, previous = it.previous + exp.placeIds) }
    }

    /** Autre proposition : mêmes réponses, en écartant les lieux de la proposition affichée (pas de hasard). */
    fun regenerateExperience() {
        val st = _experience.value
        if (experiencePools.isEmpty()) return generateExperience()
        val avoid = st.previous + (st.result?.placeIds ?: emptySet())
        val exp = ExperienceBuilder.build(st.answers, st.answers.city.trim(), experiencePools, avoid, experienceWeather)
        _experience.update { it.copy(result = exp, previous = avoid) }
    }

    /** Meilleurs lieux d'une catégorie (même logique que l'onglet Sorties, sans l'afficher). */
    private suspend fun loadCategory(
        city: String,
        geo: GeoPoint,
        category: ActivityLinks.Category,
        hint: String? = null,
    ): List<PointOfInterest> {
        // Cache séparé de l'onglet Sorties : plus de lieux par catégorie pour coller aux goûts.
        val key = "${city.lowercase()}|$category|exp|${hint.orEmpty()}"
        placesCache[key]?.items?.takeIf { it.isNotEmpty() }?.let { return it }
        val db = NotablePlaces
        val items: List<PointOfInterest> = if (db.supports(category)) {
            runCatching { db.search(geo, category, limit = 80) }.getOrNull().orEmpty()
        } else {
            val listings = attempt(15_000) { WikivoyageGuide.listings(city, geo.wikidata) }?.getOrNull().orEmpty()
            val guide = GuideParser.select(listings, category).take(30)
                .map { GuideParser.toPoi(it, category, geo) }
            val st = _settings.value
            val rated = if (st.googleKey.isNotBlank() || st.tripadvisorKey.isNotBlank() || st.foursquareKey.isNotBlank()) {
                runCatching { RatingsAggregator.search(st, quotas, city, geo, category, guide, hint) }
                    .getOrNull()?.places.orEmpty()
            } else emptyList()
            when {
                rated.size >= 3 -> rated
                guide.size >= 3 -> guide
                else -> (guide + runCatching { PlacesApi.search(geo, category) }.getOrNull().orEmpty())
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
    private suspend fun resolveCity(city: String, chosen: Place?): GeoPoint? {
        val key = city.lowercase()
        geoCache[key]?.let { return it }
        val known = chosen?.takeIf { it.lat != null && it.lon != null }
            ?: PopularPlaces.all.firstOrNull { it.name.equals(city, ignoreCase = true) && it.lat != null }
        val geo = known?.let { GeoPoint(it.lat!!, it.lon!!, it.name) }
            ?: attempt(10_000) { PlacesApi.geocode(city) }?.getOrNull()
        geo?.let { geoCache[key] = it }
        return geo
    }

    private suspend fun cityGuide(geo: GeoPoint, city: String): Set<String> {
        val id = geo.wikidata
            ?: runCatching { PlacesApi.geocode(city)?.wikidata }.getOrNull()
            ?: return emptySet()
        guideCache[id]?.let { return it }
        val names = runCatching { PlacesApi.guideNames(id) }.getOrDefault(emptySet())
        if (names.isNotEmpty()) guideCache[id] = names
        return names
    }

    /** Classe les lieux : cités par le guide + notoriété (nombre de Wikipédias), puis distance. */
    private suspend fun rank(
        items: List<PointOfInterest>,
        guide: Set<String>,
    ): List<PointOfInterest> {
        val ids = items.mapNotNull { it.wikidata }.take(150)
        val counts = if (ids.isEmpty()) emptyMap() else
            runCatching { PlacesApi.sitelinkCounts(ids) }.getOrDefault(emptyMap())
        return items.map { p ->
            p.copy(
                sitelinks = p.wikidata?.let { counts[it] } ?: 0,
                inGuide = guide.isNotEmpty() && PlacesParser.matchesGuide(p.name, guide),
            )
        }.sortedWith(
            compareByDescending<PointOfInterest> { it.score }
                .thenByDescending { it.wikipedia != null }
                .thenByDescending { it.website != null }
                .thenBy { it.distanceM },
        )
    }

    suspend fun wikiSummary(tag: String): WikiSummary? {
        if (wikiCache.containsKey(tag)) return wikiCache[tag]
        val summary = PlacesApi.wikiSummary(tag)
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
            _message.value = t("Choisissez le départ et la destination dans la liste proposée.")
            return
        }
        if (origin.code == destination.code) {
            _message.value = t("Le départ et la destination sont identiques.")
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
            _message.value = t("Ce trajet est déjà suivi.")
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
            t("Trajet suivi. Ajoutez votre clé API dans Réglages pour activer la surveillance.")
        } else t("Trajet suivi : vous serez prévenu si le prix baisse.")
    }

    fun removeAlert(id: String) {
        alertStore.remove(id)
        _alerts.value = alertStore.all()
    }

    fun checkAlertsNow() {
        if (_settings.value.isDemo) {
            _message.value = t("Ajoutez votre clé API dans Réglages pour vérifier les prix.")
            return
        }
        if (_checking.value) return
        _checking.value = true
        viewModelScope.launch {
            try {
                val drops = AlertChecker.checkAll(getApplication())
                _message.value = if (drops.isEmpty()) t("Prix vérifiés : pas de baisse pour le moment.") else
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

    fun setQuotaLimit(src: RatingSource, limit: Int) {
        quotas.setLimit(src, limit)
        placesCache.clear()
    }

    /** Changement de langue : on vide les données déjà traduites (Wikipédia, lieux) pour les recharger. */
    fun setLanguage(l: Lang?) {
        I18n.choose(getApplication(), l)
        wikiCache.clear()
        placesCache.clear()
        _places.value = PlacesState()
        if (_blog.value.city != null) loadBlogCards()
        _experience.update { it.copy(result = null) }
    }

    fun saveSettings(s: Settings) {
        placesCache.clear()
        ActivityLinks.tiqetsPartner = s.tiqetsPartner
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

/** Exécute [block] avec une limite de temps : null si trop long, sinon le résultat ou l'erreur. */
private suspend fun <T> attempt(ms: Long, block: suspend () -> T): Result<T>? =
    withTimeoutOrNull(ms) { runCatching { block() } }
