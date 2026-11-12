package com.riad.voyage

import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.GeoPoint
import com.riad.voyage.data.PlacesApi
import com.riad.voyage.data.PlacesParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {
    private val paris = GeoPoint(48.8566, 2.3522, "Paris")

    @Test
    fun geocode() {
        val g = PlacesParser.geocode("""[{"lat":"48.8588897","lon":"2.3200410","display_name":"Paris, France"}]""")
        assertNotNull(g)
        assertEquals(48.8588897, g!!.lat, 1e-6)
        assertNull(PlacesParser.geocode("[]"))
    }

    @Test
    fun overpassElements() {
        val json = """
            {"elements":[
              {"type":"node","id":1,"lat":48.8600,"lon":2.3400,"tags":{"tourism":"museum","name":"Petit musée","opening_hours":"Mo-Fr 10:00-18:00; Su off"}},
              {"type":"way","id":2,"center":{"lat":48.8606,"lon":2.3376},"tags":{"tourism":"museum","name":"Louvre","name:fr":"Musée du Louvre","wikipedia":"fr:Musée du Louvre","wikidata":"Q19675","website":"https://www.louvre.fr","addr:street":"Rue de Rivoli","addr:housenumber":"99"}},
              {"type":"node","id":3,"lat":48.8601,"lon":2.3401,"tags":{"tourism":"museum","name":"Petit musée"}},
              {"type":"node","id":4,"lat":48.85,"lon":2.35,"tags":{"amenity":"restaurant","name":"Chez Riad","cuisine":"algerian;mediterranean","phone":"+33 1 00 00 00 00"}},
              {"type":"node","id":5,"lat":48.85,"lon":2.35,"tags":{"tourism":"museum"}}
            ]}
        """.trimIndent()
        val pois = PlacesParser.pois(json, paris)
        assertEquals(3, pois.size)
        val louvre = pois.first()
        assertEquals("Musée du Louvre", louvre.name)
        assertEquals("Musée", louvre.kind)
        assertEquals("99 Rue de Rivoli", louvre.address)
        assertTrue(louvre.distanceM in 1000..1500)
        val resto = pois.first { it.isRestaurant }
        assertEquals("Restaurant · algérien, méditerranéen", resto.kind)
        assertEquals("+33 1 00 00 00 00", resto.phone)
    }

    @Test
    fun wiki() {
        val w = PlacesParser.wiki(
            """{"title":"Musée du Louvre","extract":"Le musée du Louvre est un musée d'art.","thumbnail":{"source":"https://upload.wikimedia.org/x.jpg"},"content_urls":{"mobile":{"page":"https://fr.m.wikipedia.org/wiki/Mus%C3%A9e_du_Louvre"}}}""",
        )
        assertEquals("https://upload.wikimedia.org/x.jpg", w!!.imageUrl)
        assertEquals("https://fr.wikipedia.org/wiki/Mus%C3%A9e_du_Louvre", PlacesApi.wikiPageUrl("fr:Musée du Louvre"))
    }

    @Test
    fun hoursInFrench() {
        assertEquals("Lun-Ven 10:00-18:00 ; Dim fermé", PlacesParser.frenchHours("Mo-Fr 10:00-18:00; Su off"))
    }

    @Test
    fun queries() {
        val q = PlacesApi.overpassQuery(ActivityLinks.Category.MUSEUMS, 48.85, 2.35)
        assertTrue(q.startsWith("[out:json]"))
        assertTrue(q.contains("\"tourism\"=\"museum\""))
        assertTrue(q.contains("around:8000,48.85,2.35"))
        assertTrue(PlacesApi.overpassQuery(ActivityLinks.Category.ATTRACTIONS, 1.0, 2.0).contains("theme_park)$\""))
    }
}
