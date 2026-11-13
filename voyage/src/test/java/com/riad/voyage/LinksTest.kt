package com.riad.voyage

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

class ActivityLinksTest {
    @Test
    fun everyCategoryHasEncodedLinks() {
        com.riad.voyage.data.ActivityLinks.Category.entries.forEach { c ->
            val partners = com.riad.voyage.data.ActivityLinks.partners("Saint-Émilion", c)
            assertTrue(partners.isNotEmpty())
            partners.forEach { p ->
                assertTrue(p.url, p.url.startsWith("https://"))
                assertTrue(p.url, !p.url.contains(' ') && !p.url.contains('É'))
            }
        }
    }
}
