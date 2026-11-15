package com.riad.voyage

import android.app.Application
import com.riad.voyage.data.Blog
import com.riad.voyage.data.DayWeather
import com.riad.voyage.data.GeoPoint
import com.riad.voyage.data.Place
import com.riad.voyage.data.PlacesApi
import com.riad.voyage.data.PointOfInterest
import com.riad.voyage.data.PopularPlaces
import com.riad.voyage.data.QuotaStore
import com.riad.voyage.data.SavedPlace
import com.riad.voyage.data.SettingsStore
import com.riad.voyage.data.TravelpayoutsApi
import com.riad.voyage.data.Trip
import com.riad.voyage.data.TripStore
import com.riad.voyage.data.WeatherApi
import com.riad.voyage.data.WikiSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ce que toutes les parties de l'appli partagent : réglages, écran affiché, message à l'écran, voyages
 * enregistrés, position, météo et résumés Wikipédia des villes. Chaque partie (vols, hôtels, blogue…) le reçoit.
 */
class AppCore(val app: Application, val scope: CoroutineScope) {
    val settingsStore = SettingsStore(app)
    val settings = MutableStateFlow(settingsStore.load())
    val tab = MutableStateFlow(Tab.HOME)
    val message = MutableStateFlow<String?>(null)
    val quotas = QuotaStore(app)

    fun api() = settings.value.let { TravelpayoutsApi(it.token, it.currency) }

    // ---------- Voyages enregistrés ----------

    val tripStore = TripStore(app)
    val trips = MutableStateFlow(tripStore.all())
    /** Appelé après chaque changement de voyage (le budget ouvert se met à jour). */
    var onTripsChanged: () -> Unit = {}

    fun refreshTrips() { trips.value = tripStore.all(); onTripsChanged() }

    /** Voyage d'une ville (créé s'il n'existe pas). */
    fun tripFor(city: String, country: String = ""): Trip =
        trips.value.firstOrNull { it.city.equals(city.trim(), ignoreCase = true) }
            ?: Trip(id = "trip-${System.currentTimeMillis()}", city = city.trim(), country = country)

    fun saveTrip(t: Trip, text: String) {
        tripStore.upsert(t)
        refreshTrips()
        message.value = text
    }

    fun isFavorite(name: String) = trips.value.any { tr -> tr.places.any { it.name.equals(name, ignoreCase = true) } }

    /** Lieu gardé dans le voyage de la ville, ou retiré s'il y était déjà. */
    fun toggleFavorite(p: PointOfInterest, city: String) {
        val existing = trips.value.firstOrNull { tr -> tr.places.any { it.name.equals(p.name, ignoreCase = true) } }
        if (existing != null) {
            tripStore.upsert(existing.copy(places = existing.places.filterNot { it.name.equals(p.name, ignoreCase = true) }))
            refreshTrips()
            return
        }
        val trip = tripFor(city)
        saveTrip(trip.copy(places = trip.places + SavedPlace.of(p)), tf("Ajouté à votre voyage : {0}", t(trip.city)))
    }

    // ---------- Villes ----------

    /** Pays d'une ville : celui donné, sinon les villes connues (blogue, villes populaires). */
    fun countryOf(city: String, fallback: String = ""): String =
        fallback.ifBlank {
            Blog.city(city)?.country ?: PopularPlaces.all.firstOrNull { it.name.equals(city, true) }?.country.orEmpty()
        }

    suspend fun suggestPlaces(term: String): List<Place> {
        if (term.trim().length < 2) return emptyList()
        val online = runCatching { api().searchPlaces(term.trim()) }.getOrDefault(emptyList())
        return online.ifEmpty { PopularPlaces.filter(term) }
    }

    /** Suggestions de villes uniquement (pas d'aéroports). */
    suspend fun suggestCities(term: String): List<Place> {
        val all = suggestPlaces(term)
        return all.filter { !it.isAirport }.ifEmpty { all }
    }

    /** Infos pratiques du pays d'une ville (null si le pays n'est pas connu). */
    fun practicalFor(city: String, country: String = "") = com.riad.voyage.data.Practical.of(countryOf(city, country))

    /** Taux de change (base euro), gardés en mémoire. */
    suspend fun rates(): Map<String, Double> = attempt(8_000) { com.riad.voyage.data.Fx.rates() }?.getOrNull().orEmpty()

    private val geoCache = mutableMapOf<String, GeoPoint>()
    val weatherCache = mutableMapOf<String, List<DayWeather>>()
    val wikiCache = mutableMapOf<String, WikiSummary?>()

    /** Position de la ville : suggestion choisie, liste intégrée, cache, puis OpenStreetMap. */
    suspend fun resolveCity(city: String, chosen: Place?): GeoPoint? {
        val key = city.lowercase()
        geoCache[key]?.let { return it }
        val known = chosen?.takeIf { it.lat != null && it.lon != null }
            ?: PopularPlaces.all.firstOrNull { it.name.equals(city, ignoreCase = true) && it.lat != null }
        val geo = known?.let { GeoPoint(it.lat!!, it.lon!!, it.name) }
            ?: attempt(10_000) { PlacesApi.geocode(city) }?.getOrNull()
        geo?.let { geoCache[key] = it }
        return geo
    }

    /** Prévisions à 16 jours d'une ville (Open-Meteo), gardées en mémoire. Vide si indisponible. */
    suspend fun weatherFor(city: String): List<DayWeather> {
        val key = city.trim().lowercase()
        if (key.isBlank()) return emptyList()
        weatherCache[key]?.let { return it }
        val geo = resolveCity(city, null) ?: return emptyList()
        return attempt(10_000) { WeatherApi.forecast(geo.lat, geo.lon) }?.getOrNull().orEmpty()
            .also { if (it.isNotEmpty()) weatherCache[key] = it }
    }

    suspend fun wikiSummary(tag: String): WikiSummary? {
        if (wikiCache.containsKey(tag)) return wikiCache[tag]
        return PlacesApi.wikiSummary(tag).also { wikiCache[tag] = it }
    }
}

/** Exécute [block] avec une limite de temps : null si trop long, sinon le résultat ou l'erreur (notés au diagnostic). */
suspend fun <T> attempt(ms: Long, block: suspend () -> T): Result<T>? =
    withTimeoutOrNull(ms) { runCatching { block() } }.also { r ->
        if (r == null) com.riad.voyage.data.Diagnostics.log("Délai dépassé ($ms ms)")
        else r.exceptionOrNull()?.let { com.riad.voyage.data.Diagnostics.log("${it::class.simpleName}: ${it.message}") }
    }
