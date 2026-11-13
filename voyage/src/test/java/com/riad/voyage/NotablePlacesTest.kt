package com.riad.voyage

import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.GeoPoint
import com.riad.voyage.data.NotablePlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

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
    fun searchAroundParis() = runBlocking {
        // Les zones sont placées en mémoire : aucun téléchargement pendant le test.
        listOf("24_0", "24_1", "23_0", "23_1", "21_2", "21_1", "22_2", "22_1", "21_3", "22_3").forEach { NotablePlaces.putTileForTest(it, json) }
        val paris = GeoPoint(48.8566, 2.3522, "Paris")
        val museums = NotablePlaces.search(paris, ActivityLinks.Category.MUSEUMS)
        assertEquals(listOf("Musée du Louvre", "Musée d'Orsay"), museums.map { it.name })
        assertTrue(museums[0].isMustSee)
        assertEquals("fr:Musée du Louvre", museums[0].wikipedia)
        assertEquals(
            "https://commons.wikimedia.org/wiki/Special:FilePath/Louvre_Museum_Wikimedia_Commons.jpg?width=480",
            museums[0].imageUrl,
        )
        // Toute la ville et ses environs : Versailles (~17 km) fait partie du top des monuments.
        assertEquals(listOf("Château de Versailles"), NotablePlaces.search(paris, ActivityLinks.Category.MONUMENTS).map { it.name })
        val marseille = GeoPoint(43.2965, 5.3698, "Marseille")
        val beaches = NotablePlaces.search(marseille, ActivityLinks.Category.BEACHES)
        assertEquals("Plage", beaches.single().kind)
        assertTrue(beaches.single().isFree)
        assertTrue(!NotablePlaces.supports(ActivityLinks.Category.RESTAURANTS))
        assertEquals("24_1", NotablePlaces.tileKey(48.85, 2.35))
        assertEquals("-17_-24", NotablePlaces.tileKey(-33.9, -47.9))
    }
}
