package com.riad.voyage

import com.riad.voyage.data.ActivityLinks.Category
import com.riad.voyage.data.ExperienceBuilder
import com.riad.voyage.data.PointOfInterest
import com.riad.voyage.data.Quiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperienceTest {
    private fun poi(cat: Category, i: Int, kind: String = "Lieu") = PointOfInterest(
        id = "$cat/$i", name = "$cat $i", kind = kind, lat = 48.85 + i * 0.002, lon = 2.35 + i * 0.002,
        distanceM = i * 200, address = null, openingHours = null, website = null, phone = null, wikipedia = null,
        notable = true, isVenue = cat in setOf(Category.RESTAURANTS, Category.CAFES, Category.BARS, Category.NIGHTLIFE),
    )

    private val pools = Category.entries.associateWith { c -> (0 until 12).map { poi(c, it) } }

    @Test
    fun coupleDayHasVisitsMealsAndADrink() {
        val a = Quiz.Answers(city = "Paris", interests = setOf(Quiz.Interest.ART, Quiz.Interest.FOOD), company = Quiz.Company.COUPLE)
        val exp = ExperienceBuilder.build(a, "Paris", pools, seed = 1)
        val steps = exp.days.single().steps
        val cats = steps.map { it.category }
        assertEquals(2, cats.count { it == Category.RESTAURANTS })
        assertTrue(Category.MUSEUMS in cats)
        assertTrue(Category.CAFES in cats)      // gastronomie → pause gourmande
        assertTrue(Category.BARS in cats)       // couple → verre en soirée
        assertTrue(Category.NIGHTLIFE !in cats)
        assertEquals(steps.size, steps.map { it.place.id }.distinct().size) // aucun lieu en double
        assertEquals("Une journée art et gastronomie à Paris", exp.title)
        assertEquals(listOf("9h30", "12h30"), steps.take(2).map { it.time })
    }

    @Test
    fun familyHasNoBarAndGetsAPark() {
        val a = Quiz.Answers(city = "Paris", interests = setOf(Quiz.Interest.HISTORY, Quiz.Interest.NIGHT),
            company = Quiz.Company.FAMILY, duration = Quiz.Duration.TWO)
        val exp = ExperienceBuilder.build(a, "Paris", pools)
        assertEquals(2, exp.days.size)
        val cats = exp.days.flatMap { d -> d.steps.map { it.category } }
        assertTrue(Category.BARS !in cats && Category.NIGHTLIFE !in cats)
        assertTrue(Category.PARKS in ExperienceBuilder.activityCategories(a))
    }

    @Test
    fun halfDayStopsAfterLunchAndSmallBudgetAvoidsExpensive() {
        val expensive = (0 until 5).map { poi(Category.RESTAURANTS, it, "Restaurant · €€€€") }
        val cheap = (5 until 8).map { poi(Category.RESTAURANTS, it, "Restaurant · €") }
        val a = Quiz.Answers(city = "Paris", interests = setOf(Quiz.Interest.NATURE), duration = Quiz.Duration.HALF,
            budget = Quiz.Budget.SMALL, pace = Quiz.Pace.INTENSE)
        val exp = ExperienceBuilder.build(a, "Paris", pools + (Category.RESTAURANTS to expensive + cheap))
        val steps = exp.days.single().steps
        assertEquals(listOf("Matin", "Fin de matinée", "Déjeuner"), steps.map { it.moment })
        assertTrue(steps.last().place.kind.endsWith("€") && !steps.last().place.kind.contains("€€"))
        assertEquals(4, ExperienceBuilder.priceLevel(expensive[0]))
    }

    @Test
    fun followUpQuestionsAppearForChosenInterests() {
        val a = Quiz.Answers(interests = setOf(Quiz.Interest.FOOD, Quiz.Interest.BEACH, Quiz.Interest.ART))
        assertEquals(
            listOf("city", "duration", "company", "interests", "taste:ART", "taste:FOOD", "pace", "budget"),
            Quiz.steps(a),
        )
    }

    @Test
    fun tastesDrivePlaceChoiceAndSearch() {
        val restaurants = listOf(
            poi(Category.RESTAURANTS, 0, "Restaurant · €€"),
            poi(Category.RESTAURANTS, 1, "Restaurant chinois"),
            poi(Category.RESTAURANTS, 2, "Restaurant · italien"),
            poi(Category.RESTAURANTS, 3, "Pizzeria"),
        )
        val museums = listOf(
            poi(Category.MUSEUMS, 0, "Musée d'histoire"),
            poi(Category.MUSEUMS, 1, "Musée"),
            poi(Category.MUSEUMS, 2, "Musée d'art moderne"),
        )
        val a = Quiz.Answers(
            city = "Paris", duration = Quiz.Duration.HALF, pace = Quiz.Pace.RELAXED,
            interests = setOf(Quiz.Interest.ART, Quiz.Interest.FOOD),
            tastes = setOf(Quiz.Taste.ITALIAN, Quiz.Taste.MODERN_ART),
        )
        val steps = ExperienceBuilder.build(a, "Paris", mapOf(Category.RESTAURANTS to restaurants, Category.MUSEUMS to museums, Category.CAFES to emptyList())).days.single().steps
        assertEquals("Musée d'art moderne", steps.first().place.kind)
        assertTrue(steps.first().why.startsWith("Pour votre envie « art moderne et contemporain »"))
        assertTrue(steps.first { it.category == Category.RESTAURANTS }.place.kind.let { it.contains("italien") || it == "Pizzeria" })
        assertEquals("restaurants italiens", ExperienceBuilder.searchHint(a, Category.RESTAURANTS))
        assertEquals("Une demi-journée art moderne et cuisine italienne à Paris", ExperienceBuilder.build(a, "Paris", emptyMap()).title)
    }
}
