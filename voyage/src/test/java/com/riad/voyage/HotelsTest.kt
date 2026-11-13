package com.riad.voyage

import com.riad.voyage.data.GeoPoint
import com.riad.voyage.data.HotelBooking
import com.riad.voyage.data.LiteApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HotelsTest {
    private val paris = GeoPoint(48.8566, 2.3522, "Paris")

    @Test
    fun parsesHotelsAndRates() {
        val hotels = LiteApi.parseHotels(
            """{"data":[{"id":"lp1","name":"Hôtel du Louvre","latitude":48.862,"longitude":2.336,"stars":5,"rating":8.9,
                "reviewCount":2400,"address":"Place André Malraux","main_photo":"https://img/1.jpg","currency":"EUR"},
               {"id":"lp2","name":"Petit Hôtel","latitude":48.85,"longitude":2.35,"stars":2,"rating":7.1,"reviewCount":40}]}""",
            paris,
        )
        assertEquals(2, hotels.size)
        assertEquals(5, hotels[0].stars)
        assertTrue(hotels[0].distanceM!! in 1000..1600)
        val rates = LiteApi.parseRates(
            """{"data":[{"hotelId":"lp1","roomTypes":[
                 {"offerId":"o1","offerRetailRate":{"amount":620.0,"currency":"EUR"},
                  "rates":[{"boardName":"Petit-déjeuner","cancellationPolicies":{"refundableTag":"RFN"}}]},
                 {"offerId":"o2","offerRetailRate":{"amount":540.5,"currency":"EUR"},
                  "rates":[{"boardName":"Chambre seule","cancellationPolicies":{"refundableTag":"NRFN"}}]}]},
               {"hotelId":"lp2","roomTypes":[{"offerId":"o3","rates":[{"retailRate":{"total":[{"amount":180,"currency":"EUR"}]}}]}]}]}""",
        )
        assertEquals(540.5, rates.getValue("lp1").price, 1e-9)
        assertEquals("o2", rates.getValue("lp1").offerId)
        assertTrue(!rates.getValue("lp1").refundable)
        assertEquals(180.0, rates.getValue("lp2").price, 1e-9)
    }

    @Test
    fun partnerLinksCarryDatesAndGuests() {
        val links = HotelBooking.partners("Hôtel du Louvre Paris", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 4), 2)
        val booking = links.first { it.name == "Booking.com" }.url
        assertTrue(booking.contains("checkin=2026-12-01&checkout=2026-12-04&group_adults=2"))
        assertTrue(booking.contains("ss=H%C3%B4tel%20du%20Louvre%20Paris"))
        assertEquals(3, HotelBooking.nights(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 4)))
    }
}
