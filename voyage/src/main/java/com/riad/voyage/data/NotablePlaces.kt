package com.riad.voyage.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor

/**
 * Lieux remarquables du monde (générés depuis Wikidata par tools/build_places.py), publiés en ligne
 * et découpés en zones de 2° × 2°. L'app ne télécharge que la zone de la ville (quelques dizaines
 * de Ko, servis par un CDN), puis la garde en mémoire : la recherche est quasi instantanée.
 */
object NotablePlaces {
    private const val BRANCH = "ccr-a5448643-kldk3d"
    private val servers = listOf(
        "https://cdn.jsdelivr.net/gh/RiadBen95/riad@$BRANCH/data/places/",
        "https://raw.githubusercontent.com/RiadBen95/riad/$BRANCH/data/places/",
    )

    private class Entry(
        val name: String, val lat: Double, val lon: Double, val sitelinks: Int,
        val category: String, val kind: String, val image: String, val wiki: String,
    )

    private val tiles = ConcurrentHashMap<String, List<Entry>>()

    fun supports(category: ActivityLinks.Category) = category in radiusKm

    private val radiusKm = mapOf(
        ActivityLinks.Category.MUSEUMS to 15.0,
        ActivityLinks.Category.MONUMENTS to 15.0,
        ActivityLinks.Category.ATTRACTIONS to 20.0,
        ActivityLinks.Category.PARKS to 15.0,
        ActivityLinks.Category.BEACHES to 40.0,
    )

    fun tileKey(lat: Double, lon: Double) = "${floor(lat / 2).toInt()}_${floor(lon / 2).toInt()}"

    /** Lieux de la catégorie autour du centre, les plus connus en premier. */
    suspend fun search(center: GeoPoint, category: ActivityLinks.Category, limit: Int = 60): List<PointOfInterest> {
        val radius = radiusKm[category] ?: return emptyList()
        val dLat = radius / 111.0
        val dLon = radius / (111.0 * cos(Math.toRadians(center.lat)).coerceAtLeast(0.01))
        // Zones couvertes par le rayon (1 à 4 selon la position de la ville).
        val keys = listOf(-dLat, dLat).flatMap { a -> listOf(-dLon, dLon).map { b -> tileKey(center.lat + a, center.lon + b) } }
            .distinct()
        val entries = coroutineScope { keys.map { async { tile(it) } }.awaitAll() }.flatten()
        val name = category.name
        return entries.asSequence()
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
            .distinctBy { it.name.lowercase() }
            .sortedWith(compareByDescending<PointOfInterest> { it.sitelinks }.thenBy { it.distanceM })
            .take(limit)
            .toList()
    }

    /** Zone en mémoire, sinon téléchargée (CDN, puis GitHub en secours). Une zone absente = aucun lieu. */
    private suspend fun tile(key: String): List<Entry> {
        tiles[key]?.let { return it }
        var last: Exception? = null
        for (server in servers) {
            try {
                val list = parse(download("$server$key.json"))
                tiles[key] = list
                return list
            } catch (e: FileNotFoundException) {
                tiles[key] = emptyList()
                return emptyList()
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Données des lieux indisponibles")
    }

    private suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "Envolee/1.0 (Android app)")
            when (val code = conn.responseCode) {
                in 200..299 -> conn.inputStream.bufferedReader().use { it.readText() }
                404 -> throw FileNotFoundException(url)
                else -> throw IOException("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Pour les tests : place une zone en mémoire sans la télécharger. */
    fun putTileForTest(key: String, json: String) {
        tiles[key] = parse(json)
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

    fun commonsThumb(file: String): String =
        "https://commons.wikimedia.org/wiki/Special:FilePath/" +
            URLEncoder.encode(file.replace(' ', '_'), "UTF-8").replace("+", "%20") + "?width=480"
}
