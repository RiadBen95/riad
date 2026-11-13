package com.riad.voyage.ui

import com.riad.voyage.tk
import com.riad.voyage.tf
import com.riad.voyage.t
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.riad.voyage.PlacesState
import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.PlacesApi
import com.riad.voyage.data.PlacesParser
import com.riad.voyage.data.PointOfInterest
import com.riad.voyage.data.WikiSummary
import okhttp3.OkHttpClient

/** Chargeur d'images avec un User-Agent explicite (demandé par Wikimedia). */
internal object Images {
    @Volatile private var loader: ImageLoader? = null
    fun get(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("User-Agent", "Envolee/1.0 (Android app; https://github.com/RiadBen95/riad)")
                                .build(),
                        )
                    }
                    .build()
            }
            .crossfade(true)
            .build()
            .also { loader = it }
    }
}

private fun formatDistance(m: Int) = if (m < 1000) "$m m" else "${"%.1f".format(m / 1000.0)} km"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivitiesScreen(
    city: String,
    category: ActivityLinks.Category,
    places: PlacesState,
    onCity: (String) -> Unit,
    selectedPlace: com.riad.voyage.data.Place?,
    onPlace: (com.riad.voyage.data.Place) -> Unit,
    suggestCities: suspend (String) -> List<com.riad.voyage.data.Place>,
    onCategory: (ActivityLinks.Category) -> Unit,
    onSearch: () -> Unit,
    wiki: suspend (String) -> WikiSummary?,
    isFavorite: (String) -> Boolean = { false },
    onFavorite: (PointOfInterest) -> Unit = {},
    onReadArticle: (String) -> Unit = {},
) {
    var detail by remember { mutableStateOf<PointOfInterest?>(null) }
    detail?.let { p ->
        PlaceDetailSheet(
            place = p, category = places.category, city = places.city, wiki = wiki,
            favorite = isFavorite(p.name), onFavorite = { onFavorite(p) },
            onReadArticle = { tag -> detail = null; onReadArticle(tag) },
            onDismiss = { detail = null },
        )
    }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val search = {
        focus.clearFocus()
        onSearch()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(t("Sorties"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                t("Musées, attractions et restaurants de la ville. Réservez vos billets à l'avance pour éviter la file d'attente."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceField(
                    label = t("Ville"),
                    selected = selectedPlace,
                    onSelect = onPlace,
                    suggest = suggestCities,
                    modifier = Modifier.weight(1f),
                    placeholder = t("Ex. Paris, Alger, Rome…"),
                    onTextChange = onCity,
                    initialText = city,
                    citiesOnly = true,
                )
                Button(onClick = search, enabled = city.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) { Text("OK") }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityLinks.Category.entries.forEach { c ->
                    FilterChip(selected = c == category, onClick = { onCategory(c) }, label = { Text("${c.emoji} ${t(c.label)}") })
                }
            }
        }

        when {
            city.isBlank() -> item { Text(t("Indiquez une ville."), color = MaterialTheme.colorScheme.onSurfaceVariant) }

            category == ActivityLinks.Category.TOURS -> {
                item {
                    Text(
                        t("Les visites guidées et excursions sont proposées par des organisateurs : comparez-les ici."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(ActivityLinks.partners(city, category)) { p ->
                    Card(onClick = { openUrl(context, p.url) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(t("Voir ›"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            places.loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(places.stage.ifBlank { t("Recherche des lieux…") }, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }

            places.error != null -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(places.error)
                        Button(onClick = search) { Text(t("Réessayer")) }
                    }
                }
            }

            places.city.isBlank() || places.category != category -> item {
                Button(onClick = search, modifier = Modifier.fillMaxWidth()) { Text(t("Afficher les lieux")) }
            }

            else -> {
                item {
                    Column {
                        Text(
                            when (places.source) {
                                com.riad.voyage.SOURCE_FAME -> t("Classés par notoriété : nombre de langues dans lesquelles Wikipédia consacre un article au lieu.")
                                com.riad.voyage.SOURCE_GUIDE -> t("Les adresses sélectionnées par le guide de voyage Wikivoyage, dans l'ordre du guide.")
                                com.riad.voyage.SOURCE_RATINGS -> tf("Classés selon les notes et avis {0}", places.ratingSources.joinToString(", ")) +
                                    (if (places.guideFound) t(" et les recommandations du guide Wikivoyage.") else ".")
                                else -> if (places.guideFound) t("Adresses du guide Wikivoyage, complétées par la carte OpenStreetMap.")
                                else t("Le guide ne couvre pas assez cette ville : adresses trouvées sur la carte OpenStreetMap.")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        places.notice?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                itemsIndexed(places.items, key = { _, it -> it.id }) { i, p -> PlaceCard(i + 1, p, places.city, wiki, isFavorite(p.name), onOpen = { detail = p }) { onFavorite(p) } }
                item {
                    Text(
                        t("Données © les contributeurs d'OpenStreetMap, Wikipédia, Wikidata et Wikivoyage."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceCard(
    rank: Int,
    p: PointOfInterest,
    city: String,
    wiki: suspend (String) -> WikiSummary?,
    favorite: Boolean = false,
    onOpen: () -> Unit = {},
    onFavorite: () -> Unit = {},
) {
    val context = LocalContext.current
    var summary by remember(p.id) { mutableStateOf<WikiSummary?>(null) }
    LaunchedEffect(p.id) {
        p.wikipedia?.let { summary = wiki(it) }
    }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column {
            (p.imageUrl ?: summary?.imageUrl)?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = p.name,
                    imageLoader = Images.get(context),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$rank. ${p.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onFavorite) {
                        Text(if (favorite) "♥" else "♡", style = MaterialTheme.typography.titleLarge,
                            color = if (favorite) com.riad.voyage.ui.theme.BrandCoral else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val badges = listOfNotNull(
                    "★ Incontournable".takeIf { p.isMustSee },
                    "📘 Recommandé par le guide".takeIf { p.inGuide },
                )
                if (badges.isNotEmpty()) {
                    Text(
                        badges.joinToString("   "),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = com.riad.voyage.ui.theme.dealColor(),
                    )
                }
                Text(
                    if (p.distanceM >= 0) tk(p.kind) + " · " + tf("à {0} du centre", formatDistance(p.distanceM)) else tk(p.kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (p.ratings.isNotEmpty()) RatingsRow(p)
                p.openNow?.let { open ->
                    Text(
                        if (open) t("Ouvert en ce moment") else t("Fermé en ce moment"),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (open) com.riad.voyage.ui.theme.dealColor() else MaterialTheme.colorScheme.error,
                    )
                }
                (summary?.extract ?: p.description)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                p.address?.let { Text("📍 $it", style = MaterialTheme.typography.bodySmall) }
                p.openingHours?.let {
                    Text("🕒 " + (if (com.riad.voyage.I18n.lang == com.riad.voyage.Lang.FR) PlacesParser.frenchHours(it) else it), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        p.isVenue -> {
                            Button(onClick = { openUrl(context, ActivityLinks.restaurantUrl(p, city)) }) {
                                Text(if (p.website != null && p.kind.startsWith("Restaurant")) t("Réserver") else t("Voir"))
                            }
                            p.phone?.let { phone ->
                                OutlinedButton(onClick = { dial(context, phone) }) { Text(t("Appeler")) }
                            }
                        }
                        p.isFree -> Text(
                            t("Accès libre"),
                            style = MaterialTheme.typography.labelLarge,
                            color = com.riad.voyage.ui.theme.dealColor(),
                        )
                        else -> TicketButton(p, city)
                    }
                    TextButton(onClick = { openUrl(context, ActivityLinks.directionsUrl(p, city)) }) { Text(t("Itinéraire")) }
                }
                val more = p.wikipedia?.let { PlacesApi.wikiPageUrl(it) } ?: p.website.takeIf { !p.isVenue }
                more?.let { url ->
                    TextButton(onClick = { openUrl(context, url) }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (p.wikipedia != null) t("En savoir plus (Wikipédia) ›") else t("Site officiel ›"))
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketButton(p: PointOfInterest, city: String) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { open = true }) { Text(t("Réserver des billets")) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ActivityLinks.ticketUrls(p, city).forEach { partner ->
                DropdownMenuItem(
                    text = { Text(tf("Billets sur {0}", partner.name)) },
                    onClick = {
                        open = false
                        openUrl(context, partner.url)
                    },
                )
            }
            p.website?.let { site ->
                DropdownMenuItem(
                    text = { Text(t("Site officiel du lieu")) },
                    onClick = {
                        open = false
                        openUrl(context, site)
                    },
                )
            }
        }
    }
}

private fun dial(context: Context, phone: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.replace(" ", ""))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun RatingsRow(p: PointOfInterest) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        p.ratings.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "★ ${"%.1f".format(r.rating)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = com.riad.voyage.ui.theme.BrandCoral,
                )
                Text(
                    " ${r.source.label} · " + tf("{0} avis", "%,d".format(r.count).replace(',', ' ')),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (r.url != null) Modifier.clickable { openUrl(context, r.url) } else Modifier,
                )
            }
            r.ranking?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }
    }
}
