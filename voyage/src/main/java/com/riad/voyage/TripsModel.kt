package com.riad.voyage

import com.riad.voyage.data.Article
import com.riad.voyage.data.BudgetItem
import com.riad.voyage.data.Place
import com.riad.voyage.data.SavedArticle
import com.riad.voyage.data.SavedDay
import com.riad.voyage.data.SavedPlace
import com.riad.voyage.data.SavedStep
import com.riad.voyage.data.Trip
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mes voyages : voyage ouvert, dépenses, programme modifié à la main, bagages, articles gardés.
 * [familyProgram] : le programme de ce voyage a été composé « en famille » ; [blogCity] : ville lue dans le blogue.
 */
class TripsModel(
    private val core: AppCore,
    private val familyProgram: (String) -> Boolean,
    private val blogCity: () -> String?,
) {
    /** Voyage ouvert (null = la liste) ; article gardé en cours de lecture. */
    private val _openTrip = MutableStateFlow<String?>(null)
    val openTrip: StateFlow<String?> = _openTrip.asStateFlow()
    private val _tripArticle = MutableStateFlow<Article?>(null)
    val tripArticle: StateFlow<Article?> = _tripArticle.asStateFlow()

    fun updateTrip(id: String, transform: (Trip) -> Trip) {
        val trip = core.trips.value.firstOrNull { it.id == id } ?: return
        core.tripStore.upsert(transform(trip))
        core.refreshTrips()
    }

    fun addExpense(tripId: String, label: String, amount: Double, currency: String, item: BudgetItem) = updateTrip(tripId) {
        it.copy(expenses = it.expenses + com.riad.voyage.data.Expense(System.currentTimeMillis(), label.trim(), amount, currency, item.name,
            LocalDate.now().toString()))
    }

    fun removeExpense(tripId: String, id: Long) = updateTrip(tripId) { it.copy(expenses = it.expenses.filter { e -> e.id != id }) }

    private fun editDays(tripId: String, f: (MutableList<SavedDay>) -> Unit) = updateTrip(tripId) { t ->
        t.copy(program = t.program.toMutableList().also(f))
    }

    private fun editSteps(tripId: String, day: Int, f: (MutableList<com.riad.voyage.data.SavedStep>) -> Unit) = editDays(tripId) { days ->
        days.getOrNull(day)?.let { d -> days[day] = d.copy(steps = d.steps.toMutableList().also(f)) }
    }

    /** Déplace une étape d'un cran dans la journée ([delta] = -1 vers le haut, +1 vers le bas). */
    fun moveStep(tripId: String, day: Int, index: Int, delta: Int) = editSteps(tripId, day) { s ->
        val j = index + delta
        if (index in s.indices && j in s.indices) s.add(j, s.removeAt(index))
    }

    fun deleteStep(tripId: String, day: Int, index: Int) = editSteps(tripId, day) { s -> if (index in s.indices) s.removeAt(index) }

    fun editStep(tripId: String, day: Int, index: Int, time: String, name: String, note: String) = editSteps(tripId, day) { s ->
        s.getOrNull(index)?.let { st -> s[index] = st.copy(time = time.trim(), moment = note.trim(), place = st.place.copy(name = name.trim())) }
    }

    /** Ajoute une étape libre (lieu saisi à la main) ; les étapes sont rangées par heure. */
    fun addStep(tripId: String, day: Int, time: String, name: String, note: String) = editSteps(tripId, day) { s ->
        s += com.riad.voyage.data.SavedStep(time.trim(), note.trim(), "📍", "", 60, SavedPlace(name.trim(), "Étape ajoutée", 0.0, 0.0, false))
        s.sortBy { it.time }
    }

    fun addDay(tripId: String) = editDays(tripId) { days -> days += SavedDay(tf("Jour {0}", days.size + 1), null, emptyList()) }

    fun togglePacked(tripId: String, key: String) = updateTrip(tripId) {
        it.copy(packed = if (key in it.packed) it.packed - key else it.packed + key)
    }

    fun addPackItem(tripId: String, text: String) = updateTrip(tripId) { if (text.isBlank()) it else it.copy(extraItems = it.extraItems + text.trim()) }

    fun removePackItem(tripId: String, text: String) = updateTrip(tripId) { it.copy(extraItems = it.extraItems - text, packed = it.packed - text) }

    /** Liste de bagages d'un voyage : durée, météo du séjour (si prévue), activités, pays, enfants. */
    suspend fun packingFor(trip: Trip): List<com.riad.voyage.data.PackItem> {
        val start = trip.start
        val nights = trip.nights ?: (trip.program.size - 1).coerceAtLeast(2)
        val forecast = core.weatherFor(trip.city).filter { w -> start == null || (!w.date.isBefore(start) && !w.date.isAfter(trip.end ?: start)) }
        val kinds = trip.program.flatMap { d -> d.steps.map { it.place.kind + " " + it.place.name } } + trip.places.map { it.kind + " " + it.name }
        // En famille seulement si le programme de ce voyage a été fait « en famille ».
        val family = (trip.travellers ?: 0) >= 3 && familyProgram(trip.id)
        return com.riad.voyage.data.Packing.generate(nights, forecast, kinds, core.practicalFor(trip.city, trip.country), family,
            lateDeparture = (trip.departureMin ?: 0) >= 15 * 60)
    }

    /** Nouveau voyage : destination, dates et voyageurs, saisis une fois et réutilisés partout. */
    fun createTrip(city: String, place: Place?, start: LocalDate, end: LocalDate, travellers: Int, arrival: String?, departure: String?) {
        val trip = Trip(id = "trip-${System.currentTimeMillis()}", city = city.trim(), country = place?.country.orEmpty(),
            startDate = start.toString(), endDate = end.toString(), travellers = travellers, arrivalTime = arrival, departureTime = departure)
        core.saveTrip(trip, tf("Voyage créé : {0}", t(trip.city)))
        openTrip(trip.id)
    }

    fun saveArticle(a: Article) {
        val city = blogCity() ?: core.trips.value.firstOrNull()?.city ?: t("Mes lectures")
        val trip = core.tripFor(city)
        if (trip.articles.any { it.wiki == a.wiki }) { core.message.value = t("Article déjà gardé."); return }
        core.saveTrip(trip.copy(articles = trip.articles + SavedArticle.of(a)),
            tf("Article gardé pour la lecture hors connexion : {0}", t(trip.city)))
    }

    fun openTrip(id: String?) { _openTrip.value = id; _tripArticle.value = null; core.tab.value = Tab.TRIPS }
    fun readSavedArticle(a: SavedArticle?) { _tripArticle.value = a?.toArticle() }

    fun deleteTrip(id: String) {
        core.tripStore.delete(id)
        core.refreshTrips()
        _openTrip.value = null
    }

    fun removeFromTrip(id: String, kind: String, index: Int) {
        val trip = core.trips.value.firstOrNull { it.id == id } ?: return
        val updated = when (kind) {
            "flight" -> trip.copy(flights = trip.flights.filterIndexed { i, _ -> i != index })
            "place" -> trip.copy(places = trip.places.filterIndexed { i, _ -> i != index })
            "article" -> trip.copy(articles = trip.articles.filterIndexed { i, _ -> i != index })
            "program" -> trip.copy(program = emptyList(), programTitle = null)
            else -> trip
        }
        core.tripStore.upsert(updated)
        core.refreshTrips()
    }
}
