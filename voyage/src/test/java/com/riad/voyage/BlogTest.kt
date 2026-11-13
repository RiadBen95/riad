package com.riad.voyage

import com.riad.voyage.data.Blog
import com.riad.voyage.data.WikiArticles
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlogTest {
    @Test
    fun parsesArticleIntoSections() {
        val extract = "Le Colisée est un immense amphithéâtre ovoïde situé dans le centre de Rome.\n\n" +
            "== Histoire ==\nConstruit sous les Flaviens, il pouvait accueillir des dizaines de milliers de spectateurs " +
            "venus assister aux combats de gladiateurs et aux spectacles publics.\n" +
            "=== Construction ===\nLes travaux commencent vers 70 et s'achèvent en 80 sous le règne de Titus, après une décennie de chantier.\n\n" +
            "== Notes et références ==\nRéf 1\n\n== Architecture ==\nCourt."
        val json = JSONObject().put(
            "query",
            JSONObject().put(
                "pages",
                JSONObject().put(
                    "123",
                    JSONObject().put("title", "Colisée").put("extract", extract)
                        .put("original", JSONObject().put("source", "https://upload.wikimedia.org/colisee.jpg")),
                ),
            ),
        ).toString()
        val a = WikiArticles.parseArticle(json)!!
        assertEquals("Colisée", a.title)
        assertTrue(a.intro.startsWith("Le Colisée"))
        // « Notes et références » est écartée, « Architecture » trop courte aussi.
        assertEquals(listOf("Histoire"), a.sections.map { it.first })
        assertTrue(a.sections[0].second.contains("Construction"))
        assertEquals("https://fr.wikipedia.org/wiki/Colis%C3%A9e", a.url)
        assertNull(WikiArticles.parseArticle("""{"query":{"pages":{"-1":{"title":"X","missing":""}}}}"""))
    }

    @Test
    fun parsesSearchAndKnowsCities() {
        val r = WikiArticles.parseSearch("""["tour eif",["Tour Eiffel","Tour Eiffel (homonymie)"],["",""],["u1","u2"]]""")
        assertEquals(listOf("fr:Tour Eiffel" to "Tour Eiffel"), r)
        assertEquals("fr:Rome", Blog.city("rome")!!.wiki)
        assertTrue(Blog.cities.size >= 20)
    }
}
