package com.riad.voyage

import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.Article
import com.riad.voyage.data.ArticleCard
import com.riad.voyage.data.Blog
import com.riad.voyage.data.BlogCity
import com.riad.voyage.data.BlogFilter
import com.riad.voyage.data.ExperienceBuilder
import com.riad.voyage.data.NotablePlaces
import com.riad.voyage.data.Place
import com.riad.voyage.data.Quiz
import com.riad.voyage.data.WikiArticles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Blogue : articles Wikipédia d'une ville, triés selon les envies ([answers] de l'envolée, [activityCategory] des sorties).
 */
class BlogModel(
    private val core: AppCore,
    private val answers: () -> Quiz.Answers,
    private val activityCategory: () -> ActivityLinks.Category,
) {
    private val _blog = MutableStateFlow(BlogState())
    val state: StateFlow<BlogState> = _blog.asStateFlow()
    private var blogJob: kotlinx.coroutines.Job? = null

    fun reset() { _blog.value = BlogState() }

    /** Langue changée : les articles de la ville sont rechargés. */
    fun reload() { if (_blog.value.city != null) loadBlogCards() }
    fun openBlogCity(c: BlogCity, reason: String? = null) {
        val personal = blogInterestCategories().isNotEmpty()
        _blog.update {
            it.copy(city = c, reason = reason, article = null,
                filter = if (personal) BlogFilter.FOR_YOU else BlogFilter.ALL)
        }
        loadBlogCards()
    }

    /** Ville ou lieu choisi dans la barre de recherche du blogue. */
    fun openBlogPlace(p: Place) {
        core.scope.launch {
            val geo = core.resolveCity(p.name, p) ?: run {
                _blog.update { it.copy(error = t("Lieu introuvable.")) }
                return@launch
            }
            openBlogCity(Blog.city(p.name) ?: BlogCity(p.name, p.country, "fr:${p.name}", geo.lat, geo.lon))
        }
    }

    fun clearBlogCity() = _blog.update { BlogState() }

    fun setBlogFilter(f: BlogFilter) {
        _blog.update { it.copy(filter = f) }
        loadBlogCards()
    }

    /** Catégories qui intéressent la personne : envies de l'expérience, dernière catégorie de sorties. */
    private fun blogInterestCategories(): List<ActivityLinks.Category> {
        val fromExperience = answers().interests.mapNotNull { it.category }
        val fromActivities = listOf(activityCategory()).filter { NotablePlaces.supports(it) }
        return (fromExperience + fromActivities).filter { NotablePlaces.supports(it) }.distinct()
    }

    private fun loadBlogCards() {
        val st = _blog.value
        val city = st.city ?: return
        blogJob?.cancel()
        _blog.update { it.copy(loading = true, error = null, cards = emptyList()) }
        blogJob = core.scope.launch {
            val answers = answers()
            val categories = if (st.filter == BlogFilter.FOR_YOU) blogInterestCategories()
                .ifEmpty { BlogFilter.ALL.categories } else st.filter.categories
            val cards = withTimeoutOrNull(15_000) {
                categories.flatMap { c ->
                    val tastes = ExperienceBuilder.tastesFor(answers, c)
                    runCatching { NotablePlaces.search(city.geo, c, limit = 20) }.getOrNull().orEmpty()
                        .filter { it.wikipedia != null }
                        .map { p ->
                            ArticleCard(
                                wiki = p.wikipedia!!, title = p.name, kind = p.kind, category = c, imageUrl = p.imageUrl,
                                fame = p.sitelinks, tasteMatch = ExperienceBuilder.tasteScore(p, tastes) > 0,
                            )
                        }
                }.distinctBy { it.wiki }
                    .sortedWith(compareByDescending<ArticleCard> { it.tasteMatch }.thenByDescending { it.fame })
                    .take(40)
            }
            val cityCard = ArticleCard(city.wiki, tf("Découvrir {0}", t(city.name)), tf("Ville · {0}", t(city.country)), null, null, 10_000)
            _blog.update {
                if (cards == null) it.copy(loading = false, error = t("Les articles ne se chargent pas. Vérifiez la connexion."), cards = listOf(cityCard))
                else it.copy(loading = false, cards = listOf(cityCard) + cards)
            }
        }
    }

    fun openArticle(tag: String) {
        _blog.update { it.copy(articleLoading = true, article = null, error = null) }
        core.scope.launch {
            val article = attempt(15_000) { WikiArticles.article(tag) }?.getOrNull()
            _blog.update {
                if (article == null) it.copy(articleLoading = false, error = t("Article indisponible pour le moment."))
                else it.copy(articleLoading = false, article = article)
            }
        }
    }

    fun closeArticle() = _blog.update { it.copy(article = null, articleLoading = false) }

    suspend fun searchArticles(q: String): List<Pair<String, String>> =
        runCatching { WikiArticles.search(q) }.getOrNull().orEmpty()

}
