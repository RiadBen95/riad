package com.riad.voyage.data

import org.json.JSONObject

/**
 * Niveau de prix d'un pays par rapport à la France (1,0), pour estimer repas, hôtels,
 * transports et billets. Ordres de grandeur, à ajuster par la personne si besoin.
 */
object CostIndex {
    private val byCountry = mapOf(
        "france" to 1.0, "espagne" to 0.8, "spain" to 0.8, "portugal" to 0.75, "italie" to 0.9, "italy" to 0.9,
        "allemagne" to 0.95, "germany" to 0.95, "royaume-uni" to 1.15, "united kingdom" to 1.15, "angleterre" to 1.15,
        "pays-bas" to 1.05, "netherlands" to 1.05, "belgique" to 1.0, "belgium" to 1.0, "suisse" to 1.6, "switzerland" to 1.6,
        "autriche" to 1.0, "austria" to 1.0, "grece" to 0.75, "greece" to 0.75, "tchequie" to 0.65, "czechia" to 0.65,
        "republique tcheque" to 0.65, "pologne" to 0.6, "poland" to 0.6, "hongrie" to 0.6, "hungary" to 0.6,
        "irlande" to 1.15, "ireland" to 1.15, "suede" to 1.15, "sweden" to 1.15, "norvege" to 1.4, "norway" to 1.4,
        "danemark" to 1.3, "denmark" to 1.3, "croatie" to 0.7, "croatia" to 0.7, "turquie" to 0.5, "turkey" to 0.5,
        "maroc" to 0.45, "morocco" to 0.45, "algerie" to 0.4, "algeria" to 0.4, "tunisie" to 0.4, "tunisia" to 0.4,
        "egypte" to 0.35, "egypt" to 0.35, "emirats arabes unis" to 1.1, "united arab emirates" to 1.1,
        "arabie saoudite" to 0.9, "saudi arabia" to 0.9, "qatar" to 1.1, "japon" to 0.95, "japan" to 0.95,
        "chine" to 0.6, "china" to 0.6, "thailande" to 0.45, "thailand" to 0.45, "inde" to 0.3, "india" to 0.3,
        "indonesie" to 0.4, "indonesia" to 0.4, "vietnam" to 0.35, "coree du sud" to 0.85, "south korea" to 0.85,
        "etats-unis" to 1.25, "united states" to 1.25, "canada" to 1.05, "mexique" to 0.55, "mexico" to 0.55,
        "bresil" to 0.55, "brazil" to 0.55, "argentine" to 0.45, "argentina" to 0.45, "australie" to 1.15, "australia" to 1.15,
        "senegal" to 0.5, "afrique du sud" to 0.55, "south africa" to 0.55,
    )

    private fun norm(s: String) = s.plain()

    fun of(country: String): Double = byCountry[norm(country)] ?: 0.9
}

enum class BudgetItem(val label: String, val emoji: String) {
    FLIGHT("Vols", "✈️"),
    HOTEL("Hébergement", "🏨"),
    MEALS("Repas", "🍽️"),
    ACTIVITIES("Visites et sorties", "🎟️"),
    TRANSPORT("Transports sur place", "🚇"),
    EXTRAS("Imprévus et souvenirs", "🛍️"),
}

/** Un élément choisi par la personne (vol, hôtel…) avec son prix en euros. */
data class Chosen(val name: String, val amountEur: Double)

/** Une activité choisie (étape du programme ou lieu ajouté au voyage). */
data class PlannedActivity(val name: String, val kind: String, val category: ActivityLinks.Category? = null)

data class BudgetInput(
    val city: String,
    val country: String,
    val travellers: Int = 2,
    val nights: Int = 3,
    val level: Quiz.Budget = Quiz.Budget.MEDIUM,
    val mobility: Quiz.Mobility = Quiz.Mobility.TRANSIT,
    val flights: List<Chosen> = emptyList(),        // vols gardés (prix pour le groupe)
    val hotel: Chosen? = null,                      // hôtel réservé ou mis en favori (prix du séjour)
    val hotelBooked: Boolean = false,
    val activities: List<PlannedActivity> = emptyList(),
    val includeDaily: Boolean = true,               // ajouter repas, transports et imprévus estimés
)

/**
 * Une ligne du budget. [details] liste les éléments choisis qui la composent ;
 * [missing] = rien n'a encore été choisi pour ce poste.
 */
data class BudgetLine(
    val item: BudgetItem,
    val amountEur: Double,
    val source: String,
    val fromData: Boolean,
    val details: List<Chosen> = emptyList(),
    val missing: Boolean = false,
    val freeCount: Int = 0,          // lieux gratuits inclus (places, ponts, jardins…), non listés
)

object BudgetCalculator {
    private fun norm(s: String) = s.plain()

    fun days(nights: Int) = (nights + 1).coerceAtLeast(1)

    /** Ce que coûte une activité : entrée payante, consommation (restaurant, café, bar) ou rien. */
    enum class CostKind { FREE, TICKET, FOOD, DRINK }

    private val foodWords = listOf("restaurant", "brasserie", "bistrot", "bistro", "pizzeria", "creperie", "trattoria",
        "taverne", "gastronom", "cuisine", "fast food", "fast-food", "snack", "boulangerie", "patisserie", "traiteur")

    fun costKind(a: PlannedActivity): CostKind {
        val k = norm(a.kind)
        return when {
            a.category == ActivityLinks.Category.RESTAURANTS || foodWords.any { it in k } -> CostKind.FOOD
            a.category in setOf(ActivityLinks.Category.CAFES, ActivityLinks.Category.BARS, ActivityLinks.Category.NIGHTLIFE) ||
                "cafe" in k || "salon de the" in k || Regex("\\b(bar|pub)\\b").containsMatchIn(k) || "boite" in k || "club" in k -> CostKind.DRINK
            ticketPrice(a) > 0 -> CostKind.TICKET
            else -> CostKind.FREE
        }
    }

    /**
     * Prix d'entrée habituel par personne (en France). Seuls les lieux qui font vraiment payer
     * l'entrée comptent : places, ponts, rues, quartiers, jardins, plages, églises… sont gratuits.
     */
    fun ticketPrice(a: PlannedActivity): Double {
        val k = norm(a.kind)
        return when {
            "eglise" in k || "cathedrale" in k || "basilique" in k || "chapelle" in k || "mosquee" in k ||
                "synagogue" in k || "temple" in k -> 0.0
            "parc d'attractions" in k || "parc a theme" in k || "disney" in k || "theme park" in k -> 60.0
            "spectacle" in k || "theatre" in k || "opera" in k || "concert" in k || "cabaret" in k -> 40.0
            "zoo" in k || "aquarium" in k -> 25.0
            "croisiere" in k || "excursion" in k || "visite guidee" in k -> 20.0
            "musee" in k || "museum" in k || "galerie" in k || "exposition" in k -> 15.0
            "chateau" in k || "palais" in k || "forteresse" in k || "citadelle" in k || "catacombe" in k -> 14.0
            "tour " in k || k.startsWith("tour") || "observatoire" in k || "belvedere payant" in k || "grande roue" in k -> 12.0
            "cinema" in k -> 12.0
            else -> when (a.category) {
                ActivityLinks.Category.MUSEUMS -> 15.0
                ActivityLinks.Category.ATTRACTIONS -> 20.0
                ActivityLinks.Category.TOURS -> 20.0
                else -> 0.0   // monuments en plein air, places, ponts, parcs, plages, marchés, shopping
            }
        }
    }

    /** Repas au restaurant / consommation par personne (en France), selon le niveau de budget. */
    fun foodPrice(a: PlannedActivity, level: Quiz.Budget): Double {
        val k = norm(a.kind)
        return when (costKind(a)) {
            CostKind.FOOD -> when {
                "boulangerie" in k || "patisserie" in k || "snack" in k || "fast" in k -> 9.0
                else -> when (level) { Quiz.Budget.SMALL -> 18.0; Quiz.Budget.MEDIUM -> 32.0; Quiz.Budget.COMFORT -> 65.0 }
            }
            CostKind.DRINK -> when {
                "boite" in k || "club" in k || a.category == ActivityLinks.Category.NIGHTLIFE -> 25.0
                "bar" in k || "pub" in k || a.category == ActivityLinks.Category.BARS -> 12.0
                else -> 6.0
            }
            else -> 0.0
        }
    }

    /**
     * Budget du séjour, en euros : uniquement ce qui est inclus dans le voyage (vols gardés,
     * hôtel réservé ou mis en favori, activités). Rien n'est ajouté pour un vol ou un hôtel
     * non choisi. Repas (hors restaurants choisis), transports et imprévus s'ajoutent en
     * estimation si [BudgetInput.includeDaily]. Les montants saisis ([overrides]) priment.
     */
    fun compute(i: BudgetInput, overrides: Map<BudgetItem, Double> = emptyMap()): List<BudgetLine> {
        val index = CostIndex.of(i.country)
        val people = i.travellers.coerceAtLeast(1)
        val days = days(i.nights)
        val levelMeals = when (i.level) { Quiz.Budget.SMALL -> 30.0; Quiz.Budget.MEDIUM -> 55.0; Quiz.Budget.COMFORT -> 100.0 }
        val transportDay = if (i.mobility == Quiz.Mobility.WALK) 2.0 else 7.0
        val lines = mutableListOf<BudgetLine>()

        if (i.flights.isNotEmpty() || BudgetItem.FLIGHT in overrides)
            lines += BudgetLine(BudgetItem.FLIGHT, i.flights.sumOf { it.amountEur }, "Vols gardés dans votre voyage", true, i.flights)
        if (i.hotel != null || BudgetItem.HOTEL in overrides)
            lines += BudgetLine(BudgetItem.HOTEL, i.hotel?.amountEur ?: 0.0,
                if (i.hotelBooked) "Hôtel réservé" else "Hôtel choisi", true, listOfNotNull(i.hotel))

        val acts = i.activities.distinctBy { norm(it.name) }
        val tickets = acts.filter { costKind(it) == CostKind.TICKET }
        val free = acts.count { costKind(it) == CostKind.FREE }
        if (tickets.isNotEmpty() || free > 0 || BudgetItem.ACTIVITIES in overrides) {
            val details = tickets.map { Chosen(it.name, ticketPrice(it) * index * people) }
            lines += BudgetLine(BudgetItem.ACTIVITIES, details.sumOf { it.amountEur }, "Entrées payantes de votre programme", true,
                details, freeCount = free)
        }

        val food = acts.filter { costKind(it) == CostKind.FOOD || costKind(it) == CostKind.DRINK }
        val foodDetails = food.map { Chosen(it.name, foodPrice(it, i.level) * index * people) }
        val chosenMeals = food.filter { costKind(it) == CostKind.FOOD }.sumOf { foodPrice(it, i.level) * index * people }
        if (i.includeDaily) {
            val otherMeals = (levelMeals * index * people * days - chosenMeals).coerceAtLeast(0.0)
            val all = foodDetails + Chosen("Autres repas (estimation)", otherMeals)
            lines += BudgetLine(BudgetItem.MEALS, all.sumOf { it.amountEur },
                if (food.isEmpty()) "Estimation : 3 repas par jour et par personne" else "Restaurants et cafés choisis + autres repas estimés",
                food.isNotEmpty(), if (food.isEmpty()) emptyList() else all)
            lines += BudgetLine(BudgetItem.TRANSPORT, transportDay * index * people * days,
                if (i.mobility == Quiz.Mobility.WALK) "Estimation : surtout à pied" else "Estimation : métro, bus, taxis occasionnels", false)
        } else if (food.isNotEmpty()) {
            lines += BudgetLine(BudgetItem.MEALS, foodDetails.sumOf { it.amountEur }, "Restaurants et cafés de votre programme", true, foodDetails)
        }

        val base = lines.map { line ->
            overrides[line.item]?.let { line.copy(amountEur = it, source = "Montant saisi", fromData = true) } ?: line
        }
        if (!i.includeDaily) return base
        val extrasBase = base.filter { it.item != BudgetItem.FLIGHT && it.item != BudgetItem.HOTEL }.sumOf { it.amountEur }
        val extras = overrides[BudgetItem.EXTRAS]?.let { BudgetLine(BudgetItem.EXTRAS, it, "Montant saisi", true) }
            ?: BudgetLine(BudgetItem.EXTRAS, extrasBase * 0.10, "10 % des dépenses sur place", false)
        return base + extras
    }
}

/** Lit un prix affiché (« 540 € », « US$1,234 », « ٥٤٠ MAD ») : montant et devise. */
object PriceText {
    private val symbols = linkedMapOf("US$" to "USD", "CA$" to "CAD", "A$" to "AUD", "€" to "EUR", "£" to "GBP",
        "¥" to "JPY", "₹" to "INR", "$" to "USD", "CHF" to "CHF")

    fun parse(text: String): Pair<Double, String>? {
        val digits = text.mapNotNull { c -> if (Character.isDigit(c)) Character.getNumericValue(c).digitToChar() else null }.joinToString("")
        val amount = digits.toDoubleOrNull() ?: return null
        val code = Regex("\\b[A-Z]{3}\\b").find(text)?.value
            ?: symbols.entries.firstOrNull { text.contains(it.key) }?.value ?: "EUR"
        return amount to code
    }
}

/** Taux de change (open.er-api.com, gratuit, sans clé), base euro. */
object Fx {
    @Volatile private var cache: Map<String, Double>? = null

    suspend fun rates(): Map<String, Double> {
        cache?.let { return it }
        val r = parse(PlacesApi.get("https://open.er-api.com/v6/latest/EUR"))
        if (r.isNotEmpty()) cache = r
        return r
    }

    fun parse(json: String): Map<String, Double> {
        val o = JSONObject(json).optJSONObject("rates") ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.optDouble(it) }.filterValues { !it.isNaN() && it > 0 }
    }

    /** Convertit un montant d'une devise vers l'euro (inchangé si le taux est inconnu). */
    fun toEur(amount: Double, currency: String, rates: Map<String, Double>): Double =
        if (currency.equals("EUR", true)) amount else rates[currency.uppercase()]?.let { amount / it } ?: amount
}
