package com.riad.voyage

import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.Article
import com.riad.voyage.data.ArticleCard
import com.riad.voyage.data.BlogCity
import com.riad.voyage.data.BlogFilter
import com.riad.voyage.data.BudgetCalculator
import com.riad.voyage.data.BudgetInput
import com.riad.voyage.data.BudgetItem
import com.riad.voyage.data.DayPrice
import com.riad.voyage.data.Experience
import com.riad.voyage.data.Flight
import com.riad.voyage.data.HotelBooking
import com.riad.voyage.data.HotelBookingResult
import com.riad.voyage.data.HotelDetail
import com.riad.voyage.data.HotelOffer
import com.riad.voyage.data.HotelReview
import com.riad.voyage.data.Place
import com.riad.voyage.data.PointOfInterest
import com.riad.voyage.data.PopularPlaces
import com.riad.voyage.data.Prebook
import com.riad.voyage.data.Quiz
import com.riad.voyage.data.ReviewSummary
import com.riad.voyage.data.RoomOffer
import java.time.LocalDate
import java.time.YearMonth

/**
 * Navigation : quatre entrées dans la barre du bas. Explorer regroupe la ville, les sorties et le blogue ;
 * Mes voyages regroupe vols, hôtels, budget et alertes. [parent] : entrée de la barre allumée pour cet écran.
 */
enum class Tab(val label: String, val parent: String? = null) {
    HOME("Accueil"), EXPERIENCE("Envolée"), CITY("Explorer"), TRIPS("Mes voyages"),
    ACTIVITIES("Sorties", "CITY"), BLOG("Blogue", "CITY"),
    FLIGHTS("Vols", "TRIPS"), HOTELS("Hôtels", "TRIPS"), BUDGET("Budget", "TRIPS"), ALERTS("Alertes", "TRIPS"),
    SETTINGS("Réglages", "HOME");

    val inBottomBar get() = parent == null
    val section: Tab get() = parent?.let { Tab.valueOf(it) } ?: this
}

/** Billet ou confirmation en cours de lecture par Envolée AI, puis à confirmer. */
data class BookingImport(val booking: com.riad.voyage.data.Booking? = null, val loading: Boolean = false, val error: String? = null,
                         val plan: Boolean = false)   // depuis l'Envolée : composer le programme du voyage ensuite

/** Sections de la page Ville. */
enum class CitySection(val label: String) { PLACES("Lieux"), ARTICLES("Articles"), INFO("Infos pratiques") }

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
    val tripId: String? = null,          // voyage dont on suit les dépenses
    val advice: String? = null,          // commentaire d'Envolée AI
    val advising: Boolean = false,
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
    val stage: Int = 0,                   // étape en cours de la composition (voir STAGES)
    val result: Experience? = null,
    val error: String? = null,
    val previous: Set<String> = emptySet(),
    val quick: Boolean = true,            // questionnaire rapide (3 questions)
    val describing: Boolean = false,      // Claude lit la demande libre
    val checking: Boolean = false,        // vérification des lieux en cours
    val tripId: String? = null,           // voyage relié : le programme y est enregistré automatiquement
    val aiSummary: String? = null,        // ce qu'Envolée AI a compris de la demande libre
    val aiIgnored: List<String> = emptyList(), // ce qu'elle n'a pas compris
    val aiQuestion: String? = null,       // question de précision d'Envolée AI
    val lastRequest: String = "",         // dernière demande libre (complétée par la réponse à la question)
    val recap: Boolean = false,           // page « Ajuster mes réponses »
    val editStep: String? = null,         // question ouverte depuis le récapitulatif
    val changeSummary: String? = null,    // ce qu'Envolée AI vient de modifier en parlant
    val undo: Pair<Quiz.Answers, Experience>? = null,   // état avant cette modification
    val editing: Boolean = false,         // modification en parlant en cours
) {
    val steps get() = Quiz.steps(answers, quick)

    companion object {
        /** Étapes de la composition, affichées une à une pendant l'attente (la première : demande libre seulement). */
        val STAGES = listOf("Lecture de votre demande", "Recherche de la ville", "Sélection des meilleurs lieux",
            "Analyse selon vos goûts", "Composition du programme")
    }
}
