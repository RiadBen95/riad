package com.riad.voyage.data

import com.riad.voyage.tlist
import com.riad.voyage.tf
import com.riad.voyage.t
import kotlin.math.ln
import kotlin.math.max

/** Questionnaire « Expérience ». */
object Quiz {
    enum class Duration(val label: String, val days: Int, val halfDay: Boolean) {
        HALF("Une demi-journée", 1, true),
        DAY("Une journée", 1, false),
        TWO("Deux jours", 2, false),
        THREE("Trois jours", 3, false),
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
        SEAFOOD(Interest.FOOD, "Poissons et fruits de mer", "fruits de mer", listOf("poisson", "fruits de mer", "seafood", "bouillabaisse", "huitre", "fish"), "restaurants de fruits de mer"),

        COCKTAILS(Interest.NIGHT, "Bars à cocktails", "cocktails", listOf("cocktail", "speakeasy", "mixolog", "lounge"), "bars à cocktails"),
        WINE(Interest.NIGHT, "Bars à vin", "bars à vin", listOf("vin", "wine", "cave", "oeno"), "bars à vin"),
        PUBS(Interest.NIGHT, "Pubs et bières", "pubs", listOf("pub", "biere", "beer", "brasserie", "craft", "taproom"), "pubs"),
        LIVE(Interest.NIGHT, "Musique live et jazz", "musique live", listOf("live", "concert", "jazz", "musique", "blues"), "bars musique live"),
        CLUBS(Interest.NIGHT, "Boîtes de nuit", "boîtes de nuit", listOf("club", "discotheque", "boite", "dj", "dancing")),

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
    )

    /** Étapes du questionnaire : une question de suivi par envie qui en propose. */
    fun steps(a: Answers): List<String> = buildList {
        add("city"); add("duration"); add("company"); add("interests")
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

data class ExperienceDay(val title: String, val steps: List<ExperienceStep>)

data class Experience(val city: String, val title: String, val subtitle: String, val days: List<ExperienceDay>) {
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
    private val venueCategories = setOf(
        ActivityLinks.Category.RESTAURANTS, ActivityLinks.Category.CAFES, ActivityLinks.Category.BARS, ActivityLinks.Category.NIGHTLIFE,
    )

    fun activityCategories(a: Quiz.Answers): List<ActivityLinks.Category> {
        val base = a.interests.mapNotNull { it.category }.ifEmpty { defaultActivities }
        // En famille, on glisse un parc ou une attraction pour les enfants.
        return if (a.company == Quiz.Company.FAMILY && base.none { it == ActivityLinks.Category.PARKS || it == ActivityLinks.Category.ATTRACTIONS }) {
            base + ActivityLinks.Category.PARKS
        } else base
    }

    private fun wantsCafe(a: Quiz.Answers) =
        a.pace == Quiz.Pace.RELAXED || Quiz.Interest.FOOD in a.interests || a.company == Quiz.Company.FAMILY

    private fun wantsBar(a: Quiz.Answers) = !a.duration.halfDay && a.company != Quiz.Company.FAMILY &&
        a.diet != Quiz.Diet.NO_ALCOHOL &&
        (Quiz.Interest.NIGHT in a.interests || a.company == Quiz.Company.COUPLE || a.company == Quiz.Company.FRIENDS)

    private fun wantsClub(a: Quiz.Answers) = !a.duration.halfDay && Quiz.Interest.NIGHT in a.interests &&
        a.company != Quiz.Company.FAMILY && a.diet != Quiz.Diet.NO_ALCOHOL &&
        (Quiz.Taste.CLUBS in a.tastes || a.company == Quiz.Company.FRIENDS || a.company == Quiz.Company.SOLO)

    fun neededCategories(a: Quiz.Answers): Set<ActivityLinks.Category> = buildSet {
        addAll(activityCategories(a))
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
        if (category == ActivityLinks.Category.RESTAURANTS) {
            if (a.diet == Quiz.Diet.HALAL) return "restaurants halal"
            if (a.diet == Quiz.Diet.VEGETARIAN) return "restaurants végétariens"
        }
        if (category != ActivityLinks.Category.RESTAURANTS && category != ActivityLinks.Category.BARS) return null
        return tastesFor(a, category).mapNotNull { it.hint }.singleOrNull()
    }

    private fun normalize(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    /**
     * Correspondance entre un lieu et des goûts : le type du lieu compte le plus, puis son nom,
     * puis sa description (Wikipédia, guide). 0 = aucun rapport.
     */
    fun tasteScore(p: PointOfInterest, tastes: List<Quiz.Taste>): Int {
        if (tastes.isEmpty()) return 0
        val kind = " " + normalize(p.kind) + " "
        val name = " " + normalize(p.name) + " "
        val desc = " " + normalize(p.description.orEmpty().take(600)) + " "
        return tastes.maxOf { t ->
            t.keywords.sumOf { k ->
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

    private fun fmt(min: Int) = "${min / 60}h${"%02d".format(min % 60)}"

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
        a: Quiz.Answers,
        city: String,
        pools: Map<ActivityLinks.Category, List<PointOfInterest>>,
        avoid: Set<String> = emptySet(),
    ): Experience {
        val used = mutableSetOf<String>()
        val activities = activityCategories(a).filter { !pools[it].isNullOrEmpty() }.ifEmpty {
            defaultActivities.filter { !pools[it].isNullOrEmpty() }
        }

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
            val all = pools[category].orEmpty().filter { it.id !in used }
            val dietOk = if (category == ActivityLinks.Category.RESTAURANTS && a.diet.keywords.isNotEmpty()) {
                val matching = all.filter { p -> a.diet.keywords.any { normalize("${p.name} ${p.kind} ${p.description.orEmpty()}").contains(it) } }
                if (matching.size >= 2) matching else all
            } else all
            val affordable = dietOk.filter { budgetOk(it) }.ifEmpty { dietOk }
            if (tastes.isEmpty()) return affordable
            val matching = affordable.filter { tasteScore(it, tastes) > 0 }
            return if (matching.size >= 2) matching else affordable
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
            val k = normalize(p.kind)
            if (("marche" in k || "halle" in k) && startMin < 12 * 60) s += 1.0           // les marchés le matin
            if (("tour" in k || "belvedere" in k || "observation" in k || "point de vue" in k) && startMin >= 16 * 60) s += 1.5 // la vue en fin de journée
            if ("plage" in k && startMin >= 14 * 60) s += 1.0
            if (p.id in avoid) s -= 6.0
            return s
        }

        fun why(category: ActivityLinks.Category, p: PointOfInterest): String {
            val rating = p.ratings.maxByOrNull { it.count }
            val taste = matchingTaste(p, tastesFor(a, category))
            val base = when {
                rating != null -> tf("Noté {0} sur {1} ({2} avis)", "%.1f".format(rating.rating), rating.source.label, rating.count)
                p.sitelinks >= 60 -> tf("L'un des lieux les plus célèbres de {0}", t(city))
                p.isMustSee -> tf("Un incontournable de {0}", t(city))
                p.inGuide -> t("Recommandé par le guide Wikivoyage")
                category == ActivityLinks.Category.PARKS || category == ActivityLinks.Category.BEACHES -> t("Pour souffler au grand air")
                else -> t("Parmi les lieux les plus réputés de la ville")
            }
            return if (taste != null) tf("Pour votre envie « {0} » · {1}", t(taste.label).lowercase(), base) else base
        }

        val days = (1..a.duration.days).map { dayIndex ->
            val steps = mutableListOf<ExperienceStep>()
            var last: PointOfInterest? = null
            var lastCategory: ActivityLinks.Category? = null
            var clock = a.start.minutes

            fun travelFrom(p: PointOfInterest): Pair<Int?, Int> {
                val l = last ?: return null to 0
                if (!l.hasCoordinates || !p.hasCoordinates) return null to 15
                val m = PlacesParser.distanceMeters(l.lat, l.lon, p.lat, p.lon).toInt()
                return m to travelMinutes(m, a.mobility)
            }

            fun place(category: ActivityLinks.Category, p: PointOfInterest, moment: String, minutes: Int, fixedStart: Int? = null) {
                val (meters, travel) = travelFrom(p)
                val start = max(clock + travel, fixedStart ?: 0)
                steps += ExperienceStep(fmt(start), moment, category, p, why(category, p), meters, minutes, if (last == null) null else travel)
                used += p.id
                last = p
                lastCategory = category
                clock = start + minutes
            }

            /** Remplit un créneau avec les meilleures visites qui tiennent avant [until]. */
            fun fillVisits(until: Int, moment: (Int) -> String) {
                var guard = 0
                while (guard++ < 6 && activities.isNotEmpty()) {
                    val options = activities.flatMap { c -> candidates(c).take(25).map { c to it } }.mapNotNull { (c, p) ->
                        val (_, travel) = travelFrom(p)
                        val start = clock + travel + if (last == null) 0 else a.pace.bufferMin
                        val minutes = visitMinutes(p, c, a.depth)
                        val end = start + minutes
                        if (end > until) return@mapNotNull null
                        if (closesEarly(p, c) && end > 18 * 60) return@mapNotNull null
                        Triple(c, p, score(p, c, last, start, lastCategory)) to minutes
                    }
                    val best = options.maxByOrNull { it.first.third } ?: break
                    if (last != null) clock += a.pace.bufferMin
                    place(best.first.first, best.first.second, moment(clock), best.second)
                }
            }

            fun meal(category: ActivityLinks.Category, moment: String, at: Int) {
                val best = candidates(category).take(30).maxByOrNull { p ->
                    tasteScore(p, tastesFor(a, category)) * 1.5 + emblem(p) -
                        (travelFrom(p).second / (if (a.mobility == Quiz.Mobility.WALK) 6.0 else 12.0)) -
                        (if (p.id in avoid) 6.0 else 0.0)
                } ?: return
                place(category, best, moment, visitMinutes(best, category), fixedStart = at)
            }

            val lateStart = a.start == Quiz.Start.LATE
            // Matin
            fillVisits(12 * 60 + 30) { if (it < 11 * 60) "Matin" else "Fin de matinée" }
            meal(ActivityLinks.Category.RESTAURANTS, "Déjeuner", 12 * 60 + 30)
            val halfDayDone = a.duration.halfDay && !lateStart
            if (!halfDayDone) {
                val afternoonEnd = if (a.duration.halfDay) 17 * 60 else 19 * 60 + 15
                // Première visite de l'après-midi, pause gourmande, puis le reste du créneau.
                fillVisits(if (wantsCafe(a)) 16 * 60 + 15 else afternoonEnd) { "Après-midi" }
                if (wantsCafe(a) && clock <= 17 * 60) {
                    candidates(ActivityLinks.Category.CAFES).take(20).maxByOrNull { p ->
                        tasteScore(p, tastesFor(a, ActivityLinks.Category.CAFES)) + emblem(p) - travelFrom(p).second / 6.0
                    }?.let { place(ActivityLinks.Category.CAFES, it, "Pause gourmande", 45) }
                    fillVisits(afternoonEnd) { if (it >= 17 * 60) "Fin d'après-midi" else "Après-midi" }
                }
                if (!a.duration.halfDay) {
                    meal(ActivityLinks.Category.RESTAURANTS, "Dîner", 20 * 60)
                    if (wantsBar(a)) candidates(ActivityLinks.Category.BARS).take(20).maxByOrNull { p ->
                        tasteScore(p, tastesFor(a, ActivityLinks.Category.BARS)) * 1.5 + emblem(p) - travelFrom(p).second / 6.0 -
                            (if (p.id in avoid) 6.0 else 0.0)
                    }?.let { place(ActivityLinks.Category.BARS, it, "Verre en soirée", 90) }
                    if (wantsClub(a) && dayIndex % 2 == 1) candidates(ActivityLinks.Category.NIGHTLIFE).take(15).maxByOrNull { p ->
                        emblem(p) - travelFrom(p).second / 10.0 - (if (p.id in avoid) 6.0 else 0.0)
                    }?.let { place(ActivityLinks.Category.NIGHTLIFE, it, "Pour finir la nuit", 120) }
                }
            }
            ExperienceDay(if (a.duration.days > 1) tf("Jour {0}", dayIndex) else t(a.duration.label), steps)
        }.filter { it.steps.isNotEmpty() }

        val themes = a.interests.map { i ->
            t(a.tastes.filter { it.interest == i }.takeIf { it.size == 1 }?.first()?.short ?: i.theme)
        }.ifEmpty { listOf(t("découverte")) }
        val themeText = tlist(themes)
        val subtitle = listOfNotNull(
            t(a.company.label), t(a.style.label).lowercase(), tf("rythme {0}", t(a.pace.label).lowercase()),
            t(a.mobility.label).lowercase(), a.diet.takeIf { it != Quiz.Diet.NONE }?.label?.let { t(it).lowercase() }, t(a.budget.label).lowercase(),
        ).joinToString(" · ")
        return Experience(city, tf("{0} {1} à {2}", t(a.duration.label), themeText, t(city)), subtitle, days)
    }
}
