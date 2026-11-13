package com.riad.voyage

import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.Candidate
import com.riad.voyage.data.FoursquareRatings
import com.riad.voyage.data.GeoPoint
import com.riad.voyage.data.GoogleRatings
import com.riad.voyage.data.RatingInfo
import com.riad.voyage.data.RatingSource
import com.riad.voyage.data.RatingsAggregator
import com.riad.voyage.data.TripadvisorRatings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingsTest {
    private val center = GeoPoint(43.2965, 5.3698, "Marseille")

    @Test
    fun parsesGoogle() {
        val json = """{"places":[{"id":"x","displayName":{"text":"Chez Fonfon"},"location":{"latitude":43.2853,"longitude":5.3511},
            "formattedAddress":"140 Vallon des Auffes","rating":4.5,"userRatingCount":2341,"priceLevel":"PRICE_LEVEL_EXPENSIVE",
            "googleMapsUri":"https://maps.google.com/?cid=1","currentOpeningHours":{"openNow":true}}]}"""
        val c = GoogleRatings.parse(json).single()
        assertEquals("Chez Fonfon", c.name)
        assertEquals(4.5, c.rating!!.rating, 1e-9)
        assertEquals(2341, c.rating!!.count)
        assertEquals("€€€", c.price)
        assertEquals(true, c.openNow)
    }

    @Test
    fun parsesTripadvisorAndFoursquare() {
        val ta = TripadvisorRatings.parseDetails(
            """{"location_id":"1","name":"Chez Fonfon","latitude":"43.2854","longitude":"5.3512","rating":"4.0","num_reviews":"1,204",
            "ranking_data":{"ranking_string":"N° 45 sur 2 431 restaurants à Marseille"},"web_url":"https://www.tripadvisor.fr/x"}""",
        )!!
        assertEquals(1204, ta.rating!!.count)
        assertEquals("N° 45 sur 2 431 restaurants à Marseille", ta.rating!!.ranking)
        val fsq = FoursquareRatings.parse(
            """{"results":[{"fsq_place_id":"a","name":"Le Bar","latitude":43.29,"longitude":5.37},
                           {"fsq_id":"b","name":"Old API","geocodes":{"main":{"latitude":43.3,"longitude":5.38}}}]}""",
        )
        assertEquals(listOf(0, 1), fsq.map { it.popularityRank })
        assertEquals(43.3, fsq[1].lat!!, 1e-9)
    }

    @Test
    fun mergesSamePlaceAcrossSourcesAndRanksByReliableRating() {
        val candidates = listOf(
            Candidate(RatingSource.GOOGLE, "Chez Fonfon", 43.2853, 5.3511, rating = RatingInfo(RatingSource.GOOGLE, 4.5, 2341)),
            Candidate(RatingSource.GOOGLE, "Petit Nouveau", 43.30, 5.37, rating = RatingInfo(RatingSource.GOOGLE, 5.0, 3)),
            Candidate(RatingSource.TRIPADVISOR, "Restaurant Chez Fonfon", 43.2854, 5.3512, rating = RatingInfo(RatingSource.TRIPADVISOR, 4.0, 1204, "N° 45")),
            Candidate(RatingSource.FOURSQUARE, "Chez Fonfon", 43.2853, 5.3511, popularityRank = 0),
            // Même nom mais à l'autre bout de la ville : lieu différent.
            Candidate(RatingSource.FOURSQUARE, "Petit Nouveau", 43.25, 5.40, popularityRank = 1),
        )
        val out = RatingsAggregator.merge(candidates, emptyList(), center, ActivityLinks.Category.RESTAURANTS)
        assertEquals(3, out.size)
        val first = out.first()
        assertEquals("Chez Fonfon", first.name)
        assertEquals(listOf(RatingSource.GOOGLE, RatingSource.TRIPADVISOR), first.ratings.map { it.source })
        // 5★ avec 3 avis ne passe pas devant 4,5★ avec 2 341 avis.
        assertTrue(out.indexOfFirst { it.name == "Petit Nouveau" && it.ratings.isNotEmpty() } > 0)
        assertTrue(RatingsAggregator.bayes(4.5, 2341) > RatingsAggregator.bayes(5.0, 3))
    }
}
