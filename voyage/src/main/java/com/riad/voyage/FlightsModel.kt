package com.riad.voyage

import com.riad.voyage.alerts.AlertChecker
import com.riad.voyage.data.AlertStore
import com.riad.voyage.data.DemoData
import com.riad.voyage.data.Flight
import com.riad.voyage.data.Place
import com.riad.voyage.data.PriceAlert
import com.riad.voyage.data.RecentSearch
import com.riad.voyage.data.RecentStore
import com.riad.voyage.data.SavedFlight
import com.riad.voyage.data.TravelpayoutsApi
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

/** Vols : formulaire, résultats, recherches récentes, vols gardés et alertes de prix. */
class FlightsModel(private val core: AppCore) {
    private val recentStore = RecentStore(core.app)
    private val alertStore = AlertStore(core.app)

    private val _recents = MutableStateFlow(recentStore.all())
    val recents: StateFlow<List<RecentSearch>> = _recents.asStateFlow()
    private val _form = MutableStateFlow(
        _recents.value.firstOrNull()?.let { FlightForm(origin = it.origin, adults = it.adults) } ?: FlightForm(),
    )
    val form: StateFlow<FlightForm> = _form.asStateFlow()

    private val _results = MutableStateFlow(ResultsState())
    val results: StateFlow<ResultsState> = _results.asStateFlow()

    private val _alerts = MutableStateFlow(alertStore.all())
    val alerts: StateFlow<List<PriceAlert>> = _alerts.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** « Effacer l'historique » : recherches récentes, formulaire (départ vide compris) et résultats. */
    fun reset() {
        recentStore.clear()
        _recents.value = emptyList()
        _form.value = FlightForm(origin = null)
        _results.value = ResultsState()
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
            core.message.value = t("Choisissez le départ et la destination dans la liste proposée.")
            return
        }
        if (origin.code == destination.code) {
            core.message.value = t("Le départ et la destination sont identiques.")
            return
        }
        recentStore.add(RecentSearch(origin, destination, f.departure.toString(), if (f.roundTrip) f.returnDate.toString() else null, f.adults))
        _recents.value = recentStore.all()
        core.tab.value = Tab.FLIGHTS
        val s = core.settings.value
        _results.update {
            ResultsState(visible = true, loading = true, query = f, demo = s.isDemo, currency = s.currency, sort = it.sort)
        }
        core.scope.launch {
            try {
                val (flights, calendar) = if (s.isDemo) {
                    DemoData.flights(
                        origin.code, destination.code, f.departure, if (f.roundTrip) f.returnDate else null,
                        f.flexibleMonth, f.directOnly, f.adults,
                    ) to DemoData.calendar(origin.code, destination.code, YearMonth.from(f.departure))
                } else coroutineScope {
                    val api = core.api()
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
        core.tab.value = Tab.FLIGHTS
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
        val marker = core.settings.value.marker
        return if (flight.link.startsWith("http")) {
            if (marker.isBlank()) flight.link else TravelpayoutsApi.bookingUrl(flight.link, marker)
        } else TravelpayoutsApi.bookingUrl(flight.link, marker)
    }

    fun siteSearchUrl(): String? {
        val q = _results.value.query ?: return null
        val o = q.origin ?: return null
        val d = q.destination ?: return null
        return TravelpayoutsApi.searchUrl(
            o.code, d.code, q.departure, if (q.roundTrip) q.returnDate else null, q.adults, core.settings.value.marker,
        )
    }

    fun saveFlight(f: Flight) {
        val q = _results.value.query ?: return
        val dest = q.destination ?: return
        val trip = core.tripFor(dest.name, dest.country)
        val saved = SavedFlight(
            q.origin?.name ?: f.origin, dest.name, f.departureAt, f.returnAt, f.price * q.adults,
            _results.value.currency, f.airline, bookingUrl(f),
        )
        if (trip.flights.any { it.url == saved.url }) { core.message.value = t("Ce vol est déjà dans votre voyage."); return }
        core.saveTrip(
            trip.copy(
                flights = trip.flights + saved,
                startDate = trip.startDate ?: f.departureDate?.toString(),
                endDate = trip.endDate ?: f.returnAt?.let { parseDateTime(it)?.toLocalDate()?.toString() },
                arrivalTime = trip.arrivalTime ?: parseDateTime(f.departureAt)?.plusMinutes(f.durationTo.toLong())?.let { "%02d:%02d".format(it.hour, it.minute) },
                departureTime = trip.departureTime ?: f.returnAt?.let { parseDateTime(it) }?.let { "%02d:%02d".format(it.hour, it.minute) },
            ),
            tf("Vol ajouté à votre voyage : {0}", t(dest.name)),
        )
    }

    // ---------- Alertes ----------

    fun followCurrentSearch() {
        val r = _results.value
        val q = r.query ?: return
        val o = q.origin ?: return
        val d = q.destination ?: return
        val id = listOf(o.code, d.code, q.departureParam, q.returnParam ?: "-", q.directOnly).joinToString("_")
        if (_alerts.value.any { it.id == id }) {
            core.message.value = t("Ce trajet est déjà suivi.")
            return
        }
        val cheapest = if (r.demo) null else r.flights.minOfOrNull { it.price }
        alertStore.upsert(
            PriceAlert(
                id = id, origin = o.code, originName = o.name, destination = d.code, destinationName = d.name,
                departure = q.departureParam, returnDate = q.returnParam, directOnly = q.directOnly,
                lastPrice = cheapest, currency = core.settings.value.currency,
                lastChecked = if (cheapest != null) System.currentTimeMillis() else 0L,
            ),
        )
        _alerts.value = alertStore.all()
        core.message.value = if (r.demo) {
            t("Trajet suivi. Ajoutez votre clé API dans Réglages pour activer la surveillance.")
        } else t("Trajet suivi : vous serez prévenu si le prix baisse.")
    }

    fun removeAlert(id: String) {
        alertStore.remove(id)
        _alerts.value = alertStore.all()
    }

    fun checkAlertsNow() {
        if (core.settings.value.isDemo) {
            core.message.value = t("Ajoutez votre clé API dans Réglages pour vérifier les prix.")
            return
        }
        if (_checking.value) return
        _checking.value = true
        core.scope.launch {
            try {
                val drops = AlertChecker.checkAll(core.app)
                core.message.value = if (drops.isEmpty()) t("Prix vérifiés : pas de baisse pour le moment.") else
                    drops.joinToString("\n") {
                        "${it.alert.originName} → ${it.alert.destinationName} : " +
                            "${formatPrice(it.newPrice, it.alert.currency)} (avant ${formatPrice(it.oldPrice, it.alert.currency)})"
                    }
            } catch (e: Exception) {
                core.message.value = TravelpayoutsApi.userMessage(e)
            } finally {
                _alerts.value = alertStore.all()
                _checking.value = false
            }
        }
    }

}
