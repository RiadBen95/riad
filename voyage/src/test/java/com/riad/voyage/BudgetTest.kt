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

    @Test fun budgetIsTheSumOfWhatWasChosen() {
        val i = BudgetInput("Rome", "Italie", travellers = 2, nights = 2, includeDaily = false,
            flights = listOf(Chosen("Paris → Rome", 300.0)), hotel = Chosen("Hotel Roma", 250.0),
            activities = listOf(PlannedActivity("Musées du Vatican", "Musée", ActivityLinks.Category.MUSEUMS),
                PlannedActivity("Basilique Saint-Pierre", "Basilique", ActivityLinks.Category.MONUMENTS),
                PlannedActivity("Colisée", "Monument")))
        val l = BudgetCalculator.compute(i)
        assertEquals(3, l.size)                                    // seulement vols, hôtel, activités
        assertEquals(300.0, line(l, BudgetItem.FLIGHT).amountEur, 0.0)
        assertEquals(250.0, line(l, BudgetItem.HOTEL).amountEur, 0.0)
        // musée 15 + basilique gratuite + monument 10, × indice Italie 0,9 × 2 personnes
        assertEquals((15.0 + 0 + 10.0) * 0.9 * 2, line(l, BudgetItem.ACTIVITIES).amountEur, 0.01)
        assertEquals(3, line(l, BudgetItem.ACTIVITIES).details.size)
        assertEquals(l.sumOf { it.amountEur }, 300.0 + 250.0 + 45.0, 0.01)
    }

    @Test fun nothingChosenIsFlaggedNotEstimated() {
        val l = BudgetCalculator.compute(BudgetInput("Paris", "France", includeDaily = false))
        listOf(BudgetItem.FLIGHT, BudgetItem.HOTEL, BudgetItem.ACTIVITIES).forEach {
            assertEquals(0.0, line(l, it).amountEur, 0.0)
            assertTrue(line(l, it).missing)
        }
    }

    @Test fun dailyEstimatesAndOverrides() {
        val i = BudgetInput("Paris", "France", travellers = 2, nights = 3)
        val l = BudgetCalculator.compute(i, mapOf(BudgetItem.FLIGHT to 400.0))
        assertEquals(55.0 * 2 * 4, line(l, BudgetItem.MEALS).amountEur, 0.01)
        assertEquals(400.0, line(l, BudgetItem.FLIGHT).amountEur, 0.0)
        val onSite = l.filter { it.item != BudgetItem.FLIGHT && it.item != BudgetItem.EXTRAS }.sumOf { it.amountEur }
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
