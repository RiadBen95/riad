package com.riad.voyage

import com.riad.voyage.data.HotelLinks
import com.riad.voyage.data.PopularPlaces
import com.riad.voyage.data.TravelpayoutsApi
import com.riad.voyage.ui.formatDuration
import com.riad.voyage.ui.formatStops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LinksTest {

    @Test
    fun aviasalesSearchUrl() {
        val url = TravelpayoutsApi.searchUrl(
            "PAR", "ALG", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 15), 2, "12345",
        )
        assertEquals("https://www.aviasales.com/search/PAR0112ALG15122?marker=12345", url)
        val oneWay = TravelpayoutsApi.searchUrl("PAR", "ALG", LocalDate.of(2026, 3, 9), null, 1, "")
        assertEquals("https://www.aviasales.com/search/PAR0903ALG1", oneWay)
    }

    @Test
    fun bookingUrlAddsMarker() {
        assertEquals(
            "https://www.aviasales.com/search/X?t=1&marker=42",
            TravelpayoutsApi.bookingUrl("/search/X?t=1", "42"),
        )
        assertEquals("https://www.aviasales.com/search/X", TravelpayoutsApi.bookingUrl("/search/X", ""))
    }

    @Test
    fun hotelLinks() {
        val p = HotelLinks.partners("Saint-Denis Réunion", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 4), 2, 1)
        val booking = p.first { it.name == "Booking.com" }.url
        assertTrue(booking.contains("ss=Saint-Denis%20R%C3%A9union"))
        assertTrue(booking.contains("checkin=2026-12-01&checkout=2026-12-04"))
        assertTrue(booking.contains("group_adults=2"))
    }

    @Test
    fun formatting() {
        assertEquals("2 h 05", formatDuration(125))
        assertEquals("45 min", formatDuration(45))
        assertEquals("3 h", formatDuration(180))
        assertEquals("Direct", formatStops(0))
        assertEquals("2 escales", formatStops(2))
    }

    @Test
    fun popularPlacesIgnoreAccents() {
        assertEquals("BJA", PopularPlaces.filter("beja").first().code)
        assertEquals("ALG", PopularPlaces.filter("alg").first().code)
    }
}
