package com.riad.voyage

import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.GeoPoint
import com.riad.voyage.data.GuideParser
import com.riad.voyage.data.NotablePlaces
import com.riad.voyage.data.Place
import com.riad.voyage.data.PlacesApi
import com.riad.voyage.data.PointOfInterest
import com.riad.voyage.data.RatingsAggregator
import com.riad.voyage.data.TravelpayoutsApi
import com.riad.voyage.data.WikivoyageGuide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Page Ville : ville choisie, catégorie et lieux trouvés (base de lieux, guide Wikivoyage, notes, carte), gardés en cache.
 * [loadCategory] sert aussi à l'Envolée (plus de lieux par catégorie, pour coller aux goûts).
 */
class PlacesModel(private val core: AppCore) {
    private val _city = MutableStateFlow("")
    val city: StateFlow<String> = _city.asStateFlow()
    private val _place = MutableStateFlow<Place?>(null)
    val place: StateFlow<Place?> = _place.asStateFlow()
    private val _category = MutableStateFlow(ActivityLinks.Category.ATTRACTIONS)
    val category: StateFlow<ActivityLinks.Category> = _category.asStateFlow()
    private val _places = MutableStateFlow(PlacesState())
    val state: StateFlow<PlacesState> = _places.asStateFlow()
    private var placesJob: kotlinx.coroutines.Job? = null
    private val placesCache = mutableMapOf<String, PlacesState>()

    /** Ville affichée (sans lancer de recherche). */
    fun show(city: String, place: Place? = null) { _city.value = city; _place.value = place }

    /** Lieux à recharger (langue, clés ou limites changées). */
    fun clear() { placesCache.clear(); _places.value = PlacesState() }

    /** « Effacer l'historique » : ville et lieux oubliés. */
    fun reset() { show(""); clear() }

    fun setCity(city: String) { _city.value = city }

    /** Ville choisie dans les suggestions : on garde sa position et on lance la recherche. */
    fun setPlace(p: Place) {
        _place.value = p
        _city.value = p.name
        search(force = true)
    }
    fun setCategory(c: ActivityLinks.Category) {
        _category.value = c
        search()
    }

    /** Cherche les lieux de la catégorie choisie (sauf si la même recherche est déjà affichée). */
    fun search(force: Boolean = false) {
        val city = _city.value.trim()
        val category = _category.value
        if (city.isBlank() || category == ActivityLinks.Category.TOURS) return
        val key = "${city.lowercase()}|$category"
        // Déjà cherché : réponse immédiate depuis le cache.
        if (!force) placesCache[key]?.let { _places.value = it; return }
        val current = _places.value
        if (!force && current.city.equals(city, ignoreCase = true) && current.category == category && current.loading) return

        placesJob?.cancel()
        val chosen = _place.value?.takeIf { it.name.equals(city, ignoreCase = true) }
        _places.value = PlacesState(loading = true, stage = t("Recherche de la ville…"), city = city, category = category)
        placesJob = core.scope.launch {
            val geo = core.resolveCity(city, chosen)
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
            val st = core.settings.value
            val hasRatingKeys = st.googleKey.isNotBlank() || st.tripadvisorKey.isNotBlank() || st.foursquareKey.isNotBlank()
            var quotaNotice: String? = null
            if (hasRatingKeys && agg.supports(category)) {
                _places.value = _places.value.copy(stage = t("Récupération des notes…"))
                val outcome = attempt(20_000) { agg.search(st, core.quotas, city, geo, category, guidePicks) }?.getOrNull()
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

    suspend fun loadCategory(
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
            val st = core.settings.value
            val rated = if (st.googleKey.isNotBlank() || st.tripadvisorKey.isNotBlank() || st.foursquareKey.isNotBlank()) {
                runCatching { RatingsAggregator.search(st, core.quotas, city, geo, category, guide, hint) }
                    .getOrNull()?.places.orEmpty()
            } else emptyList()
            when {
                rated.size >= 3 -> rated
                guide.size >= 3 -> guide
                else -> (guide + runCatching { PlacesApi.search(geo, category) }.getOrNull().orEmpty())
                    .distinctBy { it.name.lowercase() }.take(30)
            }
        }
        val clean = com.riad.voyage.data.PlaceText.tidy(items)
        if (clean.isNotEmpty()) placesCache[key] = PlacesState(items = clean, city = city, category = category)
        return clean
    }

    private fun publish(key: String, raw: PlacesState) {
        val state = raw.copy(items = com.riad.voyage.data.PlaceText.tidy(raw.items))
        placesCache[key] = state
        if (_places.value.city.equals(state.city, ignoreCase = true) && _places.value.category == state.category) {
            _places.value = state
        }
    }
}
