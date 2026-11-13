package com.riad.voyage

import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.BudgetCalculator
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

    @Test fun estimatesScaleWithPeopleNightsAndLevel() {
        val l = BudgetCalculator.compute(BudgetInput("Paris", "France", travellers = 2, nights = 3))
        assertEquals(130.0 * 3, line(l, BudgetItem.HOTEL).amountEur, 0.01)     // 1 chambre, 3 nuits
        assertEquals(55.0 * 2 * 4, line(l, BudgetItem.MEALS).amountEur, 0.01)  // 2 pers., 4 jours
        assertEquals(0.0, line(l, BudgetItem.FLIGHT).amountEur, 0.0)
        val three = BudgetCalculator.compute(BudgetInput("Paris", "France", travellers = 3, nights = 3))
        assertEquals(130.0 * 2 * 3, line(three, BudgetItem.HOTEL).amountEur, 0.01) // 2 chambres
        val small = BudgetCalculator.compute(BudgetInput("Paris", "France", level = Quiz.Budget.SMALL))
        assertTrue(small.sumOf { it.amountEur } < l.sumOf { it.amountEur })
    }

    @Test fun realDataAndOverridesWin() {
        val i = BudgetInput("Rome", "Italie", nights = 2, flightTotalEur = 300.0, hotelTotalEur = 250.0,
            activities = listOf(PlannedActivity(ActivityLinks.Category.MUSEUMS, "Musée"), PlannedActivity(ActivityLinks.Category.MONUMENTS, "Basilique")))
        val l = BudgetCalculator.compute(i, mapOf(BudgetItem.MEALS to 100.0))
        assertEquals(300.0, line(l, BudgetItem.FLIGHT).amountEur, 0.0)
        assertEquals(250.0, line(l, BudgetItem.HOTEL).amountEur, 0.0)
        assertEquals(15.0 * 0.9 * 2, line(l, BudgetItem.ACTIVITIES).amountEur, 0.01) // basilique gratuite
        assertEquals(100.0, line(l, BudgetItem.MEALS).amountEur, 0.0)
        val onSite = l.filter { it.item != BudgetItem.FLIGHT && it.item != BudgetItem.EXTRAS }.sumOf { it.amountEur }
        assertEquals(onSite * 0.1, line(l, BudgetItem.EXTRAS).amountEur, 0.01)
    }

    @Test fun noNightMeansNoHotel() {
        val l = BudgetCalculator.compute(BudgetInput("Lyon", "France", nights = 0))
        assertEquals(0.0, line(l, BudgetItem.HOTEL).amountEur, 0.0)
        assertEquals(1, BudgetCalculator.days(0))
    }

    @Test fun fxParsesAndConverts() {
        val r = Fx.parse("""{"result":"success","rates":{"EUR":1,"USD":1.1,"MAD":10.8}}""")
        assertEquals(1.1, r["USD"]!!, 0.0)
        assertEquals(100.0, Fx.toEur(110.0, "USD", r), 0.001)
        assertEquals(50.0, Fx.toEur(50.0, "EUR", r), 0.0)
        assertEquals(42.0, Fx.toEur(42.0, "XXX", r), 0.0)
    }
}
