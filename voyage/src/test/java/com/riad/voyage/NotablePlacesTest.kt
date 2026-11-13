package com.riad.voyage

import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.GeoPoint
import com.riad.voyage.data.NotablePlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotablePlacesTest {
    private val json = """
        {"v":1,"categories":["MUSEUMS","MONUMENTS","ATTRACTIONS","PARKS","BEACHES"],"kinds":["Musée","Château","Plage"],
         "items":[
           ["Musée du Louvre",48.86111,2.33583,180,0,0,"Louvre Museum Wikimedia Commons.jpg","fr:Musée du Louvre"],
           ["Musée d'Orsay",48.86,2.3266,120,0,0,"","fr:Musée d'Orsay"],
           ["Château de Versailles",48.8049,2.1204,150,1,1,"",""],
           ["Musée lointain",43.3,5.37,50,0,0,"",""],
           ["Plage du Prado",43.26,5.37,10,4,2,"",""]
         ]}
    """.trimIndent()

    @Test
    fun searchAroundParis() {
        NotablePlaces.loadFromJson(json)
        val paris = GeoPoint(48.8566, 2.3522, "Paris")
        val museums = NotablePlaces.search(paris, ActivityLinks.Category.MUSEUMS)
        assertEquals(listOf("Musée du Louvre", "Musée d'Orsay"), museums.map { it.name })
        assertTrue(museums[0].isMustSee)
        assertEquals("fr:Musée du Louvre", museums[0].wikipedia)
        assertEquals(
            "https://commons.wikimedia.org/wiki/Special:FilePath/Louvre_Museum_Wikimedia_Commons.jpg?width=480",
            museums[0].imageUrl,
        )
        // Versailles est à ~20 km : hors du rayon de 15 km des monuments.
        assertTrue(NotablePlaces.search(paris, ActivityLinks.Category.MONUMENTS).isEmpty())
        val marseille = GeoPoint(43.2965, 5.3698, "Marseille")
        val beaches = NotablePlaces.search(marseille, ActivityLinks.Category.BEACHES)
        assertEquals("Plage", beaches.single().kind)
        assertTrue(beaches.single().isFree)
        assertTrue(!NotablePlaces.supports(ActivityLinks.Category.RESTAURANTS))
    }
}
