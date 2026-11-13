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
        (a.company == Quiz.Company.FRIENDS || a.company == Quiz.Company.SOLO)

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
            val pool = pools[category].orEmpty().filter { it.id !in used }
            val affordable = pool.filter { budgetOk(it) }.ifEmpty { pool }
            if (affordable.isEmpty()) return null
            val top = affordable.take(10)
            val choice = if (near == null || !near.hasCoordinates) {
                // Un peu de variété quand on régénère : un des trois premiers.
                top[random.nextInt(minOf(3, top.size))]
            } else {
                top.withIndex().minByOrNull { (i, p) ->
                    val km = if (p.hasCoordinates) PlacesParser.distanceMeters(near.lat, near.lon, p.lat, p.lon) / 1000 else 3.0
                    val comfortBonus = if (a.budget == Quiz.Budget.COMFORT && (priceLevel(p) ?: 0) >= 3) -0.5 else 0.0
                    i * 0.6 + km + comfortBonus + random.nextDouble() * 0.3
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
            return when {
                rating != null -> "Noté ${"%.1f".format(rating.rating)} sur ${rating.source.label} (${rating.count} avis)"
                p.isMustSee -> "Un incontournable de $city"
                p.inGuide -> "Recommandé par le guide Wikivoyage"
                category == ActivityLinks.Category.PARKS || category == ActivityLinks.Category.BEACHES -> "Pour souffler au grand air"
                else -> "Parmi les lieux les plus réputés de la ville"
            }
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

        val themes = a.interests.map { it.theme }.ifEmpty { listOf("découverte") }
        val themeText = when (themes.size) {
            1 -> themes[0]
            else -> themes.dropLast(1).joinToString(", ") + " et " + themes.last()
        }
        val subtitle = listOf(a.company.label, "rythme ${a.pace.label.lowercase()}", a.budget.label.lowercase()).joinToString(" · ")
        return Experience(city, "${a.duration.label} $themeText à $city", subtitle, days)
    }
}
