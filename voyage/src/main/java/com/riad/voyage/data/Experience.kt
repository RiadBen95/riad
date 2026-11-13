package com.riad.voyage.data

import kotlin.random.Random

/** Réponses au questionnaire « Expérience ». */
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

    /** Étapes du questionnaire : une question de suivi par envie qui en propose. */
    fun steps(a: Answers): List<String> = buildList {
        add("city"); add("duration"); add("company"); add("interests")
        Interest.entries.filter { it in a.interests && Taste.of(it).isNotEmpty() }.forEach { add("taste:${it.name}") }
        add("pace"); add("budget")
    }

    enum class Pace(val label: String, val description: String, val morning: Int, val afternoon: Int) {
        RELAXED("Tranquille", "Peu de visites, du temps pour flâner", 1, 1),
        BALANCED("Équilibré", "Un bon mélange visites et pauses", 1, 2),
        INTENSE("Intense", "Voir un maximum de choses", 2, 2),
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
        val pace: Pace = Pace.BALANCED,
        val budget: Budget = Budget.MEDIUM,
    )
}

data class ExperienceStep(
    val time: String,
    val moment: String,
    val category: ActivityLinks.Category,
    val place: PointOfInterest,
    val why: String,
    val fromPreviousM: Int?,
)

data class ExperienceDay(val title: String, val steps: List<ExperienceStep>)

data class Experience(val city: String, val title: String, val subtitle: String, val days: List<ExperienceDay>)

/** Compose le programme à partir des réponses et des meilleurs lieux de chaque catégorie. */
object ExperienceBuilder {
    private val defaultActivities = listOf(
        ActivityLinks.Category.MONUMENTS, ActivityLinks.Category.MUSEUMS, ActivityLinks.Category.PARKS,
    )

    fun activityCategories(a: Quiz.Answers): List<ActivityLinks.Category> {
        val fromInterests = a.interests.mapNotNull { it.category }
        val base = fromInterests.ifEmpty { defaultActivities }
        // En famille, on glisse un parc ou une attraction pour les enfants.
        return if (a.company == Quiz.Company.FAMILY && base.none { it == ActivityLinks.Category.PARKS || it == ActivityLinks.Category.ATTRACTIONS }) {
            base + ActivityLinks.Category.PARKS
        } else base
    }

    private fun wantsCafe(a: Quiz.Answers) =
        a.pace == Quiz.Pace.RELAXED || Quiz.Interest.FOOD in a.interests || a.company == Quiz.Company.FAMILY

    private fun wantsBar(a: Quiz.Answers) = !a.duration.halfDay && a.company != Quiz.Company.FAMILY &&
        (Quiz.Interest.NIGHT in a.interests || a.company == Quiz.Company.COUPLE || a.company == Quiz.Company.FRIENDS)

    private fun wantsClub(a: Quiz.Answers) = !a.duration.halfDay && Quiz.Interest.NIGHT in a.interests &&
        a.company != Quiz.Company.FAMILY &&
        (Quiz.Taste.CLUBS in a.tastes || a.company == Quiz.Company.FRIENDS || a.company == Quiz.Company.SOLO)

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
        return tastesFor(a, category).mapNotNull { it.hint }.singleOrNull()
    }

    private fun normalize(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    /** Goût correspondant à ce lieu (nom, type, description), s'il y en a un. */
    fun matchingTaste(p: PointOfInterest, tastes: List<Quiz.Taste>): Quiz.Taste? {
        if (tastes.isEmpty()) return null
        val text = " " + normalize("${p.name} ${p.kind} ${p.description.orEmpty()}") + " "
        return tastes.firstOrNull { t -> t.keywords.any { k -> text.contains(normalize(k)) } }
    }

    /** Catégories à charger pour composer l'expérience. */
    fun neededCategories(a: Quiz.Answers): Set<ActivityLinks.Category> = buildSet {
        addAll(activityCategories(a))
        add(ActivityLinks.Category.RESTAURANTS)
        if (wantsCafe(a)) add(ActivityLinks.Category.CAFES)
        if (wantsBar(a)) add(ActivityLinks.Category.BARS)
        if (wantsClub(a)) add(ActivityLinks.Category.NIGHTLIFE)
    }

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

    fun build(a: Quiz.Answers, city: String, pools: Map<ActivityLinks.Category, List<PointOfInterest>>, seed: Int = 0): Experience {
        val random = Random(seed)
        val used = mutableSetOf<String>()
        val activities = activityCategories(a).filter { !pools[it].isNullOrEmpty() }.ifEmpty {
            defaultActivities.filter { !pools[it].isNullOrEmpty() }
        }
        var rotation = random.nextInt(activities.size.coerceAtLeast(1))

        fun budgetOk(p: PointOfInterest): Boolean {
            val level = priceLevel(p) ?: return true
            return when (a.budget) {
                Quiz.Budget.SMALL -> level <= 2
                Quiz.Budget.MEDIUM -> level <= 3
                Quiz.Budget.COMFORT -> true
            }
        }

        /** Meilleur lieu de la catégorie, en tenant compte du classement et de la distance au lieu précédent. */
        fun pick(category: ActivityLinks.Category, near: PointOfInterest?): PointOfInterest? {
            val tastes = tastesFor(a, category)
            val all = pools[category].orEmpty().filter { it.id !in used }
            // Les lieux qui correspondent aux goûts passent devant, dans l'ordre du classement.
            val pool = if (tastes.isEmpty()) all else {
                val (match, rest) = all.partition { matchingTaste(it, tastes) != null }
                match + rest
            }
            val affordable = pool.filter { budgetOk(it) }.ifEmpty { pool }
            if (affordable.isEmpty()) return null
            val top = affordable.take(10)
            val matching = if (tastes.isEmpty()) 0 else top.count { matchingTaste(it, tastes) != null }
            val choice = if (near == null || !near.hasCoordinates) {
                // Un peu de variété quand on régénère : un des trois premiers (parmi ceux qui collent aux goûts).
                top[random.nextInt(minOf(3, if (matching > 0) matching else top.size))]
            } else {
                top.withIndex().minByOrNull { (i, p) ->
                    val km = if (p.hasCoordinates) PlacesParser.distanceMeters(near.lat, near.lon, p.lat, p.lon) / 1000 else 3.0
                    val comfortBonus = if (a.budget == Quiz.Budget.COMFORT && (priceLevel(p) ?: 0) >= 3) -0.5 else 0.0
                    val tastePenalty = if (matching > 0 && matchingTaste(p, tastes) == null) 3.0 else 0.0
                    i * 0.6 + km + comfortBonus + tastePenalty + random.nextDouble() * 0.3
                }!!.value
            }
            used += choice.id
            return choice
        }

        fun nextActivity(): ActivityLinks.Category? {
            if (activities.isEmpty()) return null
            return activities[rotation++ % activities.size]
        }

        fun why(category: ActivityLinks.Category, p: PointOfInterest): String {
            val rating = p.ratings.maxByOrNull { it.count }
            val taste = matchingTaste(p, tastesFor(a, category))
            val base = when {
                rating != null -> "Noté ${"%.1f".format(rating.rating)} sur ${rating.source.label} (${rating.count} avis)"
                p.isMustSee -> "Un incontournable de $city"
                p.inGuide -> "Recommandé par le guide Wikivoyage"
                category == ActivityLinks.Category.PARKS || category == ActivityLinks.Category.BEACHES -> "Pour souffler au grand air"
                else -> "Parmi les lieux les plus réputés de la ville"
            }
            return if (taste != null) "Pour votre envie « ${taste.label.lowercase()} » · $base" else base
        }

        val days = (1..a.duration.days).map { dayIndex ->
            val steps = mutableListOf<ExperienceStep>()
            var last: PointOfInterest? = null
            fun add(time: String, moment: String, category: ActivityLinks.Category?) {
                category ?: return
                val p = pick(category, last) ?: return
                val dist = last?.takeIf { it.hasCoordinates && p.hasCoordinates }
                    ?.let { PlacesParser.distanceMeters(it.lat, it.lon, p.lat, p.lon).toInt() }
                steps += ExperienceStep(time, moment, category, p, why(category, p), dist)
                last = p
            }

            add("9h30", "Matin", nextActivity())
            if (a.pace.morning >= 2) add("11h00", "Fin de matinée", nextActivity())
            add("12h30", "Déjeuner", ActivityLinks.Category.RESTAURANTS)
            if (!a.duration.halfDay) {
                add("14h30", "Après-midi", nextActivity())
                if (wantsCafe(a)) add("16h00", "Pause gourmande", ActivityLinks.Category.CAFES)
                if (a.pace.afternoon >= 2) add(if (wantsCafe(a)) "16h45" else "16h30", "Fin d'après-midi", nextActivity())
                add("20h00", "Dîner", ActivityLinks.Category.RESTAURANTS)
                if (wantsBar(a)) add("22h00", "Verre en soirée", ActivityLinks.Category.BARS)
                if (wantsClub(a) && dayIndex % 2 == 1) add("23h30", "Pour finir la nuit", ActivityLinks.Category.NIGHTLIFE)
            }
            ExperienceDay(if (a.duration.days > 1) "Jour $dayIndex" else a.duration.label, steps)
        }.filter { it.steps.isNotEmpty() }

        val themes = a.interests.map { i ->
            a.tastes.filter { it.interest == i }.takeIf { it.size == 1 }?.first()?.short ?: i.theme
        }.ifEmpty { listOf("découverte") }
        val themeText = when (themes.size) {
            1 -> themes[0]
            else -> themes.dropLast(1).joinToString(", ") + " et " + themes.last()
        }
        val subtitle = listOf(a.company.label, "rythme ${a.pace.label.lowercase()}", a.budget.label.lowercase()).joinToString(" · ")
        return Experience(city, "${a.duration.label} $themeText à $city", subtitle, days)
    }
}
