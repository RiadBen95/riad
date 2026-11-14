package com.riad.voyage

import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.BudgetCalculator
import com.riad.voyage.data.Chosen
import com.riad.voyage.data.PriceText
import com.riad.voyage.data.BudgetInput
import com.riad.voyage.data.BudgetItem
import com.riad.voyage.data.CostIndex
import com.riad.voyage.data.Fx
import com.riad.voyage.data.PlannedActivity
import com.riad.voyage.data.Quiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetTest {
    private fun line(lines: List<com.riad.voyage.data.BudgetLine>, i: BudgetItem) = lines.first { it.item == i }

    @Test fun costIndexIgnoresAccentsAndCase() {
        assertEquals(1.0, CostIndex.of("France"), 0.0)
        assertEquals(0.75, CostIndex.of("Grèce"), 0.0)
        assertEquals(0.9, CostIndex.of("Pays inconnu"), 0.0)
    }

    @Test fun onlyWhatIsInTheTripIsCounted() {
        val i = BudgetInput("Rome", "Italie", travellers = 2, nights = 2, includeDaily = false,
            flights = listOf(Chosen("Paris → Rome", 300.0)), hotel = Chosen("Hotel Roma", 250.0),
            activities = listOf(PlannedActivity("Musées du Vatican", "Musée", ActivityLinks.Category.MUSEUMS),
                PlannedActivity("Basilique Saint-Pierre", "Basilique", ActivityLinks.Category.MONUMENTS),
                PlannedActivity("Piazza Navona", "Place", ActivityLinks.Category.MONUMENTS),
                PlannedActivity("Pont Saint-Ange", "Pont")))
        val l = BudgetCalculator.compute(i)
        assertEquals(300.0, line(l, BudgetItem.FLIGHT).amountEur, 0.0)
        assertEquals(250.0, line(l, BudgetItem.HOTEL).amountEur, 0.0)
        val acts = line(l, BudgetItem.ACTIVITIES)
        assertEquals(15.0 * 0.9 * 2, acts.amountEur, 0.01)   // seul le musée est payant
        assertEquals(1, acts.details.size)
        assertEquals(3, acts.freeCount)
    }

    @Test fun nothingChosenAddsNoFlightNorHotel() {
        val l = BudgetCalculator.compute(BudgetInput("Paris", "France", travellers = 2, nights = 3))
        assertTrue(l.none { it.item == BudgetItem.FLIGHT || it.item == BudgetItem.HOTEL || it.item == BudgetItem.ACTIVITIES })
        assertEquals(55.0 * 2 * 4, line(l, BudgetItem.MEALS).amountEur, 0.01)
        assertTrue(BudgetCalculator.compute(BudgetInput("Paris", "France", includeDaily = false)).isEmpty())
    }

    @Test fun restaurantsCountAsMealsNotFree() {
        val chouchou = PlannedActivity("Chouchou", "Restaurant · française")
        assertEquals(BudgetCalculator.CostKind.FOOD, BudgetCalculator.costKind(chouchou))
        val i = BudgetInput("Paris", "France", travellers = 2, nights = 0, activities = listOf(chouchou))
        val meals = line(BudgetCalculator.compute(i), BudgetItem.MEALS)
        assertEquals(55.0 * 2, meals.amountEur, 0.01)          // le restaurant remplace une part des repas estimés
        assertEquals(32.0 * 2, meals.details.first().amountEur, 0.01)
        assertTrue(BudgetCalculator.compute(i).none { it.item == BudgetItem.ACTIVITIES })
    }

    @Test fun overridesWin() {
        val i = BudgetInput("Paris", "France", travellers = 2, nights = 3)
        val l = BudgetCalculator.compute(i, mapOf(BudgetItem.FLIGHT to 400.0))
        assertEquals(400.0, line(l, BudgetItem.FLIGHT).amountEur, 0.0)
        val onSite = l.filter { it.item == BudgetItem.MEALS || it.item == BudgetItem.TRANSPORT }.sumOf { it.amountEur }
        assertEquals(onSite * 0.1, line(l, BudgetItem.EXTRAS).amountEur, 0.01)
    }

    @Test fun priceTextReadsDisplayedPrices() {
        assertEquals(540.0 to "EUR", PriceText.parse("540 €"))
        assertEquals(1234.0 to "USD", PriceText.parse("US$1,234"))
        assertEquals(540.0 to "MAD", PriceText.parse("٥٤٠ MAD"))
        assertEquals(null, PriceText.parse("Hôtel"))
    }

    @Test fun fxParsesAndConverts() {
        val r = Fx.parse("""{"result":"success","rates":{"EUR":1,"USD":1.1,"MAD":10.8}}""")
        assertEquals(1.1, r["USD"]!!, 0.0)
        assertEquals(100.0, Fx.toEur(110.0, "USD", r), 0.001)
        assertEquals(50.0, Fx.toEur(50.0, "EUR", r), 0.0)
        assertEquals(42.0, Fx.toEur(42.0, "XXX", r), 0.0)
    }
}
