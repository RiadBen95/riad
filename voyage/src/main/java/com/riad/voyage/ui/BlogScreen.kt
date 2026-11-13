package com.riad.voyage.ui

import com.riad.voyage.tk
import com.riad.voyage.tf
import com.riad.voyage.t
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.riad.voyage.BlogState
import com.riad.voyage.data.Article
import com.riad.voyage.data.ArticleCard
import com.riad.voyage.data.Blog
import com.riad.voyage.data.BlogCity
import com.riad.voyage.data.BlogFilter
import com.riad.voyage.data.Place
import com.riad.voyage.data.WikiSummary
import kotlinx.coroutines.delay

@Composable
fun BlogScreen(
    state: BlogState,
    context: List<Pair<BlogCity, String>>,
    onCity: (BlogCity, String?) -> Unit,
    onPlace: (Place) -> Unit,
    onClearCity: () -> Unit,
    onFilter: (BlogFilter) -> Unit,
    onArticle: (String) -> Unit,
    onCloseArticle: () -> Unit,
    suggestCities: suspend (String) -> List<Place>,
    searchArticles: suspend (String) -> List<Pair<String, String>>,
    wiki: suspend (String) -> WikiSummary?,
    hasPersonalFilter: Boolean,
) {
    when {
        state.article != null -> ArticleReader(state.article, onCloseArticle)
        state.articleLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(t("Blogue"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    t("Récits et histoire des lieux les plus emblématiques, choisis selon vos voyages et vos envies."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { BlogSearchBar(onPlace, onArticle, suggestCities, searchArticles) }
            state.error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }

            val city = state.city
            if (city == null) {
                if (context.isNotEmpty()) {
                    item { Text(t("Selon vos recherches"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            context.take(3).forEach { (c, why) -> AssistChip(onClick = { onCity(c, why) }, label = { Text(t(c.name)) }) }
                        }
                    }
                }
                item { Text(t("Les villes emblématiques du monde"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(Blog.cities.chunked(3)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { c -> CityIcon(c, wiki, Modifier.weight(1f)) { onCity(c, null) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                item { CityHeader(city, state.reason, context, onCity, onClearCity) }
                item { Filters(state.filter, hasPersonalFilter, onFilter) }
                if (state.loading) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
                items(state.cards, key = { it.wiki }) { card -> ArticleCardView(card, wiki) { onArticle(card.wiki) } }
                if (!state.loading && state.cards.size <= 1) {
                    item { Text(t("Peu d'articles pour ce filtre : essayez « Tout »."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                item {
                    Text(
                        t("Textes : Wikipédia, licence CC BY-SA. Photos : Wikimedia Commons."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Recherche d'une ville, d'un monument ou d'un lieu. */
@Composable
private fun BlogSearchBar(
    onPlace: (Place) -> Unit,
    onArticle: (String) -> Unit,
    suggestCities: suspend (String) -> List<Place>,
    searchArticles: suspend (String) -> List<Pair<String, String>>,
) {
    var text by remember { mutableStateOf("") }
    var cities by remember { mutableStateOf<List<Place>>(emptyList()) }
    var articles by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val focus = LocalFocusManager.current
    LaunchedEffect(text) {
        if (text.trim().length < 2) { cities = emptyList(); articles = emptyList(); return@LaunchedEffect }
        delay(300)
        cities = suggestCities(text).take(3)
        articles = searchArticles(text).take(6)
    }
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            leadingIcon = { Icon(AppIcons.Search, null) },
            placeholder = { Text(t("Une ville, un monument, un lieu…")) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (cities.isNotEmpty() || articles.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                cities.forEach { p ->
                    SuggestionRow("🏙️", p.name, t(p.country)) {
                        focus.clearFocus(); text = ""; onPlace(p)
                    }
                    HorizontalDivider()
                }
                articles.forEach { (tag, title) ->
                    SuggestionRow("📖", title, "Article") {
                        focus.clearFocus(); text = ""; onArticle(tag)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, modifier = Modifier.width(32.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CityIcon(c: BlogCity, wiki: suspend (String) -> WikiSummary?, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var image by remember(c.wiki) { mutableStateOf<String?>(null) }
    LaunchedEffect(c.wiki) { image = wiki(c.wiki)?.imageUrl }
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(c.name.take(1), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            image?.let {
                AsyncImage(model = it, contentDescription = c.name, imageLoader = Images.get(context),
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(t(c.name), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t(c.country), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CityHeader(
    city: BlogCity,
    reason: String?,
    context: List<Pair<BlogCity, String>>,
    onCity: (BlogCity, String?) -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                reason?.let { Text(t(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                Text(t(city.name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onClear) { Text(t("Toutes les villes")) }
        }
        val others = context.filter { it.first.name != city.name }
        if (others.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.take(3).forEach { (c, why) -> AssistChip(onClick = { onCity(c, why) }, label = { Text(t(c.name)) }) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(current: BlogFilter, personal: Boolean, onFilter: (BlogFilter) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BlogFilter.entries.filter { it != BlogFilter.FOR_YOU || personal }.forEach { f ->
            FilterChip(selected = f == current, onClick = { onFilter(f) }, label = { Text(if (f == BlogFilter.FOR_YOU) "✨ ${t(f.label)}" else t(f.label)) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleCardView(card: ArticleCard, wiki: suspend (String) -> WikiSummary?, onClick: () -> Unit) {
    val context = LocalContext.current
    var summary by remember(card.wiki) { mutableStateOf<WikiSummary?>(null) }
    LaunchedEffect(card.wiki) { summary = wiki(card.wiki) }
    val image = card.imageUrl ?: summary?.imageUrl
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (card.category == null) 200.dp else 160.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                image?.let {
                    AsyncImage(model = it, contentDescription = card.title, imageLoader = Images.get(context),
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                    Text(tk(card.kind), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
                    Text(card.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val badges = listOfNotNull(
                    "✨ Selon vos goûts".takeIf { card.tasteMatch },
                    "★ Incontournable".takeIf { card.fame in 40 until 10_000 },
                )
                if (badges.isNotEmpty()) {
                    Text(badges.joinToString("   "), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = com.riad.voyage.ui.theme.dealColor())
                }
                Text(
                    summary?.extract ?: t("Chargement…"),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(t("Lire l'article ›"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ArticleReader(article: Article, onClose: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(260.dp).background(MaterialTheme.colorScheme.primaryContainer)) {
                article.imageUrl?.let {
                    AsyncImage(model = it, contentDescription = article.title, imageLoader = Images.get(context),
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
                TextButton(onClick = onClose, modifier = Modifier.padding(8.dp)) { Text(t("← Retour"), color = Color.White) }
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(article.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(tf("{0} min de lecture", article.readingMinutes), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Text(article.intro, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
        items(article.sections) { (title, body) ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openUrl(context, article.url) }) { Text(t("Lire sur Wikipédia")) }
                Text(
                    tf("Texte issu de l'article Wikipédia « {0} », licence CC BY-SA 4.0.", article.title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
