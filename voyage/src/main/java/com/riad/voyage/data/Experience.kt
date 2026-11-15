package com.riad.voyage.data

import com.riad.voyage.tlist
import com.riad.voyage.tf
import com.riad.voyage.t
import com.riad.voyage.tk
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Questionnaire « Expérience ». */
object Quiz {
    /** Durée du séjour : une demi-journée, ou un nombre de jours libre (l'expérience couvre tout le séjour). */
    data class Duration(val days: Int, val halfDay: Boolean = false) {
        val label: String
            get() = when {
                halfDay -> t("Une demi-journée")
                days == 1 -> t("Une journée")
                else -> tf("{0} jours", days)
            }

        companion object {
            const val MAX_DAYS = 30
            val HALF = Duration(1, halfDay = true)
            val DAY = Duration(1)
            val TWO = Duration(2)
            val THREE = Duration(3)
        }
    }

    enum class Company(val label: String, val emoji: String) {
        SOLO("Seul", "🧍"), COUPLE("En couple", "💑"), FRIENDS("Entre amis", "🧑‍🤝‍🧑"), FAMILY("En famille", "👨‍👩‍👧"),
    }

    enum class Interest(val label: String, val emoji: String, val theme: String, val category: ActivityLinks.Category?) {
        ART("Art et musées", "🎨", "art", ActivityLinks.Category.MUSEUMS),
        HISTORY("Histoire et monuments", "🏰", "histoire", ActivityLinks.Category.MONUMENTS),
        NATURE("Nature et parcs", "🌳", "nature", ActivityLinks.Category.PARKS),
        BEACH("Plage et mer", "🏖️", "plage", ActivityLinks.Category.BEACHES),
        FUN("Attractions et loisirs", "🎡", "loisirs", ActivityLinks.Category.ATTRACTIONS),
        SHOPPING("Shopping et marchés", "🛍️", "shopping", ActivityLinks.Category.SHOPPING),
        FOOD("Gastronomie", "🍽️", "gastronomie", null),
        NIGHT("Vie nocturne", "🍸", "sorties", null),
    }

    /**
     * Goûts précis pour chaque envie (questions de suivi). Les mots-clés servent à repérer les lieux
     * correspondants ; [hint] précise la recherche en ligne (restaurants et bars).
     */
    enum class Taste(
        val interest: Interest,
        val label: String,
        val short: String,
        val keywords: List<String>,
        val hint: String? = null,
    ) {
        CLASSIC_ART(Interest.ART, "Peinture et beaux-arts", "beaux-arts", listOf("beaux-arts", "beaux arts", "peinture", "louvre", "art", "galerie", "fine arts")),
        MODERN_ART(Interest.ART, "Art moderne et contemporain", "art moderne", listOf("moderne", "contemporain", "pompidou", "modern", "contemporary", "mac ")),
        SCIENCE(Interest.ART, "Sciences et techniques", "sciences", listOf("science", "technique", "histoire naturelle", "museum", "decouverte", "cite des", "aquarium", "planetarium")),
        SOCIETY(Interest.ART, "Histoire et civilisations", "musées d'histoire", listOf("histoire", "militaire", "civilisation", "societe", "archeolog", "traditions", "armee", "guerre")),

        CASTLES(Interest.HISTORY, "Châteaux et palais", "châteaux", listOf("chateau", "palais", "fort", "forteresse", "citadelle", "kasbah", "casbah", "manoir")),
        RELIGIOUS(Interest.HISTORY, "Lieux de culte", "lieux de culte", listOf("cathedrale", "basilique", "eglise", "mosquee", "synagogue", "temple", "abbaye", "monastere", "djamaa")),
        ANCIENT(Interest.HISTORY, "Sites antiques et archéologiques", "sites antiques", listOf("archeolog", "antique", "romain", "ruines", "amphitheatre", "theatre antique", "thermes")),
        LANDMARKS(Interest.HISTORY, "Monuments et places célèbres", "monuments", listOf("monument", "place", "arc ", "tour", "memorial", "pont", "fontaine", "porte")),

        GARDENS(Interest.NATURE, "Jardins et jardins botaniques", "jardins", listOf("jardin", "botanique", "roseraie", "serre")),
        BIG_PARKS(Interest.NATURE, "Grands parcs pour se balader", "grands parcs", listOf("parc", "bois", "foret", "promenade")),
        WILD(Interest.NATURE, "Nature sauvage", "nature sauvage", listOf("reserve", "parc national", "naturel", "lac", "montagne", "cascade", "gorges")),

        ANIMALS(Interest.FUN, "Zoos et aquariums", "zoos et aquariums", listOf("zoo", "aquarium", "animalier", "biodome", "safari")),
        THRILLS(Interest.FUN, "Parcs d'attractions", "parcs d'attractions", listOf("attractions", "parc d'attractions", "fete foraine", "disney", "aventure")),
        VIEWS(Interest.FUN, "Points de vue et panoramas", "panoramas", listOf("tour", "observation", "panorama", "vue", "belvedere", "point de vue", "roue")),
        SHOWS(Interest.FUN, "Spectacles et stades", "spectacles", listOf("opera", "theatre", "stade", "concert", "salle", "arena", "cirque")),

        LOCAL_FOOD(Interest.FOOD, "Cuisine locale et traditionnelle", "cuisine locale",
            listOf("tradition", "local", "regional", "francais", "algerien", "marocain", "tunisien", "bistrot", "brasserie", "terroir", "typique", "maison"),
            "restaurants traditionnels"),
        ITALIAN(Interest.FOOD, "Italien et pizza", "cuisine italienne", listOf("italien", "pizza", "pizzeria", "trattoria", "italian", "pasta"), "restaurants italiens"),
        ASIAN(Interest.FOOD, "Cuisine asiatique", "cuisine asiatique",
            listOf("japonais", "sushi", "chinois", "thai", "vietnamien", "coreen", "asiatique", "ramen", "indien", "asian", "japanese"), "restaurants asiatiques"),
        STREET(Interest.FOOD, "Street food et sur le pouce", "street food", listOf("street", "burger", "kebab", "snack", "fast", "sandwich", "tacos", "food truck"), "street food"),
        VEGGIE(Interest.FOOD, "Végétarien et vegan", "cuisine végétarienne", listOf("vegetarien", "vegan", "vegetarian", "vegetal", "veggie"), "restaurants végétariens"),
        FINE(Interest.FOOD, "Gastronomique", "gastronomie", listOf("gastronom", "etoile", "michelin", "fine dining", "chef"), "restaurants gastronomiques"),
        ORIENTAL(Interest.FOOD, "Libanais et oriental", "cuisine orientale",
            listOf("libanais", "oriental", "marocain", "tunisien", "algerien", "couscous", "tajine", "mezze", "lebanese", "syrien", "turc"),
            "restaurants libanais"),
        SEAFOOD(Interest.FOOD, "Poissons et fruits de mer", "fruits de mer", listOf("poisson", "fruits de mer", "seafood", "bouillabaisse", "huitre", "fish"), "restaurants de fruits de mer"),

        COCKTAILS(Interest.NIGHT, "Bars à cocktails", "cocktails", listOf("cocktail", "speakeasy", "mixolog", "lounge"), "bars à cocktails"),
        WINE(Interest.NIGHT, "Bars à vin", "bars à vin", listOf("vin", "wine", "cave", "oeno"), "bars à vin"),
        PUBS(Interest.NIGHT, "Pubs et bières", "pubs", listOf("pub", "biere", "beer", "brasserie", "craft", "taproom"), "pubs"),
        LIVE(Interest.NIGHT, "Musique live et jazz", "musique live", listOf("live", "concert", "jazz", "musique", "blues"), "bars musique live"),
        CLUBS(Interest.NIGHT, "Boîtes de nuit", "boîtes de nuit", listOf("club", "discotheque", "boite", "dj", "dancing")),

        LIVELY_BEACH(Interest.BEACH, "Plages animées", "plages animées", listOf("plage", "beach", "lido", "promenade", "station balneaire")),
        QUIET_BEACH(Interest.BEACH, "Criques et plages calmes", "criques", listOf("crique", "calanque", "anse", "cove", "sauvage")),

        MARKETS(Interest.SHOPPING, "Marchés et produits locaux", "marchés", listOf("marche", "halle", "souk", "bazar", "market", "medina")),
        MALLS(Interest.SHOPPING, "Centres commerciaux", "centres commerciaux", listOf("centre commercial", "mall", "galeries", "shopping")),
        FASHION(Interest.SHOPPING, "Mode et créateurs", "mode", listOf("mode", "luxe", "boutique", "createur", "concept", "fashion")),
        ;

        companion object {
            fun of(interest: Interest) = entries.filter { it.interest == interest }
        }
    }

    enum class Style(val label: String, val description: String) {
        ICONIC("Les incontournables", "Les lieux les plus célèbres, à ne pas manquer"),
        MIX("Un mélange", "Quelques classiques et des lieux moins connus"),
        HIDDEN("Hors des sentiers battus", "Des pépites appréciées des connaisseurs"),
    }

    enum class Pace(val label: String, val description: String, val bufferMin: Int) {
        RELAXED("Tranquille", "Peu de visites, du temps pour flâner", 30),
        BALANCED("Équilibré", "Un bon mélange visites et pauses", 15),
        INTENSE("Intense", "Voir un maximum de choses", 5),
    }

    enum class Depth(val label: String, val description: String, val factor: Double) {
        QUICK("Survoler", "Voir l'essentiel de chaque lieu", 0.7),
        NORMAL("Visite classique", "Le temps habituel de visite", 1.0),
        DEEP("Prendre mon temps", "Visiter en détail, lire, s'imprégner", 1.35),
    }

    enum class Start(val label: String, val minutes: Int) {
        EARLY("Tôt (8h30)", 8 * 60 + 30), NORMAL("Vers 9h30", 9 * 60 + 30), LATE("Grasse matinée (11h)", 11 * 60),
    }

    enum class Mobility(val label: String, val description: String) {
        WALK("Tout à pied", "Des lieux proches les uns des autres"),
        TRANSIT("Transports en commun", "On peut aller plus loin entre deux étapes"),
    }

    enum class Diet(val label: String, val keywords: List<String>) {
        NONE("Pas de préférence", emptyList()),
        HALAL("Halal", listOf("halal")),
        VEGETARIAN("Végétarien", listOf("vegetarien", "vegan", "vegetarian", "vegetal", "veggie")),
        NO_ALCOHOL("Sans alcool (pas de bars)", emptyList()),
    }

    enum class Budget(val label: String, val description: String) {
        SMALL("Petit budget", "Adresses abordables"),
        MEDIUM("Budget moyen", "Bon rapport qualité-prix"),
        COMFORT("Confort", "Se faire plaisir"),
    }

    data class Answers(
        val city: String = "",
        val place: Place? = null,
        val duration: Duration = Duration.DAY,
        val company: Company = Company.COUPLE,
        val interests: Set<Interest> = emptySet(),
        val tastes: Set<Taste> = emptySet(),
        val style: Style = Style.ICONIC,
        val pace: Pace = Pace.BALANCED,
        val depth: Depth = Depth.NORMAL,
        val start: Start = Start.NORMAL,
        val mobility: Mobility = Mobility.WALK,
        val diet: Diet = Diet.NONE,
        val budget: Budget = Budget.MEDIUM,
        val startDate: java.time.LocalDate = java.time.LocalDate.now(),
        val arrivalMin: Int? = null,          // heure d'arrivée le premier jour (minutes)
        val departureMin: Int? = null,        // heure de départ (avion, train) le dernier jour
        val wanted: List<String> = emptyList(),   // lieux demandés nommément (saisie libre)
        val avoidNames: List<String> = emptyList(), // lieux ou types à éviter (saisie libre)
        val restAfterArrival: Boolean = false,      // soirée libre à l'hôtel le jour de l'arrivée
        val country: String = "",                   // pays de la ville (cuisine locale, jours fériés)
        val travellers: Int? = null,                // nombre de voyageurs, s'il est connu (sinon déduit de la compagnie)
        val youngKids: Boolean = false,             // enfant de moins de 6 ans : rythme doux, sieste, visites courtes
        val stroller: Boolean = false,              // poussette : éviter escaliers et sites accidentés
        val budgetEur: Int? = null,                 // budget annoncé pour le séjour
        val maxWalkMin: Int? = null,                // marche maximale entre deux étapes
        val extraNotes: List<String> = emptyList(), // remarques d'Envolée AI (saison, demande impossible…)
        val outings: List<String> = emptyList(),    // balades de quartier et excursions proposées par Envolée AI
    )

    /**
     * Étapes du questionnaire. Mode rapide : ville, durée, envies (le reste garde les dernières
     * réponses ou les valeurs par défaut, modifiables depuis le résultat). Mode détaillé : tout.
     */
    fun steps(a: Answers, quick: Boolean = false): List<String> = if (quick) listOf("city", "duration", "interests") else buildList {
        add("city"); add("date"); add("duration"); add("company"); add("interests")
        Interest.entries.filter { it in a.interests && Taste.of(it).isNotEmpty() }.forEach { add("taste:${it.name}") }
        add("style"); add("pace"); add("depth"); add("start"); add("mobility"); add("diet"); add("budget")
    }
}

data class ExperienceStep(
    val time: String,
    val moment: String,
    val category: ActivityLinks.Category,
    val place: PointOfInterest,
    val why: String,
    val fromPreviousM: Int?,
    val durationMin: Int = 0,
    val travelMin: Int? = null,
)

data class ExperienceDay(
    val title: String,
    val steps: List<ExperienceStep>,
    val date: java.time.LocalDate? = null,
    val weather: DayWeather? = null,
)

/** [notes] : compromis faits pour composer le programme (régime introuvable, envie écartée…), à montrer à la personne. */
data class Experience(val city: String, val title: String, val subtitle: String, val days: List<ExperienceDay>, val notes: List<String> = emptyList()) {
    val placeIds get() = days.flatMap { d -> d.steps.map { it.place.id } }.toSet()
}

/**
 * Compose le programme : lieux emblématiques qui correspondent aux goûts (analysés à partir du type,
 * du nom et de la description Wikipédia), temps de visite réaliste, trajets et horaires d'ouverture.
 */
object ExperienceBuilder {
    private val defaultActivities = listOf(
        ActivityLinks.Category.MONUMENTS, ActivityLinks.Category.MUSEUMS, ActivityLinks.Category.PARKS,
    )
    /** Repas et sorties : un lieu « à table » plutôt qu'une visite. */
    val venues get() = venueCategories
    private val venueCategories = setOf(
        ActivityLinks.Category.RESTAURANTS, ActivityLinks.Category.CAFES, ActivityLinks.Category.BARS, ActivityLinks.Category.NIGHTLIFE,
    )

    fun activityCategories(a: Quiz.Answers): List<ActivityLinks.Category> {
        // Les envies choisies décident des visites ; les visites par défaut seulement si rien n'est coché.
        if (a.interests.isEmpty()) return defaultActivities
        val base = a.interests.mapNotNull { it.category }
        // En famille, on glisse un parc ou une attraction pour les enfants (s'il y a des visites).
        return if (base.isNotEmpty() && a.company == Quiz.Company.FAMILY && base.none { it == ActivityLinks.Category.PARKS || it == ActivityLinks.Category.ATTRACTIONS }) {
            base + ActivityLinks.Category.PARKS
        } else base
    }

    private fun wantsCafe(a: Quiz.Answers) =
        a.pace == Quiz.Pace.RELAXED || Quiz.Interest.FOOD in a.interests || a.company == Quiz.Company.FAMILY

    private fun wantsBar(a: Quiz.Answers) = !a.duration.halfDay && a.company != Quiz.Company.FAMILY &&
        a.diet != Quiz.Diet.NO_ALCOHOL && Quiz.Interest.NIGHT in a.interests

    private fun wantsClub(a: Quiz.Answers) = !a.duration.halfDay && Quiz.Interest.NIGHT in a.interests &&
        a.company != Quiz.Company.FAMILY && a.diet != Quiz.Diet.NO_ALCOHOL &&
        (Quiz.Taste.CLUBS in a.tastes || a.company == Quiz.Company.FRIENDS || a.company == Quiz.Company.SOLO)

    fun neededCategories(a: Quiz.Answers): Set<ActivityLinks.Category> = buildSet {
        addAll(activityCategories(a))
        if (a.style != Quiz.Style.HIDDEN) addAll(iconicCategories)   // un lieu phare par jour, même sans envie de visite
        add(ActivityLinks.Category.RESTAURANTS)
        if (wantsCafe(a)) add(ActivityLinks.Category.CAFES)
        if (wantsBar(a)) add(ActivityLinks.Category.BARS)
        if (wantsClub(a)) add(ActivityLinks.Category.NIGHTLIFE)
    }

    /** Goûts actifs (envie cochée) qui concernent une catégorie de lieux. */
    fun tastesFor(a: Quiz.Answers, category: ActivityLinks.Category): List<Quiz.Taste> {
        val interests = when (category) {
            ActivityLinks.Category.RESTAURANTS, ActivityLinks.Category.CAFES -> listOf(Quiz.Interest.FOOD)
            ActivityLinks.Category.BARS, ActivityLinks.Category.NIGHTLIFE -> listOf(Quiz.Interest.NIGHT)
            else -> Quiz.Interest.entries.filter { it.category == category }
        }
        return a.tastes.filter { it.interest in interests && it.interest in a.interests }
    }

    /** Précision de recherche en ligne quand un seul goût est choisi (ex. « restaurants italiens »). */
    fun searchHint(a: Quiz.Answers, category: ActivityLinks.Category): String? {
        if (category != ActivityLinks.Category.RESTAURANTS && category != ActivityLinks.Category.BARS) return null
        val taste = tastesFor(a, category).mapNotNull { it.hint }.singleOrNull()
        if (category == ActivityLinks.Category.BARS) return taste
        // Le régime s'ajoute au goût (« restaurants traditionnels halal ») au lieu de le remplacer.
        val diet = when (a.diet) { Quiz.Diet.HALAL -> "halal"; Quiz.Diet.VEGETARIAN -> "végétariens"; else -> null }
        return when {
            diet == null -> taste
            taste == null -> "restaurants $diet"
            else -> "$taste $diet"
        }
    }

    private fun normalize(s: String) = s.plain()

    private val fastFood = listOf("kebab", "burger", "snack", "fast food", "fast-food", "tacos", "sandwich", "friterie", "doner")

    /** Restauration rapide, d'après le type et le nom du lieu. */
    fun isFastFood(p: PointOfInterest) = normalize("${p.kind} ${p.name}").let { t -> fastFood.any { it in t } }

    /** Pause gourmande : café, salon de thé, pâtisserie ou glacier (pas un restaurant). */
    fun isCafe(p: PointOfInterest) = normalize(p.kind).let { k ->
        "restaurant" !in k && listOf("cafe", "salon de the", "patisserie", "glacier", "coffee", "tea", "boulangerie").any { it in k }
    }

    /** Catégories où chercher les lieux phares de la ville (Sagrada Família, parc Güell…). */
    private val iconicCategories = listOf(ActivityLinks.Category.MONUMENTS, ActivityLinks.Category.MUSEUMS, ActivityLinks.Category.PARKS)

    /** Lieu phare : parmi les plus connus au monde (nombre de Wikipédias). */
    fun isIconic(p: PointOfInterest) = p.sitelinks >= 60

    /** Places et esplanades : lieux de passage (une par jour au plus). */
    private fun isSquare(p: PointOfInterest) = normalize(p.kind).let { it.startsWith("place") || it.startsWith("plaza") || it.startsWith("square") }

    /** Musées, palais, zoos… : horaires d'ouverture importants (10h au plus tôt si inconnus). */
    private fun hasHours(p: PointOfInterest, c: ActivityLinks.Category) = !p.isVenue && !p.isFree && closesEarly(p, c)

    /** Lieu demandé nommément dans la saisie libre. */
    private fun isWanted(a: Quiz.Answers, p: PointOfInterest) = a.wanted.any { w -> w.isNotBlank() && w.plain() in p.name.plain() }

    /** Familles de mots : « fruits de mer » écarte aussi les poissonneries et les « seafood ». */
    private val avoidFamilies = mapOf(
        "fruits de mer" to listOf("fruits de mer", "seafood", "poisson", "fish", "frutti di mare", "marisco", "pescado", "pesce", "huitre", "oyster"),
        "poisson" to listOf("poisson", "fish", "seafood", "pesce", "pescado"),
        "viande" to listOf("viande", "steak", "grill", "boucherie", "churrasc", "asador", "burger"),
        "alcool" to listOf("bar", "pub", "cave", "vin", "wine", "biere", "beer", "cocktail"),
    )

    /** Racines à éviter : « pizza » → « pizz » (pizzeria, pizze) ; mots de plus de 4 lettres, sans leur fin. */
    private fun avoidRoots(a: Quiz.Answers): List<String> = a.avoidNames.flatMap { w ->
        val k = w.plain().trim()
        avoidFamilies[k] ?: listOf(if (k.length > 4 && ' ' !in k) k.dropLast(1) else k)
    }.filter { it.isNotBlank() }

    private fun isAvoided(a: Quiz.Answers, p: PointOfInterest): Boolean {
        val text = " " + (p.name + " " + p.kind).plain() + " "
        return avoidRoots(a).any { r -> text.contains(" $r") }
    }

    /** Lieux trop étendus pour une visite (lignes fortifiées, réseaux, itinéraires de plusieurs kilomètres). */
    private fun isTooLarge(p: PointOfInterest) =
        Regex("\\b(ligne|line|linie|reseau|network|route|trail|itineraire|enceinte de|stelling)\\b").containsMatchIn(p.name.plain())

    /** Enseigne d'un café ou restaurant (« It's | caffè & bistrot » et « It's caffè – The Spark » : même enseigne). */
    private fun brand(p: PointOfInterest) = p.name.plain().split(Regex("[^a-z0-9]+")).filter { it.length > 1 }.take(2).joinToString(" ")

    /** Visites au plus par jour selon le rythme. */
    private fun maxVisits(pace: Quiz.Pace) = when (pace) { Quiz.Pace.RELAXED -> 3; Quiz.Pace.BALANCED -> 4; Quiz.Pace.INTENSE -> 6 }

    /** Horaires arrondis aux 5 minutes (10h08 → 10h10). */
    private fun round5(min: Int) = (min + 4) / 5 * 5

    /**
     * Correspondance entre un lieu et des goûts : le type du lieu compte le plus, puis son nom,
     * puis sa description (Wikipédia, guide). 0 = aucun rapport.
     */
    /** Cuisine du pays (mots du type de restaurant) : « locale » veut dire italienne à Milan, catalane à Barcelone. */
    private val cuisines = mapOf(
        "france" to listOf("francais", "bistrot", "brasserie", "bouchon", "creperie"),
        "italie" to listOf("italien", "trattoria", "osteria", "pizzeria", "pizza", "lombard", "milanais", "romain", "toscan", "sicilien"),
        "espagne" to listOf("espagnol", "catalan", "tapas", "paella", "basque", "andalou"),
        "portugal" to listOf("portugais"), "grece" to listOf("grec"), "algerie" to listOf("algerien"),
        "maroc" to listOf("marocain"), "tunisie" to listOf("tunisien"), "turquie" to listOf("turc"),
        "allemagne" to listOf("allemand", "bavarois"), "belgique" to listOf("belge"), "royaume uni" to listOf("britannique", "pub"),
        "japon" to listOf("japonais", "sushi", "ramen"), "liban" to listOf("libanais"), "mexique" to listOf("mexicain"),
        "etats unis" to listOf("americain", "burger"), "inde" to listOf("indien"), "thailande" to listOf("thai"),
    )
    private val otherCuisines = cuisines.values.flatten() + listOf("philippin", "chinois", "vietnamien", "coreen", "kebab", "asiatique", "fusion")

    private fun localWords(country: String) = cuisines[normalize(country)].orEmpty()

    /** Restaurant d'une cuisine étrangère au pays (« Restaurant philippin » à Milan). */
    fun isForeignCuisine(p: PointOfInterest, country: String): Boolean {
        val local = localWords(country).ifEmpty { return false }
        val kind = " " + normalize(p.kind) + " "
        return local.none { kind.contains(it) } && otherCuisines.any { it !in local && kind.contains(" $it") }
    }

    fun tasteScore(p: PointOfInterest, tastes: List<Quiz.Taste>, country: String = ""): Int {
        if (tastes.isEmpty()) return 0
        val kind = " " + normalize(p.kind) + " "
        val name = " " + normalize(p.name) + " "
        val desc = " " + normalize(p.description.orEmpty().take(600)) + " "
        return tastes.maxOf { t ->
            (t.keywords + if (t == Quiz.Taste.LOCAL_FOOD) localWords(country) else emptyList()).sumOf { k ->
                val kw = normalize(k)
                (if (kind.contains(kw)) 3 else 0) + (if (name.contains(kw)) 2 else 0) + (if (desc.contains(kw)) 1 else 0)
            }.coerceAtMost(6)
        }
    }

    fun matchingTaste(p: PointOfInterest, tastes: List<Quiz.Taste>): Quiz.Taste? =
        tastes.maxByOrNull { tasteScore(p, listOf(it)) }?.takeIf { tasteScore(p, listOf(it)) > 0 }

    /** Niveau de prix 1 à 4 (€ à €€€€) quand on le connaît. */
    fun priceLevel(p: PointOfInterest): Int? {
        val euros = p.kind.count { it == '€' }
        if (euros >= 2 || (euros == 1 && !p.kind.any { it.isDigit() })) return euros.coerceIn(1, 4)
        val n = Regex("(\\d+)").find(p.kind)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return when {
            n < 15 -> 1
            n < 35 -> 2
            n < 60 -> 3
            else -> 4
        }
    }

    /** Temps de visite réaliste, en minutes, selon le type de lieu, sa taille et la profondeur choisie. */
    fun visitMinutes(p: PointOfInterest, category: ActivityLinks.Category, depth: Quiz.Depth = Quiz.Depth.NORMAL): Int {
        val k = normalize(p.kind + " " + p.name)
        val big = p.sitelinks
        val base = when {
            category == ActivityLinks.Category.RESTAURANTS -> return 75
            category == ActivityLinks.Category.CAFES -> return 45
            category == ActivityLinks.Category.BARS -> return 90
            category == ActivityLinks.Category.NIGHTLIFE -> return 120
            "parc d'attractions" in k || "disney" in k -> 300
            "zoo" in k || "aquarium" in k -> 150
            "musee" in k || "museum" in k || "galerie" in k -> when {
                big >= 60 -> 180
                big >= 20 -> 120
                else -> 75
            }
            "chateau" in k || "palais" in k || "citadelle" in k || "forteresse" in k -> if (big >= 40) 150 else 90
            "cathedrale" in k || "basilique" in k || "mosquee" in k || "synagogue" in k || "eglise" in k ||
                "temple" in k || "abbaye" in k || "monastere" in k -> if (big >= 50) 60 else 40
            "site archeologique" in k || "archeolog" in k -> 90
            "jardin botanique" in k -> 90
            "parc national" in k || "reserve" in k -> 180
            "plage" in k -> 150
            // Tour, porte ou fontaine sans horaires ni billet : on la voit en passant.
            listOf("tour", "porte", "fontaine", "colonne").any { k.startsWith(it) } && p.openingHours == null && big < 40 -> return 15
            "tour" in k || "observation" in k || "belvedere" in k || "point de vue" in k -> 45
            "opera" in k || "theatre" in k || "stade" in k || "salle" in k -> 60
            "marche" in k || "halle" in k || "souk" in k -> 60
            "centre commercial" in k || "grand magasin" in k -> 90
            "place" in k || "pont" in k || "monument" in k || "arc " in k || "memorial" in k || "porte" in k || "fontaine" in k -> 30
            "parc" in k || "jardin" in k -> 60
            else -> when (category) {
                ActivityLinks.Category.MUSEUMS -> 90
                ActivityLinks.Category.MONUMENTS -> 45
                ActivityLinks.Category.ATTRACTIONS -> 90
                ActivityLinks.Category.PARKS -> 60
                ActivityLinks.Category.BEACHES -> 150
                ActivityLinks.Category.SHOPPING -> 60
                else -> 60
            }
        }
        return (Math.round(base * depth.factor / 5).toInt().coerceAtLeast(2)) * 5
    }

    /** Lieux visités à l'intérieur : fermés le soir (dernière entrée vers 17h). */
    private fun closesEarly(p: PointOfInterest, category: ActivityLinks.Category): Boolean {
        val k = normalize(p.kind)
        return category == ActivityLinks.Category.MUSEUMS || "musee" in k || "chateau" in k || "palais" in k ||
            "zoo" in k || "aquarium" in k || "parc d'attractions" in k || "archeolog" in k
    }

    /** Notoriété : nombre de Wikipédias, notes et nombre d'avis, recommandation du guide. */
    fun emblem(p: PointOfInterest): Double {
        val fame = ln(1.0 + p.sitelinks) / ln(200.0) * 4          // 0 à 4
        val rating = p.ratings.maxByOrNull { it.count }?.let { r ->
            (RatingsAggregator.bayes(r.rating, r.count) - 3.5) * 2 + ln(1.0 + r.count) / 4
        } ?: 0.0
        return fame + rating + (if (p.inGuide) 1.0 else 0.0)
    }

    fun travelMinutes(meters: Int, mobility: Quiz.Mobility): Int = when {
        meters <= 1500 || mobility == Quiz.Mobility.WALK -> (meters / 80.0).toInt().coerceAtLeast(3)
        else -> 10 + (meters / 1000.0 * 3).toInt()
    }

    fun isOutdoor(p: PointOfInterest, c: ActivityLinks.Category): Boolean {
        if (c == ActivityLinks.Category.PARKS || c == ActivityLinks.Category.BEACHES) return true
        val k = normalize(p.kind)
        return listOf("point de vue", "belvedere", "observation", "place", "pont", "fontaine", "jardin", "parc",
            "plage", "archeolog", "ruines", "monument", "arc ", "memorial", "porte", "marche", "zoo").any { it in k }
    }

    fun isIndoor(p: PointOfInterest, c: ActivityLinks.Category): Boolean {
        if (c == ActivityLinks.Category.MUSEUMS) return true
        val k = normalize(p.kind)
        return listOf("musee", "galerie", "chateau", "palais", "aquarium", "opera", "theatre", "salle", "centre commercial",
            "grand magasin", "cathedrale", "basilique", "eglise", "mosquee", "synagogue", "halle").any { it in k }
    }

    /** Raison affichée sous une étape : notes, notoriété, goûts, météo. */
    fun reason(a: Quiz.Answers, city: String, category: ActivityLinks.Category, p: PointOfInterest, w: DayWeather?): String {
        val rating = p.ratings.maxByOrNull { it.count }
        val label = (p.kind + " " + p.name).plain()
        val taste = tastesFor(a, category).firstOrNull { tt -> tt.keywords.any { it.plain().trim() in label } }
        val base = when {
            rating != null -> tf("Noté {0} sur {1} ({2} avis)", "%.1f".format(rating.rating), rating.source.label, rating.count)
            p.sitelinks >= 60 -> tf("L'un des lieux les plus célèbres de {0}", t(city))
            p.isMustSee -> tf("Un incontournable de {0}", t(city))
            p.inGuide -> t("Recommandé par le guide Wikivoyage")
            category == ActivityLinks.Category.PARKS || category == ActivityLinks.Category.BEACHES -> t("Pour souffler au grand air")
            p.sitelinks >= 20 -> t("Parmi les lieux les plus réputés de la ville")
            visitMinutes(p, category, a.depth) <= 15 -> tf("{0} à voir en passant", tk(p.kind.substringBefore(" · ")))
            else -> tf("{0} à visiter", tk(p.kind.substringBefore(" · ")))
        }
        val withTaste = if (taste != null) tf("Pour votre envie « {0} » · {1}", t(taste.label).lowercase(), base) else base
        return when {
            w != null && w.rainy && isIndoor(p, category) && visitMinutes(p, category, a.depth) > 15 -> t("Idéal par temps de pluie") + " · " + withTaste
            w != null && w.sunny && isOutdoor(p, category) -> t("Parfait sous le soleil prévu") + " · " + withTaste
            else -> withTaste
        }
    }

    private fun parseTime(t: String): Int? =
        Regex("(\\d{1,2})h(\\d{2})").find(t)?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }

    /** Heure minimale de certaines étapes (repas). */
    private fun fixedStart(moment: String): Int? = when (moment) {
        "Déjeuner" -> 12 * 60 + 30
        "Dîner" -> 19 * 60
        "Verre en soirée" -> 21 * 60 + 30
        else -> null
    }

    /** Recalcule horaires, trajets et distances d'une journée après une modification. */
    fun retime(a: Quiz.Answers, steps: List<ExperienceStep>, date: java.time.LocalDate? = null): List<ExperienceStep> {
        if (steps.isEmpty()) return steps
        var clock = parseTime(steps.first().time) ?: a.start.minutes
        var prev: ExperienceStep? = null
        var lunchEnd: Int? = null
        return steps.map { st ->
            if (st.moment == "Déjeuner") lunchEnd = null
            // La première étape garde son heure ; les suivantes s'enchaînent.
            val before = prev ?: run { clock += st.durationMin; prev = st; return@map st.copy(fromPreviousM = null, travelMin = null) }
            val meters = before.place.takeIf { it.hasCoordinates && st.place.hasCoordinates }
                ?.let { PlacesParser.distanceMeters(it.lat, it.lon, st.place.lat, st.place.lon).toInt() }
            val travel = if (meters == null) 15 else travelMinutes(meters, a.mobility)
            val buffer = if (fixedStart(st.moment) == null) a.pace.bufferMin else 0
            // Repas et pauses ne sont jamais avancés (heure d'origine, après minuit comprise) ; café ≥ déjeuner + 1 h 30.
            val own = if (isAnchored(st) || st.moment == "Pause gourmande") parseTime(st.time)?.let { if (it < clock - 6 * 60) it + 24 * 60 else it } ?: 0 else 0
            val afterLunch = if (st.moment == "Pause gourmande") (lunchEnd ?: 0) + 90 else 0
            val earliest = round5(maxOf(clock + travel + buffer, fixedStart(st.moment) ?: 0, own, afterLunch))
            // Lieu aux horaires connus : pas avant son ouverture (pause de midi comprise).
            val start = date?.let { OpeningHours.nextStart(st.place.openingHours, it.dayOfWeek, earliest, st.durationMin, it.monthValue) } ?: earliest
            clock = start + st.durationMin
            if (st.moment == "Déjeuner") lunchEnd = clock
            val out = st.copy(time = fmt(start), fromPreviousM = meters, travelMin = travel)
            prev = out
            out
        }
    }

    /**
     * Lieux de remplacement pour une étape : même type de repas ou de sortie, ou n'importe quelle
     * visite pour une étape de visite ; jamais un lieu déjà au programme. Les meilleurs en premier.
     */
    fun alternatives(
        a: Quiz.Answers,
        exp: Experience,
        pools: Map<ActivityLinks.Category, List<PointOfInterest>>,
        dayIndex: Int,
        stepIndex: Int,
        limit: Int = 12,
    ): List<Pair<ActivityLinks.Category, PointOfInterest>> {
        val day = exp.days.getOrNull(dayIndex) ?: return emptyList()
        val step = day.steps.getOrNull(stepIndex) ?: return emptyList()
        val usedIds = exp.placeIds
        val categories = if (step.category in venueCategories) listOf(step.category)
        else activityCategories(a).ifEmpty { defaultActivities }
        val anchor = day.steps.getOrNull(stepIndex - 1)?.place ?: step.place
        return categories.flatMap { c -> pools[c].orEmpty().filter { it.id !in usedIds }.map { c to it } }
            .distinctBy { it.second.id }
            .sortedByDescending { (c, p) ->
                val km = if (anchor.hasCoordinates && p.hasCoordinates)
                    PlacesParser.distanceMeters(anchor.lat, anchor.lon, p.lat, p.lon) / 1000 else 3.0
                tasteScore(p, tastesFor(a, c)) * 1.2 + emblem(p) - km * (if (a.mobility == Quiz.Mobility.WALK) 0.8 else 0.3) +
                    (if (day.weather?.rainy == true && isOutdoor(p, c)) -3.0 else 0.0)
            }
            .take(limit)
    }

    /** Retire une étape (replacement = null) ou la remplace, puis recalcule la journée. */
    fun replaceStep(
        a: Quiz.Answers,
        exp: Experience,
        dayIndex: Int,
        stepIndex: Int,
        replacement: Pair<ActivityLinks.Category, PointOfInterest>?,
    ): Experience {
        val day = exp.days.getOrNull(dayIndex) ?: return exp
        val step = day.steps.getOrNull(stepIndex) ?: return exp
        val steps = day.steps.toMutableList()
        if (replacement == null) steps.removeAt(stepIndex)
        else {
            val (c, p) = replacement
            steps[stepIndex] = step.copy(
                category = c, place = p, why = reason(a, exp.city, c, p, day.weather),
                durationMin = visitMinutes(p, c, a.depth),
            )
        }
        val newDay = day.copy(steps = retime(a, steps, day.date))
        return exp.copy(days = exp.days.mapIndexed { i, d -> if (i == dayIndex) newDay else d })
    }

    /** Balade, excursion ou journée libre : pas un lieu des sources. */
    private fun freePlace(name: String) = transferPlace(name).copy(id = "free/$name", kind = "Temps libre")

    /** Pause (sieste des petits) : pas un lieu à visiter. */
    private fun pausePlace(name: String) = transferPlace(name).copy(id = "pause/$name", kind = "Pause")

    /** Étape de transfert (arrivée, départ) : pas un lieu à visiter. */
    private fun transferPlace(name: String) = PointOfInterest(
        id = "transfer/$name", name = name, kind = "Transfert", lat = 0.0, lon = 0.0, distanceM = -1, address = null,
        openingHours = null, website = null, phone = null, wikipedia = null, notable = false, isVenue = false, hasCoordinates = false,
    )

    /** Déplace une étape d'un cran dans sa journée ; les horaires sont recalculés. */
    fun moveStep(a: Quiz.Answers, exp: Experience, dayIndex: Int, stepIndex: Int, delta: Int): Experience {
        val day = exp.days.getOrNull(dayIndex) ?: return exp
        val target = stepIndex + delta
        if (stepIndex !in day.steps.indices || target !in day.steps.indices) return exp
        if (listOf(stepIndex, target).any { isAnchored(day.steps[it]) }) return exp   // repas, arrivée, départ : à leur heure
        val steps = day.steps.toMutableList().apply { add(target, removeAt(stepIndex)) }
        return exp.copy(days = exp.days.mapIndexed { i, d -> if (i == dayIndex) d.copy(steps = retime(a, steps, day.date)) else d })
    }

    /** Ajoute en fin de journée une étape libre (lieu saisi à la main). */
    fun addStep(a: Quiz.Answers, exp: Experience, dayIndex: Int, name: String): Experience {
        val day = exp.days.getOrNull(dayIndex) ?: return exp
        val clean = name.replace(Regex("<[^>]*>"), "").trim().take(80)
        if (clean.isEmpty()) return exp
        val place = PointOfInterest(
            id = "custom/${System.currentTimeMillis()}", name = clean, kind = "Étape ajoutée", lat = 0.0, lon = 0.0,
            distanceM = -1, address = null, openingHours = null, website = null, phone = null, wikipedia = null,
            notable = false, isVenue = false, hasCoordinates = false,
        )
        // Avant le dîner (ou le départ) s'il y en a un : l'étape garde une heure de journée.
        val at = day.steps.indexOfFirst { it.moment == "Dîner" || it.moment == "Départ" }.takeIf { it >= 0 } ?: day.steps.size
        val start = day.steps.getOrNull(at - 1)?.let { (parseTime(it.time) ?: a.start.minutes) + it.durationMin } ?: a.start.minutes
        val step = ExperienceStep(fmt(start), "Étape ajoutée", ActivityLinks.Category.TOURS, place, "", null, 60)
        val steps = retime(a, day.steps.toMutableList().apply { add(at, step) }, day.date)
        // Journée trop pleine (dîner repoussé après 22 h 30 ou fin après 3 h du matin) : l'ajout est refusé.
        val clock = absolute(steps)
        val dinner = steps.indexOfFirst { it.moment == "Dîner" }
        if ((dinner >= 0 && clock[dinner] > 22 * 60 + 30) || clock.last() > 27 * 60) return exp
        return exp.copy(days = exp.days.mapIndexed { i, d -> if (i == dayIndex) d.copy(steps = steps) else d })
    }

    /** Change l'heure d'une étape ; les suivantes sont décalées d'autant (rien n'est recomposé). */
    fun setStepTime(a: Quiz.Answers, exp: Experience, dayIndex: Int, stepIndex: Int, minutes: Int): Experience {
        val day = exp.days.getOrNull(dayIndex) ?: return exp
        val step = day.steps.getOrNull(stepIndex) ?: return exp
        val tail = retime(a, listOf(step.copy(time = fmt(minutes))) + day.steps.drop(stepIndex + 1), day.date)
        return exp.copy(days = exp.days.mapIndexed { i, d -> if (i == dayIndex) d.copy(steps = day.steps.take(stepIndex) + tail) else d })
    }

    /** Heures de début en minutes depuis le matin (au-delà de 24 h après minuit). */
    private fun absolute(steps: List<ExperienceStep>): List<Int> {
        var prev = -1
        return steps.map { st -> var m = parseTime(st.time) ?: 0; while (prev >= 0 && m < prev - 6 * 60) m += 24 * 60; prev = m; m }
    }

    /** Temps libre entre deux étapes (« 14h30 → 20h00 »), s'il dure au moins 1 h 15 ; sinon null. */
    fun freeTime(time: String, durationMin: Int, nextTime: String): String? {
        val end = (parseTime(time) ?: return null) + durationMin
        var next = parseTime(nextTime) ?: return null
        if (next < end - 6 * 60) next += 24 * 60   // l'étape suivante est après minuit
        return if (next - end >= 75) "${fmt(end)} → ${fmt(next)}" else null
    }

    /** Nouvelle durée d'une étape ; les suivantes sont recalculées. */
    fun setDuration(a: Quiz.Answers, exp: Experience, dayIndex: Int, stepIndex: Int, minutes: Int): Experience {
        val day = exp.days.getOrNull(dayIndex) ?: return exp
        if (stepIndex !in day.steps.indices) return exp
        val steps = day.steps.mapIndexed { i, st -> if (i == stepIndex) st.copy(durationMin = minutes) else st }
        return exp.copy(days = exp.days.mapIndexed { i, d -> if (i == dayIndex) d.copy(steps = retime(a, steps, d.date)) else d })
    }

    /** Étape à heure fixe : repas, arrivée, départ. */
    fun isAnchored(s: ExperienceStep) = fixedStart(s.moment) != null || s.place.id.startsWith("transfer/")

    /** Heure affichée : après minuit, « 0h35 » (les minutes au-delà de 24 h restent dans l'ordre de la journée). */
    private fun fmt(min: Int) = Math.floorMod(min, 24 * 60).let { "${it / 60}h${"%02d".format(it % 60)}" }

    fun formatDuration(min: Int): String = when {
        min < 60 -> "$min min"
        min % 60 == 0 -> "${min / 60} h"
        else -> "${min / 60} h ${"%02d".format(min % 60)}"
    }

    /**
     * @param avoid lieux de la proposition précédente : « Autre proposition » passe aux suivants
     * du classement au lieu de tirer au hasard.
     */
    fun build(
        answers: Quiz.Answers,
        city: String,
        pools: Map<ActivityLinks.Category, List<PointOfInterest>>,
        avoid: Set<String> = emptySet(),
        weather: Map<java.time.LocalDate, DayWeather> = emptyMap(),
    ): Experience {
        val a = answers.copy(
            duration = answers.duration.copy(days = answers.duration.days.coerceIn(1, Quiz.Duration.MAX_DAYS)),
            pace = if (answers.youngKids) Quiz.Pace.RELAXED else answers.pace,   // petits enfants : rythme doux
        )
        val notes = linkedSetOf<String>()   // compromis à signaler, sans doublon
        notes += a.extraNotes
        // Lieu phare : parmi les 5 lieux les plus connus de la ville (et au plus le seuil mondial) : Naples en a aussi.
        val iconicMin = iconicCategories.flatMap { pools[it].orEmpty() }.filterNot(::isTooLarge).map { it.sitelinks }
            .sortedDescending().getOrNull(4)?.coerceIn(15, 60) ?: 60
        fun isIconic(p: PointOfInterest) = p.sitelinks >= iconicMin && !isTooLarge(p)
        // « Les incontournables » : les 10 lieux les plus connus de la ville passent avant les goûts (Duomo, Offices…).
        val mustSee = if (a.style != Quiz.Style.ICONIC) emptySet() else iconicCategories.flatMap { pools[it].orEmpty() }
            .filterNot(::isTooLarge).sortedByDescending { it.sitelinks }.take(10).map { it.id }.toSet()
        fun isMinorTower(p: PointOfInterest) = p.id !in mustSee && listOf("tour", "porte").any { normalize(p.kind).startsWith(it) }
        val usedBrands = mutableSetOf<String>()
        val kindsTrip = mutableMapOf<String, Int>()             // types de lieux déjà au programme (tout le séjour)
        val lastUsed = mutableMapOf<String, Int>()              // dernier jour où une adresse a servi (réutilisation tournante)
        var dayNumber = 0
        val worship = listOf("mosquee", "eglise", "cathedrale", "basilique", "synagogue", "temple", "chapelle", "monastere", "abbaye")
        fun isWorship(p: PointOfInterest) = worship.any { normalize(p.kind).startsWith(it) }
        // Pays où le vendredi est le jour de la grande prière (repos hebdomadaire).
        val fridayRest = normalize(a.country) in setOf("algerie", "maroc", "tunisie", "egypte", "turquie", "jordanie", "arabie saoudite",
            "emirats arabes unis", "qatar", "oman", "koweit", "bahrein", "iran", "pakistan", "malaisie", "indonesie", "senegal", "mauritanie")
        var friday = false
        var sunday = false
        val outings = a.outings.toMutableList()
        val kidsKinds = listOf("zoo", "aquarium", "science", "decouverte", "attractions", "jardin", "parc", "plage", "ferme", "enfant", "jouet", "planetarium")
        /** Lieux en plein air : à éviter sous la pluie, à privilégier au soleil. */
        fun outdoor(p: PointOfInterest, c: ActivityLinks.Category): Boolean {
            if (c == ActivityLinks.Category.PARKS || c == ActivityLinks.Category.BEACHES) return true
            val k = normalize(p.kind)
            return listOf("point de vue", "belvedere", "observation", "place", "pont", "fontaine", "jardin", "parc",
                "plage", "archeolog", "ruines", "monument", "arc ", "memorial", "porte", "marche", "zoo").any { it in k }
        }
        fun indoor(p: PointOfInterest, c: ActivityLinks.Category): Boolean {
            if (c == ActivityLinks.Category.MUSEUMS) return true
            val k = normalize(p.kind)
            return listOf("musee", "galerie", "chateau", "palais", "aquarium", "opera", "theatre", "salle", "centre commercial",
                "grand magasin", "cathedrale", "basilique", "eglise", "mosquee", "synagogue", "halle").any { it in k }
        }
        var today: DayWeather? = null
        var dayAnchor: PointOfInterest? = null                  // première visite de la demi-journée (quartier)
        var iconicToday = false
        var todayDate = a.startDate
        var holiday = false                                     // jour férié (règle « PH » des horaires)
        val kindsToday = mutableMapOf<String, Int>()            // au plus 2 lieux du même type par jour
        var visitsToday = 0
        val usedToday = mutableSetOf<String>()
        val used = mutableSetOf<String>()
        val usedKinds = mutableSetOf<String>()
        val activities = activityCategories(a).filter { !pools[it].isNullOrEmpty() }

        fun budgetOk(p: PointOfInterest): Boolean {
            val level = priceLevel(p) ?: return true
            return when (a.budget) {
                Quiz.Budget.SMALL -> level <= 2
                Quiz.Budget.MEDIUM -> level <= 3
                Quiz.Budget.COMFORT -> true
            }
        }

        /** Candidats d'une catégorie : ceux qui collent aux goûts si on en a assez, sinon tous. */
        fun candidates(category: ActivityLinks.Category): List<PointOfInterest> {
            val tastes = tastesFor(a, category)
            val fresh = pools[category].orEmpty().filter { it.id !in used && !isAvoided(a, it) && !isTooLarge(it) }
                .filter { category !in venueCategories || brand(it) !in usedBrands }
                .filter { category != ActivityLinks.Category.CAFES || isCafe(it) }
                .filter { OpeningHours.ranges(it.openingHours, todayDate.dayOfWeek, todayDate.monthValue, holiday)?.isEmpty() != true }   // fermé ce jour-là
            val all = if (fresh.isEmpty() && category in venueCategories) {
                pools[category].orEmpty().filter { it.id !in usedToday && !isAvoided(a, it) }.also {
                    if (it.isNotEmpty()) notes += t("Peu d'adresses dans cette ville : certaines reviennent plusieurs fois.")
                }
            } else fresh
            val dietOk = if (category == ActivityLinks.Category.RESTAURANTS && a.diet.keywords.isNotEmpty()) {
                val matching = all.filter { p -> a.diet.keywords.any { normalize("${p.name} ${p.kind} ${p.description.orEmpty()}").contains(it) } }
                if (matching.isNotEmpty()) matching else all.also {
                    notes += tf("Aucun restaurant « {0} » trouvé pour certains repas : vérifiez sur place.", t(a.diet.label).lowercase())
                }
            } else all
            // Budget moyen : € et €€ d'abord, €€€ seulement s'il reste trop peu d'adresses.
            val affordable = (if (a.budget == Quiz.Budget.MEDIUM) dietOk.filter { (priceLevel(it) ?: 2) <= 2 }.takeIf { it.size >= 3 } else null)
                ?: dietOk.filter { budgetOk(it) }.ifEmpty { dietOk }
            // Restauration rapide (kebab, burger…) seulement si « Street food » est choisi.
            val noFastFood = if (category == ActivityLinks.Category.RESTAURANTS && Quiz.Taste.STREET !in a.tastes) {
                affordable.filterNot { isFastFood(it) }.takeIf { it.size >= 2 } ?: affordable
            } else affordable
            // Un repas se prend au restaurant, pas dans un bar.
            val meals = if (category == ActivityLinks.Category.RESTAURANTS) {
                noFastFood.filterNot { normalize(it.kind).let { k -> Regex("\\b(bar|pub|boite)\\b").containsMatchIn(k) } }.ifEmpty { noFastFood }
            } else noFastFood
            if (tastes.isEmpty()) return meals
            // Les goûts filtrent, sauf pour les lieux phares et ceux demandés nommément.
            val matching = meals.filter { tasteScore(it, tastes, a.country) > 0 || isIconic(it) || it.id in mustSee || isWanted(a, it) ||
                (Quiz.Taste.WINE in a.tastes && Regex("\\b(vin|wine|cave|chai)").containsMatchIn(normalize(it.name))) }
            if (matching.isNotEmpty() || meals.isEmpty()) return matching
            if (category == ActivityLinks.Category.CAFES) return meals   // les goûts « cuisine » ne visent pas les cafés
            // Rien pour ces goûts : on le dit ; pas de centre commercial pour des « marchés ».
            notes += tf("Rien trouvé pour « {0} » ici : d'autres lieux sont proposés.", tlist(tastes.map { t(it.short) }))
            return if (category == ActivityLinks.Category.SHOPPING) emptyList() else meals
        }

        /** Note d'un lieu pour l'étape : goûts, notoriété (selon le style), proximité, moment de la journée. */
        fun score(p: PointOfInterest, category: ActivityLinks.Category, last: PointOfInterest?, startMin: Int, lastCategory: ActivityLinks.Category?): Double {
            val tastes = tastesFor(a, category)
            var s = tasteScore(p, tastes) * 1.2
            val e = emblem(p)
            s += when (a.style) {
                Quiz.Style.ICONIC -> e * 1.5
                Quiz.Style.MIX -> e
                // Pépites : reconnues (guide, bonnes notes) sans être les plus touristiques.
                Quiz.Style.HIDDEN -> (if (p.inGuide) 2.0 else 0.0) + (if (p.sitelinks in 2..25) 1.5 else 0.0) - (if (p.sitelinks > 60) 2.0 else 0.0) + e * 0.4
            }
            if (last != null && last.hasCoordinates && p.hasCoordinates) {
                val m = PlacesParser.distanceMeters(last.lat, last.lon, p.lat, p.lon).toInt()
                val minutes = travelMinutes(m, a.mobility)
                s -= minutes / (if (a.mobility == Quiz.Mobility.WALK) 8.0 else 15.0)
                if (a.mobility == Quiz.Mobility.WALK && m > 3000) s -= 4.0
            }
            if (category == lastCategory && category !in venueCategories) s -= 1.5   // varier les plaisirs
            // Rester dans le même quartier que la première visite du jour (lieux voisins regroupés).
            val anchor = dayAnchor
            if (anchor != null && anchor.hasCoordinates && p.hasCoordinates) {
                val m = PlacesParser.distanceMeters(anchor.lat, anchor.lon, p.lat, p.lon)
                s -= m / (if (a.mobility == Quiz.Mobility.WALK) 700.0 else 2500.0)
            }
            val k = normalize(p.kind)
            if (("marche" in k || "halle" in k) && startMin < 12 * 60) s += 1.0           // les marchés le matin
            if (("tour" in k || "belvedere" in k || "observation" in k || "point de vue" in k) && startMin >= 16 * 60) s += 1.5 // la vue en fin de journée
            if ("plage" in k && startMin >= 14 * 60) s += 1.0
            if (p.id in avoid) s -= 6.0
            if (isWanted(a, p)) s += 8.0                                         // demandé nommément
            if (p.id in mustSee) s += 6.0                                        // incontournable de la ville
            if (Quiz.Taste.WINE in a.tastes && Regex("\\b(vin|vins|wine|cave|chai|chais|vignoble)\\b").containsMatchIn(normalize(p.name + " " + p.kind))) s += 5.0
            // Varier sur tout le séjour : au-delà de 2 lieux du même type (mosquées, églises…), les autres passent devant.
            s -= ((kindsTrip[normalize(p.kind)] ?: 0) - 1).coerceAtLeast(0) * 2.5
            if (a.youngKids && kidsKinds.any { it in k }) s += 3.0                 // un lieu pour les enfants
            if (a.stroller && listOf("tour", "ruines", "archeolog", "fortification", "belvedere", "colline").any { it in k }) s -= 3.0
            // Un lieu phare par jour (pas en extérieur sous la pluie).
            if (isIconic(p) && !iconicToday && a.style != Quiz.Style.HIDDEN && !(today?.rainy == true && outdoor(p, category))) s += 4.0
            today?.let { w ->
                val out = outdoor(p, category)
                val ins = indoor(p, category)
                if (w.rainy) { if (out) s -= 3.5; if (ins) s += 1.2 }
                if (w.sunny && out) s += 0.8
                if (w.hot && "plage" in k) s += 1.5
                if (w.cold && out) s -= 1.0
                // Le point de vue juste avant le coucher du soleil.
                val sunset = w.sunsetMin
                if (sunset != null && ("tour" in k || "belvedere" in k || "observation" in k || "point de vue" in k) &&
                    startMin in (sunset - 120)..(sunset - 15)
                ) s += 2.0
            }
            return s
        }

        fun why(category: ActivityLinks.Category, p: PointOfInterest): String = when {
            p.id.startsWith("pause/") -> t("Retour à l'hôtel ou un parc tranquille pour la sieste des petits.")
            p.id.startsWith("free/") -> if (p.name != t("Journée libre")) t("Idée d'Envolée AI : renseignez-vous sur le transport et les horaires sur place.")
                else t("Plus de nouveaux lieux pour ce jour : flânez, revenez sur un coup de cœur ou reposez-vous.")
            p.id == "transfer/" + t("Arrivée et installation") -> t("Trajet depuis l'aéroport ou la gare et dépôt des bagages.")
            p.id.startsWith("transfer/") -> t("Prévoir environ 2 h à l'aéroport et 1 h de trajet.")
            else -> reason(a, city, category, p, today) + (if (hasHours(p, category) && p.openingHours == null) " · " + t("horaires à vérifier") else "")
        }

        // Vol de nuit (avant 6 h) : on quitte l'hôtel la veille au soir ; le dernier jour n'a pas de programme.
        val departure = a.departureMin
        val nightDeparture = departure != null && departure < 6 * 60
        val lastIndex = if (nightDeparture && a.duration.days > 1) a.duration.days - 1 else a.duration.days
        // Pluie : un musée s'ajoute quand les envies n'ont rien de couvert (plein air seul, ou aucune envie de visite).
        val outdoorOnly = activities.all { it == ActivityLinks.Category.PARKS || it == ActivityLinks.Category.BEACHES }

        val days = (1..a.duration.days).map { dayIndex ->
            val date = a.startDate.plusDays((dayIndex - 1).toLong())
            val title = if (a.duration.days > 1) tf("Jour {0}", dayIndex) else t(a.duration.label)
            if (dayIndex > lastIndex) return@map ExperienceDay(title, emptyList(), date, weather[date])
            today = weather[date]
            todayDate = date
            holiday = Holidays.isHoliday(a.country, date)
            dayNumber = dayIndex
            friday = fridayRest && date.dayOfWeek == java.time.DayOfWeek.FRIDAY
            sunday = date.dayOfWeek == java.time.DayOfWeek.SUNDAY
            if (friday && dayIndex <= lastIndex) notes += tf("{0} (vendredi) : jour de la grande prière ; mosquées réservées au culte en milieu de journée, certains lieux ferment ou ouvrent tard.", title)
            if (holiday && dayIndex <= lastIndex) notes += tf("{0} : jour férié, beaucoup de lieux ferment ou réduisent leurs horaires ; vérifiez avant de partir.", title)
            iconicToday = false
            usedToday.clear()
            kindsToday.clear()
            dayAnchor = null
            visitsToday = 0
            val steps = mutableListOf<ExperienceStep>()
            var last: PointOfInterest? = null
            var lastCategory: ActivityLinks.Category? = null
            var clock = a.start.minutes
            val firstDay = dayIndex == 1
            val lastDay = dayIndex == lastIndex
            // Dernier jour : fin du programme à temps pour l'aéroport (2 h avant + 1 h de trajet) ;
            // les autres jours, la soirée peut finir après minuit (minutes au-delà de 24 h).
            var dayEnd = departure?.takeIf { lastDay }?.let { (if (nightDeparture) it + 24 * 60 else it) - 180 } ?: 30 * 60
            val arrival = a.arrivalMin?.takeIf { firstDay }
            if (arrival != null && dayEnd < arrival + 30) {   // départ avant l'arrivée : heure de départ ignorée
                notes += t("L'heure de départ tombe avant l'arrivée : elle n'a pas été prise en compte, vérifiez-la.")
                dayEnd = 30 * 60
            }

            fun travelFrom(p: PointOfInterest): Pair<Int?, Int> {
                val l = last ?: return null to 0
                if (!l.hasCoordinates || !p.hasCoordinates) return null to 15
                val m = PlacesParser.distanceMeters(l.lat, l.lon, p.lat, p.lon).toInt()
                return m to travelMinutes(m, a.mobility)
            }

            /** Trajet trop long pour la marche maximale demandée. */
            fun tooFar(p: PointOfInterest): Boolean {
                // À pied : pas plus de 40 min de marche entre deux étapes (ou la limite demandée), rien à plus de 6 km du centre.
                val limit = a.maxWalkMin ?: if (a.mobility == Quiz.Mobility.WALK) 40 else null
                if (a.mobility == Quiz.Mobility.WALK && p.distanceM > 6000 && p.id !in mustSee) return true
                return limit != null && last != null && travelFrom(p).second > limit
            }

            fun place(category: ActivityLinks.Category, p: PointOfInterest, moment: String, minutes: Int, fixedStart: Int? = null, exact: Boolean = false) {
                val (meters, travel) = travelFrom(p)
                val start = if (exact) fixedStart ?: clock else round5(max(clock + travel, fixedStart ?: 0))
                steps += ExperienceStep(fmt(start), moment, category, p, why(category, p), meters, minutes, if (last == null) null else travel)
                used += p.id
                usedToday += p.id
                if (category in venueCategories) { usedKinds += normalize(p.kind); usedBrands += brand(p) }
                kindsTrip[normalize(p.kind)] = (kindsTrip[normalize(p.kind)] ?: 0) + 1
                lastUsed[p.id] = dayNumber
                last = p
                lastCategory = category
                clock = start + minutes
            }

            /** Remplit un créneau avec les meilleures visites qui tiennent avant [until]. */
            fun fillVisits(until: Int, moment: (Int) -> String) {
                var guard = 0
                val end = min(until, dayEnd)
                while (guard++ < 6 && (activities.isNotEmpty() || a.style != Quiz.Style.HIDDEN) && visitsToday < maxVisits(a.pace)) {
                    // Lieux phares (ou demandés) d'autres catégories, même hors des envies cochées.
                    val extra = (if (a.style == Quiz.Style.HIDDEN) emptyList() else (iconicCategories - activities.toSet()).flatMap { c ->
                        pools[c].orEmpty().filter { it.id !in used && (isWanted(a, it) || it.id in mustSee || (!iconicToday && isIconic(it))) }.map { c to it }
                    }) + (if (today?.rainy == true && outdoorOnly && ActivityLinks.Category.MUSEUMS !in activities)   // pluie : un abri
                        candidates(ActivityLinks.Category.MUSEUMS).take(5).map { ActivityLinks.Category.MUSEUMS to it } else emptyList())
                    val forKids = if (a.youngKids) candidates(ActivityLinks.Category.ATTRACTIONS)
                        .filter { p -> kidsKinds.any { it in normalize(p.kind + " " + p.name) } }.take(5).map { ActivityLinks.Category.ATTRACTIONS to it } else emptyList()
                    val worshipMax = if (Quiz.Taste.RELIGIOUS in a.tastes) 2 else 1
                    val all = (activities.flatMap { c -> candidates(c).take(25).map { c to it } } + extra + forKids)
                        .ifEmpty {   // plus rien dans les envies : les autres lieux phares de la ville (le jardin d'Essai…)
                            iconicCategories.flatMap { c -> candidates(c).take(10).map { c to it } }
                        }
                        .filter { (_, p) -> !isWorship(p) || steps.count { isWorship(it.place) } < worshipMax }
                        .filter { (_, p) -> (kindsToday[normalize(p.kind)] ?: 0) < (if (isSquare(p) || isMinorTower(p)) 1 else 2) && !tooFar(p) }
                        // « Place du Dam » puis « Monument national sur la place du Dam » : un seul des deux.
                        .filter { (_, p) -> steps.none { st -> st.place.name.plain().let { n -> n.length > 4 && (n in p.name.plain() || p.name.plain() in n) } } }
                    // Rester dans le quartier de la demi-journée quand c'est possible.
                    val anchor = dayAnchor
                    val radius = if (a.mobility == Quiz.Mobility.WALK) 1800 else 5000
                    val near = if (anchor == null || !anchor.hasCoordinates) all else all.filter { (_, p) ->
                        !p.hasCoordinates || PlacesParser.distanceMeters(anchor.lat, anchor.lon, p.lat, p.lon) <= radius
                    }.ifEmpty { all }
                    val options = near.mapNotNull { (c, p) ->
                        val (_, travel) = travelFrom(p)
                        val known = OpeningHours.ranges(p.openingHours, todayDate.dayOfWeek, todayDate.monthValue, holiday) != null
                        val minutes = visitMinutes(p, c, a.depth).let { if (a.youngKids) min(it, 90) else it }   // petits : visites courtes
                        val earliest = clock + travel + (if (last == null) 0 else a.pace.bufferMin)
                        // Horaires inconnus d'un musée ou d'un palais : de 10 h à 18 h.
                        val start = (if (known) OpeningHours.nextStart(p.openingHours, todayDate.dayOfWeek, earliest, minutes, todayDate.monthValue, holiday)
                            else max(earliest, if (hasHours(p, c)) 10 * 60 else 0)) ?: return@mapNotNull null
                        val finish = start + minutes
                        if (finish > end) return@mapNotNull null
                        if (friday && isWorship(p) && start < 14 * 60 + 30 && finish > 11 * 60 + 30) return@mapNotNull null   // grande prière
                        if (sunday && isWorship(p) && !normalize(p.kind).startsWith("mosquee") && start < 12 * 60 + 15 && finish > 9 * 60 + 45)
                            return@mapNotNull null   // messe du dimanche matin
                        if (!known && closesEarly(p, c) && finish > 18 * 60) return@mapNotNull null
                        Triple(c, p, score(p, c, last, start, lastCategory)) to (minutes to start)
                    }
                    val best = options.maxByOrNull { it.first.third } ?: break
                    if (last != null) clock += a.pace.bufferMin
                    val p = best.first.second
                    place(best.first.first, p, moment(max(clock, best.second.second)), best.second.first, fixedStart = best.second.second)
                    if (today?.rainy == true && outdoorOnly && best.first.first == ActivityLinks.Category.MUSEUMS)
                        notes += t("Pluie prévue : une visite couverte a été ajoutée à vos envies de plein air.")
                    if (isIconic(p)) iconicToday = true
                    if (dayAnchor == null) dayAnchor = p
                    kindsToday[normalize(p.kind)] = (kindsToday[normalize(p.kind)] ?: 0) + 1
                    visitsToday++
                }
            }

            /**
             * Heure de début d'un lieu à partir de [at] : ouvert pendant toute l'étape, sans commencer après [latest]
             * (pas de « déjeuner » à 20 h parce que le restaurant n'ouvre que le soir). Null : pas ce lieu-là.
             */
            fun openAt(p: PointOfInterest, at: Int, minutes: Int, latest: Int): Int? =
                OpeningHours.nextStart(p.openingHours, todayDate.dayOfWeek, at, minutes, todayDate.monthValue, holiday)
                    ?.takeIf { it <= latest && it + minutes <= dayEnd }

            /** Café, bar ou sortie : le mieux noté parmi ceux ouverts à l'heure prévue. */
            fun venue(category: ActivityLinks.Category, moment: String, minutes: Int, limit: Int, latest: Int, rank: (PointOfInterest) -> Double) {
                val best = candidates(category).take(limit).filterNot(::tooFar)
                    .mapNotNull { p -> openAt(p, clock + travelFrom(p).second, minutes, latest)?.let { p to it } }
                    .maxByOrNull { rank(it.first) } ?: return
                place(category, best.first, moment, minutes, fixedStart = best.second)
            }

            fun meal(category: ActivityLinks.Category, moment: String, at: Int, latest: Int) {
                val local = Quiz.Taste.LOCAL_FOOD in tastesFor(a, category).ifEmpty { listOf(Quiz.Taste.LOCAL_FOOD) }
                val best = candidates(category).take(30).filterNot(::tooFar).mapNotNull { p ->
                    openAt(p, max(at, clock + travelFrom(p).second), visitMinutes(p, category), latest)?.let { p to it }
                }.maxByOrNull { (p, _) ->
                    tasteScore(p, tastesFor(a, category).ifEmpty { listOf(Quiz.Taste.LOCAL_FOOD) }, a.country) * 1.5 + emblem(p) -
                        (if (local && isForeignCuisine(p, a.country)) 4.0 else 0.0) -   // cuisine locale demandée
                        (if (normalize(p.kind) in usedKinds) 3.0 else 0.0) -          // varier les cuisines
                        (lastUsed[p.id]?.let { 15.0 - 2.0 * (dayNumber - it) } ?: 0.0).coerceAtLeast(0.0) -   // adresse déjà servie : la plus ancienne d'abord
                        (travelFrom(p).second / (if (a.mobility == Quiz.Mobility.WALK) 6.0 else 12.0)) -
                        (if (p.id in avoid) 6.0 else 0.0)
                } ?: pools[category].orEmpty().filter { it.id !in usedToday }.mapNotNull { p ->   // repli : le plus proche ouvert
                    openAt(p, max(at, clock + travelFrom(p).second), visitMinutes(p, category), latest + 30)?.let { p to it }
                }.minWithOrNull(compareBy({ it.first.id in used }, { travelFrom(it.first).second })) ?: return   // jamais servi d'abord
                place(category, best.first, moment, visitMinutes(best.first, category), fixedStart = best.second)
            }

            // Premier jour : l'arrivée à son heure réelle, puis le trajet et le dépôt des bagages
            // (30 min seulement pour une courte escale). Arrivée de nuit : le programme reprend le matin.
            if (arrival != null) {
                val install = if (dayEnd - arrival < 4 * 60) 30 else 90
                clock = arrival
                place(ActivityLinks.Category.TOURS, transferPlace(t("Arrivée et installation")), "Arrivée", install, fixedStart = arrival, exact = true)
                last = null
                clock = max(clock, a.start.minutes)
                // Soirée libre à l'hôtel : rien après une arrivée l'après-midi ; arrivée le matin : visites jusqu'à 18 h.
                if (a.restAfterArrival) {
                    if (arrival >= 15 * 60) return@map ExperienceDay(title, steps, date, today)
                    dayEnd = min(dayEnd, 18 * 60)
                }
            }
            // Matin seulement (sans visite choisie, par exemple « Gastronomie » seule : petit-déjeuner dans un café).
            if (activities.isEmpty() && wantsCafe(a) && clock <= 10 * 60 + 30) {
                venue(ActivityLinks.Category.CAFES, "Petit-déjeuner", 60, 20, latest = 10 * 60 + 30) { p ->
                    tasteScore(p, tastesFor(a, ActivityLinks.Category.CAFES)) + emblem(p)
                }
            }
            fillVisits(12 * 60 + 30) { if (it < 11 * 60) "Matin" else "Fin de matinée" }
            // Plus aucune visite possible ce jour-là : une balade ou une excursion, sinon une journée libre assumée.
            val noVisit = steps.none { it.category !in venueCategories && !it.place.id.startsWith("transfer/") }
            val wantsVisits = activities.isNotEmpty() || a.style != Quiz.Style.HIDDEN   // « gastronomie seule » : pas de journée libre
            if (noVisit && wantsVisits && clock <= 11 * 60 && dayEnd >= 17 * 60) {
                val idea = outings.removeFirstOrNull()
                val excursion = idea?.let { Regex("excursion|km", RegexOption.IGNORE_CASE).containsMatchIn(it) } == true
                place(ActivityLinks.Category.TOURS, freePlace(idea ?: t("Journée libre")), if (excursion) "Excursion" else "Balade",
                    if (excursion) 7 * 60 else 150, fixedStart = max(clock, 10 * 60))
                last = null
            }
            if (clock <= 14 * 60 && 12 * 60 + 30 + 75 <= dayEnd) meal(ActivityLinks.Category.RESTAURANTS, "Déjeuner", 12 * 60 + 30, latest = 14 * 60)
            dayAnchor = null   // nouveau quartier pour l'après-midi
            if (a.youngKids && steps.any { it.moment == "Déjeuner" } && clock + 75 <= dayEnd) {
                place(ActivityLinks.Category.TOURS, pausePlace(t("Pause au calme (sieste, goûter)")), "Pause", 75)
                last = null
            }
            // Demi-journée : matin et déjeuner (l'après-midi seulement si l'on arrive après midi).
            val halfDayDone = a.duration.halfDay && (arrival == null || arrival < 12 * 60)
            if (!halfDayDone) {
                val afternoonEnd = if (a.duration.halfDay) 17 * 60 else 19 * 60 + 15
                // Première visite de l'après-midi, pause gourmande, puis le reste du créneau.
                fillVisits(if (wantsCafe(a)) 16 * 60 + 15 else afternoonEnd) { "Après-midi" }
                // Pause gourmande : pas collée au déjeuner (au moins 1 h 30 après sa fin).
                val lunchEnd = steps.lastOrNull { it.moment == "Déjeuner" }?.let { (minutesOf(it.time) ?: 0) + it.durationMin }
                if (lunchEnd != null) clock = max(clock, min(lunchEnd + 90, 16 * 60))   // jamais en arrière
                if (wantsCafe(a) && clock <= 17 * 60 && clock + 45 <= dayEnd) {
                    venue(ActivityLinks.Category.CAFES, "Pause gourmande", 45, 20, latest = 17 * 60 + 30) { p ->
                        tasteScore(p, tastesFor(a, ActivityLinks.Category.CAFES)) + emblem(p) - travelFrom(p).second / 6.0
                    }
                    fillVisits(afternoonEnd) { if (it >= 17 * 60) "Fin d'après-midi" else "Après-midi" }
                }
                // Fin d'après-midi libre et envie de sortir : un apéritif (bar à vin si c'est le goût).
                val aperitif = wantsBar(a) && clock <= 17 * 60 + 45 && dayEnd >= 21 * 60   // un seul bar par jour : avant ou après le dîner
                if (aperitif) {
                    clock = max(clock, 18 * 60)
                    venue(ActivityLinks.Category.BARS, "Apéritif", 60, 20, latest = 19 * 60) { p ->
                        tasteScore(p, tastesFor(a, ActivityLinks.Category.BARS)) * 1.5 + emblem(p) - travelFrom(p).second / 6.0
                    }
                }
                // Dîner à 20 h, ou dès 19 h si l'on doit partir tôt dans la soirée.
                val dinnerAt = if (dayEnd < 20 * 60 + 75) 19 * 60 else 20 * 60
                if (!a.duration.halfDay && dinnerAt + 75 <= dayEnd) {
                    meal(ActivityLinks.Category.RESTAURANTS, "Dîner", dinnerAt, latest = 22 * 60)
                    if (wantsBar(a) && !aperitif) venue(ActivityLinks.Category.BARS, "Verre en soirée", 90, 20, latest = 23 * 60 + 30) { p ->
                        tasteScore(p, tastesFor(a, ActivityLinks.Category.BARS)) * 1.5 + emblem(p) - travelFrom(p).second / 6.0 -
                            (if (p.id in avoid) 6.0 else 0.0)
                    }
                    if (wantsClub(a) && dayIndex % 2 == 1 && clock <= 24 * 60) venue(ActivityLinks.Category.NIGHTLIFE, "Pour finir la nuit", 120, 15, latest = 25 * 60) { p ->
                        emblem(p) - travelFrom(p).second / 10.0 - (if (p.id in avoid) 6.0 else 0.0)
                    }
                }
            }
            if (lastDay && departure != null && dayEnd < 30 * 60) {
                place(ActivityLinks.Category.TOURS, transferPlace(t("Départ pour l'aéroport ou la gare")), "Départ", 60,
                    fixedStart = if (steps.isEmpty()) dayEnd else max(dayEnd, clock), exact = true)
            }
            ExperienceDay(title, steps, date, today)
        }
        // Jours sans rien d'ouvert (hors jour du vol de nuit) : on le dit plutôt que de les faire disparaître en silence.
        days.filterIndexed { i, d -> d.steps.isEmpty() && i + 1 <= lastIndex }.forEach { d ->
            notes += tf("{0} : aucun lieu ouvert trouvé ce jour-là.", d.title)
        }
        if (nightDeparture && a.duration.days > 1) notes += t("Vol de nuit : le départ de l'hôtel est prévu la veille au soir.")
        if (Quiz.Interest.NIGHT in a.interests) when {
            a.diet == Quiz.Diet.NO_ALCOHOL -> notes += t("Pas de bars au programme : vous avez choisi « Sans alcool ».")
            a.company == Quiz.Company.FAMILY -> notes += t("Pas de bars ni de sorties nocturnes au programme en famille.")
        }
        val allPlaces = pools.values.flatten()
        a.wanted.filter { w -> w.isNotBlank() && allPlaces.none { w.plain() in it.name.plain() } }
            .takeIf { it.isNotEmpty() }?.let { notes += tf("Introuvable dans nos sources : {0}.", it.joinToString(", ")) }
        val placedNames = days.flatMap { d -> d.steps.map { it.place.name.plain() } }
        a.wanted.filter { w -> w.isNotBlank() && allPlaces.any { w.plain() in it.name.plain() } && placedNames.none { w.plain() in it } }
            .forEach { notes += tf("« {0} » n'a pas pu être placé (fermé ces jours-là ou trop loin) : vérifiez ses horaires.", it) }
        if (days.none { d -> d.steps.any { !it.place.id.startsWith("transfer/") } } && days.any { it.steps.isNotEmpty() })
            notes += t("Vos heures d'arrivée et de départ ne laissent aucune journée d'activité : vérifiez-les.")
        if (a.stroller) notes += t("Poussette : les lieux avec escaliers ou terrain accidenté sont évités, vérifiez l'accès sur place.")
        if (a.duration.days > 1 && a.arrivalMin == null && a.departureMin == null)
            notes += t("Heures d'arrivée et de départ non précisées : premier et dernier jours complets. Ajoutez-les pour les adapter.")
        else if (a.duration.days > 1 && a.departureMin == null)
            notes += t("Heure de départ non précisée : le dernier jour est complet. Ajoutez-la pour prévoir le trajet.")
        val freeDays = days.count { d -> d.steps.any { it.place.id.startsWith("free/") } }
        if (freeDays > 0) notes += tf("{0} journée(s) sans nouveau lieu à visiter : balades, excursions ou temps libre proposés.", freeDays)

        val present = days.flatMap { d -> d.steps.map { it.category } }.toSet()
        val themes = a.interests.filter { i ->
            when (i) {
                Quiz.Interest.FOOD -> ActivityLinks.Category.RESTAURANTS in present
                Quiz.Interest.NIGHT -> ActivityLinks.Category.BARS in present || ActivityLinks.Category.NIGHTLIFE in present
                else -> i.category in present
            }
        }.map { i ->
            t(a.tastes.filter { it.interest == i }.takeIf { it.size == 1 }?.first()?.short ?: i.theme)
        }.ifEmpty { listOf(t("découverte")) }
        val themeText = tlist(themes)
        val subtitle = listOfNotNull(
            t(a.company.label), t(a.style.label).lowercase(), tf("rythme {0}", t(a.pace.label).lowercase()),
            t(a.mobility.label).lowercase(), a.diet.takeIf { it != Quiz.Diet.NONE }?.label?.let { t(it).lowercase() }, t(a.budget.label).lowercase(),
        ).joinToString(" · ")
        return Experience(city, tf("{0} {1} à {2}", t(a.duration.label), themeText, t(city)), subtitle,
            days.filter { it.steps.isNotEmpty() }, notes.toList())
    }
}
