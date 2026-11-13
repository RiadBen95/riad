package com.riad.voyage.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** Ville mise en avant dans le blogue (icône sur la page d'accueil du blogue). */
data class BlogCity(val name: String, val country: String, val wiki: String, val lat: Double, val lon: Double) {
    val geo get() = GeoPoint(lat, lon, name)
}

/** Carte d'article dans la liste du blogue. */
data class ArticleCard(
    val wiki: String,            // ex. « fr:Colisée »
    val title: String,
    val kind: String,
    val category: ActivityLinks.Category?,
    val imageUrl: String?,
    val fame: Int,
    val tasteMatch: Boolean = false,
)

/** Article complet, lu dans l'application. */
data class Article(
    val wiki: String,
    val title: String,
    val imageUrl: String?,
    val intro: String,
    val sections: List<Pair<String, String>>,
    val url: String,
) {
    val readingMinutes get() = ((intro.length + sections.sumOf { it.second.length }) / 1100).coerceAtLeast(1)
}

/** Filtres du blogue. */
enum class BlogFilter(val label: String, val categories: List<ActivityLinks.Category>) {
    FOR_YOU("Pour vous", emptyList()),
    ALL("Tout", listOf(ActivityLinks.Category.MONUMENTS, ActivityLinks.Category.MUSEUMS, ActivityLinks.Category.ATTRACTIONS, ActivityLinks.Category.PARKS, ActivityLinks.Category.BEACHES)),
    MONUMENTS("Monuments", listOf(ActivityLinks.Category.MONUMENTS)),
    MUSEUMS("Musées", listOf(ActivityLinks.Category.MUSEUMS)),
    NATURE("Nature et plages", listOf(ActivityLinks.Category.PARKS, ActivityLinks.Category.BEACHES)),
    FUN("Loisirs", listOf(ActivityLinks.Category.ATTRACTIONS)),
}

object Blog {
    /** Les villes les plus emblématiques du monde. */
    val cities = listOf(
        BlogCity("Paris", "France", "fr:Paris", 48.8566, 2.3522),
        BlogCity("Rome", "Italie", "fr:Rome", 41.9028, 12.4964),
        BlogCity("Londres", "Royaume-Uni", "fr:Londres", 51.5074, -0.1278),
        BlogCity("New York", "États-Unis", "fr:New York", 40.7128, -74.0060),
        BlogCity("Istanbul", "Turquie", "fr:Istanbul", 41.0082, 28.9784),
        BlogCity("Barcelone", "Espagne", "fr:Barcelone", 41.3851, 2.1734),
        BlogCity("Marrakech", "Maroc", "fr:Marrakech", 31.6295, -7.9811),
        BlogCity("Alger", "Algérie", "fr:Alger", 36.7538, 3.0588),
        BlogCity("Le Caire", "Égypte", "fr:Le Caire", 30.0444, 31.2357),
        BlogCity("Dubaï", "Émirats arabes unis", "fr:Dubaï", 25.2048, 55.2708),
        BlogCity("Tokyo", "Japon", "fr:Tokyo", 35.6762, 139.6503),
        BlogCity("Kyoto", "Japon", "fr:Kyoto", 35.0116, 135.7681),
        BlogCity("Bangkok", "Thaïlande", "fr:Bangkok", 13.7563, 100.5018),
        BlogCity("Venise", "Italie", "fr:Venise", 45.4408, 12.3155),
        BlogCity("Lisbonne", "Portugal", "fr:Lisbonne", 38.7223, -9.1393),
        BlogCity("Amsterdam", "Pays-Bas", "fr:Amsterdam", 52.3676, 4.9041),
        BlogCity("Prague", "Tchéquie", "fr:Prague", 50.0755, 14.4378),
        BlogCity("Athènes", "Grèce", "fr:Athènes", 37.9838, 23.7275),
        BlogCity("Rio de Janeiro", "Brésil", "fr:Rio de Janeiro", -22.9068, -43.1729),
        BlogCity("Sydney", "Australie", "fr:Sydney", -33.8688, 151.2093),
        BlogCity("Séville", "Espagne", "fr:Séville", 37.3891, -5.9845),
        BlogCity("Tunis", "Tunisie", "fr:Tunis", 36.8065, 10.1815),
    )

    fun city(name: String) = cities.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    /** Articles des lieux emblématiques d'une ville, classés par notoriété. */
    suspend fun articlesFor(center: GeoPoint, categories: List<ActivityLinks.Category>, perCategory: Int = 15): List<ArticleCard> =
        categories.flatMap { c ->
            runCatching { NotablePlaces.search(center, c, limit = perCategory) }.getOrNull().orEmpty()
                .filter { it.wikipedia != null }
                .map { p ->
                    ArticleCard(
                        wiki = p.wikipedia!!, title = p.name, kind = p.kind, category = c,
                        imageUrl = p.imageUrl, fame = p.sitelinks,
                    )
                }
        }.distinctBy { it.wiki }.sortedByDescending { it.fame }
}

/** Accès aux articles Wikipédia : recherche, lecture complète. */
object WikiArticles {
    private val cache = ConcurrentHashMap<String, Article>()

    private fun split(tag: String): Pair<String, String> {
        val i = tag.indexOf(':')
        return if (i in 2..3) tag.substring(0, i) to tag.substring(i + 1) else "fr" to tag
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Recherche d'articles (titres) pour la barre de recherche du blogue. */
    suspend fun search(query: String, lang: String = com.riad.voyage.I18n.wikiLang): List<Pair<String, String>> {
        if (query.trim().length < 2) return emptyList()
        val json = PlacesApi.get(
            "https://$lang.wikipedia.org/w/api.php?action=opensearch&format=json&namespace=0&limit=8&search=${enc(query.trim())}",
        )
        return parseSearch(json, lang)
    }

    /** Réponse opensearch : [requête, [titres], [descriptions], [liens]] → (tag, titre). */
    fun parseSearch(json: String, lang: String = "fr"): List<Pair<String, String>> {
        val arr = JSONArray(json)
        val titles = arr.optJSONArray(1) ?: return emptyList()
        return List(titles.length()) { titles.getString(it) }
            .filter { !it.contains("(homonymie)") }
            .map { "$lang:$it" to it }
    }

    /** Texte complet de l'article, découpé en sections lisibles. */
    suspend fun article(original: String): Article? {
        val tag = WikiLang.localize(original)
        cache[tag]?.let { return it }
        val (lang, title) = split(tag)
        val json = PlacesApi.get(
            "https://$lang.wikipedia.org/w/api.php?action=query&format=json&redirects=1&prop=extracts%7Cpageimages" +
                "&explaintext=1&exsectionformat=wiki&piprop=original&titles=${enc(title)}",
        )
        return parseArticle(json, lang)?.also { cache[tag] = it }
    }

    private val skippedSections = setOf(
        "notes et références", "références", "notes", "voir aussi", "bibliographie", "liens externes",
        "articles connexes", "annexes", "sources", "galerie", "filmographie", "discographie",
    )

    fun parseArticle(json: String, lang: String = "fr"): Article? {
        val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val page = pages.keys().asSequence().map { pages.getJSONObject(it) }.firstOrNull { !it.has("missing") } ?: return null
        val title = page.optString("title")
        val text = page.optString("extract")
        if (text.isBlank()) return null
        val image = page.optJSONObject("original")?.optString("source")?.ifBlank { null }

        // Sections : « == Titre == » (niveau 2) ; les sous-titres « === … === » restent dans la section.
        val parts = Regex("(?m)^==\\s*([^=].*?)\\s*==\\s*$").split(text)
        val headers = Regex("(?m)^==\\s*([^=].*?)\\s*==\\s*$").findAll(text).map { it.groupValues[1].trim() }.toList()
        val intro = parts.first().trim()
        val sections = headers.zip(parts.drop(1))
            .filter { (h, _) -> h.lowercase() !in skippedSections }
            .map { (h, body) ->
                h to body.replace(Regex("(?m)^=+\\s*(.*?)\\s*=+\\s*$"), "\n$1\n").replace(Regex("\n{3,}"), "\n\n").trim()
            }
            .filter { it.second.length > 80 }
            .take(10)
        return Article(
            wiki = "$lang:$title", title = title, imageUrl = image, intro = intro, sections = sections,
            url = "https://$lang.wikipedia.org/wiki/${enc(title.replace(' ', '_'))}",
        )
    }
}

/**
 * Article Wikipédia équivalent dans la langue de l'app (liens interlangues), avec repli sur
 * l'anglais puis sur la langue d'origine si l'article n'existe pas.
 */
object WikiLang {
    private val cache = ConcurrentHashMap<String, String>()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    suspend fun localize(tag: String): String {
        val target = com.riad.voyage.I18n.wikiLang
        val i = tag.indexOf(':')
        if (i !in 2..3) return tag
        val lang = tag.substring(0, i)
        val title = tag.substring(i + 1)
        if (lang == target) return tag
        val key = "$target|$tag"
        cache[key]?.let { return it }
        suspend fun link(to: String): String? = runCatching {
            val json = PlacesApi.get(
                "https://$lang.wikipedia.org/w/api.php?action=query&format=json&redirects=1&prop=langlinks" +
                    "&lllang=$to&titles=${enc(title)}",
            )
            val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages") ?: return@runCatching null
            pages.keys().asSequence()
                .mapNotNull { pages.optJSONObject(it)?.optJSONArray("langlinks")?.optJSONObject(0)?.optString("*") }
                .firstOrNull()?.ifBlank { null }
        }.getOrNull()
        val out = link(target)?.let { "$target:$it" }
            ?: (if (target != "en" && lang != "en") link("en")?.let { "en:$it" } else null)
            ?: tag
        cache[key] = out
        return out
    }
}
