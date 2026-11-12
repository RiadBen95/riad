package com.riad.voyage

import com.riad.voyage.data.ApiException
import com.riad.voyage.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParsersTest {

    @Test
    fun flights() {
        val json = """
            {"success":true,"currency":"eur","data":[
              {"origin":"PAR","destination":"ALG","origin_airport":"ORY","destination_airport":"ALG",
               "price":142,"airline":"AH","flight_number":"1003","departure_at":"2026-12-01T07:00:00+01:00",
               "return_at":"2026-12-15T12:30:00+01:00","transfers":0,"return_transfers":1,
               "duration":420,"duration_to":135,"duration_back":285,"link":"/search/PAR0112ALG15121?t=abc"},
              {"origin":"PAR","destination":"ALG","price":99,"airline":"TO","departure_at":"2026-12-02T10:00:00+01:00",
               "return_at":null,"transfers":1,"duration":300,"link":"/search/x"},
              {"origin":"PAR","destination":"ALG","price":0}
            ]}
        """.trimIndent()
        val flights = Parsers.flights(json)
        assertEquals(2, flights.size)
        val f = flights[0]
        assertEquals("ORY", f.originAirport)
        assertEquals(142, f.price)
        assertEquals(135, f.durationTo)
        assertEquals(285, f.durationBack)
        assertEquals(1, f.totalTransfers)
        assertEquals(LocalDate.of(2026, 12, 1), f.departureDate)
        val g = flights[1]
        assertNull(g.returnAt)
        assertEquals("PAR", g.originAirport)
        assertEquals(300, g.durationTo)
    }

    @Test(expected = ApiException::class)
    fun errorResponse() {
        Parsers.flights("""{"success":false,"error":"bad token","data":null}""")
    }

    @Test
    fun calendar() {
        val json = """
            {"success":true,"data":{
              "2026-12-03":{"price":120,"transfers":1,"airline":"AF"},
              "2026-12-01":{"price":95,"transfers":0,"airline":"AH"}
            },"currency":"eur"}
        """.trimIndent()
        val days = Parsers.calendar(json)
        assertEquals(listOf(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3)), days.map { it.date })
        assertEquals(95, days[0].price)
    }

    @Test
    fun places() {
        val json = """
            [{"type":"city","code":"PAR","name":"Paris","country_name":"France"},
             {"type":"airport","code":"CDG","name":"Charles de Gaulle","city_name":"Paris","country_name":"France"},
             {"type":"city","code":"","name":"?"}]
        """.trimIndent()
        val places = Parsers.places(json)
        assertEquals(2, places.size)
        assertEquals("Paris", places[0].label)
        assertEquals("Charles de Gaulle (CDG)", places[1].label)
        assertTrue(places[1].subtitle.startsWith("Paris, France"))
    }
}
