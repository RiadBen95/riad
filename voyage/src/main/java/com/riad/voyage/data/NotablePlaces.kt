package com.riad.voyage.data

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.cos

/**
 * Base mondiale des lieux remarquables (générée depuis Wikidata par tools/build_places.py),
 * embarquée dans l'application : la recherche est instantanée et fonctionne hors ligne.
 */
object NotablePlaces {
    private class Entry(
        val name: String, val lat: Double, val lon: Double, val sitelinks: Int,
        val category: String, val kind: String, val image: String, val wiki: String,
    )

    @Volatile private var entries: List<Entry>? = null

    val size get() = entries?.size ?: 0

    /** Charge la base une seule fois (à appeler hors du fil principal). */
    @Synchronized
    fun load(context: Context) {
        if (entries != null) return
        entries = try {
            val text = GZIPInputStream(context.assets.open("places.json.gz")).bufferedReader().use { it.readText() }
            parse(text)
        } catch (e: Exception) {
            emptyList() // base absente : l'app se rabat sur la recherche en ligne
        }
    }

    /** Pour les tests. */
    @Synchronized
    fun loadFromJson(json: String) {
        entries = parse(json)
    }

    private fun parse(json: String): List<Entry> {
        val root = JSONObject(json)
        val cats = root.getJSONArray("categories").let { a -> List(a.length()) { a.getString(it) } }
        val kinds = root.getJSONArray("kinds").let { a -> List(a.length()) { a.getString(it) } }
        val items = root.getJSONArray("items")
        return List(items.length()) { i ->
            val a = items.getJSONArray(i)
            Entry(
                name = a.getString(0), lat = a.getDouble(1), lon = a.getDouble(2), sitelinks = a.getInt(3),
                category = cats[a.getInt(4)], kind = kinds[a.getInt(5)],
                image = a.optString(6), wiki = a.optString(7),
            )
        }
    }

    fun supports(category: ActivityLinks.Category) = category in radiusKm

    private val radiusKm = mapOf(
        ActivityLinks.Category.MUSEUMS to 15.0,
        ActivityLinks.Category.MONUMENTS to 15.0,
        ActivityLinks.Category.ATTRACTIONS to 20.0,
        ActivityLinks.Category.PARKS to 15.0,
        ActivityLinks.Category.BEACHES to 40.0,
    )

    /** Lieux de la catégorie autour du centre, les plus connus en premier. */
    fun search(center: GeoPoint, category: ActivityLinks.Category, limit: Int = 60): List<PointOfInterest> {
        val list = entries ?: return emptyList()
        val radius = radiusKm[category] ?: return emptyList()
        val dLat = radius / 111.0
        val dLon = radius / (111.0 * cos(Math.toRadians(center.lat)).coerceAtLeast(0.01))
        val name = category.name
        return list.asSequence()
            .filter { it.category == name && abs(it.lat - center.lat) <= dLat && abs(it.lon - center.lon) <= dLon }
            .map { e ->
                PointOfInterest(
                    id = "wd/${e.lat},${e.lon},${e.name.hashCode()}",
                    name = e.name,
                    kind = e.kind,
                    lat = e.lat,
                    lon = e.lon,
                    distanceM = PlacesParser.distanceMeters(center.lat, center.lon, e.lat, e.lon).toInt(),
                    address = null,
                    openingHours = null,
                    website = null,
                    phone = null,
                    wikipedia = e.wiki.ifBlank { null },
                    notable = true,
                    isVenue = false,
                    isFree = category == ActivityLinks.Category.PARKS || category == ActivityLinks.Category.BEACHES,
                    sitelinks = e.sitelinks,
                    imageUrl = e.image.ifBlank { null }?.let(::commonsThumb),
                )
            }
            .filter { it.distanceM <= radius * 1000 }
            .sortedWith(compareByDescending<PointOfInterest> { it.sitelinks }.thenBy { it.distanceM })
            .take(limit)
            .toList()
    }

    fun commonsThumb(file: String): String =
        "https://commons.wikimedia.org/wiki/Special:FilePath/" +
            URLEncoder.encode(file.replace(' ', '_'), "UTF-8").replace("+", "%20") + "?width=480"
}
