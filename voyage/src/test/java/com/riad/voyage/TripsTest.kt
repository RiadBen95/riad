package com.riad.voyage

import com.riad.voyage.data.SavedArticle
import com.riad.voyage.data.SavedDay
import com.riad.voyage.data.SavedFlight
import com.riad.voyage.data.SavedPlace
import com.riad.voyage.data.SavedStep
import com.riad.voyage.data.Trip
import com.riad.voyage.data.TripJson
import com.riad.voyage.data.TripText
import com.riad.voyage.data.WeatherApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripsTest {
    private val louvre = SavedPlace("Musée du Louvre", "Musée", 48.86, 2.33, true, wikipedia = "fr:Musée du Louvre")

    private val trip = Trip(
        id = "trip-1", city = "Paris", country = "France", startDate = "2026-12-01", endDate = "2026-12-03",
        programTitle = "Une journée art à Paris",
        program = listOf(SavedDay("Une journée", "☀️ 12° / 5°", listOf(SavedStep("9h30", "Matin", "🏛️", "Un incontournable", 180, louvre)))),
        flights = listOf(SavedFlight("Alger", "Paris", "2026-12-01T07:00:00+01:00", null, 142, "EUR", "AH", "https://aviasales.com/x")),
        places = listOf(louvre),
        articles = listOf(SavedArticle("fr:Musée du Louvre", "Musée du Louvre", null, "Intro", listOf("Histoire" to "Texte"), "https://fr.wikipedia.org/wiki/x")),
    )

    @Test
    fun tripSurvivesJsonRoundTrip() {
        val back = TripJson.decodeList(TripJson.encodeList(listOf(trip))).single()
        assertEquals(trip, back)
        assertTrue(!back.isEmpty)
    }

    @Test
    fun shareTextListsTheEssentials() {
        val text = TripText.share(trip)
        assertTrue(text.contains("Paris"))
        assertTrue(text.contains("Alger → Paris"))
        assertTrue(text.contains("9h30  🏛️ Musée du Louvre"))
        assertTrue(text.contains("https://aviasales.com/x"))
    }

    @Test
    fun parsesOpenMeteo() {
        val json = """{"daily":{"time":["2026-12-01","2026-12-02"],"weather_code":[61,0],
            "temperature_2m_max":[11.4,15.2],"temperature_2m_min":[6.1,8.0],
            "precipitation_probability_max":[85,5],"sunset":["2026-12-01T16:54","2026-12-02T16:53"]}}"""
        val days = WeatherApi.parse(json)
        assertEquals(LocalDate.of(2026, 12, 1), days[0].date)
        assertTrue(days[0].rainy)
        assertTrue(days[1].sunny)
        assertEquals(16 * 60 + 54, days[0].sunsetMin)
        assertEquals("☀️ 15° / 8°", days[1].label)
    }
}
