package com.riad.voyage

import com.riad.voyage.data.DayWeather
import com.riad.voyage.data.Expense
import com.riad.voyage.data.Packing
import com.riad.voyage.data.Practical
import com.riad.voyage.data.Trip
import com.riad.voyage.data.TripJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannerTest {
    private fun day(code: Int, max: Double, min: Double, rain: Int) = DayWeather(LocalDate.of(2026, 7, 1), code, max, min, rain, null)

    @Test fun practicalInfoByCountryName() {
        val jp = Practical.of("Japon")!!
        assertEquals("JPY", jp.currency)
        assertTrue(jp.needsAdapter)
        assertEquals("ja", jp.lang)
        assertTrue(!Practical.of("espagne")!!.needsAdapter)       // accents et casse ignorés
        assertNotNull(Practical.of("United States"))
        assertEquals(8, Practical.phrases("it").size)
        assertTrue(Practical.phrases("xx").isEmpty())
    }

    @Test fun packingFollowsWeatherActivitiesAndCountry() {
        val hotBeach = Packing.generate(5, listOf(day(0, 31.0, 22.0, 0)), listOf("Plage de la Concha", "Cathédrale"), Practical.of("Japon"))
        val keys = hotBeach.map { it.key }
        assertTrue("Crème solaire" in keys && "Maillot de bain" in keys && "Adaptateur de prise" in keys)
        assertTrue("Tenue couvrant épaules et genoux (lieux de culte)" in keys)
        assertTrue("Manteau chaud" !in keys)
        assertEquals(6, hotBeach.first { it.key == "Tenues de jour" }.count)

        val coldRain = Packing.generate(2, listOf(day(61, 6.0, 1.0, 80)), emptyList(), Practical.of("France"))
        val k2 = coldRain.map { it.key }
        assertTrue("Parapluie ou imperméable" in k2 && "Manteau chaud" in k2 && "Adaptateur de prise" !in k2)
        assertEquals(k2.size, k2.distinct().size)
    }

    @Test fun tripJsonKeepsExpensesAndPacking() {
        val t = Trip("t1", "Rome", expenses = listOf(Expense(1, "Pizza", 24.5, "EUR", "MEALS", "2026-07-01")),
            packed = setOf("Pyjama"), extraItems = listOf("Livre"), travellers = 3, startDate = "2026-07-01", endDate = "2026-07-04")
        val back = TripJson.decodeList(TripJson.encodeList(listOf(t))).single()
        assertEquals(t.expenses, back.expenses)
        assertEquals(setOf("Pyjama"), back.packed)
        assertEquals(listOf("Livre"), back.extraItems)
        assertEquals(3, back.travellers)
        assertEquals(3, back.nights)
    }
}
