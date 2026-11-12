package com.riad.voyage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riad.voyage.alerts.AlertChecker
import com.riad.voyage.data.AlertStore
import com.riad.voyage.data.DayPrice
import com.riad.voyage.data.DemoData
import com.riad.voyage.data.Flight
import com.riad.voyage.data.Place
import com.riad.voyage.data.PopularPlaces
import com.riad.voyage.data.PriceAlert
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

enum class Tab(val label: String, val emoji: String) {
    FLIGHTS("Vols", "✈️"), HOTELS("Hôtels", "🏨"), ALERTS("Alertes", "🔔"), SETTINGS("Réglages", "⚙️")
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

data class HotelForm(
    val city: String = "",
    val checkIn: LocalDate = LocalDate.now().plusDays(14),
    val checkOut: LocalDate = LocalDate.now().plusDays(17),
    val adults: Int = 2,
    val rooms: Int = 1,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val alertStore = AlertStore(app)

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _tab = MutableStateFlow(Tab.FLIGHTS)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _form = MutableStateFlow(FlightForm())
    val form: StateFlow<FlightForm> = _form.asStateFlow()

    private val _results = MutableStateFlow(ResultsState())
    val results: StateFlow<ResultsState> = _results.asStateFlow()

    private val _hotel = MutableStateFlow(HotelForm())
    val hotel: StateFlow<HotelForm> = _hotel.asStateFlow()

    private val _alerts = MutableStateFlow(alertStore.all())
    val alerts: StateFlow<List<PriceAlert>> = _alerts.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private fun api() = _settings.value.let { TravelpayoutsApi(it.token, it.currency) }

    fun selectTab(t: Tab) { _tab.value = t }
    fun clearMessage() { _message.value = null }

    // ---------- Vols ----------

    suspend fun suggestPlaces(term: String): List<Place> {
        if (term.trim().length < 2) return emptyList()
        val online = runCatching { api().searchPlaces(term.trim()) }.getOrDefault(emptyList())
        return online.ifEmpty { PopularPlaces.filter(term) }
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

    // ---------- Vol + hôtel ----------

    fun hotelsFor(flight: Flight?) {
        val q = _results.value.query ?: _form.value
        val checkIn = flight?.departureDate ?: q.departure
        val checkOut = flight?.returnAt?.let { com.riad.voyage.data.parseDateTime(it)?.toLocalDate() }
            ?: if (q.roundTrip && !q.flexibleMonth) q.returnDate else checkIn.plusDays(3)
        _hotel.update {
            it.copy(
                city = q.destination?.name ?: it.city,
                checkIn = checkIn,
                checkOut = if (checkOut.isAfter(checkIn)) checkOut else checkIn.plusDays(1),
                adults = q.adults.coerceAtLeast(1),
            )
        }
        _tab.value = Tab.HOTELS
    }

    fun updateHotel(transform: (HotelForm) -> HotelForm) {
        _hotel.update { h ->
            val n = transform(h)
            if (!n.checkOut.isAfter(n.checkIn)) n.copy(checkOut = n.checkIn.plusDays(1)) else n
        }
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
        _message.value = if (_settings.value.isDemo) "Réglages enregistrés (mode démo)." else "Réglages enregistrés : vrais prix activés."
    }
}
