package com.riad.voyage

import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.BudgetInput
import com.riad.voyage.data.BudgetItem
import com.riad.voyage.data.Chosen
import com.riad.voyage.data.Fx
import com.riad.voyage.data.PlannedActivity
import com.riad.voyage.data.PriceText
import com.riad.voyage.data.Quiz
import com.riad.voyage.data.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Budget du séjour : vols, hôtel, repas, transports et activités, d'un voyage enregistré ou du programme affiché.
 * [answers] : réponses de l'envolée (niveau de budget, déplacements) ; [choose] : aller choisir ce qui manque.
 */
class BudgetModel(
    private val core: AppCore,
    private val answers: () -> Quiz.Answers,
    private val choose: (BudgetItem, String, Int) -> Unit,
) {
    private val _budget = MutableStateFlow<BudgetState?>(null)
    val state: StateFlow<BudgetState?> = _budget.asStateFlow()
    /** Un choix a été lancé depuis le budget : le bouton « Retour au budget » s'affiche. */
    private val _budgetReturn = MutableStateFlow(false)
    val returning: StateFlow<Boolean> = _budgetReturn.asStateFlow()

    /** Change le budget ouvert (conseil d'Envolée AI, objectif annoncé). */
    fun edit(transform: (BudgetState) -> BudgetState) = _budget.update { it?.let(transform) }

    /** Reconstruit le budget à partir des choix actuels (taux de change en paramètre). */
    private var budgetBuilder: ((Map<String, Double>) -> BudgetInput)? = null

    fun open(tripId: String?, build: (Map<String, Double>) -> BudgetInput) {
        val from = core.tab.value
        budgetBuilder = build
        val rates = _budget.value?.rates.orEmpty()
        _budget.value = BudgetState(input = build(rates), rates = rates, from = from, tripId = tripId)
        core.tab.value = Tab.BUDGET
        core.scope.launch {
            val fresh = attempt(8_000) { Fx.rates() }?.getOrNull().orEmpty()
            if (fresh.isNotEmpty()) { _budget.update { it?.copy(rates = fresh) }; refresh() }
        }
    }

    /** Recalcule les postes « choisis » (vols, hôtel, activités) en gardant les réglages de la personne. */
    fun refresh() {
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
    fun openBudgetForTrip(trip0: Trip) = open(trip0.id) { rates ->
        val trip = core.trips.value.firstOrNull { it.id == trip0.id } ?: trip0
        val a = answers()
        budgetFor(trip.city, core.countryOf(trip.city, trip.country), trip, rates, trip.nights ?: 3,
            trip.travellers ?: (if (a.city.equals(trip.city, true)) a.people else null) ?: 2)
    }

    fun budgetFor(
        city: String, country: String, trip: Trip?, rates: Map<String, Double>, nights: Int, travellers: Int,
        program: List<PlannedActivity> = emptyList(),
    ): BudgetInput {
        val (flights, hotel, saved) = choicesOf(trip, rates)
        val a = answers()
        // Repas : rien le premier jour après une arrivée le soir (ou avec soirée à l'hôtel), la moitié l'après-midi ;
        // rien le dernier jour avant un départ le matin, la moitié avant un départ l'après-midi.
        val arrival = trip?.arrivalMin
        val departure = trip?.departureMin
        val firstLoss = when {
            arrival == null -> 0.0
            arrival >= 19 * 60 || (trip?.restAfterArrival == true && arrival >= 15 * 60) -> 1.0
            arrival >= 14 * 60 -> 0.5
            else -> 0.0
        }
        val lastLoss = when {
            departure == null -> 0.0
            departure <= 11 * 60 -> 1.0
            departure <= 15 * 60 -> 0.5
            else -> 0.0
        }
        val mealDays = (nights + 1) - firstLoss - lastLoss
        return BudgetInput(
            city = city, country = country, travellers = travellers, nights = nights, level = a.budget, mobility = a.mobility,
            flights = flights, hotel = hotel?.first, hotelBooked = hotel?.second == true, activities = program + saved,
            mealDays = mealDays.coerceAtLeast(0.5),
        )
    }

    /** Depuis le budget, aller choisir ce qui manque (vol, hôtel, activités) pour la même ville ; « Retour au budget » s'affiche. */
    fun chooseForBudget(item: BudgetItem) {
        val b = _budget.value ?: return
        choose(item, b.input.city, b.input.travellers)
        _budgetReturn.value = true
    }

    /** Revenir au budget (recalculé avec les nouveaux choix). */
    fun backToBudget() {
        _budgetReturn.value = false
        if (_budget.value == null) return
        refresh()
        core.tab.value = Tab.BUDGET
    }

    private fun categoryOfEmoji(e: String) = ActivityLinks.Category.entries.firstOrNull { it.emoji == e }

    fun updateBudgetInput(transform: (BudgetInput) -> BudgetInput) =
        _budget.update { it?.copy(input = transform(it.input)) }

    fun setBudgetOverride(item: BudgetItem, eur: Double?) = _budget.update { b ->
        b?.copy(overrides = if (eur == null) b.overrides - item else b.overrides + (item to eur))
    }

    fun setBudgetTarget(eur: Double?) = _budget.update { it?.copy(targetEur = eur) }

    /** Ferme le budget : retour à l'écran d'origine (ou au choix du voyage si ouvert depuis l'onglet Budget). */
    fun closeBudget() {
        _budgetReturn.value = false
        val from = _budget.value?.from ?: Tab.HOME
        if (from == Tab.BUDGET) _budget.value = null else core.tab.value = from
    }
}
