package com.riad.voyage.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.Airlines
import com.riad.voyage.data.Article
import com.riad.voyage.data.SavedArticle
import com.riad.voyage.data.SavedPlace
import com.riad.voyage.data.SavedStep
import com.riad.voyage.data.Trip
import com.riad.voyage.data.TripText
import com.riad.voyage.data.WikiSummary
import com.riad.voyage.t
import com.riad.voyage.tf
import com.riad.voyage.tk
import java.time.LocalDate

/** Actions de modification d'un voyage (programme, bagages, infos pratiques). */
class TripActions(
    val moveStep: (String, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    val deleteStep: (String, Int, Int) -> Unit = { _, _, _ -> },
    val editStep: (String, Int, Int, String, String, String) -> Unit = { _, _, _, _, _, _ -> },
    val addStep: (String, Int, String, String, String) -> Unit = { _, _, _, _, _ -> },
    val addDay: (String) -> Unit = {},
    val packing: suspend (Trip) -> List<com.riad.voyage.data.PackItem> = { emptyList() },
    val togglePacked: (String, String) -> Unit = { _, _ -> },
    val addPackItem: (String, String) -> Unit = { _, _ -> },
    val removePackItem: (String, String) -> Unit = { _, _ -> },
    val practical: (Trip) -> Unit = {},
)

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
    wiki: suspend (String) -> WikiSummary? = { null },
    weather: suspend (String) -> List<com.riad.voyage.data.DayWeather> = { emptyList() },
    onReadArticle2: (String) -> Unit = {},
    actions: TripActions = TripActions(),
) {
    val trip = trips.firstOrNull { it.id == openId }
    when {
        article != null -> ArticleReader(article, onClose = { onReadArticle(null) })
        trip != null -> TripDetail(trip, onBack = { onOpen(null) }, onDelete, onRemove, onReadArticle, onHotels, onBudget, wiki, onReadArticle2, weather, actions)
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
    wiki: suspend (String) -> WikiSummary?,
    onPlaceArticle: (String) -> Unit,
    weather: suspend (String) -> List<com.riad.voyage.data.DayWeather>,
    actions: TripActions,
) {
    val context = LocalContext.current
    var showPacking by remember { mutableStateOf(false) }
    var stepDialog by remember { mutableStateOf<Triple<Int, Int?, SavedStep?>?>(null) }   // jour, index (null = ajout), étape
    if (showPacking) PackingSheet(trip, actions, onDismiss = { showPacking = false })
    stepDialog?.let { (day, index, step) ->
        StepDialog(step, onDismiss = { stepDialog = null }) { time, name, note ->
            if (index == null) actions.addStep(trip.id, day, time, name, note) else actions.editStep(trip.id, day, index, time, name, note)
            stepDialog = null
        }
    }
    var detail by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<SavedPlace, PlaceExtra>?>(null) }
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
            val start = trip.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val end = trip.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (start == null || !start.isBefore(LocalDate.now()) || (end != null && !end.isBefore(LocalDate.now())))
                WeatherStrip(trip.city, weather, start?.coerceAtLeast(LocalDate.now()), end ?: start, Modifier.padding(top = 8.dp))
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
                Text(t("💰 Budget et dépenses"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPacking = true }, modifier = Modifier.weight(1f)) { Text(t("🧳 Bagages")) }
                OutlinedButton(onClick = { actions.practical(trip) }, modifier = Modifier.weight(1f)) { Text(t("ℹ️ Infos pratiques")) }
            }
        }

        if (trip.flights.isNotEmpty()) {
            item { SectionTitle(t("Vols"), Modifier.padding(top = 10.dp)) }
            itemsIndexed(trip.flights) { i, f ->
                Card(onClick = { openUrl(context, f.url) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${t(f.origin)} → ${t(f.destination)}", fontWeight = FontWeight.Bold)
                            Text(formatDateTime(f.departureAt) + (f.returnAt?.let { " · " + formatDateTime(it) } ?: ""),
                                style = MaterialTheme.typography.bodySmall)
                            Text(formatPrice(f.price, f.currency) + " · " + Airlines.name(f.airline),
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { onRemove(trip.id, "flight", i) }) { Text("✕") }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle(t("Programme"), Modifier.padding(top = 10.dp))
                    trip.programTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (trip.program.isNotEmpty()) TextButton(onClick = { onRemove(trip.id, "program", 0) }) { Text("✕") }
            }
            // Carte de l'itinéraire, jour par jour.
            ProgramMap(
                trip.program.map { d ->
                    d.title to d.steps.filter { it.place.hasCoordinates }.map { MapStop(it.place.name, it.place.lat, it.place.lon) }
                },
                Modifier.padding(top = 6.dp),
            )
        }
        trip.program.forEachIndexed { dayIndex, day ->
            item {
                Text(day.title + (day.weather?.let { " · $it" } ?: ""), fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp))
            }
            itemsIndexed(day.steps) { i, s ->
                Card(
                    onClick = { detail = s.place to PlaceExtra(why = s.why, time = s.time, durationMin = s.durationMin) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.time, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(54.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${s.emoji} ${s.place.name}", fontWeight = FontWeight.Medium)
                            Text(listOf(s.moment, tk(s.place.kind)).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StepMenu(
                            canUp = i > 0, canDown = i < day.steps.lastIndex,
                            onUp = { actions.moveStep(trip.id, dayIndex, i, -1) },
                            onDown = { actions.moveStep(trip.id, dayIndex, i, 1) },
                            onEdit = { stepDialog = Triple(dayIndex, i, s) },
                            onDelete = { actions.deleteStep(trip.id, dayIndex, i) },
                        )
                    }
                }
            }
            item {
                TextButton(onClick = { stepDialog = Triple(dayIndex, null, null) }, contentPadding = PaddingValues(0.dp)) {
                    Text(t("+ Ajouter une étape"))
                }
            }
        }
        item {
            TextButton(onClick = { actions.addDay(trip.id) }, contentPadding = PaddingValues(0.dp)) {
                Text(if (trip.program.isEmpty()) t("+ Créer un programme") else t("+ Ajouter un jour"))
            }
        }

        if (trip.places.isNotEmpty()) {
            item { SectionTitle("♥ " + t("Lieux favoris"), Modifier.padding(top = 10.dp)) }
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
            item { SectionTitle("📖 " + t("Articles (lecture hors connexion)"), Modifier.padding(top = 10.dp)) }
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


/** Menu d'une étape : monter, descendre, modifier, supprimer. */
@Composable
private fun StepMenu(canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (canUp) DropdownMenuItem(text = { Text(t("↑ Monter")) }, onClick = { open = false; onUp() })
            if (canDown) DropdownMenuItem(text = { Text(t("↓ Descendre")) }, onClick = { open = false; onDown() })
            DropdownMenuItem(text = { Text(t("✎ Modifier")) }, onClick = { open = false; onEdit() })
            DropdownMenuItem(text = { Text(t("✕ Supprimer")) }, onClick = { open = false; onDelete() })
        }
    }
}

/** Ajouter ou modifier une étape : heure, lieu, note. */
@Composable
private fun StepDialog(step: SavedStep?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var time by remember { mutableStateOf(step?.time ?: "10:00") }
    var name by remember { mutableStateOf(step?.place?.name.orEmpty()) }
    var note by remember { mutableStateOf(step?.moment.orEmpty()) }
    val validTime = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(time.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == null) t("Nouvelle étape") else t("Modifier l'étape")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(time, { time = it }, label = { Text(t("Heure (HH:MM)")) }, singleLine = true, isError = !validTime)
                OutlinedTextField(name, { name = it }, label = { Text(t("Lieu ou activité")) }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text(t("Note (facultatif)")) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(time, name, note) }, enabled = validTime && name.isNotBlank()) { Text(t("Enregistrer")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annuler")) } },
    )
}

/** Liste de bagages : générée (météo, durée, activités, pays) et complétée à la main ; cases cochées gardées. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackingSheet(trip: Trip, actions: TripActions, onDismiss: () -> Unit) {
    val items by produceState<List<com.riad.voyage.data.PackItem>?>(null, trip.id) { value = actions.packing(trip) }
    var extra by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Text("🧳 " + tf("Bagages pour {0}", t(trip.city)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val all = items.orEmpty().map { it.key } + trip.extraItems
                Text(tf("{0} / {1} prêts", all.count { it in trip.packed }, all.size), color = MaterialTheme.colorScheme.primary)
                if (items == null) CircularProgressIndicator(Modifier.padding(16.dp))
            }
            val list = items.orEmpty()
            com.riad.voyage.data.Packing.categories.forEach { cat ->
                val inCat = list.filter { it.category == cat }
                if (inCat.isNotEmpty()) {
                    item { Text(t(cat), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
                    items(inCat, key = { it.key }) { p ->
                        PackRow(t(p.key) + (p.count?.let { " × $it" } ?: ""), p.key in trip.packed) { actions.togglePacked(trip.id, p.key) }
                    }
                }
            }
            item { Text(t("Mes ajouts"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
            items(trip.extraItems, key = { "x_$it" }) { x ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { PackRow(x, x in trip.packed) { actions.togglePacked(trip.id, x) } }
                    TextButton(onClick = { actions.removePackItem(trip.id, x) }) { Text("✕") }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(extra, { extra = it }, label = { Text(t("Ajouter un objet")) }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { actions.addPackItem(trip.id, extra); extra = "" }, enabled = extra.isNotBlank()) { Text("+") }
                }
            }
        }
    }
}

@Composable
private fun PackRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label, color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}
