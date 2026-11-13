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
    HOME("Accueil"), FLIGHTS("Vols"), ACTIVITIES("Sorties"), ALERTS("Alertes"),
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
)

const val SOURCE_FAME = "fame"
const val SOURCE_GUIDE = "guide"
const val SOURCE_MAP = "map"

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val alertStore = AlertStore(app)
    private val recentStore = RecentStore(app)

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
        _tab.value = t
        if (t == Tab.ACTIVITIES) searchActivities()
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
            if (guidePicks.size >= 5) {
                publish(key, PlacesState(items = guidePicks, city = city, category = category, guideFound = true, source = SOURCE_GUIDE))
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

    fun saveSettings(s: Settings) {
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
