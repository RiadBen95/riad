package com.riad.voyage

import com.riad.voyage.data.ActivityLinks.Category
import com.riad.voyage.data.ExperienceBuilder
import com.riad.voyage.data.PointOfInterest
import com.riad.voyage.data.Quiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperienceTest {
    private fun poi(cat: Category, i: Int, kind: String = "Lieu", fame: Int = 10, desc: String? = null) = PointOfInterest(
        id = "$cat/$i", name = "$cat $i", kind = kind, lat = 48.85 + i * 0.002, lon = 2.35 + i * 0.002,
        distanceM = i * 200, address = null, openingHours = null, website = null, phone = null, wikipedia = null,
        notable = true, isVenue = cat in setOf(Category.RESTAURANTS, Category.CAFES, Category.BARS, Category.NIGHTLIFE),
        sitelinks = fame, description = desc,
    )

    private val pools = Category.entries.associateWith { c ->
        (0 until 12).map { poi(c, it, if (c == Category.MUSEUMS) "Musée" else "Lieu", fame = 40 - it * 3) }
    }

    private fun minutes(t: String) = t.substringBefore('h').toInt() * 60 + t.substringAfter('h').toInt()

    @Test
    fun coupleDayHasVisitsMealsAndADrinkInOrder() {
        val a = Quiz.Answers(city = "Paris", interests = setOf(Quiz.Interest.ART, Quiz.Interest.FOOD, Quiz.Interest.NIGHT), company = Quiz.Company.COUPLE)
        val exp = ExperienceBuilder.build(a, "Paris", pools)
        val steps = exp.days.single().steps
        val cats = steps.map { it.category }
        assertEquals(2, cats.count { it == Category.RESTAURANTS })
        assertTrue(Category.MUSEUMS in cats && Category.CAFES in cats && Category.BARS in cats)
        assertEquals(steps.size, steps.map { it.place.id }.distinct().size)
        assertEquals("9h30", steps.first().time)
        // Les horaires avancent toujours, en tenant compte du temps de visite.
        steps.zipWithNext().forEach { (x, y) -> assertTrue("${x.time} → ${y.time}", minutes(y.time) >= minutes(x.time) + x.durationMin) }
        assertTrue(minutes(steps.first { it.moment == "Dîner" }.time) >= 20 * 60)
        // Les musées ferment : visite terminée avant 18h.
        steps.filter { it.category == Category.MUSEUMS }.forEach { assertTrue(minutes(it.time) + it.durationMin <= 18 * 60) }
    }

    @Test
    fun visitDurationsAreRealistic() {
        assertEquals(180, ExperienceBuilder.visitMinutes(poi(Category.MUSEUMS, 0, "Musée", fame = 120), Category.MUSEUMS))
        assertEquals(75, ExperienceBuilder.visitMinutes(poi(Category.MUSEUMS, 0, "Musée", fame = 3), Category.MUSEUMS))
        assertEquals(40, ExperienceBuilder.visitMinutes(poi(Category.MONUMENTS, 0, "Église", fame = 5), Category.MONUMENTS))
        assertEquals(300, ExperienceBuilder.visitMinutes(poi(Category.ATTRACTIONS, 0, "Parc d'attractions"), Category.ATTRACTIONS))
        assertEquals(100, ExperienceBuilder.visitMinutes(poi(Category.MUSEUMS, 0, "Musée", fame = 3), Category.MUSEUMS, Quiz.Depth.DEEP))
    }

    @Test
    fun tastesAreAnalysedFromTypeAndDescription() {
        val museums = listOf(
            poi(Category.MUSEUMS, 0, "Musée", fame = 150),                                            // très célèbre mais pas d'art moderne
            poi(Category.MUSEUMS, 1, "Musée d'art", fame = 20, desc = "Collection d'art moderne et contemporain."),
            poi(Category.MUSEUMS, 2, "Musée d'art moderne", fame = 15),
        )
        val a = Quiz.Answers(city = "Paris", duration = Quiz.Duration.HALF, interests = setOf(Quiz.Interest.ART), tastes = setOf(Quiz.Taste.MODERN_ART))
        val steps = ExperienceBuilder.build(a, "Paris", mapOf(Category.MUSEUMS to museums, Category.RESTAURANTS to pools.getValue(Category.RESTAURANTS))).days.single().steps
        val visited = steps.filter { it.category == Category.MUSEUMS }.map { it.place.id }
        assertTrue("MUSEUMS/0" !in visited)
        assertTrue(steps.first().why.startsWith("Pour votre envie « art moderne et contemporain »"))
        assertEquals("Déjeuner", steps.last().moment)
    }

    @Test
    fun choiceIsDeterministicAndAlternativeAvoidsPreviousPlaces() {
        val a = Quiz.Answers(city = "Paris", interests = setOf(Quiz.Interest.HISTORY))
        val first = ExperienceBuilder.build(a, "Paris", pools)
        assertEquals(first.placeIds, ExperienceBuilder.build(a, "Paris", pools).placeIds)
        // Style « incontournables » : on commence par le monument le plus célèbre.
        assertEquals("MONUMENTS/0", first.days.single().steps.first().place.id)
        val other = ExperienceBuilder.build(a, "Paris", pools, avoid = first.placeIds)
        assertNotEquals(first.days.single().steps.first().place.id, other.days.single().steps.first().place.id)
    }

    @Test
    fun familyAndNoAlcoholHaveNoBars() {
        val family = Quiz.Answers(city = "Paris", interests = setOf(Quiz.Interest.HISTORY, Quiz.Interest.NIGHT),
            company = Quiz.Company.FAMILY, duration = Quiz.Duration.TWO)
        val exp = ExperienceBuilder.build(family, "Paris", pools)
        assertEquals(2, exp.days.size)
        val cats = exp.days.flatMap { d -> d.steps.map { it.category } }
        assertTrue(Category.BARS !in cats && Category.NIGHTLIFE !in cats)
        val sober = Quiz.Answers(city = "Paris", company = Quiz.Company.FRIENDS, interests = setOf(Quiz.Interest.NIGHT), diet = Quiz.Diet.NO_ALCOHOL)
        assertTrue(Category.BARS !in ExperienceBuilder.neededCategories(sober))
    }

    @Test
    fun questionnaireHasFineQuestions() {
        val a = Quiz.Answers(interests = setOf(Quiz.Interest.FOOD, Quiz.Interest.BEACH, Quiz.Interest.ART))
        assertEquals(
            listOf("city", "date", "duration", "company", "interests", "taste:ART", "taste:BEACH", "taste:FOOD",
                "style", "pace", "depth", "start", "mobility", "diet", "budget"),
            Quiz.steps(a),
        )
        assertEquals("restaurants halal", ExperienceBuilder.searchHint(a.copy(diet = Quiz.Diet.HALAL), Category.RESTAURANTS))
        // Le régime s'ajoute au goût au lieu de le remplacer.
        assertEquals("restaurants traditionnels halal",
            ExperienceBuilder.searchHint(a.copy(diet = Quiz.Diet.HALAL, tastes = setOf(Quiz.Taste.LOCAL_FOOD)), Category.RESTAURANTS))
    }

    @Test
    fun onlyChosenInterestsMakeTheDay() {
        val a = Quiz.Answers(city = "Paris", interests = setOf(Quiz.Interest.FOOD), company = Quiz.Company.SOLO)
        val cats = ExperienceBuilder.build(a, "Paris", pools).days.single().steps.map { it.category }.toSet()
        assertTrue(cats.isNotEmpty())
        assertTrue("$cats", cats.all { it == Category.RESTAURANTS || it == Category.CAFES })
    }

    @Test
    fun rainyDayPrefersIndoorVisits() {
        val day = java.time.LocalDate.of(2026, 12, 1)
        val rain = com.riad.voyage.data.DayWeather(day, 63, 12.0, 7.0, 90, 17 * 60)
        val parks = (0 until 6).map { poi(Category.PARKS, it, "Parc", fame = 80) }
        val museums = (0 until 6).map { poi(Category.MUSEUMS, it + 10, "Musée", fame = 15) }
        val a = Quiz.Answers(city = "Paris", interests = setOf(Quiz.Interest.NATURE, Quiz.Interest.ART), startDate = day)
        val pools = mapOf(Category.PARKS to parks, Category.MUSEUMS to museums, Category.RESTAURANTS to this.pools.getValue(Category.RESTAURANTS),
            Category.BARS to this.pools.getValue(Category.BARS))
        val wet = ExperienceBuilder.build(a, "Paris", pools, weather = mapOf(day to rain)).days.single()
        assertEquals(rain, wet.weather)
        assertEquals(Category.MUSEUMS, wet.steps.first().category)
        assertTrue(wet.steps.first().why.startsWith("Idéal par temps de pluie"))
        // Sans météo, les parcs très célèbres passent devant.
        val dry = ExperienceBuilder.build(a, "Paris", pools).days.single()
        assertEquals(Category.PARKS, dry.steps.first().category)
    }

    @Test
    fun longStayCoversEveryDay() {
        val a = Quiz.Answers(city = "Paris", interests = setOf(Quiz.Interest.HISTORY, Quiz.Interest.ART), duration = Quiz.Duration(7))
        val exp = ExperienceBuilder.build(a, "Paris", pools)
        assertEquals(7, exp.days.size)
        // Chaque jour a son déjeuner et son dîner, même quand les restaurants doivent resservir.
        exp.days.forEach { d -> assertEquals(2, d.steps.count { it.category == Category.RESTAURANTS }) }
        exp.days.forEach { d -> assertEquals(d.steps.size, d.steps.map { it.place.id }.distinct().size) }
        assertEquals("7 jours", Quiz.Duration(7).label)
    }

    @Test
    fun stepCanBeReplacedOrRemoved() {
        val a = Quiz.Answers(city = "Paris", interests = setOf(Quiz.Interest.HISTORY))
        val exp = ExperienceBuilder.build(a, "Paris", pools)
        val day = exp.days.single()
        val alts = ExperienceBuilder.alternatives(a, exp, pools, 0, 0)
        assertTrue(alts.isNotEmpty())
        assertTrue(alts.none { (_, p) -> p.id in exp.placeIds })
        val replaced = ExperienceBuilder.replaceStep(a, exp, 0, 0, alts.first())
        assertEquals(alts.first().second.id, replaced.days.single().steps.first().place.id)
        assertEquals(day.steps.size, replaced.days.single().steps.size)
        val removed = ExperienceBuilder.replaceStep(a, exp, 0, 0, null)
        assertEquals(day.steps.size - 1, removed.days.single().steps.size)
        // Les horaires restent cohérents après modification, et le déjeuner pas avant 12h30.
        removed.days.single().steps.zipWithNext().forEach { (x, y) -> assertTrue(minutes(y.time) >= minutes(x.time) + x.durationMin) }
        assertTrue(minutes(removed.days.single().steps.first { it.moment == "Déjeuner" }.time) >= 12 * 60 + 30)
    }
}
