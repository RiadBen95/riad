package com.riad.voyage.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.riad.voyage.FlightForm
import com.riad.voyage.R
import com.riad.voyage.data.Place
import com.riad.voyage.data.PopularPlaces
import com.riad.voyage.data.PriceAlert
import com.riad.voyage.data.RecentSearch
import com.riad.voyage.data.Trip
import com.riad.voyage.data.WikiSummary
import com.riad.voyage.t
import com.riad.voyage.tf
import com.riad.voyage.ui.theme.BrandNavy
import com.riad.voyage.ui.theme.BrandNight
import java.time.LocalDate
import java.time.LocalTime

/** Destinations mises en avant sur l'accueil, avec leur article Wikipédia (pour la photo). */
private val featured = listOf(
    "BCN" to "fr:Barcelone", "IST" to "fr:Istanbul", "RAK" to "fr:Marrakech", "ROM" to "fr:Rome",
    "LIS" to "fr:Lisbonne", "DXB" to "fr:Dubaï", "ALG" to "fr:Alger", "LON" to "fr:Londres",
    "NYC" to "fr:New York", "TUN" to "fr:Tunis", "PAR" to "fr:Paris", "AMS" to "fr:Amsterdam",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    form: FlightForm,
    recents: List<RecentSearch>,
    alerts: List<PriceAlert>,
    demo: Boolean,
    onOpenFlights: () -> Unit,
    onOpenActivities: () -> Unit,
    onOpenExperience: () -> Unit,
    onOpenBlog: () -> Unit,
    onOpenHotels: () -> Unit = {},
    trips: List<Trip> = emptyList(),
    onOpenTrip: (String?) -> Unit = {},
    onOpenAlerts: () -> Unit,
    onOpenSettings: () -> Unit,
    onRecent: (RecentSearch) -> Unit,
    onClearRecents: () -> Unit = {},
    onDestination: (Place) -> Unit,
    wiki: suspend (String) -> WikiSummary?,
) {
    val hour = LocalTime.now().hour
    val greeting = if (hour in 5..17) t("Bonjour 👋") else t("Bonsoir 👋")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        // En-tête en dégradé, avec la barre de recherche.
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(BrandNavy, BrandNight)))
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 52.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.logo_mark),
                            contentDescription = null,
                            modifier = Modifier.height(30.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "envolée",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onOpenSettings) {
                            Icon(AppIcons.Settings, contentDescription = t("Réglages"), tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(greeting, color = Color.White.copy(alpha = 0.8f))
                    Text(
                        t("Où partez-vous ?"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
        item {
            Card(
                onClick = onOpenFlights,
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 0.dp)
                    .offset(y = (-36).dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Search, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Rechercher un vol"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            tf("Depuis {0} · {1} passager(s)", form.origin?.name?.let { t(it) } ?: "…", form.adults),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-20).dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickTile(t("Vols"), AppIcons.Flight, onOpenFlights, Modifier.weight(1f))
                QuickTile(t("Hôtels"), AppIcons.Hotel, onOpenHotels, Modifier.weight(1f))
                QuickTile(t("Sorties"), AppIcons.Ticket, onOpenActivities, Modifier.weight(1f))
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-10).dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickTile(t("Expérience"), AppIcons.Sparkle, onOpenExperience, Modifier.weight(1f))
                QuickTile(t("Blogue"), AppIcons.Book, onOpenBlog, Modifier.weight(1f))
                QuickTile(t("Voyages"), AppIcons.Luggage, { onOpenTrip(null) }, Modifier.weight(1f), badge = alerts.size.takeIf { it > 0 })
            }
        }
        // Prochain voyage (ou le dernier modifié) : accès direct au carnet.
        val next = trips.filter { !it.isEmpty }.sortedWith(
            compareBy<Trip> { tr ->
                tr.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { !it.isBefore(LocalDate.now()) } ?: LocalDate.MAX
            }.thenByDescending { it.updated },
        ).firstOrNull()
        if (next != null) {
            item {
                Card(
                    onClick = { onOpenTrip(next.id) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Luggage, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tf("Mon voyage : {0}", t(next.city)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    next.startDate?.let { runCatching { formatDay(LocalDate.parse(it)) }.getOrNull() },
                                    t("Programme").takeIf { next.program.isNotEmpty() },
                                    tf("{0} vol(s)", next.flights.size).takeIf { next.flights.isNotEmpty() },
                                    tf("{0} lieu(x)", next.places.size).takeIf { next.places.isNotEmpty() },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (demo) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) { DemoBanner(onOpenSettings) }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (recents.isNotEmpty()) {
            item { SectionTitle(t("Reprendre une recherche"), sectionPad, AppIcons.History) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recents) { r -> RecentCard(r) { onRecent(r) } }
                }
            }
            // Discret : petit lien gris sous l'historique.
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                    androidx.compose.material3.TextButton(onClick = onClearRecents) {
                        Text(t("Effacer l'historique"), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
        }
        item { SectionTitle(t("Destinations populaires"), sectionPad, null) }
        item {
            val list = featured.filter { it.first != form.origin?.code }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list) { (code, tag) ->
                    val place = PopularPlaces.find(code) ?: return@items
                    DestinationCard(place, tag, wiki) { onDestination(place) }
                }
            }
        }
        item {
            Text(
                t("Prix des vols : Aviasales · Lieux : OpenStreetMap · Photos : Wikipédia"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, badge: Int? = null) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                if (badge != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = (-4).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(badge.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentCard(r: RecentSearch, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), modifier = Modifier.width(220.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "${r.origin.name} → ${r.destination.name}",
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val dates = buildString {
                append(formatDateOrMonth(r.departure))
                r.returnDate?.let { append(" – ").append(formatDateOrMonth(it)) }
            }
            Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val past = runCatching { LocalDate.parse(r.departure).isBefore(LocalDate.now()) }.getOrDefault(false)
            Text(
                if (past) t("Relancer avec de nouvelles dates ›") else t("Relancer la recherche ›"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationCard(place: Place, wikiTag: String, wiki: suspend (String) -> WikiSummary?, onClick: () -> Unit) {
    val context = LocalContext.current
    var image by remember(wikiTag) { mutableStateOf<String?>(null) }
    LaunchedEffect(wikiTag) { image = wiki(wikiTag)?.imageUrl }
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), modifier = Modifier.size(width = 150.dp, height = 190.dp)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer)) {
            image?.let {
                AsyncImage(
                    model = it,
                    contentDescription = place.name,
                    imageLoader = Images.get(context),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(t(place.name), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(t(place.country), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val sectionPad = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)
