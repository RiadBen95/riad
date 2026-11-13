package com.riad.voyage

import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.GeoPoint
import com.riad.voyage.data.GuideParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTest {
    private val text = """
        ==Manger==
        * {{eat | name=Chez Fonfon | url=https://www.chez-fonfon.com | address=140 Vallon des Auffes | lat=43.2853 | long=5.3511
          | phone=+33 4 91 52 14 38 | hours=Mar-Sam 12h-14h | price=45-60 € | content=La '''bouillabaisse''' de référence, vue sur le [[Vallon des Auffes|vallon]]. }}
        * {{Listing | type=manger | nom=Le Café des Épices | adresse=4 rue Lacydon | description=Cuisine inventive. }}
        * {{eat | name=Pâtisserie Saint-Victor | content=Navettes et brunch. }}
        ==Sortir==
        * {{drink | name=La Caravelle | content=Bar sur le Vieux-Port, concerts de jazz {{dead link}}. }}
        * {{drink | name=Le Trolleybus | content=Discothèque mythique sur trois niveaux. }}
        * {{buy | name=Marché des Capucins }}
        * {{sleep | name=Hôtel X }}
        * {{see | name=Notre-Dame }}
    """.trimIndent()

    @Test
    fun parsesListings() {
        val all = GuideParser.listings(text)
        assertEquals(8, all.size)
        val fonfon = all.first()
        assertEquals("Chez Fonfon", fonfon.name)
        assertEquals(43.2853, fonfon.lat!!, 1e-6)
        assertEquals("45-60 €", fonfon.price)
        assertEquals("La bouillabaisse de référence, vue sur le vallon.", fonfon.description)
        val epices = all.first { it.name == "Le Café des Épices" }
        assertEquals("eat", epices.type)
        assertNull(epices.lat)
        assertEquals("Bar sur le Vieux-Port, concerts de jazz .", all.first { it.name == "La Caravelle" }.description)
    }

    @Test
    fun selectsByCategory() {
        val all = GuideParser.listings(text)
        fun names(c: ActivityLinks.Category) = GuideParser.select(all, c).map { it.name }
        assertEquals(listOf("Chez Fonfon"), names(ActivityLinks.Category.RESTAURANTS))
        assertEquals(listOf("Le Café des Épices", "Pâtisserie Saint-Victor"), names(ActivityLinks.Category.CAFES))
        assertEquals(listOf("La Caravelle"), names(ActivityLinks.Category.BARS))
        assertEquals(listOf("Le Trolleybus"), names(ActivityLinks.Category.NIGHTLIFE))
        assertEquals(listOf("Marché des Capucins"), names(ActivityLinks.Category.SHOPPING))
    }

    @Test
    fun toPoi() {
        val center = GeoPoint(43.2965, 5.3698, "Marseille")
        val all = GuideParser.listings(text)
        val withCoords = GuideParser.toPoi(all.first(), ActivityLinks.Category.RESTAURANTS, center)
        assertEquals("Restaurant · 45-60 €", withCoords.kind)
        assertTrue(withCoords.distanceM in 1000..2000)
        assertTrue(withCoords.inGuide && withCoords.isVenue)
        val noCoords = GuideParser.toPoi(all[1], ActivityLinks.Category.CAFES, center)
        assertEquals(-1, noCoords.distanceM)
        assertTrue(!noCoords.hasCoordinates)
    }
}
