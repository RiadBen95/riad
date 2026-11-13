package com.riad.voyage.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** Une adresse recommandée par le guide Wikivoyage (fiche {{eat|…}}, {{Listing|…}}, etc.). */
data class GuideListing(
    val type: String,              // see, eat, drink, buy, sleep
    val name: String,
    val lat: Double?,
    val lon: Double?,
    val address: String?,
    val phone: String?,
    val url: String?,
    val hours: String?,
    val price: String?,
    val description: String?,
)

/** Lecture des fiches du guide dans le texte source (wikitexte) d'une page Wikivoyage. */
object GuideParser {
    private val templateTypes = mapOf(
        "see" to "see", "do" to "see", "voir" to "see", "faire" to "see",
        "eat" to "eat", "manger" to "eat",
        "drink" to "drink", "sortir" to "drink", "boire" to "drink",
        "buy" to "buy", "acheter" to "buy",
        "sleep" to "sleep", "se loger" to "sleep", "dormir" to "sleep",
    )

    fun listings(wikitext: String): List<GuideListing> {
        val out = mutableListOf<GuideListing>()
        var i = 0
        while (true) {
            val start = wikitext.indexOf("{{", i)
            if (start < 0) break
            val end = matchingEnd(wikitext, start) ?: break
            val body = wikitext.substring(start + 2, end)
            i = end + 2
            val parts = splitTopLevel(body)
            val templateName = parts.first().trim().lowercase()
            val params = parts.drop(1).mapNotNull { p ->
                val eq = p.indexOf('=')
                if (eq <= 0) null else p.substring(0, eq).trim().lowercase() to p.substring(eq + 1).trim()
            }.toMap()
            val type = templateTypes[templateName]
                ?: if (templateName == "listing" || templateName == "fiche") templateTypes[params["type"]?.lowercase()?.trim()] else null
            type ?: continue
            val name = clean(params["name"] ?: params["nom"] ?: continue)
            if (name.length < 2) continue
            out += GuideListing(
                type = type,
                name = name,
                lat = (params["lat"] ?: params["latitude"])?.trim()?.toDoubleOrNull(),
                lon = (params["long"] ?: params["lon"] ?: params["longitude"])?.trim()?.toDoubleOrNull(),
                address = (params["address"] ?: params["adresse"])?.let(::clean)?.ifBlank { null },
                phone = (params["phone"] ?: params["téléphone"] ?: params["tél"] ?: params["tel"])?.let(::clean)?.ifBlank { null },
                url = (params["url"] ?: params["site"])?.trim()?.takeIf { it.startsWith("http") },
                hours = (params["hours"] ?: params["horaire"] ?: params["horaires"])?.let(::clean)?.ifBlank { null },
                price = (params["price"] ?: params["prix"])?.let(::clean)?.ifBlank { null },
                description = (params["content"] ?: params["description"])?.let(::clean)?.ifBlank { null },
            )
        }
        return out.distinctBy { it.type + "|" + it.name.lowercase() }
    }

    /** Position du « }} » qui ferme le modèle ouvert en [start], en tenant compte des imbrications. */
    private fun matchingEnd(s: String, start: Int): Int? {
        var depth = 0
        var i = start
        while (i < s.length - 1) {
            when {
                s.startsWith("{{", i) -> { depth++; i += 2 }
                s.startsWith("}}", i) -> { depth--; if (depth == 0) return i; i += 2 }
                else -> i++
            }
        }
        return null
    }

    /** Découpe sur les « | » qui ne sont ni dans un lien [[…]] ni dans un modèle {{…}}. */
    private fun splitTopLevel(s: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var last = 0
        var i = 0
        while (i < s.length) {
            when {
                s.startsWith("{{", i) || s.startsWith("[[", i) -> { depth++; i += 2; continue }
                s.startsWith("}}", i) || s.startsWith("]]", i) -> { depth--; i += 2; continue }
                s[i] == '|' && depth == 0 -> { parts += s.substring(last, i); last = i + 1 }
            }
            i++
        }
        parts += s.substring(last)
        return parts
    }

    /** Retire la mise en forme wiki : [[lien|texte]] → texte, '''gras''', balises, modèles. */
    fun clean(s: String): String = s
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("\\{\\{[^{}]*\\}\\}"), "")
        .replace(Regex("\\[\\[(?:[^\\]|]*\\|)?([^\\]]*)\\]\\]"), "$1")
        .replace(Regex("\\[https?://\\S+\\s+([^\\]]+)\\]"), "$1")
        .replace("'''", "").replace("''", "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val cafeWords = Regex("café|cafe|coffee|salon de thé|tea ?room|pâtisserie|patisserie|boulangerie|brunch|glacier", RegexOption.IGNORE_CASE)
    private val clubWords = Regex("club|discoth|boîte|boite|night|dancing", RegexOption.IGNORE_CASE)

    /** Adresses du guide correspondant à la catégorie, dans l'ordre du guide. */
    fun select(listings: List<GuideListing>, category: ActivityLinks.Category): List<GuideListing> {
        fun text(l: GuideListing) = l.name + " " + (l.description ?: "")
        return listings.filter { l ->
            val cafe = cafeWords.containsMatchIn(text(l))
            val club = clubWords.containsMatchIn(text(l))
            when (category) {
                ActivityLinks.Category.RESTAURANTS -> l.type == "eat" && !cafe
                ActivityLinks.Category.CAFES -> (l.type == "eat" || l.type == "drink") && cafe
                ActivityLinks.Category.BARS -> l.type == "drink" && !cafe && !club
                ActivityLinks.Category.NIGHTLIFE -> l.type == "drink" && club
                ActivityLinks.Category.SHOPPING -> l.type == "buy"
                else -> false
            }
        }
    }

    fun toPoi(l: GuideListing, category: ActivityLinks.Category, center: GeoPoint): PointOfInterest {
        val hasCoords = l.lat != null && l.lon != null
        val kind = when (category) {
            ActivityLinks.Category.RESTAURANTS -> "Restaurant"
            ActivityLinks.Category.CAFES -> "Café"
            ActivityLinks.Category.BARS -> "Bar"
            ActivityLinks.Category.NIGHTLIFE -> "Sortie de nuit"
            ActivityLinks.Category.SHOPPING -> "Boutique"
            else -> "Lieu"
        } + (l.price?.let { " · $it" } ?: "")
        return PointOfInterest(
            id = "wv/${l.type}/${l.name}",
            name = l.name,
            kind = kind,
            lat = l.lat ?: center.lat,
            lon = l.lon ?: center.lon,
            distanceM = if (hasCoords) PlacesParser.distanceMeters(center.lat, center.lon, l.lat!!, l.lon!!).toInt() else -1,
            address = l.address,
            openingHours = l.hours,
            website = l.url,
            phone = l.phone,
            wikipedia = null,
            notable = true,
            isVenue = true,
            inGuide = true,
            description = l.description,
            hasCoordinates = hasCoords,
        )
    }
}

/** Récupère toutes les fiches du guide Wikivoyage d'une ville (page principale + quartiers). */
object WikivoyageGuide {
    private val cache = ConcurrentHashMap<String, List<GuideListing>>()

    suspend fun listings(city: String, cityWikidata: String?): List<GuideListing> {
        val key = city.lowercase()
        cache[key]?.let { return it }
        val result = mutableListOf<GuideListing>()
        for ((lang, title) in titles(city, cityWikidata)) {
            result += pageWithDistricts(lang, title)
            if (result.count { it.type != "sleep" } >= 15) break  // le guide français suffit
        }
        val list = result.distinctBy { it.type + "|" + it.name.lowercase() }
        if (list.isNotEmpty()) cache[key] = list
        return list
    }

    /** Titres possibles : le nom de la ville directement, puis ceux donnés par Wikidata. */
    private suspend fun titles(city: String, cityWikidata: String?): List<Pair<String, String>> {
        val direct = listOf("fr" to city, "en" to city)
        val fromWikidata = cityWikidata?.let {
            runCatching {
                PlacesParser.wikivoyageTitles(
                    PlacesApi.get("https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&props=sitelinks&ids=${enc(it)}"),
                )
            }.getOrNull()
        }.orEmpty()
        return (fromWikidata + direct).distinctBy { it.first + "|" + it.second.lowercase() }
    }

    private suspend fun pageWithDistricts(lang: String, title: String): List<GuideListing> = coroutineScope {
        val main = async { raw(lang, title) }
        // Les grandes villes répartissent leurs adresses dans des sous-pages de quartier (« Paris/Marais »).
        val districts = async {
            withTimeoutOrNull(6_000) {
                runCatching {
                    val json = JSONObject(
                        PlacesApi.get(
                            "https://$lang.wikivoyage.org/w/api.php?action=query&list=prefixsearch&format=json" +
                                "&pslimit=25&pssearch=${enc("$title/")}",
                        ),
                    )
                    val arr = json.optJSONObject("query")?.optJSONArray("prefixsearch")
                    List(arr?.length() ?: 0) { arr!!.getJSONObject(it).optString("title") }.filter { it.isNotBlank() }
                }.getOrNull()
            }.orEmpty()
        }
        val mainText = main.await()
        val subTexts = districts.await().take(20).map { async { raw(lang, it) } }.awaitAll()
        (listOfNotNull(mainText) + subTexts.filterNotNull()).flatMap { GuideParser.listings(it) }
    }

    /** Texte source d'une page (suit une redirection). Null si la page n'existe pas. */
    private suspend fun raw(lang: String, title: String, depth: Int = 0): String? {
        val text = withTimeoutOrNull(8_000) {
            runCatching {
                PlacesApi.get("https://$lang.wikivoyage.org/w/index.php?action=raw&title=${enc(title.replace(' ', '_'))}")
            }.getOrNull()
        } ?: return null
        val redirect = Regex("^#(?:REDIRECT|REDIRECTION)\\s*\\[\\[([^\\]|#]+)", RegexOption.IGNORE_CASE).find(text.trimStart())
        return if (redirect != null && depth < 2) raw(lang, redirect.groupValues[1].trim(), depth + 1) else text
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
