package com.riad.voyage.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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

private fun formatDistance(m: Int) = if (m < 1000) "à $m m" else "à ${"%.1f".format(m / 1000.0)} km"

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
) {
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
            Text("Sorties", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Musées, attractions et restaurants de la ville. Réservez vos billets à l'avance pour éviter la file d'attente.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceField(
                    label = "Ville",
                    selected = selectedPlace,
                    onSelect = onPlace,
                    suggest = suggestCities,
                    modifier = Modifier.weight(1f),
                    placeholder = "Ex. Paris, Alger, Rome…",
                    onTextChange = onCity,
                    initialText = city,
                )
                Button(onClick = search, enabled = city.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) { Text("OK") }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityLinks.Category.entries.forEach { c ->
                    FilterChip(selected = c == category, onClick = { onCategory(c) }, label = { Text("${c.emoji} ${c.label}") })
                }
            }
        }

        when {
            city.isBlank() -> item { Text("Indiquez une ville.", color = MaterialTheme.colorScheme.onSurfaceVariant) }

            category == ActivityLinks.Category.TOURS -> {
                item {
                    Text(
                        "Les visites guidées et excursions sont proposées par des organisateurs : comparez-les ici.",
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
                            Text("Voir ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            places.loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(places.stage.ifBlank { "Recherche des lieux…" }, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }

            places.error != null -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(places.error)
                        Button(onClick = search) { Text("Réessayer") }
                    }
                }
            }

            places.city.isBlank() || places.category != category -> item {
                Button(onClick = search, modifier = Modifier.fillMaxWidth()) { Text("Afficher les lieux") }
            }

            else -> {
                item {
                    Column {
                        Text(
                            "Top ${places.items.size} · ${category.label} à ${places.city}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when (places.source) {
                                com.riad.voyage.SOURCE_FAME -> "Classés par notoriété : nombre de langues dans lesquelles Wikipédia consacre un article au lieu."
                                com.riad.voyage.SOURCE_GUIDE -> "Les adresses sélectionnées par le guide de voyage Wikivoyage, dans l'ordre du guide."
                                else -> if (places.guideFound) "Adresses du guide Wikivoyage, complétées par la carte OpenStreetMap."
                                else "Le guide ne couvre pas assez cette ville : adresses trouvées sur la carte OpenStreetMap."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(places.items, key = { _, it -> it.id }) { i, p -> PlaceCard(i + 1, p, places.city, wiki) }
                item {
                    Text(
                        "Données © les contributeurs d'OpenStreetMap, Wikipédia, Wikidata et Wikivoyage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceCard(rank: Int, p: PointOfInterest, city: String, wiki: suspend (String) -> WikiSummary?) {
    val context = LocalContext.current
    var summary by remember(p.id) { mutableStateOf<WikiSummary?>(null) }
    LaunchedEffect(p.id) {
        p.wikipedia?.let { summary = wiki(it) }
    }
    Card(Modifier.fillMaxWidth()) {
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
                Text("$rank. ${p.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    if (p.distanceM >= 0) "${p.kind} · ${formatDistance(p.distanceM)} du centre" else p.kind,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                (summary?.extract ?: p.description)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                p.address?.let { Text("📍 $it", style = MaterialTheme.typography.bodySmall) }
                p.openingHours?.let {
                    Text("🕒 ${PlacesParser.frenchHours(it)}", style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        p.isVenue -> {
                            Button(onClick = { openUrl(context, ActivityLinks.restaurantUrl(p, city)) }) {
                                Text(if (p.website != null && p.kind.startsWith("Restaurant")) "Réserver" else "Voir")
                            }
                            p.phone?.let { phone ->
                                OutlinedButton(onClick = { dial(context, phone) }) { Text("Appeler") }
                            }
                        }
                        p.isFree -> Text(
                            "Accès libre",
                            style = MaterialTheme.typography.labelLarge,
                            color = com.riad.voyage.ui.theme.dealColor(),
                        )
                        else -> TicketButton(p, city)
                    }
                    TextButton(onClick = { openUrl(context, ActivityLinks.directionsUrl(p, city)) }) { Text("Itinéraire") }
                }
                val more = p.wikipedia?.let { PlacesApi.wikiPageUrl(it) } ?: p.website.takeIf { !p.isVenue }
                more?.let { url ->
                    TextButton(onClick = { openUrl(context, url) }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (p.wikipedia != null) "En savoir plus (Wikipédia) ›" else "Site officiel ›")
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
        Button(onClick = { open = true }) { Text("Réserver des billets") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ActivityLinks.ticketUrls(p, city).forEach { partner ->
                DropdownMenuItem(
                    text = { Text("Billets sur ${partner.name}") },
                    onClick = {
                        open = false
                        openUrl(context, partner.url)
                    },
                )
            }
            p.website?.let { site ->
                DropdownMenuItem(
                    text = { Text("Site officiel du lieu") },
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
