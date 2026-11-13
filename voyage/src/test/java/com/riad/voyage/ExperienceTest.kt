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
}
