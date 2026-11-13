package com.riad.voyage.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.Article
import com.riad.voyage.data.SavedArticle
import com.riad.voyage.data.Trip
import com.riad.voyage.data.TripText
import com.riad.voyage.t
import com.riad.voyage.tf
import com.riad.voyage.tk

@Composable
fun TripsScreen(
    trips: List<Trip>,
    openId: String?,
    article: Article?,
    onOpen: (String?) -> Unit,
    onDelete: (String) -> Unit,
    onRemove: (String, String, Int) -> Unit,
    onReadArticle: (SavedArticle?) -> Unit,
    onAlerts: () -> Unit,
    alertCount: Int,
    onHotels: (Trip) -> Unit = {},
    onBudget: (Trip) -> Unit = {},
    wiki: suspend (String) -> com.riad.voyage.data.WikiSummary? = { null },
    onReadArticle2: (String) -> Unit = {},
) {
    val trip = trips.firstOrNull { it.id == openId }
    when {
        article != null -> ArticleReader(article, onClose = { onReadArticle(null) })
        trip != null -> TripDetail(trip, onBack = { onOpen(null) }, onDelete, onRemove, onReadArticle, onHotels, onBudget, wiki, onReadArticle2)
        else -> TripList(trips, onOpen, onAlerts, alertCount)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripList(trips: List<Trip>, onOpen: (String?) -> Unit, onAlerts: () -> Unit, alertCount: Int) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(t("Mes voyages"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                t("Vos vols, programmes, lieux et articles gardés, disponibles même sans connexion."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedButton(onClick = onAlerts, modifier = Modifier.fillMaxWidth()) {
                Icon(AppIcons.Bell, null)
                Spacer(Modifier.width(8.dp))
                Text(t("Alertes de prix") + if (alertCount > 0) " ($alertCount)" else "")
            }
        }
        if (trips.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        t("Aucun voyage pour l'instant. Gardez un vol (♡), un programme Expérience, un lieu ou un article : il apparaîtra ici."),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        items(trips, key = { it.id }) { tr ->
            Card(onClick = { onOpen(tr.id) }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("✈️ " + t(tr.city), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    datesOf(tr)?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    Text(summaryOf(tr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun datesOf(tr: Trip): String? {
    val s = tr.startDate?.let { runCatching { formatDay(java.time.LocalDate.parse(it)) }.getOrNull() } ?: return null
    val e = tr.endDate?.let { runCatching { formatDay(java.time.LocalDate.parse(it)) }.getOrNull() }
    return if (e != null && e != s) "$s → $e" else s
}

private fun summaryOf(tr: Trip) = listOfNotNull(
    tf("{0} vol(s)", tr.flights.size).takeIf { tr.flights.isNotEmpty() },
    t("Programme").takeIf { tr.program.isNotEmpty() },
    tf("{0} lieu(x)", tr.places.size).takeIf { tr.places.isNotEmpty() },
    tf("{0} article(s)", tr.articles.size).takeIf { tr.articles.isNotEmpty() },
).joinToString(" · ").ifBlank { t("Vide") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetail(
    trip: Trip,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    onRemove: (String, String, Int) -> Unit,
    onReadArticle: (SavedArticle?) -> Unit,
    onHotels: (Trip) -> Unit,
    onBudget: (Trip) -> Unit,
    wiki: suspend (String) -> com.riad.voyage.data.WikiSummary?,
    onPlaceArticle: (String) -> Unit,
) {
    val context = LocalContext.current
    var detail by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<com.riad.voyage.data.SavedPlace, PlaceExtra>?>(null) }
    detail?.let { (p, extra) ->
        PlaceDetailSheet(
            place = p.toPoi(), category = null, city = trip.city, extra = extra, wiki = wiki,
            favorite = trip.places.any { it.name == p.name }, onFavorite = {},
            onReadArticle = { tag -> detail = null; onPlaceArticle(tag) },
            onDismiss = { detail = null },
        )
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text(t("← Mes voyages")) }
            Text("✈️ " + t(trip.city), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            datesOf(trip)?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Envolée · " + t(trip.city))
                        .putExtra(Intent.EXTRA_TEXT, TripText.share(trip) { t(it) })
                    runCatching { context.startActivity(Intent.createChooser(send, t("Partager mon voyage"))) }
                }) { Text(t("Partager")) }
                OutlinedButton(onClick = { onDelete(trip.id) }) { Text(t("Supprimer")) }
            }
            OutlinedButton(onClick = { onHotels(trip) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(t("🏨 Trouver un hôtel"))
            }
            OutlinedButton(onClick = { onBudget(trip) }, modifier = Modifier.fillMaxWidth()) {
                Text(t("💰 Budget du séjour"))
            }
        }

        if (trip.flights.isNotEmpty()) {
            item { SectionHeader(t("Vols")) }
            itemsIndexed(trip.flights) { i, f ->
                Card(onClick = { openUrl(context, f.url) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${t(f.origin)} → ${t(f.destination)}", fontWeight = FontWeight.Bold)
                            Text(formatDateTime(f.departureAt) + (f.returnAt?.let { " · " + formatDateTime(it) } ?: ""),
                                style = MaterialTheme.typography.bodySmall)
                            Text(formatPrice(f.price, f.currency) + " · " + com.riad.voyage.data.Airlines.name(f.airline),
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { onRemove(trip.id, "flight", i) }) { Text("✕") }
                    }
                }
            }
        }

        if (trip.program.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionHeader(t("Programme"))
                        trip.programTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    TextButton(onClick = { onRemove(trip.id, "program", 0) }) { Text("✕") }
                }
            }
            trip.program.forEach { day ->
                item {
                    Text(day.title + (day.weather?.let { " · $it" } ?: ""), fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp))
                }
                items(day.steps) { s ->
                    Card(
                        onClick = { detail = s.place to PlaceExtra(why = s.why, time = s.time, durationMin = s.durationMin) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Text(s.time, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(54.dp))
                            Column {
                                Text("${s.emoji} ${s.place.name}", fontWeight = FontWeight.Medium)
                                Text(s.moment + " · " + tk(s.place.kind), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (trip.places.isNotEmpty()) {
            item { SectionHeader("♥ " + t("Lieux favoris")) }
            itemsIndexed(trip.places) { i, p ->
                Card(onClick = { detail = p to PlaceExtra() }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tk(p.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { openUrl(context, ActivityLinks.directionsUrl(p.toPoi(), trip.city)) }) { Text(t("Itinéraire")) }
                        TextButton(onClick = { onRemove(trip.id, "place", i) }) { Text("✕") }
                    }
                }
            }
        }

        if (trip.articles.isNotEmpty()) {
            item { SectionHeader("📖 " + t("Articles (lecture hors connexion)")) }
            itemsIndexed(trip.articles) { i, a ->
                Card(onClick = { onReadArticle(a) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(a.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemove(trip.id, "article", i) }) { Text("✕") }
                    }
                }
            }
        }

        if (trip.isEmpty) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp)) { Text(t("Ce voyage est vide.")) } }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
}
